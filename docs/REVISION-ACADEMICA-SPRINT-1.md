# Revisión académica del Sprint 1

Corte: 22/10/2026. Contraste directo con el Charter aportado, versión 1.1 del 17/09/2026.

## Objetivo propuesto

Habilitar una base segura para gestionar usuarios y permisos, registrar y buscar pacientes y recuperar el acceso mediante OTP controlado, con trazabilidad verificable.

Periodo original del Charter: 30/09 al 07/10/2026. La revisión actual no acredita ejecución durante ese periodo.

## Estimación propuesta

8 + 5 + 8 = 21 SP. Inicial: 18 SP. El Charter no asigna puntos. Developers deben consensuar y registrar la estimación con capacidad real. Fibonacci es una técnica complementaria.

## HU-01 Gestionar usuarios y roles

Como Administrador, quiero gestionar usuarios y roles, para controlar quién accede a cada módulo.

8 SP propuestos. Referencia de mayor complejidad: matriz RBAC, cambio de permisos, revocación de sesiones y bitácora. Mantener 8 sujeto al consenso de Developers.

### Criterios

- CA-01.1 Dado un ADMIN autenticado, cuando crea un usuario válido y asigna un rol autorizado, entonces se registra la cuenta y sus permisos corresponden a la matriz acordada.
- CA-01.2 Dado un usuario con sesión activa, cuando ADMIN cambia su rol o lo bloquea, entonces el siguiente acceso con la sesión previa se rechaza y se aplican los nuevos permisos tras autenticar de nuevo.
- CA-01.3 Dado un usuario sin rol ADMIN, cuando invoca la administración de usuarios, entonces la API deniega la operación aunque se acceda por URL directa.
- CA-01.4 Dada una asignación o retiro de rol, cuando se guarda, entonces la bitácora conserva actor, fecha, usuario afectado y rol anterior y nuevo sin almacenar contraseñas ni OTP.

### DoR específica

Matriz ADMIN, RECEPCION y DOCTOR acordada; reglas de alta, bloqueo y retiro de rol aclaradas; cuenta de ensayo disponible; casos de acceso permitido y denegado definidos.

### DoD específica

Asignación y retiro de permisos contrastados en API e interfaz; sesiones anteriores rechazadas tras cambio relevante; bitácora verificable con actor, fecha y cambio; pruebas y revisión enlazadas.

## HU-02 Registrar y buscar pacientes

Como Recepcionista, quiero registrar y buscar un paciente, para evitar duplicados y disponer de su información.

5 SP propuestos. Referencia intermedia: ficha, validaciones, unicidad por documento y tres búsquedas. Se mantiene 5 si la ficha y reglas quedan cerradas.

### Criterios

- CA-02.1 Dada Recepción autenticada y una ficha válida, cuando registra un documento nuevo, entonces el paciente se guarda y puede recuperarse en la clínica correspondiente.
- CA-02.2 Dado un documento existente, cuando se intenta registrar de nuevo, entonces el sistema advierte la coincidencia y no crea otro paciente; la API devuelve conflicto 409.
- CA-02.3 Dado un paciente de ensayo, cuando se busca por documento, nombre o teléfono con al menos dos caracteres, entonces aparece el registro correcto; una búsqueda sin coincidencias devuelve lista vacía.
- CA-02.4 Dadas dos solicitudes concurrentes con el mismo documento en la misma clínica, cuando se procesan, entonces existe un solo registro persistido y se informa el conflicto restante.
- CA-02.5 Dada una cuenta sin permiso de escritura, cuando intenta crear o editar pacientes, entonces se rechaza la operación; el aislamiento entre clínicas se verifica con datos ficticios.

### DoR específica

Ficha mínima y formatos acordados; definición de duplicado por documento dentro de la clínica confirmada; datos ficticios para las tres búsquedas; dependencia de autenticación disponible.

### DoD específica

Registro y persistencia comprobados en ensayo MySQL; búsqueda por documento, nombre y teléfono; duplicado secuencial y concurrente rechazado sin duplicar registros; acceso por rol y clínica contrastado.

## HU-12 Recuperar acceso mediante OTP

Como Usuario autorizado, quiero recuperar mi acceso mediante OTP, para restablecer credenciales de forma controlada.

8 SP propuestos. Propuesta de aumento de 5 a 8 por solicitud previa, expiración, intentos, consumo único, no enumeración, auditoría e invalidación de sesiones. No es una votación realizada ni un punto impuesto por el Charter.

