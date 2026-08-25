# Seguridad del repositorio

Este repositorio contiene código fuente, configuración de ejemplo y archivos necesarios
para construir Clínica Cárdenas. No debe contener credenciales reales, archivos `.env`,
datos de pacientes, respaldos, logs ni artefactos de compilación.

## Reportar una vulnerabilidad

Usa un aviso de seguridad privado de GitHub (*Security advisory*) en lugar de publicar
credenciales, datos clínicos o detalles explotables en un issue público.

## Reglas para secretos y datos

- Guarda secretos locales únicamente en `.env`; Git lo ignora.
- Configura secretos de producción directamente en la plataforma de despliegue.
- Usa `.env.production.example` solo como lista de variables y reemplaza todos los
  marcadores `CHANGE_ME` con valores aleatorios diferentes.
- Nunca reutilices los secretos de desarrollo en producción.
- No subas exportaciones de MySQL, respaldos, archivos de pacientes ni logos privados.
- Si un secreto llega al historial de Git, revócalo y rótalo; borrarlo en un commit
  posterior no lo elimina del historial.

Antes de un despliegue se debe revisar la configuración TLS, los permisos de red, las
copias de seguridad cifradas, el acceso mínimo necesario y las obligaciones legales
aplicables al tratamiento de datos de salud.
