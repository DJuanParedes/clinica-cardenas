package pe.clinica.cardenas.appointmentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.appointmentservice.domain.DoctorProfile;

import java.util.List;
import java.util.Optional;

public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, Long> {
    List<DoctorProfile> findAllByOrderByFullNameAsc();
    Optional<DoctorProfile> findByFullNameIgnoreCase(String fullName);
    Optional<DoctorProfile> findByAccountEmailIgnoreCase(String accountEmail);
}
