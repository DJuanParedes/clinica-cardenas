# Producción — Clínica Cárdenas V4.9.17 / MySQL

Esta documentación corresponde únicamente a la versión vigente.

## Arquitectura de datos

Producción usa un único servidor **MySQL** dentro de Docker. Cada microservicio persistente utiliza una base y un usuario independientes. El contenedor MySQL pertenece a la red interna `db` y no publica `3306` al host.

## Preparación

1. Copia `.env.production.example` a `.env.production`.
2. Configura `DOMAIN`, datos del administrador y Twilio.
3. Ejecuta `PREPARAR-PRODUCCION.bat` en Windows o `PREPARAR-PRODUCCION.sh` en Linux para generar secretos MySQL seguros.
4. Ejecuta `VALIDAR-PRODUCCION.ps1`/`.sh`.
5. Ejecuta `INICIAR-PRODUCCION.bat` o `INICIAR-PRODUCCION.sh`.

## Bases creadas

`auth_db`, `patient_db`, `appointment_db`, `clinical_db`, `finance_db`, `lab_db`, `operations_db`, `inventory_db`, `content_db`, `encounter_db` y `survey_db`.

El script `database/mysql/init-databases.sh` crea las bases y los usuarios durante la inicialización de un volumen nuevo.

## Seguridad

- MySQL no expone un puerto público en producción.
- Cada servicio recibe solo la credencial de su propia base.
- `.env.production` permanece fuera de Git y del contexto de construcción Docker.
- Caddy es el único componente que publica HTTP/HTTPS.

## Datos existentes

Esta entrega no convierte automáticamente una base de datos preexistente de otro motor. Si existe información real que deba conservarse, realiza una migración de datos controlada antes de poner la versión MySQL en producción.

## Validación final

No uses datos reales hasta comprobar: HTTPS, autenticación/OTP, salud de MySQL, salud de los microservicios, copias de seguridad y restauración.
