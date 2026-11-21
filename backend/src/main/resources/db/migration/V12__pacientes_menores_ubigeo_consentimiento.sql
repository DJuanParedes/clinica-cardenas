-- Registro de pacientes acorde a la normativa peruana:
-- * UBIGEO (INEI): departamento, provincia y distrito con su código de 6 dígitos.
-- * Menores de 18 años (Código Civil, arts. 42-46 y patria potestad): se registra a su representante legal
--   (padre, madre o tutor legal) con su documento; el "contacto de emergencia" queda solo para adultos.
-- * Consentimiento informado para el tratamiento de datos de salud (Ley 29733 y su Reglamento, D.S. 016-2024-JUS):
--   versión del documento aceptado, canal, quién lo registró y, si es presencial, la copia firmada.
ALTER TABLE patients
  ADD COLUMN ubigeo CHAR(6) NULL,
  ADD COLUMN department VARCHAR(60) NULL,
  ADD COLUMN province VARCHAR(60) NULL,
  ADD COLUMN emergency_contact_relationship VARCHAR(30) NULL,
  ADD COLUMN guardian_name VARCHAR(120) NULL,
  ADD COLUMN guardian_doc_type VARCHAR(5) NULL,
  ADD COLUMN guardian_doc_number VARCHAR(15) NULL,
  ADD COLUMN guardian_relationship VARCHAR(20) NULL,
  ADD COLUMN guardian_phone VARCHAR(15) NULL,
  ADD COLUMN consent_version VARCHAR(20) NULL,
  ADD COLUMN consent_channel VARCHAR(20) NULL,
  ADD COLUMN consent_by VARCHAR(60) NULL,
  ADD COLUMN consent_file_id BIGINT NULL;

