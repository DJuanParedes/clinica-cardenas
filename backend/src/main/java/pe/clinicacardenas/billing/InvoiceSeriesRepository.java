package pe.clinicacardenas.billing;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InvoiceSeriesRepository extends JpaRepository<InvoiceSeries, Long> {
    List<InvoiceSeries> findByActiveTrueOrderBySeriesAsc();

    /** Numeración controlada: bloquea la serie mientras se asigna el correlativo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InvoiceSeries s where s.series = :series and s.active = true")
    Optional<InvoiceSeries> lockBySeries(@Param("series") String series);
}
