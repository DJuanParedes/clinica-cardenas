-- Quita los datos de demostración para empezar a usar el sistema con pacientes reales.
-- Borra: pacientes marcados demo = TRUE y todo lo suyo (citas, historia, planes, cobros, comprobantes, avisos,
-- conversaciones de WhatsApp), las liquidaciones de comisiones que queden vacías y los egresos de ejemplo
-- (los que no vienen de una compra de inventario).
-- Conserva: usuarios, roles, catálogo de procedimientos, promociones, inventario, compras y el registro de auditoría.
-- Hacer antes una copia: sudo bash /opt/clinica/deploy/respaldo.sh
SET NAMES utf8mb4;
START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS demo_p;
CREATE TEMPORARY TABLE demo_p (id BIGINT PRIMARY KEY) SELECT id FROM patients WHERE demo = TRUE;
DROP TEMPORARY TABLE IF EXISTS demo_a;
CREATE TEMPORARY TABLE demo_a (id BIGINT PRIMARY KEY) SELECT a.id FROM appointments a JOIN demo_p p ON p.id = a.patient_id;
DROP TEMPORARY TABLE IF EXISTS demo_plan;
CREATE TEMPORARY TABLE demo_plan (id BIGINT PRIMARY KEY) SELECT t.id FROM treatment_plans t JOIN demo_p p ON p.id = t.patient_id;
DROP TEMPORARY TABLE IF EXISTS demo_item;
CREATE TEMPORARY TABLE demo_item (id BIGINT PRIMARY KEY) SELECT i.id FROM treatment_items i JOIN demo_plan p ON p.id = i.plan_id;
DROP TEMPORARY TABLE IF EXISTS demo_inv;
CREATE TEMPORARY TABLE demo_inv (id BIGINT PRIMARY KEY) SELECT v.id FROM invoices v JOIN demo_p p ON p.id = v.patient_id;
DROP TEMPORARY TABLE IF EXISTS demo_phone;
CREATE TEMPORARY TABLE demo_phone (phone VARCHAR(20) PRIMARY KEY)
  SELECT DISTINCT RIGHT(REGEXP_REPLACE(phone, '[^0-9]', ''), 9) AS phone FROM patients WHERE demo = TRUE AND phone IS NOT NULL;

SELECT (SELECT COUNT(*) FROM demo_p) AS pacientes, (SELECT COUNT(*) FROM demo_a) AS citas,
       (SELECT COUNT(*) FROM demo_item) AS procedimientos, (SELECT COUNT(*) FROM demo_inv) AS comprobantes;

DELETE FROM surveys WHERE patient_id IN (SELECT id FROM demo_p) OR appointment_id IN (SELECT id FROM demo_a);
DELETE FROM notifications WHERE patient_id IN (SELECT id FROM demo_p) OR appointment_id IN (SELECT id FROM demo_a);
DELETE FROM assistant_bookings WHERE patient_id IN (SELECT id FROM demo_p);
DELETE FROM file_requests WHERE patient_id IN (SELECT id FROM demo_p);
DELETE FROM consent_records WHERE patient_id IN (SELECT id FROM demo_p);
DELETE FROM portal_codes WHERE patient_id IN (SELECT id FROM demo_p);
DELETE FROM patient_notices WHERE patient_id IN (SELECT id FROM demo_p);
DELETE FROM recall_log WHERE patient_id IN (SELECT id FROM demo_p);
DELETE FROM waitlist WHERE patient_id IN (SELECT id FROM demo_p);
DELETE FROM odontogram_entries WHERE patient_id IN (SELECT id FROM demo_p);
DELETE FROM diagnoses WHERE patient_id IN (SELECT id FROM demo_p);
DELETE FROM clinical_histories WHERE patient_id IN (SELECT id FROM demo_p);

DELETE FROM online_charges WHERE patient_id IN (SELECT id FROM demo_p) OR treatment_item_id IN (SELECT id FROM demo_item);
DELETE FROM commission_lines WHERE treatment_item_id IN (SELECT id FROM demo_item);
DELETE FROM payments WHERE patient_id IN (SELECT id FROM demo_p) OR treatment_item_id IN (SELECT id FROM demo_item);

UPDATE treatment_items SET invoice_id = NULL WHERE invoice_id IN (SELECT id FROM demo_inv);
DELETE FROM invoice_items WHERE invoice_id IN (SELECT id FROM demo_inv) OR treatment_item_id IN (SELECT id FROM demo_item);
DELETE FROM sunat_submissions WHERE invoice_id IN (SELECT id FROM demo_inv);
DELETE FROM invoices WHERE id IN (SELECT id FROM demo_inv);

-- El stock no se toca: los movimientos de consumo quedan, solo pierden el vínculo al procedimiento borrado
UPDATE inventory_moves SET treatment_item_id = NULL WHERE treatment_item_id IN (SELECT id FROM demo_item);
DELETE FROM evolutions WHERE plan_id IN (SELECT id FROM demo_plan) OR item_id IN (SELECT id FROM demo_item);
DELETE FROM installments WHERE plan_id IN (SELECT id FROM demo_plan);
DELETE FROM treatment_items WHERE id IN (SELECT id FROM demo_item);
DELETE FROM treatment_plans WHERE id IN (SELECT id FROM demo_plan);
DELETE FROM appointments WHERE id IN (SELECT id FROM demo_a);

DELETE FROM wa_messages WHERE RIGHT(REGEXP_REPLACE(phone, '[^0-9]', ''), 9) IN (SELECT phone FROM demo_phone);
DELETE FROM wa_threads WHERE patient_id IN (SELECT id FROM demo_p) OR RIGHT(REGEXP_REPLACE(phone, '[^0-9]', ''), 9) IN (SELECT phone FROM demo_phone);
DELETE FROM patient_accounts WHERE patient_id IN (SELECT id FROM demo_p);
DELETE FROM patient_files WHERE patient_id IN (SELECT id FROM demo_p);
DELETE FROM patients WHERE id IN (SELECT id FROM demo_p);

-- Liquidaciones de comisiones que quedaron sin líneas
DELETE FROM commission_payouts WHERE settlement_id IN (SELECT s.id FROM commission_settlements s WHERE NOT EXISTS (SELECT 1 FROM commission_lines l WHERE l.settlement_id = s.id));
DELETE FROM commission_settlements WHERE NOT EXISTS (SELECT 1 FROM commission_lines l WHERE l.settlement_id = commission_settlements.id);

-- Egresos de ejemplo (los de compras de inventario se conservan)
DELETE FROM expenses WHERE id NOT IN (SELECT expense_id FROM inventory_purchases WHERE expense_id IS NOT NULL);

SELECT (SELECT COUNT(*) FROM patients) AS pacientes_que_quedan, (SELECT COUNT(*) FROM appointments) AS citas_que_quedan,
       (SELECT COUNT(*) FROM payments) AS cobros_que_quedan, (SELECT COUNT(*) FROM expenses) AS egresos_que_quedan;
COMMIT;
