-- v2.10: cuadre y cierre de caja diario. Se guarda lo esperado por medio de pago (foto del momento), lo contado,
-- la diferencia y el fondo que queda para el día siguiente. Una caja cerrada no acepta cobros ni egresos manuales
-- de ese día hasta que el administrador la reabra (queda en auditoría).

CREATE TABLE cash_closings (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  day            DATE          NOT NULL,
  status         VARCHAR(10)   NOT NULL DEFAULT 'CERRADA',   -- CERRADA, REABIERTA
  opening_float  DECIMAL(10,2) NOT NULL DEFAULT 0,           -- fondo inicial (sencillo)
  expected_cash  DECIMAL(10,2) NOT NULL,                     -- fondo + cobros en efectivo - egresos en efectivo
  counted_cash   DECIMAL(10,2) NOT NULL,
  difference     DECIMAL(10,2) NOT NULL,                     -- contado - esperado (todos los medios)
  next_float     DECIMAL(10,2) NOT NULL DEFAULT 0,           -- se deja en caja para mañana
  deposit        DECIMAL(10,2) NOT NULL DEFAULT 0,           -- efectivo que se retira / deposita
  methods_json   TEXT          NOT NULL,                     -- [{method, expected, declared, difference}]
  denominations_json TEXT      NULL,                         -- conteo de billetes y monedas
  income         DECIMAL(10,2) NOT NULL,
  outgo          DECIMAL(10,2) NOT NULL,
  payments_count INT           NOT NULL,
  notes          VARCHAR(500)  NULL,
  closed_at      DATETIME      NOT NULL,
  closed_by      VARCHAR(60)   NOT NULL,
  reopened_at    DATETIME      NULL,
  reopened_by    VARCHAR(60)   NULL,
  reopen_reason  VARCHAR(300)  NULL,
  CONSTRAINT uk_cash_closings_day UNIQUE (day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO app_settings (k, v, updated_at, updated_by) VALUES ('caja_fondo_inicial', '100', NOW(), 'sistema');

INSERT INTO permissions (code, group_name, label, sort_order) VALUES
('CAJA_CIERRE',   'Caja y facturación', 'Cuadrar y cerrar la caja del día', 46),
('CAJA_REABRIR',  'Caja y facturación', 'Reabrir una caja ya cerrada', 47);
INSERT INTO role_permissions (role_code, permission_code) VALUES
('ADMIN', 'CAJA_CIERRE'), ('CAJA', 'CAJA_CIERRE'),
('ADMIN', 'CAJA_REABRIR'), ('DIRECTOR', 'CAJA_REABRIR');

-- Horario de silencio (21:00–08:00): lo ya programado de noche que va después de la cita o sin cita pasa a las 08:00.
UPDATE notifications n LEFT JOIN appointments a ON a.id = n.appointment_id
   SET n.scheduled_for = CASE WHEN TIME(n.scheduled_for) < '08:00:00' THEN TIMESTAMP(DATE(n.scheduled_for), '08:00:00')
                              ELSE TIMESTAMP(DATE(n.scheduled_for) + INTERVAL 1 DAY, '08:00:00') END
 WHERE n.status = 'PROGRAMADO' AND n.scheduled_for IS NOT NULL
   AND (TIME(n.scheduled_for) >= '21:00:00' OR TIME(n.scheduled_for) < '08:00:00')
   AND (a.id IS NULL OR n.scheduled_for > a.start_at);
