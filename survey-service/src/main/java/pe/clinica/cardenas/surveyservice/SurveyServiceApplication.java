package pe.clinica.cardenas.surveyservice; import org.springframework.boot.*; import org.springframework.boot.autoconfigure.*;
@SpringBootApplication(scanBasePackages={"pe.clinica.cardenas.surveyservice","pe.clinica.cardenas.security"}) public class SurveyServiceApplication{public static void main(String[]a){SpringApplication.run(SurveyServiceApplication.class,a);}}
