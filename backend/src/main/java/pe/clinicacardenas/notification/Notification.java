package pe.clinicacardenas.notification;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** HU-23 / HU-24: bandeja de salida de notificaciones (patrón outbox). */
@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "patient_id")
    public Long patientId;
    @Column(name = "appointment_id")
    public Long appointmentId;
    /** RECORDATORIO, CAMBIO_CITA, CANCELACION, CODIGO_ACCESO */
    public String type;
    /** ENVIAR (inmediato), PROGRAMAR (recordatorio en el microservicio), ANULAR (cancela un recordatorio programado) */
    public String operation;
    /** WHATSAPP, EMAIL */
    public String channel;
    public String destination;
    public String subject;
    public String body;
    @Column(name = "wa_template")
    public String waTemplate;
    @Column(name = "wa_params")
    public String waParams;
    @Column(name = "idempotency_key")
    public String idempotencyKey;
    @Column(name = "scheduled_for")
    public LocalDateTime scheduledFor;
    /** PENDIENTE, PROGRAMADO, ENVIADO, FALLIDO, ANULADO, DUPLICADO */
    public String status;
    public int attempts;
    @Column(name = "next_attempt_at")
    public LocalDateTime nextAttemptAt;
    @Column(name = "last_error")
    public String lastError;
    @Column(name = "provider_id")
    public String providerId;
    @Column(name = "sent_at")
    public LocalDateTime sentAt;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
}
