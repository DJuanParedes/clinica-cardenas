-- Archivos que la clínica le pide al paciente para un tratamiento (placa, radiografía, receta externa, informe…).
-- Los puede subir el propio paciente desde la app/portal, o recepción/administración en la ficha.
CREATE TABLE file_requests (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id    BIGINT       NOT NULL,
  plan_id       BIGINT       NULL,
  title         VARCHAR(150) NOT NULL,
  description   VARCHAR(500) NULL,
  kind          VARCHAR(20)  NOT NULL DEFAULT 'RADIOGRAFIA',
  status        VARCHAR(12)  NOT NULL DEFAULT 'PENDIENTE',    -- PENDIENTE, RECIBIDO, ANULADO
  due_date      DATE         NULL,
  file_id       BIGINT       NULL,
  requested_by  VARCHAR(60)  NOT NULL,
  requested_at  DATETIME     NOT NULL,
  fulfilled_by  VARCHAR(60)  NULL,
  fulfilled_at  DATETIME     NULL,
  CONSTRAINT fk_fr_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  CONSTRAINT fk_fr_plan FOREIGN KEY (plan_id) REFERENCES treatment_plans(id),
  INDEX idx_fr_patient (patient_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Seguimiento de recepción a las citas que el paciente no confirmó (llamada, sin respuesta…)
ALTER TABLE appointments
  ADD COLUMN call_note VARCHAR(200) NULL,
  ADD COLUMN call_at   DATETIME NULL,
  ADD COLUMN call_by   VARCHAR(40) NULL;
