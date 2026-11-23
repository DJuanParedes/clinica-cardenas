# Genera los diagramas C4 (niveles 1, 2 y 3 + dinámico de sincronización) con Graphviz, al estilo C4-PlantUML.
import subprocess, textwrap
PERSON='#08427B'; SYS='#1168BD'; EXT='#999999'; CONT='#438DD5'; COMP='#85BBF0'; DB='#438DD5'
def esc(s): return s.replace('&','&amp;').replace('<','&lt;').replace('>','&gt;')
def label(name, kind, desc, dark=True):
    fc = 'white' if dark else '#000000'
    d = '<BR/>'.join(esc(l) for l in textwrap.wrap(desc, 34))
    tail = f'<BR/><BR/><FONT POINT-SIZE="10">{d}</FONT>' if d else ''
    return f'<<FONT COLOR="{fc}"><B>{esc(name)}</B><BR/><FONT POINT-SIZE="9">[{esc(kind)}]</FONT>{tail}</FONT>>'
def node(id, name, kind, desc, color, shape='box', dark=True):
    style = 'rounded,filled' if shape=='box' else 'filled'
    return f'  {id} [label={label(name,kind,desc,dark)}, shape={shape}, style="{style}", fillcolor="{color}", color="{color}", margin="0.25,0.15"];\n'
def edge(a,b,txt,tech=''):
    t = esc(txt) + (f'<BR/><FONT POINT-SIZE="8">[{esc(tech)}]</FONT>' if tech else '')
    return f'  {a} -> {b} [label=<<FONT POINT-SIZE="9">{t}</FONT>>, color="#707070", fontcolor="#404040"];\n'
def boundary(id, title, body):
    return f'  subgraph cluster_{id} {{ label=<<B>{esc(title)}</B>>; style="dashed"; color="#888888"; fontcolor="#444444"; fontsize=11;\n{body}  }}\n'
def render(name, body, rankdir='TB', title=''):
    src = f'digraph G {{\n  rankdir={rankdir}; nodesep=0.6; ranksep=0.75; fontname="Helvetica"; labelloc=t; label=<<B>{esc(title)}</B>>; fontsize=14;\n  node [fontname="Helvetica"]; edge [fontname="Helvetica"];\n{body}}}\n'
    open(f'{name}.dot','w').write(src)
    for fmt in ('png','svg'):
        subprocess.run(['dot', f'-T{fmt}', '-Gdpi=170' if fmt=='png' else '-Gdpi=72', f'{name}.dot', '-o', f'{name}.{fmt}'], check=True)

# ---------------- Nivel 1: contexto
b = node('pac','Paciente','Persona','Reserva, confirma y consulta sus citas, pagos y documentos desde el teléfono.',PERSON)
b += node('per','Personal de la clínica','Persona','Recepción, odontólogos, caja, dirección y administrador: agenda, fichas, cobros.',PERSON)
b += node('sis','Sistema Clínica Dental Cárdenas','Sistema de software','Gestiona pacientes, citas, historia clínica, caja y comprobantes; app móvil offline-first para pacientes y personal.',SYS)
b += node('wa','WhatsApp Cloud API (Meta)','Sistema externo','Envía recordatorios y avisos de las citas por WhatsApp.',EXT)
b += node('mail','Servidor de correo (SMTP)','Sistema externo','Envía códigos, comprobantes y avisos por correo.',EXT)
b += node('sunat','SUNAT','Sistema externo','Recibe los comprobantes electrónicos y devuelve la constancia (CDR).',EXT)
b += node('reniec','Consulta DNI (RENIEC)','Sistema externo','Autocompleta los datos del paciente a partir del DNI.',EXT)
b += node('mp','Mercado Pago','Sistema externo','Procesa los pagos en línea de los pacientes.',EXT)
b += edge('pac','sis','Usa la app para reservar y confirmar citas')
b += edge('per','sis','Atiende, registra y cobra')
b += edge('sis','wa','Envía recordatorios')
b += edge('sis','mail','Envía correos')
b += edge('sis','sunat','Emite comprobantes')
b += edge('sis','reniec','Consulta el DNI')
b += edge('sis','mp','Cobra en línea')
b += edge('wa','pac','Entrega los mensajes')
render('c4-1-contexto', b, title='Nivel 1 · Contexto — Sistema Clínica Dental Cárdenas')

