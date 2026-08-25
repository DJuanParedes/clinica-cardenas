package pe.clinica.cardenas.backupservice;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix="backup")
public class BackupProperties {
    private Map<String,String> services = new LinkedHashMap<>();
    public Map<String,String> getServices(){ return services; }
    public void setServices(Map<String,String> services){ this.services=services; }
}
