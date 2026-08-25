package pe.clinica.cardenas.encounterservice.service;
import org.springframework.beans.factory.annotation.Value; import org.springframework.http.HttpHeaders; import org.springframework.stereotype.Service; import org.springframework.web.client.RestClient;
import java.util.*;
@Service
public class RemoteApiClient {
 private final RestClient patients; private final RestClient appointments; private final RestClient finance; private final RestClient surveys;
 public RemoteApiClient(@Value("${services.patients}") String p,@Value("${services.appointments}") String a,@Value("${services.finance}") String f,@Value("${services.surveys}") String s){patients=RestClient.create(p);appointments=RestClient.create(a);finance=RestClient.create(f);surveys=RestClient.create(s);}
 @SuppressWarnings("unchecked") public Map<String,Object> patient(Long id,String auth){return patients.get().uri("/api/patients/{id}",id).header(HttpHeaders.AUTHORIZATION,auth).retrieve().body(Map.class);}
 @SuppressWarnings("unchecked") public Map<String,Object> appointment(Long id,String auth){return appointments.get().uri("/api/appointments/{id}",id).header(HttpHeaders.AUTHORIZATION,auth).retrieve().body(Map.class);}
 @SuppressWarnings("unchecked") public java.util.List<Map<String,Object>> appointments(String auth){return appointments.get().uri("/api/appointments").header(HttpHeaders.AUTHORIZATION,auth).retrieve().body(java.util.List.class);}
 @SuppressWarnings("unchecked") public java.util.List<Map<String,Object>> patientAppointments(Long patientId,String auth){return appointments.get().uri("/api/appointments/patient/{id}",patientId).header(HttpHeaders.AUTHORIZATION,auth).retrieve().body(java.util.List.class);}
 @SuppressWarnings("unchecked") public java.util.List<Map<String,Object>> appointmentAnalytics(java.time.LocalDate from,java.time.LocalDate to,String auth){return appointments.get().uri(uriBuilder->uriBuilder.path("/api/appointments/analytics").queryParam("from",from).queryParam("to",to).build()).header(HttpHeaders.AUTHORIZATION,auth).retrieve().body(java.util.List.class);}
 public void updateAppointmentStatus(Long appointmentId,String status,String auth){appointments.patch().uri(uriBuilder->uriBuilder.path("/api/appointments/{id}/status").queryParam("value",status).queryParam("source","ENCOUNTER").build(appointmentId)).header(HttpHeaders.AUTHORIZATION,auth).retrieve().toBodilessEntity();}
 @SuppressWarnings("unchecked") public Map<String,Object> account(Long id,String auth){return finance.get().uri("/api/finance/patients/{id}/account-summary",id).header(HttpHeaders.AUTHORIZATION,auth).retrieve().body(Map.class);}
 @SuppressWarnings("unchecked") public Map<String,Object> visitPayments(Long id,String auth){return finance.get().uri("/api/finance/patients/{id}/visit-payments",id).header(HttpHeaders.AUTHORIZATION,auth).retrieve().body(Map.class);}
 @SuppressWarnings("unchecked") public Map<String,Object> proformaPayments(Long id,String auth){return finance.get().uri("/api/finance/proformas/{id}/payments-summary",id).header(HttpHeaders.AUTHORIZATION,auth).retrieve().body(Map.class);}
 @SuppressWarnings("unchecked") public java.util.List<Map<String,Object>> chairs(String auth){return appointments.get().uri("/api/scheduling/chairs").header(HttpHeaders.AUTHORIZATION,auth).retrieve().body(java.util.List.class);}
 @SuppressWarnings("unchecked") public Map<String,Object> surveySummary(String auth){return surveys.get().uri("/api/surveys/summary").header(HttpHeaders.AUTHORIZATION,auth).retrieve().body(Map.class);}
}