# ---------------- Nivel 2: contenedores
inner = node('and','App Android','Kotlin · Compose · APK/AAB','Punto de entrada de Android: actividad, Keystore, notificaciones y cámara.',CONT)
inner += node('ios','App iOS','Swift + framework Kotlin/Native','Punto de entrada de iOS (Xcode): Keychain y notificaciones.',CONT)
inner += node('shared','Módulo compartido (KMP)','Kotlin Multiplatform · Compose MP · Koin','Dominio, datos y presentación comunes: casos de uso, sincronización, ViewModels.',CONT)
inner += node('local','Base local del dispositivo','SQLite · SQLDelight','Fuente única de verdad de la app: citas y cola de operaciones pendientes.',DB,shape='cylinder')
inner += node('web','Aplicación web','Angular 18 · SPA','Interfaz del personal y portal del paciente en el navegador.',CONT)
inner += node('api','Servicio REST','Spring Boot 3.4 · Java 21','Reglas de negocio, seguridad JWT y privilegios; API /api/v1.',CONT)
inner += node('db','Base de datos','MySQL 8.4 · Flyway','Pacientes, citas, historia clínica, caja, comprobantes, roles.',DB,shape='cylinder')
inner += node('notif','Microservicio de notificaciones','Node.js','Cola y programación de recordatorios por WhatsApp y correo.',CONT)
b = node('pac','Paciente','Persona','Usa la app en su teléfono.',PERSON)
b += node('per','Personal','Persona','Usa la web o la app del personal.',PERSON)
b += boundary('sis','Sistema Clínica Dental Cárdenas', inner)
b += node('wa','WhatsApp Cloud API','Sistema externo','Mensajería.',EXT)
b += node('sunat','SUNAT','Sistema externo','Facturación electrónica.',EXT)
b += edge('pac','and','Usa'); b += edge('pac','ios','Usa'); b += edge('per','web','Usa','HTTPS')
b += edge('and','shared','Ejecuta','Kotlin/JVM'); b += edge('ios','shared','Ejecuta','framework nativo')
b += edge('shared','local','Lee (observable) y escribe','SQL')
b += edge('shared','api','Sincroniza y consulta','HTTPS · JSON · JWT · /api/v1')
b += edge('web','api','Consume','HTTPS · JSON · JWT')
b += edge('api','db','Lee y escribe','JDBC')
b += edge('api','notif','Encola avisos','HTTP · JSON')
b += edge('notif','wa','Envía','HTTPS'); b += edge('api','sunat','Envía comprobantes','SOAP · XML firmado')
render('c4-2-contenedores', b, title='Nivel 2 · Contenedores')

# ---------------- Nivel 3: componentes del módulo compartido
pres = node('vm','HomeViewModel / BookingViewModel','Presentación · ViewModel','StateFlow con un UiState por pantalla; llama solo a casos de uso.',COMP,dark=False)
pres += node('ui','Pantallas Compose','Presentación · Compose MP','Dibujan el UiState: carga, contenido, vacío y error; estado de sincronización.',COMP,dark=False)
dom = node('uc','Casos de uso','Dominio','ObserveAppointments, ConfirmAppointment, RequestAppointment, SyncAppointments, ObserveSyncStatus, ScheduleReminders.',COMP,dark=False)
dom += node('irepo','Interfaces de repositorio','Dominio','AppointmentLocal/RemoteRepository, TokenStorage, ReminderScheduler, Clock.',COMP,dark=False)
dom += node('model','Modelo y errores','Dominio','Appointment, PendingOperation, SyncStatus; DomainError (sealed).',COMP,dark=False)
dat = node('lrepo','AppointmentLocalRepositoryImpl','Datos · SQLDelight','Consultas observables, cola de pendientes, reemplazo sin pisar lo pendiente.',COMP,dark=False)
dat += node('rrepo','AppointmentRemoteRepositoryImpl','Datos · Ktor','DTO → dominio; Idempotency-Key en reservas.',COMP,dark=False)
dat += node('http','ApiClient + ErrorMapper','Datos · Ktor HttpClient (única instancia)','Cabecera Bearer centralizada; traduce errores a DomainError.',COMP,dark=False)
dat += node('tok','TokenStorage (expect/actual)','Datos · plataforma','Android Keystore AES-GCM / Keychain de iOS.',COMP,dark=False)
dat += node('rem','ReminderSchedulerImpl','Datos · plataforma','AlarmManager / UNUserNotificationCenter; permiso en 3 estados.',COMP,dark=False)
b = boundary('p','Presentación', pres) + boundary('d','Dominio (Kotlin puro)', dom) + boundary('t','Datos', dat)
b += node('sq','Base local','SQLite','',DB,shape='cylinder') + node('api','Servicio REST','Spring Boot','',CONT)
b += edge('ui','vm','Observa StateFlow'); b += edge('vm','uc','Invoca')
b += edge('uc','irepo','Depende de'); b += edge('uc','model','Usa')
b += edge('lrepo','irepo','Implementa'); b += edge('rrepo','irepo','Implementa'); b += edge('tok','irepo','Implementa'); b += edge('rem','irepo','Implementa')
b += edge('rrepo','http','Usa'); b += edge('lrepo','sq','SQL'); b += edge('http','api','HTTPS /api/v1')
b += '  lrepo -> rrepo [style=invis]; tok -> rem [style=invis]; lrepo -> tok [style=invis];\n'
render('c4-3-componentes-app', b, title='Nivel 3 · Componentes del módulo compartido (las flechas apuntan al dominio)')

