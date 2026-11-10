package pe.clinicacardenas.agenda;

/** Se publica cuando una cita se crea, se reprograma o cambia de estado (avisos en la app, lista de espera). */
public record AppointmentEvent(Appointment appointment, Type type) {
    public enum Type { CREATED, RESCHEDULED, STATUS }
}
