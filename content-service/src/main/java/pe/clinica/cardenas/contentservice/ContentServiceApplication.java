package pe.clinica.cardenas.contentservice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import pe.clinica.cardenas.security.DataTransferConfiguration;
import pe.clinica.cardenas.security.DataTransferController;
import pe.clinica.cardenas.security.GlobalExceptionHandler;
import pe.clinica.cardenas.security.MicroserviceSecurityConfiguration;
@SpringBootApplication
@Import({MicroserviceSecurityConfiguration.class, GlobalExceptionHandler.class, DataTransferConfiguration.class, DataTransferController.class})
public class ContentServiceApplication { public static void main(String[] args){SpringApplication.run(ContentServiceApplication.class,args);} }
