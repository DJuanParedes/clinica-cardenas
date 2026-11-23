# Contrato del API · Clínica Dental Cárdenas

Generado del código fuente de los controladores (`@GetMapping`, `@PostMapping`… y `@PreAuthorize`), así que coincide con lo que el servicio expone.

- **Ruta canónica:** `/api/v1/...` (la usa la app móvil). Las mismas rutas sin versión (`/api/...`) se mantienen como sinónimo para la aplicación web.
- **Autenticación:** `POST /api/v1/auth/login` (personal) y `POST /api/v1/portal/auth/login` (paciente) devuelven un JWT. Las demás rutas exigen la cabecera `Authorization: Bearer <token>`.
- **Rutas del personal** (`/api/**`) exigen el rol PERSONAL; **rutas del portal** (`/api/portal/**`) exigen el rol PACIENTE (ver `SecurityConfig`). Encima, cada endpoint pide su privilegio con `@PreAuthorize` (403 si falta).
- **Formato único de error** (`GlobalExceptionHandler` + `ApiException`): `{"code": "...", "message": "...", "fields": {"campo": "detalle"}}`.
- **Códigos:** 200 OK · 201 creado · 400 datos inválidos (con `fields`) · 401 sin token o token vencido · 403 sin el privilegio · 404 no existe · 409 conflicto de estado (p. ej. horario ocupado, caja cerrada).
- **Idempotencia:** `POST /api/v1/portal/appointments` acepta la cabecera `Idempotency-Key`; el mismo valor nunca crea dos citas (lo usa la sincronización offline de la app).
- **Paginación:** los listados largos (pacientes, comprobantes, notificaciones, auditoría) aceptan `page` y `size` y responden con el envoltorio de Spring (`content`, `totalElements`, `totalPages`, `number`, `size`).

Total: **347 rutas** en **45 controladores**.

## AgendaController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/agenda/day` | Personal · AGENDA_VER | 200, 401, 403 | Consulta agenda · day |
| GET | `/api/v1/agenda/slots` | Personal · AGENDA_VER | 200, 401, 403 | Consulta agenda · horarios libres |
| GET | `/api/v1/appointments` | Personal · AGENDA_VER | 200, 401, 403 | Consulta citas |
| POST | `/api/v1/appointments` | Personal · CITAS_GESTIONAR | 201, 400, 401, 403, 409 | Registra / ejecuta citas |
| GET | `/api/v1/appointments/{id}` | Personal · AGENDA_VER | 200, 401, 403, 404 | Consulta citas |
| PUT | `/api/v1/appointments/{id}` | Personal · CITAS_GESTIONAR | 200, 400, 401, 403, 404 | Actualiza citas |
| GET | `/api/v1/appointments/{id}/audit` | Personal · AGENDA_VER | 200, 401, 403, 404 | Consulta citas · auditoría |
| PATCH | `/api/v1/appointments/{id}/status` | Personal · CITAS_ESTADO | 200, 400, 401, 403, 404 | Modifica citas · status |
| GET | `/api/v1/dentists` | Personal (autenticado) | 200, 401 | Consulta odontólogos |

## AnamnesisController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/anamnesis/templates` | Personal · HISTORIA_CLINICA | 200, 401, 403 | Consulta anamnesis · plantillas |
| POST | `/api/v1/anamnesis/templates` | Personal · HISTORIA_CLINICA | 200, 400, 401, 403 | Registra / ejecuta anamnesis · plantillas |
| PUT | `/api/v1/anamnesis/templates/{id}` | Personal · HISTORIA_CLINICA | 200, 400, 401, 403, 404 | Actualiza anamnesis · plantillas |
| GET | `/api/v1/patients/{id}/anamnesis` | Personal · HISTORIA_CLINICA | 200, 401, 403, 404 | Consulta pacientes · anamnesis |
| POST | `/api/v1/patients/{id}/anamnesis` | Personal · HISTORIA_CLINICA | 200, 400, 401, 403, 404 | Registra / ejecuta pacientes · anamnesis |
| PUT | `/api/v1/patients/{id}/anamnesis/{recordId}` | Personal · HISTORIA_CLINICA | 200, 400, 401, 403, 404 | Actualiza pacientes · anamnesis |
| GET | `/api/v1/patients/{id}/vitals` | Personal · HISTORIA_CLINICA | 200, 401, 403, 404 | Consulta pacientes · signos vitales |
| POST | `/api/v1/patients/{id}/vitals` | Personal · HISTORIA_CLINICA | 200, 400, 401, 403, 404 | Registra / ejecuta pacientes · signos vitales |
| DELETE | `/api/v1/patients/{id}/vitals/{vitalId}` | Personal · ELIMINAR_REGISTROS | 200, 401, 403, 404 | Elimina pacientes · signos vitales |

## AuthController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| POST | `/api/v1/auth/2fa/disable` | Personal (autenticado) | 200, 400, 401 | Registra / ejecuta 2fa · disable |
| POST | `/api/v1/auth/2fa/email/enable` | Personal (autenticado) | 200, 400, 401 | Registra / ejecuta email · enable |
| POST | `/api/v1/auth/2fa/email/start` | Personal (autenticado) | 200, 401 | Registra / ejecuta email · start |
| POST | `/api/v1/auth/2fa/enable` | Personal (autenticado) | 200, 400, 401 | Registra / ejecuta 2fa · enable |
| POST | `/api/v1/auth/2fa/setup` | Personal (autenticado) | 200, 401 | Registra / ejecuta 2fa · setup |
| POST | `/api/v1/auth/login` | Público (sin token) | 200, 400, 401 | Registra / ejecuta autenticación · inicio de sesión |
| POST | `/api/v1/auth/logout` | Personal (autenticado) | 200, 401 | Registra / ejecuta autenticación · logout |
| GET | `/api/v1/auth/me` | Personal (autenticado) | 200, 401 | Consulta autenticación · perfil propio |
| POST | `/api/v1/auth/recover` | Público (sin token) | 200, 400 | Registra / ejecuta autenticación · recover |
| POST | `/api/v1/auth/reset` | Público (sin token) | 200, 400 | Registra / ejecuta autenticación · reset |

