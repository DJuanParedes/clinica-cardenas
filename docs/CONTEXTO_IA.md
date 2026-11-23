# Contexto del proyecto para asistentes de IA

Lee este archivo primero. Resume qué es el sistema, dónde está cada cosa, cómo se prueba y se publica, y las
reglas que no se deben romper. El detalle funcional (historias de usuario, cuentas demo, puertos) está en
`README.md`; lo pendiente, en `PENDIENTES.md`.

## Qué es

Sistema de gestión de la **Clínica Dental Cárdenas** (Chaclacayo, Lima, Perú), proyecto universitario que ya está
publicado en internet:

- **Web del personal:** recepción, odontólogos, caja, director y administrador (27 historias de usuario + mejoras).
- **Portal del paciente** en la misma web, ruta `/app` (PWA).
- **App Android del paciente** (Kotlin Multiplatform + Compose; también compila para iOS). Versión de la app **2.4.0**
  (código 6); el sistema web va en la **2.10.0**. El APK se descarga desde el QR del login: `/app/ClinicaCardenas.apk`.
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
FDI, periodontograma, anamnesis por plantillas, signos vitales, planes, evoluciones, consentimientos firmados) · `commission` · `dashboard` · `files` (archivos, `SimplePdf`
generador de PDF propio) · `inventory` (lotes FEFO, compras, solicitudes de insumos) · `notification` (bandeja de
salida con reintentos) · `patient` (pacientes, menores con representante legal, UBIGEO, consulta RENIEC, fuente de captación, etiquetas,
N° de historia clínica, datos fiscales y familiares) ·
`lab` (laboratorios y órdenes de laboratorio; el pago al laboratorio es un egreso de caja) ·
`payment` (Mercado Pago, modo simulado) · `portal` (API del paciente: citas, historia, documentos, archivos
solicitados, contacto) · `report` · `security` (JWT, roles y 17+ permisos editables) · `user` (usuarios y perfil).

Base de datos: migraciones Flyway `V1`…`V22` en `backend/src/main/resources/db/migration/`. **Nunca edites una
migración ya publicada**: crea la siguiente (`V23__descripcion.sql`). Errores de negocio con `ApiException`
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
- Pruebas e2e (backend local con datos demo): `revision-profesor`, `portal-paciente`, `version-2-5`, `registro-legal`,
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

## Estado actual (noviembre 2026, versión 2.10.0)

Versión 2.10.0: correcciones de uso (migración V24).
- Categorías únicas (`core/categories.ts`, mismo orden y color): la cita guarda `appointments.category` (se elige en
  "Categoría y motivo"; el detalle del motivo es opcional) y las reglas de comisión tienen `commission_rules.category`.
  Prioridad de regla: procedimiento puntual → su categoría → general (`CommissionService.ruleFor`).
- Agenda semanal con eje de horas: citas posicionadas por hora, carriles cuando se cruzan, canceladas ocultas
  (casilla "Ver canceladas"), columna de horas fija al desplazar.
- Consumo exacto por atención: al marcar un procedimiento como realizado (plan o evolución) se abre "Materiales usados",
  precargado con el kit (`procedure_supplies`) y editable; `TreatmentItemDone.used` manda lo indicado
  (`GET /api/plan-items/{id}/supplies`). Si no se indica nada se descuenta el kit.
- Fotos reales de materiales: `MaterialPhotoSeeder` descarga al arrancar fotos libres de Wikimedia Commons para los
  materiales sin foto (créditos en `docs/CREDITOS_IMAGENES.md`).
- Responsive: tablas como tarjetas hasta 900 px con el nombre de la columna en cada dato (`AppComponent` pone
  `data-label`); en pantallas grandes la tabla se desplaza dentro de su tarjeta; gráficos de barras a escala 1:1 con
  etiquetas que no se enciman. Revisión automática con Playwright en 375/768/1280 px.

