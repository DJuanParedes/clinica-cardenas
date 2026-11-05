package pe.clinicacardenas.clinical;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosisRepository extends JpaRepository<Diagnosis, Long> {
    java.util.List<Diagnosis> findByPatientIdOrderByCreatedAtDesc(Long patientId);
}
