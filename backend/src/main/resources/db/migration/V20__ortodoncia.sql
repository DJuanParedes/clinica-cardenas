-- v2.8.0 (paso 13): módulo de ortodoncia. Un caso por tratamiento, sus controles mensuales y álbumes de fotos por
-- etapa. Las fotos son archivos del paciente (patient_files): aquí solo se ordenan por etapa y vista.

CREATE TABLE ortho_cases (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id       BIGINT       NOT NULL,
  dentist_id       BIGINT       NULL,
  appliance        VARCHAR(30)  NOT NULL,                -- BRACKETS_METALICOS, BRACKETS_ESTETICOS, AUTOLIGADO, ALINEADORES, REMOVIBLE, OTRO
  angle_class      VARCHAR(10)  NULL,                    -- I, II_1, II_2, III
  diagnosis        VARCHAR(1000) NULL,
  objectives       VARCHAR(1000) NULL,
  start_date       DATE         NOT NULL,
  estimated_months INT          NULL,
  status           VARCHAR(15)  NOT NULL DEFAULT 'ACTIVO', -- ACTIVO, RETENCION, FINALIZADO, ABANDONADO
  current_stage    VARCHAR(20)  NOT NULL DEFAULT 'INICIAL',
  end_date         DATE         NULL,
  plan_id          BIGINT       NULL,
  created_by       VARCHAR(60)  NOT NULL,
  created_at       DATETIME     NOT NULL,
  updated_at       DATETIME     NULL,
  INDEX ix_ortho_patient (patient_id),
  INDEX ix_ortho_status (status),
  CONSTRAINT fk_ortho_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  CONSTRAINT fk_ortho_dentist FOREIGN KEY (dentist_id) REFERENCES dentists(id),
  CONSTRAINT fk_ortho_plan FOREIGN KEY (plan_id) REFERENCES treatment_plans(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ortho_controls (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  case_id       BIGINT       NOT NULL,
  control_date  DATE         NOT NULL,
  stage         VARCHAR(20)  NOT NULL,
  upper_wire    VARCHAR(60)  NULL,                       -- arco superior (p. ej. NiTi 0.016)
  lower_wire    VARCHAR(60)  NULL,
  elastics      VARCHAR(100) NULL,
  hygiene       VARCHAR(10)  NULL,                       -- BUENA, REGULAR, MALA
  notes         VARCHAR(1000) NULL,
  next_weeks    INT          NULL,
  created_by    VARCHAR(60)  NOT NULL,
  created_at    DATETIME     NOT NULL,
  INDEX ix_ortho_controls (case_id, control_date),
  CONSTRAINT fk_oc_case FOREIGN KEY (case_id) REFERENCES ortho_cases(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ortho_photos (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  case_id     BIGINT      NOT NULL,
  file_id     BIGINT      NOT NULL,
  stage       VARCHAR(20) NOT NULL,                      -- INICIAL, ALINEACION, CIERRE, DETALLADO, RETIRO, RETENCION
  view_code   VARCHAR(20) NOT NULL,                      -- FRONTAL, SONRISA, PERFIL, INTRA_FRONTAL, INTRA_DER, INTRA_IZQ, OCLUSAL_SUP, OCLUSAL_INF, PANORAMICA, CEFALOMETRIA, OTRA
  taken_on    DATE        NOT NULL,
  created_by  VARCHAR(60) NOT NULL,
  created_at  DATETIME    NOT NULL,
  CONSTRAINT uk_ortho_photo_file UNIQUE (case_id, file_id),
  INDEX ix_ortho_photos (case_id, stage),
  CONSTRAINT fk_op_case FOREIGN KEY (case_id) REFERENCES ortho_cases(id),
  CONSTRAINT fk_op_file FOREIGN KEY (file_id) REFERENCES patient_files(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
