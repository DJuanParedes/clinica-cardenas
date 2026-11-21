-- Versión 2.6.0: plantillas de mensajes editables, automatizaciones de citas y enlace público de reservas.

-- ---------------------------------------------------------------- 5. Plantillas de mensajes
-- Texto con variables {paciente}, {fecha}, {hora}… El mismo texto sirve para WhatsApp (*negrita*) y correo.
-- default_body guarda el texto original: si la clínica no lo cambió, en WhatsApp real se usa la plantilla aprobada
-- por Meta con el mismo formato; si lo cambió, se envía el texto editado.
CREATE TABLE message_templates (
  code         VARCHAR(30)  PRIMARY KEY,
  category     VARCHAR(20)  NOT NULL,          -- CITA, AUTOMATIZACION, RESERVA
  name         VARCHAR(80)  NOT NULL,
  description  VARCHAR(250) NULL,
  subject      VARCHAR(150) NOT NULL,          -- asunto del correo
  body         TEXT         NOT NULL,
  default_body TEXT         NOT NULL,
  buttons      VARCHAR(200) NULL,              -- JSON: ["Confirmar","No podré asistir"] (máx. 3, 20 caracteres)
  sort_order   INT          NOT NULL DEFAULT 0,
  updated_at   DATETIME     NOT NULL,
  updated_by   VARCHAR(40)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO message_templates (code, category, name, description, subject, body, default_body, buttons, sort_order, updated_at, updated_by) VALUES
('CITA_REGISTRADA', 'CITA', 'Cita registrada', 'Se envía al programar una cita desde recepción o la app.',
 'Tu cita quedó registrada · {clinica}',
 'Hola {paciente}, tu cita en {clinica} quedó registrada:\n\n📅 {fecha_hora}\n🦷 {odontologo}\n📍 {direccion}\n\nTe enviaremos un recordatorio antes de la cita.',
 'Hola {paciente}, tu cita en {clinica} quedó registrada:\n\n📅 {fecha_hora}\n🦷 {odontologo}\n📍 {direccion}\n\nTe enviaremos un recordatorio antes de la cita.',
 NULL, 1, NOW(), 'sistema'),
('CAMBIO_CITA', 'CITA', 'Cita reprogramada', 'Se envía al cambiar la fecha, hora u odontólogo.',
 'Tu cita fue reprogramada · {clinica}',
 'Hola {paciente}, tu cita del {fecha_anterior} cambió. Este es el nuevo horario:\n\n📅 {fecha_hora}\n🦷 {odontologo}\n📍 {direccion}\n\nSi el nuevo horario no te acomoda, escríbenos.',
 'Hola {paciente}, tu cita del {fecha_anterior} cambió. Este es el nuevo horario:\n\n📅 {fecha_hora}\n🦷 {odontologo}\n📍 {direccion}\n\nSi el nuevo horario no te acomoda, escríbenos.',
 NULL, 2, NOW(), 'sistema'),
('CANCELACION', 'CITA', 'Cita cancelada', 'Se envía al cancelar una cita.',
 'Tu cita fue cancelada · {clinica}',
 'Hola {paciente}, tu cita del {fecha_hora} con {odontologo} fue cancelada{motivo_cancelacion}.\n\nPara programar una nueva cita entra a {enlace_citas} o llámanos al {telefono_clinica}.',
 'Hola {paciente}, tu cita del {fecha_hora} con {odontologo} fue cancelada{motivo_cancelacion}.\n\nPara programar una nueva cita entra a {enlace_citas} o llámanos al {telefono_clinica}.',
 NULL, 3, NOW(), 'sistema'),
('RESERVA_WEB', 'RESERVA', 'Reserva desde la web', 'Se envía cuando el paciente reserva desde el enlace público.',
 'Recibimos tu reserva · {clinica}',
 'Hola {paciente}, recibimos tu reserva desde nuestra web:\n\n📅 {fecha_hora}\n🦷 {odontologo}\n📍 {direccion}\n\nPor favor confirma tu asistencia.',
 'Hola {paciente}, recibimos tu reserva desde nuestra web:\n\n📅 {fecha_hora}\n🦷 {odontologo}\n📍 {direccion}\n\nPor favor confirma tu asistencia.',
 '["Confirmar","No podré asistir"]', 4, NOW(), 'sistema'),
('AUTO_5D', 'AUTOMATIZACION', 'Aviso unos días antes', 'Para que el paciente tenga presente la cita.',
 'Tienes una cita pronto · {clinica}',
 'Hola {paciente}, te saludamos de {clinica}. En unos días tenemos tu cita y queremos que la tengas presente:\n\n📅 {fecha_hora}\n🦷 {odontologo}\n\nUn día antes te enviaremos un recordatorio. ¡Nos vemos pronto!',
 'Hola {paciente}, te saludamos de {clinica}. En unos días tenemos tu cita y queremos que la tengas presente:\n\n📅 {fecha_hora}\n🦷 {odontologo}\n\nUn día antes te enviaremos un recordatorio. ¡Nos vemos pronto!',
 NULL, 5, NOW(), 'sistema'),
('RECORDATORIO', 'AUTOMATIZACION', 'Recordatorio con confirmación', 'Pide confirmar la asistencia (se confirma hasta 2 horas antes).',
 'Recordatorio de tu cita · {clinica}',
 'Hola {paciente}, te recordamos tu cita en {clinica}:\n\n📅 {fecha_hora}\n🦷 {odontologo}\n📍 {direccion}\n\nPor favor confirma tu asistencia.',
 'Hola {paciente}, te recordamos tu cita en {clinica}:\n\n📅 {fecha_hora}\n🦷 {odontologo}\n📍 {direccion}\n\nPor favor confirma tu asistencia.',
 '["Confirmar","No podré asistir"]', 6, NOW(), 'sistema'),
('AUTO_DIA', 'AUTOMATIZACION', 'El día de la cita', 'Recordatorio corto la mañana de la cita.',
 'Hoy es tu cita · {clinica}',
 '¡Buenos días, {paciente}! Hoy es tu cita a las *{hora}* con {odontologo}.\n\n📍 {direccion}\n\nTe recomendamos llegar 10 minutos antes.',
 '¡Buenos días, {paciente}! Hoy es tu cita a las *{hora}* con {odontologo}.\n\n📍 {direccion}\n\nTe recomendamos llegar 10 minutos antes.',
 '["Confirmar","No podré asistir"]', 7, NOW(), 'sistema'),
('AGRADECIMIENTO', 'AUTOMATIZACION', 'Agradecimiento después de la cita', 'Solo si la cita quedó como atendida.',
 'Gracias por tu visita · {clinica}',
 'Gracias por visitarnos hoy, {paciente}. Si sientes alguna molestia después del tratamiento, escríbenos por aquí y te ayudamos. ¡Cuida tu sonrisa! 😊',
 'Gracias por visitarnos hoy, {paciente}. Si sientes alguna molestia después del tratamiento, escríbenos por aquí y te ayudamos. ¡Cuida tu sonrisa! 😊',
 NULL, 8, NOW(), 'sistema'),
('ENCUESTA', 'AUTOMATIZACION', 'Encuesta de satisfacción', 'Solo si la cita quedó como atendida. El enlace abre una calificación de 1 a 5 estrellas.',
 '¿Cómo te atendimos? · {clinica}',
 'Hola {paciente}, ¿cómo fue tu atención con {odontologo}? Tu opinión nos ayuda a mejorar. Califícanos en 1 minuto:\n\n{enlace_encuesta}',
 'Hola {paciente}, ¿cómo fue tu atención con {odontologo}? Tu opinión nos ayuda a mejorar. Califícanos en 1 minuto:\n\n{enlace_encuesta}',
 NULL, 9, NOW(), 'sistema');

-- ---------------------------------------------------------------- 6. Automatizaciones
-- kind: ANTES (amount + unit antes del inicio; con unit DIAS se envía a send_time), DIA (el día de la cita a
-- send_time) o DESPUES (al quedar atendida: amount + unit después del fin; con DIAS a send_time).
CREATE TABLE automation_rules (
  code          VARCHAR(30)  PRIMARY KEY,
  name          VARCHAR(80)  NOT NULL,
  kind          VARCHAR(10)  NOT NULL,
  amount        INT          NOT NULL DEFAULT 0,
  unit          VARCHAR(6)   NOT NULL DEFAULT 'HORAS',  -- HORAS, DIAS
  send_time     TIME         NULL,
  template_code VARCHAR(30)  NOT NULL,
  notif_type    VARCHAR(30)  NOT NULL,
  active        BOOLEAN      NOT NULL DEFAULT TRUE,
  sort_order    INT          NOT NULL DEFAULT 0,
  updated_at    DATETIME     NOT NULL,
  updated_by    VARCHAR(40)  NOT NULL,
  CONSTRAINT fk_rule_template FOREIGN KEY (template_code) REFERENCES message_templates(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO automation_rules (code, name, kind, amount, unit, send_time, template_code, notif_type, active, sort_order, updated_at, updated_by) VALUES
('AUTO_5D',        '5 días antes',                    'ANTES',   5,  'DIAS',  '10:00:00', 'AUTO_5D',        'AVISO_PREVIO',   TRUE, 1, NOW(), 'sistema'),
('AUTO_1D',        '1 día antes (con confirmación)',  'ANTES',   24, 'HORAS', NULL,       'RECORDATORIO',   'RECORDATORIO',   TRUE, 2, NOW(), 'sistema'),
('AUTO_DIA',       'El día de la cita',               'DIA',     0,  'HORAS', '07:30:00', 'AUTO_DIA',       'RECORDATORIO_DIA', TRUE, 3, NOW(), 'sistema'),
('AUTO_GRACIAS',   'Agradecimiento (después)',        'DESPUES', 2,  'HORAS', NULL,       'AGRADECIMIENTO', 'AGRADECIMIENTO', TRUE, 4, NOW(), 'sistema'),
('AUTO_ENCUESTA',  'Encuesta (después)',              'DESPUES', 1,  'DIAS',  '18:00:00', 'ENCUESTA',       'ENCUESTA',       TRUE, 5, NOW(), 'sistema');

ALTER TABLE notifications
  ADD COLUMN template_code VARCHAR(30)  NULL,
  ADD COLUMN wa_buttons    VARCHAR(200) NULL,
  ADD INDEX idx_notif_template (template_code, created_at);

-- ---------------------------------------------------------------- 7. Enlace público de citas y prerregistro
INSERT INTO app_settings (k, v, updated_at, updated_by) VALUES
('reserva_publica_activa', 'true', NOW(), 'sistema'),
('prerregistro_activo', 'true', NOW(), 'sistema');

ALTER TABLE appointments ADD COLUMN booked_via VARCHAR(10) NULL;  -- RECEPCION, APP, WHATSAPP, WEB

INSERT INTO permissions (code, group_name, label, sort_order) VALUES
('AUTOMATIZACIONES', 'Comunicación', 'Plantillas de mensajes, automatizaciones y enlaces públicos', 71);
INSERT INTO role_permissions (role_code, permission_code) VALUES ('ADMIN', 'AUTOMATIZACIONES');
