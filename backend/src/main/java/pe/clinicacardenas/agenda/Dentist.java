package pe.clinicacardenas.agenda;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import pe.clinicacardenas.files.ImageService;
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

    @JsonIgnore
    @Column(name = "photo_path")
    public String photoPath;

    public String getPhotoUrl() {
        return ImageService.url(photoPath);
    }

    public String getFullName() {
        return user == null ? null : user.fullName;
    }
}
