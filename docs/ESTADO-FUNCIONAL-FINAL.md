# Estado funcional actual — Clínica Cárdenas V4.9.17

## Persistencia
- MySQL 8.4 es el único motor de base de datos de la entrega.
- Local: un contenedor `clinica-mysql` con 11 bases separadas, una por microservicio con persistencia.
- Producción: el mismo modelo de 11 bases/usuarios dentro de una instancia MySQL en red Docker interna.
- En local MySQL se publica en `localhost:3307` únicamente para administración con herramientas como DBeaver.
- En producción el puerto de MySQL no se publica al host.

## Aplicación
- 16 módulos Maven de aplicación.
- 17 servicios Compose en local: MySQL + 16 componentes.
- 18 contenedores en producción: MySQL + 16 componentes + Caddy.
- Los servicios Java usan `application-mysql.yml`, MySQL Connector/J y `MySQLDialect`.

## Seguridad
- JWT y secretos de base de datos se generan fuera del código fuente.
- El ADMIN inicial define su contraseña mediante recuperación OTP.
- Producción usa Twilio Verify y expone únicamente Caddy por 80/443.
- Cada microservicio de datos utiliza un usuario MySQL limitado a su propia base.

## Respaldos
- El módulo de copias exporta información lógica y registra trazabilidad.
- Para recuperación total se recomienda además un `mysqldump`/respaldo nativo de MySQL y copia del volumen de documentos clínicos.
- Antes de una actualización de producción debe probarse restauración en una copia o staging.

## Esquema
Actualmente JPA utiliza `hibernate.ddl-auto=update`. Para una operación de producción madura se recomienda introducir migraciones versionadas con Flyway o Liquibase.
