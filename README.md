# Clínica Dental Cárdenas · versión completa

Sistema Web de Gestión Odontológica con **las 27 historias de usuario** del product backlog: pacientes, agenda,
historia clínica y odontograma, facturación electrónica con SUNAT, caja, comisiones, indicadores gerenciales,
aplicación móvil del paciente, notificaciones por WhatsApp y correo, y seguridad por roles y permisos.

> Esta rama (`sprint-final-todas-las-hu`) contiene la versión completa. La rama `main` conserva el Sprint 1.
> Ambas pueden correr a la vez: el Sprint 1 usa los puertos 8081/8082 y esta versión 9081/9082/9083.

| Capa | Tecnología | Imagen Docker |
|---|---|---|
| Frontend | Angular 18 (standalone, sin librerías de UI) | `nginx:1.27-alpine` · ~25 MB |
| Backend | Java 21 · Spring Boot 3.4 · JPA · Flyway · JWT | JRE recortado con `jlink` sobre `alpine` · ~90 MB |
| Base de datos | MySQL 8.4 | `mysql:8.4` con memoria ajustada |
| API simulada de SUNAT | Node 20 sin dependencias | `node:20-alpine` · ~45 MB |
| Microservicio de notificaciones | Node 20 sin dependencias | `node:20-alpine` · ~45 MB |

## Cómo levantarlo

Solo necesitas **Docker Desktop** abierto. No hace falta tener Java, Maven ni Node instalados.

En Windows basta con doble clic en uno de estos archivos:

| Archivo | Qué hace |
|---|---|
| `iniciar.bat` | Construye y levanta todo, y abre el navegador |
| `actualizar.bat` | Aplica cambios del código **conservando los datos** |
| `reiniciar-limpio.bat` | Borra la base de datos y vuelve a crear los datos de ejemplo |
| `pruebas-unitarias.bat` | Ejecuta las pruebas JUnit del backend (resultado en `pruebas-unitarias.log`) |

O desde una terminal:

```bash
docker compose up -d --build
```

La primera vez tarda unos minutos porque descarga dependencias y compila. Luego:

- **Aplicación (personal):** http://localhost:9081
- **App del paciente (celular):** http://localhost:9081/app · demo: DNI `45872196`, contraseña `Paciente2026`
- **API simulada de SUNAT:** http://localhost:9082
- **Microservicio de notificaciones** (bandeja de WhatsApp y correo): http://localhost:9083
- **MySQL** (DBeaver / Workbench): `localhost:3308` · base `clinica` · usuario `clinica` · contraseña `clinica`

Para ver el avance del arranque: `docker compose logs -f backend`.
Para apagar: `docker compose down`. Para borrar también la base de datos: `docker compose down -v`.

### Cuentas de prueba

Todas usan la contraseña **`Clinica2026`**.

| Usuario | Rol | Qué ve |
|---|---|---|
| `admin` | Administrador | Todo, incluida la gestión de usuarios y horarios |
| `recepcion` | Recepcionista | Pacientes, nueva cita, agenda, llegada de pacientes |
| `dra.cardenas` | Odontóloga (Ortodoncia) | Su agenda, historia clínica, odontograma, plan |
| `dr.ramos` | Odontólogo (Endodoncia) | Igual que la anterior |
| `caja` | Responsable de caja | Caja (cobros y egresos), comprobantes y SUNAT |
| `director` | Director | Indicadores, flujo de caja, agenda, pacientes y auditoría |

Lo que ve cada rol se puede cambiar en **Roles y permisos** (cuenta `admin`).

En el primer arranque también se crean 8 pacientes, citas para hoy y el siguiente día hábil, y una historia
clínica completa (María Fernanda Quispe, DNI 45872196) con odontograma y plan en curso. Para arrancar sin
datos de ejemplo usa `APP_SEED_DEMO=false` en un archivo `.env` (ver `.env.example`).

### Consumo de recursos

Los contenedores tienen límites de memoria: MySQL 384 MB, backend 384 MB, frontend 64 MB, SUNAT simulada 64 MB y notificaciones 64 MB.
En reposo el sistema completo usa alrededor de 450–550 MB de RAM.

