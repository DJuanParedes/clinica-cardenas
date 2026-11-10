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
    @Column(name = "emergency_contact_name")
    public String emergencyContactName;
    @Column(name = "emergency_contact_phone")
    public String emergencyContactPhone;
    public String notes;
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
