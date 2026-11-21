-- Mejoras: archivos clínicos, avisos en la app, reservas y encuestas, inventario, lista de espera,
-- pagos en cuotas, recordatorios de control, verificación en 2 pasos y consentimiento de datos (Ley 29733).

-- Fotos, radiografías, documentos, consentimientos y recetas de cada paciente
CREATE TABLE patient_files (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id BIGINT NOT NULL,
  kind VARCHAR(20) NOT NULL,                -- FOTO, RADIOGRAFIA, DOCUMENTO, CONSENTIMIENTO, RECETA, PRESUPUESTO
  title VARCHAR(150) NOT NULL,
  note VARCHAR(500),
  tooth INT,
  content_type VARCHAR(100) NOT NULL,
  size_bytes BIGINT NOT NULL,
  storage_name VARCHAR(80) NOT NULL,
  shared_with_patient BOOLEAN NOT NULL DEFAULT FALSE,
  signed_at DATETIME,
  created_at DATETIME NOT NULL,
  created_by VARCHAR(60),
  deleted_at DATETIME,
  deleted_by VARCHAR(60),
  CONSTRAINT fk_pf_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  INDEX idx_pf_patient (patient_id, created_at)
);

-- Bandeja de avisos de la app del paciente (notificaciones dentro del teléfono)
CREATE TABLE patient_notices (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id BIGINT NOT NULL,
  kind VARCHAR(30) NOT NULL,
  title VARCHAR(150) NOT NULL,
  body VARCHAR(500) NOT NULL,
  ref_id BIGINT,
  created_at DATETIME NOT NULL,
  read_at DATETIME,
  CONSTRAINT fk_pn_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  INDEX idx_pn_patient (patient_id, id)
);

-- Encuesta de satisfacción después de cada cita atendida
CREATE TABLE surveys (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  appointment_id BIGINT NOT NULL UNIQUE,
  patient_id BIGINT NOT NULL,
  dentist_id BIGINT,
  score TINYINT NOT NULL,
  comment VARCHAR(500),
  created_at DATETIME NOT NULL,
  CONSTRAINT fk_sv_appt FOREIGN KEY (appointment_id) REFERENCES appointments(id),
  INDEX idx_sv_created (created_at)
);

-- Inventario de materiales e insumos
CREATE TABLE inventory_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(120) NOT NULL UNIQUE,
  unit VARCHAR(20) NOT NULL,
  stock DECIMAL(10,2) NOT NULL DEFAULT 0,
  min_stock DECIMAL(10,2) NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL,
  updated_at DATETIME
);

CREATE TABLE inventory_moves (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  item_id BIGINT NOT NULL,
  kind VARCHAR(20) NOT NULL,                -- ENTRADA, SALIDA, AJUSTE, CONSUMO
  quantity DECIMAL(10,2) NOT NULL,          -- positivo suma, negativo resta
  stock_after DECIMAL(10,2) NOT NULL,
  reason VARCHAR(200),
  treatment_item_id BIGINT,
  created_at DATETIME NOT NULL,
  created_by VARCHAR(60),
  CONSTRAINT fk_im_item FOREIGN KEY (item_id) REFERENCES inventory_items(id),
  INDEX idx_im_item (item_id, created_at)
);

-- Materiales que consume cada procedimiento (se descuentan al marcarlo como realizado)
CREATE TABLE procedure_supplies (
  procedure_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  quantity DECIMAL(10,2) NOT NULL,
  PRIMARY KEY (procedure_id, item_id),
  CONSTRAINT fk_ps_proc FOREIGN KEY (procedure_id) REFERENCES procedures(id),
  CONSTRAINT fk_ps_item FOREIGN KEY (item_id) REFERENCES inventory_items(id)
);

-- Lista de espera: si alguien cancela, se avisa a quien espera un espacio
CREATE TABLE waitlist (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id BIGINT NOT NULL,
  dentist_id BIGINT,
  reason VARCHAR(200) NOT NULL,
  preference VARCHAR(200),
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',  -- ACTIVA, AVISADA, CERRADA
  created_at DATETIME NOT NULL,
  created_by VARCHAR(60),
  notified_at DATETIME,
  closed_at DATETIME,
  CONSTRAINT fk_wl_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  INDEX idx_wl_status (status, created_at)
);

-- Pagos en cuotas de un plan de tratamiento
CREATE TABLE installments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id BIGINT NOT NULL,
  number INT NOT NULL,
  due_date DATE NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  reminded_at DATETIME,
  created_at DATETIME NOT NULL,
  created_by VARCHAR(60),
  UNIQUE KEY uq_inst (plan_id, number),
  CONSTRAINT fk_in_plan FOREIGN KEY (plan_id) REFERENCES treatment_plans(id)
);

-- Recordatorios de control enviados (para no repetirlos)
CREATE TABLE recall_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id BIGINT NOT NULL,
  kind VARCHAR(30) NOT NULL,
  sent_at DATETIME NOT NULL,
  sent_by VARCHAR(60),
  INDEX idx_rl_patient (patient_id, sent_at)
);

