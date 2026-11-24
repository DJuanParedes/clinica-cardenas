# Portal del paciente — fase 1

Fecha: 19 de noviembre de 2026. Esta fase reutiliza los endpoints autenticados existentes; no modifica los
datos clínicos ni integra proveedores externos.

## Implementado

- Inicio ejecutivo en `/app`: próxima cita, acceso a indicaciones por mapa, avisos no leídos y accesos a agenda,
  tratamientos/documentos y cuenta.
- Agenda en `/app/agendar`: flujo de tres pasos con búsqueda por odontólogo/especialidad/motivo, tarjetas de
  profesionales, motivo, fecha, horarios y confirmación. El backend vuelve a validar cruces, disponibilidad,
  límite de citas y anticipación antes de crear la cita.
- Historial de citas existente: se mantiene separado en próximas y pasadas; la pantalla incorpora el acceso a
  reprogramación cuando la cita aún está dentro del plazo permitido.
- Ya estaban disponibles y se conservan: tratamientos, estado de solicitudes de archivos, documentos compartidos,
  consentimientos con constancia, saldo/movimientos, comprobantes con enlaces temporales y edición limitada de
  contacto.

## Contraste con la base

| Requisito | Soporte encontrado | Estado de esta fase |
| --- | --- | --- |
| Próxima cita, alertas y accesos | `PortalService.appointments`, `PortalExtrasService.notices` y Angular portal | Inicio ejecutivo incorporado |
| Agenda por pasos, filtros y perfiles | API de opciones, horarios, reserva, reprogramación y cancelación ya existente | Reserva guiada incorporada; reprogramación conserva el endpoint y acceso desde citas |
| Historia, resultados y descargas | Tratamientos, archivos compartidos, solicitudes y consentimientos; no resultados estructurados | Se mantienen documentos/visor y descargas existentes; resultados clínicos quedan pendientes |
| Recetas, tratamientos, pagos y comprobantes | Planes/tratamientos, caja, facturación y PDF existentes; no entidad de receta | Tratamientos y documentos visibles; pagos y comprobantes conservados; receta pendiente |
| Perfil y familia/apoderados | Perfil de contacto y un representante legal único en `patients` | Perfil existente conservado; familia y múltiples apoderados pendientes |
| Registro, OTP y estados de verificación | Alta administrativa, control de duplicado por documento, activación con código y auditoría | No se expone registro público hasta definir identidad, proveedor y base legal |

## Alcance pendiente

- No hay modelo de recetas médicas ni resultados de laboratorio estructurados. Los archivos compartidos cubren
  documentos, imágenes y radiografías; para semáforo, rangos y visor clínico se requiere acordar el modelo y la
  política de publicación por el odontólogo.
- El modelo actual admite un representante legal de menores, pero no grupo familiar, múltiples apoderados ni
  delegaciones para adolescente/adulto mayor. Esto exige una migración y definición legal de vínculos, permisos,
  revocación y trazabilidad antes de exponer datos de terceros.
- El registro actual es administrativo (`/api/patients`) y la activación de portal verifica documento y fecha de
  nacimiento y usa código de un canal configurado. No se habilitó un registro público de cinco pasos: faltan una
  decisión legal sobre identidad/verificación y un proveedor OTP real. No se debe presentar el simulador de
  WhatsApp, RENIEC ni Mercado Pago como servicios reales.

## Seguridad y operaciones

- Las rutas `/api/portal/**` siguen protegidas por JWT con rol `PACIENTE`; la identidad se obtiene del token,
  nunca de un identificador enviado por la interfaz.
- No se alteraron migraciones publicadas, producción, pagos, OTP, RENIEC ni integraciones externas.
- Antes de una segunda fase, definir con clínica/asesoría legal los consentimientos separados, políticas por edad,
  condiciones de representantes y retención de auditoría. Luego crear migraciones nuevas a partir de `V16`.

## Verificación local

No fue posible ejecutar `mvn test` ni `ng build` en este entorno porque no están instalados Maven ni Node/npm.
El ZIP se verificó listando su contenido; al abrirlo en un equipo de desarrollo, ejecutar primero `mvn test` en
`backend/` y `npm ci && npx ng build --configuration production` en `frontend/`, sin apuntar a producción.

