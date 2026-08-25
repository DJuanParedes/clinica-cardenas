# Matriz de permisos por rol — V4.9.17

Leyenda: **C** consultar, **E** crear/editar, **A** cambiar estado/aprobar/ejecutar acción operativa, **—** sin acceso. Cuando una acción tiene una regla adicional, se aclara en la nota.

| Módulo | Administrador | Doctor | Recepción | Finanzas |
|---|---|---|---|---|
| Dashboard y reportes | C | C | C | C |
| Pacientes | C/E/A | C | C/E/A | — |
| Paciente 360 | C | C | C | C |
| Agenda y citas | C/E/A | C | C/E/A | — |
| Agenda diaria | C/E/A | C | C/E/A | — |
| Tratamientos y proformas | C/E | C/E | C/E | C |
| Atenciones clínicas | C/E/A | C/E/A propias | — | C |
| Mapa de sillones | C | — | C | C |
| Doctores y sillones | C/E sillones | C | — | — |
| Emergencias | C/E/A | C/E/A | — | — |
| Odontograma | C/E | C/E | — | — |
| Presupuestos | C | C/E/A/versionar | — | C |
| Laboratorio | C/E/A | C/E/A | C/A | C |
| Seguimientos | C/E/A | C/E/A | — | — |
| Membresías | C/E/A | — | — | C/E/A |
| Comisiones | C/E reglas + liquidación | C propias | — | C/A liquidación |
| Inventario | C/E/A | C/A según operación | C/A según operación | C/E/A según operación |
| Esterilización | C/E/A | C | — | — |
| Documentos clínicos | C/E/A | C/E/A | — | — |
| Reclamos y solicitudes | C/E/A | — | C/E/A | — |
| Análisis por sillón / metas | C/E/A | — | — | C/E/A |
| Satisfacción | C | C | C/A seguimiento | C |
| Usuarios y roles | C/E/A | — | — | — |
| Tratamientos y reglas de comisión | C/E/A | — | — | — |
| Auditoría y respaldos | C/E/A | — | — | C |
| Copias e importación Excel | C/E/A | — | — | — |
| Empleados | C/E/A | — | — | — |
| Equipos | C/E/A | C | — | — |
| Seguridad / monitoreo / integraciones | C/E/A | — | — | — |
| Ambientes / calidad / migración / configuración | C/E/A | — | — | — |

## Reglas que prevalecen sobre la tabla

- **Recepción** usa una lista blanca estricta: no accede a Atenciones clínicas, Emergencias, Presupuestos, Documentos, Membresías, Seguimientos, Esterilización, Tratamientos clínicos, Finanzas, Usuarios, Recursos de agenda, Análisis por sillón ni Gobierno.
- **Doctor** puede consultar agenda, pero el alta administrativa de doctores se realiza desde **Usuarios y roles**. Un doctor trabaja sobre su propia atención clínica cuando el backend aplica propiedad por correo.
- **Finanzas** puede generar y cambiar el estado de liquidaciones de comisión, pero **no modifica porcentajes**. Las reglas predeterminadas y las excepciones doctor + tratamiento son exclusivas de ADMIN.
- **Proformas:** ADMIN/DOCTOR/RECEPCIÓN pueden gestionar el plan; FINANZAS lo consulta. Registrar cobros se realiza en Caja y pagos conforme al rol.
- La protección se aplica en navegación Angular y nuevamente en controladores Spring Boot; ocultar un botón no sustituye la autorización del backend.
