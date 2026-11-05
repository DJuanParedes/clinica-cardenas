package pe.clinicacardenas.clinical;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TreatmentItemRepository extends JpaRepository<TreatmentItem, Long> {
    java.util.List<TreatmentItem> findByPlanIdOrderBySortOrderAscIdAsc(Long planId);
    java.util.List<TreatmentItem> findByPlanIdIn(java.util.Collection<Long> planIds);
    long countByStatus(String status);
}
