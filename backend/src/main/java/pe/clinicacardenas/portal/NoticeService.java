package pe.clinicacardenas.portal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.agenda.Appointment;
import pe.clinicacardenas.agenda.AppointmentEvent;
import pe.clinicacardenas.common.Dates;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Avisos para la app del paciente. Cada cambio importante de su cita deja un aviso en su bandeja; la app los
 * consulta al abrirse y en segundo plano, y los muestra como notificaciones del teléfono (push simulado).
 */
@Service
public class NoticeService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, h:mm a", new Locale("es", "PE"));
    private final PatientNoticeRepository notices;

    public NoticeService(PatientNoticeRepository notices) {
        this.notices = notices;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void add(Long patientId, String kind, String title, String body, Long refId) {
        PatientNotice n = new PatientNotice();
        n.patientId = patientId;
        n.kind = kind;
        n.title = title.length() > 150 ? title.substring(0, 150) : title;
        n.body = body.length() > 500 ? body.substring(0, 500) : body;
        n.refId = refId;
        n.createdAt = Dates.now();
        notices.save(n);
    }

    private static String when(Appointment a) {
        return a.startAt.format(WHEN).replace("a. m.", "a. m.").replace("p. m.", "p. m.");
    }

    @EventListener
    public void on(AppointmentEvent e) {
        switch (e.type()) {
            case CREATED -> appointmentCreated(e.appointment());
            case RESCHEDULED -> appointmentRescheduled(e.appointment());
            case STATUS -> appointmentStatus(e.appointment());
        }
    }

    public void appointmentCreated(Appointment a) {
        add(a.patient.id, "CITA_CREADA", "Cita reservada",
                "Tu cita de " + a.reason.toLowerCase() + " con " + a.dentist.getFullName() + " es el " + when(a) + ".", a.id);
    }

    public void appointmentRescheduled(Appointment a) {
        add(a.patient.id, "CITA_REPROGRAMADA", "Cita reprogramada",
                "Tu cita ahora es el " + when(a) + " con " + a.dentist.getFullName() + ".", a.id);
    }

    public void appointmentStatus(Appointment a) {
        switch (a.status) {
            case CONFIRMADA -> add(a.patient.id, "CITA_CONFIRMADA", "Cita confirmada",
                    "Te esperamos el " + when(a) + ". Llega 10 minutos antes.", a.id);
            case CANCELADA -> add(a.patient.id, "CITA_CANCELADA", "Cita cancelada",
                    "Se canceló tu cita del " + when(a) + (a.statusReason == null ? "." : ": " + a.statusReason + "."), a.id);
            case ATENDIDA -> add(a.patient.id, "ENCUESTA", "¿Cómo te atendimos?",
                    "Califica tu cita con " + a.dentist.getFullName() + ". Nos ayuda a mejorar.", a.id);
            default -> { }
        }
    }
}