## BillingController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/billing/company` | Personal · COMPROBANTES | 200, 401, 403 | Consulta facturación · company |
| GET | `/api/v1/billing/invoices` | Personal · COMPROBANTES | 200, 401, 403 | Consulta facturación · comprobantes |
| POST | `/api/v1/billing/invoices` | Personal · COMPROBANTES | 201, 400, 401, 403 | Registra / ejecuta facturación · comprobantes |
| GET | `/api/v1/billing/invoices/{id}` | Personal · COMPROBANTES | 200, 401, 403, 404 | Consulta facturación · comprobantes |
| GET | `/api/v1/billing/invoices/{id}/cdr` | Personal · COMPROBANTES | 200, 401, 403, 404 | Consulta comprobantes · cdr |
| POST | `/api/v1/billing/invoices/{id}/email` | Personal · COMPROBANTES | 200, 400, 401, 403, 404 | Registra / ejecuta comprobantes · email |
| GET | `/api/v1/billing/invoices/{id}/pdf` | Personal · COMPROBANTES | 200, 401, 403, 404 | Consulta comprobantes · pdf |
| POST | `/api/v1/billing/invoices/{id}/send` | Personal · COMPROBANTES | 200, 401, 403, 404 | Registra / ejecuta comprobantes · send |
| POST | `/api/v1/billing/invoices/{id}/status` | Personal · COMPROBANTES | 200, 401, 403, 404 | Registra / ejecuta comprobantes · status |
| GET | `/api/v1/billing/invoices/{id}/xml` | Personal · COMPROBANTES | 200, 401, 403, 404 | Consulta comprobantes · xml |
| GET | `/api/v1/billing/patients/{patientId}/billable-items` | Personal · COMPROBANTES | 200, 401, 403, 404 | Consulta pacientes · billable items |
| GET | `/api/v1/billing/patients/{patientId}/invoices` | Personal · COMPROBANTES | 200, 401, 403, 404 | Consulta pacientes · comprobantes |
| GET | `/api/v1/billing/ruc/{ruc}` | Personal · COMPROBANTES | 200, 401, 403, 404 | Consulta facturación · ruc |
| GET | `/api/v1/billing/series` | Personal · COMPROBANTES | 200, 401, 403 | Consulta facturación · series |

## CareController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/agenda/reschedule-requests` | Personal · AGENDA_VER | 200, 401, 403 | Consulta agenda · reschedule requests |
| POST | `/api/v1/internal/whatsapp/reply` | Público (sin token) | 200 | Mensajes del paciente por WhatsApp (los reenvía el microservicio de notificaciones) |
| GET | `/api/v1/plans/{planId}/installments` | Personal · PLAN_TRATAMIENTO o CAJA_COBRAR o HISTORIA_CLINICA | 200, 401, 403, 404 | Consulta planes de tratamiento · installments |
| POST | `/api/v1/plans/{planId}/installments` | Personal · PLAN_TRATAMIENTO o CAJA_COBRAR | 200, 400, 401, 403, 404 | Registra / ejecuta planes de tratamiento · installments |
| GET | `/api/v1/recalls` | Personal · CITAS_GESTIONAR o REPORTES | 200, 401, 403 | Consulta recalls |
| POST | `/api/v1/recalls/{patientId}/send` | Personal · CITAS_GESTIONAR | 200, 400, 401, 403, 404 | Registra / ejecuta recalls · send |
| GET | `/api/v1/reports/satisfaction` | Personal · REPORTES o AGENDA_VER | 200, 401, 403 | Consulta reportes · satisfaction |
| GET | `/api/v1/waitlist` | Personal · AGENDA_VER | 200, 401, 403 | Consulta lista de espera |
| POST | `/api/v1/waitlist` | Personal · CITAS_GESTIONAR | 201, 400, 401, 403 | Registra / ejecuta lista de espera |
| POST | `/api/v1/waitlist/{id}/close` | Personal · CITAS_GESTIONAR | 200, 401, 403, 404 | Registra / ejecuta lista de espera · close |

## CashController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/cash/closing` | Personal · CAJA_CIERRE o CAJA_REPORTE | 200, 401, 403 | Consulta caja · cierre de caja |
| POST | `/api/v1/cash/closing` | Personal · CAJA_CIERRE | 200, 400, 401, 403 | Registra / ejecuta caja · cierre de caja |
| GET | `/api/v1/cash/closing/{date}/pdf` | Personal · CAJA_CIERRE o CAJA_REPORTE | 200, 401, 403, 404 | Consulta cierre de caja · pdf |
| POST | `/api/v1/cash/closing/{date}/reopen` | Personal · CAJA_REABRIR | 200, 401, 403, 404 | Registra / ejecuta cierre de caja · reopen |
| GET | `/api/v1/cash/closings` | Personal · CAJA_CIERRE o CAJA_REPORTE | 200, 401, 403 | Consulta caja · closings |
| GET | `/api/v1/cash/day` | Personal · CAJA_COBRAR o CAJA_EGRESOS o CAJA_REPORTE | 200, 401, 403 | Historial: la caja de cualquier día anterior (hasta un año atrás), no solo la de hoy. |
| POST | `/api/v1/cash/expenses` | Personal · CAJA_EGRESOS | 201, 400, 401, 403 | Registra / ejecuta caja · egresos |
| POST | `/api/v1/cash/expenses/{id}/void` | Personal · CAJA_EGRESOS | 200, 400, 401, 403, 404 | Registra / ejecuta egresos · void |
| GET | `/api/v1/cash/meta` | Personal (autenticado) | 200, 401 | Consulta caja · meta |
| GET | `/api/v1/cash/patients/{patientId}/account` | Personal · CAJA_COBRAR | 200, 401, 403, 404 | Consulta pacientes · estado de cuenta |
| POST | `/api/v1/cash/payments` | Personal · CAJA_COBRAR | 201, 400, 401, 403 | Registra / ejecuta caja · pagos |
| POST | `/api/v1/cash/payments/{id}/void` | Personal · CAJA_COBRAR | 200, 400, 401, 403, 404 | Registra / ejecuta pagos · void |
| GET | `/api/v1/cash/report` | Personal · CAJA_REPORTE | 200, 401, 403 | Consulta caja · report |
| GET | `/api/v1/cash/today` | Personal · CAJA_COBRAR o CAJA_EGRESOS o CAJA_REPORTE | 200, 401, 403 | Caja del día: lo que cobró y pagó el turno. |

## CatalogController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/catalog/procedures` | Personal (autenticado) | 200, 401 | Consulta catálogo · procedimientos |
| POST | `/api/v1/catalog/procedures` | Personal · PROMOCIONES | 200, 400, 401, 403 | Registra / ejecuta catálogo · procedimientos |
| PUT | `/api/v1/catalog/procedures/{id}` | Personal · PROMOCIONES | 200, 400, 401, 403, 404 | Actualiza catálogo · procedimientos |
| GET | `/api/v1/catalog/promotions` | Personal (autenticado) | 200, 401 | Consulta catálogo · promociones |
| POST | `/api/v1/catalog/promotions` | Personal · PROMOCIONES | 200, 400, 401, 403 | Registra / ejecuta catálogo · promociones |
| PUT | `/api/v1/catalog/promotions/{id}` | Personal · PROMOCIONES | 200, 400, 401, 403, 404 | Actualiza catálogo · promociones |

