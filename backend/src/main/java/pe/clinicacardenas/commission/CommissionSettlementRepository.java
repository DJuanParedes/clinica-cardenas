package pe.clinicacardenas.commission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface CommissionSettlementRepository extends JpaRepository<CommissionSettlement, Long> {
    List<CommissionSettlement> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CommissionSettlement s where s.id = :id")
    Optional<CommissionSettlement> lockById(@Param("id") Long id);
}