-- El paciente pidió reprogramar respondiendo "2" al recordatorio de WhatsApp
ALTER TABLE appointments ADD COLUMN reschedule_requested_at DATETIME NULL;

-- Verificación en 2 pasos (TOTP, app Google Authenticator o similar) para el personal
ALTER TABLE users ADD COLUMN totp_secret VARCHAR(64) NULL, ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Consentimiento para el tratamiento de datos personales (Ley N.° 29733)
ALTER TABLE patients ADD COLUMN data_consent BOOLEAN NOT NULL DEFAULT FALSE, ADD COLUMN data_consent_at DATETIME NULL;

INSERT INTO permissions (code, group_name, label, sort_order) VALUES
('INVENTARIO', 'Atención clínica', 'Inventario de materiales e insumos', 32);
INSERT INTO role_permissions (role_code, permission_code) VALUES
('ADMIN', 'INVENTARIO'), ('RECEPCION', 'INVENTARIO'), ('ODONTOLOGO', 'INVENTARIO');

-- Inventario inicial de ejemplo y consumo por procedimiento
INSERT INTO inventory_items (name, unit, stock, min_stock, created_at) VALUES
('Anestesia lidocaína 2% (cartucho)', 'unidad', 120, 40, NOW()),
('Guantes de nitrilo (par)', 'par', 300, 100, NOW()),
('Mascarilla descartable', 'unidad', 200, 60, NOW()),
('Resina compuesta A2 (jeringa)', 'unidad', 8, 5, NOW()),
('Ácido grabador 37%', 'unidad', 6, 3, NOW()),
('Adhesivo universal', 'unidad', 4, 2, NOW()),
('Pasta profiláctica', 'unidad', 10, 4, NOW()),
('Flúor gel', 'unidad', 6, 2, NOW()),
('Limas endodónticas (set)', 'set', 12, 4, NOW()),
('Gutapercha (caja)', 'caja', 5, 2, NOW()),
('Sutura reabsorbible', 'unidad', 30, 10, NOW()),
('Película radiográfica periapical', 'unidad', 80, 30, NOW());

INSERT INTO procedure_supplies (procedure_id, item_id, quantity)
SELECT p.id, i.id, s.qty FROM (
  SELECT 'CON-001' code, 'Guantes de nitrilo (par)' item, 1 qty UNION ALL
  SELECT 'CON-001', 'Mascarilla descartable', 1 UNION ALL
  SELECT 'RAD-001', 'Película radiográfica periapical', 1 UNION ALL
  SELECT 'PRE-001', 'Pasta profiláctica', 0.25 UNION ALL
  SELECT 'PRE-001', 'Guantes de nitrilo (par)', 1 UNION ALL
  SELECT 'PRE-002', 'Flúor gel', 0.2 UNION ALL
  SELECT 'OPE-001', 'Resina compuesta A2 (jeringa)', 0.25 UNION ALL
  SELECT 'OPE-001', 'Ácido grabador 37%', 0.1 UNION ALL
  SELECT 'OPE-001', 'Adhesivo universal', 0.1 UNION ALL
  SELECT 'OPE-001', 'Anestesia lidocaína 2% (cartucho)', 1 UNION ALL
  SELECT 'OPE-002', 'Resina compuesta A2 (jeringa)', 0.5 UNION ALL
  SELECT 'OPE-002', 'Ácido grabador 37%', 0.1 UNION ALL
  SELECT 'OPE-002', 'Adhesivo universal', 0.1 UNION ALL
  SELECT 'OPE-002', 'Anestesia lidocaína 2% (cartucho)', 1 UNION ALL
  SELECT 'END-001', 'Limas endodónticas (set)', 0.5 UNION ALL
  SELECT 'END-001', 'Gutapercha (caja)', 0.1 UNION ALL
  SELECT 'END-001', 'Anestesia lidocaína 2% (cartucho)', 2 UNION ALL
  SELECT 'END-002', 'Limas endodónticas (set)', 1 UNION ALL
  SELECT 'END-002', 'Gutapercha (caja)', 0.2 UNION ALL
  SELECT 'END-002', 'Anestesia lidocaína 2% (cartucho)', 2 UNION ALL
  SELECT 'CIR-001', 'Anestesia lidocaína 2% (cartucho)', 2 UNION ALL
  SELECT 'CIR-002', 'Anestesia lidocaína 2% (cartucho)', 3 UNION ALL
  SELECT 'CIR-002', 'Sutura reabsorbible', 1
) s
JOIN procedures p ON p.code = s.code
JOIN inventory_items i ON i.name = s.item;

-- Los pacientes ya registrados por la clínica se consideran informados (el consentimiento se firmó en papel)
UPDATE patients SET data_consent = TRUE, data_consent_at = created_at WHERE demo = FALSE;