- MySQL arranca sin `performance_schema` y con un buffer pool de 64 MB.
- El backend usa un JRE recortado con `jlink`, GC serial y heap relativo al límite del contenedor.
- El frontend es estático y lo sirve nginx con gzip; el backend queda detrás de `/api` (mismo origen, sin CORS).

## Historias implementadas en la versión completa

Además de todo el Sprint 1 (detallado más abajo):

| Historia | Dónde está |
|---|---|
| **HU-13** Registrar pago | Caja → Registrar cobro. Se elige al paciente y la obligación (un procedimiento con saldo, o pago a cuenta), el importe y el medio: efectivo (con cálculo del vuelto), Yape, Plin, tarjeta o transferencia con su n.° de operación. Guarda fecha y responsable; emite un recibo interno. Bloqueo de fila: dos cobros simultáneos no superan el pendiente. Anulación con motivo |
| **HU-14** Saldo pendiente | Ficha del paciente → Cuenta y pagos: cargos (procedimientos realizados), abonos, saldo, saldo a favor y estado de cuenta con saldo acumulado |
| **HU-15** Ingresos y egresos | Caja del día y Flujo de caja: egresos por categoría, ingresos y egresos por día, por medio de pago y detalle; descarga CSV |
| **HU-16** Reglas de comisión | Comisiones → Reglas: porcentaje o monto fijo, general o por procedimiento, sobre el precio o solo lo cobrado, con vigencia. No permite reglas superpuestas |
| **HU-17** Calcular comisiones | Comisiones → Calcular: por odontólogo y período, con la regla vigente a la fecha de cada procedimiento; muestra lo que espera cobro, lo que no tiene regla y lo ya liquidado |
| **HU-18** Liquidación | Se eligen los procedimientos a liquidar; cada uno se liquida una sola vez. Pagos parciales o totales, estados por pagar / parcial / pagada y anulación sin pagos |
| **HU-19** Ingresos por período | Indicadores → Ingresos: total, ticket promedio, variación contra el período anterior, facturado SUNAT, resultado de caja; por odontólogo, especialidad y medio |
| **HU-20** Citas por estado | Indicadores → Citas: programadas, atendidas, canceladas y no asistió; tasas de asistencia y cancelación; por odontólogo; motivos de cancelación |
| **HU-21** Tratamientos | Indicadores → Tratamientos: realizados y pendientes por procedimiento y odontólogo, avance y planes |
| **HU-22** App del paciente | `/app`: aplicación instalable en el celular (PWA). Activación con DNI, fecha de nacimiento y código por WhatsApp; próximas citas con fecha, hora y odontólogo; confirmar asistencia; agregar al calendario; saldo; canal de avisos |
| **HU-23** Recordatorio de cita | Al programar una cita se agenda un recordatorio 24 h antes (o 2 h si es pronto) en el microservicio de notificaciones, por WhatsApp, correo o ambos según el paciente. Recordatorio manual desde la agenda sin duplicados |
| **HU-24** Aviso de cambio | Al reprogramar o cancelar: aviso inmediato con el nuevo horario, se anula el recordatorio anterior y se programa uno nuevo |
| **HU-26** Roles y permisos | Seguridad → Roles y permisos: matriz de 17 permisos por rol, aplicada en cada endpoint del servidor y en el menú; el cambio rige al instante |

### Microservicio de notificaciones (SP-02)

`notificaciones/` es un servicio aparte (puerto **9083**) que envía por WhatsApp y correo. Los proveedores son de
prueba: los mensajes se ven en su panel, con una vista tipo WhatsApp y una bandeja de correo.

- **Idempotencia:** cada mensaje lleva una clave (`recordatorio:{cita}:{fecha}:{canal}`); si llega dos veces no se reenvía.
- **Bandeja de salida en el backend:** las notificaciones se guardan en la misma transacción que la cita y se entregan
  al confirmarse; si el microservicio no responde se reintentan hasta 5 veces con espera creciente.
- **Recordatorios programados:** el microservicio los envía a su hora e informa el resultado al backend por un webhook firmado con token.
- En el panel se puede simular el servicio caído y enviar los recordatorios pendientes en el momento.

### App del paciente (SP-03)

El paciente entra con su documento y contraseña; su token solo abre `/api/portal/**` y el del personal no abre la API
del paciente. El identificador del paciente sale del token, nunca de la URL, así que no puede ver datos de otro.

## Qué incluye el Sprint 1

