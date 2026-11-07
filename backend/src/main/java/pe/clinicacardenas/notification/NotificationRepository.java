package pe.clinicacardenas.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    List<Notification> findByAppointmentIdAndOperationAndStatusIn(Long appointmentId, String operation, List<String> statuses);

    List<Notification> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    List<Notification> findAllByOrderByCreatedAtDesc(Pageable page);

    long countByStatus(String status);
}
