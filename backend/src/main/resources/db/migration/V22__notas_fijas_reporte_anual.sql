-- v2.8.0 (paso 15): notas fijas en la ficha del paciente (se ven arriba en todas las pestañas).
CREATE TABLE patient_notes (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id  BIGINT       NOT NULL,
  text        VARCHAR(500) NOT NULL,
  tone        VARCHAR(10)  NOT NULL DEFAULT 'AMARILLO',   -- AMARILLO (aviso), ROJO (importante), AZUL (dato útil)
  pinned      BOOLEAN      NOT NULL DEFAULT TRUE,
  created_by  VARCHAR(60)  NOT NULL,
  created_at  DATETIME     NOT NULL,
  updated_by  VARCHAR(60)  NULL,
  updated_at  DATETIME     NULL,
  deleted_at  DATETIME     NULL,
  deleted_by  VARCHAR(60)  NULL,
  INDEX ix_patient_notes (patient_id, deleted_at),
  CONSTRAINT fk_patient_notes FOREIGN KEY (patient_id) REFERENCES patients(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
