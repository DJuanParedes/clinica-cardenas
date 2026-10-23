# Clínica Cárdenas V4.9.17 — MySQL

Esta carpeta contiene **solo la versión vigente V4.9.17**. La base de datos del sistema es **MySQL** tanto en local como en producción.

## Plataforma multiclinica y multimarca

Una sola instalación administra varias clínicas o marcas. El superadministrador entra
en **Clínicas y marcas**, registra logo, colores, dirección pública, dominio y contacto,
y usa el selector superior para cambiar la clínica activa. Después crea el administrador
y los demás usuarios de esa clínica desde **Usuarios y roles**.

El aislamiento no depende de la pantalla: `clinic_id` es obligatorio en las 34 entidades
operativas, Hibernate agrega el filtro a consultas y escrituras, y el JWT firmado contiene
la clínica activa. Pacientes, citas, odontogramas, caja, inventario, auditoría, encuestas y
respaldos quedan separados aunque compartan la misma instalación MySQL. Los registros
anteriores se asignan automáticamente a `CARDENAS`. Consulta `docs/MULTICLINICA.md`.

La interfaz tiene tres niveles claramente separados: `/` es el portal general neutral y
muestra el directorio de todas las clínicas activas; `/marca/{slug}` es la página pública de
una marca concreta; y `/app` es el espacio privado que solo adopta el logo, los colores y los
datos de la clínica después de autenticar al usuario. `/login` y la recuperación de contraseña
también son neutrales, porque la clínica se determina mediante la cuenta validada.
## Superadministrador global privado

La plataforma conserva exactamente una cuenta global con `platformAdmin=true`. Esta cuenta
puede entrar al portal principal, crear clínicas, cambiar la clínica activa y administrar a
sus responsables. La API impide eliminarla, deshabilitarla o cambiarle el rol.

Su nombre, correo, teléfono y canal de recuperación se guardan únicamente en el archivo
privado `.env`, excluido de Git y de los ZIP. En una instalación nueva el asistente solicita
esos datos. No existe una contraseña fija en el código: la cuenta nace con una clave aleatoria
y el propietario establece o recupera su contraseña mediante OTP.

La sede confirmada de **Clínica Cárdenas** se muestra como **La Floresta,
Lima, Perú**. El teléfono, correo y horario público quedan sin inventar hasta que el
superadministrador registre los datos oficiales en **Clínicas y marcas**.

`CONFIGURAR-CLINICA.bat` se conserva únicamente para la identidad predeterminada de una
instalación nueva o para compatibilidad; las marcas adicionales se crean desde el sistema.

## Uso local recomendado

1. Extrae la carpeta en una ruta corta, por ejemplo `C:\ClinicaCardenas`.
2. Abre Docker Desktop.
3. Primera instalación o después de recibir una actualización: ejecuta `01-INSTALAR-O-ACTUALIZAR.bat`.
4. En los siguientes días: ejecuta `02-INICIAR.bat`. **No recompila.**
5. Para detener: `03-DETENER.bat`.
6. Para revisar el estado: `04-VER-ESTADO.bat`.
7. Si ocurre un problema: `05-GENERAR-DIAGNOSTICO.bat`.

## Base de datos MySQL

Se usa **un solo servidor MySQL** y una base independiente por microservicio que persiste datos. Esto evita levantar múltiples servidores de base de datos y mantiene separado el esquema lógico de cada servicio.

Bases locales:

- `auth_db`
- `patient_db`
- `appointment_db`
- `clinical_db`
- `finance_db`
- `lab_db`
- `operations_db`
- `inventory_db`
- `content_db`
- `encounter_db`
- `survey_db`

### Conexión desde DBeaver en local

- Host: `localhost`
- Puerto: `3307`
- Motor: MySQL
- Usuario administrador local: `root`
- Contraseña: valor `MYSQL_ROOT_PASSWORD` del archivo `.env` generado por `PREPARAR-ENTORNO-LOCAL.bat`

También puedes conectarte con los usuarios propios de cada microservicio (`auth_user`, `patient_user`, etc.) usando su contraseña correspondiente del `.env`.

## Persistencia

Los datos MySQL locales se guardan en el volumen Docker `mysql-data`. `03-DETENER.bat` **no borra** ese volumen. `LIMPIAR-DATOS-LOCAL.bat` sí elimina los volúmenes locales y debe usarse únicamente si quieres reiniciar los datos desde cero.

**Importante:** al cambiar a esta arquitectura se crea un almacenamiento MySQL nuevo. Si necesitas conservar datos reales de una instalación anterior con otro motor, esos datos deben exportarse y migrarse explícitamente; el instalador no hace una conversión automática.

## Compilación optimizada

