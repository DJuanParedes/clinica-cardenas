# App móvil · Kotlin Multiplatform + Compose Multiplatform (versión 2.5.0)

App de la Clínica Dental Cárdenas para **pacientes** y **personal**. Un solo módulo compartido en Kotlin genera la app de **Android** y la de **iOS**. Consume el servicio REST en `/api/v1`.

Documentación del proyecto final:

- [`docs/arquitectura.md`](docs/arquitectura.md): capas, offline-first, seguridad del token y capacidad nativa.
- [`docs/pruebas-dispositivos.md`](docs/pruebas-dispositivos.md): recorrido de 8 pasos y matriz de dispositivos.
- [`../docs/c4/`](../docs/c4/): diagramas C4 (contexto, contenedores, componentes y sincronización).
- [`../docs/contrato-api.md`](../docs/contrato-api.md): las 347 rutas del servicio.

## Qué hace (modo paciente)

| Función | Cómo |
|---|---|
| Ingreso con documento y contraseña; primera vez con código | `POST /api/v1/portal/auth/login` · token en Keystore / Keychain |
| Próxima cita, próximas e historial | Lee **solo** de la base local (SQLDelight, `Flow`) |
| Confirmar asistencia, también sin conexión | Escritura local + cola `operacion_pendiente` |
| Reservar una cita, también sin conexión | Id provisional negativo + `Idempotency-Key` |
| Cambiar horario y cancelar | Solo en línea (la clínica valida el horario) |
| Recordatorios 24 h y 2 h antes | AlarmManager / UNUserNotificationCenter, permiso en 3 estados |
| Cuenta, documentos, avisos y perfil | Servicio REST con validación del servidor |

El **modo personal** (agenda, ficha, fotos con la cámara, desbloqueo con huella) trabaja en línea.

## Arquitectura

```
composeApp/src/commonMain/kotlin/pe/clinicacardenas/pacientes/
├── domain/   modelo, DomainError (sealed), interfaces de repositorio, casos de uso  ← Kotlin puro
├── data/     local (SQLDelight), remote (Ktor + ErrorMapper), platform, repository
├── di/       Modules.kt (Koin)
└── ui/       pantalla + ViewModel (StateFlow) por carpeta
androidMain/  MainActivity, AndroidTokenStorage (Keystore AES-GCM), AndroidPlatformActions, ReminderReceiver
iosMain/      MainViewController, IosTokenStorage (Keychain), IosPlatformActions
commonTest/   ErrorMapperTest, OfflineSyncTest, ApiContractTest, ReminderSchedulerTest
androidUnitTest/ LocalDataTest (SQLite real en memoria)
```

Sincronización (`SyncAppointmentsUseCase`): **sube primero y baja después**; máximo 5 intentos con espera 30 s · 2^(n-1); la falta de red no gasta intentos; un rechazo del servicio (400/409) queda como operación **fallida y visible** con Reintentar / Descartar.

## Compilar y probar

Requisitos: JDK 17+, Android SDK 35 (o Android Studio). En la carpeta `movil`:

```bash
./gradlew :composeApp:assembleDebug            # APK de desarrollo
./gradlew :composeApp:testDebugUnitTest        # 27 pruebas (commonTest + JVM)
./gradlew :composeApp:koverXmlReportDebug      # cobertura (Kover)
./gradlew :composeApp:iosSimulatorArm64Test    # pruebas en el simulador (solo en macOS)
```

## Build firmado de Android

El almacén de firma **no está en el repositorio** (`*.jks` está en `.gitignore`). Con el archivo y su contraseña:

```bash
./gradlew :composeApp:assembleRelease :composeApp:bundleRelease
apksigner sign --ks clinica-cardenas-release.jks --ks-key-alias clinica-release \
  --out ClinicaCardenas-2.5.0-release.apk composeApp/build/outputs/apk/release/composeApp-release-unsigned.apk
jarsigner -keystore clinica-cardenas-release.jks composeApp/build/outputs/bundle/release/composeApp-release.aab clinica-release
apksigner verify --print-certs ClinicaCardenas-2.5.0-release.apk
```

Huella SHA-256 del certificado: `3A:95:C5:EE:B0:C0:48:BE:82:90:88:B2:FA:08:63:1E:AF:83:44:6D:40:5A:D5:E2:18:83:E7:0B:5F:96:30:4D`.

**Instalar:** desinstalar antes las versiones anteriores (estaban firmadas con la clave de depuración), copiar el APK al teléfono y permitir "Instalar apps desconocidas".

## iOS

Requiere macOS con Xcode 15+: `cd iosApp && xcodegen && open iosApp.xcodeproj`, elegir el equipo en *Signing & Capabilities* y ejecutar en el iPhone. El CI compila el framework de iOS y corre las pruebas en el simulador en cada cambio.