Versión 2.9.0: cumplimiento legal y operación real (migración V23).
- Libro de Reclamaciones virtual (paquete `complaint`, permiso `RECLAMOS`: admin, dirección y recepción): página
  pública `/libro-reclamaciones` (enlazada desde /reservar y el login) con los campos del Anexo I del DS 011-2011-PCM,
  número correlativo por año (`2026-000001`) y código de consulta de 8 letras. No se condiciona a aceptar la política
  de privacidad. Plazo en días hábiles (`reclamos_plazo_dias`, 15 por defecto; feriados nacionales + Semana Santa en
  `ComplaintService.holiday`). Al registrar: copia en PDF al correo del consumidor, tarea ALTA (origen RECLAMO) y aviso
  a `reclamos_correo_aviso`; recordatorio diario 8:00. Módulo /reclamos para responder (correo, domicilio o en la
  clínica) y exportar el CSV del año. Consulta pública con código + documento.
- Plantillas de WhatsApp propias (`NotificationService.WA_PROMOCION/WA_CUMPLEANOS/WA_DOCUMENTO`): campañas →
  `novedad_clinica`, cumpleaños → `cumpleanos_clinica`, consentimientos → `documento_por_firmar`. Textos en
  `docs/PLANTILLAS_WHATSAPP.md`; si Meta no las aprobó, se usa `aviso_clinica`.
- Respaldo (`deploy/respaldo.sh`): base de datos + volumen de archivos, copia a Azure Blob con la identidad
  administrada de la VM (`/etc/clinica-respaldo.conf` → `RESPALDO_BLOB`) y prueba de restauración los domingos en un
  MySQL temporal (`/opt/clinica-respaldos/pruebas-restauracion.log`).
- SUNAT configurable por `.env` (`SUNAT_URL`, certificado en `deploy/sunat/`); sin variables sigue el simulador.
  Guía ANPD en `docs/PROTECCION_DATOS_ANPD.md`. Prueba: `pruebas/version-2-9-e2e.js`.

Versión 2.8.0: operación y detalles de uso (migraciones V19 a V22).
- Kardex valorizado (V19, permiso `INVENTARIO_COSTOS`): cada movimiento de `inventory_moves` guarda `unit_cost`,
  `total_cost` (con signo) y `value_after`. Las salidas se valorizan al costo de los lotes que consumen (FEFO); el
  consumo clínico guarda `patient_id` y `procedure_id`. `KardexService`: kardex por material, valor del inventario y
  consumo por servicio (con % del precio en materiales), por paciente y por material. Pestañas en /inventario.
- Ortodoncia (V20, paquete `ortho`, permiso `HISTORIA_CLINICA`): `ortho_cases`, `ortho_controls` (arcos, elásticos,
  higiene) y `ortho_photos`, que solo enlazan `patient_files` por etapa y vista. Pestaña "Ortodoncia" en la ficha
  (álbum y comparación por etapas) y lista /ortodoncia con controles atrasados.
- Consentimientos informados (V21, paquete `consent`, clase `InformedConsentService` para no chocar con el
  `ConsentService` de datos personales): plantillas editables (permiso `CONSENTIMIENTOS`), envío por WhatsApp,
  correo o tablet con enlace HMAC `/firmar/{token}` (vence en 7 días). La firma genera un PDF en la ficha con la
  constancia (fecha, IP, a dónde se envió y SHA-256 del texto). Menores: firma el apoderado. API `/api/informed-consents`.
- Ficha (V22): notas fijas (`patient_notes`) y franja fija con alergias y medicación (`/flags`; el detalle médico
  solo con `HISTORIA_CLINICA`). Botón global "Crear" en el menú. Reporte anual `/reportes/anual` con PDF
  (`SimplePdf.table` y `bars`) y CSV (`AnnualReportService`).
- Prueba: `pruebas/version-2-8-e2e.js`. Pendiente solo si la clínica lo pide: doble moneda, multisede, chat interno
  y pantalla de sala de espera.

Versión 2.7.0: seguimiento y marketing (paquete `crm` del backend, migración V18).
- Tareas (`tasks`, permiso `TAREAS`, todos los roles): llamar, cobrar, recordar…, de un paciente, de un lead o generales,
  con responsable, fecha y prioridad. Bandeja `/tareas`, panel en la ficha del paciente y contador en el menú e Inicio.
- Lista de pacientes: última y próxima cita, tarea pendiente, % de avance del presupuesto (planes ACTIVO) y saldo
  (`PatientInsightsService`), con filtros de seguimiento (`SegmentFilter`, el mismo motor de los segmentos).
