package pe.clinicacardenas.clinical;

import jakarta.persistence.*;
import pe.clinicacardenas.agenda.Dentist;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "evolutions")
public class Evolution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "plan_id")
    public Long planId;
    @Column(name = "item_id")
    public Long itemId;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dentist_id")
    public Dentist dentist;
    @Column(name = "session_date")
    public LocalDate sessionDate;
    public String notes;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "created_by")
    public String createdBy;
}
