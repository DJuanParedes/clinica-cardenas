package pe.clinicacardenas.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {
    List<Invoice> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    @Query("select coalesce(sum(i.totalAmount), 0) from Invoice i where i.issueDate between :from and :to and i.status <> 'RECHAZADO'")
    BigDecimal sumTotal(@Param("from") LocalDate from, @Param("to") LocalDate to);

    long countByStatus(String status);
}
