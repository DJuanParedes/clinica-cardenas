package pe.clinicacardenas.notification;

import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Optional<Notification> findByIdempotencyKey(String key);

    boolean existsByIdempotencyKey(String key);

    @Query("""
           select n from Notification n
            where n.status = 'PENDIENTE' and (n.nextAttemptAt is null or n.nextAttemptAt <= :now)
            order by n.id
           """)
    List<Notification> findDue(@Param("now") LocalDateTime now, Pageable page);

    /** Bloquea las filas: así la anulación no se cruza con un envío en curso del despachador. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Notification> findByAppointmentIdAndOperationAndStatusIn(Long appointmentId, String operation, List<String> statuses);

    List<Notification> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    List<Notification> findAllByOrderByCreatedAtDesc(Pageable page);

    long countByStatus(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notification n where n.id = :id")
    Optional<Notification> lockById(@Param("id") Long id);
}
