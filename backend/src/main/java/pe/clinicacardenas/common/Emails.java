package pe.clinicacardenas.common;

import java.util.regex.Pattern;

/**
 * Validación de correos sin retroceso exponencial (hallazgo S5852 de SonarQube): las partes del dominio no aceptan
 * puntos, así que ninguna cuantificación se solapa y la comprobación es lineal. Además se limita la longitud (RFC 5321).
 */
public final class Emails {
    private Emails() {}

    public static final Pattern PATTERN = Pattern.compile("^[^@\\s]{1,64}+@[^@\\s.]++(?:\\.[^@\\s.]++)++$");

    public static boolean valid(String email) {
        return email != null && email.length() <= 254 && PATTERN.matcher(email).matches();
    }
}
