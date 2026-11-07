/*
 * Microservicio de notificaciones · Clínica Dental Cárdenas (HU-23 / HU-24, SP-02)
 * -----------------------------------------------------------------------------
 * Envía mensajes por WhatsApp (WhatsApp Cloud API de Meta) y correo electrónico (SMTP). Ver proveedores.js.
 * Si un canal no tiene credenciales, funciona en modo de prueba: el mensaje solo se ve en el panel web.
 * Todos los mensajes, reales o de prueba, aparecen en el panel.
 *
 *   POST   /v1/mensajes                 envío inmediato            { clave, canal, destino, asunto, cuerpo, tipo, referencia }
 *   POST   /v1/recordatorios            programa un envío futuro   { ...lo anterior, enviarEn (ISO-8601) }
 *   DELETE /v1/recordatorios/{clave}    anula un recordatorio programado
 *   GET    /v1/mensajes                 lista (para el panel)
 *   POST   /admin/modo                  {"modo":"normal|caido"} simula caída del servicio
 *   POST   /admin/enviar-pendientes     envía ya los recordatorios programados (demostración)
 *   POST   /admin/prueba                {canal, destino, plantilla?} envío real de prueba desde el panel
 *   POST   /v1/contactos                {pacienteId, nombre, telefono, correo, demo} o una lista: agenda de pacientes
 *   GET    /v1/contactos                agenda (número → nombre del paciente)
 *   POST   /admin/enviar                {destino, texto} mensaje de WhatsApp escrito desde el panel
 *   GET    /webhook/whatsapp            verificación del webhook de Meta (hub.verify_token = WA_VERIFY_TOKEN)
 *   POST   /webhook/whatsapp            Meta avisa: entregado / leído y mensajes que escribe el paciente
 *                                       ("1" confirma la cita, "2" pide reprogramar; se responde solo)
 *   GET    /                            panel: bandeja de WhatsApp y de correo
 *
 * Reglas:
 *   - Idempotencia: la "clave" identifica el mensaje. Si llega de nuevo, NO se reenvía: se responde duplicado=true.
 *   - Los recordatorios se envían a su hora con un planificador interno y el resultado se informa al sistema
 *     central por webhook (X-Webhook-Token). Si el webhook falla, se reintenta.
 * Node 20 + nodemailer.
 */
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const prov = require('./proveedores');

const PORT = Number(process.env.PORT || 8093);
const DATA_FILE = process.env.DATA_FILE || '/data/notificaciones.json';
const WEBHOOK_URL = process.env.WEBHOOK_URL || '';
const WEBHOOK_TOKEN = process.env.WEBHOOK_TOKEN || 'token-webhook-clinica-cardenas';
// Respuestas del paciente por WhatsApp: se reenvían al sistema central, que confirma o marca "quiere reprogramar".
const REPLY_URL = process.env.REPLY_URL || (WEBHOOK_URL ? new URL('/api/internal/whatsapp/reply', WEBHOOK_URL).toString() : '');
const WA_VERIFY_TOKEN = process.env.WA_VERIFY_TOKEN || '';
const WA_APP_SECRET = process.env.WA_APP_SECRET || '';

let modo = 'normal';
let db = load();

function load() {
  try { return JSON.parse(fs.readFileSync(DATA_FILE, 'utf8')); } catch { return { mensajes: [] }; }
}
let saveTimer = null;
function save() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    try { fs.mkdirSync(path.dirname(DATA_FILE), { recursive: true }); fs.writeFileSync(DATA_FILE, JSON.stringify(db)); }
    catch (e) { console.warn('No se pudo guardar:', e.message); }
  }, 200);
}
const byKey = (k) => db.mensajes.find((m) => m.clave === k);
db.contactos = db.contactos || {};

/** Agenda: cada paciente que registra la clínica se guarda con su número y su nombre (lo envía el sistema central). */
function guardarContacto(c) {
  if (!c || !c.nombre) return null;
  const tel = prov.localPhone(c.telefono);
  const correo = c.correo && validEmail(c.correo) ? String(c.correo).trim().toLowerCase() : null;
  const datos = { pacienteId: c.pacienteId ?? c.id ?? null, nombre: String(c.nombre).trim(), telefono: tel, correo, demo: Boolean(c.demo), actualizado: now().toISOString() };
  // si el paciente cambió de número, se quita el número anterior
  for (const [k, v] of Object.entries(db.contactos)) if (datos.pacienteId && v.pacienteId === datos.pacienteId && k !== tel) delete db.contactos[k];
  if (tel) db.contactos[tel] = datos;
  save();
  return datos;
}
const contactoDe = (destino) => db.contactos[prov.localPhone(destino)] || Object.values(db.contactos).find((c) => c.correo && c.correo === String(destino).trim().toLowerCase()) || null;
const now = () => new Date();

// ---------------------------------------------------------------- proveedores simulados
function normalizePhone(v) {
  const d = String(v || '').replace(/\D/g, '');
  const local = d.startsWith('51') && d.length === 11 ? d.slice(2) : d;
  if (!/^9\d{8}$/.test(local)) return null;
  return '+51 ' + local.slice(0, 3) + ' ' + local.slice(3, 6) + ' ' + local.slice(6);
}
const validEmail = (v) => /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(String(v || ''));

