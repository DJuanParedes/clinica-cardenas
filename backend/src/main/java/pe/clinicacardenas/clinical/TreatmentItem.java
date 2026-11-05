package pe.clinicacardenas.clinical;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "treatment_items")
public class TreatmentItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "plan_id")
    public Long planId;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "procedure_id")
    public Procedure procedure;
    public Integer tooth;
    public BigDecimal price;
    /** PENDIENTE, EN_PROCESO, REALIZADO */
    public String status;
    @Column(name = "done_at")
    public LocalDateTime doneAt;
    @Column(name = "invoice_id")
    public Long invoiceId;
    @Column(name = "sort_order")
    public int sortOrder;
    /** Odontólogo que realizó el procedimiento (base para comisiones, HU-17). */
    @Column(name = "done_by_dentist_id")
    public Long doneByDentistId;
}
