package pe.clinicacardenas.cash;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    @Query("select e from Expense e where e.expenseDate >= :from and e.expenseDate <= :to order by e.expenseDate desc, e.id desc")
    List<Expense> findInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
