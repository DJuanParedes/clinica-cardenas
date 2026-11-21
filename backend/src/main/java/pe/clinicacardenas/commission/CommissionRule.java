package pe.clinicacardenas.commission;

import jakarta.persistence.*;
import pe.clinicacardenas.agenda.Dentist;
import pe.clinicacardenas.clinical.Procedure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** HU-16: regla de comisión acordada con un odontólogo, con su vigencia. */
@Entity
@Table(name = "commission_rules")
public class CommissionRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "dentist_id")
    public Dentist dentist;
    /** Si es null, la regla aplica a todos los procedimientos del odontólogo. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "procedure_id")
    public Procedure procedure;
    /** PORCENTAJE o MONTO_FIJO */
    @Column(name = "calc_type")
    public String calcType;
    public BigDecimal value;
    /** REALIZADO: sobre el precio del procedimiento · COBRADO: sobre lo efectivamente pagado */
    public String base;
    @Column(name = "valid_from")
    public LocalDate validFrom;
    @Column(name = "valid_to")
    public LocalDate validTo;
    public boolean active = true;
    public String notes;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "created_by")
    public String createdBy;
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
    @Column(name = "updated_by")
    public String updatedBy;

    public boolean appliesOn(LocalDate d) {
        return active && !d.isBefore(validFrom) && (validTo == null || !d.isAfter(validTo));
    }

    public String getLabel() {
        String v = "PORCENTAJE".equals(calcType) ? value.stripTrailingZeros().toPlainString() + " %" : "S/ " + value.toPlainString() + " fijo";
        String b = "COBRADO".equals(base) ? "de lo cobrado" : "del precio";
        return v + ("PORCENTAJE".equals(calcType) ? " " + b : "") + (procedure == null ? " · todos los procedimientos" : " · " + procedure.name);
    }
}
