package pe.clinica.cardenas.encounterservice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages={"pe.clinica.cardenas.encounterservice","pe.clinica.cardenas.security"})
public class EncounterServiceApplication { public static void main(String[] args){SpringApplication.run(EncounterServiceApplication.class,args);} }
