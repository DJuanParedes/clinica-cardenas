package pe.clinica.cardenas.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DataTransferConfiguration {
    @Bean
    DataTransferService dataTransferService(JdbcTemplate jdbcTemplate) {
        return new DataTransferService(jdbcTemplate);
    }
}
