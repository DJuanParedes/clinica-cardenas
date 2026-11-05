package pe.clinicacardenas.agenda;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DentistRepository extends JpaRepository<Dentist, Long> {
    Optional<Dentist> findByUserId(Long userId);

    @Query("select d from Dentist d where d.active = true and d.user.active = true order by d.user.fullName")
    List<Dentist> findAvailable();

    /** Bloqueo de fila: serializa la reserva de citas del mismo odontólogo y evita cruces por concurrencia. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dentist d where d.id = :id")
    Optional<Dentist> lockById(@Param("id") Long id);
}
