package pe.clinica.cardenas.security;

import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Locale;

/** Configuración de multitenencia por discriminador para los servicios JPA. */
@Configuration
@ConditionalOnClass(CurrentTenantIdentifierResolver.class)
public class TenantPersistenceConfiguration {
    @Bean
    CurrentTenantIdentifierResolver<String> clinicTenantIdentifierResolver() {
        return new CurrentTenantIdentifierResolver<>() {
            @Override public String resolveCurrentTenantIdentifier() { return TenantContext.currentClinicId(); }
            @Override public boolean validateExistingCurrentSessions() { return true; }
        };
    }

    @Bean
    HibernatePropertiesCustomizer clinicTenantHibernateCustomizer(CurrentTenantIdentifierResolver<String> resolver) {
        return properties -> properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }

    /**
     * Migra instalaciones anteriores sin perder datos: asigna CARDENAS a las filas
     * existentes, indexa clinic_id y convierte unicidades globales en unicidades
     * dentro de cada clínica.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    ApplicationRunner clinicTenantSchemaMigration(ObjectProvider<JdbcTemplate> jdbcProvider) {
        return new ApplicationRunner() {
            @Override public void run(ApplicationArguments args) {
                JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
                if (jdbc == null) return;
                String schema = jdbc.queryForObject("SELECT DATABASE()", String.class);
                if (schema == null || schema.isBlank()) return;
                List<String> tables = jdbc.queryForList(
                        "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=? AND LOWER(COLUMN_NAME)='clinic_id'",
                        String.class, schema);
                for (String table : tables) migrateTable(jdbc, schema, table);
            }

            private void migrateTable(JdbcTemplate jdbc, String schema, String table) {
                jdbc.update("UPDATE " + q(table) + " SET clinic_id=? WHERE clinic_id IS NULL OR TRIM(clinic_id)=''",
                        TenantContext.DEFAULT_CLINIC);
                Integer tenantIndex = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND LOWER(COLUMN_NAME)='clinic_id'",
                        Integer.class, schema, table);
                if (tenantIndex == null || tenantIndex == 0) {
                    jdbc.execute("CREATE INDEX " + q(indexName("IX_TENANT", table)) + " ON " + q(table) + " (clinic_id)");
                }
                List<UniqueIndex> indexes = jdbc.query(
                        "SELECT INDEX_NAME,GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') COLS " +
                                "FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND NON_UNIQUE=0 AND INDEX_NAME<>'PRIMARY' " +
                                "GROUP BY INDEX_NAME",
                        (rs, row) -> new UniqueIndex(rs.getString("INDEX_NAME"), rs.getString("COLS")), schema, table);
                for (UniqueIndex index : indexes) {
                    List<String> columns = List.of(index.columns().split(","));
                    if (columns.stream().anyMatch(c -> c.equalsIgnoreCase("clinic_id"))) continue;
                    String scopedName = indexName("UQ_TENANT", table + "_" + index.name());
                    String scopedColumns = "clinic_id," + columns.stream().map(String::trim).map(TenantPersistenceConfiguration::q).reduce((a,b) -> a+","+b).orElse("");
                    jdbc.execute("ALTER TABLE " + q(table) + " DROP INDEX " + q(index.name()));
                    Integer scopedIndexExists = jdbc.queryForObject(
                            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND INDEX_NAME=?",
                            Integer.class, schema, table, scopedName);
                    if (scopedIndexExists == null || scopedIndexExists == 0) {
                        jdbc.execute("ALTER TABLE " + q(table) + " ADD UNIQUE INDEX " + q(scopedName) + " (" + scopedColumns + ")");
                    }
                }
            }
        };
    }

    private static String indexName(String prefix, String source) {
        String clean = source.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        String hash = Integer.toUnsignedString(source.hashCode(), 36).toUpperCase(Locale.ROOT);
        int maxBase = Math.max(8, 60 - prefix.length() - hash.length());
        if (clean.length() > maxBase) clean = clean.substring(0, maxBase);
        return prefix + "_" + clean + "_" + hash;
    }

    private static String q(String identifier) { return "`" + identifier.replace("`", "``") + "`"; }
    private record UniqueIndex(String name, String columns) {}
}
