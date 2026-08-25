# Manual rápido por rol — V4.9.17

## Administrador

1. En una instalación nueva, activa la cuenta principal mediante **Olvidé mi contraseña → OTP → nueva contraseña**.
2. En **Usuarios y roles**, crea las cuentas reales con nombre, correo, DNI, teléfono y canal de recuperación. No crees contraseñas manuales.
3. Si el usuario es DOCTOR, completa especialidad, sede y horario; el sistema sincroniza su perfil de Agenda.
4. En **Doctores y sillones**, administra sillones y su estado operativo; el alta de doctores se hace exclusivamente en Usuarios y roles.
5. En **Tratamientos y comisiones**, define el porcentaje predeterminado de cada doctor y solo las excepciones por tratamiento necesarias.
6. Supervisa pacientes, agenda, proformas, clínica, caja, inventario, reportes, auditoría y respaldos.
7. En **Copias e importación**, genera respaldos globales/individuales y usa plantillas Excel para migraciones controladas. Las cuentas de usuario son solo exportables.

## Doctor

1. Consulta pacientes y su agenda asignada.
2. Cuando Recepción/Administración ya confirmó **Llegó**, continúa la atención clínica; no cancela ni reprograma una cita después de la llegada.
3. Registra historia clínica, odontograma, tratamientos clínicos, recetas, documentos y órdenes de laboratorio según sus permisos.
4. Crea/versiona presupuestos y trabaja sobre tratamientos aceptados vinculados a proforma y sesiones.
5. Consulta sus comisiones; no modifica porcentajes ni reglas financieras.
6. No crea pacientes, cuentas de usuario, pagos ni configuraciones administrativas.

## Recepción

1. Revisa el panel: pacientes, atenciones de hoy por doctor y sillones disponibles.
2. Registra/actualiza pacientes y programa citas desde **Agenda y citas** o **Agenda diaria**.
3. Ante una cita pendiente decide asistencia: **Llegó** o **No asistió**. Si llegó, ya no aparecen cancelar/reprogramar; si no asistió, resuelve con **Reprogramar** o **Cerrar sin reprogramar**.
4. Gestiona solicitudes web, reclamos, laboratorio dentro de sus acciones permitidas, satisfacción, inventario operativo y reportes.
5. En **Tratamientos y proformas**, gestiona planes aceptados conforme al flujo; en **Caja y pagos** registra cobros y gastos permitidos.
6. No accede a Atenciones clínicas, Emergencias, Presupuestos, Documentos, Membresías, Seguimientos, Esterilización, Finanzas, Usuarios, Análisis por sillón ni Gobierno.

## Finanzas

1. Gestiona Caja/pagos/gastos y consulta saldos pendientes.
2. Revisa membresías, producción, cobranza, cartera, medios de pago y reportes financieros.
3. Consulta proformas/atenciones necesarias para la conciliación financiera, sin modificar información clínica.
4. Genera liquidaciones de comisión y cambia su estado dentro de las transiciones permitidas.
5. **No modifica porcentajes de comisión**: el porcentaje predeterminado por doctor y las excepciones por tratamiento los define ADMIN.
6. Consulta Gobierno/respaldos y análisis por sillón dentro de sus permisos, pero no administra usuarios ni configuración clínica.
