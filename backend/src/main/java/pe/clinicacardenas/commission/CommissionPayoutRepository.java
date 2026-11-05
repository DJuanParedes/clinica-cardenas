package pe.clinicacardenas.commission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommissionPayoutRepository extends JpaRepository<CommissionPayout, Long> {
    List<CommissionPayout> findBySettlementIdOrderByPaidOnAsc(Long settlementId);
}
