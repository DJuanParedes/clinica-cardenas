package pe.clinica.cardenas.authservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * El perfil local reutiliza BootstrapAdminInitializer. Esta clase permanece
 * únicamente por compatibilidad con paquetes anteriores y nunca contiene
 * identidades, teléfonos ni contraseñas.
 */
@Configuration
@Profile("local")
public class LocalDataInitializer {
}