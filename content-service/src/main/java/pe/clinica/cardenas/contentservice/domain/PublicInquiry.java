package pe.clinica.cardenas.contentservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "public_inquiries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PublicInquiry extends TenantOwnedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank private String fullName;
    @NotBlank @Pattern(regexp = "\\d{9}", message = "debe contener 9 dígitos")
    private String phone;
    @NotBlank private String specialty;
    @Column(length = 1200) private String message;
    private LocalDateTime createdAt;
    private String status;
    private String assignedTo;
}
