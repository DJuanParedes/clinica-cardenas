package pe.clinicacardenas.portal;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientAccountRepository extends JpaRepository<PatientAccount, Long> {
}