| Historia | Dónde está |
|---|---|
| **HU-25** Iniciar sesión | Pantalla de acceso, JWT, bloqueo de 15 min tras 5 intentos fallidos, cierre de sesión que invalida el token, protección de pantallas y endpoints por rol |
| Recuperar contraseña | Código de 6 dígitos con vencimiento de 15 min y máximo 5 intentos. Sin SMTP configurado, el código aparece en pantalla (modo demostración) y en el log del backend |
| **HU-27** Cuentas de usuario | Crear, editar, desactivar o reactivar, desbloquear y asignar contraseña temporal, todo con registro de cambios. Horario de atención por odontólogo |
| **HU-01** Registrar paciente | Validación de DNI (8 dígitos), carné de extranjería y pasaporte. Detecta duplicados mientras se escribe y la base los bloquea con una restricción única |
| **HU-02** Buscar paciente | Búsqueda exacta o por prefijo de documento y búsqueda parcial por nombres o apellidos, con índices. Si no hay resultados, ofrece registrar al paciente |
| **HU-03** Actualizar datos *(extra)* | Edición con fecha y usuario de modificación y registro de cambios |
| **HU-07** Programar cita | Horarios libres calculados en el servidor según el horario del odontólogo. Valida cruces del odontólogo y del paciente; un bloqueo de fila evita reservas simultáneas |
| Agenda diaria | Columnas por odontólogo, línea de "ahora" y estados de la cita (llegada, en atención, atendida, cancelada con motivo) |
| **HU-08 / HU-09** *(extra)* | Reprogramar revalidando disponibilidad y estados de la sala de espera |
| **HU-04** Historia clínica | Antecedentes, alergias en alerta y diagnósticos CIE-10. Solo la ven el odontólogo y el administrador (restringido en el servidor) |
| **HU-05** Odontograma | Interactivo por pieza y cara (notación FDI, dentición permanente y decidua). Rojo para hallazgos y azul para tratamientos; imprimible |
| **HU-06** Plan de tratamiento | Procedimientos del catálogo con precio, estado por procedimiento, evolución por sesión y avance del plan |
| **HU-10** Generar comprobante | Boleta B001 y factura F001 con correlativo controlado (bloqueo de serie), IGV incluido, validación de RUC con dígito verificador e importe en letras. Factura los procedimientos realizados del paciente |
| **HU-11** Enviar a SUNAT | Genera el XML UBL 2.1 y lo envía al servicio de facturación. Guarda el CDR y cada intento de envío |
| **HU-12** Consultar comprobante | Búsqueda por número, cliente, estado o fecha; representación impresa; descarga del XML y del CDR |

### Facturación electrónica y API simulada de SUNAT

Los comprobantes se emiten con el RUC de la clínica, **10102984841** (Clínica Dental Cárdenas). El RUC, la razón
social y la dirección se cambian sin tocar el código con las variables `EMPRESA_*` del archivo `.env`.

El backend hace lo mismo que con SUNAT real:

1. Genera el XML UBL 2.1 y lo **firma digitalmente** (XMLDSig, RSA-SHA256, firma `SignatureSP` dentro de
   `ext:ExtensionContent`). El valor de resumen de la firma es el código hash impreso en el comprobante.
2. Lo comprime en `10102984841-03-B001-1.zip` y lo envía por **SOAP `sendBill`** con WS-Security (usuario SOL
   secundario `RUC + MODDATOS`).
3. Lee la **Constancia de Recepción (CDR)** que devuelve SUNAT y guarda el estado y cada intento.

La API simulada (`sunat-sim/`, puerto **8082**) imita el servicio `billService` del entorno beta:

| Servicio | Qué hace |
|---|---|
| `POST /ol-ti-itcpfegem-beta/billService` · `sendBill` | Valida usuario SOL, nombre del ZIP, RUC emisor, serie, tipo, firma, receptor y totales. Responde con el CDR (ZIP) o con un SOAP Fault |
| `POST /ol-ti-itcpfegem-beta/billService` · `getStatusCdr` | Devuelve el CDR de un comprobante ya recibido |
| `GET /v1/ruc/{ruc}` | Consulta RUC: razón social, dirección, estado y condición (padrón de ejemplo) |
| `GET /` | Panel con los comprobantes recibidos y botones para simular el servicio **normal, lento o caído** |

