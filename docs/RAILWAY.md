# Preparación para Railway

El repositorio está preparado como fuente segura para conectar con Railway después de
definir la topología de producción. La versión verificada es `4.9.17` y se conservan:

- `docker-compose.production-secure.yml` como referencia de producción;
- `docker/Dockerfile.service` para construir cualquiera de los 15 servicios Java con el
  argumento `MODULE`;
- `frontend/Dockerfile` para la interfaz Angular/Nginx;
- `.env.production.example` como inventario de variables, sin secretos reales.

## Flujo recomendado

1. Mantener el repositorio privado y proteger la rama `main`.
2. Confirmar que el flujo `Validación del repositorio` termina correctamente en GitHub.
3. Crear un proyecto nuevo en Railway y conectarlo a este repositorio.
4. Provisionar MySQL persistente y evitar exponer su puerto públicamente.
5. Crear los servicios de aplicación con red privada. Para cada servicio Java, usar el
   contexto raíz, `docker/Dockerfile.service` y el valor `MODULE` correspondiente.
6. Crear el frontend desde `frontend/Dockerfile` y exponer públicamente solo la interfaz
   y el gateway.
7. Configurar las variables de `.env.production.example` en Railway; no subir un `.env`.
8. Sustituir todas las URLs internas de Compose por los nombres privados asignados en
   Railway y adaptar cada servicio al puerto inyectado por la plataforma.
9. Ejecutar migraciones y pruebas con datos ficticios antes de importar información real.
10. Configurar dominio, TLS, copias de seguridad y monitoreo antes de abrir producción.

## Servicios Java (`MODULE`)

`eureka-server`, `api-gateway`, `auth-service`, `patient-service`,
`appointment-service`, `clinical-service`, `finance-service`, `lab-service`,
`operations-service`, `inventory-service`, `dashboard-service`, `content-service`,
`encounter-service`, `survey-service` y `backup-service`.

## Decisiones pendientes antes del primer despliegue

Railway ejecutará cada servicio de forma independiente, mientras el Compose actual fue
diseñado para un único host Docker. Antes de desplegar se debe decidir si se conserva
Eureka o se reemplaza su descubrimiento por DNS privado, cómo compartir variables y cómo
persistir los respaldos fuera del disco efímero. Estas decisiones afectan costos, red y
recuperación; no deben improvisarse durante el despliegue de datos clínicos.
