package pe.clinicacardenas.billing;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "invoice_items")
public class InvoiceItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "invoice_id")
    public Long invoiceId;
    @Column(name = "line_number")
    public int lineNumber;
    public String description;
    public BigDecimal quantity;
    @Column(name = "unit_price")
    public BigDecimal unitPrice;
    @Column(name = "unit_value")
    public BigDecimal unitValue;
    @Column(name = "line_value")
    public BigDecimal lineValue;
    @Column(name = "line_igv")
    public BigDecimal lineIgv;
    @Column(name = "line_total")
    public BigDecimal lineTotal;
    @Column(name = "treatment_item_id")
    public Long treatmentItemId;
}