## ClinicalController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| POST | `/api/v1/patients/{id}/diagnoses` | Personal (autenticado) | 201, 400, 401, 404 | Registra / ejecuta pacientes · diagnósticos |
| GET | `/api/v1/patients/{id}/history` | Personal (autenticado) | 200, 401, 404 | Consulta pacientes · historia clínica |
| PUT | `/api/v1/patients/{id}/history` | Personal (autenticado) | 200, 400, 401, 404 | Actualiza pacientes · historia clínica |
| GET | `/api/v1/patients/{id}/odontogram` | Personal (autenticado) | 200, 401, 404 | Consulta pacientes · odontograma |
| PUT | `/api/v1/patients/{id}/odontogram` | Personal (autenticado) | 200, 400, 401, 404 | Actualiza pacientes · odontograma |
| GET | `/api/v1/patients/{id}/plans` | Personal · PLAN_TRATAMIENTO o HISTORIA_CLINICA o CAJA_COBRAR | 200, 401, 403, 404 | Consulta pacientes · planes de tratamiento |
| POST | `/api/v1/patients/{id}/plans` | Personal (autenticado) | 201, 400, 401, 404 | Registra / ejecuta pacientes · planes de tratamiento |
| PATCH | `/api/v1/plan-items/{itemId}` | Personal (autenticado) | 200, 400, 401, 404 | Modifica tratamientos del plan |
| GET | `/api/v1/plan-items/{itemId}/supplies` | Personal (autenticado) | 200, 401, 404 | Consulta tratamientos del plan · insumos |
| POST | `/api/v1/plans/{planId}/cancel` | Personal (autenticado) | 200, 401, 404 | Registra / ejecuta planes de tratamiento · cancelación |
| POST | `/api/v1/plans/{planId}/evolutions` | Personal (autenticado) | 200, 400, 401, 404 | Registra / ejecuta planes de tratamiento · evoluciones |
| POST | `/api/v1/plans/{planId}/items` | Personal (autenticado) | 200, 400, 401, 404 | Registra / ejecuta planes de tratamiento · ítems |
| GET | `/api/v1/procedures` | Personal (autenticado) | 200, 401 | Consulta procedimientos |

## CommissionController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/commissions/calculate` | Personal · COMISIONES | 200, 401, 403 | Consulta comisiones · calculate |
| GET | `/api/v1/commissions/rules` | Personal · COMISIONES | 200, 401, 403 | Consulta comisiones · reglas |
| POST | `/api/v1/commissions/rules` | Personal · COMISIONES | 201, 400, 401, 403 | Registra / ejecuta comisiones · reglas |
| PUT | `/api/v1/commissions/rules/{id}` | Personal · COMISIONES | 200, 400, 401, 403, 404 | Actualiza comisiones · reglas |
| GET | `/api/v1/commissions/settlements` | Personal · COMISIONES | 200, 401, 403 | Consulta comisiones · liquidaciones |
| POST | `/api/v1/commissions/settlements` | Personal · COMISIONES | 201, 400, 401, 403 | Registra / ejecuta comisiones · liquidaciones |
| GET | `/api/v1/commissions/settlements/{id}` | Personal · COMISIONES | 200, 401, 403, 404 | Consulta comisiones · liquidaciones |
| POST | `/api/v1/commissions/settlements/{id}/annul` | Personal · COMISIONES | 200, 400, 401, 403, 404 | Registra / ejecuta liquidaciones · annul |
| POST | `/api/v1/commissions/settlements/{id}/payouts` | Personal · COMISIONES | 200, 400, 401, 403, 404 | Registra / ejecuta liquidaciones · payouts |
| GET | `/api/v1/commissions/summary` | Personal · COMISIONES | 200, 401, 403 | Consulta comisiones · summary |

## ComplaintController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/complaints` | Personal · RECLAMOS | 200, 401, 403 | Consulta reclamos |
| GET | `/api/v1/complaints/export` | Personal · RECLAMOS | 200, 401, 403 | Consulta reclamos · export |
| PUT | `/api/v1/complaints/settings` | Personal · RECLAMOS | 200, 400, 401, 403 | Actualiza reclamos · ajustes |
| GET | `/api/v1/complaints/summary` | Personal · RECLAMOS | 200, 401, 403 | Consulta reclamos · summary |
| GET | `/api/v1/complaints/{id}` | Personal · RECLAMOS | 200, 401, 403, 404 | Consulta reclamos |
| PUT | `/api/v1/complaints/{id}` | Personal · RECLAMOS | 200, 400, 401, 403, 404 | Actualiza reclamos |
| GET | `/api/v1/complaints/{id}/pdf` | Personal · RECLAMOS | 200, 401, 403, 404 | Consulta reclamos · pdf |
| POST | `/api/v1/complaints/{id}/respond` | Personal · RECLAMOS | 200, 400, 401, 403, 404 | Registra / ejecuta reclamos · respond |
| POST | `/api/v1/public/complaints` | Público (sin token) | 201, 400 | Registra / ejecuta público · reclamos |
| GET | `/api/v1/public/complaints/info` | Público (sin token) | 200 | Consulta reclamos · info |
| GET | `/api/v1/public/complaints/{code}` | Público (sin token) | 200, 404 | Consulta público · reclamos |
| GET | `/api/v1/public/complaints/{code}/pdf` | Público (sin token) | 200, 404 | Consulta reclamos · pdf |

## ConfirmationFollowupController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/agenda/unconfirmed` | Personal · CITAS_GESTIONAR o CITAS_ESTADO | 200, 401, 403 | Consulta agenda · unconfirmed |
| POST | `/api/v1/appointments/{id}/call` | Personal · CITAS_GESTIONAR o CITAS_ESTADO | 200, 400, 401, 403, 404 | Registra / ejecuta citas · call |

## ConversationController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/assistant/faq` | Personal · MENSAJES | 200, 401, 403 | Consulta asistente · preguntas frecuentes |
| POST | `/api/v1/assistant/faq` | Personal · MENSAJES | 200, 400, 401, 403 | Registra / ejecuta asistente · preguntas frecuentes |
| DELETE | `/api/v1/assistant/faq/{id}` | Personal · ELIMINAR_REGISTROS | 200, 401, 403, 404 | Elimina asistente · preguntas frecuentes |
| PUT | `/api/v1/assistant/faq/{id}` | Personal · MENSAJES | 200, 400, 401, 403, 404 | Actualiza asistente · preguntas frecuentes |
| GET | `/api/v1/assistant/settings` | Personal · MENSAJES | 200, 401, 403 | Consulta asistente · ajustes |
| PUT | `/api/v1/assistant/settings` | Personal · MENSAJES | 200, 400, 401, 403 | Actualiza asistente · ajustes |
| POST | `/api/v1/assistant/test` | Personal · MENSAJES | 200, 400, 401, 403 | Registra / ejecuta asistente · test |
| GET | `/api/v1/conversations` | Personal · MENSAJES | 200, 401, 403 | Consulta conversations |
| GET | `/api/v1/conversations/summary` | Personal · MENSAJES | 200, 401, 403 | Consulta conversations · summary |
| GET | `/api/v1/conversations/{phone}` | Personal · MENSAJES | 200, 401, 403, 404 | Consulta conversations |
| PATCH | `/api/v1/conversations/{phone}` | Personal · MENSAJES | 200, 400, 401, 403, 404 | Modifica conversations |
| POST | `/api/v1/conversations/{phone}/messages` | Personal · MENSAJES | 201, 400, 401, 403, 404 | Registra / ejecuta conversations · messages |