/** Entrega un mensaje: por el proveedor real si está configurado y el destino está permitido; si no, queda en el panel. */
async function deliver(m) {
  m.intentos = (m.intentos || 0) + 1;
  if (m.canal === 'WHATSAPP') {
    const phone = normalizePhone(m.destino);
    if (!phone) { m.estado = 'FALLIDO'; m.error = 'El número no es un celular peruano válido para WhatsApp'; return m; }
    m.destinoNormalizado = phone;
  } else if (m.canal === 'EMAIL') {
    if (!validEmail(m.destino)) { m.estado = 'FALLIDO'; m.error = 'Dirección de correo inválida'; return m; }
    m.destinoNormalizado = String(m.destino).trim().toLowerCase();
  } else {
    m.estado = 'FALLIDO'; m.error = 'Canal no soportado'; return m;
  }
  const clave = m.canal === 'WHATSAPP' ? prov.localPhone(m.destino) : m.destinoNormalizado;
  const contacto = contactoDe(m.destino);
  if (contacto && !m.paciente) m.paciente = { id: contacto.pacienteId, nombre: contacto.nombre, demo: contacto.demo };
  const decision = m.forzarReal ? { real: true } : prov.destinoReal(m.canal, clave, m.paciente);
  m.real = decision.real;
  m.nota = decision.motivo || null;
  if (!decision.real) {
    m.proveedorId = m.canal === 'WHATSAPP' ? 'prueba.' + crypto.randomBytes(9).toString('hex') : '<prueba.' + crypto.randomBytes(8).toString('hex') + '@clinicacardenas.pe>';
    m.via = 'bandeja de prueba';
  } else {
    try {
      const r = m.canal === 'WHATSAPP'
        ? await prov.enviarWhatsApp(clave, m.cuerpo, m.plantilla, { chat: m.tipo === 'MENSAJE' })
        : await prov.enviarCorreo(m.destinoNormalizado, m.asunto, m.cuerpo);
      m.proveedorId = r.id;
      m.via = r.via;
      if (m.canal === 'WHATSAPP' && r.via === 'texto') m.nota = 'Enviado como texto: Meta solo lo entrega si el paciente escribió a la clínica en las últimas 24 h';
    } catch (e) {
      m.estado = 'FALLIDO';
      m.error = e.name === 'TimeoutError' ? 'El proveedor no respondió a tiempo' : e.message;
      console.warn(`envío ${m.canal} a ${m.destinoNormalizado} falló: ${m.error}`);
      return m;
    }
  }
  m.estado = 'ENVIADO';
  m.enviadoEn = now().toISOString();
  m.error = null;
  return m;
}

function validate(b, scheduled) {
  const errs = [];
  if (!b || typeof b !== 'object') return ['Cuerpo JSON requerido'];
  if (!b.clave || String(b.clave).length > 160) errs.push('clave requerida');
  if (!['WHATSAPP', 'EMAIL'].includes(b.canal)) errs.push('canal debe ser WHATSAPP o EMAIL');
  if (!b.destino) errs.push('destino requerido');
  if (!b.cuerpo) errs.push('cuerpo requerido');
  if (scheduled && (!b.enviarEn || isNaN(Date.parse(b.enviarEn)))) errs.push('enviarEn debe ser una fecha ISO-8601');
  return errs;
}

function create(b, scheduled) {
  return {
    id: db.mensajes.length + 1,
    clave: String(b.clave),
    canal: b.canal,
    destino: String(b.destino),
    asunto: b.asunto || null,
    cuerpo: String(b.cuerpo),
    tipo: b.tipo || 'GENERAL',
    referencia: b.referencia || null,
    recibidoEn: now().toISOString(),
    programadoPara: scheduled ? new Date(b.enviarEn).toISOString() : null,
    estado: scheduled ? 'PROGRAMADO' : 'PENDIENTE',
    intentos: 0,
    webhookPendiente: false,
    paciente: b.paciente && b.paciente.nombre ? { id: b.paciente.id ?? null, nombre: String(b.paciente.nombre), demo: Boolean(b.paciente.demo) } : null,
  };
}

const publicView = (m) => ({ id: m.id, clave: m.clave, estado: m.estado, error: m.error || null, proveedorId: m.proveedorId || null,
  programadoPara: m.programadoPara, enviadoEn: m.enviadoEn || null, real: Boolean(m.real), nota: m.nota || null });

// ---------------------------------------------------------------- planificador y webhook
async function notifyCentral(m) {
  if (!WEBHOOK_URL) return;
  try {
    const res = await fetch(WEBHOOK_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Webhook-Token': WEBHOOK_TOKEN },
      body: JSON.stringify({ clave: m.clave, estado: m.entrega || m.estado, error: m.error || null, proveedorId: m.proveedorId || null }),
      signal: AbortSignal.timeout(5000),
    });
    m.webhookPendiente = !res.ok;
    m.webhookRespuesta = res.status;
  } catch (e) {
    m.webhookPendiente = true;
    m.webhookRespuesta = 'sin conexión';
  }
  save();
}

let ticking = false;
async function tick(force = false) {
  if (modo === 'caido' || ticking) return 0;
  ticking = true;
  try { return await runTick(force); } finally { ticking = false; }
}

