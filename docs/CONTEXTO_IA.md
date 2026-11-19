# Contexto del proyecto para asistentes de IA

Lee este archivo primero. Resume qué es el sistema, dónde está cada cosa, cómo se prueba y se publica, y las
reglas que no se deben romper. El detalle funcional (historias de usuario, cuentas demo, puertos) está en
`README.md`; lo pendiente, en `PENDIENTES.md`.

## Qué es

Sistema de gestión de la **Clínica Dental Cárdenas** (Chaclacayo, Lima, Perú), proyecto universitario que ya está
publicado en internet:

- **Web del personal:** recepción, odontólogos, caja, director y administrador (27 historias de usuario + mejoras).
- **Portal del paciente** en la misma web, ruta `/app` (PWA).
- **App Android del paciente** (Kotlin Multiplatform + Compose; también compila para iOS). Versión actual **2.4.0**
  (código 6). El APK se descarga desde el QR del login: `/app/ClinicaCardenas.apk`.
- **Producción:** https://clinica-cardenas.chilecentral.cloudapp.azure.com (VM de Azure con Docker Compose + Caddy).

Idioma de la interfaz, mensajes de error, commits y documentación: **español de Perú**. Moneda: soles (S/), IGV 18 %.
Zona horaria: America/Lima.

## Arquitectura

| Carpeta | Qué es | Tecnología |
|---|---|---|
| `backend/` | API REST principal | Java 21, Spring Boot 3.4, JPA, Flyway, JWT, MySQL 8.4 |
| `frontend/` | Web del personal y portal del paciente | Angular 18 standalone, sin librerías de UI |
| `movil/` | App del paciente (y vista del personal) | Kotlin Multiplatform, Compose, Ktor, Koin |
| `notificaciones/` | Microservicio de avisos (WhatsApp Cloud API, correo) | Node 20 sin dependencias |
| `whatsapp-sim/` | Simulador de WhatsApp con panel web (`/whatsapp-sim/`) | Node 20 |
| `sunat-sim/` | API SUNAT simulada (sendBill, CDR, consulta RUC) | Node 20 |
| `deploy/` | Instalación y actualización automática en la nube, Caddyfile, respaldos | bash |
| `pruebas/` | Pruebas e2e contra la API (`node pruebas/<x>-e2e.js http://localhost:8080`) | Node 20 |
| `docs/` | Flujo de ramas, RENIEC, pagos y SUNAT reales, este archivo | Markdown |

### Backend (`backend/src/main/java/pe/clinicacardenas/`)

Un paquete por módulo, cada uno con `*Controller` (endpoints + `@PreAuthorize` por permiso), `*Service` (reglas de
negocio, muchas con `JdbcTemplate`) y entidades/repositorios JPA:

`agenda` (citas, horarios libres, confirmación 2 h, seguimiento de llamadas) · `assistant` (asistente de WhatsApp
y reserva por pasos) · `auth` (login, 2FA por app o correo, recuperación, `MailNotifier`) · `billing` (boletas y
facturas UBL 2.1, envío a SUNAT, `InvoicePdf` con formato SUNAT y QR) · `care` (respuestas de WhatsApp,
seguimiento, lista de espera, controles) · `cash` (caja, cobros, egresos) · `clinical` (historia clínica, odontograma
FDI, planes, evoluciones, consentimientos firmados) · `commission` · `dashboard` · `files` (archivos, `SimplePdf`
generador de PDF propio) · `inventory` (lotes FEFO, compras, solicitudes de insumos) · `notification` (bandeja de
salida con reintentos) · `patient` (pacientes, menores con representante legal, UBIGEO, consulta RENIEC) ·
`payment` (Mercado Pago, modo simulado) · `portal` (API del paciente: citas, historia, documentos, archivos
solicitados, contacto) · `report` · `security` (JWT, roles y 17+ permisos editables) · `user` (usuarios y perfil).

Base de datos: migraciones Flyway `V1`…`V15` en `backend/src/main/resources/db/migration/`. **Nunca edites una
migración ya publicada**: crea la siguiente (`V16__descripcion.sql`). Errores de negocio con `ApiException`
(`ApiException.field("campo", "mensaje")` para errores de formulario; el frontend los muestra bajo el campo).

### Frontend (`frontend/src/app/`)

- `core/api.service.ts`: todas las llamadas HTTP (clase `Api`). `core/format.ts`: fechas, moneda, `apiError`.
- `pages/`: pantallas del personal. `pages/clinical/`: historia clínica y odontograma.
- `portal/`: portal del paciente (`portal-shell` con el menú inferior; Mis citas, Mi historia, Promociones, Mi cuenta, Perfil).
- `shared/`: componentes comunes (iconos SVG propios en `icon.component.ts`, buscador de tablas, modal legal).
- Estilos globales en `src/styles.css` con variables CSS; diseño responsive (las tablas pasan a tarjetas en celular).

