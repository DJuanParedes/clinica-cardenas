package pe.clinicacardenas.care;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Cuota de un plan de tratamiento (presupuesto en cuotas). */
@Entity
@Table(name = "installments")
public class Installment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "plan_id")
    public Long planId;
    public int number;
    @Column(name = "due_date")
    public LocalDate dueDate;
    public BigDecimal amount;
    @Column(name = "reminded_at")
    public LocalDateTime remindedAt;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "created_by")
    public String createdBy;
}
