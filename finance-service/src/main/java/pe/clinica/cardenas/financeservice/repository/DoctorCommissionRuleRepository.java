package pe.clinica.cardenas.financeservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.financeservice.domain.DoctorCommissionRule;

import java.util.List;
import java.util.Optional;

public interface DoctorCommissionRuleRepository extends JpaRepository<DoctorCommissionRule, Long> {
    List<DoctorCommissionRule> findAllByOrderByDoctorNameAsc();
    Optional<DoctorCommissionRule> findByDoctorNameIgnoreCase(String doctorName);
}
