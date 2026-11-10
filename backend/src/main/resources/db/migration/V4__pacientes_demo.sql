-- Marca los pacientes de ejemplo que se crean al instalar. Sus números y correos son inventados y podrían ser de
-- personas reales, por eso el microservicio de notificaciones no les envía WhatsApp ni correos de verdad.
ALTER TABLE patients ADD COLUMN demo BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE patients SET demo = TRUE
WHERE doc_type = 'DNI' AND doc_number IN (
  '45872196','70214583','41236985','72589634','46985231','73659812','40125896','75896321','43215678',
  '71548236','44875123','76321458','42563987','70985632','45698712','72145896','48523691','74123659'
);