La compilación Java usa un solo reactor Maven para generar los 15 JAR y los exporta temporalmente al host sin crear una imagen intermedia pesada. Después Docker construye las 15 imágenes Java de forma secuencial, una por una, y compila el frontend al final. Si una fase posterior falla, los JAR exportados de la misma versión se reutilizan en el siguiente intento. MySQL usa la imagen local `mysql:8.4` cuando ya existe y solo se descarga cuando hace falta.

## Creación automática de contenedores

Después de completar las imágenes, `01-INSTALAR-O-ACTUALIZAR.bat` ejecuta `ASEGURAR-CONTENEDORES.ps1`. Si no existe ningún contenedor, crea los 17 definidos por Compose (MySQL + 16 componentes) sin recompilar y verifica 17/17 antes del arranque. `02-INICIAR.bat` hace la misma comprobación: si las imágenes son correctas pero los contenedores fueron borrados, los vuelve a crear automáticamente sin tocar los volúmenes persistentes.

## Inicio optimizado

El inicio local es escalonado: MySQL se inicia y valida primero; después Eureka y los microservicios por grupos; finalmente Gateway y frontend. Esto reduce picos de RAM/CPU.

## URLs locales

- Portal general de clínicas: http://localhost:8080
- Página pública Cárdenas: http://localhost:8080/marca/cardenas
- Acceso general: http://localhost:8080/login
- Gateway: http://localhost:8090/actuator/health
- Eureka: http://localhost:8761
- MySQL para DBeaver: `localhost:3307`

## Odontograma FDI / ISO 3950

El módulo clínico mantiene el flujo original de la clínica y aplica una estructura compatible con la nomenclatura de dos dígitos de **FDI / ISO 3950:2016**. Esta implementación es una alineación técnica del módulo; no representa una certificación del software por FDI ni por ISO.

- Dentición permanente: cuadrantes 1 a 4 y piezas 11–48.
- Dentición temporal: cuadrantes 5 a 8 y piezas 51–85.
- Registro de dentición permanente, temporal o mixta.
- Superficies mesial (M), distal (D), vestibular (V), lingual/palatina (L), oclusal (O) e incisal (I), con validación anatómica básica.
- Caries documentada por severidad (`INICIAL`, `MODERADA`, `SEVERA`) y actividad (`ACTIVA`, `INACTIVA`).
- Evaluación de riesgo de caries, factores de riesgo, factores protectores, diagnóstico y plan de tratamiento.
- El backend rechaza números dentales, superficies y valores clínicos no reconocidos; además convierte los odontogramas antiguos del formato simple al formato estructurado al volver a guardarlos.

Referencias oficiales consultadas:

- ISO 3950:2016, *Dentistry — Designation system for teeth and areas of the oral cavity*: https://www.iso.org/standard/68292.html
- FDI, *Classification of caries lesions of tooth surfaces and caries management systems*: https://www.fdiworlddental.org/classification-caries-lesions-tooth-surfaces-and-caries-management-systems
- FDI, *Carious lesions and first restorative treatment*: https://www.fdiworlddental.org/carious-lesions-and-first-restorative-treatment
- FDI, *Caries prevention and management chairside guide*: https://www.fdiworlddental.org/caries-prevention-and-management-chairside-guide

## Flujo simplificado de Recepción

El menú **Recepción de hoy** concentra las tareas operativas que requieren una decisión inmediata y mantiene separadas las responsabilidades clínicas:

1. Buscar o registrar al paciente verificando DNI y teléfono.
2. Programar la cita seleccionando doctor, sillón y horario disponible.
3. El día de la cita, registrar únicamente `LLEGÓ` o `NO ASISTIÓ`.
4. Si no asistió, resolver el caso reprogramando con historial o cerrando sin reprogramar.
5. Derivar al doctor para diagnóstico y atención clínica; Recepción no modifica odontogramas ni evoluciones.
6. Registrar pagos solamente contra una proforma o saldo identificado.

La pantalla también muestra solicitudes web nuevas, pacientes por recibir, personas presentes e inasistencias pendientes. Desde cada tarjeta se abre la operación correspondiente sin navegar por módulos clínicos. El odontograma se vincula al identificador único del paciente para evitar cruces entre personas con nombres iguales.

## Producción

En producción MySQL queda en una red Docker interna y **no publica el puerto 3306 al host**. Consulta `docs/PRODUCCION.md`.

Para preparar un despliegue por servicios desde GitHub, consulta
[`docs/RAILWAY.md`](docs/RAILWAY.md). Los secretos reales se configuran fuera del
repositorio usando `.env.production.example` únicamente como inventario. Las reglas para
credenciales y datos clínicos están en [`SECURITY.md`](SECURITY.md).

## Recuperación cuando las imágenes ya existen pero no hay contenedores

Si Docker muestra las 17 imágenes necesarias pero `Containers: 0`, **no vuelvas a compilar**. Ejecuta `02-INICIAR.bat`. El iniciador valida las imágenes mediante `docker image inspect` en JSON, crea automáticamente los 17 contenedores con `docker compose create --no-build --pull never`, verifica 17/17 y recién después inicia MySQL y el resto del sistema.