async function runTick(force) {
  const t = now();
  let sent = 0;
  for (const m of db.mensajes) {
    if (m.estado === 'PROGRAMADO' && (force || new Date(m.programadoPara) <= t)) {
      await deliver(m);
      sent++;
      save();
      await notifyCentral(m);
    } else if (m.webhookPendiente && m.estado !== 'PROGRAMADO') {
      await notifyCentral(m);
    }
  }
  return sent;
}
setInterval(() => tick().catch((e) => console.warn('planificador:', e.message)), 10_000);
// Revisa credenciales y plantilla al arrancar y cada 30 minutos (para avisar en el panel si el token vence).
setTimeout(() => prov.revisarProveedores(), 3_000);
setInterval(() => prov.revisarProveedores(), 30 * 60_000);

// ---------------------------------------------------------------- webhook de Meta (WhatsApp)
const RANK = { ENVIADO: 1, ENTREGADO: 2, LEIDO: 3 };

/** Meta firma cada aviso con la clave secreta de la app (X-Hub-Signature-256). Si no está configurada, se acepta. */
function firmaValida(raw, header) {
  if (!WA_APP_SECRET) return true;
  if (!header || !header.startsWith('sha256=')) return false;
  const esperado = Buffer.from('sha256=' + crypto.createHmac('sha256', WA_APP_SECRET).update(raw).digest('hex'));
  const recibido = Buffer.from(header);
  return esperado.length === recibido.length && crypto.timingSafeEqual(esperado, recibido);
}

async function estadoDeMeta(st) {
  const m = db.mensajes.find((x) => x.proveedorId && x.proveedorId === st.id);
  if (!m) return;
  if (st.status === 'failed') {
    m.estado = 'FALLIDO';
    m.error = (st.errors && st.errors[0] && (st.errors[0].title || st.errors[0].message)) || 'Meta no pudo entregar el mensaje';
    m.entrega = null;
  } else {
    const nuevo = st.status === 'read' ? 'LEIDO' : st.status === 'delivered' ? 'ENTREGADO' : null;
    if (!nuevo || (RANK[nuevo] || 0) <= (RANK[m.entrega || 'ENVIADO'] || 0)) return;
    m.entrega = nuevo;
    m[nuevo === 'LEIDO' ? 'leidoEn' : 'entregadoEn'] = new Date(Number(st.timestamp) * 1000 || Date.now()).toISOString();
  }
  save();
  await notifyCentral(m);
}

async function mensajeEntrante(msg, perfil) {
  const local = prov.localPhone(msg.from);
  if (!local) return;
  const texto = msg.type === 'text' ? String(msg.text && msg.text.body || '')
    : msg.type === 'button' ? String(msg.button && msg.button.text || '')
    : msg.type === 'interactive' ? String((msg.interactive && (msg.interactive.button_reply || msg.interactive.list_reply) || {}).title || '')
    : '[' + msg.type + ']';
  if (db.mensajes.some((x) => x.entrante && x.proveedorId === msg.id)) return; // Meta puede reenviar el mismo aviso
  const contacto = contactoDe(local);
  db.mensajes.push({
    id: db.mensajes.length + 1, clave: 'entrante-' + msg.id, canal: 'WHATSAPP', destino: local, destinoNormalizado: local, cuerpo: texto,
    tipo: 'ENTRANTE', entrante: true, estado: 'RECIBIDO', proveedorId: msg.id, recibidoEn: new Date().toISOString(),
    enviadoEn: new Date(Number(msg.timestamp) * 1000 || Date.now()).toISOString(), real: true,
    paciente: contacto ? { id: contacto.pacienteId, nombre: contacto.nombre, demo: contacto.demo } : (perfil ? { id: null, nombre: perfil, demo: false } : null),
  });
  save();
  if (!REPLY_URL) return;
  let r = null;
  try {
    const res = await fetch(REPLY_URL, {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Webhook-Token': WEBHOOK_TOKEN },
      body: JSON.stringify({ telefono: local, texto }), signal: AbortSignal.timeout(8000),
    });
    r = res.ok ? await res.json() : null;
  } catch (e) { console.warn('respuesta WhatsApp → sistema central:', e.message); }
  if (r && r.respuesta) {
    const m = create({ clave: 'auto-' + msg.id, canal: 'WHATSAPP', destino: local, cuerpo: r.respuesta, tipo: 'MENSAJE',
      paciente: contacto ? { id: contacto.pacienteId, nombre: contacto.nombre, demo: contacto.demo } : null }, false);
    db.mensajes.push(m);
    await deliver(m); // el paciente acaba de escribir: dentro de las 24 h, el texto libre sí llega
    save();
    console.log(`respuesta automática a ${local}: ${r.accion} → ${m.estado}`);
  }
}

async function avisoDeMeta(body) {
  for (const entry of (body && body.entry) || []) {
    for (const ch of entry.changes || []) {
      const v = ch.value || {};
      for (const st of v.statuses || []) await estadoDeMeta(st);
      const perfil = v.contacts && v.contacts[0] && v.contacts[0].profile && v.contacts[0].profile.name;
      for (const msg of v.messages || []) await mensajeEntrante(msg, perfil);
    }
  }
}

