package pe.clinicacardenas.patient;

import jakarta.persistence.*;
import pe.clinicacardenas.common.Dates;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

@Entity
@Table(name = "patients")
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "doc_type")
    public String docType;
    @Column(name = "doc_number")
    public String docNumber;
    @Column(name = "first_name")
    public String firstName;
    @Column(name = "last_name")
    public String lastName;
    @Column(name = "birth_date")
    public LocalDate birthDate;
    public String sex;
    public String phone;
    public String email;
    public String address;
    public String district;
    /** Código UBIGEO (INEI) del distrito y nombres de su departamento y provincia. */
    public String ubigeo;
    public String department;
    public String province;
    @Column(name = "emergency_contact_relationship")
    public String emergencyContactRelationship;
    /** Menores de 18 años: representante legal (padre, madre o tutor) que decide y firma por el paciente. */
    @Column(name = "guardian_name")
    public String guardianName;
    @Column(name = "guardian_doc_type")
    public String guardianDocType;
    @Column(name = "guardian_doc_number")
    public String guardianDocNumber;
    @Column(name = "guardian_relationship")
    public String guardianRelationship;
    @Column(name = "guardian_phone")
    public String guardianPhone;
    @Column(name = "consent_version")
    public String consentVersion;
    @Column(name = "consent_channel")
    public String consentChannel;
    @Column(name = "consent_by")
    public String consentBy;
    @Column(name = "consent_file_id")
    public Long consentFileId;
    @Column(name = "emergency_contact_name")
    public String emergencyContactName;
    @Column(name = "emergency_contact_phone")
    public String emergencyContactPhone;
    public String notes;
    /** v2.5: de dónde llegó el paciente (RECOMENDACION, FACEBOOK…) y detalle (quién lo recomendó, qué campaña). */
    public String source;
    @Column(name = "source_detail")
    public String sourceDetail;
    /** N° de historia clínica: se asigna solo (000123) y se puede cambiar por el número de la ficha en papel. */
    @Column(name = "hc_number")
    public String hcNumber;
    /** Datos para factura: RUC, razón social y dirección fiscal (persona con negocio o empresa que paga). */
    @Column(name = "fiscal_ruc")
    public String fiscalRuc;
    @Column(name = "fiscal_name")
    public String fiscalName;
    @Column(name = "fiscal_address")
    public String fiscalAddress;
    public boolean active = true;
    /** HU-23: canal preferido para avisos (WHATSAPP, EMAIL, AMBOS, NINGUNO). */
    @Column(name = "notify_channel")
    public String notifyChannel = "WHATSAPP";

    /** Consentimiento para el tratamiento de datos personales (Ley N.° 29733). */
    @Column(name = "data_consent")
    public boolean dataConsent;
    @Column(name = "data_consent_at")
    public LocalDateTime dataConsentAt;

    /** Paciente de ejemplo creado al instalar (no recibe mensajes reales). */
    @Column(name = "demo")
    public boolean demo;

    /** Menor de 18 años (Código Civil): requiere representante legal. */
    public boolean isMinor() {
        Integer a = getAge();
        return a != null && a < 18;
    }

    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "created_by")
    public String createdBy;
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
    @Column(name = "updated_by")
    public String updatedBy;

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public Integer getAge() {
        return birthDate == null ? null : Period.between(birthDate, Dates.today()).getYears();
    }
}
