package pe.clinicacardenas.user;

public enum Role {
    ADMIN("Administrador"),
    RECEPCION("Recepcionista"),
    ODONTOLOGO("Odontólogo"),
    CAJA("Responsable de caja"),
    DIRECTOR("Director de la clínica");

    public final String label;

    Role(String label) {
        this.label = label;
    }
}
