package pe.clinicacardenas.billing;

import jakarta.persistence.*;

@Entity
@Table(name = "invoice_series")
public class InvoiceSeries {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "doc_type")
    public String docType;
    public String series;
    @Column(name = "last_number")
    public int lastNumber;
    public boolean active = true;
}
