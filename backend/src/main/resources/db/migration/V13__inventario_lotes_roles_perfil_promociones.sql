-- =====================================================================================================
-- V13 · Correcciones de la revisión del sprint
--   1. Inventario real: marca, laboratorio/proveedor, tipo (insumo / instrumental / medicamento), costo,
--      lotes con fecha de vencimiento (salida FEFO: primero lo que vence antes), compras ligadas a caja.
--   2. El odontólogo ya no administra inventario: solicita insumos y recepción/administración los atiende.
--   3. Roles personalizados (además de los puestos base) y perfil propio de cada usuario (teléfono).
--   4. Verificación en 2 pasos también por código al correo (sin instalar ninguna app).
--   5. Variantes de procedimientos (limpieza simple / profunda) y promociones con vigencia.
-- =====================================================================================================

-- 1. Inventario ---------------------------------------------------------------------------------------
ALTER TABLE inventory_items
  ADD COLUMN category  VARCHAR(20)   NOT NULL DEFAULT 'INSUMO' AFTER name,
  ADD COLUMN brand     VARCHAR(80)   NULL AFTER category,
  ADD COLUMN supplier  VARCHAR(120)  NULL AFTER brand,
  ADD COLUMN unit_cost DECIMAL(10,2) NULL AFTER unit,
  ADD COLUMN tracks_expiry BOOLEAN   NOT NULL DEFAULT TRUE AFTER unit_cost;

