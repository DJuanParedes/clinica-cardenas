# Pruebas en dispositivos reales

Recorrido de 8 pasos que se ejecuta **completo en cada dispositivo**, desde la instalación del archivo firmado y con la computadora de desarrollo desconectada. Mínimo: **dos Android distintos y un iPhone**.

## Recorrido

| N.º | Paso | Cómo se hace en esta app | Qué se espera ver |
|---|---|---|---|
| 1 | Instalación desde el archivo firmado | Copiar `ClinicaCardenas-2.5.0-release.apk` al teléfono e instalarlo (si había una versión anterior firmada con otra clave, desinstalarla antes). | La app abre y muestra la pantalla de ingreso. |
| 2 | Inicio de sesión correcto e incorrecto | Paciente DNI 45872196 con una contraseña errada; luego con la correcta. | Error "Documento o contraseña incorrectos"; luego la pantalla de inicio. |
| 3 | CRUD completo con un rechazo de validación | **Crear:** Reservar una cita. **Leer:** verla en "Próximas citas". **Actualizar:** Cambiar horario. **Eliminar:** Cancelar la cita. **Rechazo:** en Perfil, guardar un correo inválido. | Cada operación se refleja en la lista; el correo inválido muestra "Correo no válido". |
| 4 | Operación sin el privilegio | Entrar como personal con el usuario **caja** y abrir la ficha de un paciente. | No aparecen Historia clínica ni Plan; el servicio responde 403 si se intenta por la API. |
| 5 | Modo avión: abrir, leer y crear | Activar modo avión, cerrar y abrir la app, ver las citas y **Confirmar** una (o reservar con un horario ya cargado). | Las citas se ven ("Sin conexión"); la cita queda con "⏳ Pendiente de envío" y el panel dice "1 cambio por enviar". |
| 6 | Recuperar la conexión y sincronizar | Quitar el modo avión y pulsar **Sincronizar** (o esperar 30 s). | "✓ Todo enviado"; la cita queda Confirmada y sin marca de pendiente. |
| 7 | Permiso: conceder, denegar y denegar para siempre | Primera vez: **Permitir**. Luego en Ajustes del sistema quitar el permiso → la tarjeta pide permiso; denegar dos veces (Android) o una vez (iOS). | Concedido: "🔔 N recordatorios programados". Denegado: botón "Permitir notificaciones". Para siempre: botón "Abrir ajustes". |
| 8 | Cerrar sesión y reabrir | Perfil → Cerrar sesión; cerrar la app y abrirla. | Vuelve a la pantalla de ingreso sin datos del paciente. Sin cerrar sesión, al reabrir entra directo. |

## Matriz (F-06)

| Dispositivo y versión del sistema | Plataforma | Build instalado | Pasos ejecutados | Resultado | Responsable y fecha |
|---|---|---|---|---|---|
| _(completar)_ | Android | 2.5.0 (7) release firmado | 1–8 | | |
| _(completar)_ | Android | 2.5.0 (7) release firmado | 1–8 | | |
| _(completar)_ | iOS | — | — | | |

## Registro de defectos (F-07)

| N.º | Defecto | Severidad | Dispositivo | Estado |
|---|---|---|---|---|
| | | | | |

Severidad: alta si impide el recorrido, media si lo estorba, baja si es cosmética. Estado: corregido, pendiente o descartado (con el motivo).
