package pe.clinica.cardenas.encounterservice.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.encounterservice.domain.BackupVerification;
public interface BackupVerificationRepository extends JpaRepository<BackupVerification,Long> {
    java.util.List<BackupVerification> findAllByOrderByCompletedAtDesc();
}