La verificación de versión de las imágenes no usa plantillas `--format` de Docker; procesa el JSON con PowerShell para evitar diferencias de interpretación de comillas en Windows PowerShell 5.1. Si falla esa comprobación, el diagnóstico lo registra como `ERROR_VERIFICACION_IMAGENES`, separado de errores reales del frontend.
## Instalación limpia desde Docker vacío

Si eliminaste imágenes, contenedores y volúmenes, abre Docker Desktop y ejecuta **solo** `01-INSTALAR-O-ACTUALIZAR.bat`. El instalador descargará MySQL, compilará y construirá las 16 imágenes de la aplicación, creará los 17 contenedores, inicializará las 11 bases MySQL, arrancará el sistema por etapas y ejecutará la prueba funcional final.

No ejecutes `02-INICIAR.bat` hasta que `01-INSTALAR-O-ACTUALIZAR.bat` termine con `INSTALACION / ACTUALIZACION COMPLETADA`.

### Recuperación automática de MySQL
La inicialización MySQL es idempotente. Si una ejecución anterior dejó el volumen creado pero faltan bases o usuarios, el arranque vuelve a ejecutar el inicializador y comprueba 11/11 antes de iniciar Eureka. Al actualizar desde otra carpeta, se recuperan los secretos MySQL del contenedor existente para mantener compatibilidad con el volumen.

## Repetir solo la validación funcional

Si los contenedores ya están ejecutándose y solo necesitas volver a comprobar el sistema, usa `06-VALIDAR-SISTEMA.bat`. Este flujo recupera los secretos de la instalación existente, verifica Gateway, MySQL y Backup, y ejecuta la suite funcional **sin recompilar imágenes ni recrear contenedores sanos**. Si `backup-service` estaba detenido, puede iniciarlo sin tocar los demás servicios.

La suite distingue una respuesta HTTP correcta con lista vacía (`[]`) de un error de autenticación. Los HTTP 400/401/403 que forman parte de pruebas negativas esperadas se registran como validaciones correctas y no como errores reales.


## Reparación dirigida del ruteo local
Si el Gateway necesita la corrección de ruteo local, ejecuta `07-REPARAR-RUTEO-Y-VALIDAR.bat`. Reconstruye únicamente `api-gateway` cuando su marcador runtime sea antiguo. Si el ruteo ya está correcto, no recrea Gateway/Frontend de forma innecesaria. No elimina ni renueva los volúmenes MySQL/documentos.

La comprobación de `/api/public/home` valida el contrato JSON por propiedades (`clinic`, `tagline`, `specialties`, `hours`, `phone`, `address`) y horario, sin comparar literalmente textos acentuados. Esto evita falsos negativos de Windows PowerShell 5.1 por decodificación UTF-8.

## Validación final del estado actual

Cuando el Gateway ya tenga el marcador `docker-dns-auth-scope-v3`, usa `08-VALIDAR-FINAL.bat`. No ejecuta Maven, NPM ni `docker build`; verifica ruteo, recupera `backup-service` si estaba detenido, comprueba MySQL 11/11 y ejecuta la batería funcional completa.

El filtro de sesión del Gateway limita únicamente la llamada interna a `auth-service`; el tiempo de ejecución del microservicio destino queda fuera de ese timeout. En local la validación interna admite hasta 10 segundos y un solo reintento transitorio, pero continúa en modo fail-closed: si no se puede confirmar la vigencia del JWT, la operación se rechaza. Producción mantiene un timeout explícito de 3 segundos para esa validación interna.

Si una carpeta anterior tiene el marcador de Gateway previo, ejecuta `07-REPARAR-RUTEO-Y-VALIDAR.bat`: reconstruye únicamente `api-gateway`, recrea Gateway + Frontend para refrescar DNS y continúa con la validación sin tocar MySQL ni los demás microservicios.

## Avance académico del Sprint 1

La trazabilidad de HU-01, HU-02 y HU-12, las mejoras de seguridad, el guion de demostración y las verificaciones pendientes están en [docs/SPRINT-1.md](docs/SPRINT-1.md). La evidencia corresponde al 22/10/2026 y no acredita reuniones ni ejecución durante el calendario original del Sprint.


## Documentación académica revisada

El contraste directo con el Charter y las tarjetas completas están en [docs/REVISION-ACADEMICA-SPRINT-1.md](docs/REVISION-ACADEMICA-SPRINT-1.md). La página académica y sus archivos descargables se incluyen en `frontend/public/sprint-1/`; tras desplegar este cambio estarán en [Sprint 1](https://djuanparedes.github.io/clinica-cardenas/sprint-1/). La estimación 21 SP es propuesta y no acredita eventos ni conformidad del cliente.