### App móvil (`movil/composeApp/src/commonMain/kotlin/pe/clinicacardenas/pacientes/`)

`data/remote` (`ApiClient` con Ktor, `Dto.kt`), `data/repository/ClinicRepository.kt`, `di/Modules.kt` (Koin:
registrar aquí cada ViewModel nuevo), `App.kt` (pestañas del paciente: Citas, Historia, Docs, Promos, Avisos,
Perfil), `ui/<módulo>/` (pantalla + ViewModel con `StateFlow`), `ui/components` y `ui/theme` (colores `Brand`).
Compilar: `cd movil && ./gradlew :composeApp:assembleDebug` (requiere Android SDK). Subir `versionCode`/`versionName`
en `composeApp/build.gradle.kts` y `iosApp/project.yml` en cada versión, y copiar el APK a
`frontend/public/app/ClinicaCardenas.apk` para que lo sirva la web.

## Cómo correrlo y probarlo

- Todo junto en local: `docker compose up -d --build` (ver puertos y cuentas demo en `README.md`; contraseña demo `Clinica2026`).
- Backend suelto: `cd backend && mvn -DskipTests package && java -jar target/app.jar` con las variables `DB_URL`,
  `DB_USER`, `DB_PASSWORD`… (ver `docker-compose.yml` y `application.yml`).
- Pruebas unitarias: `cd backend && mvn test`.
- Pruebas e2e (backend local con datos demo): `revision-profesor`, `portal-paciente`, `registro-legal`,
  `logica-citas`, `asistente`, `completo`, `sprint1`. Algunas dependen de la hora del día (horarios libres).
- Web: `cd frontend && npx ng build --configuration production` debe compilar sin errores.

## Publicación (producción)

1. Flujo de ramas en `docs/FLUJO_GIT.md`: `feat/<versión>/<área>` → PR a `release/<versión>` → PR a
   `sprint-final-todas-las-hu`. Commits *Conventional Commits* en español (`feat(portal): …`).
2. El servidor revisa `sprint-final-todas-las-hu` **cada 5 minutos** (`deploy/actualizar.sh`): respalda la base,
   descarga, reconstruye con Docker y Flyway aplica las migraciones nuevas. No hay que entrar al servidor.
3. Las variables secretas (claves de WhatsApp, correo SMTP, token RENIEC, Mercado Pago, contraseña de la base) están
   solo en el `.env` del servidor y del equipo local. **Nunca se suben al repositorio** (`.env.example` lista los nombres).

## Reglas que no se deben romper

- **No ejecutar las pruebas e2e contra producción**: crean pacientes y pueden enviar WhatsApp reales.
- Respaldar la base antes de cualquier cambio destructivo en producción (`deploy/respaldo.sh`).
- Datos personales (Ley 29733): consentimiento al registrar y al activar la app; el paciente no puede cambiar nombre,
  DNI ni fecha de nacimiento (vienen de RENIEC), solo celular y correo. La historia clínica solo la ven odontólogo y administrador.
- Un paciente solo ve sus datos: el id sale del token (`/api/portal/**`), nunca de la URL.
- Menores de 18: representante legal obligatorio (no "contacto de emergencia").
- Comprobantes: formato SUNAT (UBL 2.1, serie B001/F001, QR, importe en letras). SUNAT hoy es simulada.
- Promociones: precio antes/ahora, vigencia y condiciones visibles (Código de Protección al Consumidor, Ley 29571).
- Cada cambio de permisos se controla en el servidor (`@PreAuthorize`), no solo ocultando el menú.

## Estado actual (noviembre 2026, versión 2.4.0)

Recién agregado: portal y app con historia de tratamientos, pagos, ficha y consentimientos; archivos solicitados
(radiografías) que suben paciente, recepción o administración; confirmación de cita hasta 2 h antes con alerta a
recepción para llamar; boleta/factura PDF con formato SUNAT adjunta al correo con su XML; QR de descarga de la app
en el login; autocompletado con RENIEC al escribir el DNI (necesita `RENIEC_API_TOKEN`, ver `docs/CONSULTA_RENIEC.md`);
Promociones como módulo propio en la app y el portal.

Pendiente (ver `PENDIENTES.md`): número propio de WhatsApp, SUNAT y pagos reales, notificaciones push con
Firebase, publicación en Google Play, token RENIEC en producción.
