package pe.clinica.cardenas.clinicalservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.clinicalservice.domain.OdontogramRecord;

import java.util.Optional;

public interface OdontogramRecordRepository extends JpaRepository<OdontogramRecord, Long> {
    Optional<OdontogramRecord> findByPatientId(Long patientId);
    Optional<OdontogramRecord> findFirstByPatientNameIgnoreCaseOrderByUpdatedAtDesc(String patientName);
}