## DashboardController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/dashboard` | Personal (autenticado) | 200, 401 | Consulta tablero |

## DeletionController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/admin/records/trash` | Personal · ELIMINAR_REGISTROS | 200, 401, 403 | Consulta registros · papelera |
| GET | `/api/v1/admin/records/trash/{id}` | Personal · ELIMINAR_REGISTROS | 200, 401, 403, 404 | Consulta registros · papelera |
| GET | `/api/v1/admin/records/types` | Personal · ELIMINAR_REGISTROS | 200, 401, 403 | Consulta registros · types |
| POST | `/api/v1/admin/records/{entity}/{id}/delete` | Personal · ELIMINAR_REGISTROS | 200, 400, 401, 403, 404 | Registra / ejecuta registros · eliminación |
| GET | `/api/v1/admin/records/{entity}/{id}/preview` | Personal · ELIMINAR_REGISTROS | 200, 401, 403, 404 | Consulta registros · revisión previa |

## DentistPhotoController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| DELETE | `/api/v1/dentists/{id}/photo` | Personal · USUARIOS | 200, 401, 403, 404 | Elimina odontólogos · photo |
| POST | `/api/v1/dentists/{id}/photo` | Personal · USUARIOS | 200, 401, 403, 404 | Registra / ejecuta odontólogos · photo |

## FileController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| DELETE | `/api/v1/files/{id}` | Personal · ELIMINAR_REGISTROS | 200, 401, 403, 404 | Elimina archivos |
| PATCH | `/api/v1/files/{id}` | Personal (autenticado) | 200, 400, 401, 404 | Modifica archivos |
| GET | `/api/v1/files/{id}/content` | Personal · PACIENTES_VER | 200, 401, 403, 404 | Consulta archivos · content |
| POST | `/api/v1/patients/{id}/documents` | Personal (autenticado) | 201, 400, 401, 404 | Registra / ejecuta pacientes · documents |
| GET | `/api/v1/patients/{id}/files` | Personal · PACIENTES_VER | 200, 401, 403, 404 | Consulta pacientes · archivos |
| POST | `/api/v1/patients/{id}/files` | Personal (autenticado) | 201, 401, 404 | Registra / ejecuta pacientes · archivos |

## InformedConsentController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/informed-consents` | Personal (autenticado) | 200, 401 | Consulta informed consents |
| GET | `/api/v1/informed-consents/templates` | Personal · HISTORIA_CLINICA o PACIENTES_EDITAR o CONSENTIMIENTOS | 200, 401, 403 | Consulta informed consents · plantillas |
| POST | `/api/v1/informed-consents/templates` | Personal · CONSENTIMIENTOS | 200, 400, 401, 403 | Registra / ejecuta informed consents · plantillas |
| PUT | `/api/v1/informed-consents/templates/{id}` | Personal · CONSENTIMIENTOS | 200, 400, 401, 403, 404 | Actualiza informed consents · plantillas |
| POST | `/api/v1/informed-consents/{id}/cancel` | Personal (autenticado) | 200, 400, 401, 404 | Registra / ejecuta informed consents · cancelación |
| GET | `/api/v1/patients/{id}/informed-consents` | Personal (autenticado) | 200, 401, 404 | Consulta pacientes · informed consents |
| POST | `/api/v1/patients/{id}/informed-consents` | Personal (autenticado) | 200, 400, 401, 404 | Registra / ejecuta pacientes · informed consents |
| GET | `/api/v1/public/consent/{token}` | Público (sin token) | 200, 404 | Consulta público · consent |
| POST | `/api/v1/public/consent/{token}` | Público (sin token) | 200, 400, 404 | Registra / ejecuta público · consent |

## InventoryController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/inventory` | Personal · INVENTARIO | 200, 401, 403 | Consulta inventario |
| POST | `/api/v1/inventory` | Personal · INVENTARIO | 201, 400, 401, 403 | Registra / ejecuta inventario |
| GET | `/api/v1/inventory/supplies` | Personal · INVENTARIO | 200, 401, 403 | Consulta inventario · insumos |
| PUT | `/api/v1/inventory/supplies/{procedureId}` | Personal · INVENTARIO | 200, 400, 401, 403, 404 | Actualiza inventario · insumos |
| PUT | `/api/v1/inventory/{id}` | Personal · INVENTARIO | 200, 400, 401, 403, 404 | Actualiza inventario |
| DELETE | `/api/v1/inventory/{id}/image` | Personal · INVENTARIO | 200, 401, 403, 404 | Elimina inventario · imagen |
| POST | `/api/v1/inventory/{id}/image` | Personal · INVENTARIO | 200, 401, 403, 404 | Registra / ejecuta inventario · imagen |
| GET | `/api/v1/inventory/{id}/moves` | Personal · INVENTARIO | 200, 401, 403, 404 | Consulta inventario · moves |
| POST | `/api/v1/inventory/{id}/moves` | Personal · INVENTARIO | 200, 400, 401, 403, 404 | Registra / ejecuta inventario · moves |

## KardexController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/inventory/consumption` | Personal · INVENTARIO_COSTOS | 200, 401, 403 | Consulta inventario · consumption |
| GET | `/api/v1/inventory/valuation` | Personal · INVENTARIO_COSTOS | 200, 401, 403 | Consulta inventario · valuation |
| GET | `/api/v1/inventory/{id}/kardex` | Personal · INVENTARIO_COSTOS | 200, 401, 403, 404 | Consulta inventario · kardex |

## LabController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/lab-options` | Personal (autenticado) | 200, 401 | Consulta lab options |
| GET | `/api/v1/lab-orders` | Personal (autenticado) | 200, 401 | Consulta lab orders |
| POST | `/api/v1/lab-orders` | Personal · LABORATORIO | 200, 400, 401, 403 | Registra / ejecuta lab orders |
| GET | `/api/v1/lab-orders/{id}` | Personal (autenticado) | 200, 401, 404 | Consulta lab orders |
| PUT | `/api/v1/lab-orders/{id}` | Personal · LABORATORIO | 200, 400, 401, 403, 404 | Actualiza lab orders |
| POST | `/api/v1/lab-orders/{id}/payments` | Personal · CAJA_EGRESOS | 200, 400, 401, 403, 404 | Registra / ejecuta lab orders · pagos |
| POST | `/api/v1/lab-orders/{id}/status` | Personal · LABORATORIO | 200, 400, 401, 403, 404 | Registra / ejecuta lab orders · status |
| GET | `/api/v1/laboratories` | Personal (autenticado) | 200, 401 | Consulta laboratorios |
| POST | `/api/v1/laboratories` | Personal · LABORATORIO | 200, 400, 401, 403 | Registra / ejecuta laboratorios |
| PUT | `/api/v1/laboratories/{id}` | Personal · LABORATORIO | 200, 400, 401, 403, 404 | Actualiza laboratorios |

