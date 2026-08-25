import { ModuleConfig, ModuleField, NavItem } from './models';

export const PRIMARY_NAV: NavItem[] = [
  { label:'Panel principal', icon:'▦', route:'/app/dashboard', roles:['ADMIN','DOCTOR','RECEPCION','FINANZAS'], group:'principal' },
  { label:'Recepción de hoy', icon:'☑', route:'/app/recepcion-hoy', roles:['ADMIN','RECEPCION'], group:'principal' },
  { label:'Pacientes', icon:'👥', route:'/app/pacientes', roles:['ADMIN','DOCTOR','RECEPCION'], group:'principal' },
  { label:'Paciente 360', icon:'◎', route:'/app/paciente-360', roles:['ADMIN','DOCTOR','RECEPCION','FINANZAS'], group:'principal' },
  { label:'Agenda y citas', icon:'📅', route:'/app/citas', roles:['ADMIN','DOCTOR','RECEPCION'], group:'principal' },
  { label:'Agenda diaria por sillón', icon:'▥', route:'/app/agenda-diaria', roles:['ADMIN','DOCTOR','RECEPCION'], group:'principal' },
  { label:'Tratamientos y proformas', icon:'🧾', route:'/app/tratamientos-proformas', roles:['ADMIN','DOCTOR','RECEPCION','FINANZAS'], group:'principal' },
  { label:'Atenciones clínicas', icon:'✚', route:'/app/atenciones', roles:['ADMIN','DOCTOR','FINANZAS'], group:'principal' },
  { label:'Mapa de sillones', icon:'🗺', route:'/app/mapa-sillones', roles:['ADMIN','RECEPCION','FINANZAS'], group:'principal' },
  { label:'Doctores y sillones', icon:'🪑', route:'/app/recursos-agenda', roles:['ADMIN','DOCTOR'], group:'principal' },
  { label:'Emergencias', icon:'🚨', route:'/app/emergencias', roles:['ADMIN','DOCTOR'], group:'principal' },
  { label:'Odontograma', icon:'🦷', route:'/app/odontograma', roles:['ADMIN','DOCTOR'], group:'clinica' },
  { label:'Presupuestos', icon:'🧾', route:'/app/presupuestos', roles:['ADMIN','DOCTOR','FINANZAS'], group:'clinica' },
  { label:'Inventario', icon:'📦', route:'/app/inventario', roles:['ADMIN','DOCTOR','RECEPCION','FINANZAS'], group:'operaciones' },
  { label:'Documentos', icon:'📁', route:'/app/documentos', roles:['ADMIN','DOCTOR'], group:'operaciones' },
  { label:'Caja y pagos', icon:'💳', route:'/app/caja-pagos', roles:['ADMIN','RECEPCION','FINANZAS'], group:'operaciones' },
  { label:'Finanzas', icon:'📊', route:'/app/finanzas', roles:['ADMIN','FINANZAS'], group:'operaciones' },
  { label:'Análisis por sillón', icon:'📈', route:'/app/analisis-sillones', roles:['ADMIN','FINANZAS'], group:'operaciones' },
  { label:'Satisfacción', icon:'★', route:'/app/satisfaccion', roles:['ADMIN','DOCTOR','RECEPCION','FINANZAS'], group:'operaciones' },
  { label:'Tratamientos y comisiones', icon:'⚙️', route:'/app/configuracion-tratamientos-comisiones', roles:['ADMIN'], group:'administracion' },
  { label:'Usuarios y roles', icon:'👤', route:'/app/usuarios', roles:['ADMIN'], group:'administracion' },
  { label:'Clínicas y marcas', icon:'🏥', route:'/app/clinicas', roles:['ADMIN'], group:'administracion' },
  { label:'Auditoría y respaldos', icon:'🔐', route:'/app/gobierno', roles:['ADMIN','FINANZAS'], group:'administracion' },
  { label:'Copias e importación', icon:'🗃', route:'/app/copias-importacion', roles:['ADMIN'], group:'administracion' },
  { label:'Reportes', icon:'📊', route:'/app/reportes', roles:['ADMIN','DOCTOR','RECEPCION','FINANZAS'], group:'administracion' }
];

