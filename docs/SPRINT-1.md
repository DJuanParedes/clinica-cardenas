# Sprint 1 de Clínica Dental Cárdenas

## Alcance y fecha de la evidencia

Fuente académica: `PROJECT_CHARTER_CLINICA_CARDENAS_PORTADA_UPEU.docx`, secciones 13.1, 13.2 y 14.
El Charter fija el Sprint 1 en identidad, seguridad y pacientes, del **30/09 al 07/10/2026**.
La selección **HU-01, HU-02 y HU-12** y sus **8, 5 y 8 Story Points** son propuestas pendientes
de validación por el equipo y el Product Owner. Los puntos no representan avance ni horas.

Este cambio se prepara el **22/10/2026**, 15 días después del cierre originalmente previsto.
El repositorio ya incluía módulos de sprints posteriores. Se conservan; no se presentan como
trabajo nuevo de Sprint 1 ni como evidencia de ejecución dentro de las fechas originales.
No se acreditan reuniones Daily, Review, aceptación del cliente ni implementación histórica.

## Objetivo propuesto

Habilitar una base segura para gestionar usuarios y permisos, registrar y buscar pacientes
y recuperar el acceso mediante OTP controlado, con trazabilidad verificable.

| ID del Charter | Alcance exacto | Implementación y evidencia |
|---|---|---|
| HU-01 | Como Administrador, quiero gestionar usuarios y roles, para controlar quién accede a cada módulo. | Pantalla `/app/usuarios`, API `/api/users`, autorización ADMIN y revocación de sesiones existentes. Se añade bitácora persistente de creación, actualización, habilitación, bloqueo, eliminación previa al primer acceso y reemisión. |
| HU-02 | Como Recepcionista, quiero registrar y buscar un paciente, para evitar duplicados y disponer de su información. | Pantalla `/app/pacientes`, API `/api/patients` y `/api/patients/search?term=...`. Búsqueda existente por documento, nombre o teléfono. Se exige DNI y se informa HTTP 409 ante coincidencia y conflicto concurrente de integridad. |
| HU-12 | Como Usuario autorizado, quiero recuperar mi acceso mediante OTP, para restablecer credenciales de forma controlada. | Pantalla `/recuperar-contrasena`, API `/api/auth/password-reset/request` y `/confirm`. Respuesta pública genérica existente. OTP local con solicitud previa, diez minutos de vigencia, cinco intentos y consumo único; registro de solicitud, rechazo por código y recuperación completada. |

El rol técnico `DOCTOR` corresponde al Odontólogo del Charter. `RECEPCION` corresponde
a Recepción. Los roles FINANZAS y superadministración son capacidades preexistentes del
repositorio; este cambio no amplía las historias seleccionadas.

## Bitácora

`GET /api/users/audit` requiere ADMIN y devuelve los últimos 100 eventos de la clínica activa,
ordenados por fecha e ID descendentes. La pantalla Usuarios incluye una tabla de consulta.
`SECURITY_AUDIT` registra fecha UTC, actor, acción, ID del usuario y rol anterior/nuevo.
La interfaz muestra la hora en la zona local del navegador. Las recuperaciones no autenticadas
se registran como actor anónimo; la clínica se obtiene de la cuenta, no de un encabezado público.
No se guardan claves, códigos OTP, tokens ni datos clínicos en esta tabla.

El perfil MySQL existente usa `ddl-auto: update` y crea esta tabla al iniciar el servicio.
La creación física y persistencia en MySQL quedan por comprobar en el entorno de demostración.
No se ejecutaron cambios sobre una base de datos real de la clínica.

## OTP académico

Se conserva `OTP_PROVIDER=local` y el código privado configurado en `LOCAL_OTP_CODE` para
la demostración controlada. El código por sí solo ya no permite recuperar una cuenta: debe
existir una solicitud vigente para ese destino y canal. Los desafíos locales y de correo
se consumen atómicamente y se invalidan al quinto error. Reiniciar auth-service los elimina
y exige solicitar otro código. Para varias réplicas se necesita un almacén compartido de
desafíos; esta mejora no implementa ese despliegue.