## LeadController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/leads` | Personal · LEADS | 200, 401, 403 | Consulta oportunidades CRM |
| POST | `/api/v1/leads` | Personal · LEADS | 200, 400, 401, 403 | Registra / ejecuta oportunidades CRM |
| GET | `/api/v1/leads/metrics` | Personal · LEADS | 200, 401, 403 | Consulta oportunidades CRM · metrics |
| PUT | `/api/v1/leads/settings/whatsapp-auto` | Personal · LEADS o MARKETING | 200, 400, 401, 403 | Actualiza ajustes · whatsapp auto |
| GET | `/api/v1/leads/{id}` | Personal · LEADS | 200, 401, 403, 404 | Consulta oportunidades CRM |
| PUT | `/api/v1/leads/{id}` | Personal · LEADS | 200, 400, 401, 403, 404 | Actualiza oportunidades CRM |
| POST | `/api/v1/leads/{id}/notes` | Personal · LEADS | 200, 400, 401, 403, 404 | Registra / ejecuta oportunidades CRM · notas |
| POST | `/api/v1/leads/{id}/patient` | Personal · LEADS | 200, 400, 401, 403, 404 | Registra / ejecuta oportunidades CRM · patient |
| POST | `/api/v1/leads/{id}/stage` | Personal · LEADS | 200, 400, 401, 403, 404 | Registra / ejecuta oportunidades CRM · stage |

## LegalController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/lookup/dni-config` | Personal (autenticado) | 200, 401 | Consulta lookup · dni config |
| GET | `/api/v1/lookup/dni/{number}` | Personal · PACIENTES_EDITAR | 200, 401, 403, 404 | Autocompletar nombres con el DNI (RENIEC, mediante un proveedor de datos abiertos). |
| GET | `/api/v1/patients/{id}/consents` | Personal · PACIENTES_VER | 200, 401, 403, 404 | Consulta pacientes · consentimientos |
| POST | `/api/v1/patients/{id}/consents/signed` | Personal · PACIENTES_EDITAR | 200, 401, 403, 404 | Registra / ejecuta consentimientos · signed |
| GET | `/api/v1/public/legal/{code}` | Público (sin token) | 200, 404 | Consulta público · legal |

## MarketingController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/marketing/birthdays` | Personal · MARKETING | 200, 401, 403 | Consulta marketing · birthdays |
| POST | `/api/v1/marketing/birthdays/run` | Personal · MARKETING | 200, 401, 403 | Registra / ejecuta birthdays · run |
| PUT | `/api/v1/marketing/birthdays/settings` | Personal · MARKETING | 200, 400, 401, 403 | Actualiza birthdays · ajustes |
| GET | `/api/v1/marketing/campaigns` | Personal · MARKETING | 200, 401, 403 | Consulta marketing · campañas |
| POST | `/api/v1/marketing/campaigns` | Personal · MARKETING | 200, 400, 401, 403 | Registra / ejecuta marketing · campañas |
| POST | `/api/v1/marketing/campaigns/preview` | Personal · MARKETING | 200, 400, 401, 403 | Registra / ejecuta campañas · revisión previa |
| GET | `/api/v1/marketing/campaigns/{id}` | Personal · MARKETING | 200, 401, 403, 404 | Consulta marketing · campañas |
| PUT | `/api/v1/marketing/campaigns/{id}` | Personal · MARKETING | 200, 400, 401, 403, 404 | Actualiza marketing · campañas |
| POST | `/api/v1/marketing/campaigns/{id}/cancel` | Personal · MARKETING | 200, 401, 403, 404 | Registra / ejecuta campañas · cancelación |
| POST | `/api/v1/marketing/campaigns/{id}/schedule` | Personal · MARKETING | 200, 400, 401, 403, 404 | Registra / ejecuta campañas · schedule |
| GET | `/api/v1/marketing/segments` | Personal · MARKETING | 200, 401, 403 | Consulta marketing · segmentos |
| POST | `/api/v1/marketing/segments` | Personal · MARKETING | 200, 400, 401, 403 | Registra / ejecuta marketing · segmentos |
| POST | `/api/v1/marketing/segments/preview` | Personal · MARKETING o TAREAS | 200, 400, 401, 403 | Registra / ejecuta segmentos · revisión previa |
| POST | `/api/v1/marketing/segments/tasks` | Personal · MARKETING o TAREAS | 200, 400, 401, 403 | Registra / ejecuta segmentos · tareas |
| DELETE | `/api/v1/marketing/segments/{id}` | Personal · ELIMINAR_REGISTROS | 200, 401, 403, 404 | Elimina marketing · segmentos |
| PUT | `/api/v1/marketing/segments/{id}` | Personal · MARKETING | 200, 400, 401, 403, 404 | Actualiza marketing · segmentos |
| PUT | `/api/v1/patients/{id}/marketing-consent` | Personal · PACIENTES_EDITAR | 200, 400, 401, 403, 404 | Actualiza pacientes · marketing consent |

## MessagingController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/messaging/automations` | Personal (autenticado) | 200, 401 | Consulta messaging · automations |
| PUT | `/api/v1/messaging/automations/{code}` | Personal (autenticado) | 200, 400, 401, 404 | Actualiza messaging · automations |
| GET | `/api/v1/messaging/history` | Personal · AUTOMATIZACIONES o NOTIFICACIONES | 200, 401, 403 | Consulta messaging · historia clínica |
| GET | `/api/v1/messaging/public-links` | Personal (autenticado) | 200, 401 | Consulta messaging · public links |
| PUT | `/api/v1/messaging/public-links` | Personal (autenticado) | 200, 400, 401 | Actualiza messaging · public links |
| GET | `/api/v1/messaging/templates` | Personal (autenticado) | 200, 401 | Consulta messaging · plantillas |
| PUT | `/api/v1/messaging/templates/{code}` | Personal (autenticado) | 200, 400, 401, 404 | Actualiza messaging · plantillas |
| POST | `/api/v1/messaging/templates/{code}/preview` | Personal (autenticado) | 200, 400, 401, 404 | Registra / ejecuta plantillas · revisión previa |
| POST | `/api/v1/messaging/templates/{code}/restore` | Personal (autenticado) | 200, 401, 404 | Registra / ejecuta plantillas · restore |

## MyEarningsController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/me/earnings` | Personal (autenticado) | 200, 401 | Consulta perfil propio · earnings |

## NotificationController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| POST | `/api/v1/appointments/{id}/remind` | Personal · CITAS_GESTIONAR | 200, 401, 403, 404 | Registra / ejecuta citas · remind |
| POST | `/api/v1/internal/notifications/callback` | Público (sin token) | 200 | Webhook del microservicio: resultado de un recordatorio programado. |
| GET | `/api/v1/notifications` | Personal · NOTIFICACIONES | 200, 401, 403 | Consulta notificaciones |
| POST | `/api/v1/notifications/{id}/retry` | Personal · NOTIFICACIONES | 200, 401, 403, 404 | Registra / ejecuta notificaciones · retry |
| PATCH | `/api/v1/patients/{id}/notification-channel` | Personal · PACIENTES_EDITAR | 200, 400, 401, 403, 404 | Modifica pacientes · notification channel |
| GET | `/api/v1/patients/{id}/notifications` | Personal · PACIENTES_VER | 200, 401, 403, 404 | Consulta pacientes · notificaciones |

