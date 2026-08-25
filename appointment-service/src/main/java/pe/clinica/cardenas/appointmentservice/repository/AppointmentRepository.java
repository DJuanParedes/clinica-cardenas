package pe.clinica.cardenas.appointmentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.appointmentservice.domain.Appointment;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment,Long> {
    List<Appointment> findAllByOrderByStartAtAsc();
    List<Appointment> findAllByPatientIdOrderByStartAtDesc(Long patientId);
}
