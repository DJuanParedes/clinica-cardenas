package pe.clinicacardenas.clinical;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EvolutionRepository extends JpaRepository<Evolution, Long> {
    java.util.List<Evolution> findByPlanIdOrderBySessionDateDescIdDesc(Long planId);
}
