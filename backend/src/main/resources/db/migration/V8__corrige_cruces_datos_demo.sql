-- Los datos de demostración generaban, en algunos días, dos atenciones del mismo odontólogo a la misma hora
-- (10:00). Se mueve la atención más antigua del par a las 12:30-13:00 del mismo día, solo si ese horario está
-- libre para el odontólogo y para el paciente, y solo entre pacientes de demostración (no toca datos reales).
UPDATE appointments a
JOIN (
    SELECT DISTINCT x.id
      FROM appointments x
      JOIN appointments y ON y.dentist_id = x.dentist_id AND y.id > x.id
                         AND y.start_at < x.end_at AND y.end_at > x.start_at
                         AND y.status IN ('PROGRAMADA', 'CONFIRMADA', 'EN_ESPERA', 'EN_ATENCION', 'ATENDIDA')
      JOIN patients px ON px.id = x.patient_id AND px.demo = TRUE
      JOIN patients py ON py.id = y.patient_id AND py.demo = TRUE
     WHERE x.status = 'ATENDIDA'
       AND x.start_at < NOW()
       AND NOT EXISTS (
           SELECT 1 FROM appointments z
            WHERE z.id <> x.id
              AND (z.dentist_id = x.dentist_id OR z.patient_id = x.patient_id)
              AND z.status IN ('PROGRAMADA', 'CONFIRMADA', 'EN_ESPERA', 'EN_ATENCION', 'ATENDIDA')
              AND z.start_at < TIMESTAMP(DATE(x.start_at), '13:00:00')
              AND z.end_at > TIMESTAMP(DATE(x.start_at), '12:30:00'))
) t ON t.id = a.id
SET a.end_at = TIMESTAMP(DATE(a.start_at), '13:00:00'),
    a.start_at = TIMESTAMP(DATE(a.start_at), '12:30:00'),
    a.duration_min = 30,
    a.arrived_at = CASE WHEN a.arrived_at IS NULL THEN NULL ELSE TIMESTAMP(DATE(a.start_at), '12:25:00') END;
