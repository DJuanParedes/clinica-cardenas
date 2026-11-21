package pe.clinicacardenas.portal;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyRepository extends JpaRepository<Survey, Long> {
    boolean existsByAppointmentId(Long appointmentId);
}
