package pe.clinica.cardenas.encounterservice.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.encounterservice.domain.ClinicalEncounter;
public interface ClinicalEncounterRepository extends JpaRepository<ClinicalEncounter,Long> {
    java.util.List<ClinicalEncounter> findAllByPatientIdOrderByScheduledStartDesc(Long patientId);
    java.util.Optional<ClinicalEncounter> findByAppointmentId(Long appointmentId);
    java.util.List<ClinicalEncounter> findAllByScheduledStartBetweenOrderByScheduledStartAsc(java.time.LocalDateTime from, java.time.LocalDateTime to);
}