// ---------------------------------------------------------------- panel
function panel() {
  return `<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Notificaciones · Clínica Dental Cárdenas</title>
<style>
:root{--brand:#00b0e0;--orange:#f89818;--ink:#0e2a3b;--muted:#688494;--line:#dbe7ee;--wa:#075e54;--wa2:#dcf8c6;--bg:#f3f8fb}
*{box-sizing:border-box}body{margin:0;font-family:system-ui,Segoe UI,sans-serif;background:var(--bg);color:var(--ink)}
header{background:#fff;border-bottom:3px solid var(--brand);padding:14px 22px;display:flex;align-items:center;gap:14px;flex-wrap:wrap}
header h1{font-size:18px;margin:0}header .sub{color:var(--muted);font-size:13px}
.sp{flex:1}.btn{border:1px solid var(--line);background:#fff;padding:7px 14px;border-radius:999px;cursor:pointer;font-weight:600;font-size:13px}
.btn.on{background:var(--ink);color:#fff;border-color:var(--ink)}.btn.orange{background:var(--orange);border-color:var(--orange);color:#fff}
main{display:grid;grid-template-columns:380px 1fr;gap:18px;padding:18px 22px;max-width:1300px}
.card{background:#fff;border:1px solid var(--line);border-radius:14px;overflow:hidden}
.card h2{font-size:15px;margin:0;padding:12px 16px;border-bottom:1px solid var(--line);display:flex;justify-content:space-between;align-items:center}
.phone{background:#e5ddd5;height:560px;display:flex;flex-direction:column}
.phone .top{background:var(--wa);color:#fff;padding:10px 14px;font-weight:600;display:flex;gap:10px;align-items:center}
.phone .top select{flex:1;border:0;border-radius:6px;padding:5px;font:inherit}
.chat{flex:1;overflow:auto;padding:12px;display:flex;flex-direction:column;gap:8px}
.compose{display:flex;gap:6px;padding:8px;background:#f0f0f0}.compose input{flex:1;border:0;border-radius:18px;padding:9px 14px;font:inherit;font-size:14px}
.bub{background:var(--wa2);border-radius:8px 8px 2px 8px;padding:8px 10px;max-width:92%;align-self:flex-end;white-space:pre-wrap;font-size:13.5px;box-shadow:0 1px 1px rgba(0,0,0,.1)}
.bub .meta{font-size:11px;color:#667;text-align:right;margin-top:4px}.bub.fail{background:#fde2e2}.bub.in{background:#fff;align-self:flex-start;border-radius:8px 8px 8px 2px}
.mail{display:grid;grid-template-columns:300px 1fr;height:560px}
.inbox{border-right:1px solid var(--line);overflow:auto}.inbox div{padding:10px 14px;border-bottom:1px solid var(--line);cursor:pointer;font-size:13px}
.inbox div.on,.inbox div:hover{background:#e2f6fc}.inbox b{display:block}.inbox span{color:var(--muted);font-size:12px}
.read{padding:18px 22px;overflow:auto;white-space:pre-wrap;font-size:14px;line-height:1.5}.read h3{margin:0 0 4px}
table{width:100%;border-collapse:collapse;font-size:13px}th,td{padding:8px 12px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top}
th{background:#f6fafc;color:var(--muted)}.tag{display:inline-block;padding:2px 8px;border-radius:999px;font-size:12px;font-weight:700}
.PROGRAMADO{background:#e2f4fb;color:#055f88}.ENVIADO{background:#e1f4e9;color:#217a4a}.FALLIDO{background:#fdece4;color:#c2410c}.ANULADO{background:#eef1f4;color:#688494}
.prov{display:flex;gap:10px;flex-wrap:wrap;padding:12px 22px 0;max-width:1300px}
.pv{background:#fff;border:1px solid var(--line);border-radius:12px;padding:10px 14px;font-size:13px;flex:1;min-width:260px}
.pv b{font-size:14px}.pv .real{color:#217a4a;font-weight:700}.pv .test{color:#9a5b00;font-weight:700}
.pv form{display:flex;gap:6px;margin-top:8px;flex-wrap:wrap}.pv input,.pv select{border:1px solid var(--line);border-radius:8px;padding:6px 8px;font:inherit;font-size:13px;flex:1;min-width:120px}
.badge{display:inline-block;font-size:10.5px;font-weight:700;padding:1px 6px;border-radius:6px;margin-left:4px}.badge.r{background:#217a4a;color:#fff}.badge.t{background:#eef1f4;color:#688494}
#res{font-size:12.5px;margin-top:6px}
.al{margin:12px 22px 0;max-width:1256px;border-radius:12px;padding:12px 16px;font-size:14px;display:flex;gap:10px;align-items:flex-start}
.al.error{background:#fdece4;color:#9a2f06;border:1px solid #f6c3ab}.al.aviso{background:#fff4dc;color:#7a4600;border:1px solid #f3d79c}
.al b{display:block;margin-bottom:2px}.al .sp{flex:1}
.wide{grid-column:1/-1}.empty{padding:30px;text-align:center;color:var(--muted)}
.stats{display:flex;gap:18px;font-size:13px;color:var(--muted)}.stats b{color:var(--ink);font-size:16px}
@media (max-width:900px){main{grid-template-columns:1fr}.mail{grid-template-columns:1fr;height:auto}.inbox{max-height:260px}}
</style></head><body>
<header>
  <svg width="38" height="38" viewBox="40 40 332 332" aria-hidden="true"><path d="M334 102 A154 154 0 1 0 342 300 A149.5 149.5 0 1 1 334 102 Z" fill="#F89818"/><circle cx="206" cy="206" r="78" fill="#00B0E0"/></svg>
  <div><h1>Microservicio de notificaciones</h1><div class="sub">Clínica Dental Cárdenas · WhatsApp Cloud API (Meta) y correo SMTP</div></div>
  <span class="sp"></span>
  <div class="stats" id="stats"></div>
  <span>Servicio:</span><button class="btn" data-m="normal">Normal</button><button class="btn" data-m="caido">Caído</button>
  <button class="btn orange" id="force">Enviar recordatorios ahora</button>
</header>
<div id="alertas"></div>
<div class="prov" id="prov"></div>
<main>
  <section class="card"><h2>WhatsApp <span class="sub" id="wacount"></span></h2>
    <div class="phone"><div class="top">💬 <select id="who"></select></div><div class="chat" id="chat"></div>
    <form class="compose" id="compose"><input id="txt" placeholder="Escribe un mensaje para este paciente…" autocomplete="off"><button class="btn orange">Enviar</button></form></div></section>
  <section class="card"><h2>Correo electrónico <span class="sub" id="mailcount"></span></h2>
    <div class="mail"><div class="inbox" id="inbox"></div><div class="read" id="read"><div class="empty">Selecciona un correo.</div></div></div></section>
  <section class="card wide"><h2>Recordatorios programados y bitácora</h2><div style="overflow:auto"><table>
    <thead><tr><th>Estado</th><th>Tipo</th><th>Canal</th><th>Destino</th><th>Programado para</th><th>Enviado</th><th>Clave de idempotencia</th><th>Webhook</th></tr></thead>
    <tbody id="log"></tbody></table></div></section>
</main>
<script>
let data=null, who=null, mailSel=null;
const esc=s=>String(s??'').replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]));
const fmt=s=>s?new Date(s).toLocaleString('es-PE',{timeZone:'America/Lima',day:'2-digit',month:'short',hour:'2-digit',minute:'2-digit'}):'—';
const TYPE={RECORDATORIO:'Recordatorio',CAMBIO_CITA:'Cambio de cita',CANCELACION:'Cancelación',CODIGO_ACCESO:'Código de acceso',PRUEBA:'Prueba',MENSAJE:'Mensaje',ENTRANTE:'Escribió el paciente',CONTROL:'Control preventivo',LISTA_ESPERA:'Lista de espera',CUOTA:'Cuota'};
const badge=m=>m.estado==='ENVIADO'?(m.real?'<span class="badge r">REAL</span>':'<span class="badge t">PRUEBA</span>'):'';
let provDrawn=false;
function drawProv(p){
  if(provDrawn) return; provDrawn=true;
  const box=(t,c,ch,ph,extra)=>'<div class="pv"><b>'+t+'</b> · '+(c.modo==='real'?'<span class="real">Envío real</span> · '+esc(c.proveedor)+(c.numero?' · desde '+esc(c.numero):'')+(c.envio?' · '+esc(c.envio):'')+(c.estadoPlantilla?' ('+({APPROVED:'aprobada',PENDING:'en revisión',REJECTED:'rechazada'}[c.estadoPlantilla]||c.estadoPlantilla)+')':'')+(c.remitente?' · desde '+esc(c.remitente):'')
    +'<div class="sub">'+(p.destinos==='todos'?'Envía a todos los destinos.':p.destinos==='pacientes'?'Envía a todos los pacientes registrados por la clínica'+(c.permitidos.length?' y a '+esc(c.permitidos.join(', ')):'')+'. Los pacientes de ejemplo quedan en la bandeja de prueba.':'Solo envía de verdad a: '+(c.permitidos.length?esc(c.permitidos.join(', ')):'(nadie; agrega destinos en el .env)')+'. El resto queda en la bandeja de prueba.')+'</div>'
    :'<span class="test">Modo de prueba</span><div class="sub">Configura las credenciales en el archivo .env para enviar de verdad (ver README).</div>')
    +(c.modo==='real'?'<form data-c="'+ch+'"><input name="d" placeholder="'+ph+'" required>'+extra+'<button class="btn orange">Enviar prueba real</button></form>':'')+'</div>';
  document.getElementById('prov').innerHTML=box('WhatsApp',p.whatsapp,'WHATSAPP','987654321','<select name="t"><option value="">Mensaje normal</option><option value="hello_world">Plantilla hello_world</option></select>')
    +box('Correo',p.correo,'EMAIL','tucorreo@gmail.com','')+'<div id="res" style="width:100%"></div>';
  document.querySelectorAll('.pv form').forEach(f=>f.onsubmit=async e=>{e.preventDefault();const b=f.querySelector('button');b.disabled=true;
    const r=await fetch('/admin/prueba',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({canal:f.dataset.c,destino:f.elements.d.value,plantilla:f.elements.t?f.elements.t.value:''})});
    const j=await r.json();b.disabled=false;
    document.getElementById('res').innerHTML=r.ok?'<span class="real">✓ Enviado. ID del proveedor: '+esc(j.proveedorId)+'</span>':'<span style="color:#c2410c">✗ '+esc(j.error)+'</span>';load();});
}
function drawAlertas(list){
  document.getElementById('alertas').innerHTML=(list||[]).map(a=>'<div class="al '+a.nivel+'"><span>'+(a.nivel==='error'?'⚠️':'⏳')+'</span><div class="sp"><b>'+(a.clave==='wa-token'?'WhatsApp: revisa el token':a.clave==='wa-plantilla'?'WhatsApp: plantilla':'Correo')+'</b>'+esc(a.texto)+'<div class="sub">Desde '+fmt(a.desde)+'</div></div><button class="btn" onclick="recheck(this)">Volver a comprobar</button></div>').join('');
}
async function recheck(b){ b.disabled=true; await fetch('/admin/revisar',{method:'POST'}); provDrawn=false; await load(); }
async function load(){ const r=await fetch('/api/estado'); data=await r.json(); drawAlertas(data.alertas); drawProv(data.proveedores); render(); }
function render(){
  document.querySelectorAll('[data-m]').forEach(b=>b.classList.toggle('on',b.dataset.m===data.modo));
  const ms=data.mensajes;
  const c=s=>ms.filter(m=>m.estado===s).length;
  document.getElementById('stats').innerHTML='<span><b>'+c('ENVIADO')+'</b> enviados</span><span><b>'+c('PROGRAMADO')+'</b> programados</span><span><b>'+data.duplicadosEvitados+'</b> duplicados evitados</span><span><b>'+c('FALLIDO')+'</b> fallidos</span>';
  const wa=ms.filter(m=>m.canal==='WHATSAPP'&&(m.estado==='ENVIADO'||m.estado==='FALLIDO'||m.estado==='RECIBIDO'));
  const nums=[...new Set(wa.map(m=>m.destinoNormalizado||m.destino))];
  const fp=t=>'+51 '+t.slice(0,3)+' '+t.slice(3,6)+' '+t.slice(6);
  const ag={}; Object.entries(data.contactos||{}).forEach(([t,c])=>ag[fp(t)]=c);
  Object.keys(ag).filter(n=>!ag[n].demo).forEach(n=>{ if(!nums.includes(n)) nums.push(n); });
  if(!who||!nums.includes(who)) who=nums[nums.length-1]||null;
  const lbl=n=>ag[n]?ag[n].nombre+' · '+n+(ag[n].demo?' (ejemplo)':''):n;
  const sel=document.getElementById('who'); sel.innerHTML=nums.map(n=>'<option value="'+esc(n)+'" '+(n===who?'selected':'')+'>'+esc(lbl(n))+'</option>').join('')||'<option>Sin conversaciones</option>';
  document.getElementById('wacount').textContent=wa.length+' mensajes';
  const chat=document.getElementById('chat');
  chat.innerHTML=wa.filter(m=>(m.destinoNormalizado||m.destino)===who).map(m=>'<div class="bub '+(m.estado==='FALLIDO'?'fail':'')+(m.entrante?' in':'')+'">'+esc(m.cuerpo)+'<div class="meta">'+(TYPE[m.tipo]||m.tipo)+' · '+fmt(m.enviadoEn||m.recibidoEn)+(m.entrante?'':m.estado==='ENVIADO'?(m.entrega==='LEIDO'?' <span style="color:#34b7f1">✓✓ leído</span>':m.entrega==='ENTREGADO'?' ✓✓ entregado':' ✓ enviado'):' ✗ '+esc(m.error))+badge(m)+(m.estado==='ENVIADO'&&m.via==='texto'?'<br>Texto libre: llega si el paciente te escribió en las últimas 24 h':'')+'</div></div>').join('')||'<div class="empty">Aún no hay mensajes.</div>';
  chat.scrollTop=chat.scrollHeight;
  const em=ms.filter(m=>m.canal==='EMAIL'&&(m.estado==='ENVIADO'||m.estado==='FALLIDO')).reverse();
  document.getElementById('mailcount').textContent=em.length+' correos';
  document.getElementById('inbox').innerHTML=em.map(m=>'<div data-id="'+m.id+'" class="'+(m.id===mailSel?'on':'')+'"><b>'+esc(m.asunto||'(sin asunto)')+badge(m)+'</b><span>'+esc(m.destino)+' · '+fmt(m.enviadoEn||m.recibidoEn)+'</span></div>').join('')||'<div class="empty">Bandeja vacía.</div>';
  const cur=em.find(m=>m.id===mailSel);
  if(cur) document.getElementById('read').innerHTML='<h3>'+esc(cur.asunto)+'</h3><div class="sub">De: notificaciones@clinicacardenas.pe · Para: '+esc(cur.destino)+' · '+fmt(cur.enviadoEn)+'</div><hr style="border:0;border-top:1px solid var(--line)">'+esc(cur.cuerpo);
  document.getElementById('log').innerHTML=ms.slice().reverse().slice(0,120).map(m=>'<tr><td><span class="tag '+m.estado+'">'+m.estado+'</span>'+badge(m)+(m.error?'<div class="sub">'+esc(m.error)+'</div>':'')+(m.nota&&m.estado==='ENVIADO'?'<div class="sub">'+esc(m.nota)+'</div>':'')+'</td><td>'+(TYPE[m.tipo]||m.tipo)+'</td><td>'+m.canal+'</td><td>'+esc(m.destino)+'</td><td>'+fmt(m.programadoPara)+'</td><td>'+fmt(m.enviadoEn)+'</td><td style="font-family:monospace;font-size:11.5px">'+esc(m.clave)+'</td><td>'+(m.programadoPara?(m.webhookRespuesta??'—'):'—')+'</td></tr>').join('')||'<tr><td colspan="8" class="empty">Sin mensajes.</td></tr>';
}
document.getElementById('who').onchange=e=>{who=e.target.value;render();};
document.getElementById('compose').onsubmit=async e=>{e.preventDefault();const i=document.getElementById('txt');if(!who||!i.value.trim())return;
  const b=e.target.querySelector('button');b.disabled=true;
  const r=await fetch('/admin/enviar',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({destino:who,texto:i.value})});
  const j=await r.json();b.disabled=false;if(r.ok)i.value='';else alert(j.error||'No se pudo enviar');load();};
document.getElementById('inbox').onclick=e=>{const d=e.target.closest('[data-id]'); if(d){mailSel=+d.dataset.id; render();}};
document.querySelectorAll('[data-m]').forEach(b=>b.onclick=async()=>{await fetch('/admin/modo',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({modo:b.dataset.m})});load();});
document.getElementById('force').onclick=async()=>{const r=await fetch('/admin/enviar-pendientes',{method:'POST'});const j=await r.json();alert(j.enviados+' recordatorio(s) enviado(s).');load();};
load(); setInterval(load,5000);
</script></body></html>`;
}

