package pe.clinicacardenas.portal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface PatientNoticeRepository extends JpaRepository<PatientNotice, Long> {
    List<PatientNotice> findTop50ByPatientIdOrderByIdDesc(Long patientId);

    long countByPatientIdAndReadAtIsNull(Long patientId);

    @Modifying
    @Query("update PatientNotice n set n.readAt = ?2 where n.patientId = ?1 and n.readAt is null")
    int markAllRead(Long patientId, LocalDateTime at);
}
