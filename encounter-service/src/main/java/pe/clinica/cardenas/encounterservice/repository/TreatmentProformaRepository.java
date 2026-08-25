package pe.clinica.cardenas.encounterservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.encounterservice.domain.TreatmentProforma;
import java.util.List;

public interface TreatmentProformaRepository extends JpaRepository<TreatmentProforma,Long> {
    List<TreatmentProforma> findTop300ByOrderByUpdatedAtDesc();
    List<TreatmentProforma> findAllByPatientIdOrderByUpdatedAtDesc(Long patientId);
    List<TreatmentProforma> findAllByAppointmentIdOrderByUpdatedAtDesc(Long appointmentId);
}
