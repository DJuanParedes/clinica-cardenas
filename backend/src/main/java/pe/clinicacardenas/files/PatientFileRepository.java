package pe.clinicacardenas.files;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientFileRepository extends JpaRepository<PatientFile, Long> {
    List<PatientFile> findByPatientIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long patientId);

    List<PatientFile> findByPatientIdAndSharedWithPatientTrueAndDeletedAtIsNullOrderByCreatedAtDesc(Long patientId);
}