const commonRecordFields: ModuleField[] = [
  { key:'title', label:'Título', required:true, list:true },
  { key:'subject', label:'Paciente / asunto', list:true },
  { key:'detail', label:'Detalle', type:'textarea' as const, required:true, list:true },
  { key:'responsible', label:'Responsable', list:true },
  { key:'eventDate', label:'Fecha', type:'date' as const, list:true },
  { key:'amount', label:'Monto', type:'number' as const, list:true }
];


const clinicalHistoryFields: ModuleField[] = [
  {key:'patientName',label:'Paciente',required:true,list:true},{key:'consultationReason',label:'Motivo de consulta',required:true,list:true},
  {key:'currentIllness',label:'Enfermedad actual',type:'textarea',required:true},{key:'personalHistory',label:'Antecedentes personales',type:'textarea'},
  {key:'familyHistory',label:'Antecedentes familiares',type:'textarea'},{key:'allergies',label:'Alergias',type:'textarea',list:true},
  {key:'medications',label:'Medicamentos actuales',type:'textarea'},{key:'diagnosis',label:'Diagnóstico',type:'textarea',required:true,list:true},
  {key:'responsible',label:'Doctor responsable',required:true,list:true},{key:'eventDate',label:'Fecha de evaluación',type:'date',required:true,list:true}
];
const treatmentFields: ModuleField[] = [
  {key:'patientName',label:'Paciente',required:true,list:true},{key:'diagnosis',label:'Diagnóstico relacionado',type:'textarea',required:true},
  {key:'procedure',label:'Procedimiento',required:true,list:true},{key:'toothPieces',label:'Piezas dentales',list:true},
  {key:'sessions',label:'Número de sesiones',type:'number',required:true,list:true},{key:'amount',label:'Costo previsto',type:'number',list:true},
  {key:'responsible',label:'Doctor responsable',required:true,list:true},{key:'eventDate',label:'Fecha de inicio',type:'date',list:true},
  {key:'detail',label:'Indicaciones y observaciones',type:'textarea'}
];
const prescriptionFields: ModuleField[] = [
  {key:'patientName',label:'Paciente',required:true,list:true},{key:'medication',label:'Medicamento',required:true,list:true},
  {key:'concentration',label:'Concentración',required:true,list:true},{key:'dose',label:'Dosis',required:true,list:true},
  {key:'frequency',label:'Frecuencia',required:true,list:true},{key:'duration',label:'Duración',required:true},
  {key:'administrationRoute',label:'Vía de administración',required:true},{key:'warnings',label:'Advertencias e indicaciones',type:'textarea'},
  {key:'responsible',label:'Doctor responsable',required:true,list:true},{key:'eventDate',label:'Fecha de emisión',type:'date',required:true,list:true}
];
const sterilizationFields: ModuleField[] = [
  {key:'cycleCode',label:'Código de ciclo',required:true,list:true},{key:'equipment',label:'Autoclave / equipo',required:true,list:true},
  {key:'packageCode',label:'Paquete o bandeja',required:true,list:true},{key:'chemicalIndicator',label:'Indicador químico',type:'select',options:['CONFORME','NO CONFORME'],required:true,list:true},
  {key:'biologicalIndicator',label:'Indicador biológico',type:'select',options:['NO APLICA','CONFORME','NO CONFORME'],list:true},
  {key:'responsible',label:'Responsable',required:true,list:true},{key:'eventDate',label:'Fecha del ciclo',type:'date',required:true,list:true},
  {key:'expiryDate',label:'Vencimiento del paquete',type:'date'},{key:'detail',label:'Observaciones y trazabilidad',type:'textarea'}
];
const securityFields: ModuleField[] = [
  {key:'eventType',label:'Tipo de evento',type:'select',options:['LOGIN','PERMISO','EXPORTACIÓN','CAMBIO DE PRECIO','ANULACIÓN','INCIDENTE'],required:true,list:true},
  {key:'userName',label:'Usuario',list:true},{key:'ipAddress',label:'Dirección IP',list:true},{key:'action',label:'Acción',required:true,list:true},
  {key:'severity',label:'Severidad',type:'select',options:['BAJA','MEDIA','ALTA','CRÍTICA'],required:true,list:true},
  {key:'detail',label:'Detalle y evidencia',type:'textarea',required:true},{key:'responsible',label:'Responsable de revisión',list:true},{key:'eventDate',label:'Fecha',type:'date',required:true,list:true}
];
const monitoringFields: ModuleField[] = [
  {key:'serviceName',label:'Servicio',required:true,list:true},{key:'endpoint',label:'Endpoint / recurso',list:true},
  {key:'responseTimeMs',label:'Respuesta (ms)',type:'number',list:true},{key:'cpuPercent',label:'CPU %',type:'number'},
  {key:'memoryPercent',label:'Memoria %',type:'number'},{key:'diskPercent',label:'Disco %',type:'number'},
  {key:'detail',label:'Diagnóstico técnico',type:'textarea'},{key:'responsible',label:'Responsable',list:true},{key:'eventDate',label:'Fecha',type:'date',list:true}
];
const backupFields: ModuleField[] = [
  {key:'backupName',label:'Nombre del respaldo',required:true,list:true},{key:'scope',label:'Alcance',required:true,list:true},
  {key:'location',label:'Ubicación segura',required:true,list:true},{key:'sizeMb',label:'Tamaño MB',type:'number',list:true},
  {key:'checksum',label:'Checksum',list:true},{key:'responsible',label:'Responsable',required:true,list:true},
  {key:'eventDate',label:'Fecha',type:'date',required:true,list:true},{key:'detail',label:'Prueba de restauración / observaciones',type:'textarea'}
];
const integrationFields: ModuleField[] = [
  {key:'serviceName',label:'Integración',required:true,list:true},{key:'provider',label:'Proveedor',required:true,list:true},
  {key:'environment',label:'Ambiente',type:'select',options:['LOCAL','PRUEBAS','PRODUCCIÓN'],required:true,list:true},
  {key:'endpoint',label:'Endpoint',list:true},{key:'credentialAlias',label:'Alias del secreto',list:true},
  {key:'lastTest',label:'Última prueba',type:'date',list:true},{key:'responsible',label:'Responsable',list:true},{key:'detail',label:'Configuración no sensible',type:'textarea'}
];
const environmentFields: ModuleField[] = [
  {key:'name',label:'Nombre del ambiente',required:true,list:true},{key:'environmentType',label:'Tipo',type:'select',options:['DESARROLLO','PRUEBAS','LOCAL','PRODUCCIÓN'],required:true,list:true},
  {key:'url',label:'URL',list:true},{key:'version',label:'Versión desplegada',list:true},{key:'lastDeployment',label:'Último despliegue',type:'date',list:true},
  {key:'responsible',label:'Responsable',list:true},{key:'detail',label:'Variables, infraestructura y observaciones',type:'textarea'}
];
const qualityFields: ModuleField[] = [
  {key:'testName',label:'Prueba / criterio',required:true,list:true},{key:'module',label:'Módulo',required:true,list:true},
  {key:'testType',label:'Tipo',type:'select',options:['UNITARIA','INTEGRACIÓN','END TO END','SEGURIDAD','CARGA','USABILIDAD'],required:true,list:true},
  {key:'expectedResult',label:'Resultado esperado',type:'textarea',required:true},{key:'actualResult',label:'Resultado obtenido',type:'textarea'},
  {key:'evidence',label:'Evidencia / referencia',list:true},{key:'responsible',label:'Responsable',list:true},{key:'eventDate',label:'Fecha',type:'date',list:true}
];
const migrationFields: ModuleField[] = [
  {key:'sourceName',label:'Origen / archivo',required:true,list:true},{key:'fileType',label:'Formato',type:'select',options:['EXCEL','CSV','SISTEMA ANTERIOR','OTRO'],required:true,list:true},
  {key:'totalRows',label:'Filas totales',type:'number',list:true},{key:'validRows',label:'Filas válidas',type:'number',list:true},
  {key:'errorRows',label:'Filas con error',type:'number',list:true},{key:'duplicateRows',label:'Duplicados',type:'number',list:true},
  {key:'responsible',label:'Responsable',list:true},{key:'eventDate',label:'Fecha',type:'date',list:true},{key:'detail',label:'Errores y decisiones',type:'textarea'}
];
const settingFields: ModuleField[] = [
  {key:'name',label:'Parámetro',required:true,list:true},{key:'category',label:'Categoría',required:true,list:true},
  {key:'value',label:'Valor',required:true,list:true},{key:'branch',label:'Sede',list:true},{key:'effectiveDate',label:'Vigencia',type:'date',list:true},
  {key:'responsible',label:'Responsable',list:true},{key:'detail',label:'Descripción y alcance',type:'textarea'}
];

