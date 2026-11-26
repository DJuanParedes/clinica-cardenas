package pe.clinicacardenas.patient;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Reglas de validación del registro de pacientes (HU-01). Sin dependencias de Spring para poder probarlas aisladas. */
public final class PatientValidator {

    private static final Pattern DNI = Pattern.compile("^\\d{8}$");
    private static final Pattern CE = Pattern.compile("^[A-Z0-9]{9,12}$");
    private static final Pattern PAS = Pattern.compile("^[A-Z0-9]{6,12}$");
    private static final Pattern NAME = Pattern.compile("^[\\p{L}][\\p{L} '.-]{1,79}$");
    private static final Pattern NAME_LONG = Pattern.compile("^[\\p{L}][\\p{L} '.-]{1,99}$");
    private static final Pattern CONTACT = Pattern.compile("^[\\p{L}][\\p{L} '.-]{1,119}$");
    private static final Pattern DISTRICT = Pattern.compile("^[\\p{L}][\\p{L} '.-]{0,79}$");
    private static final Pattern PHONE = Pattern.compile("^\\+?\\d{6,12}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");

    private PatientValidator() {}

    public record Input(String docType, String docNumber, String firstName, String lastName, LocalDate birthDate,
                        String sex, String phone, String email, String address, String district,
                        String emergencyContactName, String emergencyContactPhone, String notes) {}

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
            case "DNI" -> { if (!DNI.matcher(doc).matches()) e.put("docNumber", "El DNI debe tener exactamente 8 dígitos numéricos."); }
            case "CE" -> { if (!CE.matcher(doc).matches()) e.put("docNumber", "El carné de extranjería tiene de 9 a 12 caracteres."); }
            case "PAS" -> { if (!PAS.matcher(doc).matches()) e.put("docNumber", "El pasaporte tiene de 6 a 12 caracteres."); }
            default -> e.put("docType", "Selecciona el tipo de documento.");
        }
        if (in.firstName() == null || !NAME.matcher(in.firstName().trim()).matches()) {
            e.put("firstName", "Ingresa los nombres (solo letras).");
        }
        if (in.lastName() == null || !NAME_LONG.matcher(in.lastName().trim()).matches()) {
            e.put("lastName", "Ingresa los apellidos (solo letras).");
        }
        if (in.birthDate() == null) {
            e.put("birthDate", "Ingresa la fecha de nacimiento.");
        } else if (in.birthDate().isAfter(today)) {
            e.put("birthDate", "La fecha de nacimiento no puede ser futura.");
        } else if (in.birthDate().isBefore(today.minusYears(120))) {
            e.put("birthDate", "Revisa la fecha de nacimiento.");
        }
        if (in.sex() == null || !(in.sex().equals("F") || in.sex().equals("M"))) {
            e.put("sex", "Selecciona el sexo.");
        }
        if (!PHONE.matcher(normalizePhone(in.phone())).matches()) {
            e.put("phone", "Ingresa un teléfono válido (6 a 12 dígitos).");
        }
        if (in.email() != null && !in.email().isBlank()
                && (in.email().trim().length() > 120 || !EMAIL.matcher(in.email().trim()).matches())) {
            e.put("email", "Ingresa un correo válido.");
        }
        if (in.address() != null && in.address().trim().length() > 200) {
            e.put("address", "La dirección admite hasta 200 caracteres.");
        }
        if (in.district() != null && !in.district().isBlank() && !DISTRICT.matcher(in.district().trim()).matches()) {
            e.put("district", "El distrito solo admite letras (hasta 80).");
        }
        if (in.emergencyContactName() != null && !in.emergencyContactName().isBlank()
                && !CONTACT.matcher(in.emergencyContactName().trim()).matches()) {
            e.put("emergencyContactName", "El nombre del contacto solo admite letras.");
        }
        if (in.notes() != null && in.notes().trim().length() > 500) {
            e.put("notes", "Las notas admiten hasta 500 caracteres.");
        }
        if (in.emergencyContactPhone() != null && !in.emergencyContactPhone().isBlank()
                && !PHONE.matcher(normalizePhone(in.emergencyContactPhone())).matches()) {
            e.put("emergencyContactPhone", "Ingresa un teléfono válido.");
        }
        return e;
    }
}
