package pe.clinica.cardenas.contentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.contentservice.domain.PublicInquiry;
import java.util.List;

public interface PublicInquiryRepository extends JpaRepository<PublicInquiry, Long> {
    List<PublicInquiry> findAllByOrderByCreatedAtDesc();
}
