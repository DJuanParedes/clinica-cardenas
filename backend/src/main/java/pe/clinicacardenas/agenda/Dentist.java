package pe.clinicacardenas.agenda;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import pe.clinicacardenas.user.User;

@Entity
@Table(name = "dentists")
public class Dentist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties({"failedAttempts", "lockedUntil", "lastLoginAt", "createdAt", "createdBy", "updatedAt", "updatedBy", "email"})
    public User user;

    public String specialty;
    public String cop;
    public String color;
    public boolean active = true;

    public String getFullName() {
        return user == null ? null : user.fullName;
    }
}
