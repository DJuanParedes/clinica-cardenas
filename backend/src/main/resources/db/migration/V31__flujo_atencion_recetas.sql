-- v2.17: el flujo de atención queda amarrado.
-- 1) La cita guarda lo que se hizo y lo que se cobró en ella.
ALTER TABLE evolutions      ADD COLUMN appointment_id BIGINT NULL;
ALTER TABLE treatment_items ADD COLUMN done_appointment_id BIGINT NULL;
ALTER TABLE payments        ADD COLUMN appointment_id BIGINT NULL;
CREATE INDEX idx_evo_appt ON evolutions (appointment_id);
CREATE INDEX idx_item_appt ON treatment_items (done_appointment_id);
CREATE INDEX idx_pay_appt ON payments (appointment_id);
-- 3) Procedimientos que exigen un consentimiento informado firmado antes de realizarse.
ALTER TABLE procedures ADD COLUMN consent_template_id BIGINT NULL;
-- 5) Recetas estructuradas (medicamento, dosis, frecuencia, días).
CREATE TABLE prescriptions (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id     BIGINT        NOT NULL,
  dentist_id     BIGINT        NULL,
  appointment_id BIGINT        NULL,
  indications    VARCHAR(1000) NULL,
  file_id        BIGINT        NULL,
  created_at     DATETIME      NOT NULL,
  created_by     VARCHAR(40)   NOT NULL,
  voided_at      DATETIME      NULL,
  voided_by      VARCHAR(40)   NULL,
  void_reason    VARCHAR(300)  NULL,
  CONSTRAINT fk_rx_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  INDEX idx_rx_patient (patient_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE prescription_items (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  prescription_id BIGINT       NOT NULL,
  drug            VARCHAR(120) NOT NULL,
  presentation    VARCHAR(80)  NULL,
  dose            VARCHAR(60)  NOT NULL,
  frequency       VARCHAR(60)  NOT NULL,
  days            INT          NULL,
  route           VARCHAR(30)  NULL,
  instructions    VARCHAR(200) NULL,
  CONSTRAINT fk_rxi_rx FOREIGN KEY (prescription_id) REFERENCES prescriptions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