-- Documentos legales vigentes (versionados): lo que se acepta queda ligado a su versión.
CREATE TABLE legal_documents (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  code        VARCHAR(30)  NOT NULL,          -- PRIVACIDAD, TERMINOS
  version     VARCHAR(20)  NOT NULL,
  title       VARCHAR(150) NOT NULL,
  body        MEDIUMTEXT   NOT NULL,          -- texto con párrafos separados por línea en blanco
  active      BOOLEAN      NOT NULL DEFAULT TRUE,
  published_at DATETIME    NOT NULL,
  CONSTRAINT uk_legal_doc UNIQUE (code, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Cada aceptación (presencial en recepción, en la app o en el portal) con su evidencia.
CREATE TABLE consent_records (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id   BIGINT       NOT NULL,
  doc_code     VARCHAR(30)  NOT NULL,
  doc_version  VARCHAR(20)  NOT NULL,
  channel      VARCHAR(20)  NOT NULL,         -- PRESENCIAL, APP, PORTAL
  accepted_by  VARCHAR(120) NOT NULL,         -- titular o representante legal que acepta
  registered_by VARCHAR(60) NOT NULL,         -- usuario del sistema que lo registra
  file_id      BIGINT       NULL,             -- copia firmada (patient_files) si es presencial
  ip           VARCHAR(45)  NULL,
  accepted_at  DATETIME     NOT NULL,
  revoked_at   DATETIME     NULL,
  CONSTRAINT fk_consent_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  INDEX idx_consent_patient (patient_id, accepted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Los pacientes que ya habían dado su consentimiento quedan registrados con la versión inicial.
INSERT INTO consent_records (patient_id, doc_code, doc_version, channel, accepted_by, registered_by, accepted_at)
SELECT id, 'PRIVACIDAD', '2026.1', 'PRESENCIAL', CONCAT(first_name, ' ', last_name), COALESCE(created_by, 'sistema'), COALESCE(data_consent_at, created_at)
  FROM patients WHERE data_consent = TRUE;
UPDATE patients SET consent_version = '2026.1', consent_channel = 'PRESENCIAL', consent_by = created_by WHERE data_consent = TRUE;

INSERT INTO legal_documents (code, version, title, body, published_at) VALUES
('PRIVACIDAD', '2026.1', 'Política de privacidad y consentimiento para el tratamiento de datos personales',
'CLÍNICA DENTAL CÁRDENAS (en adelante, "la Clínica"), con domicilio en Urb. La Floresta, Chaclacayo, Lima, es titular del banco de datos de pacientes y responsable de su tratamiento, conforme a la Ley N.° 29733, Ley de Protección de Datos Personales, y su Reglamento aprobado por Decreto Supremo N.° 016-2024-JUS.

1. Datos que tratamos. Datos de identificación (nombres, documento, fecha de nacimiento, sexo), de contacto (teléfono, correo, dirección), datos de salud (historia clínica, odontograma, diagnósticos, tratamientos, alergias, imágenes y radiografías) y datos económicos de los servicios (pagos y comprobantes). Los datos de salud son datos sensibles y solo se tratan con su consentimiento expreso y por escrito.

2. Finalidades. (a) Brindar la atención odontológica y llevar la historia clínica, obligatoria según la Ley N.° 26842, Ley General de Salud, y la NTS N.° 139-MINSA/2018/DGAIN; (b) programar citas y enviar recordatorios y avisos por WhatsApp, correo o la app; (c) emitir comprobantes electrónicos ante SUNAT; (d) atender consultas, quejas y reclamos. Finalidades adicionales (encuestas de satisfacción, promociones) requieren su aceptación aparte y puede rechazarlas sin que afecte su atención.

3. Menores de edad. Los datos de menores de 18 años se tratan con el consentimiento de quien ejerce la patria potestad o la tutela, quien firma y acepta en su nombre.

4. Conservación. La historia clínica se conserva por los plazos que establece la NTS N.° 139-MINSA/2018/DGAIN. Los demás datos, mientras dure la relación con la Clínica y los plazos legales (tributarios y de reclamos).

5. Destinatarios. Personal de la Clínica con obligación de confidencialidad; SUNAT para la facturación electrónica; proveedores tecnológicos que actúan como encargados (alojamiento en la nube, mensajería) bajo contrato de confidencialidad. No vendemos ni cedemos sus datos.

6. Seguridad. Acceso con usuario y contraseña, permisos por rol, conexión cifrada (HTTPS), registro de auditoría y copias de seguridad.

7. Sus derechos (ARCO). Puede solicitar el acceso, rectificación, cancelación u oposición al tratamiento de sus datos, y revocar este consentimiento, escribiendo a la Clínica o en recepción. Responderemos en los plazos de ley. Si no está conforme, puede acudir a la Autoridad Nacional de Protección de Datos Personales (Ministerio de Justicia y Derechos Humanos).

8. Aceptación. Al aceptar declara haber leído esta política y autoriza el tratamiento de sus datos personales y de salud para las finalidades indicadas.', NOW()),
('TERMINOS', '2026.1', 'Términos y condiciones de uso de la app y el portal del paciente',
'1. Objeto. La app y el portal permiten al paciente de la Clínica Dental Cárdenas ver y reservar sus citas, consultar sus pagos y comprobantes y recibir avisos.

2. Cuenta. La cuenta es personal. Se activa con el documento del paciente y un código enviado a su celular o correo registrado. El paciente es responsable de no compartir su contraseña.

3. Citas. Las reservas quedan sujetas a la disponibilidad del odontólogo. Las cancelaciones y reprogramaciones deben hacerse con anticipación; la Clínica puede reprogramar por causas de fuerza mayor y avisará al paciente.

4. Pagos. Los pagos en línea se procesan en la página segura de la pasarela de pagos; la Clínica no almacena datos de tarjetas. Cada pago genera su comprobante electrónico.

5. Información clínica. La información de la app es referencial y no reemplaza la consulta con el odontólogo. Ante una urgencia, comuníquese por teléfono con la Clínica.

6. Libro de reclamaciones. El paciente puede registrar quejas o reclamos en el Libro de Reclamaciones de la Clínica, conforme al Código de Protección y Defensa del Consumidor (Ley N.° 29571) y la normativa de SUSALUD.

7. Datos personales. El uso de la app se rige por la Política de privacidad de la Clínica.', NOW());
