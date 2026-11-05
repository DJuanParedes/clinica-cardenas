package pe.clinicacardenas.auth;

import pe.clinicacardenas.common.ApiException;

public final class PasswordPolicy {
    private PasswordPolicy() {}

    /** Mínimo 8 caracteres, con al menos una letra y un número. */
    public static boolean isValid(String pwd) {
        if (pwd == null || pwd.length() < 8 || pwd.length() > 72) return false;
        boolean letter = false, digit = false;
        for (char c : pwd.toCharArray()) {
            if (Character.isLetter(c)) letter = true;
            if (Character.isDigit(c)) digit = true;
        }
        return letter && digit;
    }

    public static void check(String field, String pwd) {
        if (!isValid(pwd)) {
            throw ApiException.field(field, "La contraseña debe tener al menos 8 caracteres, una letra y un número.");
        }
    }
}
