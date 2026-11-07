-- Los pacientes que crean las pruebas automáticas (carpeta pruebas/) también tienen números inventados:
-- se marcan como de ejemplo para que nunca reciban WhatsApp ni correos reales.
UPDATE patients SET demo = TRUE
WHERE email LIKE '%@correo.pe'
   OR (first_name = 'Sin' AND last_name LIKE 'Correo %')
   OR (first_name = 'Prueba' AND last_name LIKE 'Completa %')
   OR (first_name = 'Lucía Fernanda' AND last_name = 'Ñahui Céspedes');
