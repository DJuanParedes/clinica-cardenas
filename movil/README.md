# App móvil de pacientes · Kotlin Multiplatform + Compose Multiplatform

App para los pacientes de la Clínica Dental Cárdenas. Un solo código en Kotlin genera la app de **Android** y la de **iOS**. La app consume el API REST del portal del paciente (`/api/portal`) del backend Spring Boot.

## Qué hace

| Función | Historia | Detalle |
|---|---|---|
| Ingreso con DNI/CE/Pasaporte y contraseña | Portal del paciente | Token Bearer guardado en SQLite |
| Primera vez: código + crear contraseña | Portal del paciente | Valida fecha de nacimiento; el código llega por WhatsApp o correo |
| Próxima cita, próximas y historial | HU-16 | Con colores por odontólogo y estado |
| Confirmar asistencia | HU-21 | `POST /appointments/{id}/confirm` |
| Recordatorios en el teléfono 24 h y 2 h antes | HU-22 | AlarmManager + notificación (Android) / UNUserNotificationCenter (iOS) |
| Elegir canal de avisos (WhatsApp / correo / ambos) | HU-23 | `PATCH /me/notifications` |
| Estado de cuenta: saldo, cargos y pagos | Caja | `GET /account` |
| Agregar cita al calendario, WhatsApp, llamar, cómo llegar | Extras nativos | Intents de Android / URLs en iOS |
| Funciona sin conexión | Persistencia | Muestra las citas guardadas en SQLDelight |

Lo que **no** está en la app, a propósito: agendar citas y pagar en línea. En la clínica las citas las asigna recepción y los pagos se cobran en caja con comprobante SUNAT.

## Arquitectura (lo del curso)

- **U1 · KMP y Compose:** `commonMain` contiene toda la interfaz (Material 3), el tema con los colores de la marca y el logo vectorial.
- **U2 · REST, CRUD y persistencia:** Ktor Client + kotlinx.serialization para el API. SQLDelight guarda en SQLite la sesión, el servidor y la caché de citas. Koin hace la inyección de dependencias.
- **MVVM:** cada pantalla tiene su `ViewModel` con `StateFlow`. `ClinicRepository` junta el API, la base local y las funciones del teléfono.
- **Código por plataforma:** la interfaz `PlatformActions` tiene una implementación para Android y otra para iOS.

```
composeApp/src/
├─ commonMain/   App.kt, di/, data/(remote, local, repository), ui/(login, home, account, profile, theme, components), sqldelight/
├─ androidMain/  MainActivity, ClinicaApp (Koin), AndroidPlatformActions, ReminderReceiver, recursos e íconos
└─ iosMain/      MainViewController, IosPlatformActions, NativeDriverFactory
iosApp/          Proyecto de Xcode (SwiftUI que muestra la UI de Compose)
```

## Compilar

**Android Studio:** File → Open → carpeta `movil`. Espera el Gradle Sync y ejecuta `composeApp` en un emulador o celular.

**Sin Android Studio:** en la raíz del repo, doble clic en `construir-apk.bat` (usa Docker). El APK queda en `apk\ClinicaCardenas.apk`.

**iOS:** necesita una Mac con Xcode 15 o superior. Apple no permite compilar apps de iOS en Windows.
```bash
cd movil/iosApp
brew install xcodegen && xcodegen   # genera iosApp.xcodeproj
open iosApp.xcodeproj               # Run en el simulador de iPhone
```

## Conectarse al servidor

En la pantalla de ingreso toca **Servidor**:
- **Emulador de Android:** `10.0.2.2:9081` (ya viene por defecto).
- **Celular real:** la IP de la PC en el mismo Wi-Fi, por ejemplo `192.168.1.20:9081`. Si no conecta, permite el puerto 9081 en el Firewall de Windows.
- **Simulador de iOS:** `localhost:9081`.

Paciente de prueba: DNI **45872196**, contraseña **Paciente2026**.
