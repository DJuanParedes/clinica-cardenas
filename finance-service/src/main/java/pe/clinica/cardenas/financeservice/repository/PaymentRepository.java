package pe.clinica.cardenas.financeservice.repository;
import org.springframework.data.jpa.repository.JpaRepository; import pe.clinica.cardenas.financeservice.domain.Payment; import java.util.*;
public interface PaymentRepository extends JpaRepository<Payment,Long>{
 List<Payment> findTop300ByOrderByPaidAtDesc();
 List<Payment> findAllByPatientIdOrderByPaidAtDesc(Long patientId);
 List<Payment> findAllByProformaIdOrderByPaidAtDesc(Long proformaId);
}
