package pe.clinica.cardenas.appointmentservice.service;

import org.slf4j.Logger;import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import pe.clinica.cardenas.appointmentservice.domain.Appointment;
import java.util.*;

@Service
public class AuditClient {
    private static final Logger log=LoggerFactory.getLogger(AuditClient.class);
    private final RestClient client;
    public AuditClient(@Value("${services.encounters:http://localhost:8092}") String url){client=RestClient.create(url);}

    public void validateProformaLink(Long proformaId,Long patientId,String lineReference,Long currentAppointmentId,String auth){
        if(proformaId==null)return;
        if(patientId==null||lineReference==null||lineReference.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Selecciona el paciente y el tratamiento de la proforma");
        try{
            @SuppressWarnings("unchecked") Map<String,Object> base=client.get().uri("/api/proformas/{id}/billing-base",proformaId).header(HttpHeaders.AUTHORIZATION,auth).retrieve().body(Map.class);
            if(base==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"No se pudo validar la proforma");
            Long owner=number(base.get("patientId"));
            if(!Objects.equals(owner,patientId))throw new ResponseStatusException(HttpStatus.CONFLICT,"La proforma seleccionada no pertenece al paciente de la cita");
            String status=String.valueOf(base.getOrDefault("status","")).toUpperCase(Locale.ROOT);
            if(!Set.of("ACEPTADA","EN_TRATAMIENTO").contains(status))throw new ResponseStatusException(HttpStatus.CONFLICT,"La proforma no está disponible para programar tratamientos");
            Object raw=base.get("lines");Map<?,?> selected=null;
            if(raw instanceof List<?> list)for(Object item:list)if(item instanceof Map<?,?>m&&lineReference.equals(String.valueOf(m.get("lineReference")))){selected=m;break;}
            if(selected==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"El tratamiento seleccionado ya no existe en la proforma");
            String lineStatus=String.valueOf(selected.get("treatmentStatus")).toUpperCase(Locale.ROOT);
            if(Set.of("CANCELADO","REALIZADO").contains(lineStatus))throw new ResponseStatusException(HttpStatus.CONFLICT,"El tratamiento seleccionado ya está cerrado y no admite nuevas citas");
            Object pendingNoShows=selected.get("pendingNoShowAppointmentIds");
            if(pendingNoShows instanceof List<?> noShows && !noShows.isEmpty()){
                boolean resolvingCurrent=currentAppointmentId!=null&&noShows.stream().anyMatch(v->Objects.equals(number(v),currentAppointmentId));
                if(!resolvingCurrent)throw new ResponseStatusException(HttpStatus.CONFLICT,"Este tratamiento tiene una inasistencia pendiente. Primero elige Reprogramar o Cerrar esa cita antes de crear otra sesión.");
            }
            int planned=intNumber(selected.get("plannedSessions"),1);
            int scheduled=intNumber(selected.get("scheduledSessions"),0);
            boolean currentAlreadyCounts=false;Object ids=selected.get("appointmentIds");
            if(currentAppointmentId!=null&&ids instanceof List<?> list)currentAlreadyCounts=list.stream().anyMatch(v->Objects.equals(number(v),currentAppointmentId));
            if(scheduled>=planned&&!currentAlreadyCounts)throw new ResponseStatusException(HttpStatus.CONFLICT,"Ya se programaron todas las sesiones previstas para este tratamiento");
        }catch(ResponseStatusException ex){throw ex;}catch(Exception ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo validar la proforma. Verifica que el servicio de atenciones esté disponible.");}
    }
    private Long number(Object value){if(value==null)return null;try{return value instanceof Number n?n.longValue():Long.valueOf(String.valueOf(value));}catch(Exception ex){return null;}}
    private int intNumber(Object value,int fallback){if(value==null)return fallback;try{return value instanceof Number n?n.intValue():Integer.parseInt(String.valueOf(value));}catch(Exception ex){return fallback;}}

    /**
     * La atención central forma parte del mismo flujo funcional que Agenda.
     * Enviamos el snapshot de la cita para no depender de leer una transacción aún no confirmada.
     * Si la llamada remota falla, se propaga el error y la transacción local de Agenda no confirma.
     * No se afirma atomicidad distribuida entre bases; las operaciones remotas son idempotentes.
     */
    public void ensureEncounter(Appointment appointment,String auth){
        try{client.post().uri("/api/encounters/from-appointment/{id}",appointment.getId()).header(HttpHeaders.AUTHORIZATION,auth).body(appointment).retrieve().toBodilessEntity();}
        catch(Exception e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo sincronizar la cita con Atención Clínica. La operación local no se confirmará; intenta nuevamente.");}
    }
    public void syncStatus(Long appointmentId,String status,String auth){
        try{client.patch().uri("/api/encounters/by-appointment/{id}/status?value={status}",appointmentId,status).header(HttpHeaders.AUTHORIZATION,auth).retrieve().toBodilessEntity();}
        catch(Exception e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo sincronizar la asistencia con Atención Clínica. El estado anterior se conserva.");}
    }
    public void reprogramEncounter(Long oldAppointmentId,Appointment newAppointment,String auth){
        try{client.post().uri(uri->uri.path("/api/encounters/reprogram").queryParam("oldAppointmentId",oldAppointmentId).queryParam("newAppointmentId",newAppointment.getId()).build()).header(HttpHeaders.AUTHORIZATION,auth).body(newAppointment).retrieve().toBodilessEntity();}
        catch(Exception e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo trasladar la atención/proforma al nuevo horario. La reprogramación local no se confirmará; intenta nuevamente.");}
    }
    public void log(String entityId,String action,Object before,Object after,String reason,String auth){log("AGENDA","Appointment",entityId,action,before,after,reason,auth);}
    public void log(String module,String entityType,String entityId,String action,Object before,Object after,String reason,String auth){try{Map<String,Object>b=new LinkedHashMap<>();b.put("module",module);b.put("entityType",entityType);b.put("entityId",entityId);b.put("action",action);b.put("before",before);b.put("after",after);b.put("reason",reason);client.post().uri("/api/audit/events").header(HttpHeaders.AUTHORIZATION,auth).body(b).retrieve().toBodilessEntity();}catch(Exception e){log.warn("Auditoría remota no disponible: {}",e.getMessage());}}
}
