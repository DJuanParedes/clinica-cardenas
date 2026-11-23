package pe.clinicacardenas.patient;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Reglas de validación del registro de pacientes (HU-01). Sin dependencias de Spring para poder probarlas aisladas. */
public final class PatientValidator {
    private static final String BIRTHDATE = "birthDate";
    private static final String DOCNUMBER = "docNumber";


    private static final Pattern DNI = Pattern.compile("^\\d{8}$");
    private static final Pattern CE = Pattern.compile("^[A-Z0-9]{9,12}$");
    private static final Pattern PAS = Pattern.compile("^[A-Z0-9]{6,12}$");
    private static final Pattern NAME = Pattern.compile("^[\\p{L}][\\p{L} '.-]{1,79}$");
    private static final Pattern PHONE = Pattern.compile("^\\+?\\d{6,12}$");
    private static final Pattern EMAIL = pe.clinicacardenas.common.Emails.PATTERN;

    private PatientValidator() {}

    public static final java.util.Set<String> GUARDIAN_RELATIONSHIPS = java.util.Set.of("PADRE", "MADRE", "TUTOR_LEGAL");

    /**
     * dataConsent: el paciente (o su representante legal si es menor) aceptó la política de privacidad (Ley N.° 29733).
     * Menores de 18 años: guardian* = representante legal (Código Civil: patria potestad o tutela); los adultos
     * registran, si desean, un contacto de emergencia.
     */
    public record Input(String docType, String docNumber, String firstName, String lastName, LocalDate birthDate,
                        String sex, String phone, String email, String address, String district,
                        String emergencyContactName, String emergencyContactPhone, String notes, Boolean dataConsent,
                        String ubigeo, String emergencyContactRelationship,
                        String guardianName, String guardianDocType, String guardianDocNumber, String guardianRelationship,
                        String guardianPhone) {
        public Input(String docType, String docNumber, String firstName, String lastName, LocalDate birthDate,
                     String sex, String phone, String email, String address, String district,
                     String emergencyContactName, String emergencyContactPhone, String notes, Boolean dataConsent) {
            this(docType, docNumber, firstName, lastName, birthDate, sex, phone, email, address, district,
                    emergencyContactName, emergencyContactPhone, notes, dataConsent, null, null, null, null, null, null, null);
        }

        public Input(String docType, String docNumber, String firstName, String lastName, LocalDate birthDate,
                     String sex, String phone, String email, String address, String district,
                     String emergencyContactName, String emergencyContactPhone, String notes) {
            this(docType, docNumber, firstName, lastName, birthDate, sex, phone, email, address, district,
                    emergencyContactName, emergencyContactPhone, notes, null);
        }
    }

    public static String normalizeDoc(String doc) {
        return doc == null ? "" : doc.replaceAll("\\s", "").toUpperCase();
    }

    public static String normalizePhone(String phone) {
        return phone == null ? "" : phone.replaceAll("[\\s()-]", "");
    }

    public static Map<String, String> validate(Input in, LocalDate today) {
        Map<String, String> e = new LinkedHashMap<>();
        String type = in.docType() == null ? "" : in.docType().trim().toUpperCase();
        String doc = normalizeDoc(in.docNumber());
        switch (type) {
            case "DNI" -> { if (!DNI.matcher(doc).matches()) e.put(DOCNUMBER, "El DNI debe tener 8 dígitos."); }
            case "CE" -> { if (!CE.matcher(doc).matches()) e.put(DOCNUMBER, "El carné de extranjería tiene de 9 a 12 caracteres."); }
            case "PAS" -> { if (!PAS.matcher(doc).matches()) e.put(DOCNUMBER, "El pasaporte tiene de 6 a 12 caracteres."); }
            default -> e.put("docType", "Selecciona el tipo de documento.");
        }
        if (in.firstName() == null || !NAME.matcher(in.firstName().trim()).matches()) {
            e.put("firstName", "Ingresa los nombres (solo letras).");
        }
        if (in.lastName() == null || !NAME.matcher(in.lastName().trim()).matches()) {
            e.put("lastName", "Ingresa los apellidos (solo letras).");
        }
        if (in.birthDate() == null) {
            e.put(BIRTHDATE, "Ingresa la fecha de nacimiento.");
        } else if (in.birthDate().isAfter(today)) {
            e.put(BIRTHDATE, "La fecha de nacimiento no puede ser futura.");
        } else if (in.birthDate().isBefore(today.minusYears(120))) {
            e.put(BIRTHDATE, "Revisa la fecha de nacimiento.");
        }
        if (in.sex() == null || !(in.sex().equals("F") || in.sex().equals("M"))) {
            e.put("sex", "Selecciona el sexo.");
        }
        if (!PHONE.matcher(normalizePhone(in.phone())).matches()) {
            e.put("phone", "Ingresa un teléfono válido (6 a 12 dígitos).");
        }
        if (in.email() != null && !in.email().isBlank() && !EMAIL.matcher(in.email().trim()).matches()) {
            e.put("email", "Ingresa un correo válido.");
        }
        boolean minor = in.birthDate() != null && !in.birthDate().isAfter(today) && in.birthDate().plusYears(18).isAfter(today);
        if (minor) {
            // Menor de edad: el representante legal es obligatorio y es quien acepta y firma por el paciente.
            if (in.guardianName() == null || !NAME.matcher(in.guardianName().trim()).matches()) {
                e.put("guardianName", "Paciente menor de edad: ingresa el nombre completo del padre, madre o tutor legal.");
            }
            if (in.guardianRelationship() == null || !GUARDIAN_RELATIONSHIPS.contains(in.guardianRelationship())) {
                e.put("guardianRelationship", "Selecciona el vínculo: padre, madre o tutor legal.");
            }
            String gType = in.guardianDocType() == null ? "DNI" : in.guardianDocType().trim().toUpperCase();
            String gDoc = normalizeDoc(in.guardianDocNumber());
            boolean gOk = switch (gType) {
                case "DNI" -> DNI.matcher(gDoc).matches();
                case "CE" -> CE.matcher(gDoc).matches();
                case "PAS" -> PAS.matcher(gDoc).matches();
                default -> false;
            };
            if (!gOk) e.put("guardianDocNumber", "Ingresa el documento del representante legal (DNI de 8 dígitos).");
            else if (gType.equals(type) && gDoc.equals(doc)) e.put("guardianDocNumber", "El representante legal no puede ser el mismo paciente.");
            if (!PHONE.matcher(normalizePhone(in.guardianPhone())).matches()) {
                e.put("guardianPhone", "Ingresa el celular del representante legal.");
            }
        } else if (in.emergencyContactPhone() != null && !in.emergencyContactPhone().isBlank()
                && !PHONE.matcher(normalizePhone(in.emergencyContactPhone())).matches()) {
            e.put("emergencyContactPhone", "Ingresa un teléfono válido.");
        }
        if (in.ubigeo() != null && !in.ubigeo().isBlank() && !Ubigeo.exists(in.ubigeo())) {
            e.put("ubigeo", "Selecciona un distrito válido.");
        }
        return e;
    }
}
