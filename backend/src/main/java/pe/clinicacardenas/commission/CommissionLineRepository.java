package pe.clinicacardenas.commission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommissionLineRepository extends JpaRepository<CommissionLine, Long> {
    List<CommissionLine> findBySettlementIdOrderByDoneAtAsc(Long settlementId);
    void deleteBySettlementId(Long settlementId);
}
