-- =====================================================================
-- Clínica Dental Cárdenas · Versión completa (todas las historias)
-- HU-26 permisos por rol · HU-13/14/15 caja · HU-16/17/18 comisiones
-- HU-22 portal del paciente · HU-23/24 notificaciones
-- =====================================================================

-- ---------- HU-26: catálogo de permisos y permisos por rol ----------
CREATE TABLE permissions (
  code       VARCHAR(30)  NOT NULL PRIMARY KEY,
  group_name VARCHAR(40)  NOT NULL,
  label      VARCHAR(120) NOT NULL,
  sort_order INT          NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE role_permissions (
  role_code       VARCHAR(20) NOT NULL,
  permission_code VARCHAR(30) NOT NULL,
  PRIMARY KEY (role_code, permission_code),
  CONSTRAINT fk_rp_role FOREIGN KEY (role_code) REFERENCES roles(code),
  CONSTRAINT fk_rp_perm FOREIGN KEY (permission_code) REFERENCES permissions(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO permissions (code, group_name, label, sort_order) VALUES
('PACIENTES_VER',     'Pacientes',            'Buscar pacientes y ver su ficha',                       10),
('PACIENTES_EDITAR',  'Pacientes',            'Registrar y actualizar datos de pacientes',             11),
('AGENDA_VER',        'Agenda',               'Ver la agenda y las citas',                             20),
('CITAS_GESTIONAR',   'Agenda',               'Programar, reprogramar y cancelar citas',               21),
('CITAS_ESTADO',      'Agenda',               'Registrar llegada y estado de atención',                22),
('HISTORIA_CLINICA',  'Atención clínica',     'Historia clínica, diagnóstico y odontograma',           30),
('PLAN_TRATAMIENTO',  'Atención clínica',     'Plan de tratamiento y evolución',                       31),
('COMPROBANTES',      'Caja y facturación',   'Emitir comprobantes y enviarlos a SUNAT',               40),
('CAJA_COBRAR',       'Caja y facturación',   'Registrar pagos y consultar saldos de pacientes',       41),
('CAJA_EGRESOS',      'Caja y facturación',   'Registrar egresos de caja',                             42),
('CAJA_REPORTE',      'Caja y facturación',   'Consultar ingresos y egresos por período',              43),
('COMISIONES',        'Comisiones',           'Configurar, calcular y liquidar comisiones',            50),
('REPORTES',          'Dirección',            'Indicadores gerenciales',                               60),
('AUDITORIA',         'Dirección',            'Registro de cambios del sistema',                       61),
('NOTIFICACIONES',    'Comunicación',         'Ver y reenviar notificaciones a pacientes',             70),
('USUARIOS',          'Seguridad',            'Crear, editar y desactivar cuentas de usuario',         80),
('ROLES',             'Seguridad',            'Asignar permisos a los roles',                          81);

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'ADMIN', code FROM permissions;

INSERT INTO role_permissions (role_code, permission_code) VALUES
('RECEPCION', 'PACIENTES_VER'), ('RECEPCION', 'PACIENTES_EDITAR'), ('RECEPCION', 'AGENDA_VER'),
('RECEPCION', 'CITAS_GESTIONAR'), ('RECEPCION', 'CITAS_ESTADO'), ('RECEPCION', 'NOTIFICACIONES'),
('ODONTOLOGO', 'PACIENTES_VER'), ('ODONTOLOGO', 'AGENDA_VER'), ('ODONTOLOGO', 'CITAS_ESTADO'),
('ODONTOLOGO', 'HISTORIA_CLINICA'), ('ODONTOLOGO', 'PLAN_TRATAMIENTO'),
('CAJA', 'PACIENTES_VER'), ('CAJA', 'COMPROBANTES'), ('CAJA', 'CAJA_COBRAR'), ('CAJA', 'CAJA_EGRESOS'),
('DIRECTOR', 'PACIENTES_VER'), ('DIRECTOR', 'AGENDA_VER'), ('DIRECTOR', 'CAJA_REPORTE'),
('DIRECTOR', 'REPORTES'), ('DIRECTOR', 'AUDITORIA');

-- ---------- Quién realizó cada procedimiento (base de comisiones) ----------
ALTER TABLE treatment_items ADD COLUMN done_by_dentist_id BIGINT NULL,
  ADD CONSTRAINT fk_item_done_by FOREIGN KEY (done_by_dentist_id) REFERENCES dentists(id),
  ADD INDEX idx_item_done (status, done_at);

-- ---------- Preferencia de notificación del paciente ----------
ALTER TABLE patients ADD COLUMN notify_channel VARCHAR(10) NOT NULL DEFAULT 'WHATSAPP';

-- ---------- HU-13 / HU-14: pagos ----------
CREATE TABLE payments (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id        BIGINT        NOT NULL,
  treatment_item_id BIGINT        NULL,          -- obligación pagada; NULL = pago a cuenta
  amount            DECIMAL(10,2) NOT NULL,
  method            VARCHAR(15)   NOT NULL,      -- EFECTIVO, TARJETA, YAPE, PLIN, TRANSFERENCIA
  reference         VARCHAR(40)   NULL,          -- n.° de operación o voucher
  notes             VARCHAR(200)  NULL,
  paid_at           DATETIME      NOT NULL,
  cashier           VARCHAR(40)   NOT NULL,
  status            VARCHAR(10)   NOT NULL DEFAULT 'VALIDO',   -- VALIDO, ANULADO
  void_reason       VARCHAR(200)  NULL,
  voided_at         DATETIME      NULL,
  voided_by         VARCHAR(40)   NULL,
  CONSTRAINT fk_pay_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  CONSTRAINT fk_pay_item FOREIGN KEY (treatment_item_id) REFERENCES treatment_items(id),
  INDEX idx_pay_patient (patient_id, paid_at),
  INDEX idx_pay_date (paid_at),
  INDEX idx_pay_item (treatment_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------- HU-15: egresos ----------
CREATE TABLE expenses (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  expense_date DATE          NOT NULL,
  category     VARCHAR(30)   NOT NULL,
  concept      VARCHAR(200)  NOT NULL,
  amount       DECIMAL(10,2) NOT NULL,
  method       VARCHAR(15)   NOT NULL,
  reference    VARCHAR(40)   NULL,
  supplier     VARCHAR(120)  NULL,
  status       VARCHAR(10)   NOT NULL DEFAULT 'VALIDO',
  void_reason  VARCHAR(200)  NULL,
  created_at   DATETIME      NOT NULL,
  created_by   VARCHAR(40)   NOT NULL,
  INDEX idx_exp_date (expense_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------- HU-16 / HU-17 / HU-18: comisiones ----------
CREATE TABLE commission_rules (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  dentist_id   BIGINT        NOT NULL,
  procedure_id BIGINT        NULL,               -- NULL = aplica a todos los procedimientos
  calc_type    VARCHAR(12)   NOT NULL,           -- PORCENTAJE, MONTO_FIJO
  value        DECIMAL(10,2) NOT NULL,
  base         VARCHAR(10)   NOT NULL,           -- REALIZADO (precio del procedimiento) o COBRADO (lo pagado)
  valid_from   DATE          NOT NULL,
  valid_to     DATE          NULL,
  active       BOOLEAN       NOT NULL DEFAULT TRUE,
  notes        VARCHAR(200)  NULL,
  created_at   DATETIME      NOT NULL,
  created_by   VARCHAR(40)   NULL,
  updated_at   DATETIME      NULL,
  updated_by   VARCHAR(40)   NULL,
  CONSTRAINT fk_rule_dentist FOREIGN KEY (dentist_id) REFERENCES dentists(id),
  CONSTRAINT fk_rule_proc FOREIGN KEY (procedure_id) REFERENCES procedures(id),
  INDEX idx_rule_dentist (dentist_id, valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commission_settlements (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  dentist_id        BIGINT        NOT NULL,
  period_from       DATE          NOT NULL,
  period_to         DATE          NOT NULL,
  total_amount      DECIMAL(10,2) NOT NULL,
  paid_amount       DECIMAL(10,2) NOT NULL DEFAULT 0,
  status            VARCHAR(10)   NOT NULL,      -- PENDIENTE, PARCIAL, PAGADA, ANULADA
  notes             VARCHAR(300)  NULL,
  created_at        DATETIME      NOT NULL,
  created_by        VARCHAR(40)   NULL,
  CONSTRAINT fk_set_dentist FOREIGN KEY (dentist_id) REFERENCES dentists(id),
  INDEX idx_set_dentist (dentist_id, period_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commission_lines (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  settlement_id     BIGINT        NOT NULL,
  treatment_item_id BIGINT        NOT NULL,
  rule_id           BIGINT        NOT NULL,
  done_at           DATETIME      NOT NULL,
  patient_name      VARCHAR(200)  NOT NULL,
  procedure_name    VARCHAR(120)  NOT NULL,
  rule_label        VARCHAR(120)  NOT NULL,
  base_amount       DECIMAL(10,2) NOT NULL,
  commission_amount DECIMAL(10,2) NOT NULL,
  CONSTRAINT uk_line_item UNIQUE (treatment_item_id),   -- un procedimiento se liquida una sola vez
  CONSTRAINT fk_line_set FOREIGN KEY (settlement_id) REFERENCES commission_settlements(id) ON DELETE CASCADE,
  CONSTRAINT fk_line_item FOREIGN KEY (treatment_item_id) REFERENCES treatment_items(id),
  CONSTRAINT fk_line_rule FOREIGN KEY (rule_id) REFERENCES commission_rules(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commission_payouts (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  settlement_id BIGINT        NOT NULL,
  paid_on       DATE          NOT NULL,
  amount        DECIMAL(10,2) NOT NULL,
  method        VARCHAR(15)   NOT NULL,
  reference     VARCHAR(40)   NULL,
  created_at    DATETIME      NOT NULL,
  created_by    VARCHAR(40)   NULL,
  CONSTRAINT fk_payout_set FOREIGN KEY (settlement_id) REFERENCES commission_settlements(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------- HU-23 / HU-24: notificaciones (bandeja de salida) ----------
CREATE TABLE notifications (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id      BIGINT        NULL,
  appointment_id  BIGINT        NULL,
  type            VARCHAR(20)   NOT NULL,        -- RECORDATORIO, CAMBIO_CITA, CANCELACION, CODIGO_ACCESO
  operation       VARCHAR(10)   NOT NULL,        -- ENVIAR, PROGRAMAR, ANULAR
  channel         VARCHAR(10)   NOT NULL,        -- WHATSAPP, EMAIL
  destination     VARCHAR(120)  NOT NULL,
  subject         VARCHAR(200)  NULL,
  body            TEXT          NOT NULL,
  idempotency_key VARCHAR(120)  NOT NULL,
  scheduled_for   DATETIME      NULL,
  status          VARCHAR(12)   NOT NULL,        -- PENDIENTE, PROGRAMADO, ENVIADO, FALLIDO, ANULADO, DUPLICADO
  attempts        INT           NOT NULL DEFAULT 0,
  next_attempt_at DATETIME      NULL,
  last_error      VARCHAR(300)  NULL,
  provider_id     VARCHAR(60)   NULL,
  sent_at         DATETIME      NULL,
  created_at      DATETIME      NOT NULL,
  updated_at      DATETIME      NULL,
  CONSTRAINT uk_notif_key UNIQUE (idempotency_key),
  CONSTRAINT fk_notif_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  INDEX idx_notif_status (status, next_attempt_at),
  INDEX idx_notif_patient (patient_id, created_at),
  INDEX idx_notif_appt (appointment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------- HU-22: acceso del paciente a la aplicación móvil ----------
CREATE TABLE patient_accounts (
  patient_id      BIGINT       NOT NULL PRIMARY KEY,
  password_hash   VARCHAR(100) NULL,
  active          BOOLEAN      NOT NULL DEFAULT TRUE,
  failed_attempts INT          NOT NULL DEFAULT 0,
  locked_until    DATETIME     NULL,
  last_login_at   DATETIME     NULL,
  activated_at    DATETIME     NULL,
  created_at      DATETIME     NOT NULL,
  CONSTRAINT fk_pacc_patient FOREIGN KEY (patient_id) REFERENCES patients(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE portal_codes (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id BIGINT       NOT NULL,
  code_hash  VARCHAR(100) NOT NULL,
  attempts   INT          NOT NULL DEFAULT 0,
  expires_at DATETIME     NOT NULL,
  used_at    DATETIME     NULL,
  created_at DATETIME     NOT NULL,
  CONSTRAINT fk_pcode_patient FOREIGN KEY (patient_id) REFERENCES patients(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
