package pe.clinicacardenas.agenda;

import java.util.EnumSet;
import java.util.Set;

public enum AppointmentStatus {
    PROGRAMADA, CONFIRMADA, EN_ESPERA, EN_ATENCION, ATENDIDA, CANCELADA, NO_ASISTIO;

    /** Estados que ocupan el horario del odontólogo. */
    public static final Set<AppointmentStatus> BLOCKING =
            EnumSet.of(PROGRAMADA, CONFIRMADA, EN_ESPERA, EN_ATENCION, ATENDIDA);

    public boolean isFinal() {
        return this == ATENDIDA || this == CANCELADA || this == NO_ASISTIO;
    }
}
