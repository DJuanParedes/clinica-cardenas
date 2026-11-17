package pe.clinicacardenas.patient;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** Bloqueo de fila: dos citas simultáneas del mismo paciente (con distintos odontólogos) no pueden cruzarse. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Patient p where p.id = :id")
    Optional<Patient> lockById(@Param("id") Long id);
}
