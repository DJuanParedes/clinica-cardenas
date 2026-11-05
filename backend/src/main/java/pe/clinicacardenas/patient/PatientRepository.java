package pe.clinicacardenas.patient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long>, JpaSpecificationExecutor<Patient> {
    Optional<Patient> findByDocTypeAndDocNumber(String docType, String docNumber);
    List<Patient> findByDocNumber(String docNumber);
    List<Patient> findByBirthDateAndLastNameIgnoreCase(LocalDate birthDate, String lastName);
    long countByActiveTrue();
    long countByCreatedAtGreaterThanEqual(java.time.LocalDateTime from);
}
