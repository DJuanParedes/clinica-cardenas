package pe.clinicacardenas.billing;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "invoices")
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    /** 01 factura, 03 boleta */
    @Column(name = "doc_type")
    public String docType;
    public String series;
    public int number;
    @Column(name = "issue_date")
    public LocalDate issueDate;
    @JsonFormat(pattern = "HH:mm:ss")
    @Column(name = "issue_time")
    public LocalTime issueTime;
    @Column(name = "patient_id")
    public Long patientId;
    @Column(name = "customer_doc_type")
    public String customerDocType;
    @Column(name = "customer_doc_number")
    public String customerDocNumber;
    @Column(name = "customer_name")
    public String customerName;
    @Column(name = "customer_address")
    public String customerAddress;
    public String currency = "PEN";
    @Column(name = "taxable_amount")
    public BigDecimal taxableAmount;
    @Column(name = "igv_amount")
    public BigDecimal igvAmount;
    @Column(name = "total_amount")
    public BigDecimal totalAmount;
    /** GENERADO, ACEPTADO, OBSERVADO, RECHAZADO, ERROR */
    public String status;
    @JsonIgnore
    @Column(name = "xml_content")
    public String xmlContent;
    @Column(name = "hash_code")
    public String hash;
    @Column(name = "sunat_code")
    public String sunatCode;
    @Column(name = "sunat_description")
    public String sunatDescription;
    @JsonIgnore
    @Column(name = "cdr_content")
    public String cdrContent;
    @Column(name = "sent_at")
    public LocalDateTime sentAt;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "created_by")
    public String createdBy;

    public String getFullNumber() {
        return series + "-" + String.format("%08d", number);
    }

    public String getDocTypeLabel() {
        return "01".equals(docType) ? "Factura electrónica" : "Boleta de venta electrónica";
    }

    public boolean isHasCdr() {
        return cdrContent != null;
    }
}
