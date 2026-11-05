package pe.clinicacardenas.clinical;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TreatmentPlanRepository extends JpaRepository<TreatmentPlan, Long> {
    java.util.List<TreatmentPlan> findByPatientIdOrderByCreatedAtDesc(Long patientId);
    long countByStatus(String status);
}
