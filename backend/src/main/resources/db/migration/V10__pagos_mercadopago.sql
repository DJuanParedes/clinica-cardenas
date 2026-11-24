-- Cobros en línea con Mercado Pago (tarjeta, Yape, saldo de Mercado Pago).
-- Cada cobro es un link de pago (preferencia de Checkout Pro). Cuando Mercado Pago lo aprueba, el sistema
-- registra el pago en caja automáticamente (payments) y guarda aquí su número de operación.
CREATE TABLE online_charges (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  provider          VARCHAR(20)   NOT NULL DEFAULT 'MERCADOPAGO',
  source            VARCHAR(10)   NOT NULL,             -- CAJA (lo genera el personal) o APP (lo paga el paciente)
  patient_id        BIGINT        NOT NULL,
  treatment_item_id BIGINT        NULL,
  amount            DECIMAL(10,2) NOT NULL,
  description       VARCHAR(200)  NOT NULL,
  status            VARCHAR(20)   NOT NULL,             -- PENDIENTE, APROBADO, RECHAZADO, CANCELADO, VENCIDO
  preference_id     VARCHAR(80)   NULL,
  checkout_url      VARCHAR(500)  NULL,
  mp_payment_id     VARCHAR(40)   NULL,
  mp_status         VARCHAR(40)   NULL,
  mp_status_detail  VARCHAR(80)   NULL,
  mp_method         VARCHAR(40)   NULL,                 -- visa, master, yape, account_money…
  payment_id        BIGINT        NULL,                 -- cobro registrado en caja
  expires_at        DATETIME      NOT NULL,
  created_at        DATETIME      NOT NULL,
  created_by        VARCHAR(40)   NULL,
  updated_at        DATETIME      NULL,
  CONSTRAINT fk_oc_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  CONSTRAINT fk_oc_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
  CONSTRAINT uk_oc_mp_payment UNIQUE (mp_payment_id),
  INDEX idx_oc_patient (patient_id, created_at),
  INDEX idx_oc_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
