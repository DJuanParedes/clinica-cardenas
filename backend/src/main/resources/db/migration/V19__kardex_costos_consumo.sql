-- v2.8.0 (paso 12): kardex valorizado. Cada movimiento guarda su costo y el valor del stock que deja,
-- y el consumo queda atribuido al paciente y al servicio (procedimiento) en que se usó.

ALTER TABLE inventory_moves
  ADD COLUMN unit_cost    DECIMAL(12,4) NULL,        -- costo unitario del movimiento
  ADD COLUMN total_cost   DECIMAL(12,2) NULL,        -- con signo: + entra valor, - sale valor
  ADD COLUMN value_after  DECIMAL(14,2) NULL,        -- valor del stock después del movimiento
  ADD COLUMN patient_id   BIGINT NULL,
  ADD COLUMN procedure_id BIGINT NULL,
  ADD INDEX ix_moves_item_date (item_id, created_at),
  ADD INDEX ix_moves_patient (patient_id),
  ADD INDEX ix_moves_procedure (procedure_id);

-- Movimientos anteriores: costo del lote si lo tienen; si no, el último costo del material.
UPDATE inventory_moves m
  LEFT JOIN inventory_lots l ON l.id = m.lot_id
  JOIN inventory_items i ON i.id = m.item_id
   SET m.unit_cost = COALESCE(l.unit_cost, i.unit_cost, 0),
       m.total_cost = ROUND(m.quantity * COALESCE(l.unit_cost, i.unit_cost, 0), 2);

-- Valor del stock después de cada movimiento: al costo del material (había stock inicial sin movimiento de entrada).
UPDATE inventory_moves m
  JOIN inventory_items i ON i.id = m.item_id
   SET m.value_after = ROUND(GREATEST(m.stock_after, 0) * COALESCE(i.unit_cost, 0), 2);

-- Consumo clínico anterior: paciente y procedimiento desde el ítem del plan.
UPDATE inventory_moves m
  JOIN treatment_items ti ON ti.id = m.treatment_item_id
  JOIN treatment_plans tp ON tp.id = ti.plan_id
   SET m.patient_id = tp.patient_id, m.procedure_id = ti.procedure_id;

INSERT INTO permissions (code, group_name, label, sort_order) VALUES
('INVENTARIO_COSTOS', 'Dirección', 'Kardex valorizado y costo de materiales por paciente y servicio', 61);
INSERT INTO role_permissions (role_code, permission_code) VALUES ('ADMIN', 'INVENTARIO_COSTOS'), ('DIRECTOR', 'INVENTARIO_COSTOS');
