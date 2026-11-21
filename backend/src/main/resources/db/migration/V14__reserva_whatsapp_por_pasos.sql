-- 6. Reserva de citas por WhatsApp paso a paso: fecha → odontólogo y hora libres → confirmación.
--    La cita solo se guarda al confirmar, y en ese momento se vuelve a validar que el horario siga libre.
CREATE TABLE assistant_bookings (
  patient_id   BIGINT       NOT NULL PRIMARY KEY,
  step         VARCHAR(12)  NOT NULL,             -- FECHA, HORA, CONFIRMAR
  day          DATE         NULL,
  options      TEXT         NULL,                 -- opciones numeradas mostradas al paciente
  dentist_id   BIGINT       NULL,
  start_at     DATETIME     NULL,
  expires_at   DATETIME     NOT NULL,
  CONSTRAINT fk_ab_patient FOREIGN KEY (patient_id) REFERENCES patients(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
