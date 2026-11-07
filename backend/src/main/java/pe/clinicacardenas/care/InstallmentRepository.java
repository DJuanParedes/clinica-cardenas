package pe.clinicacardenas.care;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface InstallmentRepository extends JpaRepository<Installment, Long> {
    List<Installment> findByPlanIdOrderByNumberAsc(Long planId);

    List<Installment> findByDueDateAndRemindedAtIsNull(LocalDate dueDate);

    @Modifying
    @Query("delete from Installment i where i.planId = ?1")
    void deleteByPlanId(Long planId);
}
