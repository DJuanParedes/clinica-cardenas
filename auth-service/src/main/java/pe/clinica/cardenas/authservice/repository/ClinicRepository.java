package pe.clinica.cardenas.authservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.authservice.domain.Clinic;

import java.util.List;
import java.util.Optional;

public interface ClinicRepository extends JpaRepository<Clinic, Long> {
    Optional<Clinic> findByCodeIgnoreCase(String code);
    Optional<Clinic> findBySlugIgnoreCase(String slug);
    Optional<Clinic> findByCustomDomainIgnoreCase(String customDomain);
    List<Clinic> findAllByActiveTrueOrderByNameAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsBySlugIgnoreCase(String slug);
}
