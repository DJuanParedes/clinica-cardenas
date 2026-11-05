package pe.clinicacardenas.agenda;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DentistScheduleRepository extends JpaRepository<DentistSchedule, Long> {
    List<DentistSchedule> findByDentistIdOrderByWeekdayAscStartTimeAsc(Long dentistId);
    List<DentistSchedule> findByDentistIdAndWeekdayOrderByStartTimeAsc(Long dentistId, int weekday);
    void deleteByDentistId(Long dentistId);
}
