-- Versión 2.5.0: ficha del paciente ampliada, anamnesis por plantillas, signos vitales, periodontograma y
-- órdenes de laboratorio.

-- ---------------------------------------------------------------- 1. Ficha del paciente
-- Fuente de captación (de dónde llegó), N° de historia clínica y datos para factura (RUC).
ALTER TABLE patients
  ADD COLUMN source         VARCHAR(30)  NULL,
  ADD COLUMN source_detail  VARCHAR(120) NULL,
  ADD COLUMN hc_number      VARCHAR(20)  NULL,
  ADD COLUMN fiscal_ruc     CHAR(11)     NULL,
  ADD COLUMN fiscal_name    VARCHAR(200) NULL,
  ADD COLUMN fiscal_address VARCHAR(250) NULL;

-- Las historias existentes reciben su número a partir del id (000001, 000002…); se puede cambiar por el de papel.
UPDATE patients SET hc_number = LPAD(id, 6, '0') WHERE hc_number IS NULL;
ALTER TABLE patients ADD UNIQUE INDEX uq_patients_hc (hc_number), ADD INDEX idx_patients_source (source);

CREATE TABLE patient_tags (
  patient_id BIGINT      NOT NULL,
  tag        VARCHAR(40) NOT NULL,
  PRIMARY KEY (patient_id, tag),
  INDEX idx_tag (tag),
  CONSTRAINT fk_tag_patient FOREIGN KEY (patient_id) REFERENCES patients(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Familiares vinculados: pueden ser pacientes de la clínica (related_patient_id) o solo un contacto.
CREATE TABLE patient_relations (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id         BIGINT       NOT NULL,
  related_patient_id BIGINT       NULL,
  full_name          VARCHAR(150) NOT NULL,
  doc_type           VARCHAR(5)   NULL,
  doc_number         VARCHAR(15)  NULL,
  relationship       VARCHAR(20)  NOT NULL,   -- PADRE, MADRE, HIJO, CONYUGE, HERMANO, ABUELO, TUTOR, OTRO
  phone              VARCHAR(15)  NULL,
  email              VARCHAR(120) NULL,
  is_guardian        BOOLEAN      NOT NULL DEFAULT FALSE,
  pays               BOOLEAN      NOT NULL DEFAULT FALSE,  -- responsable de pago
  notes              VARCHAR(200) NULL,
  created_at         DATETIME     NOT NULL,
  created_by         VARCHAR(40)  NOT NULL,
  CONSTRAINT fk_rel_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  CONSTRAINT fk_rel_related FOREIGN KEY (related_patient_id) REFERENCES patients(id),
  INDEX idx_rel_patient (patient_id),
  INDEX idx_rel_related (related_patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- 2. Anamnesis y signos vitales
-- Cada plantilla es una lista de preguntas en JSON: [{id, section, label, type: TEXT|YES_NO|CHOICE, options, alert}].
-- alert = true: si la respuesta es "Sí" aparece como alerta clínica en la ficha.
CREATE TABLE anamnesis_templates (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  code       VARCHAR(30)  NOT NULL UNIQUE,
  name       VARCHAR(80)  NOT NULL,
  specialty  VARCHAR(60)  NULL,
  questions  TEXT         NOT NULL,
  active     BOOLEAN      NOT NULL DEFAULT TRUE,
  sort_order INT          NOT NULL DEFAULT 0,
  updated_at DATETIME     NOT NULL,
  updated_by VARCHAR(40)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE anamnesis_records (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id    BIGINT       NOT NULL,
  template_id   BIGINT       NOT NULL,
  template_name VARCHAR(80)  NOT NULL,
  questions     TEXT         NOT NULL,   -- copia de las preguntas al momento de responder
  answers       TEXT         NOT NULL,   -- {preguntaId: {value, note}}
  alerts        VARCHAR(500) NULL,       -- resumen de respuestas con alerta
  created_at    DATETIME     NOT NULL,
  created_by    VARCHAR(40)  NOT NULL,
  updated_at    DATETIME     NULL,
  CONSTRAINT fk_anam_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  CONSTRAINT fk_anam_template FOREIGN KEY (template_id) REFERENCES anamnesis_templates(id),
  INDEX idx_anam_patient (patient_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE vital_signs (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id  BIGINT       NOT NULL,
  taken_at    DATETIME     NOT NULL,
  systolic    SMALLINT     NULL,
  diastolic   SMALLINT     NULL,
  heart_rate  SMALLINT     NULL,
  resp_rate   SMALLINT     NULL,
  temperature DECIMAL(4,1) NULL,
  spo2        SMALLINT     NULL,
  weight      DECIMAL(5,1) NULL,
  height      SMALLINT     NULL,
  glucose     SMALLINT     NULL,
  notes       VARCHAR(250) NULL,
  created_by  VARCHAR(40)  NOT NULL,
  CONSTRAINT fk_vital_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  INDEX idx_vital_patient (patient_id, taken_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO anamnesis_templates (code, name, specialty, sort_order, updated_at, updated_by, questions) VALUES
('ODONTOLOGIA', 'Anamnesis odontológica', 'Odontología general', 1, NOW(), 'sistema', '[
 {"id":"motivo","section":"Motivo de consulta","label":"Motivo de consulta","type":"TEXT"},
 {"id":"tiempo","section":"Enfermedad actual","label":"Tiempo de enfermedad","type":"TEXT"},
 {"id":"sintomas","section":"Enfermedad actual","label":"Signos y síntomas principales","type":"TEXT"},
 {"id":"relato","section":"Enfermedad actual","label":"Relato cronológico","type":"TEXT"},
 {"id":"hta","section":"¿Tiene o ha tenido?","label":"Presión alta","type":"YES_NO","alert":true},
 {"id":"hipotension","section":"¿Tiene o ha tenido?","label":"Presión baja","type":"YES_NO","alert":true},
 {"id":"diabetes","section":"¿Tiene o ha tenido?","label":"Diabetes","type":"YES_NO","alert":true},
 {"id":"cardiaco","section":"¿Tiene o ha tenido?","label":"Enfermedad del corazón o marcapasos","type":"YES_NO","alert":true},
 {"id":"coagulacion","section":"¿Tiene o ha tenido?","label":"Problemas de coagulación o toma anticoagulantes","type":"YES_NO","alert":true},
 {"id":"hepatitis","section":"¿Tiene o ha tenido?","label":"Hepatitis","type":"YES_NO","alert":true},
 {"id":"vih","section":"¿Tiene o ha tenido?","label":"VIH","type":"YES_NO","alert":true},
 {"id":"asma","section":"¿Tiene o ha tenido?","label":"Asma","type":"YES_NO","alert":true},
 {"id":"epilepsia","section":"¿Tiene o ha tenido?","label":"Epilepsia o convulsiones","type":"YES_NO","alert":true},
 {"id":"gastritis","section":"¿Tiene o ha tenido?","label":"Gastritis o úlceras","type":"YES_NO"},
 {"id":"embarazo","section":"¿Tiene o ha tenido?","label":"Embarazo o lactancia","type":"YES_NO","alert":true},
 {"id":"anestesia","section":"¿Tiene o ha tenido?","label":"Reacción a la anestesia dental","type":"YES_NO","alert":true},
 {"id":"fuma","section":"Hábitos","label":"¿Fuma?","type":"YES_NO"},
 {"id":"bruxismo","section":"Hábitos","label":"Aprieta o rechina los dientes","type":"YES_NO"},
 {"id":"cepillado","section":"Hábitos","label":"Cepillado al día","type":"CHOICE","options":["1 vez","2 veces","3 o más"]},
 {"id":"sangrado_encias","section":"Examen","label":"Le sangran las encías","type":"YES_NO"},
 {"id":"comentario","section":"Comentario","label":"Comentario","type":"TEXT"}
]'),
('ODONTOPEDIATRIA', 'Anamnesis odontopediátrica', 'Odontopediatría', 2, NOW(), 'sistema', '[
 {"id":"motivo","section":"Motivo de consulta","label":"Motivo de consulta","type":"TEXT"},
 {"id":"embarazo_madre","section":"Antecedentes prenatales y del parto","label":"Complicaciones en el embarazo o el parto","type":"YES_NO"},
 {"id":"prematuro","section":"Antecedentes prenatales y del parto","label":"Nació prematuro","type":"YES_NO"},
 {"id":"lactancia","section":"Alimentación","label":"Lactancia","type":"CHOICE","options":["Materna","Biberón","Mixta"]},
 {"id":"biberon_noche","section":"Alimentación","label":"Toma biberón o pecho de noche","type":"YES_NO"},
 {"id":"azucar","section":"Alimentación","label":"Consumo de dulces entre comidas","type":"CHOICE","options":["Poco","Moderado","Frecuente"]},
 {"id":"chupon","section":"Hábitos","label":"Usa chupón o se chupa el dedo","type":"YES_NO"},
 {"id":"respira_boca","section":"Hábitos","label":"Respira por la boca","type":"YES_NO"},
 {"id":"cepillado_adulto","section":"Higiene","label":"Un adulto supervisa el cepillado","type":"YES_NO"},
 {"id":"fluor","section":"Higiene","label":"Pasta con flúor","type":"YES_NO"},
 {"id":"asma","section":"Salud general","label":"Asma o alergias","type":"YES_NO","alert":true},
 {"id":"cardiaco","section":"Salud general","label":"Enfermedad del corazón","type":"YES_NO","alert":true},
 {"id":"medicacion","section":"Salud general","label":"Toma algún medicamento","type":"YES_NO","alert":true},
 {"id":"conducta","section":"Conducta","label":"Conducta esperada en consulta","type":"CHOICE","options":["Colaborador","Ansioso","No colaborador"]},
 {"id":"comentario","section":"Comentario","label":"Comentario","type":"TEXT"}
]'),
('ENDODONCIA', 'Ficha de endodoncia', 'Endodoncia', 3, NOW(), 'sistema', '[
 {"id":"pieza","section":"Pieza","label":"Pieza dentaria","type":"TEXT"},
 {"id":"dolor","section":"Dolor","label":"Dolor","type":"CHOICE","options":["Ausente","Provocado","Espontáneo"]},
 {"id":"intensidad","section":"Dolor","label":"Intensidad","type":"CHOICE","options":["Leve","Moderado","Severo"]},
 {"id":"duracion","section":"Dolor","label":"Duración","type":"CHOICE","options":["Segundos","Minutos","Horas","Continuo"]},
 {"id":"nocturno","section":"Dolor","label":"Dolor nocturno","type":"YES_NO"},
 {"id":"frio","section":"Pruebas","label":"Prueba al frío","type":"CHOICE","options":["Normal","Aumentada","Prolongada","Negativa"]},
 {"id":"calor","section":"Pruebas","label":"Prueba al calor","type":"CHOICE","options":["Normal","Aumentada","Negativa"]},
 {"id":"percusion_v","section":"Pruebas","label":"Percusión vertical dolorosa","type":"YES_NO"},
 {"id":"percusion_h","section":"Pruebas","label":"Percusión horizontal dolorosa","type":"YES_NO"},
 {"id":"palpacion","section":"Pruebas","label":"Dolor a la palpación apical","type":"YES_NO"},
 {"id":"movilidad","section":"Pruebas","label":"Movilidad","type":"CHOICE","options":["0","I","II","III"]},
 {"id":"fistula","section":"Hallazgos","label":"Fístula","type":"YES_NO"},
 {"id":"edema","section":"Hallazgos","label":"Edema","type":"YES_NO","alert":true},
 {"id":"radiografia","section":"Hallazgos","label":"Hallazgos radiográficos","type":"TEXT"},
 {"id":"dx_pulpar","section":"Diagnóstico","label":"Diagnóstico pulpar","type":"CHOICE","options":["Pulpa normal","Pulpitis reversible","Pulpitis irreversible sintomática","Pulpitis irreversible asintomática","Necrosis pulpar","Previamente tratado"]},
 {"id":"dx_periapical","section":"Diagnóstico","label":"Diagnóstico periapical","type":"CHOICE","options":["Tejidos normales","Periodontitis apical sintomática","Periodontitis apical asintomática","Absceso apical agudo","Absceso apical crónico"]},
 {"id":"conductometria","section":"Tratamiento","label":"Conductometría (conductos y longitudes)","type":"TEXT"},
 {"id":"comentario","section":"Comentario","label":"Comentario","type":"TEXT"}
]');

-- ---------------------------------------------------------------- 3. Periodontograma
-- data: {"18": {"missing":false,"implant":false,"mobility":0,"furcation":0,
--        "v":{"mg":[0,0,0],"ps":[0,0,0],"bleed":[false,false,false],"plaque":[false,false,false]},
--        "l":{...}}, ...}   (v = vestibular, l = palatino/lingual; 3 sitios: distal, central, mesial)
CREATE TABLE periodontograms (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id     BIGINT       NOT NULL,
  exam_date      DATE         NOT NULL,
  dentist_name   VARCHAR(120) NULL,
  data           MEDIUMTEXT   NOT NULL,
  plaque_index   DECIMAL(5,2) NOT NULL DEFAULT 0,
  bleeding_index DECIMAL(5,2) NOT NULL DEFAULT 0,
  mean_depth     DECIMAL(4,2) NOT NULL DEFAULT 0,
  deep_sites     INT          NOT NULL DEFAULT 0,   -- sitios con sondaje >= 4 mm
  teeth_present  INT          NOT NULL DEFAULT 0,
  notes          VARCHAR(500) NULL,
  created_at     DATETIME     NOT NULL,
  created_by     VARCHAR(40)  NOT NULL,
  updated_at     DATETIME     NULL,
  CONSTRAINT fk_perio_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  INDEX idx_perio_patient (patient_id, exam_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- 4. Laboratorio dental
CREATE TABLE laboratories (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  name       VARCHAR(120) NOT NULL UNIQUE,
  ruc        CHAR(11)     NULL,
  phone      VARCHAR(15)  NULL,
  email      VARCHAR(120) NULL,
  contact    VARCHAR(120) NULL,
  address    VARCHAR(200) NULL,
  notes      VARCHAR(300) NULL,
  active     BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at DATETIME     NOT NULL,
  created_by VARCHAR(40)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE lab_orders (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id        BIGINT        NOT NULL,
  laboratory_id     BIGINT        NOT NULL,
  dentist_id        BIGINT        NULL,
  plan_id           BIGINT        NULL,
  treatment_item_id BIGINT        NULL,
  work_type         VARCHAR(30)   NOT NULL,
  description       VARCHAR(300)  NOT NULL,
  teeth             VARCHAR(60)   NULL,
  shade             VARCHAR(20)   NULL,     -- color (escala VITA)
  material          VARCHAR(60)   NULL,
  status            VARCHAR(15)   NOT NULL DEFAULT 'POR_ENVIAR', -- POR_ENVIAR, ENVIADA, EN_PRUEBA, RECIBIDA, ENTREGADA, ANULADA
  sent_at           DATE          NULL,
  due_date          DATE          NULL,
  received_at       DATE          NULL,
  delivered_at      DATE          NULL,
  cost              DECIMAL(10,2) NOT NULL DEFAULT 0,
  notes             VARCHAR(500)  NULL,
  void_reason       VARCHAR(200)  NULL,
  created_at        DATETIME      NOT NULL,
  created_by        VARCHAR(40)   NOT NULL,
  updated_at        DATETIME      NULL,
  updated_by        VARCHAR(40)   NULL,
  CONSTRAINT fk_lab_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  CONSTRAINT fk_lab_lab FOREIGN KEY (laboratory_id) REFERENCES laboratories(id),
  CONSTRAINT fk_lab_plan FOREIGN KEY (plan_id) REFERENCES treatment_plans(id),
  INDEX idx_lab_status (status, due_date),
  INDEX idx_lab_patient (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE lab_order_events (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id   BIGINT       NOT NULL,
  status     VARCHAR(15)  NOT NULL,
  note       VARCHAR(300) NULL,
  created_at DATETIME     NOT NULL,
  created_by VARCHAR(40)  NOT NULL,
  CONSTRAINT fk_labev_order FOREIGN KEY (order_id) REFERENCES lab_orders(id),
  INDEX idx_labev_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Cada pago al laboratorio es un egreso de caja (categoría LABORATORIO); si se anula el egreso, deja de contar.
CREATE TABLE lab_order_payments (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id   BIGINT        NOT NULL,
  expense_id BIGINT        NOT NULL,
  amount     DECIMAL(10,2) NOT NULL,
  CONSTRAINT fk_labpay_order FOREIGN KEY (order_id) REFERENCES lab_orders(id),
  CONSTRAINT fk_labpay_expense FOREIGN KEY (expense_id) REFERENCES expenses(id),
  INDEX idx_labpay_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO permissions (code, group_name, label, sort_order) VALUES
('LABORATORIO', 'Atención clínica', 'Órdenes de laboratorio dental (envío, recepción y pagos)', 34);
INSERT INTO role_permissions (role_code, permission_code) VALUES
('ADMIN', 'LABORATORIO'), ('RECEPCION', 'LABORATORIO'), ('ODONTOLOGO', 'LABORATORIO');
