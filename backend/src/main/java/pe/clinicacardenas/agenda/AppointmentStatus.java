package pe.clinicacardenas.agenda;

import java.util.EnumSet;
import java.util.Set;

public enum AppointmentStatus {
    PROGRAMADA, CONFIRMADA, EN_ESPERA, EN_ATENCION, ATENDIDA, CANCELADA, NO_ASISTIO;

    /** Estados que ocupan el horario del odontólogo. */
    public static final Set<AppointmentStatus> BLOCKING =
            java.util.Collections.unmodifiableSet(EnumSet.of(PROGRAMADA, CONFIRMADA, EN_ESPERA, EN_ATENCION, ATENDIDA));

    /** Citas que todavía deben atenderse (ocupan horario y no se han cerrado). */
    public static final Set<AppointmentStatus> PENDING = java.util.Collections.unmodifiableSet(EnumSet.of(PROGRAMADA, CONFIRMADA, EN_ESPERA, EN_ATENCION));

    public boolean isFinal() {
        return this == ATENDIDA || this == CANCELADA || this == NO_ASISTIO;
    }
}
