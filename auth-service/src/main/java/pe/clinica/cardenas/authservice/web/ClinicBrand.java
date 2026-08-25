package pe.clinica.cardenas.authservice.web;

import pe.clinica.cardenas.authservice.domain.Clinic;

public record ClinicBrand(
        Long id, String code, String slug, String name, String shortName, String tagline, String description,
        String logoUrl, String heroImageUrl, String primaryColor, String primaryDarkColor,
        String accentColor, String accentDarkColor, String sidebarDarkColor,
        String phone, String email, String address, String workingHours, String customDomain, boolean active) {
    public static ClinicBrand from(Clinic clinic) {
        return new ClinicBrand(clinic.getId(), clinic.getCode(), clinic.getSlug(), clinic.getName(), clinic.getShortName(),
                clinic.getTagline(), clinic.getDescription(), clinic.getLogoUrl(), clinic.getHeroImageUrl(),
                clinic.getPrimaryColor(), clinic.getPrimaryDarkColor(), clinic.getAccentColor(), clinic.getAccentDarkColor(),
                clinic.getSidebarDarkColor(), clinic.getPhone(), clinic.getEmail(), clinic.getAddress(),
                clinic.getWorkingHours(), clinic.getCustomDomain(), clinic.isActive());
    }
}
