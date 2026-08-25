package pe.clinica.cardenas.security;

/** Datos no sensibles de la sesión autenticada disponibles en los servicios. */
public record SessionDetails(String fullName, String clinicId, String clinicSlug, boolean platformAdmin) {}
