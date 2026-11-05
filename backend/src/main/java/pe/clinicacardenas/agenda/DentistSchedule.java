package pe.clinicacardenas.agenda;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;

import java.time.LocalTime;

@Entity
@Table(name = "dentist_schedules")
public class DentistSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "dentist_id")
    public Long dentistId;

    /** 1 = lunes ... 7 = domingo (ISO-8601). */
    public int weekday;

    @JsonFormat(pattern = "HH:mm")
    @Column(name = "start_time")
    public LocalTime startTime;

    @JsonFormat(pattern = "HH:mm")
    @Column(name = "end_time")
    public LocalTime endTime;

    public DentistSchedule() {}

    public DentistSchedule(Long dentistId, int weekday, LocalTime start, LocalTime end) {
        this.dentistId = dentistId;
        this.weekday = weekday;
        this.startTime = start;
        this.endTime = end;
    }
}