Respuestas según el catálogo de errores de SUNAT:

| Caso | Código | Estado en el sistema |
|---|---|---|
| Comprobante correcto | `0` | Aceptado |
| RUC del receptor no figura en el padrón | `0` + observación `4093` | Aceptado con observaciones |
| Factura a un cliente sin RUC | `2800` | Rechazado (con CDR) |
| RUC del receptor dado de baja | `2018` | Rechazado (con CDR) |
| Total distinto de valor de venta + IGV | `3277` | Rechazado (con CDR) |
| XML sin firma o alterado | `2335` | Rechazado |
| Usuario o clave SOL incorrectos | `0102` | Error de envío, se puede reintentar |
| Ya enviado antes | `1033` | El sistema recupera el CDR con `getStatusCdr` |
| Servicio caído o sin conexión | — | Error de envío, se puede reintentar |

RUC de prueba del padrón: `20100070970` (activo), `20511465061` (colegio de Chosica), `20548777217`
(dado de baja, no habido). Al escribir un RUC en una factura, la razón social y la dirección se completan solas.

**Para pasar a SUNAT real** no cambia el código, solo la configuración del `.env`:

```
SUNAT_URL=https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService   # beta oficial
SUNAT_USUARIO=<usuario SOL secundario>
SUNAT_CLAVE=<clave SOL>
SUNAT_CERTIFICADO=file:/ruta/certificado.pfx
SUNAT_CERTIFICADO_CLAVE=<clave del certificado>
```

El certificado incluido (`backend/src/main/resources/sunat/certificado-demo.p12`) es autofirmado y solo sirve para
la demostración. Con `SUNAT_MODE=interno` el backend valida sin red, sin usar la API simulada.

## App móvil (Android e iOS)

La carpeta [`movil/`](movil/README.md) tiene la app de pacientes hecha con **Kotlin Multiplatform + Compose Multiplatform**. Desde la app el paciente ve sus citas, las confirma, recibe recordatorios en el teléfono, revisa su estado de cuenta y elige por dónde le llegan los avisos.
Para generar el APK sin Android Studio, haz doble clic en `construir-apk.bat`; el APK queda en `apk\ClinicaCardenas.apk`. Los detalles están en [movil/README.md](movil/README.md).

## Estructura

```
clinica-cardenas/
├── docker-compose.yml
├── pruebas/sprint1-e2e.js           pruebas end-to-end de todas las historias y de SUNAT
├── pruebas/completo-e2e.js          pruebas end-to-end de las historias de la versión completa
├── sunat-sim/                       API simulada de SUNAT (billService SOAP, consulta RUC, panel)
├── notificaciones/                  microservicio de WhatsApp y correo (recordatorios, avisos, panel)
├── backend/                         Spring Boot
│   ├── src/main/resources/db/migration   V1 esquema · V2 catálogos (Flyway)
│   └── src/main/java/pe/clinicacardenas/
│       ├── auth/        login, recuperación, política de contraseñas
│       ├── security/    JWT, filtro, reglas por rol
│       ├── user/        cuentas, roles, horarios
│       ├── patient/     registro y búsqueda
│       ├── agenda/      odontólogos, horarios libres, citas
│       ├── clinical/    historia, diagnóstico, odontograma, plan, evolución
│       ├── billing/     series, comprobantes, UBL, firma digital, cliente SOAP de SUNAT
│       ├── cash/        pagos, saldos, egresos y flujo de caja
│       ├── commission/  reglas, cálculo y liquidación de comisiones
│       ├── report/      indicadores gerenciales
│       ├── notification/ bandeja de salida, recordatorios y avisos
│       ├── portal/      API de la app del paciente
│       ├── dashboard/   resumen de inicio por rol
│       └── common/      errores con formato único, auditoría
└── frontend/                        Angular
    └── src/app/
        ├── core/        API, sesión, guardas por rol, formatos
        ├── shared/      odontograma, buscador de pacientes, modal, íconos
        ├── layout/      menú lateral según rol
        └── pages/       una pantalla por historia
```

Todos los errores de la API usan el mismo formato:

```json
{ "timestamp": "...", "status": 409, "code": "CRUCE_HORARIO",
  "message": "Dra. Rosa Cárdenas Vílchez ya tiene una cita de 10:00 a 10:30. Elige otro horario libre.",
  "path": "/api/appointments" }
```

