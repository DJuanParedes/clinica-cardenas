# Arquitectura de la app móvil · Clínica Dental Cárdenas

App multiplataforma (Android e iOS) hecha con **Kotlin Multiplatform** y **Compose Multiplatform**. Un solo módulo compartido (`composeApp`) contiene el dominio, los datos y la presentación; Android e iOS solo aportan el punto de entrada y las piezas nativas (almacén seguro, notificaciones, cámara).

Los diagramas C4 completos (contexto, contenedores y componentes) están en el informe técnico; aquí van las capas y las decisiones.

## Capas y dirección de las dependencias

```
┌──────────────── Presentación (ui/) ────────────────┐
│ Pantallas Compose  ──observa StateFlow──▶  ViewModel │
└───────────────────────────┬────────────────────────┘
                            │ invoca
┌──────────────── Dominio (domain/) ─────────────────┐   Kotlin puro: sin Ktor, sin SQLDelight,
│ usecase/   ObserveAppointments, ConfirmAppointment, │   sin SDK de proveedor.
│            RequestAppointment, SyncAppointments,    │
│            ObserveSyncStatus, ScheduleReminders     │
│ repository/ interfaces: AppointmentLocalRepository, │
│            AppointmentRemoteRepository, TokenStorage,│
│            ReminderScheduler, Clock, KeyGenerator    │
│ model/     Appointment, PendingOperation, SyncStatus │
│ error/     DomainError (sealed class)                │
└───────────────────────────▲────────────────────────┘
                            │ implementan
┌──────────────── Datos (data/) ─────────────────────┐
│ local/    AppointmentLocalRepositoryImpl (SQLDelight)│
│ remote/   AppointmentRemoteRepositoryImpl, ApiClient │
│           (Ktor, una sola instancia), ErrorMapper    │
│ platform/ ReminderSchedulerImpl                      │
│ androidMain / iosMain: AndroidTokenStorage (Keystore)│
│           IosTokenStorage (Keychain), drivers SQLite │
└──────────────────────────────────────────────────────┘
```

Las flechas apuntan siempre hacia el dominio: el dominio define las interfaces y la capa de datos las implementa. La inyección de dependencias (Koin, `di/Modules.kt`) une ambas partes.

## Flujo offline-first

1. **Fuente única de verdad:** la pantalla de inicio lee las citas de SQLite con consultas observables (`asFlow().mapToList()`); nunca espera a la red para dibujar.
2. **Escritura local primero:** confirmar o reservar guarda el cambio en la base (la reserva con un **id provisional negativo**) y encola una operación en `operacion_pendiente`. La cita muestra "Pendiente de envío".
3. **Sincronización** (`SyncAppointmentsUseCase`): primero **sube** las operaciones en orden de creación y después **baja** las citas del servicio, reemplazándolas sin pisar las que siguen pendientes.
4. **Reintento acotado:** máximo 5 intentos por operación con espera creciente (30 s, 60 s, 120 s, 240 s). La falta de red no cuenta como intento. Solo se reintenta lo idempotente: confirmar lo es por naturaleza y la reserva lleva `Idempotency-Key`, así que el servicio nunca crea dos citas.
5. **Conflictos:** manda el servicio. Si rechaza la operación (horario ocupado, validación) queda **fallida y visible** en Inicio, con "Reintentar" o "Descartar".
6. **Cuándo sincroniza:** al abrir la pantalla, cada 30 s mientras haya pendientes y al pulsar "Sincronizar". La pantalla muestra cuántos cambios faltan y la antigüedad de los datos.

## Seguridad en el dispositivo

- El token JWT se guarda en el **Android Keystore** (clave AES-256 no extraíble, cifrado AES/GCM) y en el **Keychain de iOS** (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`), detrás de la interfaz común `TokenStorage`.
- Las versiones anteriores lo guardaban en SQLite: al abrir la versión 2.5.0 se mueve al almacén seguro y se borra de la base.
- Cerrar sesión borra el token, los datos del paciente, las citas, la cola de pendientes y la hora de la última sincronización.
- No se guardan contraseñas. El cliente HTTP no tiene registro de peticiones, así que la cabecera `Authorization` nunca se escribe en el log.

## Capacidad nativa: recordatorios

`ReminderScheduler` (dominio) → `ReminderSchedulerImpl` sobre `PlatformActions`: AlarmManager + notificaciones en Android, `UNUserNotificationCenter` en iOS. El permiso de notificaciones se resuelve en tres estados:

| Estado | Qué ve el paciente |
|---|---|
| Concedido | Los recordatorios se programan 24 h y 2 h antes de cada cita vigente. |
| Denegado | Tarjeta "No diste permiso…" con el botón **Permitir notificaciones** (vuelve a preguntar). |
| Denegado para siempre | Tarjeta "Las notificaciones están bloqueadas…" con el botón **Abrir ajustes** (Android: "no volver a preguntar"; iOS: cualquier rechazo). |

## Decisiones de arquitectura

| Decisión | Alternativa descartada | Por qué |
|---|---|---|
| La sincronización es un caso de uso del dominio que solo conoce interfaces | Sincronizar dentro del repositorio o del ViewModel | El orden subir→bajar, el reintento y la política de conflictos son reglas del negocio: así se prueban en `commonTest` con dobles, sin red ni base. |
| Reservas con id provisional negativo y `Idempotency-Key` en el servicio | Bloquear la reserva sin conexión | El paciente no pierde lo que hizo sin señal y el reintento nunca duplica una cita. |
| Un solo `HttpClient` de Ktor con la cabecera Bearer centralizada | Un cliente por pantalla o token pasado a mano | Configuración, tiempo de espera y token en un único lugar; el `ErrorMapper` traduce todo a `DomainError`. |
| Monorepositorio con `backend/` y `movil/` | Dos repositorios | El contrato del API y la app evolucionan juntos en cada versión (mismas ramas `feat/<versión>/<tema>` → `release/<versión>`). Ver la ficha de repositorios en el informe. |

## Pruebas automatizadas

- `commonTest` (JVM e iOS): traducción de errores, registro sin conexión, orden de la sincronización, reintento acotado, contrato con respuestas reales del API (Ktor `MockEngine`), recordatorios y permisos.
- `androidUnitTest` (JVM): la capa de datos sobre una base **SQLite real** en memoria (mismo esquema SQLDelight), sesión en el almacén seguro y cierre de sesión.
- Se ejecutan en CI (`.github/workflows/ci.yml`) junto con la cobertura (Kover).
