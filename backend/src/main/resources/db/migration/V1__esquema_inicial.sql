-- =====================================================================
-- Clínica Dental Cárdenas · Sprint 1 · Esquema inicial
-- Seguridad · Pacientes · Agenda · Historia clínica · Facturación
-- =====================================================================

-- ---------- Seguridad: usuarios, roles y registro de cambios ----------
CREATE TABLE roles (
  code        VARCHAR(20)  NOT NULL PRIMARY KEY,
  name        VARCHAR(60)  NOT NULL,
  description VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE users (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  username        VARCHAR(40)  NOT NULL,
  email           VARCHAR(120) NOT NULL,
  full_name       VARCHAR(120) NOT NULL,
  password_hash   VARCHAR(100) NOT NULL,
  role_code       VARCHAR(20)  NOT NULL,
  active          BOOLEAN      NOT NULL DEFAULT TRUE,
  failed_attempts INT          NOT NULL DEFAULT 0,
  locked_until    DATETIME     NULL,
  last_login_at   DATETIME     NULL,
  created_at      DATETIME     NOT NULL,
  created_by      VARCHAR(40)  NULL,
  updated_at      DATETIME     NULL,
  updated_by      VARCHAR(40)  NULL,
  CONSTRAINT uk_users_username UNIQUE (username),
  CONSTRAINT uk_users_email UNIQUE (email),
  CONSTRAINT fk_users_role FOREIGN KEY (role_code) REFERENCES roles(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE password_reset_codes (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT       NOT NULL,
  code_hash  VARCHAR(100) NOT NULL,
  attempts   INT          NOT NULL DEFAULT 0,
  expires_at DATETIME     NOT NULL,
  used_at    DATETIME     NULL,
  created_at DATETIME     NOT NULL,
  CONSTRAINT fk_prc_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE audit_log (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  entity     VARCHAR(40)  NOT NULL,
  entity_id  BIGINT       NULL,
  action     VARCHAR(40)  NOT NULL,
  detail     VARCHAR(500) NULL,
  username   VARCHAR(40)  NULL,
  created_at DATETIME     NOT NULL,
  INDEX idx_audit_entity (entity, entity_id),
  INDEX idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------- Pacientes: registro, documento único, búsqueda rápida ----------
CREATE TABLE patients (
  id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
  doc_type                VARCHAR(4)   NOT NULL,          -- DNI, CE, PAS
  doc_number              VARCHAR(15)  NOT NULL,
  first_name              VARCHAR(80)  NOT NULL,
  last_name               VARCHAR(100) NOT NULL,
  birth_date              DATE         NOT NULL,
  sex                     CHAR(1)      NOT NULL,          -- F, M
  phone                   VARCHAR(20)  NOT NULL,
  email                   VARCHAR(120) NULL,
  address                 VARCHAR(200) NULL,
  district                VARCHAR(80)  NULL,
  emergency_contact_name  VARCHAR(120) NULL,
  emergency_contact_phone VARCHAR(20)  NULL,
  notes                   VARCHAR(500) NULL,
  active                  BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at              DATETIME     NOT NULL,
  created_by              VARCHAR(40)  NULL,
  updated_at              DATETIME     NULL,
  updated_by              VARCHAR(40)  NULL,
  CONSTRAINT uk_patients_doc UNIQUE (doc_type, doc_number),
  INDEX idx_patients_doc_number (doc_number),
  INDEX idx_patients_last_name (last_name),
  INDEX idx_patients_first_name (first_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------- Agenda: odontólogos, horarios y citas ----------
CREATE TABLE dentists (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id   BIGINT      NOT NULL,
  specialty VARCHAR(80) NOT NULL,
  cop       VARCHAR(20) NULL,                 -- colegiatura COP
  color     VARCHAR(9)  NOT NULL DEFAULT '#0E7C7B',
  active    BOOLEAN     NOT NULL DEFAULT TRUE,
  CONSTRAINT uk_dentists_user UNIQUE (user_id),
  CONSTRAINT fk_dentists_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE dentist_schedules (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  dentist_id BIGINT  NOT NULL,
  weekday    TINYINT NOT NULL,                -- 1 = lunes ... 7 = domingo
  start_time TIME    NOT NULL,
  end_time   TIME    NOT NULL,
  CONSTRAINT fk_sched_dentist FOREIGN KEY (dentist_id) REFERENCES dentists(id) ON DELETE CASCADE,
  INDEX idx_sched_dentist_day (dentist_id, weekday)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE appointments (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id    BIGINT       NOT NULL,
  dentist_id    BIGINT       NOT NULL,
  start_at      DATETIME     NOT NULL,
  end_at        DATETIME     NOT NULL,
  duration_min  INT          NOT NULL,
  reason        VARCHAR(200) NOT NULL,
  notes         VARCHAR(500) NULL,
  status        VARCHAR(20)  NOT NULL,        -- PROGRAMADA, CONFIRMADA, EN_ESPERA, EN_ATENCION, ATENDIDA, CANCELADA, NO_ASISTIO
  status_reason VARCHAR(200) NULL,
  arrived_at    DATETIME     NULL,
  created_at    DATETIME     NOT NULL,
  created_by    VARCHAR(40)  NULL,
  updated_at    DATETIME     NULL,
  updated_by    VARCHAR(40)  NULL,
  CONSTRAINT fk_appt_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  CONSTRAINT fk_appt_dentist FOREIGN KEY (dentist_id) REFERENCES dentists(id),
  INDEX idx_appt_dentist_start (dentist_id, start_at),
  INDEX idx_appt_patient_start (patient_id, start_at),
  INDEX idx_appt_start (start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------- Historia clínica, diagnóstico, odontograma y plan ----------
CREATE TABLE clinical_histories (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id          BIGINT       NOT NULL,
  blood_type          VARCHAR(4)   NULL,
  allergies           VARCHAR(500) NULL,
  medical_background  VARCHAR(1000) NULL,
  current_medication  VARCHAR(500) NULL,
  surgeries           VARCHAR(500) NULL,
  habits              VARCHAR(500) NULL,
  family_background   VARCHAR(500) NULL,
  observations        VARCHAR(1000) NULL,
  created_at          DATETIME     NOT NULL,
  updated_at          DATETIME     NULL,
  updated_by          VARCHAR(40)  NULL,
  CONSTRAINT uk_history_patient UNIQUE (patient_id),
  CONSTRAINT fk_history_patient FOREIGN KEY (patient_id) REFERENCES patients(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE diagnoses (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id  BIGINT       NOT NULL,
  dentist_id  BIGINT       NULL,
  cie10_code  VARCHAR(10)  NULL,
  description VARCHAR(500) NOT NULL,
  tooth       SMALLINT     NULL,
  created_at  DATETIME     NOT NULL,
  created_by  VARCHAR(40)  NULL,
  CONSTRAINT fk_diag_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  CONSTRAINT fk_diag_dentist FOREIGN KEY (dentist_id) REFERENCES dentists(id),
  INDEX idx_diag_patient (patient_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE odontogram_entries (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id     BIGINT      NOT NULL,
  tooth          SMALLINT    NOT NULL,        -- notación FDI 11..48
  face           CHAR(1)     NOT NULL,        -- V, L, M, D, O, X (pieza completa)
  condition_code VARCHAR(20) NOT NULL,
  note           VARCHAR(200) NULL,
  recorded_at    DATETIME    NOT NULL,
  recorded_by    VARCHAR(40) NULL,
  CONSTRAINT fk_odo_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  CONSTRAINT uk_odo_patient_tooth_face UNIQUE (patient_id, tooth, face)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE procedures (
  id       BIGINT AUTO_INCREMENT PRIMARY KEY,
  code     VARCHAR(12)   NOT NULL,
  name     VARCHAR(120)  NOT NULL,
  category VARCHAR(40)   NOT NULL,
  price    DECIMAL(10,2) NOT NULL,
  active   BOOLEAN       NOT NULL DEFAULT TRUE,
  CONSTRAINT uk_procedures_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE treatment_plans (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id BIGINT       NOT NULL,
  dentist_id BIGINT       NULL,
  title      VARCHAR(120) NOT NULL,
  notes      VARCHAR(500) NULL,
  status     VARCHAR(20)  NOT NULL,           -- ACTIVO, COMPLETADO, ANULADO
  created_at DATETIME     NOT NULL,
  created_by VARCHAR(40)  NULL,
  CONSTRAINT fk_plan_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  CONSTRAINT fk_plan_dentist FOREIGN KEY (dentist_id) REFERENCES dentists(id),
  INDEX idx_plan_patient (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE treatment_items (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id      BIGINT        NOT NULL,
  procedure_id BIGINT        NOT NULL,
  tooth        SMALLINT      NULL,
  price        DECIMAL(10,2) NOT NULL,
  status       VARCHAR(20)   NOT NULL,        -- PENDIENTE, EN_PROCESO, REALIZADO
  done_at      DATETIME      NULL,
  invoice_id   BIGINT        NULL,
  sort_order   INT           NOT NULL DEFAULT 0,
  CONSTRAINT fk_item_plan FOREIGN KEY (plan_id) REFERENCES treatment_plans(id),
  CONSTRAINT fk_item_proc FOREIGN KEY (procedure_id) REFERENCES procedures(id),
  INDEX idx_item_plan (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE evolutions (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id      BIGINT        NOT NULL,
  item_id      BIGINT        NULL,
  dentist_id   BIGINT        NULL,
  session_date DATE          NOT NULL,
  notes        VARCHAR(1000) NOT NULL,
  created_at   DATETIME      NOT NULL,
  created_by   VARCHAR(40)   NULL,
  CONSTRAINT fk_evo_plan FOREIGN KEY (plan_id) REFERENCES treatment_plans(id),
  CONSTRAINT fk_evo_item FOREIGN KEY (item_id) REFERENCES treatment_items(id),
  INDEX idx_evo_plan (plan_id, session_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------- Facturación: comprobantes, series y envíos a SUNAT ----------
CREATE TABLE invoice_series (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  doc_type    CHAR(2)    NOT NULL,            -- 01 factura, 03 boleta
  series      VARCHAR(4) NOT NULL,
  last_number INT        NOT NULL DEFAULT 0,
  active      BOOLEAN    NOT NULL DEFAULT TRUE,
  CONSTRAINT uk_series UNIQUE (series)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE invoices (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  doc_type            CHAR(2)       NOT NULL,
  series              VARCHAR(4)    NOT NULL,
  number              INT           NOT NULL,
  issue_date          DATE          NOT NULL,
  issue_time          TIME          NOT NULL,
  patient_id          BIGINT        NULL,
  customer_doc_type   CHAR(1)       NOT NULL,  -- 0 sin doc, 1 DNI, 4 CE, 6 RUC, 7 pasaporte
  customer_doc_number VARCHAR(15)   NOT NULL,
  customer_name       VARCHAR(200)  NOT NULL,
  customer_address    VARCHAR(200)  NULL,
  currency            CHAR(3)       NOT NULL DEFAULT 'PEN',
  taxable_amount      DECIMAL(12,2) NOT NULL,
  igv_amount          DECIMAL(12,2) NOT NULL,
  total_amount        DECIMAL(12,2) NOT NULL,
  status              VARCHAR(20)   NOT NULL,  -- GENERADO, ACEPTADO, OBSERVADO, RECHAZADO, ERROR
  xml_content         MEDIUMTEXT    NULL,
  hash_code           VARCHAR(64)   NULL,
  sunat_code          VARCHAR(10)   NULL,
  sunat_description   VARCHAR(500)  NULL,
  cdr_content         MEDIUMTEXT    NULL,
  sent_at             DATETIME      NULL,
  created_at          DATETIME      NOT NULL,
  created_by          VARCHAR(40)   NULL,
  CONSTRAINT uk_invoice_number UNIQUE (series, number),
  CONSTRAINT fk_invoice_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  INDEX idx_invoice_issue (issue_date),
  INDEX idx_invoice_customer (customer_doc_number),
  INDEX idx_invoice_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE invoice_items (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  invoice_id        BIGINT        NOT NULL,
  line_number       INT           NOT NULL,
  description       VARCHAR(250)  NOT NULL,
  quantity          DECIMAL(10,2) NOT NULL,
  unit_price        DECIMAL(12,2) NOT NULL,   -- con IGV
  unit_value        DECIMAL(12,4) NOT NULL,   -- sin IGV
  line_value        DECIMAL(12,2) NOT NULL,   -- valor de venta (sin IGV)
  line_igv          DECIMAL(12,2) NOT NULL,
  line_total        DECIMAL(12,2) NOT NULL,
  treatment_item_id BIGINT        NULL,
  CONSTRAINT fk_invitem_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE,
  INDEX idx_invitem_invoice (invoice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sunat_submissions (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  invoice_id           BIGINT       NOT NULL,
  attempt_at           DATETIME     NOT NULL,
  environment          VARCHAR(20)  NOT NULL,
  status               VARCHAR(20)  NOT NULL,
  response_code        VARCHAR(10)  NULL,
  response_description VARCHAR(500) NULL,
  duration_ms          INT          NULL,
  created_by           VARCHAR(40)  NULL,
  CONSTRAINT fk_sub_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE,
  INDEX idx_sub_invoice (invoice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
