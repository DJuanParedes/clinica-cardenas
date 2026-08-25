package pe.clinica.cardenas.financeservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.financeservice.domain.DoctorTreatmentCommissionRule;

import java.util.List;
import java.util.Optional;

public interface DoctorTreatmentCommissionRuleRepository extends JpaRepository<DoctorTreatmentCommissionRule, Long> {
    List<DoctorTreatmentCommissionRule> findAllByOrderByDoctorNameAscTreatmentNameAsc();
    List<DoctorTreatmentCommissionRule> findAllByDoctorNameIgnoreCaseOrderByTreatmentNameAsc(String doctorName);
    Optional<DoctorTreatmentCommissionRule> findByDoctorNameIgnoreCaseAndTreatmentNameIgnoreCase(String doctorName, String treatmentName);
}
