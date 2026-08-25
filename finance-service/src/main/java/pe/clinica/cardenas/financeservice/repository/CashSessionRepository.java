package pe.clinica.cardenas.financeservice.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.financeservice.domain.CashSession;
import java.util.List;
import java.util.Optional;
public interface CashSessionRepository extends JpaRepository<CashSession, Long> {
    List<CashSession> findTop100ByOrderByOpenedAtDesc();
    Optional<CashSession> findFirstByCashierAndCashStatusOrderByOpenedAtDesc(String cashier, String cashStatus);
}
