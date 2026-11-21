-- v2.9.0: Libro de Reclamaciones virtual (Código de Protección y Defensa del Consumidor, art. 150, y su
-- reglamento DS 011-2011-PCM; para servicios de salud, también DS 002-2019-SA de SUSALUD).
-- Cada hoja tiene un número correlativo por año y un código para que el consumidor la consulte.
-- El plazo de respuesta se cuenta en días hábiles (por defecto 15, el más estricto de las dos normas).

CREATE TABLE complaints (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  year              INT          NOT NULL,
  number            INT          NOT NULL,          -- correlativo del año: 2026-000001
  code              VARCHAR(12)  NOT NULL,          -- código de consulta que recibe el consumidor
  kind              VARCHAR(8)   NOT NULL,          -- RECLAMO (disconformidad con el servicio) o QUEJA (atención)
  filed_at          DATETIME     NOT NULL,
  full_name         VARCHAR(150) NOT NULL,
  doc_type          VARCHAR(10)  NOT NULL,
  doc_number        VARCHAR(15)  NOT NULL,
  address           VARCHAR(250) NOT NULL,
  phone             VARCHAR(20)  NULL,
  email             VARCHAR(120) NULL,
  is_minor          BOOLEAN      NOT NULL DEFAULT FALSE,
  guardian_name     VARCHAR(150) NULL,             -- padre, madre o apoderado si el consumidor es menor de edad
  guardian_doc      VARCHAR(15)  NULL,
  service           VARCHAR(250) NOT NULL,         -- bien o servicio contratado
  amount            DECIMAL(10,2) NULL,
  detail            TEXT         NOT NULL,
  request           TEXT         NOT NULL,         -- pedido del consumidor
  patient_id        BIGINT       NULL,             -- si el DNI coincide con un paciente
  status            VARCHAR(12)  NOT NULL DEFAULT 'PENDIENTE', -- PENDIENTE, EN_PROCESO, RESPONDIDO
  due_date          DATE         NOT NULL,
  provider_notes    TEXT         NULL,             -- observaciones y acciones adoptadas por el proveedor
  response          TEXT         NULL,
  responded_at      DATETIME     NULL,
  responded_by      VARCHAR(60)  NULL,
  response_channel  VARCHAR(12)  NULL,             -- EMAIL, DOMICILIO, PRESENCIAL
  response_sent     BOOLEAN      NOT NULL DEFAULT FALSE,
  copy_sent         BOOLEAN      NOT NULL DEFAULT FALSE,
  ip                VARCHAR(45)  NULL,
  user_agent        VARCHAR(250) NULL,
  task_id           BIGINT       NULL,
  CONSTRAINT uk_complaints_number UNIQUE (year, number),
  CONSTRAINT uk_complaints_code UNIQUE (code),
  INDEX ix_complaints_status (status, due_date),
  CONSTRAINT fk_complaints_patient FOREIGN KEY (patient_id) REFERENCES patients(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO app_settings (k, v, updated_at, updated_by) VALUES
('reclamos_plazo_dias', '15', NOW(), 'sistema'),
('reclamos_correo_aviso', '', NOW(), 'sistema');

INSERT INTO permissions (code, group_name, label, sort_order) VALUES
('RECLAMOS', 'Dirección', 'Libro de reclamaciones: ver y responder reclamos y quejas', 95);
INSERT INTO role_permissions (role_code, permission_code) VALUES
('ADMIN', 'RECLAMOS'), ('DIRECTOR', 'RECLAMOS'), ('RECEPCION', 'RECLAMOS');