export const MODULES: ModuleConfig[] = [
  {
    label:'Historia clínica', icon:'🩺', route:'/app/historia-clinica', roles:['ADMIN','DOCTOR'], group:'clinica',
    title:'Historia clínica digital', subtitle:'Anamnesis, antecedentes, diagnósticos, evolución y trazabilidad clínica.', eyebrow:'Atención clínica',
    endpoint:'/modules/clinical-history', createRoles:['ADMIN','DOCTOR'], editRoles:['ADMIN','DOCTOR'], statusRoles:['ADMIN','DOCTOR'],
    fields:clinicalHistoryFields, statusField:'status', statusOptions:['ACTIVA','EN REVISIÓN','CERRADA']
  },
  {
    label:'Tratamientos', icon:'✚', route:'/app/tratamientos', roles:['ADMIN','DOCTOR','FINANZAS'], group:'clinica',
    title:'Planes de tratamiento', subtitle:'Procedimientos, piezas, sesiones, responsables y estado de ejecución.', eyebrow:'Planificación clínica',
    endpoint:'/modules/treatments', createRoles:['ADMIN','DOCTOR'], editRoles:['ADMIN','DOCTOR'], statusRoles:['ADMIN','DOCTOR'],
    fields:treatmentFields, statusField:'status', statusOptions:['PROPUESTO','ACEPTADO','EN PROCESO','COMPLETADO','CANCELADO']
  },
  {
    label:'Recetas', icon:'💊', route:'/app/recetas', roles:['ADMIN','DOCTOR'], group:'clinica',
    title:'Recetas e indicaciones', subtitle:'Medicamentos, dosis, frecuencia, duración, alergias y versiones.', eyebrow:'Documentación clínica',
    endpoint:'/modules/prescriptions', createRoles:['ADMIN','DOCTOR'], editRoles:['ADMIN','DOCTOR'], statusRoles:['ADMIN','DOCTOR'],
    fields:prescriptionFields, statusField:'status', statusOptions:['VIGENTE','ANULADA','REEMPLAZADA']
  },
  {
    label:'Seguimientos', icon:'☎', route:'/app/seguimientos', roles:['ADMIN','DOCTOR'], group:'clinica',
    title:'Seguimientos posteriores', subtitle:'Controles después de cirugías, endodoncias y otros tratamientos.', eyebrow:'Continuidad del cuidado',
    endpoint:'/operations/follow-ups', createRoles:['ADMIN','DOCTOR'], editRoles:['ADMIN','DOCTOR'], statusRoles:['ADMIN','DOCTOR'],
    fields:[
      {key:'patientName',label:'Paciente',required:true,list:true},{key:'treatment',label:'Tratamiento',required:true,list:true},
      {key:'doctorName',label:'Doctor',required:true,list:true},{key:'treatmentDate',label:'Fecha tratamiento',type:'date',list:true},
      {key:'followUpDate',label:'Fecha seguimiento',type:'date',required:true,list:true},{key:'symptoms',label:'Síntomas',type:'textarea'},
      {key:'recommendations',label:'Recomendaciones',type:'textarea'}
    ], statusField:'followUpStatus', statusOptions:['PENDIENTE','CONTACTADO','REQUIERE EVALUACIÓN','CITA PROGRAMADA','FINALIZADO']
  },
  {
    label:'Laboratorio dental', icon:'⚗', route:'/app/laboratorio', roles:['ADMIN','DOCTOR','RECEPCION','FINANZAS'], group:'operaciones',
    title:'Laboratorio dental', subtitle:'Órdenes, materiales, color, fechas de entrega, costos, correcciones e instalación.', eyebrow:'Coordinación externa',
    endpoint:'/lab-orders', createRoles:['ADMIN','DOCTOR'], editRoles:['ADMIN','DOCTOR','RECEPCION'], statusRoles:['ADMIN','DOCTOR','RECEPCION'],
    fields:[
      {key:'patientName',label:'Paciente',required:true,list:true},{key:'doctorName',label:'Doctor',required:true,list:true},
      {key:'laboratory',label:'Laboratorio',required:true,list:true},{key:'workType',label:'Tipo de trabajo',required:true,list:true},
      {key:'toothPieces',label:'Piezas / arcada'},{key:'material',label:'Material',list:true},{key:'shade',label:'Color'},
      {key:'sentDate',label:'Fecha de envío',type:'date'},{key:'expectedDate',label:'Entrega esperada',type:'date',list:true},
      {key:'receivedDate',label:'Fecha recibida',type:'date'},{key:'cost',label:'Costo',type:'number',list:true},{key:'notes',label:'Observaciones',type:'textarea'}
    ], statusField:'labStatus', statusOptions:['SOLICITUD CREADA','ENVIADO','EN FABRICACIÓN','LISTO PARA ENTREGA','RECIBIDO','REQUIERE CORRECCIÓN','INSTALADO','FINALIZADO']
  },
  {
    label:'Paquetes y membresías', icon:'🎫', route:'/app/membresias', roles:['ADMIN','FINANZAS'], group:'operaciones',
    title:'Paquetes y membresías', subtitle:'Planes, sesiones disponibles, beneficiarios, vigencia, pagos y renovaciones.', eyebrow:'Servicios y fidelización',
    endpoint:'/finance/memberships', createRoles:['ADMIN','FINANZAS'], editRoles:['ADMIN','FINANZAS'], statusRoles:['ADMIN','FINANZAS'],
    fields:[
      {key:'patientName',label:'Paciente / familia',required:true,list:true},{key:'planName',label:'Plan',required:true,list:true},
      {key:'totalSessions',label:'Sesiones totales',type:'number',required:true},{key:'usedSessions',label:'Sesiones usadas',type:'number',list:true},
      {key:'price',label:'Precio',type:'number',required:true,list:true},{key:'paidAmount',label:'Monto pagado',type:'number',list:true},
      {key:'startDate',label:'Inicio',type:'date'},{key:'endDate',label:'Vencimiento',type:'date',list:true}
    ], statusField:'membershipStatus', statusOptions:['ACTIVA','SUSPENDIDA','VENCIDA','CANCELADA','COMPLETADA']
  },
  {
    label:'Comisiones de doctores', icon:'％', route:'/app/comisiones', roles:['ADMIN','FINANZAS','DOCTOR'], group:'operaciones',
    title:'Comisiones y liquidaciones de doctores', subtitle:'Liquidaciones generadas desde Finanzas con la fórmula: (proformas acordadas - laboratorio) × porcentaje configurado por el Administrador.', eyebrow:'Control financiero',
    endpoint:'/finance/commissions', createRoles:[], editRoles:[], statusRoles:['ADMIN','FINANZAS'],
    fields:[
      {key:'doctorName',label:'Doctor',required:true,list:true},{key:'period',label:'Periodo',required:true,list:true},
      {key:'grossProduction',label:'Proformas acordadas',type:'number',list:true},{key:'collectedProduction',label:'Cobranza informativa',type:'number',list:true},
      {key:'commissionRate',label:'Comisión % definida por Admin',type:'number',list:true},{key:'laboratoryCosts',label:'Costos laboratorio',type:'number',list:true},
      {key:'finalAmount',label:'Pago final',type:'number',list:true}
    ], statusField:'paymentStatus', statusOptions:['CALCULADO','EN REVISIÓN','APROBADO','PAGADO','OBSERVADO']
  },
  {
    label:'Esterilización', icon:'♨', route:'/app/esterilizacion', roles:['ADMIN','DOCTOR'], group:'operaciones',
    title:'Esterilización y bioseguridad', subtitle:'Ciclos de autoclave, paquetes, indicadores y trazabilidad por atención.', eyebrow:'Bioseguridad',
    endpoint:'/modules/sterilization', createRoles:['ADMIN'], editRoles:['ADMIN'], statusRoles:['ADMIN'],
    fields:sterilizationFields, statusField:'status', statusOptions:['PENDIENTE','EN PROCESO','CONFORME','OBSERVADO','RECHAZADO']
  },
  {
    label:'Solicitudes web', icon:'🌐', route:'/app/solicitudes-web', roles:['ADMIN','RECEPCION'], group:'principal',
    title:'Solicitudes recibidas desde la web', subtitle:'Evaluaciones, emergencias y consultas enviadas desde el portal público.', eyebrow:'Captación y atención',
    endpoint:'/inquiries', createRoles:['ADMIN','RECEPCION'], editRoles:['ADMIN','RECEPCION'], statusRoles:['ADMIN','RECEPCION'],
    fields:[
      {key:'fullName',label:'Nombre completo',required:true,list:true},{key:'phone',label:'Teléfono',required:true,list:true},
      {key:'specialty',label:'Especialidad',required:true,list:true},{key:'message',label:'Mensaje',type:'textarea',list:true},
      {key:'assignedTo',label:'Responsable',list:true},{key:'createdAt',label:'Fecha de solicitud',type:'datetime-local',list:true}
    ], statusField:'status', statusOptions:['NUEVA','CONTACTADA','CITA PROGRAMADA','ATENDIDA','DESCARTADA']
  },
  {
    label:'Reclamos y satisfacción', icon:'💬', route:'/app/reclamos', roles:['ADMIN','RECEPCION'], group:'administracion',
    title:'Reclamos y satisfacción', subtitle:'Quejas, sugerencias, responsables, vencimientos y acciones correctivas.', eyebrow:'Experiencia del paciente',
    endpoint:'/operations/complaints', createRoles:['ADMIN','RECEPCION'], editRoles:['ADMIN','RECEPCION'], statusRoles:['ADMIN','RECEPCION'],
    fields:[
      {key:'type',label:'Tipo',type:'select',options:['RECLAMO','QUEJA','SUGERENCIA','FELICITACIÓN'],required:true,list:true},
      {key:'patientName',label:'Paciente',required:true,list:true},{key:'area',label:'Área',required:true,list:true},
      {key:'description',label:'Descripción',type:'textarea',required:true,list:true},{key:'createdAt',label:'Fecha',type:'date'},
      {key:'dueDate',label:'Fecha límite',type:'date',list:true},{key:'responsible',label:'Responsable',list:true},{key:'response',label:'Respuesta',type:'textarea'}
    ], statusField:'complaintStatus', statusOptions:['REGISTRADO','EN PROCESO','RESPONDIDO','CERRADO']
  },
  {
    label:'Empleados', icon:'🪪', route:'/app/empleados', roles:['ADMIN'], group:'administracion',
    title:'Gestión de empleados', subtitle:'Contratos, turnos, asistencia, documentos, capacitaciones y vencimientos.', eyebrow:'Talento humano',
    endpoint:'/operations/employees', createRoles:['ADMIN'], editRoles:['ADMIN'], statusRoles:['ADMIN'],
    fields:[
      {key:'fullName',label:'Nombre completo',required:true,list:true},{key:'position',label:'Puesto',required:true,list:true},
      {key:'area',label:'Área',required:true,list:true},{key:'branch',label:'Sede',required:true,list:true},
      {key:'shift',label:'Turno',list:true},{key:'contractEnd',label:'Fin de contrato',type:'date',list:true}
    ], statusField:'employeeStatus', statusOptions:['ACTIVO','VACACIONES','SUSPENDIDO','CESADO']
  },
  {
    label:'Equipos y mantenimiento', icon:'🛠', route:'/app/equipos', roles:['ADMIN','DOCTOR'], group:'administracion',
    title:'Equipos y mantenimiento', subtitle:'Sillones, autoclaves, compresores, garantías, fallas y mantenimiento preventivo.', eyebrow:'Infraestructura clínica',
    endpoint:'/operations/equipment', createRoles:['ADMIN'], editRoles:['ADMIN'], statusRoles:['ADMIN'],
    fields:[
      {key:'name',label:'Equipo',required:true,list:true},{key:'brand',label:'Marca',list:true},{key:'model',label:'Modelo',list:true},
      {key:'serialNumber',label:'Serie',list:true},{key:'location',label:'Ubicación',required:true,list:true},
      {key:'lastMaintenance',label:'Último mantenimiento',type:'date'},{key:'nextMaintenance',label:'Próximo mantenimiento',type:'date',list:true}
    ], statusField:'equipmentStatus', statusOptions:['OPERATIVO','EN MANTENIMIENTO','FUERA DE SERVICIO','DADO DE BAJA']
  },
  {
    label:'Seguridad y auditoría', icon:'🔐', route:'/app/seguridad', roles:['ADMIN'], group:'administracion',
    title:'Seguridad, privacidad y auditoría', subtitle:'Accesos, permisos, sesiones, exportaciones y eventos sensibles.', eyebrow:'Gobierno de datos',
    endpoint:'/modules/security', createRoles:['ADMIN'], editRoles:['ADMIN'], statusRoles:['ADMIN'], fields:securityFields,
    statusField:'status', statusOptions:['PENDIENTE','REVISADO','MITIGADO','CERRADO']
  },
  {
    label:'Monitoreo técnico', icon:'🖥', route:'/app/monitoreo', roles:['ADMIN'], group:'administracion',
    title:'Monitoreo técnico', subtitle:'Disponibilidad, base de datos, errores, respaldos y alertas.', eyebrow:'Operación en internet',
    endpoint:'/modules/monitoring', createRoles:['ADMIN'], editRoles:['ADMIN'], statusRoles:['ADMIN'], fields:monitoringFields,
    statusField:'status', statusOptions:['SALUDABLE','ADVERTENCIA','CRÍTICO','RESUELTO']
  },
  {
    label:'Respaldos y recuperación', icon:'⟳', route:'/app/respaldos', roles:['ADMIN'], group:'administracion',
    title:'Respaldos y recuperación', subtitle:'Copias automáticas, restauración, continuidad y registro de incidentes.', eyebrow:'Continuidad operativa',
    endpoint:'/modules/backups', createRoles:['ADMIN'], editRoles:['ADMIN'], statusRoles:['ADMIN'], fields:backupFields,
    statusField:'status', statusOptions:['PROGRAMADO','EN PROCESO','COMPLETADO','FALLIDO','RESTAURADO']
  },
  {
    label:'Integraciones', icon:'🔗', route:'/app/integraciones', roles:['ADMIN'], group:'administracion',
    title:'Integraciones externas', subtitle:'Correo, WhatsApp, pagos, calendario, firma y servicios en la nube.', eyebrow:'Conectividad',
    endpoint:'/modules/integrations', createRoles:['ADMIN'], editRoles:['ADMIN'], statusRoles:['ADMIN'], fields:integrationFields,
    statusField:'status', statusOptions:['BORRADOR','CONFIGURADO','EN PRUEBAS','ACTIVO','SUSPENDIDO']
  },
  {
    label:'Ambientes y despliegue', icon:'☁', route:'/app/ambientes', roles:['ADMIN'], group:'administracion',
    title:'Ambientes y despliegue', subtitle:'Desarrollo, pruebas, entorno local y producción con configuración independiente.', eyebrow:'Operación en internet',
    endpoint:'/modules/environments', createRoles:['ADMIN'], editRoles:['ADMIN'], statusRoles:['ADMIN'], fields:environmentFields,
    statusField:'status', statusOptions:['ACTIVO','DETENIDO','EN DESPLIEGUE','DEGRADADO']
  },
  {
    label:'Calidad y documentación', icon:'✓', route:'/app/calidad', roles:['ADMIN'], group:'administracion',
    title:'Calidad, pruebas y documentación', subtitle:'Pruebas, manuales, evidencias, incidencias y criterios de aceptación.', eyebrow:'Control de calidad',
    endpoint:'/modules/quality', createRoles:['ADMIN'], editRoles:['ADMIN'], statusRoles:['ADMIN'], fields:qualityFields,
    statusField:'status', statusOptions:['PENDIENTE','EN EJECUCIÓN','APROBADO','OBSERVADO','CERRADO']
  },
  {
    label:'Migración de datos', icon:'⇄', route:'/app/migracion', roles:['ADMIN'], group:'administracion',
    title:'Migración de datos', subtitle:'Importación desde Excel, validación, duplicados, vista previa y errores.', eyebrow:'Carga inicial',
    endpoint:'/modules/migration', createRoles:['ADMIN'], editRoles:['ADMIN'], statusRoles:['ADMIN'], fields:migrationFields,
    statusField:'status', statusOptions:['BORRADOR','EN REVISIÓN','VALIDADO','IMPORTADO','OBSERVADO']
  },
  {
    label:'Configuración', icon:'⚙', route:'/app/configuracion', roles:['ADMIN'], group:'administracion',
    title:'Configuración general', subtitle:'Sedes, sillones, horarios, servicios, tarifas, permisos e integraciones.', eyebrow:'Administración',
    endpoint:'/modules/settings', createRoles:['ADMIN'], editRoles:['ADMIN'], statusRoles:['ADMIN'], fields:settingFields,
    statusField:'status', statusOptions:['ACTIVO','INACTIVO','PENDIENTE']
  }
];

export const ALL_NAV: NavItem[] = [...PRIMARY_NAV, ...MODULES];