## OnlineChargeController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/cash/online` | Personal · CAJA_COBRAR | 200, 401, 403 | Consulta caja · online |
| POST | `/api/v1/cash/online` | Personal · CAJA_COBRAR | 201, 400, 401, 403 | Registra / ejecuta caja · online |
| GET | `/api/v1/cash/online/config` | Personal (autenticado) | 200, 401 | Consulta online · config |
| GET | `/api/v1/cash/online/{id}` | Personal · CAJA_COBRAR | 200, 401, 403, 404 | Consulta caja · online |
| POST | `/api/v1/cash/online/{id}/cancel` | Personal · CAJA_COBRAR | 200, 401, 403, 404 | Registra / ejecuta online · cancelación |
| POST | `/api/v1/cash/online/{id}/whatsapp` | Personal · CAJA_COBRAR | 200, 401, 403, 404 | Registra / ejecuta online · whatsapp |
| POST | `/api/v1/portal/payments/online` | Paciente (portal) (autenticado) | 201, 400, 401 | Registra / ejecuta pagos · online |
| GET | `/api/v1/portal/payments/online/config` | Paciente (portal) (autenticado) | 200, 401 | Consulta online · config |
| GET | `/api/v1/portal/payments/online/{id}` | Paciente (portal) (autenticado) | 200, 401, 404 | Consulta pagos · online |
| POST | `/api/v1/public/mercadopago/webhook` | Público (sin token) | 200 | Registra / ejecuta mercadopago · webhook |

## OrthoController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/ortho/cases` | Personal · HISTORIA_CLINICA | 200, 401, 403 | Consulta ortodoncia · casos |
| PUT | `/api/v1/ortho/cases/{id}` | Personal · HISTORIA_CLINICA | 200, 400, 401, 403, 404 | Actualiza ortodoncia · casos |
| POST | `/api/v1/ortho/cases/{id}/controls` | Personal · HISTORIA_CLINICA | 200, 400, 401, 403, 404 | Registra / ejecuta casos · controls |
| POST | `/api/v1/ortho/cases/{id}/photos` | Personal · HISTORIA_CLINICA | 200, 401, 403, 404 | Registra / ejecuta casos · fotos |
| POST | `/api/v1/ortho/cases/{id}/photos/link` | Personal · HISTORIA_CLINICA | 200, 400, 401, 403, 404 | Registra / ejecuta fotos · link |
| DELETE | `/api/v1/ortho/cases/{id}/photos/{photoId}` | Personal · HISTORIA_CLINICA | 200, 401, 403, 404 | Elimina casos · fotos |
| PUT | `/api/v1/ortho/cases/{id}/photos/{photoId}` | Personal · HISTORIA_CLINICA | 200, 400, 401, 403, 404 | Actualiza casos · fotos |
| GET | `/api/v1/ortho/options` | Personal · HISTORIA_CLINICA | 200, 401, 403 | Consulta ortodoncia · opciones |
| GET | `/api/v1/patients/{patientId}/ortho` | Personal · HISTORIA_CLINICA | 200, 401, 403, 404 | Consulta pacientes · ortodoncia |
| POST | `/api/v1/patients/{patientId}/ortho` | Personal · HISTORIA_CLINICA | 200, 400, 401, 403, 404 | Registra / ejecuta pacientes · ortodoncia |

## PatientController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/patients` | Personal · PACIENTES_VER | 200, 401, 403 | Consulta pacientes |
| POST | `/api/v1/patients` | Personal · PACIENTES_EDITAR | 201, 400, 401, 403 | Registra / ejecuta pacientes |
| GET | `/api/v1/patients/check-document` | Personal · PACIENTES_VER | 200, 401, 403 | Consulta pacientes · check document |
| GET | `/api/v1/patients/{id}` | Personal · PACIENTES_VER | 200, 401, 403, 404 | Consulta pacientes |
| PUT | `/api/v1/patients/{id}` | Personal · PACIENTES_EDITAR | 200, 400, 401, 403, 404 | Actualiza pacientes |
| GET | `/api/v1/patients/{id}/appointments` | Personal · PACIENTES_VER | 200, 401, 403, 404 | Consulta pacientes · citas |
| GET | `/api/v1/patients/{id}/audit` | Personal · PACIENTES_EDITAR | 200, 401, 403, 404 | Consulta pacientes · auditoría |

## PatientNoteService

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/patients/{patientId}/flags` | Personal · PACIENTES_VER | 200, 401, 403, 404 | Consulta pacientes · flags |
| POST | `/api/v1/patients/{patientId}/notes` | Personal · PACIENTES_EDITAR o HISTORIA_CLINICA | 200, 400, 401, 403, 404 | Registra / ejecuta pacientes · notas |
| DELETE | `/api/v1/patients/{patientId}/notes/{id}` | Personal · ELIMINAR_REGISTROS | 200, 401, 403, 404 | Elimina pacientes · notas |
| PUT | `/api/v1/patients/{patientId}/notes/{id}` | Personal · PACIENTES_EDITAR o HISTORIA_CLINICA | 200, 400, 401, 403, 404 | Actualiza pacientes · notas |

## PatientProfileController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/patients/profile-options` | Personal · PACIENTES_VER | 200, 401, 403 | Consulta pacientes · profile options |
| GET | `/api/v1/patients/ruc/{ruc}` | Personal · PACIENTES_EDITAR | 200, 401, 403, 404 | Consulta de RUC para llenar la razón social (recepción no tiene acceso al módulo de comprobantes). |
| GET | `/api/v1/patients/tags` | Personal · PACIENTES_VER | 200, 401, 403 | Consulta pacientes · tags |
| GET | `/api/v1/patients/{id}/profile` | Personal · PACIENTES_VER | 200, 401, 403, 404 | Consulta pacientes · profile |
| PUT | `/api/v1/patients/{id}/profile` | Personal · PACIENTES_EDITAR | 200, 400, 401, 403, 404 | Actualiza pacientes · profile |
| POST | `/api/v1/patients/{id}/relations` | Personal · PACIENTES_EDITAR | 200, 400, 401, 403, 404 | Registra / ejecuta pacientes · parentescos |
| DELETE | `/api/v1/patients/{id}/relations/{relationId}` | Personal · PACIENTES_EDITAR | 200, 401, 403, 404 | Elimina pacientes · parentescos |
| PUT | `/api/v1/patients/{id}/relations/{relationId}` | Personal · PACIENTES_EDITAR | 200, 400, 401, 403, 404 | Actualiza pacientes · parentescos |

