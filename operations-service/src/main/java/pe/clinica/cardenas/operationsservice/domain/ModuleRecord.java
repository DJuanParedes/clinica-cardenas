package pe.clinica.cardenas.operationsservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "MODULE_RECORDS", indexes = @Index(name = "IDX_MODULE_RECORD_KEY", columnList = "MODULE_KEY"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ModuleRecord extends TenantOwnedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "MODULE_KEY", nullable = false, length = 60)
    private String moduleKey;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(length = 180)
    private String subject;

    @Column(length = 1200)
    private String detail;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(length = 140)
    private String responsible;

    private LocalDate eventDate;

    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String payloadJson;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
