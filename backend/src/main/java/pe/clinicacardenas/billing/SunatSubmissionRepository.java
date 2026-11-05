package pe.clinicacardenas.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SunatSubmissionRepository extends JpaRepository<SunatSubmission, Long> {
    List<SunatSubmission> findByInvoiceIdOrderByAttemptAtDesc(Long invoiceId);
}
