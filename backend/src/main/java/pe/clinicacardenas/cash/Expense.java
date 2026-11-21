package pe.clinicacardenas.cash;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** HU-15: egreso de caja con su concepto. */
@Entity
@Table(name = "expenses")
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "expense_date")
    public LocalDate expenseDate;
    public String category;
    public String concept;
    public BigDecimal amount;
    public String method;
    public String reference;
    public String supplier;
    /** VALIDO, ANULADO */
    public String status;
    @Column(name = "void_reason")
    public String voidReason;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "created_by")
    public String createdBy;
}
