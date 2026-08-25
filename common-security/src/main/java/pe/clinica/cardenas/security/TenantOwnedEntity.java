package pe.clinica.cardenas.security;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.TenantId;

/**
 * Base para toda entidad que pertenece a una clínica.
 * Hibernate agrega el predicado clinic_id a consultas, actualizaciones y eliminaciones.
 */
@MappedSuperclass
public abstract class TenantOwnedEntity {
    @TenantId
    @Column(name = "clinic_id", nullable = false, length = 40,
            columnDefinition = "varchar(40) default 'CARDENAS'")
    private String clinicId;

    public String getClinicId() { return clinicId; }
    public void setClinicId(String clinicId) { this.clinicId = TenantContext.normalize(clinicId); }
}
