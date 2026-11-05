package pe.clinicacardenas.billing;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sunat_submissions")
public class SunatSubmission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "invoice_id")
    public Long invoiceId;
    @Column(name = "attempt_at")
    public LocalDateTime attemptAt;
    public String environment;
    public String status;
    @Column(name = "response_code")
    public String responseCode;
    @Column(name = "response_description")
    public String responseDescription;
    @Column(name = "duration_ms")
    public Integer durationMs;
    @Column(name = "created_by")
    public String createdBy;
}