// ---------------------------------------------------------------- servidor
function send(res, status, body, type = 'application/json; charset=utf-8') {
  res.writeHead(status, { 'Content-Type': type, 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET,POST,DELETE,OPTIONS', 'Access-Control-Allow-Headers': 'Content-Type' });
  res.end(typeof body === 'string' ? body : JSON.stringify(body));
}

const server = http.createServer((req, res) => {
  if (req.method === 'OPTIONS') return send(res, 204, '');
  const url = new URL(req.url, 'http://localhost');
  const chunks = [];
  req.on('data', (c) => chunks.push(c));
  req.on('end', async () => {
    let body = null;
    try { body = chunks.length ? JSON.parse(Buffer.concat(chunks).toString('utf8')) : null; } catch { return send(res, 400, { error: 'JSON inválido' }); }
    const p = url.pathname;
    const log = (status) => console.log(`${new Date().toISOString()} ${req.method} ${p} → ${status}`);

    if (p === '/health') return send(res, 200, { status: 'UP' });
    if (p === '/webhook/whatsapp' && req.method === 'GET') {
      const ok = WA_VERIFY_TOKEN && url.searchParams.get('hub.mode') === 'subscribe' && url.searchParams.get('hub.verify_token') === WA_VERIFY_TOKEN;
      log(ok ? 'verificado' : 'rechazado');
      return ok ? send(res, 200, url.searchParams.get('hub.challenge') || '', 'text/plain') : send(res, 403, { error: 'Token de verificación incorrecto' });
    }
    if (p === '/webhook/whatsapp' && req.method === 'POST') {
      if (!firmaValida(Buffer.concat(chunks), req.headers['x-hub-signature-256'])) { log(401); return send(res, 401, { error: 'Firma inválida' }); }
      send(res, 200, { ok: true }); // Meta espera respuesta rápida; se procesa después
      avisoDeMeta(body).catch((e) => console.warn('webhook Meta:', e.message));
      return;
    }
    // Simulación local del webhook (para probar sin Meta): {telefono, texto}
    if (p === '/admin/simular-respuesta' && req.method === 'POST') {
      const tel = prov.localPhone(body && body.telefono);
      if (!tel) return send(res, 400, { error: 'Teléfono inválido' });
      const id = 'sim.' + crypto.randomBytes(6).toString('hex');
      await mensajeEntrante({ from: '51' + tel, id, type: 'text', text: { body: String(body.texto || '') }, timestamp: String(Math.floor(Date.now() / 1000)) });
      const auto = db.mensajes.find((x) => x.clave === 'auto-' + id);
      return send(res, 200, { recibido: true, respuesta: auto ? auto.cuerpo : null });
    }
    if (p === '/' && req.method === 'GET') return send(res, 200, panel(), 'text/html; charset=utf-8');
    if (p === '/api/estado' && req.method === 'GET') return send(res, 200, { contactos: db.contactos, modo, proveedores: prov.estadoProveedores(), alertas: prov.listaAlertas(), mensajes: db.mensajes, duplicadosEvitados: db.duplicados || 0 });
    if (p === '/v1/contactos' && req.method === 'GET') return send(res, 200, Object.entries(db.contactos).map(([tel, c]) => ({ ...c, telefono: tel })));
    if (p === '/v1/contactos' && req.method === 'POST') {
      const lista = Array.isArray(body) ? body : [body];
      const guardados = lista.map(guardarContacto).filter(Boolean).length;
      return send(res, 200, { guardados, total: Object.keys(db.contactos).length });
    }
    if (p === '/admin/enviar' && req.method === 'POST') {
      const texto = String(body && body.texto || '').trim();
      const contacto = body && contactoDe(body.destino);
      if (!texto) return send(res, 400, { error: 'Escribe el mensaje.' });
      if (!body.destino || !prov.localPhone(body.destino)) return send(res, 400, { error: 'Elige un número de celular válido.' });
      const m = create({ clave: 'panel-' + Date.now(), canal: 'WHATSAPP', destino: body.destino, cuerpo: texto, tipo: 'MENSAJE', paciente: contacto ? { id: contacto.pacienteId, nombre: contacto.nombre, demo: contacto.demo } : null }, false);
      db.mensajes.push(m);
      await deliver(m);
      save(); log('panel ' + m.estado);
      return send(res, m.estado === 'ENVIADO' ? 200 : 502, publicView(m));
    }
    if (p === '/admin/revisar' && req.method === 'POST') {
      await prov.revisarProveedores();
      return send(res, 200, { proveedores: prov.estadoProveedores(), alertas: prov.listaAlertas() });
    }
    if (p === '/api/alertas' && req.method === 'GET') return send(res, 200, prov.listaAlertas());
    if (p === '/admin/prueba' && req.method === 'POST') {
      // Envío real de prueba escrito a mano por el operador en el panel (por eso no aplica la lista de permitidos).
      const canal = body && body.canal;
      if (!['WHATSAPP', 'EMAIL'].includes(canal) || !body.destino) return send(res, 400, { error: 'Indica canal y destino.' });
      if (canal === 'WHATSAPP' && !prov.waActivo()) return send(res, 400, { error: 'WhatsApp no está configurado: faltan WA_TOKEN y WA_PHONE_NUMBER_ID en el archivo .env.' });
      if (canal === 'EMAIL' && !prov.correoActivo()) return send(res, 400, { error: 'El correo no está configurado: faltan SMTP_HOST, SMTP_USUARIO y SMTP_CLAVE en el archivo .env.' });
      const m = create({
        clave: 'prueba-' + Date.now(), canal, destino: body.destino, tipo: 'PRUEBA',
        asunto: 'Prueba de envío · Clínica Dental Cárdenas',
        cuerpo: 'Hola, este es un mensaje de prueba del sistema de la Clínica Dental Cárdenas. Si lo recibiste, los avisos de citas ya llegan a tu ' + (canal === 'EMAIL' ? 'correo.' : 'WhatsApp.'),
      }, false);
      m.forzarReal = true;
      if (body.plantilla) m.plantilla = String(body.plantilla);
      db.mensajes.push(m);
      await deliver(m);
      delete m.forzarReal;
      save(); log('prueba ' + m.estado);
      return send(res, m.estado === 'ENVIADO' ? 200 : 502, publicView(m));
    }
    if (p === '/admin/modo' && req.method === 'POST') {
      if (['normal', 'caido'].includes(body && body.modo)) modo = body.modo;
      return send(res, 200, { modo });
    }
    if (p === '/admin/enviar-pendientes' && req.method === 'POST') {
      const n = await tick(true);
      return send(res, 200, { enviados: n });
    }
    if (p === '/v1/mensajes' && req.method === 'GET') {
      const d = url.searchParams.get('destino');
      return send(res, 200, db.mensajes.filter((m) => !d || m.destino === d).map(publicView));
    }

    if (modo === 'caido' && p.startsWith('/v1/')) { log(503); return send(res, 503, { error: 'Servicio de notificaciones no disponible' }); }

    if (p === '/v1/mensajes' && req.method === 'POST') {
      const errs = validate(body, false);
      if (errs.length) { log(400); return send(res, 400, { error: errs.join('; ') }); }
      const prev = byKey(body.clave);
      if (prev) { db.duplicados = (db.duplicados || 0) + 1; save(); log('200 duplicado'); return send(res, 200, { ...publicView(prev), duplicado: true }); }
      const m = create(body, false);
      db.mensajes.push(m);
      await deliver(m);
      save(); log(m.estado + (m.real ? ' (real)' : ''));
      return send(res, 201, { ...publicView(m), duplicado: false });
    }
    if (p === '/v1/recordatorios' && req.method === 'POST') {
      const errs = validate(body, true);
      if (errs.length) { log(400); return send(res, 400, { error: errs.join('; ') }); }
      const prev = byKey(body.clave);
      if (prev) { db.duplicados = (db.duplicados || 0) + 1; save(); log('200 duplicado'); return send(res, 200, { ...publicView(prev), duplicado: true }); }
      const m = create(body, true);
      db.mensajes.push(m); save(); log('PROGRAMADO');
      return send(res, 201, { ...publicView(m), duplicado: false });
    }
    const del = p.match(/^\/v1\/recordatorios\/(.+)$/);
    if (del && req.method === 'DELETE') {
      const m = byKey(decodeURIComponent(del[1]));
      if (!m) { log(404); return send(res, 404, { error: 'Recordatorio no encontrado' }); }
      if (m.estado === 'PROGRAMADO') { m.estado = 'ANULADO'; m.anuladoEn = now().toISOString(); save(); }
      log(m.estado);
      return send(res, 200, publicView(m));
    }
    send(res, 404, { error: 'No encontrado' });
  });
});

server.listen(PORT, () => console.log(`Notificaciones en :${PORT} · webhook ${WEBHOOK_URL || '(desactivado)'}`));
