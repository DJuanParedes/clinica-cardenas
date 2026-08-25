package pe.clinica.cardenas.encounterservice.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.encounterservice.domain.ChairGoal;
public interface ChairGoalRepository extends JpaRepository<ChairGoal,Long> {
    java.util.List<ChairGoal> findAllByOrderByPeriodStartDesc();
}
