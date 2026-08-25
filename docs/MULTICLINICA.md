# Arquitectura multiclinica real

## Modelo implementado

La plataforma usa una sola instalación Docker y un único conjunto de microservicios para
atender varias clínicas o marcas. Cada registro operativo contiene `clinic_id` y cada sesión
lleva esa misma clínica dentro de un JWT firmado. La interfaz nunca decide qué filas puede
leer: el filtro se aplica dentro de Hibernate antes de ejecutar las consultas.

La clínica inicial es `CARDENAS`. Al actualizar una instalación existente, las filas sin
clínica se migran automáticamente a ese código y los índices únicos pasan a ser únicos por
clínica. Por ejemplo, dos marcas pueden registrar el mismo DNI sin compartir al paciente.

## Roles

- **Superadministrador de plataforma:** crea, edita, activa y desactiva marcas; puede cambiar
  la clínica activa desde el selector superior y crear el administrador de cada clínica.
- **Administrador de clínica:** administra usuarios y operaciones de su propia clínica, pero
  no puede cambiar de organización ni crear marcas.
- **Doctor, Recepción y Finanzas:** pertenecen a una clínica y reciben solo sus datos.

Se mantiene el rol técnico `ADMIN`; la facultad global se controla por el atributo independiente
`platformAdmin`, incluido y validado en el token.

## Capas de interfaz

- **Portal general (`/`):** tiene identidad neutral, explica la plataforma y muestra las clínicas
  activas. Nunca usa Cárdenas ni otra marca como identidad predeterminada.
- **Página pública de marca (`/marca/{slug}`):** muestra logo, colores, servicios, contacto,
  solicitudes y encuesta de una clínica concreta.
- **Acceso general (`/login`):** no obliga al usuario a escoger una clínica. La cuenta validada
  determina la organización, el rol y la marca que corresponde.
- **Interfaz privada (`/app`):** se muestra únicamente después de iniciar sesión y aplica la
  identidad de la clínica contenida en el JWT. El superadministrador conserva un selector de
  clínica que emite un nuevo token al cambiar de contexto.

## Crear una marca

1. Inicia sesión con el superadministrador.
2. Abre **Administración → Clínicas y marcas**.
3. Pulsa **Nueva clínica** y registra código, dirección pública, nombre, logo, paleta, contacto
   y dominio opcional.
4. Selecciona la nueva clínica en la barra superior. El portal recarga la sesión con un nuevo
   JWT limitado a esa clínica.
5. Abre **Usuarios y roles** y crea su **Administrador de clínica**; luego crea recepción,
   doctores y finanzas.
6. La web pública queda en `/marca/{slug}` y la encuesta en `/marca/{slug}/encuesta`.

## Identidad por marca

Cada clínica configura:

- código interno y `slug` público;
- nombre, nombre corto, lema y descripción;
- logo e imagen principal mediante URL HTTPS o recurso público;
- colores principal, oscuro, de acento y menú lateral;
- teléfono, correo, dirección, horario y dominio propio opcional.

Al iniciar sesión se aplica automáticamente la marca del usuario. En una URL pública, el
`slug` o dominio resuelve la identidad antes de registrar solicitudes o encuestas.

## Aislamiento de datos

La base compartida usa multitenencia por discriminador:

- `TenantOwnedEntity` declara el campo obligatorio `clinic_id`;
- `@TenantId` agrega el predicado de clínica a lecturas, escrituras y eliminaciones;
- `TenantContext` se obtiene del claim firmado para peticiones autenticadas;
- `X-Clinic-Id` se acepta solo como contexto de portales públicos sin sesión;
- el gateway valida que la cuenta siga habilitada y que el token corresponda a una clínica activa;
- exportación e importación filtran y fuerzan la clínica activa;
- los índices únicos heredados se convierten a `(clinic_id, campo)` sin borrar información.

Todas las 34 entidades persistentes de pacientes, agenda, clínica, finanzas, laboratorio,
operaciones, inventario, contenido, atenciones y encuestas heredan el aislamiento común.

## Seguridad y operación

No se debe aceptar un `clinic_id` enviado por el navegador para sustituir la clínica de un
usuario autenticado. El filtro siempre prefiere el claim del JWT. Cambiar de clínica exige ser
superadministrador y emite un token nuevo. Desactivar una marca impide nuevas sesiones y
cambios hacia ella.

Los contenedores, bases por microservicio y volumen MySQL siguen siendo únicos para optimizar
RAM. La separación organizacional ocurre en cada tabla y en cada petición, por lo que no es
necesario duplicar el stack para añadir una marca.
