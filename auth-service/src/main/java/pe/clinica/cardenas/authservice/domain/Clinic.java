package pe.clinica.cardenas.authservice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "CLINICS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Clinic {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 40)
    private String code;
    @Column(nullable = false, unique = true, length = 60)
    private String slug;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 80)
    private String shortName;
    @Column(length = 160)
    private String tagline;
    @Column(length = 600)
    private String description;
    @Column(length = 300)
    private String logoUrl;
    @Column(length = 300)
    private String heroImageUrl;
    @Column(nullable = false, length = 9)
    private String primaryColor;
    @Column(nullable = false, length = 9)
    private String primaryDarkColor;
    @Column(nullable = false, length = 9)
    private String accentColor;
    @Column(nullable = false, length = 9)
    private String accentDarkColor;
    @Column(nullable = false, length = 9)
    private String sidebarDarkColor;
    @Column(length = 30)
    private String phone;
    @Column(length = 160)
    private String email;
    @Column(length = 220)
    private String address;
    @Column(length = 160)
    private String workingHours;
    @Column(length = 160)
    private String customDomain;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