## Desarrollo local sin Docker (opcional)

```bash
# Solo la base de datos
docker compose up -d db

# Backend (IntelliJ o consola), con Java 21
cd backend
DB_URL="jdbc:mysql://localhost:3307/clinica?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Lima" mvn spring-boot:run

# Frontend, con Node 20
cd frontend
npm install
npm start          # http://localhost:4200 (redirige /api al backend en :8080)
```

## Pruebas realizadas

**Unitarias (JUnit, 15/15):** las 11 del Sprint 1 más recordatorio 24 h / 2 h, canal según preferencia, vigencia de reglas de comisión y agrupación de indicadores. Sprint 1: validación de pacientes, horarios libres y cruces, RUC, IGV, importe en letras,
odontograma, política de contraseñas y firma digital del XML (válida y detecta alteraciones). Se ejecutan con `pruebas-unitarias.bat` o `cd backend && mvn test`.

**End-to-end de la versión completa (27/27 en el navegador)** con `node pruebas/completo-e2e.js http://localhost:9081`:
permisos que se quitan y restauran al instante, cobros (validaciones, simultáneos, a cuenta, anulación), flujo de caja, reglas
y liquidación de comisiones, indicadores, activación y uso de la app del paciente, recordatorios, avisos de cambio y
cancelación. Con Node se agrega una prueba más que apaga el microservicio de notificaciones y verifica el reintento automático
(comprobado también a mano: el aviso queda en cola y pasa a programado al volver el servicio).

Las pruebas también se pueden correr desde la consola del navegador en http://localhost:9081 (los archivos se sirven en `/pruebas/`):

```js
eval((await (await fetch('/pruebas/completo-e2e.js')).text()).split("if (typeof module")[0]);
console.table((await completoE2E(location.origin)).results);
```

**End-to-end del Sprint 1 (48/48)** contra el sistema completo en Docker, con `node pruebas/sprint1-e2e.js http://localhost:9081`:

| Historia | Qué se probó |
|---|---|
| HU-25 | Login de los 5 roles · aviso de intentos restantes · bloqueo tras 5 intentos y desbloqueo · recuperación con código (incorrecto, débil, reutilizado) · cierre de sesión invalida el token · 6 accesos indebidos por rol devuelven 403 |
| HU-27 | Crear cuenta · validaciones · desactivar corta el acceso al instante · el admin no puede desactivarse · auditoría |
| HU-01 / HU-02 / HU-03 | Registro con normalización de nombres · duplicado bloqueado · DNI/fecha/teléfono inválidos · búsqueda por DNI y por nombre con tildes (24 ms) · edición con trazabilidad |
| HU-07 / HU-08 / HU-09 | Horarios libres · cita en horario libre · cruce del odontólogo y del paciente · fuera de horario y en el pasado · **reservas simultáneas: siempre una sola gana** · flujo de estados · reprogramar y cancelar con motivo |
| HU-04 / HU-05 / HU-06 | Historia y alerta de alergias · diagnóstico CIE-10 · odontograma válido e inválido (sin perder datos) · plan, evolución y cierre automático |
| SUNAT | XML firmado con el RUC 10102984841 y hash = DigestValue · envío SOAP aceptado y registrado en la API simulada · consulta RUC (activo, no habido, inexistente, inválido) · factura a RUC de baja rechazada con CDR `2018` · RUC fuera del padrón aceptado con observación `4093` · SUNAT caída → error → reintento aceptado · consulta de estado `getStatusCdr` |
| HU-10 / HU-11 / HU-12 | Boleta con IGV y correlativo · no se factura dos veces un procedimiento · factura con RUC validado · límite S/ 700 · envío a SUNAT y bloqueo de reenvío · XML UBL 2.1 y CDR · búsqueda por número, documento y estado |

También se recorrieron en el navegador: acceso, recuperación de contraseña, registro de paciente (con aviso de duplicado),
nueva cita, edición del odontograma y emisión de boleta, en escritorio y en celular.

Durante las pruebas se corrigió un caso real: dos recepcionistas reservando el mismo horario al mismo tiempo
podían crear dos citas. Ahora la reserva bloquea al odontólogo y lee con `READ COMMITTED`, y la prueba
con 6 solicitudes simultáneas acepta exactamente una.
