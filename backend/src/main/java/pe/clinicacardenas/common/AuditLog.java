package pe.clinicacardenas.common;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String entity;
    @Column(name = "entity_id")
    public Long entityId;
    public String action;
    public String detail;
    public String username;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
}
