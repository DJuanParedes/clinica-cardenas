package pe.clinica.cardenas.security;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.Base64;

public class DataTransferService {
    private static final Set<String> EXCLUDED = Set.of("DATABASECHANGELOG", "DATABASECHANGELOGLOCK");
    private final JdbcTemplate jdbc;

    public DataTransferService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<TableInfo> tables() {
        try (Connection c = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            String catalog = c.getCatalog();
            // MySQL expone la base activa como catalog. Usar schema aquí puede filtrar todas las tablas.
            String schema = null;
            List<TableInfo> out = new ArrayList<>();
            try (ResultSet rs = md.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if (name == null || EXCLUDED.contains(name.toUpperCase(Locale.ROOT))) continue;
                    List<ColumnInfo> cols = columns(md, catalog, schema, name);
                    List<String> pk = primaryKeys(md, catalog, schema, name);
                    boolean tenantOwned = cols.stream().anyMatch(col -> col.name().equalsIgnoreCase("clinic_id"));
                    long count = tenantOwned
                            ? jdbc.queryForObject("SELECT COUNT(*) FROM " + q(name) + " WHERE clinic_id=?", Long.class, TenantContext.currentClinicId())
                            : jdbc.queryForObject("SELECT COUNT(*) FROM " + q(name), Long.class);
                    out.add(new TableInfo(name, count, cols, pk));
                }
            }
            out.sort(Comparator.comparing(TableInfo::tableName));
            return out;
        } catch (SQLException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo inspeccionar la base de datos", e);
        }
    }

    public TableInfo table(String requested) {
        return tables().stream().filter(t -> t.tableName().equalsIgnoreCase(requested)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tabla no encontrada"));
    }

    public List<Map<String,Object>> rows(String table) {
        TableInfo info = table(table);
        String sql = "SELECT * FROM " + q(info.tableName()) + (hasTenant(info) ? " WHERE clinic_id=?" : "");
        Object[] args = hasTenant(info) ? new Object[]{TenantContext.currentClinicId()} : new Object[]{};
        return jdbc.query(sql, (rs, rowNum) -> {
            Map<String,Object> row = new LinkedHashMap<>();
            for (ColumnInfo col : info.columns()) row.put(col.name(), exportValue(rs.getObject(col.name())));
            return row;
        }, args);
    }

    public ImportResult importRows(String tableName, String mode, List<Map<String,Object>> rows) {
        if (rows == null) rows = List.of();
        if (rows.size() > 50_000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Máximo 50,000 filas por importación");
        String normalizedMode = mode == null ? "APPEND" : mode.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("APPEND", "UPSERT").contains(normalizedMode)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modo permitido: APPEND o UPSERT");
        TableInfo table = table(tableName);
        Map<String,ColumnInfo> columns = new LinkedHashMap<>();
        table.columns().forEach(c -> columns.put(c.name().toUpperCase(Locale.ROOT), c));
        int inserted=0, updated=0, skipped=0;
        List<RowError> errors = new ArrayList<>();
        int excelRow = 2;
        for (Map<String,Object> input : rows) {
            try {
                Map<String,Object> clean = normalizeInput(input, columns, normalizedMode);
                if (clean.isEmpty()) { skipped++; excelRow++; continue; }
                boolean hasPk = !table.primaryKeys().isEmpty() && table.primaryKeys().stream().allMatch(pk -> present(clean.get(pk)));
                if ("UPSERT".equals(normalizedMode) && hasPk && exists(table, clean)) {
                    update(table, clean); updated++;
                } else {
                    insert(table, clean); inserted++;
                }
            } catch (Exception ex) {
                errors.add(new RowError(excelRow, readable(ex)));
            }
            excelRow++;
        }
        return new ImportResult(rows.size(), inserted, updated, skipped, errors.size(), errors);
    }

    private Map<String,Object> normalizeInput(Map<String,Object> input, Map<String,ColumnInfo> columns, String mode) {
        Map<String,Object> clean = new LinkedHashMap<>();
        if (input == null) return clean;
        for (Map.Entry<String,Object> e : input.entrySet()) {
            if (e.getKey() == null) continue;
            ColumnInfo col = columns.get(e.getKey().trim().toUpperCase(Locale.ROOT));
            if (col == null || col.generated()) continue;
            if (col.name().equalsIgnoreCase("clinic_id")) continue;
            Object raw = e.getValue();
            if (!present(raw)) {
                if ("UPSERT".equals(mode)) continue;
                if (col.autoIncrement() || col.defaultValue() != null) continue;
                if (col.nullable()) clean.put(col.name(), null);
                continue;
            }
            clean.put(col.name(), convert(raw, col.jdbcType()));
        }
        ColumnInfo tenant = columns.get("CLINIC_ID");
        if (tenant != null) clean.put(tenant.name(), TenantContext.currentClinicId());
        return clean;
    }

    private boolean exists(TableInfo table, Map<String,Object> row) {
        String where = String.join(" AND ", table.primaryKeys().stream().map(k -> q(k)+"=?").toList());
        List<Object> args = new ArrayList<>(table.primaryKeys().stream().map(row::get).toList());
        if (hasTenant(table)) { where += " AND clinic_id=?"; args.add(TenantContext.currentClinicId()); }
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM "+q(table.tableName())+" WHERE "+where, Integer.class, args.toArray());
        return n != null && n > 0;
    }

    private void update(TableInfo table, Map<String,Object> row) {
        List<String> setCols = row.keySet().stream().filter(k -> !k.equalsIgnoreCase("clinic_id") && table.primaryKeys().stream().noneMatch(pk -> pk.equalsIgnoreCase(k))).toList();
        if (setCols.isEmpty()) return;
        String set = String.join(",", setCols.stream().map(k -> q(k)+"=?").toList());
        String where = String.join(" AND ", table.primaryKeys().stream().map(k -> q(k)+"=?").toList());
        List<Object> args = new ArrayList<>(); setCols.forEach(k -> args.add(row.get(k))); table.primaryKeys().forEach(k -> args.add(row.get(k)));
        if (hasTenant(table)) { where += " AND clinic_id=?"; args.add(TenantContext.currentClinicId()); }
        jdbc.update("UPDATE "+q(table.tableName())+" SET "+set+" WHERE "+where, args.toArray());
    }

    private void insert(TableInfo table, Map<String,Object> row) {
        if (row.isEmpty()) throw new IllegalArgumentException("Fila sin datos importables");
        List<String> cols = new ArrayList<>(row.keySet());
        String sql = "INSERT INTO "+q(table.tableName())+" ("+String.join(",", cols.stream().map(DataTransferService::q).toList())+") VALUES ("+String.join(",", Collections.nCopies(cols.size(), "?"))+")";
        jdbc.update(sql, cols.stream().map(row::get).toArray());
    }

    private List<ColumnInfo> columns(DatabaseMetaData md, String catalog, String schema, String table) throws SQLException {
        List<ColumnInfo> out = new ArrayList<>();
        try (ResultSet rs = md.getColumns(catalog, schema, table, "%")) {
            while (rs.next()) {
                out.add(new ColumnInfo(
                        rs.getString("COLUMN_NAME"), rs.getInt("DATA_TYPE"), rs.getString("TYPE_NAME"),
                        rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                        "YES".equalsIgnoreCase(safe(rs, "IS_AUTOINCREMENT")),
                        "YES".equalsIgnoreCase(safe(rs, "IS_GENERATEDCOLUMN")), rs.getString("COLUMN_DEF")
                ));
            }
        }
        return out;
    }

    private List<String> primaryKeys(DatabaseMetaData md, String catalog, String schema, String table) throws SQLException {
        List<Map.Entry<Short,String>> keys = new ArrayList<>();
        try (ResultSet rs = md.getPrimaryKeys(catalog, schema, table)) {
            while (rs.next()) keys.add(Map.entry(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME")));
        }
        keys.sort(Map.Entry.comparingByKey());
        return keys.stream().map(Map.Entry::getValue).toList();
    }

    private static String safe(ResultSet rs, String col) { try { return rs.getString(col); } catch (SQLException e) { return null; } }
    private static boolean hasTenant(TableInfo table) { return table.columns().stream().anyMatch(c -> c.name().equalsIgnoreCase("clinic_id")); }
    private static String q(String id) { return "`" + id.replace("`", "``") + "`"; }
    private static boolean present(Object v) { return v != null && (!(v instanceof String s) || !s.trim().isEmpty()); }
    private static String readable(Exception e) { Throwable t=e; while(t.getCause()!=null)t=t.getCause(); String m=t.getMessage(); return m==null?t.getClass().getSimpleName():m; }

    private Object exportValue(Object v) {
        if (v == null) return null;
        if (v instanceof Clob c) { try { return c.getSubString(1, (int)c.length()); } catch(SQLException e){ return ""; } }
        if (v instanceof Blob b) { try { return Base64.getEncoder().encodeToString(b.getBytes(1,(int)b.length())); } catch(SQLException e){ return ""; } }
        if (v instanceof byte[] bytes) return Base64.getEncoder().encodeToString(bytes);
        return v;
    }

    private Object convert(Object raw, int jdbcType) {
        if (raw == null) return null;
        String s = raw instanceof String ? ((String) raw).trim() : String.valueOf(raw);
        return switch (jdbcType) {
            case Types.BIGINT -> raw instanceof Number n ? n.longValue() : Long.valueOf(s);
            case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> raw instanceof Number n ? n.intValue() : Integer.valueOf(s);
            case Types.DECIMAL, Types.NUMERIC -> raw instanceof Number n ? new BigDecimal(n.toString()) : new BigDecimal(s.replace(",", "."));
            case Types.DOUBLE, Types.FLOAT, Types.REAL -> raw instanceof Number n ? n.doubleValue() : Double.valueOf(s.replace(",", "."));
            case Types.BOOLEAN, Types.BIT -> parseBoolean(raw);
            case Types.DATE -> java.sql.Date.valueOf(normalizeDate(s));
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> Time.valueOf(normalizeTime(s));
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> Timestamp.valueOf(normalizeTimestamp(s));
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> Base64.getDecoder().decode(s);
            default -> s;
        };
    }

    private Boolean parseBoolean(Object raw) {
        if (raw instanceof Boolean b) return b;
        if (raw instanceof Number n) return n.intValue()!=0;
        String s=String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (Set.of("true","1","si","sí","yes","x").contains(s)) return true;
        if (Set.of("false","0","no").contains(s)) return false;
        throw new IllegalArgumentException("Valor booleano inválido: "+raw);
    }
    private String normalizeDate(String s){ if(s.contains("T"))return s.substring(0,10); if(s.contains(" "))return s.substring(0,10); return s; }
    private String normalizeTime(String s){ if(s.length()==5)return s+":00"; if(s.contains("T"))return s.substring(s.indexOf('T')+1, Math.min(s.length(),s.indexOf('T')+9)); return s; }
    private String normalizeTimestamp(String s){ String x=s.replace('T',' '); if(x.length()==16)x += ":00"; if(x.endsWith("Z"))x=x.substring(0,x.length()-1); int plus=x.indexOf('+',10); if(plus>0)x=x.substring(0,plus); return x; }

    public record TableInfo(String tableName, long rowCount, List<ColumnInfo> columns, List<String> primaryKeys) {}
    public record ColumnInfo(String name, int jdbcType, String typeName, boolean nullable, boolean autoIncrement, boolean generated, String defaultValue) {}
    public record RowError(int row, String message) {}
    public record ImportResult(int totalRows, int inserted, int updated, int skipped, int errorCount, List<RowError> errors) {}
}
