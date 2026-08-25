package pe.clinica.cardenas.backupservice;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class BackupOrchestrator {
    private static final List<String> DATA_SERVICES = List.of("pacientes","agenda","atenciones","clinica","finanzas","laboratorio","inventario","operaciones","encuestas","contenido","auth");
    private final BackupProperties properties;
    private final ObjectMapper mapper;
    private final RestClient rest;

    public BackupOrchestrator(BackupProperties properties, ObjectMapper mapper){
        this.properties=properties;
        this.mapper=mapper;
        this.rest=RestClient.builder().build();
    }

    public List<ServiceCatalog> catalog(String auth){
        List<ServiceCatalog> out=new ArrayList<>();
        for(String key: DATA_SERVICES){
            String base=url(key);
            try{
                JsonNode node=getJson(base+"/api/internal/data-transfer/tables",auth);
                List<TableMeta> tables=mapper.convertValue(node,new TypeReference<List<TableMeta>>(){});
                out.add(new ServiceCatalog(key,label(key),tables,true,null));
            }catch(Exception ex){ out.add(new ServiceCatalog(key,label(key),List.of(),false,root(ex))); }
        }
        return out;
    }

    public byte[] exportTable(String service,String table,String auth,boolean template){
        TableMeta meta=findTable(service,table,auth);
        List<Map<String,Object>> rows=template?List.of():rowsForExport(service,meta.tableName(),auth);
        return workbookForTable(service,meta,rows,template);
    }

    public ImportResponse importExcel(String service,String table,String mode,MultipartFile file,String auth){
        if("auth".equalsIgnoreCase(service)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Las cuentas de usuario no se importan por Excel. Créelas desde Administración → Usuarios para aplicar DNI temporal, OTP y permisos correctamente.");
        if(file==null||file.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Selecciona un archivo Excel .xlsx");
        if(file.getSize()>25L*1024*1024)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El archivo supera 25 MB");
        TableMeta meta=findTable(service,table,auth);
        List<Map<String,Object>> rows=readExcel(file,meta);
        if(rows.size()>50_000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Máximo 50,000 filas por importación");
        String base=url(service);
        try{
            JsonNode result=rest.post().uri(base+"/api/internal/data-transfer/tables/"+encode(meta.tableName())+"/import?mode="+encode(mode==null?"APPEND":mode))
                    .header(HttpHeaders.AUTHORIZATION,auth).contentType(MediaType.APPLICATION_JSON).body(rows).retrieve().body(JsonNode.class);
            ImportResponse response=mapper.convertValue(result,ImportResponse.class);
            audit(auth,"IMPORTAR_EXCEL",service+"/"+meta.tableName(),Map.of("modo",mode==null?"APPEND":mode,"filas",rows.size(),"insertados",response.inserted(),"actualizados",response.updated(),"errores",response.errorCount()));
            return response;
        }catch(Exception ex){throw remoteError("No se pudo importar el Excel",ex);}
    }

    public List<ReportInfo> reports(){
        LocalDate today=LocalDate.now();
        String month=YearMonth.from(today).toString();
        String first=today.withDayOfMonth(1).toString();
        return List.of(
                new ReportInfo("analitica-citas","Analítica de citas del mes","agenda","/api/appointments/analytics?from="+first+"&to="+today),
                new ReportInfo("resumen-operativo","Resumen operativo","dashboard","/api/reports/summary"),
                new ReportInfo("resumen-financiero","Resumen financiero","finanzas","/api/finance/summary"),
                new ReportInfo("analisis-financiero","Análisis financiero","finanzas","/api/finance/analysis"),
                new ReportInfo("comisiones-mes","Vista previa de comisiones del mes","finanzas","/api/finance/commission-preview?month="+month),
                new ReportInfo("ingresos-diarios","Ingresos del día","finanzas","/api/finance/reports/daily?date="+today),
                new ReportInfo("ingresos-mensuales","Ingresos del mes","finanzas","/api/finance/reports/monthly?month="+month),
                new ReportInfo("cartera-pendiente","Cartera de tratamientos con saldo","atenciones","/api/encounters/pending-treatments"),
                new ReportInfo("analisis-sillones","Análisis por sillón del mes","atenciones","/api/encounters/chairs/analytics?from="+first+"&to="+today),
                new ReportInfo("calidad-clinica","Calidad clínica","atenciones","/api/encounters/quality/summary"),
                new ReportInfo("satisfaccion","Resumen de satisfacción","encuestas","/api/surveys/summary")
        );
    }

    public byte[] exportReport(String id,String auth){
        ReportInfo report=reports().stream().filter(r->r.id().equals(id)).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Informe no encontrado"));
        JsonNode data=getJson(url(report.service())+report.path(),auth);
        return workbookForJson(report.label(),data);
    }

    public byte[] globalZip(String auth){
        try(ByteArrayOutputStream baos=new ByteArrayOutputStream(); ZipOutputStream zip=new ZipOutputStream(baos)){
            List<Map<String,Object>> manifestTables=new ArrayList<>();
            List<ServiceCatalog> services=catalog(auth);
            List<ServiceCatalog> unavailable=services.stream().filter(s->!s.available()).toList();
            if(!unavailable.isEmpty()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No se generó un respaldo parcial. Servicios no disponibles: "+unavailable.stream().map(ServiceCatalog::label).toList());
            for(ServiceCatalog service:services){
                for(TableMeta table:service.tables()){
                    byte[] xlsx=workbookForTable(service.key(),table,rowsForExport(service.key(),table.tableName(),auth),false);
                    String name="tablas/"+safe(service.key())+"/"+safe(table.tableName().toLowerCase(Locale.ROOT))+".xlsx";
                    put(zip,name,xlsx);
                    manifestTables.add(Map.of("servicio",service.key(),"tabla",table.tableName(),"filas",table.rowCount(),"archivo",name));
                }
            }
            List<Map<String,Object>> manifestReports=new ArrayList<>();
            for(ReportInfo report:reports()){
                String name="informes/"+safe(report.id())+".xlsx";
                put(zip,name,exportReport(report.id(),auth));
                manifestReports.add(Map.of("id",report.id(),"nombre",report.label(),"archivo",name));
            }
            Map<String,Object> manifest=new LinkedHashMap<>();
            manifest.put("producto","Clínica Cárdenas"); manifest.put("tipo","Respaldo lógico Excel/ZIP");
            manifest.put("generadoEn",OffsetDateTime.now().toString()); manifest.put("versionFormato","1.0");
            manifest.put("tablas",manifestTables); manifest.put("informes",manifestReports);
            manifest.put("nota","Este respaldo contiene tablas e informes exportados. Por seguridad, los hashes de contraseña de la tabla de usuarios se exportan protegidos y no son importables por Excel. Para recuperación total conserve además un respaldo nativo de MySQL y los archivos físicos adjuntos.");
            put(zip,"manifest.json",mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
            put(zip,"LEEME.txt",("CLÍNICA CÁRDENAS - RESPALDO GLOBAL\r\n\r\nIncluye una copia Excel por tabla y los informes disponibles al momento de la descarga.\r\nPor seguridad, los hashes de contraseña de usuarios se reemplazan por [PROTEGIDO] y las cuentas no se importan por Excel.\r\nPara recuperación total en producción conserve además un respaldo nativo de MySQL y los archivos físicos/adjuntos externos.\r\nPara importar datos use las plantillas del módulo Copias e importación.\r\nNo modifique identificadores primarios salvo que conozca las relaciones entre tablas.\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.finish(); byte[] result=baos.toByteArray(); registerBackup(auth,result,manifestTables.size(),manifestReports.size()); audit(auth,"DESCARGAR_RESPALDO_GLOBAL","GLOBAL",Map.of("tablas",manifestTables.size(),"informes",manifestReports.size(),"bytes",result.length)); return result;
        }catch(IOException e){throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"No se pudo generar el respaldo global",e);}
    }

    private void registerBackup(String auth,byte[] bytes,int tables,int reports){
        try{
            Map<String,Object> body=new LinkedHashMap<>();
            body.put("backupName","Respaldo global "+LocalDateTime.now().withSecond(0).withNano(0));
            body.put("scope",tables+" tablas + "+reports+" informes");
            body.put("location","Descarga local del administrador");
            body.put("sizeMb",Math.max(1L,(long)Math.ceil(bytes.length/1024.0/1024.0)));
            body.put("checksum",sha256(bytes));
            body.put("status","EXITOSO");
            body.put("encrypted",false);body.put("offsiteCopy",false);
            body.put("completedAt",LocalDateTime.now().toString());body.put("restorationTestedAt",null);
            body.put("notes","Generado automáticamente desde Copias e importación. El ZIP contiene tablas e informes; archivos físicos externos requieren respaldo adicional.");
            rest.post().uri(url("atenciones")+"/api/governance/backups").header(HttpHeaders.AUTHORIZATION,auth).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
        }catch(Exception ex){
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"El respaldo se generó en memoria, pero no pudo registrarse en Auditoría. No se entregará una copia sin trazabilidad; verifica el servicio de Gobierno/Auditoría e intenta nuevamente.",ex);
        }
    }
    private String sha256(byte[] bytes){
        try{byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder b=new StringBuilder();for(byte x:digest)b.append(String.format("%02x",x));return b.toString();}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 no está disponible en la JVM",e);}
    }

    private void audit(String auth,String action,String entityId,Object after){
        try{
            String encounter=url("atenciones");
            Map<String,Object> body=new LinkedHashMap<>();body.put("module","RESPALDOS");body.put("entityType","DataTransfer");body.put("entityId",entityId);body.put("action",action);body.put("before",null);body.put("after",after);body.put("reason","Operación ejecutada desde Copias e importación");
            rest.post().uri(encounter+"/api/audit/events").header(HttpHeaders.AUTHORIZATION,auth).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
        }catch(Exception ignored){}
    }

    private TableMeta findTable(String service,String table,String auth){
        if(!DATA_SERVICES.contains(service))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Servicio de datos no encontrado");
        JsonNode node=getJson(url(service)+"/api/internal/data-transfer/tables",auth);
        List<TableMeta> tables=mapper.convertValue(node,new TypeReference<List<TableMeta>>(){});
        return tables.stream().filter(t->t.tableName().equalsIgnoreCase(table)).findFirst()
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tabla no encontrada"));
    }
    private List<Map<String,Object>> rows(String service,String table,String auth){
        JsonNode node=getJson(url(service)+"/api/internal/data-transfer/tables/"+encode(table),auth);
        return mapper.convertValue(node,new TypeReference<List<Map<String,Object>>>(){});
    }

    private List<Map<String,Object>> rowsForExport(String service,String table,String auth){
        List<Map<String,Object>> source=rows(service,table,auth);
        if(!"auth".equalsIgnoreCase(service)) return source;
        List<Map<String,Object>> safeRows=new ArrayList<>(source.size());
        for(Map<String,Object> original:source){
            Map<String,Object> copy=new LinkedHashMap<>(original);
            copy.replaceAll((key,value)->isCredentialSecretColumn(key)?"[PROTEGIDO]":value);
            safeRows.add(copy);
        }
        return safeRows;
    }

    private boolean isCredentialSecretColumn(String key){
        if(key==null) return false;
        String normalized=key.replaceAll("[^A-Za-z0-9]","").toUpperCase(Locale.ROOT);
        return normalized.equals("PASSWORDHASH") || normalized.equals("PASSWORD");
    }
    private JsonNode getJson(String uri,String auth){
        try{return rest.get().uri(uri).header(HttpHeaders.AUTHORIZATION,auth).retrieve().body(JsonNode.class);}catch(Exception ex){throw remoteError("No se pudo consultar "+uri,ex);}
    }
    private ResponseStatusException remoteError(String message,Exception ex){return new ResponseStatusException(HttpStatus.BAD_GATEWAY,message+": "+root(ex),ex);}
    private String root(Throwable t){while(t.getCause()!=null)t=t.getCause();return t.getMessage()==null?t.getClass().getSimpleName():t.getMessage();}
    private String url(String key){String v=properties.getServices().get(key);if(!StringUtils.hasText(v))throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"URL no configurada para "+key);return v.replaceAll("/$","");}

    private byte[] workbookForTable(String service,TableMeta meta,List<Map<String,Object>> rows,boolean template){
        try(XSSFWorkbook wb=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            CellStyle head=headerStyle(wb); CellStyle date=dateStyle(wb); CellStyle text=textStyle(wb);
            Sheet sheet=wb.createSheet("DATOS"); sheet.createFreezePane(0,1);
            Row hr=sheet.createRow(0);
            for(int i=0;i<meta.columns().size();i++){Cell c=hr.createCell(i);c.setCellValue(meta.columns().get(i).name());c.setCellStyle(head);}
            int r=1;
            for(Map<String,Object> row:rows){Row rr=sheet.createRow(r++);for(int i=0;i<meta.columns().size();i++){ColumnMeta col=meta.columns().get(i);Cell cell=rr.createCell(i);if(isTextJdbcType(col.jdbcType()))cell.setCellStyle(text);writeCell(cell,row.get(col.name()),date);}}
            if(template){for(int rr=1;rr<=200;rr++){Row row=sheet.getRow(rr);if(row==null)row=sheet.createRow(rr);for(int i=0;i<meta.columns().size();i++){ColumnMeta col=meta.columns().get(i);if(isTextJdbcType(col.jdbcType())){Cell cell=row.getCell(i);if(cell==null)cell=row.createCell(i);cell.setCellStyle(text);}}}}
            if(!meta.columns().isEmpty())sheet.setAutoFilter(new CellRangeAddress(0,Math.max(0,r-1),0,meta.columns().size()-1));
            for(int i=0;i<meta.columns().size();i++)sheet.setColumnWidth(i,Math.min(55*256,Math.max(14*256,(meta.columns().get(i).name().length()+3)*256)));
            Sheet info=wb.createSheet("INSTRUCCIONES");
            String[][] notes={{"Servicio",label(service)},{"Tabla",meta.tableName()},{"Filas actuales",String.valueOf(meta.rowCount())},{"Clave primaria",String.join(", ",meta.primaryKeys())},{"Modo APPEND","Agrega filas nuevas. Deja el ID vacío si es autogenerado."},{"Modo UPSERT","Si completas la clave primaria existente, actualiza esa fila; si no existe, agrega una nueva."},{"Celdas vacías","En UPSERT no uses el archivo para borrar datos sensibles sin revisar dependencias. Valida el resultado mostrado por el sistema."},{"Importante","Respeta exactamente los encabezados. No cambies nombres de columnas."}};
            for(int i=0;i<notes.length;i++){Row x=info.createRow(i);x.createCell(0).setCellValue(notes[i][0]);x.createCell(1).setCellValue(notes[i][1]);}
            int start=notes.length+2; Row ch=info.createRow(start);for(int i=0;i<5;i++)ch.createCell(i).setCellStyle(head);ch.getCell(0).setCellValue("COLUMNA");ch.getCell(1).setCellValue("TIPO");ch.getCell(2).setCellValue("NULO");ch.getCell(3).setCellValue("AUTO ID");ch.getCell(4).setCellValue("VALOR POR DEFECTO");
            int ri=start+1;for(ColumnMeta col:meta.columns()){Row x=info.createRow(ri++);x.createCell(0).setCellValue(col.name());x.createCell(1).setCellValue(col.typeName());x.createCell(2).setCellValue(col.nullable()?"Sí":"No");x.createCell(3).setCellValue(col.autoIncrement()?"Sí":"No");x.createCell(4).setCellValue(Objects.toString(col.defaultValue(),""));}
            for(int i=0;i<5;i++)info.setColumnWidth(i,i==1?28*256:24*256);
            wb.write(out);return out.toByteArray();
        }catch(IOException e){throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"No se pudo crear Excel",e);}
    }

    private List<Map<String,Object>> readExcel(MultipartFile file,TableMeta meta){
        try(InputStream in=file.getInputStream();Workbook wb=WorkbookFactory.create(in)){
            Sheet sheet=wb.getSheet("DATOS");if(sheet==null)sheet=wb.getSheetAt(0);
            if(sheet.getPhysicalNumberOfRows()==0)return List.of();
            Row header=sheet.getRow(sheet.getFirstRowNum());if(header==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El Excel no contiene encabezados");
            DataFormatter fmt=new DataFormatter(Locale.forLanguageTag("es-PE"));FormulaEvaluator evaluator=wb.getCreationHelper().createFormulaEvaluator();
            List<String> heads=new ArrayList<>();Set<String> allowed=new HashSet<>();meta.columns().forEach(c->allowed.add(c.name().toUpperCase(Locale.ROOT)));
            for(int i=0;i<header.getLastCellNum();i++){String h=fmt.formatCellValue(header.getCell(i),evaluator).trim();if(!h.isEmpty()&&!allowed.contains(h.toUpperCase(Locale.ROOT)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Columna desconocida: "+h);heads.add(h);}
            List<Map<String,Object>> rows=new ArrayList<>();
            Map<String,ColumnMeta> byName=new HashMap<>();meta.columns().forEach(c->byName.put(c.name().toUpperCase(Locale.ROOT),c));
            for(int r=header.getRowNum()+1;r<=sheet.getLastRowNum();r++){Row rr=sheet.getRow(r);if(rr==null)continue;Map<String,Object> row=new LinkedHashMap<>();boolean any=false;for(int c=0;c<heads.size();c++){String h=heads.get(c);if(h.isEmpty())continue;Cell cell=rr.getCell(c,Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);ColumnMeta col=byName.get(h.toUpperCase(Locale.ROOT));Object v=cellValue(cell,col,fmt,evaluator);if(v!=null&&(!(v instanceof String s)||!s.isBlank()))any=true;row.put(h,v);}if(any)rows.add(row);}
            return rows;
        }catch(ResponseStatusException e){throw e;}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"No se pudo leer el Excel: "+root(e),e);}
    }
    private Object cellValue(Cell cell,ColumnMeta column,DataFormatter fmt,FormulaEvaluator evaluator){
        if(cell==null)return null;
        // Para columnas de texto (DNI, teléfonos, códigos, etc.) se respeta el valor
        // formateado de Excel. Así un 00123456 no se convierte silenciosamente en 123456.
        if(column!=null&&isTextJdbcType(column.jdbcType())){String value=fmt.formatCellValue(cell,evaluator).trim();return value.isEmpty()?null:value;}
        CellType type=cell.getCellType()==CellType.FORMULA?evaluator.evaluateFormulaCell(cell):cell.getCellType();
        if(type==CellType.BLANK)return null;if(type==CellType.BOOLEAN)return cell.getBooleanCellValue();
        if(type==CellType.NUMERIC){if(DateUtil.isCellDateFormatted(cell)){LocalDateTime dt=cell.getLocalDateTimeCellValue();return dt.toLocalTime().equals(LocalTime.MIDNIGHT)?dt.toLocalDate().toString():dt.toString();}double d=cell.getNumericCellValue();return Math.rint(d)==d?(long)d:d;}
        return fmt.formatCellValue(cell,evaluator).trim();
    }

    private byte[] workbookForJson(String title,JsonNode data){
        try(XSSFWorkbook wb=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(data==null||data.isNull()){wb.createSheet("INFORME");}
            else if(data.isArray())writeArraySheet(wb,"INFORME",data);
            else if(data.isObject()){
                Sheet summary=wb.createSheet("RESUMEN");int row=0;
                Iterator<Map.Entry<String,JsonNode>> it=data.properties().iterator();
                while(it.hasNext()){var e=it.next();if(e.getValue().isArray()){writeArraySheet(wb,sheetName(e.getKey()),e.getValue());}else{Row r=summary.createRow(row++);r.createCell(0).setCellValue(e.getKey());r.createCell(1).setCellValue(e.getValue().isValueNode()?e.getValue().asText():e.getValue().toString());}}
                summary.setColumnWidth(0,28*256);summary.setColumnWidth(1,50*256);
            } else {Sheet s=wb.createSheet("INFORME");s.createRow(0).createCell(0).setCellValue(data.asText());}
            wb.write(out);return out.toByteArray();
        }catch(IOException e){throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"No se pudo crear el informe Excel",e);}
    }
    private void writeArraySheet(XSSFWorkbook wb,String name,JsonNode array){Sheet s=wb.createSheet(uniqueSheet(wb,name));if(array.isEmpty())return;LinkedHashSet<String> keys=new LinkedHashSet<>();array.forEach(n->{if(n.isObject())n.propertyNames().forEach(keys::add);else keys.add("valor");});Row h=s.createRow(0);CellStyle hs=headerStyle(wb);int c=0;for(String k:keys){Cell x=h.createCell(c++);x.setCellValue(k);x.setCellStyle(hs);}int r=1;for(JsonNode n:array){Row rr=s.createRow(r++);c=0;for(String k:keys){JsonNode v=n.isObject()?n.get(k):n;rr.createCell(c++).setCellValue(v==null||v.isNull()?"":v.isValueNode()?v.asText():v.toString());}}s.createFreezePane(0,1);for(int i=0;i<keys.size();i++)s.setColumnWidth(i,Math.min(50*256,22*256));}

    private boolean isTextJdbcType(int jdbcType){return Set.of(java.sql.Types.CHAR,java.sql.Types.VARCHAR,java.sql.Types.LONGVARCHAR,java.sql.Types.NCHAR,java.sql.Types.NVARCHAR,java.sql.Types.LONGNVARCHAR,java.sql.Types.CLOB,java.sql.Types.NCLOB).contains(jdbcType);}
    private CellStyle textStyle(Workbook wb){CellStyle st=wb.createCellStyle();st.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("@"));return st;}
    private CellStyle headerStyle(Workbook wb){CellStyle st=wb.createCellStyle();Font f=wb.createFont();f.setBold(true);st.setFont(f);st.setWrapText(true);return st;}
    private CellStyle dateStyle(Workbook wb){CellStyle st=wb.createCellStyle();st.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd hh:mm"));return st;}
    private void writeCell(Cell cell,Object value,CellStyle date){if(value==null)return;if(value instanceof java.math.BigDecimal b){cell.setCellValue(b.toPlainString());return;}if(value instanceof java.math.BigInteger b){cell.setCellValue(b.toString());return;}if(value instanceof Long l && Math.abs(l)>9_000_000_000_000_000L){cell.setCellValue(l.toString());return;}if(value instanceof Number n){cell.setCellValue(n.doubleValue());return;}if(value instanceof Boolean b){cell.setCellValue(b);return;}cell.setCellValue(String.valueOf(value));}
    private void put(ZipOutputStream zip,String name,byte[] bytes)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();}
    private String sheetName(String s){return s.replaceAll("[\\\\/?*\\[\\]:]","_");}
    private String uniqueSheet(Workbook wb,String base){String n=sheetName(base);if(n.length()>31)n=n.substring(0,31);String x=n;int i=2;while(wb.getSheet(x)!=null){String suffix="_"+i++;x=n.substring(0,Math.min(n.length(),31-suffix.length()))+suffix;}return x;}
    private String safe(String v){return v.replaceAll("[^a-zA-Z0-9._-]","-");}
    private String encode(String v){return java.net.URLEncoder.encode(v,java.nio.charset.StandardCharsets.UTF_8);}
    private String label(String key){return switch(key){case"auth"->"Usuarios y seguridad";case"pacientes"->"Pacientes";case"agenda"->"Agenda y recursos";case"clinica"->"Clínica";case"finanzas"->"Finanzas";case"laboratorio"->"Laboratorio";case"operaciones"->"Operaciones";case"contenido"->"Solicitudes web";case"inventario"->"Inventario";case"atenciones"->"Atenciones, tratamientos y proformas";case"encuestas"->"Satisfacción";default->key;};}

    public record ColumnMeta(String name,int jdbcType,String typeName,boolean nullable,boolean autoIncrement,boolean generated,String defaultValue){}
    public record TableMeta(String tableName,long rowCount,List<ColumnMeta> columns,List<String> primaryKeys){}
    public record ServiceCatalog(String key,String label,List<TableMeta> tables,boolean available,String error){}
    public record ReportInfo(String id,String label,String service,String path){}
    public record RowError(int row,String message){}
    public record ImportResponse(int totalRows,int inserted,int updated,int skipped,int errorCount,List<RowError> errors){}
}
