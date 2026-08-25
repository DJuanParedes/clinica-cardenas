package pe.clinica.cardenas.operationsservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.operationsservice.domain.ModuleRecord;

import java.util.List;

public interface ModuleRecordRepository extends JpaRepository<ModuleRecord, Long> {
    List<ModuleRecord> findAllByModuleKeyOrderByUpdatedAtDesc(String moduleKey);
}
