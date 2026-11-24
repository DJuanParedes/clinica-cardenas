-- Plantilla de WhatsApp propia de cada aviso (recordatorio, cita registrada, reprogramada, cancelada)
-- y sus parámetros en JSON. Si la plantilla no está aprobada, el microservicio usa la plantilla general.
ALTER TABLE notifications ADD COLUMN wa_template VARCHAR(60) NULL, ADD COLUMN wa_params VARCHAR(1500) NULL;
