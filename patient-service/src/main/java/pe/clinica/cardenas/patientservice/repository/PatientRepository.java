package pe.clinica.cardenas.patientservice.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.clinica.cardenas.patientservice.domain.Patient;
import java.util.*;
public interface PatientRepository extends JpaRepository<Patient,Long>{
 boolean existsByDni(String dni); Optional<Patient> findByDni(String dni);
 @Query("select p from Patient p where lower(p.fullName) like lower(concat('%',:term,'%')) or p.dni like concat('%',:term,'%') or p.phone like concat('%',:term,'%') order by p.fullName")
 List<Patient> search(@Param("term") String term);
}