CREATE TABLE inventory_purchases (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  purchase_date DATE          NOT NULL,
  supplier      VARCHAR(120)  NOT NULL,
  doc_type      VARCHAR(20)   NOT NULL,           -- FACTURA, BOLETA, GUIA, SIN_COMPROBANTE
  doc_number    VARCHAR(30)   NULL,
  total         DECIMAL(12,2) NOT NULL,
  method        VARCHAR(20)   NULL,               -- cómo se pagó (mismo catálogo que caja); NULL si es al crédito
  expense_id    BIGINT        NULL,               -- egreso de caja generado automáticamente
  notes         VARCHAR(255)  NULL,
  status        VARCHAR(12)   NOT NULL DEFAULT 'PAGADA',  -- PAGADA, POR_PAGAR (crédito del proveedor), ANULADA
  due_date      DATE          NULL,
  created_at    DATETIME      NOT NULL,
  created_by    VARCHAR(40)   NOT NULL,
  INDEX idx_ip_date (purchase_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE inventory_lots (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  item_id       BIGINT        NOT NULL,
  purchase_id   BIGINT        NULL,
  lot_code      VARCHAR(40)   NULL,
  expires_on    DATE          NULL,
  quantity      DECIMAL(10,2) NOT NULL,           -- cantidad que ingresó
  remaining     DECIMAL(10,2) NOT NULL,           -- lo que queda del lote
  unit_cost     DECIMAL(10,2) NULL,
  created_at    DATETIME      NOT NULL,
  CONSTRAINT fk_lot_item FOREIGN KEY (item_id) REFERENCES inventory_items(id),
  CONSTRAINT fk_lot_purchase FOREIGN KEY (purchase_id) REFERENCES inventory_purchases(id),
  INDEX idx_lot_item_exp (item_id, remaining, expires_on)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE inventory_moves ADD COLUMN lot_id BIGINT NULL, ADD COLUMN purchase_id BIGINT NULL, ADD COLUMN request_id BIGINT NULL;

-- El stock que ya existía queda como un lote "saldo inicial" sin vencimiento conocido
INSERT INTO inventory_lots (item_id, lot_code, expires_on, quantity, remaining, created_at)
SELECT id, 'SALDO-INICIAL', NULL, stock, stock, NOW() FROM inventory_items WHERE stock > 0;

-- Datos reales de ejemplo: marca, proveedor y tipo
UPDATE inventory_items SET category = 'MEDICAMENTO', brand = 'New Stetic', supplier = 'Dental Perú S.A.C.', unit_cost = 3.20 WHERE name LIKE 'Anestesia%';
UPDATE inventory_items SET brand = 'Supermax', supplier = 'Distribuidora Médica Lima', unit_cost = 0.90, tracks_expiry = FALSE WHERE name LIKE 'Guantes%';
UPDATE inventory_items SET brand = '3M Filtek', supplier = 'Dental Perú S.A.C.', unit_cost = 85.00 WHERE name LIKE 'Resina%';
UPDATE inventory_items SET category = 'INSTRUMENTAL', tracks_expiry = FALSE WHERE name LIKE '%fresa%' OR name LIKE '%Fresa%' OR name LIKE '%lima%' OR name LIKE '%Lima%' OR name LIKE '%espejo%' OR name LIKE '%pinza%';

-- 2. Solicitudes de insumos del odontólogo ------------------------------------------------------------
CREATE TABLE supply_requests (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  item_id       BIGINT        NULL,               -- material del catálogo
  item_text     VARCHAR(160)  NULL,               -- o algo que no está en el catálogo
  quantity      DECIMAL(10,2) NOT NULL,
  urgency       VARCHAR(10)   NOT NULL DEFAULT 'NORMAL',  -- NORMAL, URGENTE
  note          VARCHAR(255)  NULL,
  status        VARCHAR(12)   NOT NULL DEFAULT 'PENDIENTE', -- PENDIENTE, ENTREGADA, RECHAZADA, ANULADA
  requested_by  VARCHAR(40)   NOT NULL,
  requested_at  DATETIME      NOT NULL,
  answered_by   VARCHAR(40)   NULL,
  answered_at   DATETIME      NULL,
  answer        VARCHAR(255)  NULL,
  CONSTRAINT fk_sr_item FOREIGN KEY (item_id) REFERENCES inventory_items(id),
  INDEX idx_sr_status (status, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO permissions (code, group_name, label, sort_order) VALUES
('SOLICITAR_INSUMOS', 'Atención clínica', 'Solicitar insumos y materiales a recepción', 33),
('PROMOCIONES',       'Caja y facturación', 'Catálogo de procedimientos, variantes y promociones', 44);
DELETE FROM role_permissions WHERE role_code = 'ODONTOLOGO' AND permission_code = 'INVENTARIO';
INSERT INTO role_permissions (role_code, permission_code) VALUES
('ODONTOLOGO', 'SOLICITAR_INSUMOS'), ('ADMIN', 'SOLICITAR_INSUMOS'), ('ADMIN', 'PROMOCIONES'), ('DIRECTOR', 'PROMOCIONES');

-- 3. Roles personalizados y perfil ---------------------------------------------------------------------
ALTER TABLE roles
  ADD COLUMN base_role VARCHAR(20) NULL,
  ADD COLUMN custom    BOOLEAN     NOT NULL DEFAULT FALSE,
  ADD COLUMN created_at DATETIME   NULL;
UPDATE roles SET base_role = code WHERE custom = FALSE;

ALTER TABLE users
  ADD COLUMN phone            VARCHAR(15) NULL AFTER email,
  ADD COLUMN access_role_code VARCHAR(20) NULL AFTER role_code,
  ADD CONSTRAINT fk_users_access_role FOREIGN KEY (access_role_code) REFERENCES roles(code);

-- 4. 2FA por correo -----------------------------------------------------------------------------------
ALTER TABLE users
  ADD COLUMN two_factor_method  VARCHAR(10) NULL,     -- APP o CORREO (NULL = desactivada)
  ADD COLUMN email_otp_hash     VARCHAR(100) NULL,
  ADD COLUMN email_otp_expires  DATETIME NULL,
  ADD COLUMN email_otp_attempts INT NOT NULL DEFAULT 0;
UPDATE users SET two_factor_method = 'APP' WHERE totp_enabled = TRUE;

-- 5. Variantes y promociones -------------------------------------------------------------------------
ALTER TABLE procedures
  ADD COLUMN family      VARCHAR(60)  NULL,
  ADD COLUMN description VARCHAR(255) NULL,
  ADD COLUMN duration_min INT NULL;

UPDATE procedures SET name = 'Limpieza simple (profilaxis y destartraje)', family = 'Limpieza dental',
       description = 'Retiro de placa y sarro supragingival y pulido. Una sesión.', duration_min = 40 WHERE code = 'PRE-001';
INSERT INTO procedures (code, name, category, price, family, description, duration_min) VALUES
('PRE-004', 'Limpieza profunda (raspado y alisado radicular)', 'Prevención', 280.00, 'Limpieza dental',
 'Para encías inflamadas o con bolsas: retiro de sarro subgingival por cuadrantes, con anestesia local.', 60),
('PRE-005', 'Limpieza con ultrasonido y aeropulido', 'Prevención', 180.00, 'Limpieza dental',
 'Limpieza simple más remoción de manchas con bicarbonato a presión.', 45);
UPDATE procedures SET family = 'Restauración con resina' WHERE code IN ('OPE-001', 'OPE-002');
UPDATE procedures SET family = 'Endodoncia' WHERE code IN ('END-001', 'END-002');
UPDATE procedures SET family = 'Exodoncia' WHERE code IN ('CIR-001', 'CIR-002');
UPDATE procedures SET family = 'Corona' WHERE code IN ('PRO-001', 'PRO-002');

CREATE TABLE promotions (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(100)  NOT NULL,
  procedure_id  BIGINT        NULL,               -- NULL = aplica a toda la familia o a todo
  family        VARCHAR(60)   NULL,
  discount_type VARCHAR(12)   NOT NULL,           -- PORCENTAJE, MONTO, PRECIO
  value         DECIMAL(10,2) NOT NULL,
  valid_from    DATE          NOT NULL,
  valid_to      DATE          NOT NULL,
  conditions    VARCHAR(255)  NULL,
  active        BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at    DATETIME      NOT NULL,
  created_by    VARCHAR(40)   NOT NULL,
  CONSTRAINT fk_promo_proc FOREIGN KEY (procedure_id) REFERENCES procedures(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE treatment_items
  ADD COLUMN list_price   DECIMAL(10,2) NULL,
  ADD COLUMN promotion_id BIGINT NULL;

INSERT INTO promotions (name, procedure_id, family, discount_type, value, valid_from, valid_to, conditions, created_at, created_by)
SELECT 'Mes de la sonrisa: limpieza simple', id, NULL, 'PRECIO', 89.00, CURDATE(), DATE_ADD(CURDATE(), INTERVAL 45 DAY),
       'Precio promocional por campaña. No acumulable.', NOW(), 'sistema' FROM procedures WHERE code = 'PRE-001';
INSERT INTO promotions (name, procedure_id, family, discount_type, value, valid_from, valid_to, conditions, created_at, created_by)
SELECT 'Blanqueamiento 20% dscto.', id, NULL, 'PORCENTAJE', 20.00, CURDATE(), DATE_ADD(CURDATE(), INTERVAL 30 DAY),
       'Previa evaluación. Una por paciente.', NOW(), 'sistema' FROM procedures WHERE code = 'EST-001';

-- Fechas de vencimiento de ejemplo en el saldo inicial (para ver las alertas funcionando)
UPDATE inventory_items SET tracks_expiry = FALSE WHERE name LIKE 'Mascarilla%';
UPDATE inventory_items SET brand = 'Ultradent', supplier = 'Dental Perú S.A.C.', unit_cost = 38.00 WHERE name LIKE 'Ácido grabador%';
UPDATE inventory_items SET brand = '3M Single Bond', supplier = 'Dental Perú S.A.C.', unit_cost = 120.00 WHERE name LIKE 'Adhesivo%';
UPDATE inventory_items SET brand = 'Dentsply Maillefer', supplier = 'Importaciones Odonto S.R.L.', unit_cost = 65.00 WHERE name LIKE 'Limas%';
UPDATE inventory_items SET category = 'MEDICAMENTO', brand = 'Fluorophat', supplier = 'Distribuidora Médica Lima', unit_cost = 25.00 WHERE name LIKE 'Flúor%';
UPDATE inventory_lots l JOIN inventory_items i ON i.id = l.item_id
   SET l.expires_on = CASE
       WHEN i.name LIKE 'Anestesia%' THEN DATE_ADD(CURDATE(), INTERVAL 9 MONTH)
       WHEN i.name LIKE 'Resina%' THEN DATE_ADD(CURDATE(), INTERVAL 40 DAY)
       WHEN i.name LIKE 'Ácido%' THEN DATE_ADD(CURDATE(), INTERVAL 20 DAY)
       WHEN i.name LIKE 'Flúor%' THEN DATE_ADD(CURDATE(), INTERVAL 5 MONTH)
       WHEN i.name LIKE 'Adhesivo%' THEN DATE_ADD(CURDATE(), INTERVAL 7 MONTH)
       ELSE NULL END
 WHERE l.lot_code = 'SALDO-INICIAL' AND i.tracks_expiry = TRUE;

