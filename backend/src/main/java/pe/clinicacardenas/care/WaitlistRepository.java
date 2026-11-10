package pe.clinicacardenas.care;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {
    List<WaitlistEntry> findByStatusInOrderByCreatedAtAsc(List<String> status);
}
