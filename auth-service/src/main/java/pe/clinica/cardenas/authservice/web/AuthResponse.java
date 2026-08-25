package pe.clinica.cardenas.authservice.web;

public record AuthResponse(String token, String name, String email, String role,
                           boolean platformAdmin, ClinicBrand clinic) {}
