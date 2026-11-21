package pe.clinicacardenas.clinical;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "procedures")
public class Procedure {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String code;
    public String name;
    public String category;
    public BigDecimal price;
    public boolean active = true;
    /** Agrupa variantes del mismo servicio (p. ej. "Limpieza dental": simple, profunda, con ultrasonido). */
    public String family;
    public String description;
    @Column(name = "duration_min")
    public Integer durationMin;

    /** Promoción vigente hoy (la calcula PromotionService al listar el catálogo). */
    @Transient
    public java.util.Map<String, Object> promotion;
}
