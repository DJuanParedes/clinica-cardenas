package pe.clinica.cardenas.appointmentservice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import pe.clinica.cardenas.security.DataTransferConfiguration;
import pe.clinica.cardenas.security.DataTransferController;
import pe.clinica.cardenas.security.GlobalExceptionHandler;
import pe.clinica.cardenas.security.MicroserviceSecurityConfiguration;
@SpringBootApplication
@Import({MicroserviceSecurityConfiguration.class, GlobalExceptionHandler.class, DataTransferConfiguration.class, DataTransferController.class})
public class AppointmentServiceApplication { public static void main(String[] args){SpringApplication.run(AppointmentServiceApplication.class,args);} }
