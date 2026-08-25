package pe.clinica.cardenas.authservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.authservice.domain.Role;
import pe.clinica.cardenas.authservice.domain.UserAccount;

import java.util.Optional;
import java.util.List;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmail(String email);
    Optional<UserAccount> findByDni(String dni);
    Optional<UserAccount> findByPhoneE164(String phoneE164);
    Optional<UserAccount> findByPlatformAdminTrue();
    long countByRole(Role role);
    long countByPlatformAdminTrue();
    long countByRoleAndClinicId(Role role, String clinicId);
    List<UserAccount> findAllByClinicId(String clinicId);
}
