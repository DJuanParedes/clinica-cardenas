package pe.clinicacardenas.commission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommissionRuleRepository extends JpaRepository<CommissionRule, Long> {
    List<CommissionRule> findByDentistIdOrderByValidFromDesc(Long dentistId);
    List<CommissionRule> findAllByOrderByDentistIdAscValidFromDesc();
}
