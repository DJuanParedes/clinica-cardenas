-- v2.8.0 (paso 14): consentimientos informados con plantillas editables y firma a distancia.
-- La clínica edita los textos; al enviarlo se guarda una copia del texto (con los datos del paciente) y un enlace
-- firmado. El paciente (o su apoderado) lo lee y firma desde su celular; se genera el PDF en su ficha.

CREATE TABLE consent_templates (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  code        VARCHAR(40)  NOT NULL,
  name        VARCHAR(120) NOT NULL,
  body        TEXT         NOT NULL,
  version     INT          NOT NULL DEFAULT 1,
  active      BOOLEAN      NOT NULL DEFAULT TRUE,
  sort_order  INT          NOT NULL DEFAULT 0,
  updated_by  VARCHAR(60)  NOT NULL,
  updated_at  DATETIME     NOT NULL,
  CONSTRAINT uk_consent_templates UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE consent_requests (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id         BIGINT       NOT NULL,
  template_id        BIGINT       NOT NULL,
  template_version   INT          NOT NULL,
  title              VARCHAR(150) NOT NULL,
  body               TEXT         NOT NULL,      -- texto final que se firma (no cambia aunque se edite la plantilla)
  body_hash          CHAR(64)     NOT NULL,      -- SHA-256 del texto, va impreso en el PDF
  dentist_id         BIGINT       NULL,
  status             VARCHAR(12)  NOT NULL DEFAULT 'PENDIENTE', -- PENDIENTE, FIRMADO, ANULADO
  channel            VARCHAR(12)  NOT NULL,      -- WHATSAPP, EMAIL, PRESENCIAL (se firma en la tablet de la clínica)
  sent_to            VARCHAR(120) NULL,
  expires_at         DATETIME     NOT NULL,
  opened_at          DATETIME     NULL,
  signed_at          DATETIME     NULL,
  signer_name        VARCHAR(120) NULL,
  signer_doc         VARCHAR(15)  NULL,
  signer_relationship VARCHAR(20) NULL,          -- PACIENTE, MADRE, PADRE, TUTOR
  signer_ip          VARCHAR(45)  NULL,
  signer_agent       VARCHAR(250) NULL,
  file_id            BIGINT       NULL,
  cancel_reason      VARCHAR(200) NULL,
  created_by         VARCHAR(60)  NOT NULL,
  created_at         DATETIME     NOT NULL,
  INDEX ix_consent_req_patient (patient_id, status),
  INDEX ix_consent_req_status (status, created_at),
  CONSTRAINT fk_cr2_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  CONSTRAINT fk_cr2_template FOREIGN KEY (template_id) REFERENCES consent_templates(id),
  CONSTRAINT fk_cr2_file FOREIGN KEY (file_id) REFERENCES patient_files(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO consent_templates (code, name, body, sort_order, updated_by, updated_at) VALUES
('GENERAL', 'Atención odontológica general',
'Yo, {firmante}, identificado(a) con {documento_firmante}, en mi condición de {condicion}, declaro que el/la {odontologo} de {clinica} me ha explicado de forma clara y comprensible el diagnóstico y el tratamiento propuesto:

{procedimiento}

Se me informó en qué consiste, sus beneficios, las alternativas posibles, los riesgos y molestias más frecuentes (dolor, inflamación, sensibilidad, sangrado leve) y las consecuencias de no realizarlo. Tuve la oportunidad de hacer preguntas y todas fueron respondidas.

Autorizo la aplicación de anestesia local si fuese necesaria y que se tomen las radiografías y fotografías clínicas que el tratamiento requiera, las cuales forman parte de mi historia clínica.

Sé que puedo retirar este consentimiento en cualquier momento antes del procedimiento, sin que ello afecte mi atención.

Ley N.° 29414, que establece los derechos de las personas usuarias de los servicios de salud.', 10, 'sistema', NOW()),
('EXODONCIA', 'Extracción dental (exodoncia)',
'Yo, {firmante}, identificado(a) con {documento_firmante}, en mi condición de {condicion}, autorizo al/la {odontologo} de {clinica} a realizar la extracción de: {procedimiento}{pieza}.

Se me explicó que, después de la extracción, pueden presentarse dolor, inflamación, sangrado, hematoma, limitación para abrir la boca y, con menor frecuencia, infección (alveolitis), lesión de dientes vecinos, comunicación con el seno maxilar o adormecimiento temporal del labio o la lengua.

Me comprometo a seguir las indicaciones posteriores: no enjuagarme ni escupir con fuerza las primeras 24 horas, no fumar, aplicar frío, tomar la medicación indicada y volver al control.

Declaro haber informado mis enfermedades, alergias y los medicamentos que tomo, en especial anticoagulantes.', 20, 'sistema', NOW()),
('ENDODONCIA', 'Tratamiento de conductos (endodoncia)',
'Yo, {firmante}, identificado(a) con {documento_firmante}, en mi condición de {condicion}, autorizo al/la {odontologo} de {clinica} a realizar el tratamiento de conductos de: {procedimiento}{pieza}.

Se me explicó que el objetivo es conservar el diente eliminando el nervio infectado o inflamado, que puede requerir varias sesiones y que, al terminar, el diente necesitará una restauración definitiva (resina, incrustación o corona) para no fracturarse.

Riesgos posibles: dolor o inflamación después de la sesión, fractura de un instrumento dentro del conducto, perforación, conductos calcificados que no puedan tratarse y, en algunos casos, que el tratamiento no tenga éxito y se requiera repetirlo, una cirugía o la extracción.', 30, 'sistema', NOW()),
('ORTODONCIA', 'Tratamiento de ortodoncia',
'Yo, {firmante}, identificado(a) con {documento_firmante}, en mi condición de {condicion}, autorizo al/la {odontologo} de {clinica} a iniciar el tratamiento de ortodoncia: {procedimiento}.

Se me explicó la duración estimada, la frecuencia de los controles y que el resultado depende en gran parte de mi colaboración: asistir a los controles, mantener una higiene muy cuidadosa, usar los elásticos o aparatos como se indique y evitar alimentos duros o pegajosos.

Riesgos posibles: manchas blancas o caries por mala higiene, inflamación de encías, molestias los primeros días después de cada control, reabsorción radicular, problemas de la articulación temporomandibular y recidiva si no se usan los retenedores al terminar.

Autorizo la toma de fotografías y radiografías al inicio, durante y al final del tratamiento para el seguimiento clínico.', 40, 'sistema', NOW()),
('IMPLANTE', 'Implante dental',
'Yo, {firmante}, identificado(a) con {documento_firmante}, en mi condición de {condicion}, autorizo al/la {odontologo} de {clinica} a colocar un implante dental: {procedimiento}{pieza}.

Se me explicó que es una cirugía en la que se coloca un tornillo de titanio en el hueso, que necesita un tiempo de integración de varios meses antes de colocar la corona y que puede requerir injerto de hueso.

Riesgos posibles: dolor, inflamación, hematoma, infección, adormecimiento temporal o permanente del labio, lesión del seno maxilar o de dientes vecinos y que el implante no se integre y deba retirarse. Fumar, la diabetes no controlada y la mala higiene aumentan el riesgo de fracaso.', 50, 'sistema', NOW()),
('BLANQUEAMIENTO', 'Blanqueamiento dental',
'Yo, {firmante}, identificado(a) con {documento_firmante}, en mi condición de {condicion}, autorizo al/la {odontologo} de {clinica} a realizar un blanqueamiento dental: {procedimiento}.

Se me explicó que el resultado varía en cada persona, que no cambia el color de resinas, coronas ni carillas, y que puede ser necesario repetirlo con el tiempo.

Es frecuente una sensibilidad dental pasajera y, a veces, irritación de las encías. Me comprometo a evitar alimentos y bebidas que manchan (café, té, vino, gaseosas oscuras) y a no fumar en los días indicados.', 60, 'sistema', NOW());

INSERT INTO permissions (code, group_name, label, sort_order) VALUES
('CONSENTIMIENTOS', 'Atención clínica', 'Editar las plantillas de consentimiento informado', 35);
INSERT INTO role_permissions (role_code, permission_code) VALUES ('ADMIN', 'CONSENTIMIENTOS'), ('ODONTOLOGO', 'CONSENTIMIENTOS'), ('DIRECTOR', 'CONSENTIMIENTOS');