## PeriodontogramController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/patients/{patientId}/periodontograms` | Personal · HISTORIA_CLINICA | 200, 401, 403, 404 | Consulta pacientes · periodontograms |
| POST | `/api/v1/patients/{patientId}/periodontograms` | Personal · HISTORIA_CLINICA | 200, 400, 401, 403, 404 | Registra / ejecuta pacientes · periodontograms |
| GET | `/api/v1/patients/{patientId}/periodontograms/{id}` | Personal · HISTORIA_CLINICA | 200, 401, 403, 404 | Consulta pacientes · periodontograms |
| PUT | `/api/v1/patients/{patientId}/periodontograms/{id}` | Personal · HISTORIA_CLINICA | 200, 400, 401, 403, 404 | Actualiza pacientes · periodontograms |

## PortalController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/portal/account` | Paciente (portal) (autenticado) | 200, 401 | Consulta portal · estado de cuenta |
| GET | `/api/v1/portal/appointments` | Paciente (portal) (autenticado) | 200, 401 | Consulta portal · citas |
| POST | `/api/v1/portal/appointments/{id}/confirm` | Paciente (portal) (autenticado) | 200, 401, 404 | Registra / ejecuta citas · confirmación |
| POST | `/api/v1/portal/auth/activate` | Público (sin token) | 200, 400 | Registra / ejecuta autenticación · activación |
| POST | `/api/v1/portal/auth/code` | Público (sin token) | 200, 400 | Registra / ejecuta autenticación · código |
| POST | `/api/v1/portal/auth/login` | Público (sin token) | 200, 400, 401 | Registra / ejecuta autenticación · inicio de sesión |
| GET | `/api/v1/portal/auth/ping` | Público (sin token) | 200 | La app móvil comprueba la conexión con el servidor de la clínica. |
| POST | `/api/v1/portal/logout` | Paciente (portal) (autenticado) | 200, 401 | Registra / ejecuta portal · logout |
| GET | `/api/v1/portal/me` | Paciente (portal) (autenticado) | 200, 401 | Consulta portal · perfil propio |
| PATCH | `/api/v1/portal/me/notifications` | Paciente (portal) (autenticado) | 200, 400, 401 | Modifica perfil propio · notificaciones |

## PortalExtrasController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| POST | `/api/v1/portal/appointments` | Paciente (portal) (autenticado) | 201, 400, 401, 409 | Registra / ejecuta portal · citas |
| POST | `/api/v1/portal/appointments/{id}/cancel` | Paciente (portal) (autenticado) | 200, 400, 401, 404 | Registra / ejecuta citas · cancelación |
| POST | `/api/v1/portal/appointments/{id}/reschedule` | Paciente (portal) (autenticado) | 200, 400, 401, 404 | Registra / ejecuta citas · reprogramación |
| GET | `/api/v1/portal/booking/options` | Paciente (portal) (autenticado) | 200, 401 | Consulta reserva · opciones |
| GET | `/api/v1/portal/booking/slots` | Paciente (portal) (autenticado) | 200, 401 | Consulta reserva · horarios libres |
| GET | `/api/v1/portal/documents` | Paciente (portal) (autenticado) | 200, 401 | Consulta portal · documents |
| GET | `/api/v1/portal/documents/files/{id}/link` | Paciente (portal) (autenticado) | 200, 401, 404 | Consulta archivos · link |
| GET | `/api/v1/portal/documents/invoices/{id}/link` | Paciente (portal) (autenticado) | 200, 401, 404 | Consulta comprobantes · link |
| GET | `/api/v1/portal/notices` | Paciente (portal) (autenticado) | 200, 401 | Consulta portal · notices |
| POST | `/api/v1/portal/notices/read` | Paciente (portal) (autenticado) | 200, 401 | Registra / ejecuta notices · read |
| POST | `/api/v1/portal/payments` | Paciente (portal) (autenticado) | 200, 400, 401 | Registra / ejecuta portal · pagos |
| POST | `/api/v1/portal/surveys` | Paciente (portal) (autenticado) | 200, 400, 401 | Registra / ejecuta portal · encuestas |
| GET | `/api/v1/portal/surveys/pending` | Paciente (portal) (autenticado) | 200, 401 | Consulta encuestas · pending |
| GET | `/api/v1/public/invoices/{token}` | Público (sin token) | 200, 404 | Boleta o factura en PDF con enlace temporal firmado (se abre en el visor del teléfono). |

## PortalRecordsController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/patients/{patientId}/file-requests` | Personal · PACIENTES_VER | 200, 401, 403, 404 | Consulta pacientes · file requests |
| POST | `/api/v1/patients/{patientId}/file-requests` | Personal · HISTORIA_CLINICA o PACIENTES_EDITAR | 200, 400, 401, 403, 404 | Registra / ejecuta pacientes · file requests |
| POST | `/api/v1/patients/{patientId}/file-requests/{id}/cancel` | Personal · HISTORIA_CLINICA o PACIENTES_EDITAR | 200, 401, 403, 404 | Registra / ejecuta file requests · cancelación |
| POST | `/api/v1/patients/{patientId}/file-requests/{id}/upload` | Personal · PACIENTES_EDITAR o HISTORIA_CLINICA | 200, 401, 403, 404 | Registra / ejecuta file requests · upload |
| GET | `/api/v1/portal/consents` | Paciente (portal) (autenticado) | 200, 401 | Consulta portal · consentimientos |
| GET | `/api/v1/portal/consents/{id}/pdf` | Paciente (portal) (autenticado) | 200, 401, 404 | Consulta consentimientos · pdf |
| GET | `/api/v1/portal/file-requests` | Paciente (portal) (autenticado) | 200, 401 | Consulta portal · file requests |
| POST | `/api/v1/portal/file-requests/{id}/upload` | Paciente (portal) (autenticado) | 200, 401, 404 | Registra / ejecuta file requests · upload |
| GET | `/api/v1/portal/files/{id}/link` | Paciente (portal) (autenticado) | 200, 401, 404 | Consulta archivos · link |
| PUT | `/api/v1/portal/me/contact` | Paciente (portal) (autenticado) | 200, 400, 401 | Actualiza perfil propio · contact |
| GET | `/api/v1/portal/treatments` | Paciente (portal) (autenticado) | 200, 401 | Consulta portal · treatments |

## ProfileController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| POST | `/api/v1/me/password` | Personal · rol PERSONAL | 200, 400, 401, 403 | Registra / ejecuta perfil propio · password |
| GET | `/api/v1/me/profile` | Personal · rol PERSONAL | 200, 401, 403 | Consulta perfil propio · profile |
| PUT | `/api/v1/me/profile` | Personal · rol PERSONAL | 200, 400, 401, 403 | Actualiza perfil propio · profile |

## PublicFileController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/public/files/{token}` | Público (sin token) | 200, 404 | Consulta público · archivos |

## PublicImageController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/public/images/{kind}/{name}` | Público (sin token) | 200, 404 | Consulta público · images |

## PublicPatientController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| POST | `/api/v1/public/booking` | Público (sin token) | 200, 400 | Registra / ejecuta público · reserva |
| GET | `/api/v1/public/booking/options` | Público (sin token) | 200 | Consulta reserva · opciones |
| GET | `/api/v1/public/booking/slots` | Público (sin token) | 200 | Consulta reserva · horarios libres |
| GET | `/api/v1/public/preregistro` | Público (sin token) | 200 | Consulta público · preregistro |
| POST | `/api/v1/public/preregistro` | Público (sin token) | 200, 400 | Registra / ejecuta público · preregistro |
| GET | `/api/v1/public/survey/{token}` | Público (sin token) | 200, 404 | Consulta público · encuesta |
| POST | `/api/v1/public/survey/{token}` | Público (sin token) | 200, 400, 404 | Registra / ejecuta público · encuesta |

