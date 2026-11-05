package pe.clinicacardenas.clinical;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalHistoryRepository extends JpaRepository<ClinicalHistory, Long> {
    java.util.Optional<ClinicalHistory> findByPatientId(Long patientId);
}
