# Alcance de la entrega actual V4.9.17

Esta entrega contiene únicamente la versión vigente de Clínica Cárdenas.

## Incluye
- Frontend web, Gateway, Eureka y microservicios clínicos/administrativos.
- MySQL 8.4 como único motor de persistencia.
- 11 bases independientes dentro de una sola instancia MySQL.
- Inicialización automática de bases y usuarios.
- Inicio local optimizado por etapas.
- Compilación Java en reactor único y uso diario sin recompilar.
- Producción con Caddy/HTTPS, red interna para MySQL y secretos externos.
- Respaldos lógicos, importación controlada y respaldo de volúmenes local.

## Local
- MySQL: `localhost:3307`.
- Portal: `http://localhost:8080`.
- Gateway: `http://localhost:8090`.
- Eureka: `http://localhost:8761`.

## Producción
- Solo Caddy publica 80/443.
- MySQL, Gateway, Eureka y microservicios permanecen internos.

## Pendiente recomendado para madurez operativa
- Migraciones versionadas de esquema con Flyway/Liquibase.
- Política automática de `mysqldump`, cifrado y copia externa.