No usar el proveedor local como mecanismo productivo ni publicar `.env`. No se enviaron
correos ni SMS reales durante estas pruebas. Las integraciones comerciales no forman parte
de la evidencia académica de este Sprint.

## Reproducción y revisión

Usar una instalación de ensayo y datos ficticios. Requisitos de la aplicación existente:
Docker Desktop para el sistema completo, o Java 21 y Maven para las pruebas del backend.

```powershell
# Desde la raíz del repositorio, con Java 21
mvn -B -ntp -pl auth-service,patient-service -am test
# En frontend, con Node compatible con package.json
npm ci --no-audit --no-fund
npm test
```

La verificación local usa JBR 25 con objetivo Java 21 y `-Dmaven.compiler.proc=full`, necesario
para activar Lombok con ese compilador. El CI existente usa Java 21.

Resultados verificados el 22/10/2026:

| Verificación ejecutada | Resultado |
|---|---|
| Maven de common-security, auth-service y patient-service | Compilación correcta; 19 pruebas, 0 fallos, 0 errores |
| OTP | 5 pruebas: solicitud previa, consumo único, expiración, límite de intentos, aislamiento y concurrencia |
| Seguridad de usuarios | 4 pruebas: autorización por rol, consulta ADMIN, cambio/revocación y aislamiento de clínica |
| Recuperación y sesiones | 4 pruebas: respuesta genérica, bloqueo, recuperación y rechazo de tokens previos |
| Servicio de bitácora | 1 prueba de asignación y consulta por clínica |
| Pacientes | 5 pruebas: búsqueda, duplicado previo, conflicto concurrente simulado, registro y DNI obligatorio |
| Frontend | 33 plantillas verificadas y compilación Angular de desarrollo correcta |

Las pruebas del backend usan colaboradores simulados y autorización de métodos mediante
Spring; no son una prueba de integración con MySQL ni un ensayo de extremo a extremo.
La compilación de Angular no equivale a probar sus interacciones en navegador.

Para instalar el sistema completo desde esta rama, seguir el README y ejecutar
`01-INSTALAR-O-ACTUALIZAR.bat`; en usos posteriores, `02-INICIAR.bat`.
El punto de entrada local es `http://localhost:8080/login`. La configuración de la cuenta
administradora y los secretos se realiza fuera de Git. No se incluye contraseña compartida.

Guion de demostración pendiente de ejecutar contra MySQL:

1. Entrar con ADMIN en una clínica de ensayo y crear un usuario de Recepción.
2. Completar su activación por OTP. Ingresar y registrar un paciente ficticio.
3. Repetir su DNI y comprobar la advertencia. Buscarlo por documento, nombre y teléfono.
4. Cambiar el rol o bloquear la cuenta desde ADMIN; comprobar que su sesión previa se rechaza.
5. Consultar la bitácora en Usuarios y revisar actor, fecha y cambio de rol.
6. Solicitar recuperación, comprobar expiración, cinco errores y consumo único con una cuenta ficticia.
7. Guardar capturas y resultados reales con fecha, versión y responsable.

## DoR y DoD pendientes de aceptación

- DoR común: reglas, dependencias, criterios, datos de prueba, capacidad y estimación consensuada.
- HU-01: acordar la matriz RBAC y contrastar asignación/retiro y bitácora.
- HU-02: acordar ficha, formatos y coincidencias; contrastar registro y las tres búsquedas.
- HU-12: validar vigencia e intentos propuestos, canal académico y no enumeración.
- DoD común: revisión, integración, pruebas, documentación, criterios cumplidos y ausencia de defectos críticos conocidos.
- Las pruebas automatizadas no sustituyen la demostración integrada, Review ni conformidad del PO.

Daily, responsables personales, capacidad, porcentaje de avance y reprogramación permanecen
pendientes de registro. No deben completarse retrospectivamente sin evidencia.
