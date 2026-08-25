package pe.clinica.cardenas.financeservice.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.financeservice.domain.Expense;
import java.util.List;
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findTop300ByOrderByRegisteredAtDesc();
    List<Expense> findTop300ByRegisteredByOrderByRegisteredAtDesc(String registeredBy);
}