- Marketing (`/marketing`, permiso `MARKETING`: administración y dirección): segmentos guardados (`segments`), tareas
  en lote desde un segmento, campañas (`campaigns`, `campaign_recipients`) y saludo de cumpleaños (apagado por
  defecto; `app_settings.cumpleanos_*`). Consentimiento de publicidad aparte del de datos: `patients.marketing_consent`
  (NULL sin preguntar, TRUE, FALSE) y documento legal `PUBLICIDAD`. Las campañas solo van a TRUE; cumpleaños a quien
  no es FALSE. Responder "BAJA" por WhatsApp lo pone en FALSE (`CareService.whatsappReply`). Envío 08:00–20:00.
- Embudo de ventas (`/embudo`, permiso `LEADS`): `leads` y `lead_events`, etapas NUEVO → CONTACTADO → CITA_AGENDADA →
  ASISTIO → GANADO / PERDIDO. Avanza solo con los eventos de la agenda (`LeadService.onAppointment`). Quien escribe por
  WhatsApp sin ser paciente entra como lead (`leads_whatsapp_auto`). No se vincula al paciente solo por el celular:
  se sugiere y recepción confirma.
- Prueba: `pruebas/version-2-7-e2e.js`.

Versión 2.6.0: comunicación con el paciente (`/comunicacion`, permiso `AUTOMATIZACIONES`, solo administración).
- Plantillas de mensajes editables (`message_templates`) con variables `{paciente}`, `{fecha_hora}`, `{odontologo}`,
  `{enlace_encuesta}`… y botones de respuesta; `MessageTemplateService.render` arma cada aviso. Historial de envíos.
- Automatizaciones (`automation_rules`): 5 días antes, 1 día antes con Confirmar / No podré asistir, el día de la
  cita, agradecimiento y encuesta después de ATENDIDA. Reemplazan el recordatorio fijo; editar una regla o una
  plantilla reprograma las citas futuras (`NotificationService.rescheduleFuture`). El recordatorio de 1 día conserva
  la clave `recordatorio:` y el tipo `RECORDATORIO`.
- WhatsApp con botones interactivos (`botonesWa`); si Meta rechaza fuera de la ventana de 24 h, cae a texto con
  "Responde 1 / 2". El simulador los muestra y responde como `button_reply`.
- Páginas públicas sin sesión: `/reservar` (usa los horarios libres reales, valida DNI + fecha de nacimiento si el
  paciente ya existe, `appointments.booked_via = WEB`), `/registro` (prerregistro con antecedentes de salud) y
  `/encuesta/{token}` (token HMAC con `app.jwt.secret`). API en `/api/public/*` con límite por IP.
- El despachador y la anulación de recordatorios bloquean la fila (`PESSIMISTIC_WRITE`) para que una cancelación no
  se cruce con un envío en curso.
- Prueba: `pruebas/version-2-6-e2e.js`. Siguiente: 2.7.0 (tareas, listas y campañas, embudo).

Versión 2.5.0: ficha del paciente con fuente de captación, etiquetas, N° de historia clínica, RUC para factura y
familiares; anamnesis por plantillas editables (`/configuracion/anamnesis`) con alertas en la ficha; signos vitales;
periodontograma con %IP y %SAS; órdenes de laboratorio (`/laboratorio`) con estados, pruebas y pagos por caja.

Versión 2.4.0: portal y app con historia de tratamientos, pagos, ficha y consentimientos; archivos solicitados
(radiografías) que suben paciente, recepción o administración; confirmación de cita hasta 2 h antes con alerta a
recepción para llamar; boleta/factura PDF con formato SUNAT adjunta al correo con su XML; QR de descarga de la app
en el login; autocompletado con RENIEC al escribir el DNI (necesita `RENIEC_API_TOKEN`, ver `docs/CONSULTA_RENIEC.md`);
Promociones como módulo propio en la app y el portal.

Pendiente (ver `PENDIENTES.md`): número propio de WhatsApp, SUNAT y pagos reales, notificaciones push con
Firebase, publicación en Google Play, token RENIEC en producción.
