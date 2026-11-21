-- v2.7.0: seguimiento y marketing.
-- Tareas por paciente o lead, segmentos guardados, campañas, cumpleaños y embudo de ventas (leads).

-- Tareas: llamar, cobrar, recordar… con responsable y fecha. Pueden ser de un paciente, de un lead o generales.
CREATE TABLE tasks (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id    BIGINT       NULL,
  lead_id       BIGINT       NULL,
  kind          VARCHAR(20)  NOT NULL,                 -- LLAMAR, COBRAR, RECORDAR, WHATSAPP, OTRO
  title         VARCHAR(150) NOT NULL,
  notes         VARCHAR(1000) NULL,
  assignee_id   BIGINT       NULL,                     -- usuario responsable (NULL = sin asignar)
  due_date      DATE         NOT NULL,
  due_time      TIME         NULL,
  priority      VARCHAR(10)  NOT NULL DEFAULT 'NORMAL', -- NORMAL, ALTA
  status        VARCHAR(12)  NOT NULL DEFAULT 'PENDIENTE', -- PENDIENTE, HECHA, CANCELADA
  result        VARCHAR(500) NULL,                     -- qué pasó al cerrarla
  origin        VARCHAR(20)  NOT NULL DEFAULT 'MANUAL', -- MANUAL, SEGMENTO, CUMPLEANOS, LEAD, CAMPANA
  origin_ref    VARCHAR(150) NULL,
  created_by    VARCHAR(60)  NOT NULL,
  created_at    DATETIME     NOT NULL,
  updated_at    DATETIME     NULL,
  completed_at  DATETIME     NULL,
  completed_by  VARCHAR(60)  NULL,
  INDEX ix_tasks_open (status, due_date),
  INDEX ix_tasks_patient (patient_id, status),
  INDEX ix_tasks_assignee (assignee_id, status),
  INDEX ix_tasks_lead (lead_id),
  CONSTRAINT fk_tasks_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  CONSTRAINT fk_tasks_assignee FOREIGN KEY (assignee_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Segmentos: filtros de pacientes guardados con un nombre (se recalculan cada vez que se usan).
CREATE TABLE segments (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  name         VARCHAR(80)  NOT NULL,
  description  VARCHAR(300) NULL,
  filters      JSON         NOT NULL,
  created_by   VARCHAR(60)  NOT NULL,
  created_at   DATETIME     NOT NULL,
  updated_by   VARCHAR(60)  NULL,
  updated_at   DATETIME     NULL,
  CONSTRAINT uk_segments_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Campañas: mensaje a un segmento. Solo llegan a pacientes que aceptaron recibir publicidad.
CREATE TABLE campaigns (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(100) NOT NULL,
  segment_id    BIGINT       NULL,
  filters       JSON         NOT NULL,                  -- copia de los filtros al momento de enviar
  channel       VARCHAR(10)  NOT NULL DEFAULT 'PREFERIDO', -- PREFERIDO, WHATSAPP, EMAIL
  subject       VARCHAR(150) NOT NULL,
  body          VARCHAR(1000) NOT NULL,
  status        VARCHAR(12)  NOT NULL DEFAULT 'BORRADOR', -- BORRADOR, PROGRAMADA, ENVIADA, CANCELADA
  scheduled_at  DATETIME     NULL,
  sent_at       DATETIME     NULL,
  recipients    INT          NOT NULL DEFAULT 0,
  created_by    VARCHAR(60)  NOT NULL,
  created_at    DATETIME     NOT NULL,
  updated_by    VARCHAR(60)  NULL,
  updated_at    DATETIME     NULL,
  INDEX ix_campaigns_due (status, scheduled_at),
  CONSTRAINT fk_campaigns_segment FOREIGN KEY (segment_id) REFERENCES segments(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE campaign_recipients (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  campaign_id      BIGINT      NOT NULL,
  patient_id       BIGINT      NOT NULL,
  channel          VARCHAR(10) NOT NULL,
  notification_id  BIGINT      NULL,
  created_at       DATETIME    NOT NULL,
  CONSTRAINT uk_campaign_recipient UNIQUE (campaign_id, patient_id, channel),
  CONSTRAINT fk_cr_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id),
  CONSTRAINT fk_cr_patient FOREIGN KEY (patient_id) REFERENCES patients(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Embudo de ventas: personas interesadas que aún no son pacientes (o que vienen por un tratamiento nuevo).
CREATE TABLE leads (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  full_name        VARCHAR(120) NOT NULL,
  phone            VARCHAR(15)  NULL,
  email            VARCHAR(120) NULL,
  source           VARCHAR(30)  NOT NULL,
  interest         VARCHAR(150) NULL,                   -- qué tratamiento le interesa
  procedure_id     BIGINT       NULL,
  estimated_value  DECIMAL(10,2) NULL,
  stage            VARCHAR(20)  NOT NULL DEFAULT 'NUEVO', -- NUEVO, CONTACTADO, CITA_AGENDADA, ASISTIO, GANADO, PERDIDO
  assignee_id      BIGINT       NULL,
  patient_id       BIGINT       NULL,
  appointment_id   BIGINT       NULL,
  lost_reason      VARCHAR(200) NULL,
  lost_from_stage  VARCHAR(20)  NULL,                   -- en qué etapa estaba cuando se perdió (para el embudo)
  notes            VARCHAR(1000) NULL,
  stage_changed_at DATETIME     NOT NULL,
  closed_at        DATETIME     NULL,
  created_by       VARCHAR(60)  NOT NULL,
  created_at       DATETIME     NOT NULL,
  updated_at       DATETIME     NULL,
  INDEX ix_leads_stage (stage),
  INDEX ix_leads_phone (phone),
  INDEX ix_leads_patient (patient_id),
  CONSTRAINT fk_leads_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  CONSTRAINT fk_leads_assignee FOREIGN KEY (assignee_id) REFERENCES users(id),
  CONSTRAINT fk_leads_procedure FOREIGN KEY (procedure_id) REFERENCES procedures(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE lead_events (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  lead_id     BIGINT       NOT NULL,
  kind        VARCHAR(20)  NOT NULL,                    -- CREADO, ETAPA, NOTA, PACIENTE, CITA
  detail      VARCHAR(500) NOT NULL,
  created_by  VARCHAR(60)  NOT NULL,
  created_at  DATETIME     NOT NULL,
  INDEX ix_lead_events (lead_id, created_at),
  CONSTRAINT fk_lead_events FOREIGN KEY (lead_id) REFERENCES leads(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE tasks ADD CONSTRAINT fk_tasks_lead FOREIGN KEY (lead_id) REFERENCES leads(id);

-- Consentimiento para publicidad (Ley 29733 y Código de Protección al Consumidor, art. 58): aparte del de datos.
-- NULL = no se le preguntó; TRUE = aceptó; FALSE = no aceptó o pidió la baja.
ALTER TABLE patients
  ADD COLUMN marketing_consent    BOOLEAN  NULL,
  ADD COLUMN marketing_consent_at DATETIME NULL;

INSERT INTO legal_documents (code, version, title, body, published_at) VALUES
('PUBLICIDAD', '2026.1', 'Consentimiento para recibir promociones y novedades',
'CLÍNICA DENTAL CÁRDENAS podrá enviarte por WhatsApp o correo electrónico promociones, campañas de salud bucal, saludos de cumpleaños y novedades de la clínica.

Este consentimiento es voluntario y aparte del tratamiento de tus datos para tu atención: si no lo aceptas, igual recibirás los avisos de tus citas y tratamientos.

Puedes retirarlo cuando quieras respondiendo BAJA a cualquier mensaje, pidiéndolo en recepción o escribiendo al correo de la clínica. Desde ese momento no te enviaremos más promociones.

Base legal: Ley N.° 29733, Ley de Protección de Datos Personales, y su Reglamento (D.S. N.° 016-2024-JUS); y artículo 58 del Código de Protección y Defensa del Consumidor (Ley N.° 29571).', NOW());

INSERT INTO message_templates (code, category, name, description, subject, body, default_body, buttons, sort_order, updated_at, updated_by) VALUES
('CUMPLEANOS', 'MARKETING', 'Saludo de cumpleaños', 'Se envía el día del cumpleaños a quienes no pidieron la baja de promociones.',
 '¡Feliz cumpleaños, {paciente}! · {clinica}',
 '¡Feliz cumpleaños, {paciente}! 🎂\n\nTodo el equipo de {clinica} te desea un día lleno de sonrisas. Gracias por confiarnos tu salud bucal.\n\nSi quieres agendar tu control, reserva aquí: {enlace_citas}',
 '¡Feliz cumpleaños, {paciente}! 🎂\n\nTodo el equipo de {clinica} te desea un día lleno de sonrisas. Gracias por confiarnos tu salud bucal.\n\nSi quieres agendar tu control, reserva aquí: {enlace_citas}',
 NULL, 90, NOW(), 'sistema');

-- El saludo automático empieza apagado: lo enciende administración cuando revise el texto.
INSERT INTO app_settings (k, v, updated_at, updated_by) VALUES
('cumpleanos_activo', 'false', NOW(), 'sistema'),
('cumpleanos_hora', '09:00', NOW(), 'sistema'),
('cumpleanos_tarea', 'false', NOW(), 'sistema'),
('leads_whatsapp_auto', 'true', NOW(), 'sistema');

INSERT INTO permissions (code, group_name, label, sort_order) VALUES
('TAREAS', 'Seguimiento', 'Tareas: ver, crear y cerrar tareas de pacientes y leads', 72),
('LEADS', 'Seguimiento', 'Embudo de ventas: registrar y mover leads', 73),
('MARKETING', 'Comunicación', 'Segmentos, campañas y cumpleaños', 74);
INSERT INTO role_permissions (role_code, permission_code) VALUES
('ADMIN', 'TAREAS'), ('RECEPCION', 'TAREAS'), ('ODONTOLOGO', 'TAREAS'), ('CAJA', 'TAREAS'), ('DIRECTOR', 'TAREAS'),
('ADMIN', 'LEADS'), ('RECEPCION', 'LEADS'), ('DIRECTOR', 'LEADS'),
('ADMIN', 'MARKETING'), ('DIRECTOR', 'MARKETING');
