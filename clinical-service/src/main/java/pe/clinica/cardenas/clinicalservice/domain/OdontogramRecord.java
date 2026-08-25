package pe.clinica.cardenas.clinicalservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "ODONTOGRAMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OdontogramRecord extends TenantOwnedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", unique = true)
    private Long patientId;

    @Column(nullable = false, length = 120)
    private String patientName;

    /*
     * MATERIALIZED_CLOB evita exponer locators JDBC de CLOB al serializador JSON.
     * Hibernate lo materializa siempre como String en MySQL.
    */
    @JdbcTypeCode(SqlTypes.MATERIALIZED_CLOB)
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String stateJson;

    @JdbcTypeCode(SqlTypes.MATERIALIZED_CLOB)
    @Column(columnDefinition = "LONGTEXT")
    private String diagnosis;

    @JdbcTypeCode(SqlTypes.MATERIALIZED_CLOB)
    @Column(columnDefinition = "LONGTEXT")
    private String treatment;

    @Column(length = 120)
    private String updatedBy;

    private LocalDateTime updatedAt;
}