### Criterios

- CA-12.1 Dada una cuenta activa, cuando solicita recuperación, entonces se crea un desafío para su destino y canal; la respuesta pública no distingue cuenta existente de inexistente.
- CA-12.2 Dado un OTP local solicitado, cuando transcurren diez minutos o se acumulan cinco errores, entonces no permite restablecer el acceso y se requiere una nueva solicitud.
- CA-12.3 Dado un desafío vigente y un código correcto, cuando se confirma una contraseña válida, entonces se restablece el acceso, se consume el desafío y un segundo uso se rechaza.
- CA-12.4 Dada una recuperación completada, cuando se usa un token previo, entonces se rechaza; el nuevo inicio de sesión usa la contraseña nueva.
- CA-12.5 Dada una solicitud, un rechazo por código o una recuperación completada, cuando ocurre el evento, entonces se registra la auditoría sin contraseña, OTP ni token; el fallo no revela existencia de cuenta.

### DoR específica

Canal académico local y datos de ensayo autorizados; vigencia de 10 minutos y cinco intentos acordados; contrato de respuesta genérica y eventos de auditoría definidos; dependencia de cuentas activas resuelta.

### DoD específica

Solicitud previa, expiración, cinco errores, uso único y no enumeración contrastados; contraseña nueva permite acceso y sesiones anteriores dejan de ser válidas; eventos auditados; proveedor local documentado como académico.

## Condiciones comunes

DoR: valor, reglas, criterios, datos de ensayo, dependencias, estimación y capacidad acordados.

DoD: integración, revisión, pruebas pertinentes y criterios aprobados, documentación actualizada y sin defectos críticos conocidos. Verificar ensayo MySQL. La conformidad del cliente se registra por separado.

## Sprint Backlog propuesto

| ID | HU | Tarea | Rol sugerido | Evidencia | Depende de |
|---|---|---|---|---|---|
| T01 | HU-01 | Acordar RBAC y reglas de usuario | Analista y Developers | Matriz validada | Ninguna |
| T02 | HU-01 | Verificar API, cambio de rol y revocación | Developers | CA-01.1 a CA-01.3 con resultados | T01 |
| T03 | HU-01 | Contrastar bitácora en MySQL e interfaz | Developers y QA | CA-01.4 con consulta y captura | T02 |
| T04 | HU-02 | Acordar ficha y regla de duplicados | Analista y PO | Ficha mínima aprobada | T01 |
| T05 | HU-02 | Verificar registro y búsquedas | Developers y QA | CA-02.1 y CA-02.3 | T04 |
| T06 | HU-02 | Probar duplicidad concurrente y permisos | Developers y QA | CA-02.2, CA-02.4 y CA-02.5 | T05 |
| T07 | HU-12 | Acordar canal, vigencia e intentos OTP | PO y Developers | Contrato de recuperación acordado | T01 |
| T08 | HU-12 | Contrastar desafío y no enumeración | Developers y QA | CA-12.1 a CA-12.3 | T07 |
| T09 | HU-12 | Probar sesiones y auditoría de recuperación | Developers y QA | CA-12.4 y CA-12.5 | T08 |
| T10 | Común | Ejecutar demostración integrada de ensayo | Developers y QA | Versión, casos, resultados y capturas | T03, T06, T09 |
| T11 | Común | Inspeccionar incremento y adaptar backlog | Scrum Team e interesados | Registro real de Review | T10 |
| T12 | Común | Acordar mejora y seguimiento | Scrum Team | Acción real de Retrospective | T11 |

Todas las tareas quedan por verificar. Personas asignadas, horas y fechas reales se registran en Excel. No se declaran completadas.

## Eventos propuestos

Daily Scrum: 15 minutos para Developers. Registrar fecha, asistentes, progreso hacia el objetivo, adaptación e impedimentos. No hay actas históricas aportadas.

Review: agenda sugerida de 45 minutos, con 5 de contexto, 25 de inspección, 10 de feedback y 5 de acuerdos. Registrar versión, resultados, feedback real y adaptación del backlog.

Retrospective: agenda sugerida de 30 minutos, con 5 de hechos, 10 de causas, 10 de mejora y 5 de seguimiento. Registrar acción, responsable y verificación acordados. No se atribuyen conclusiones al equipo.

## Contraste del Charter

### Selección de historias

HU-01, HU-02 y HU-12 alineadas con identidad, seguridad y pacientes. Conservar selección, condicionada a capacidad y preparación. Fuente: Charter 13.1 y 14.

