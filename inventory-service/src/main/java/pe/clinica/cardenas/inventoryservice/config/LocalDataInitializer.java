package pe.clinica.cardenas.inventoryservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import pe.clinica.cardenas.inventoryservice.domain.InventoryItem;
import pe.clinica.cardenas.inventoryservice.repository.InventoryItemRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

@Configuration
@Profile("local")
@RequiredArgsConstructor
public class LocalDataInitializer {
    private final InventoryItemRepository repository;
    @Bean CommandLineRunner seedInventory() { return args -> {
        if (repository.count() == 0) {
            repository.save(InventoryItem.builder().code("INS-001").name("Guantes de nitrilo").category("Bioseguridad").unit("caja").currentStock(new BigDecimal("18")).minimumStock(new BigDecimal("10")).lot("GN-2607").expiryDate(LocalDate.now().plusYears(2)).unitCost(new BigDecimal("32.50")).inventoryStatus("DISPONIBLE").active(true).build());
            repository.save(InventoryItem.builder().code("INS-002").name("Anestesia dental 2%").category("Medicamentos").unit("caja").currentStock(new BigDecimal("6")).minimumStock(new BigDecimal("8")).lot("AN-2604").expiryDate(LocalDate.now().plusMonths(8)).unitCost(new BigDecimal("85.00")).inventoryStatus("STOCK BAJO").active(true).build());
            repository.save(InventoryItem.builder().code("INS-003").name("Resina fotocurable A2").category("Restauración").unit("unidad").currentStock(new BigDecimal("12")).minimumStock(new BigDecimal("5")).lot("RF-A2-11").expiryDate(LocalDate.now().plusYears(1)).unitCost(new BigDecimal("96.00")).inventoryStatus("DISPONIBLE").active(true).build());
        }
    }; }
}
