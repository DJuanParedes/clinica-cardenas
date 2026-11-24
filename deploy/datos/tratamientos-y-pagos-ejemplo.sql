-- Datos de ejemplo coherentes con las citas atendidas de los pacientes de ejemplo (demo = TRUE):
-- cada cita ATENDIDA genera su procedimiento REALIZADO (según el motivo) y ~90 % de ellos quedan pagados.
-- Solo toca pacientes demo; se puede ejecutar una sola vez (no duplica: se salta pacientes que ya tienen plan).
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO treatment_plans (patient_id, dentist_id, title, notes, status, created_at, created_by)
SELECT p.id, MIN(a.dentist_id), 'Plan de atención integral', 'Generado a partir de sus citas atendidas', 'ACTIVO',
       DATE_SUB(MIN(a.start_at), INTERVAL 1 HOUR), 'dra.cardenas'
  FROM patients p JOIN appointments a ON a.patient_id = p.id AND a.status = 'ATENDIDA'
 WHERE p.demo = TRUE AND NOT EXISTS (SELECT 1 FROM treatment_plans t WHERE t.patient_id = p.id)
 GROUP BY p.id;

INSERT INTO treatment_items (plan_id, procedure_id, tooth, price, status, done_at, done_by_dentist_id, sort_order)
SELECT tp.id, pr.id,
       CASE WHEN pr.code IN ('OPE-002','END-001','CIR-001') THEN ELT(1 + MOD(a.id, 8), 16, 26, 36, 46, 11, 21, 14, 24) ELSE NULL END,
       pr.price, 'REALIZADO', a.end_at, a.dentist_id, 0
  FROM appointments a
  JOIN patients p ON p.id = a.patient_id AND p.demo = TRUE
  JOIN treatment_plans tp ON tp.patient_id = p.id AND tp.title = 'Plan de atención integral'
  JOIN procedures pr ON pr.code = CASE a.reason
        WHEN 'Profilaxis' THEN 'PRE-001'
        WHEN 'Restauración' THEN 'OPE-002'
        WHEN 'Endodoncia' THEN 'END-001'
        WHEN 'Exodoncia' THEN 'CIR-001'
        WHEN 'Control de ortodoncia' THEN 'ORT-001'
        WHEN 'Blanqueamiento' THEN 'EST-001'
        ELSE 'CON-001' END
 WHERE a.status = 'ATENDIDA'
   AND NOT EXISTS (SELECT 1 FROM treatment_items x WHERE x.plan_id = tp.id AND x.done_at = a.end_at);

INSERT INTO payments (patient_id, treatment_item_id, amount, method, reference, notes, paid_at, cashier, status)
SELECT tp.patient_id, ti.id, ti.price,
       ELT(1 + MOD(ti.id, 5), 'EFECTIVO', 'YAPE', 'TARJETA', 'EFECTIVO', 'PLIN'),
       CASE WHEN MOD(ti.id, 5) IN (0, 3) THEN NULL ELSE CONCAT('OP', LPAD(ti.id * 7919 MOD 1000000, 6, '0')) END,
       NULL, DATE_ADD(ti.done_at, INTERVAL 5 MINUTE), 'caja', 'VALIDO'
  FROM treatment_items ti
  JOIN treatment_plans tp ON tp.id = ti.plan_id AND tp.title = 'Plan de atención integral'
  JOIN patients p ON p.id = tp.patient_id AND p.demo = TRUE
 WHERE ti.status = 'REALIZADO' AND MOD(ti.id, 10) <> 0
   AND NOT EXISTS (SELECT 1 FROM payments y WHERE y.treatment_item_id = ti.id);

COMMIT;

SELECT 'procedimientos realizados' AS dato, COUNT(*) AS valor FROM treatment_items WHERE status = 'REALIZADO'
UNION ALL SELECT 'cobros', COUNT(*) FROM payments WHERE status = 'VALIDO'
UNION ALL SELECT 'ingresos 2026 (S/)', COALESCE(SUM(amount), 0) FROM payments WHERE status = 'VALIDO' AND YEAR(paid_at) = 2026
UNION ALL SELECT 'egresos 2026 (S/)', COALESCE(SUM(amount), 0) FROM expenses WHERE status = 'VALIDO' AND YEAR(expense_date) = 2026
UNION ALL SELECT 'deuda pendiente (S/)', COALESCE(SUM(ti.price), 0) FROM treatment_items ti
       WHERE ti.status = 'REALIZADO' AND NOT EXISTS (SELECT 1 FROM payments y WHERE y.treatment_item_id = ti.id AND y.status = 'VALIDO');