### Estimación

18 SP preliminares frente a propuesta 21 SP del repositorio. Documentar 8 + 5 + 8 como propuesta, validar con Developers. Fuente: Charter 13.1 no contiene puntos.

### Calendario

Sprint 1 del 30/09 al 07/10/2026. Corte de esta revisión 22/10/2026. No atribuir cambios actuales al periodo original. Reprogramar solo por decisión registrada. Fuente: Charter 14.

### PO y aprobación

Representante del cliente sin confirmar y versión 1.1 pendiente de validación. Obtener designación y conformidad. El equipo académico no sustituye al PO sin delegación. Fuente: Charter 1, 13 y control de versiones.

### Línea base

Medición prevista sin acta ni resultados aportados. Mantener indicadores como metas. No afirmar reducción de tiempos ni duplicados. Fuente: Charter 7.1.

### Alcance ampliado

Repositorio incluye multiclínica, FINANZAS y módulos de sprints posteriores. Registrar diferencias mediante control de cambios. No contabilizarlas como nuevo Sprint 1. Fuente: README y docs/MULTICLINICA.md.

### Permisos de pacientes

Tabla indica Finanzas sin acceso a Pacientes; API permite search y get a FINANZAS. Aclarar si consulta financiera limitada es excepción aprobada o ajustar contrato y pruebas. No cambiar permisos sin decidir la regla. Fuente: MATRIZ-DE-PERMISOS.md y PatientController.java.

### Pruebas y aceptación

CI exitoso y pruebas existentes no acreditan UAT ni reuniones. Separar inspección de código, ejecución automatizada, ensayo integrado y conformidad. Fuente: SPRINT-1.md y GitHub Actions.

### Publicación

Pages sirve frontend y configuración pública sin backend HTTPS. La publicación estática no acredita operación de usuarios, pacientes u OTP. Fuente: pages.yml y deployment-config.js.

### Gobernanza del Charter

Emisión académica no equivale a aceptación del cliente. Conservar versión original y documentar adenda sin firma inventada. Fuente: Charter 19 y control de versiones.

## Guion de demostración pendiente de ejecutar

1. Identificar ensayo: Mostrar versión y entorno, usar cuentas y pacientes ficticios. Evidencia: Versión, fecha y responsable.
2. Usuarios: ADMIN crea Recepción, asigna rol y consulta bitácora. Evidencia: CA-01.1 y CA-01.4.
3. Autorización: Intentar administración con Recepción por interfaz y API directa. Evidencia: CA-01.3.
4. Pacientes: Registrar paciente ficticio y buscar por documento, nombre y teléfono. Evidencia: CA-02.1 y CA-02.3.
5. Duplicados: Repetir documento y ejecutar dos solicitudes concurrentes controladas. Evidencia: CA-02.2 y CA-02.4.
6. Cambio de acceso: Cambiar rol o bloquear cuenta y probar token anterior. Evidencia: CA-01.2.
7. Recuperación: Comparar respuesta genérica, probar código correcto, expiración, cinco errores y reutilización en desafíos separados. Evidencia: CA-12.1 a CA-12.3.
8. Sesiones y cierre: Probar token previo, acceso nuevo y auditoría. Registrar lo fallido y feedback del PO. Evidencia: CA-12.4 y CA-12.5.

## Publicación y límites

Base inspeccionada: 235502c738ca3e0256710a901fc7e4b5a02326cb. CI de validación 39872400142 y Pages 39872400201 exitosos. La raíz y deployment-config.js respondieron 200; apiBaseUrl está vacío. No se validaron interacciones en navegador ni backend público. Este cambio incorpora la página académica y los tres entregables al repositorio para su despliegue por el flujo existente de Pages. Los documentos conservan el corte de la revisión previa, cuando la conexión todavía no tenía escritura.

Los documentos descargables están en `frontend/public/sprint-1/entregables`. Tras desplegar este cambio se consultan en `/clinica-cardenas/sprint-1/`. Esta página no sustituye la aplicación ni configura backend.

## Fuentes

- Charter aportado por el usuario: secciones 1, 7.1, 9, 13.1, 13.2, 14, 15, 18 y 19.
- [Repositorio base](https://github.com/DJuanParedes/clinica-cardenas/tree/235502c738ca3e0256710a901fc7e4b5a02326cb).
- [Guía de Scrum 2020](https://scrumguides.org/docs/scrumguide/v2020/2020-Scrum-Guide-Spanish-Latin-South-American.pdf).
