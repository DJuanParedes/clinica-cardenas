package pe.clinica.cardenas.clinicalservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Migra instalaciones anteriores donde patient_name era único. El odontograma
 * moderno se identifica por patient_id; dos pacientes pueden compartir nombre.
 */
@Component
@RequiredArgsConstructor
public class OdontogramSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        List<String> indexes = jdbc.queryForList("""
                SELECT DISTINCT INDEX_NAME
                  FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'odontograms'
                   AND COLUMN_NAME = 'patient_name'
                   AND NON_UNIQUE = 0
                   AND INDEX_NAME <> 'PRIMARY'
                """, String.class);
        for (String index : indexes) {
            if (!index.matches("[A-Za-z0-9_$]+")) {
                throw new IllegalStateException("Índice MySQL inesperado en odontograms: " + index);
            }
            jdbc.execute("ALTER TABLE odontograms DROP INDEX `" + index + "`");
        }
    }
}
