package pe.clinica.cardenas.surveyservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity; import jakarta.persistence.*; import lombok.*; import java.time.*;
@Entity @Table(name="SURVEY_TOKENS") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder public class SurveyToken extends TenantOwnedEntity{@Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Long id;@Column(nullable=false,unique=true,length=24)private String code;private Long encounterId;private Long patientId;@Column(length=120)private String doctorName;@Column(length=80)private String specialty;@Column(length=80)private String branch;@Column(length=60)private String chairName;private LocalDateTime expiresAt;@Column(nullable=false)private boolean used;}
