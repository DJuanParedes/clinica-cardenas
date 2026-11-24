package pe.clinicacardenas.cash;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByPatientIdOrderByPaidAtDesc(Long patientId);

    @Query("select p from Payment p where p.paidAt >= :from and p.paidAt < :to order by p.paidAt desc")
    List<Payment> findInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.treatmentItemId = :itemId and p.status = 'VALIDO'")
    BigDecimal paidForItem(@Param("itemId") Long itemId);
}