# ---------------- Nivel 3: componentes del servicio
c = node('flt','ApiVersionFilter + JwtAuthFilter','Seguridad · Servlet filter','/api/v1 → /api; valida el JWT y carga los privilegios como authorities.',COMP,dark=False)
c += node('sec','SecurityConfig + @PreAuthorize','Seguridad · Spring Security','Rutas por rol; cada endpoint exige su privilegio (403 si falta).',COMP,dark=False)
c += node('ctl','Controladores REST','Web · @RestController','Portal, Agenda, Pacientes, Caja, Facturación, Roles, Eliminación…',COMP,dark=False)
c += node('svc','Servicios de negocio','Negocio · @Service','AgendaService, PortalExtrasService, CashService, BillingService, PermissionService…',COMP,dark=False)
c += node('idem','IdempotencyService','Negocio','Ejecuta una sola vez cada escritura con Idempotency-Key.',COMP,dark=False)
c += node('err','GlobalExceptionHandler + ApiException','Web','Formato de error único con detalle por campo.',COMP,dark=False)
c += node('repo','Repositorios JPA / JdbcTemplate','Datos','Acceso a MySQL; migraciones Flyway V1–V27.',COMP,dark=False)
c += node('jwt','JwtService','Seguridad','Firma HS256 y valida tokens con vigencia acotada.',COMP,dark=False)
b = boundary('s','Servicio REST (Spring Boot)', c)
b += node('app','App / Web','Cliente','',CONT) + node('db','MySQL','Base de datos','',DB,shape='cylinder')
b += edge('app','flt','HTTPS · JSON'); b += edge('flt','jwt','Valida token'); b += edge('flt','sec','Autentica')
b += edge('sec','ctl','Autoriza'); b += edge('ctl','svc','Invoca'); b += edge('ctl','idem','Envuelve la reserva'); b += edge('ctl','err','Errores')
b += edge('svc','repo','Usa'); b += edge('repo','db','JDBC')
b += '  idem -> err [style=invis];\n'
render('c4-3-componentes-servicio', b, title='Nivel 3 · Componentes del servicio')

# ---------------- Dinámico: sincronización offline-first
b = node('u','Paciente','Persona','',PERSON) + node('vm','HomeViewModel','ViewModel','',COMP,dark=False)
b += node('uc','SyncAppointmentsUseCase','Caso de uso','',COMP,dark=False) + node('l','Base local','SQLDelight','',DB,shape='cylinder')
b += node('r','Repositorio remoto','Ktor','',COMP,dark=False) + node('s','Servicio','Spring Boot','',CONT)
for a,bb,t in [('u','vm','1. Confirma sin red'),('vm','uc','2. ConfirmAppointment → guarda local + encola'),('uc','l','3. Cita "pendiente"; operación en cola'),
               ('uc','r','4. Vuelve la red: SUBE pendientes en orden'),('r','s','5. POST confirm / reservas con Idempotency-Key'),
               ('uc','r','6. Después BAJA las citas'),('uc','l','7. Reemplaza sin pisar lo pendiente; marca hora'),('l','vm','8. Flow emite; la pantalla se actualiza')]:
    b += edge(a,bb,t)
render('c4-dinamico-sincronizacion', b, rankdir='LR', title='Diagrama dinámico · Sincronización offline-first')
print('ok')
