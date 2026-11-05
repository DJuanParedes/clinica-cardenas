package pe.clinicacardenas.clinical;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OdontogramEntryRepository extends JpaRepository<OdontogramEntry, Long> {
    java.util.List<OdontogramEntry> findByPatientIdOrderByToothAscFaceAsc(Long patientId);
    void deleteByPatientId(Long patientId);
}
