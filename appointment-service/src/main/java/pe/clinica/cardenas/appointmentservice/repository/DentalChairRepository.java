package pe.clinica.cardenas.appointmentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.appointmentservice.domain.DentalChair;

import java.util.List;
import java.util.Optional;

public interface DentalChairRepository extends JpaRepository<DentalChair, Long> {
    List<DentalChair> findAllByOrderByNameAsc();
    Optional<DentalChair> findByNameIgnoreCase(String name);
}
