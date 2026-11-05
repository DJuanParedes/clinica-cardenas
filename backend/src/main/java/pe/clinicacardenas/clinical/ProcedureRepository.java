package pe.clinicacardenas.clinical;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcedureRepository extends JpaRepository<Procedure, Long> {
    java.util.List<Procedure> findByActiveTrueOrderByCategoryAscNameAsc();
}
