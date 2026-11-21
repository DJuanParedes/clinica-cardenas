package pe.clinicacardenas.portal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortalCodeRepository extends JpaRepository<PortalCode, Long> {
    List<PortalCode> findByPatientIdAndUsedAtIsNullOrderByCreatedAtDesc(Long patientId);
}
