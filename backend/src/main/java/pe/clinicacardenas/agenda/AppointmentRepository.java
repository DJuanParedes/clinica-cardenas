package pe.clinicacardenas.agenda;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Query("""
           select a from Appointment a
           where a.dentist.id = :dentistId and a.status in :statuses
             and a.startAt < :end and a.endAt > :start
             and (:excludeId is null or a.id <> :excludeId)
           """)
    List<Appointment> findDentistOverlaps(@Param("dentistId") Long dentistId,
                                          @Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end,
                                          @Param("statuses") Collection<AppointmentStatus> statuses,
                                          @Param("excludeId") Long excludeId);

    @Query("""
           select a from Appointment a
           where a.patient.id = :patientId and a.status in :statuses
             and a.startAt < :end and a.endAt > :start
             and (:excludeId is null or a.id <> :excludeId)
           """)
    List<Appointment> findPatientOverlaps(@Param("patientId") Long patientId,
                                          @Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end,
                                          @Param("statuses") Collection<AppointmentStatus> statuses,
                                          @Param("excludeId") Long excludeId);

    @Query("""
           select a from Appointment a
           where a.startAt >= :from and a.startAt < :to
             and (:dentistId is null or a.dentist.id = :dentistId)
           order by a.startAt asc
           """)
    List<Appointment> findInRange(@Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to,
                                  @Param("dentistId") Long dentistId);

    List<Appointment> findByPatientIdOrderByStartAtDesc(Long patientId);
}