## PublicPromotionsController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/public/promotions` | Público (sin token) | 200 | Consulta público · promociones |

## ReportController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/reports/annual` | Personal · REPORTES | 200, 401, 403 | Consulta reportes · annual |
| GET | `/api/v1/reports/annual/csv` | Personal · REPORTES | 200, 401, 403 | Consulta annual · csv |
| GET | `/api/v1/reports/annual/pdf` | Personal · REPORTES | 200, 401, 403 | Consulta annual · pdf |
| GET | `/api/v1/reports/appointments` | Personal · REPORTES | 200, 401, 403 | Consulta reportes · citas |
| GET | `/api/v1/reports/income` | Personal · REPORTES | 200, 401, 403 | Consulta reportes · income |
| GET | `/api/v1/reports/treatments` | Personal · REPORTES | 200, 401, 403 | Consulta reportes · treatments |

## RoleController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/permissions` | Personal · rol ADMIN | 200, 401, 403 | Consulta permisos |
| PATCH | `/api/v1/permissions/catalog/{code}` | Personal · rol ADMIN | 200, 400, 401, 403, 404 | Modifica permisos · catálogo |
| POST | `/api/v1/permissions/roles` | Personal · rol ADMIN | 200, 400, 401, 403 | Registra / ejecuta permisos · roles |
| DELETE | `/api/v1/permissions/roles/{code}` | Personal · rol ADMIN | 200, 401, 403, 404 | Elimina permisos · roles |
| PATCH | `/api/v1/permissions/roles/{code}` | Personal · rol ADMIN | 200, 400, 401, 403, 404 | Modifica permisos · roles |
| PUT | `/api/v1/permissions/{role}` | Personal · rol ADMIN | 200, 400, 401, 403, 404 | Actualiza permisos |

## SupplyController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/inventory/expiring` | Personal · INVENTARIO | 200, 401, 403 | Consulta inventario · expiring |
| GET | `/api/v1/inventory/purchases` | Personal · INVENTARIO | 200, 401, 403 | Consulta inventario · compras |
| POST | `/api/v1/inventory/purchases` | Personal · INVENTARIO | 201, 400, 401, 403 | Registra / ejecuta inventario · compras |
| GET | `/api/v1/inventory/purchases/{id}` | Personal · INVENTARIO | 200, 401, 403, 404 | Consulta inventario · compras |
| POST | `/api/v1/inventory/purchases/{id}/pay` | Personal · rol CAJA_EGRESOS, ADMIN | 200, 400, 401, 403, 404 | Registra / ejecuta compras · pay |
| GET | `/api/v1/inventory/{id}/lots` | Personal · INVENTARIO | 200, 401, 403, 404 | Consulta inventario · lots |
| GET | `/api/v1/supply-requests` | Personal · SOLICITAR_INSUMOS o INVENTARIO | 200, 401, 403 | Consulta supply requests |
| POST | `/api/v1/supply-requests` | Personal · SOLICITAR_INSUMOS o INVENTARIO | 201, 400, 401, 403 | Registra / ejecuta supply requests |
| GET | `/api/v1/supply-requests/catalog` | Personal · SOLICITAR_INSUMOS o INVENTARIO | 200, 401, 403 | Consulta supply requests · catálogo |
| POST | `/api/v1/supply-requests/{id}/answer` | Personal · INVENTARIO | 200, 400, 401, 403, 404 | Registra / ejecuta supply requests · answer |
| POST | `/api/v1/supply-requests/{id}/cancel` | Personal · SOLICITAR_INSUMOS o INVENTARIO | 200, 401, 403, 404 | Registra / ejecuta supply requests · cancelación |

## TaskController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/tasks` | Personal · TAREAS | 200, 401, 403 | Consulta tareas |
| POST | `/api/v1/tasks` | Personal · TAREAS | 200, 400, 401, 403 | Registra / ejecuta tareas |
| GET | `/api/v1/tasks/options` | Personal · TAREAS | 200, 401, 403 | Consulta tareas · opciones |
| GET | `/api/v1/tasks/summary` | Personal · TAREAS | 200, 401, 403 | Consulta tareas · summary |
| PUT | `/api/v1/tasks/{id}` | Personal · TAREAS | 200, 400, 401, 403, 404 | Actualiza tareas |
| POST | `/api/v1/tasks/{id}/cancel` | Personal · TAREAS | 200, 400, 401, 403, 404 | Registra / ejecuta tareas · cancelación |
| POST | `/api/v1/tasks/{id}/complete` | Personal · TAREAS | 200, 400, 401, 403, 404 | Registra / ejecuta tareas · complete |
| POST | `/api/v1/tasks/{id}/reopen` | Personal · TAREAS | 200, 401, 403, 404 | Registra / ejecuta tareas · reopen |

## UserController

| Método | Ruta (`/api/v1`) | Privilegio exigido | Códigos | Qué hace |
|---|---|---|---|---|
| GET | `/api/v1/audit` | Personal · AUDITORIA | 200, 401, 403 | Consulta auditoría |
| GET | `/api/v1/dentists/{id}/schedules` | Personal (autenticado) | 200, 401, 404 | Consulta odontólogos · schedules |
| PUT | `/api/v1/dentists/{id}/schedules` | Personal · USUARIOS | 200, 400, 401, 403, 404 | Actualiza odontólogos · schedules |
| GET | `/api/v1/roles` | Personal · USUARIOS | 200, 401, 403 | Consulta roles |
| GET | `/api/v1/roles/assignable` | Personal · USUARIOS | 200, 401, 403 | v2.10: todos los roles asignables (sistema + creados) con su resumen de permisos. |
| GET | `/api/v1/users` | Personal · USUARIOS | 200, 401, 403 | Consulta usuarios |
| POST | `/api/v1/users` | Personal · USUARIOS | 201, 400, 401, 403 | Registra / ejecuta usuarios |
| PUT | `/api/v1/users/{id}` | Personal · USUARIOS | 200, 400, 401, 403, 404 | Actualiza usuarios |
| PATCH | `/api/v1/users/{id}/active` | Personal · USUARIOS | 200, 400, 401, 403, 404 | Modifica usuarios · active |
| GET | `/api/v1/users/{id}/audit` | Personal · USUARIOS | 200, 401, 403, 404 | Consulta usuarios · auditoría |
| POST | `/api/v1/users/{id}/password` | Personal · USUARIOS | 200, 400, 401, 403, 404 | Registra / ejecuta usuarios · password |
| POST | `/api/v1/users/{id}/unlock` | Personal · USUARIOS | 200, 401, 403, 404 | Registra / ejecuta usuarios · unlock |
