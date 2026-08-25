package pe.clinica.cardenas.financeservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.financeservice.domain.DoctorCommission;

import java.util.List;
import java.util.Optional;

public interface DoctorCommissionRepository extends JpaRepository<DoctorCommission,Long> {
    List<DoctorCommission> findAllByOrderByDoctorNameAsc();
    Optional<DoctorCommission> findFirstByDoctorNameIgnoreCaseAndPeriod(String doctorName, String period);
}
