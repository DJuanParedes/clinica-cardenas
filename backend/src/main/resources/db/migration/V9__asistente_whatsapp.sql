-- Conversaciones de WhatsApp con los pacientes y asistente virtual (RAG + modelo de lenguaje).

-- Una conversación por número de celular (9 dígitos).
CREATE TABLE wa_threads (
  phone           VARCHAR(9)   NOT NULL PRIMARY KEY,
  patient_id      BIGINT       NULL,
  display_name    VARCHAR(120) NULL,
  ai_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,   -- el asistente responde en esta conversación
  manual_until    DATETIME     NULL,                    -- el personal tomó la conversación: el asistente calla hasta esta hora
  needs_attention BOOLEAN      NOT NULL DEFAULT FALSE,  -- el asistente pidió ayuda del personal
  unread          INT          NOT NULL DEFAULT 0,
  last_message_at DATETIME     NULL,
  last_preview    VARCHAR(200) NULL,
  CONSTRAINT fk_wa_thread_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  INDEX idx_wa_thread_last (last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wa_messages (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  phone      VARCHAR(9)   NOT NULL,
  direction  VARCHAR(3)   NOT NULL,   -- IN (paciente → clínica), OUT (clínica → paciente)
  source     VARCHAR(10)  NOT NULL,   -- PACIENTE, IA, PERSONAL, SISTEMA
  body       TEXT         NOT NULL,
  intent     VARCHAR(30)  NULL,       -- intención detectada por el asistente
  engine     VARCHAR(40)  NULL,       -- modelo que redactó la respuesta (local, claude-..., etc.)
  status     VARCHAR(12)  NULL,       -- ENVIADO / FALLIDO (mensajes del personal)
  author     VARCHAR(60)  NULL,       -- usuario del personal que escribió
  created_at DATETIME     NOT NULL,
  INDEX idx_wa_msg_phone (phone, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Parámetros del sistema que se cambian desde la web.
CREATE TABLE app_settings (
  k          VARCHAR(60)  NOT NULL PRIMARY KEY,
  v          VARCHAR(500) NOT NULL,
  updated_at DATETIME     NULL,
  updated_by VARCHAR(60)  NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- AUTOMATICO: el asistente responde siempre · FUERA_HORARIO: solo fuera del horario de atención · APAGADO: solo el personal
INSERT INTO app_settings (k, v, updated_at, updated_by) VALUES
('asistente.modo', 'AUTOMATICO', NOW(), 'sistema'),
('asistente.pausa_minutos', '120', NOW(), 'sistema');

-- Base de conocimiento que la clínica mantiene desde la web (el asistente la consulta antes de responder).
CREATE TABLE assistant_faq (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  question   VARCHAR(300)  NOT NULL,
  answer     VARCHAR(1500) NOT NULL,
  active     BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at DATETIME      NOT NULL,
  updated_at DATETIME      NULL,
  updated_by VARCHAR(60)   NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO assistant_faq (question, answer, created_at, updated_by) VALUES
('¿Atienden a niños? ¿Desde qué edad?', 'Sí, atendemos a niños desde los 3 años. La primera consulta incluye evaluación y consejos de higiene para los papás.', NOW(), 'sistema'),
('¿Cuánto dura una limpieza o profilaxis?', 'Una profilaxis dura entre 30 y 45 minutos. Se recomienda cada 6 meses.', NOW(), 'sistema'),
('¿Qué debo llevar a mi primera cita?', 'Trae tu DNI y, si tienes, radiografías o recetas anteriores. Llega 10 minutos antes para registrar tus datos.', NOW(), 'sistema'),
('¿Puedo llegar tarde a mi cita?', 'Te esperamos hasta 15 minutos. Si llegarás más tarde, avísanos por aquí para ver si podemos reprogramarte.', NOW(), 'sistema'),
('¿Hacen ortodoncia o brackets?', 'Sí, la Dra. Rosa Cárdenas es ortodoncista. Ofrecemos brackets metálicos y estéticos; la evaluación de ortodoncia define el plan y el costo.', NOW(), 'sistema'),
('¿Hacen endodoncia o tratamiento de conducto?', 'Sí, el Dr. Andrés Ramos realiza endodoncias. Primero hacemos una evaluación con radiografía para confirmar el tratamiento.', NOW(), 'sistema'),
('¿Puedo pagar en cuotas?', 'Sí, los tratamientos largos se pueden pagar en cuotas mensuales. Recepción arma el cronograma cuando aprobamos tu plan.', NOW(), 'sistema'),
('¿Emiten boleta o factura?', 'Sí, emitimos boleta o factura electrónica por cada pago. Si necesitas factura, indícanos tu RUC antes de pagar.', NOW(), 'sistema'),
('¿Cómo uso la app de la clínica?', 'Descarga la app Clínica Cárdenas, entra con tu DNI y, la primera vez, pide tu código de activación. Desde ahí reservas, reprogramas, pagas y ves tus documentos.', NOW(), 'sistema'),
('¿Qué hago si me duele después de un tratamiento?', 'Es normal una molestia leve por 1 o 2 días. Si el dolor es fuerte, hay sangrado que no para o hinchazón, llámanos o escríbenos para atenderte cuanto antes.', NOW(), 'sistema'),
('¿Hacen blanqueamiento dental?', 'Sí, hacemos blanqueamiento en consultorio. Antes revisamos que no haya caries ni sensibilidad para que el resultado sea seguro.', NOW(), 'sistema'),
('¿Tienen estacionamiento?', 'Hay estacionamiento en la calle frente al local, en la Urb. La Floresta.', NOW(), 'sistema');

INSERT INTO permissions (code, group_name, label, sort_order) VALUES
('MENSAJES', 'Comunicación', 'Conversar con pacientes por WhatsApp y configurar el asistente virtual', 71);

INSERT INTO role_permissions (role_code, permission_code) VALUES
('ADMIN', 'MENSAJES'), ('RECEPCION', 'MENSAJES'), ('DIRECTOR', 'MENSAJES');
