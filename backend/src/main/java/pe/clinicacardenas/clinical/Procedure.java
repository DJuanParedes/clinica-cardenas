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
}
