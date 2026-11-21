package pe.clinicacardenas.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Envío asíncrono de notificaciones y reintentos programados (HU-23 / HU-24). */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
