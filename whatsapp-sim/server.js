/*
 * Simulador de WhatsApp · Clínica Dental Cárdenas
 * -----------------------------------------------------------------------------
 * Reemplaza a WhatsApp Cloud API de Meta para pruebas y demostraciones: el sistema le envía los mensajes con el
 * mismo formato que a Meta, pero NADA llega a celulares reales. Todo se ve en un panel tipo chat, donde se puede
 * responder como si fuera el paciente ("1" confirma la cita, "2" pide reprogramar).
 *
 * API compatible con WhatsApp Cloud API (subconjunto que usa el microservicio de notificaciones):
 *   POST /{version}/{phoneNumberId}/messages          envía texto o plantilla       (Authorization: Bearer TOKEN)
 *   GET  /{version}/{phoneNumberId}?fields=...        datos del número de la clínica
 *   GET  /{version}/{wabaId}/message_templates?name=  plantillas aprobadas (las 4 de la clínica + hello_world + aviso_clinica)
 *   POST /{version}/{wabaId}/message_templates        crea una plantilla (queda aprobada al instante)
 * Webhook hacia el sistema (igual que Meta, firmado con X-Hub-Signature-256 si hay APP_SECRET):
 *   estados  sent → delivered → read   y   mensajes que escribe el "paciente" desde el panel
 * Panel y API del simulador:
 *   GET  /                      panel tipo WhatsApp
 *   GET  /sim/chats             conversaciones (JSON)
 *   POST /sim/responder         {telefono, texto, nombre?}  el paciente escribe a la clínica
 *   POST /sim/leer              {telefono}                  el paciente abre el chat (marca "leído")
 *   POST /sim/telefono          {telefono, apagado}         celular apagado: los mensajes quedan "enviados"
 *   POST /sim/sin-whatsapp      {telefono, activo}          número sin WhatsApp: Meta acepta y luego avisa "failed"
 *   GET  /sim/plantillas                                    plantillas aprobadas del simulador
 *   POST /sim/modo              {modo: normal|caido|lento}  simula caídas o lentitud del proveedor
 *   POST /sim/borrar                                        vacía las conversaciones
 *   GET  /health
 * Node 20, sin dependencias.
 */
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const env = (k, d = '') => (process.env[k] ?? d).trim();
const PORT = Number(env('PORT', '8094'));
const DATA_FILE = env('DATA_FILE', '/data/whatsapp.json');
const TOKEN = env('SIM_TOKEN', 'token-simulador-whatsapp');
const PHONE_ID = env('SIM_PHONE_ID', '100000000000001');
const WABA_ID = env('SIM_WABA_ID', '200000000000001');
const NUMERO = env('SIM_NUMERO', '+51 987 654 000');
const NOMBRE = env('SIM_NOMBRE', 'Clínica Dental Cárdenas');
const WEBHOOK_URL = env('WEBHOOK_URL');            // p. ej. http://notificaciones:8093/webhook/whatsapp
const APP_SECRET = env('APP_SECRET');              // firma de los avisos (igual que WA_APP_SECRET)
const CONTACTOS_URL = env('CONTACTOS_URL');        // nombres de los pacientes (agenda del microservicio)
// Con AUTH_URL (login del sistema), el panel pide usuario y contraseña del personal: admin o con permiso NOTIFICACIONES.
const AUTH_URL = env('AUTH_URL');                  // p. ej. http://backend:8080/api/auth/login
const VENTANA_24H = env('SIM_VENTANA_24H', 'false').toLowerCase() === 'true';
const DEMORA_ENTREGA_MS = Number(env('SIM_DEMORA_ENTREGA_MS', '1500'));

// Plantillas aprobadas (las mismas que notificaciones/crear-plantillas.js crea en Meta). {{n}} = parámetro n.
const PLANTILLAS = {
  hello_world: { idioma: 'en_US', texto: 'Hello World\n\nWelcome and congratulations!! This message demonstrates your ability to send a WhatsApp message notification from the Cloud API, hosted by Meta. Thank you for taking the time to test with us.' },
  aviso_clinica: { texto: 'Hola, te escribe Clínica Dental Cárdenas: {{1}} Gracias por tu preferencia.' },
  recordatorio_cita: { texto: 'Hola {{1}} 👋\n\nTe recordamos tu cita en *Clínica Dental Cárdenas*:\n\n📅 *{{2}}*\n🦷 {{3}}\n📍 {{4}}\n\nResponde *1* para confirmar tu asistencia o *2* si necesitas reprogramar.' },
  cita_registrada: { texto: 'Hola {{1}} 👋\n\nTu cita en *Clínica Dental Cárdenas* quedó registrada ✅\n\n📅 *{{2}}*\n🦷 {{3}}\n📍 {{4}}\n\nTe enviaremos un recordatorio antes de tu cita.' },
  cita_reprogramada: { texto: 'Hola {{1}}, tu cita en *Clínica Dental Cárdenas* fue reprogramada 🔁\n\nAntes: {{2}}\n📅 Nuevo horario: *{{3}}*\n🦷 {{4}}\n📍 {{5}}\n\nSi el nuevo horario no te acomoda, respóndenos por aquí.' },
  cita_cancelada: { texto: 'Hola {{1}}, tu cita del *{{2}}* con {{3}} en *Clínica Dental Cárdenas* fue cancelada.\n\nPara programar una nueva cita, reserva desde la app o llámanos al {{4}}. ¡Te esperamos!' },
};
for (const [n, t] of Object.entries(load().plantillas || {})) PLANTILLAS[n] = t;
for (const p of Object.values(PLANTILLAS)) p.parametros = new Set((p.texto.match(/\{\{\d+\}\}/g) || [])).size;

let modo = 'normal';
let db = load();

function load() {
  try {
    const d = JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
    return { chats: d.chats || {}, apagados: d.apagados || [], sinWhatsapp: d.sinWhatsapp || [], plantillas: d.plantillas || {} };
  } catch { return { chats: {}, apagados: [], sinWhatsapp: [], plantillas: {} }; }
}
let saveTimer = null;
function save() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    try { fs.mkdirSync(path.dirname(DATA_FILE), { recursive: true }); fs.writeFileSync(DATA_FILE, JSON.stringify(db)); }
    catch (e) { console.warn('No se pudo guardar:', e.message); }
  }, 200);
}

const nowIso = () => new Date().toISOString();
const wamid = () => 'wamid.SIM' + crypto.randomBytes(12).toString('base64url');
/** "51987654321" o "987 654 321" → "987654321" (celular peruano) o null. */
function local(v) {
  const d = String(v || '').replace(/\D/g, '');
  const l = d.startsWith('51') && d.length === 11 ? d.slice(2) : d;
  return /^9\d{8}$/.test(l) ? l : null;
}
function chat(tel) {
  if (!db.chats[tel]) db.chats[tel] = { telefono: tel, nombre: null, mensajes: [], ultimoEntrante: null };
  return db.chats[tel];
}

// ---------------------------------------------------------------- nombres de los pacientes
let contactos = {};
async function cargarContactos() {
  if (!CONTACTOS_URL) return;
  try {
    const r = await fetch(CONTACTOS_URL, { signal: AbortSignal.timeout(4000) });
    if (!r.ok) return;
    const list = await r.json();
    const m = {};
    for (const c of Array.isArray(list) ? list : []) { const t = local(c.telefono); if (t) m[t] = c.nombre; }
    contactos = m;
  } catch { /* el microservicio aún no arranca */ }
}
setInterval(cargarContactos, 30_000).unref();
setTimeout(cargarContactos, 3_000).unref();
const nombreDe = (tel) => db.chats[tel]?.nombre || contactos[tel] || null;

// ---------------------------------------------------------------- webhook (igual que Meta)
const pendientes = []; // avisos que no se pudieron entregar: se reintentan
async function webhook(value) {
  if (!WEBHOOK_URL) return false;
  const body = JSON.stringify({ object: 'whatsapp_business_account', entry: [{ id: WABA_ID, changes: [{ field: 'messages', value: {
    messaging_product: 'whatsapp', metadata: { display_phone_number: NUMERO.replace(/\D/g, ''), phone_number_id: PHONE_ID }, ...value,
  } }] }] });
  const headers = { 'Content-Type': 'application/json' };
  if (APP_SECRET) headers['X-Hub-Signature-256'] = 'sha256=' + crypto.createHmac('sha256', APP_SECRET).update(body).digest('hex');
  try {
    const r = await fetch(WEBHOOK_URL, { method: 'POST', headers, body, signal: AbortSignal.timeout(8000) });
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return true;
  } catch (e) {
    console.warn('webhook →', WEBHOOK_URL, e.message);
    pendientes.push(value);
    return false;
  }
}
setInterval(async () => {
  const lote = pendientes.splice(0, pendientes.length);
  for (const v of lote) await webhook(v);
}, 15_000).unref();

function estado(m, nuevo, extra) {
  const orden = { sent: 1, delivered: 2, read: 3, failed: 9 };
  if ((orden[nuevo] || 0) <= (orden[m.estado] || 0)) return;
  m.estado = nuevo;
  if (nuevo === 'failed') m.error = extra?.errors?.[0]?.message || extra?.errors?.[0]?.title || 'No se pudo entregar';
  m[nuevo === 'read' ? 'leidoEn' : nuevo === 'delivered' ? 'entregadoEn' : 'enviadoEn'] = nowIso();
  save();
  webhook({ statuses: [{ id: m.id, status: nuevo, timestamp: String(Math.floor(Date.now() / 1000)), recipient_id: '51' + m.telefono, ...(extra || {}) }] });
}

// ---------------------------------------------------------------- API compatible con Meta
function metaError(res, status, code, message, details) {
  send(res, status, { error: { message, type: 'OAuthException', code, error_data: details ? { messaging_product: 'whatsapp', details } : undefined, fbtrace_id: 'SIM' + crypto.randomBytes(5).toString('hex') } });
}

/** Arma el texto que vería el paciente. Devuelve {error} con el mismo código que usaría Meta si algo no cuadra. */
function texto(body) {
  if (body.type === 'text') return { texto: String(body.text?.body || ''), plantilla: null };
  if (body.type === 'interactive') {
    // Igual que Meta: mensaje con hasta 3 botones de respuesta de máximo 20 caracteres.
    const it = body.interactive || {};
    const bts = (it.action && it.action.buttons) || [];
    if (it.type !== 'button' || !bts.length || bts.length > 3) return { error: [400, 131009, '(#131009) Parameter value is not valid', 'Los mensajes interactivos llevan de 1 a 3 botones.'] };
    if (bts.some((b) => !b.reply || !b.reply.title || String(b.reply.title).length > 20)) return { error: [400, 131009, '(#131009) Parameter value is not valid', 'Cada botón necesita un título de hasta 20 caracteres.'] };
    return { texto: String(it.body?.text || ''), plantilla: null, botones: bts.map((b) => ({ id: String(b.reply.id || ''), titulo: String(b.reply.title) })) };
  }
  if (body.type === 'template') {
    const t = body.template || {};
    const def = PLANTILLAS[t.name];
    if (!def) return { error: [404, 132001, '(#132001) Template name does not exist in the translation', `La plantilla "${t.name}" no existe en el simulador.`] };
    const params = (t.components || []).filter((c) => c.type === 'body').flatMap((c) => (c.parameters || []).map((p) => String(p.text ?? '')));
    if (params.length !== def.parametros) {
      return { error: [400, 132000, '(#132000) Number of parameters does not match the expected number of params',
        `La plantilla "${t.name}" espera ${def.parametros} parámetro(s) y llegaron ${params.length}.`] };
    }
    if (params.some((x) => /\n|\t| {5,}/.test(x))) {
      return { error: [400, 132012, '(#132012) Parameter format does not match format in the created template',
        'Los parámetros no pueden tener saltos de línea, tabulaciones ni más de 4 espacios seguidos.'] };
    }
    return { texto: def.texto.replace(/\{\{(\d+)\}\}/g, (_, n) => params[Number(n) - 1] ?? ''), plantilla: t.name };
  }
  return null;
}

async function enviar(req, res, body) {
  const auth = req.headers.authorization || '';
  if (auth !== 'Bearer ' + TOKEN) return metaError(res, 401, 190, 'Invalid OAuth access token - Cannot parse access token');
  if (modo === 'caido') return metaError(res, 503, 2, 'Service temporarily unavailable (simulador en modo caído)');
  if (modo === 'lento') await new Promise((r) => setTimeout(r, 12_000));
  if (!body || body.messaging_product !== 'whatsapp') return metaError(res, 400, 100, '(#100) The parameter messaging_product is required.');
  const tel = local(body.to);
  if (!tel) return metaError(res, 400, 131026, 'Message undeliverable.', 'El número no es un celular peruano válido (51 9XX XXX XXX).');
  const t = texto(body);
  if (t && t.error) return metaError(res, t.error[0], t.error[1], t.error[2], t.error[3]);
  if (!t) return metaError(res, 400, 131051, 'Unsupported message type.', 'El simulador acepta mensajes de tipo text, template e interactive.');
  if (body.type === 'text' && !t.texto.trim()) return metaError(res, 400, 100, '(#100) Param text[\'body\'] must be a non-empty string.');
  const c = chat(tel);
  if (VENTANA_24H && (body.type === 'text' || body.type === 'interactive') && (!c.ultimoEntrante || Date.now() - Date.parse(c.ultimoEntrante) > 24 * 3600_000)) {
    return metaError(res, 400, 131047, 'Re-engagement message', 'Más de 24 horas desde el último mensaje del paciente: usa una plantilla.');
  }
  const m = { id: wamid(), dir: 'out', telefono: tel, texto: t.texto.slice(0, 4096), plantilla: t.plantilla, botones: t.botones || null, estado: null, creadoEn: nowIso() };
  c.mensajes.push(m);
  if (c.mensajes.length > 500) c.mensajes.splice(0, c.mensajes.length - 500);
  save();
  console.log(`${nowIso()} → ${tel}: ${m.plantilla ? '[' + m.plantilla + '] ' : ''}${m.texto.slice(0, 80).replace(/\n/g, ' ')}`);
  send(res, 200, { messaging_product: 'whatsapp', contacts: [{ input: body.to, wa_id: '51' + tel }], messages: [{ id: m.id, message_status: 'accepted' }] });
  // Igual que Meta: primero "enviado" y, si el celular está encendido, "entregado" un momento después.
  setTimeout(() => {
    if (db.sinWhatsapp.includes(tel)) {
      // Meta acepta el envío y luego avisa que no se pudo entregar.
      return estado(m, 'failed', { errors: [{ code: 131026, title: 'Message undeliverable', message: 'El número no tiene WhatsApp (simulado)' }] });
    }
    estado(m, 'sent');
    if (!db.apagados.includes(tel)) setTimeout(() => estado(m, 'delivered'), DEMORA_ENTREGA_MS);
  }, 300);
}

/** El paciente escribe a la clínica desde el panel: llega al sistema como un mensaje entrante de Meta. */
async function responder(body) {
  const tel = local(body && body.telefono);
  const txt = String(body && body.texto || '').trim();
  if (!tel) return { status: 400, body: { error: 'Número de celular inválido (9 dígitos).' } };
  if (!txt) return { status: 400, body: { error: 'Escribe el mensaje.' } };
  const c = chat(tel);
  if (body.nombre) c.nombre = String(body.nombre).trim().slice(0, 60);
  // Al responder, el paciente ya leyó lo anterior.
  for (const m of c.mensajes) if (m.dir === 'out' && m.estado !== 'failed') { if (!m.estado || m.estado === 'sent') estado(m, 'delivered'); estado(m, 'read'); }
  const m = { id: wamid(), dir: 'in', telefono: tel, texto: txt.slice(0, 4096), estado: 'received', creadoEn: nowIso() };
  c.mensajes.push(m);
  c.ultimoEntrante = m.creadoEn;
  save();
  const boton = body.boton && body.boton.id ? { id: String(body.boton.id), title: txt.slice(0, 20) } : null;
  if (boton) m.boton = true;
  const entrante = boton
    ? { from: '51' + tel, id: m.id, timestamp: String(Math.floor(Date.now() / 1000)), type: 'interactive', interactive: { type: 'button_reply', button_reply: boton } }
    : { from: '51' + tel, id: m.id, timestamp: String(Math.floor(Date.now() / 1000)), type: 'text', text: { body: m.texto } };
  const ok = await webhook({ contacts: [{ profile: { name: nombreDe(tel) || 'Paciente' }, wa_id: '51' + tel }], messages: [entrante] });
  return { status: 200, body: { enviado: true, entregadoAlSistema: ok, id: m.id } };
}

// ---------------------------------------------------------------- panel
function panel() {
  return `<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Simulador de WhatsApp · Clínica Cárdenas</title>
<style>
*{box-sizing:border-box}body{margin:0;font-family:"Segoe UI",Helvetica,Arial,sans-serif;background:#d1d7db;color:#111b21;height:100vh;display:flex;flex-direction:column;font-size:14.5px}
.top{background:#00a884;height:6px}
.app{flex:1;display:grid;grid-template-columns:380px minmax(0,1fr);min-height:0;max-width:1500px;width:100%;margin:0 auto;box-shadow:0 6px 18px rgba(11,20,26,.15)}
aside{background:#fff;display:flex;flex-direction:column;min-height:0;border-right:1px solid #e9edef}
.yo{background:#f0f2f5;padding:10px 16px;display:flex;align-items:center;gap:12px}
.yo .av{background:#00a884;color:#fff}.yo b{display:block}.yo small{color:#667781}
.stats{display:flex;gap:6px;padding:8px 12px;border-bottom:1px solid #f0f2f5;flex-wrap:wrap}
.st{flex:1;min-width:70px;background:#f7f8fa;border-radius:8px;padding:6px 8px;text-align:center}.st b{display:block;font-size:17px}.st span{font-size:11.5px;color:#667781}
.modo{display:flex;gap:6px;align-items:center;padding:8px 12px;border-bottom:1px solid #f0f2f5;font-size:12.5px;color:#54656f;flex-wrap:wrap}
.pill{border:1px solid #d1d7db;background:#fff;border-radius:14px;padding:4px 10px;cursor:pointer;font:inherit;font-size:12.5px}
.pill.on{background:#00a884;border-color:#00a884;color:#fff}.pill.rojo.on{background:#ea0038;border-color:#ea0038}.pill.amb.on{background:#f7b928;border-color:#f7b928;color:#111}
.buscar{padding:8px 12px}.buscar input{width:100%;background:#f0f2f5;border:0;border-radius:8px;padding:9px 12px;font:inherit}
#lista{flex:1;overflow:auto}
.item{padding:10px 14px;display:flex;gap:12px;align-items:center;cursor:pointer;border-bottom:1px solid #f0f2f5}
.item:hover{background:#f5f6f6}.item.sel{background:#f0f2f5}
.av{width:46px;height:46px;border-radius:50%;background:#dfe5e7;display:grid;place-items:center;font-weight:700;color:#54656f;flex:none;font-size:15px}
.item .c{flex:1;min-width:0}.item .n{display:flex;justify-content:space-between;gap:6px}.item .n b{font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.item .n small{color:#667781;font-size:12px;flex:none}.item .n small.nu{color:#00a884;font-weight:600}
.item .p{display:flex;gap:6px;color:#667781;font-size:13.5px;margin-top:2px}.item .p span{white-space:nowrap;overflow:hidden;text-overflow:ellipsis;flex:1}
.badge{background:#25d366;color:#fff;border-radius:12px;min-width:20px;height:20px;font-size:12px;display:grid;place-items:center;padding:0 6px;font-weight:600}
.nuevo{display:flex;gap:6px;padding:0 12px 8px}.nuevo input{flex:1;border:1px solid #d1d7db;border-radius:8px;padding:7px 10px;font:inherit}
#chat{display:flex;flex-direction:column;min-height:0;background:#efeae2 url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='80' height='80'%3E%3Cg fill='%23d9d3c9' fill-opacity='.35'%3E%3Ccircle cx='10' cy='10' r='2'/%3E%3Ccircle cx='50' cy='40' r='2'/%3E%3Cpath d='M30 60h6v6h-6z'/%3E%3C/g%3E%3C/svg%3E")}
.cab{background:#f0f2f5;padding:9px 16px;display:flex;align-items:center;gap:12px;flex-wrap:wrap}
.cab .q{flex:1;min-width:150px}.cab small{color:#667781;display:block}
.chk{font-size:12.5px;color:#54656f;display:flex;align-items:center;gap:4px;cursor:pointer}
#msgs{flex:1;overflow:auto;padding:16px 6%}
.dia{text-align:center;margin:10px 0}.dia span{background:#fff;border-radius:7px;padding:5px 12px;font-size:12.5px;color:#54656f;box-shadow:0 1px .5px rgba(11,20,26,.13)}
.b{max-width:min(560px,75%);margin:3px 0;padding:6px 8px 8px 9px;border-radius:7.5px;position:relative;white-space:pre-wrap;line-height:1.38;box-shadow:0 1px .5px rgba(11,20,26,.13);word-wrap:break-word;clear:both}
.b.in{background:#fff;float:left;border-top-left-radius:0}.b.out{background:#d9fdd3;float:right;border-top-right-radius:0}
.bts{display:flex;flex-direction:column;gap:1px;margin:8px -8px -8px -9px;border-top:1px solid rgba(0,0,0,.08)}.bt{border:0;background:#fff;color:#027eb5;font:inherit;font-weight:600;padding:9px;cursor:pointer}.bt:hover{background:#f5f6f6}.b.out .bt{background:#e9fbe5}
.b.fail{background:#ffe3e3}
.fila{overflow:hidden}
.b .h{float:right;margin:8px 0 -5px 10px;font-size:11px;color:#667781;white-space:nowrap}
.tk{font-size:13px;margin-left:3px;letter-spacing:-4px;margin-right:3px}.tk.read{color:#53bdeb}.tk.fail{color:#ea0038;letter-spacing:0}
.tpl{display:inline-block;font-size:11px;color:#00a884;font-weight:600;margin-bottom:3px;background:#e7fce3;border-radius:4px;padding:0 5px}
.err{display:block;font-size:12px;color:#ea0038;margin-top:3px}
.compo{display:flex;gap:8px;padding:8px 12px;background:#f0f2f5;align-items:center;flex-wrap:wrap}
.compo input{flex:1;min-width:160px;border:0;border-radius:8px;padding:11px 12px;font:inherit}
.send{background:#00a884;color:#fff;border:0;border-radius:50%;width:42px;height:42px;cursor:pointer;font-size:18px}
.rap{border:1px solid #c7ced2;background:#fff;border-radius:16px;padding:6px 12px;cursor:pointer;font:inherit;font-size:13px}
.como{font-size:12px;color:#54656f;width:100%}
.vacio{margin:auto;color:#667781;text-align:center;padding:40px;line-height:1.6}.vacio b{display:block;font-size:26px;color:#41525d;font-weight:300;margin-bottom:8px}
.aviso{background:#fff3c4;color:#54656f;font-size:12.5px;padding:6px 14px;text-align:center}
html{overflow-x:hidden}.app>*{min-width:0}
@media(max-width:820px){body{height:auto;min-height:100vh}.app{grid-template-columns:minmax(0,1fr);grid-template-rows:auto minmax(78vh,1fr)}aside{border-right:0;border-bottom:1px solid #e9edef}#lista{max-height:36vh;min-height:120px}.stats .st{min-width:56px}.b{max-width:88%}#msgs{padding:12px 3%}}
</style></head><body><div class="top"></div>
<div class="app"><aside>
 <div class="yo"><div class="av">CC</div><div><b>${NOMBRE}</b><small>${NUMERO} · Simulador de WhatsApp</small></div></div>
 <div class="stats" id="stats"></div>
 <div class="modo">Proveedor: <button class="pill" data-m="normal">Normal</button><button class="pill amb" data-m="lento">Lento (12 s)</button><button class="pill rojo" data-m="caido">Caído</button>
   <span style="flex:1"></span><button class="pill" id="borrar" title="Vaciar las conversaciones">🗑</button></div>
 <div class="buscar"><input id="q" placeholder="Buscar paciente o número"></div>
 <div class="nuevo"><input id="nt" placeholder="Escribir como un número nuevo: 9XXXXXXXX" maxlength="11"><button class="pill" id="nb">Abrir</button></div>
 <div id="lista"></div>
</aside>
<section id="chat"><div class="vacio"><b>Simulador de WhatsApp</b>Aquí llegan los mensajes que el sistema envía a los pacientes<br>(recordatorios, citas registradas, reprogramadas y canceladas).<br>Nada sale a celulares reales. Abre un chat para responder como si fueras el paciente.</div></section></div>
<script>
let data={chats:[],modo:'normal'},sel=null,firma='',filtro='';
const esc=s=>String(s??'').replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
/* Formato de WhatsApp: *negrita* _cursiva_ ~tachado~ \`\`\`mono\`\`\` */
const wa=s=>esc(s).replace(/\\*([^*\\n]+)\\*/g,'<b>$1</b>').replace(/(^|\\s)_([^_\\n]+)_/g,'$1<i>$2</i>').replace(/~([^~\\n]+)~/g,'<s>$1</s>').replace(/\`\`\`([^\`]+)\`\`\`/g,'<code>$1</code>');
const hh=s=>s?new Date(s).toLocaleTimeString('es-PE',{hour:'2-digit',minute:'2-digit'}):'';
const dd=s=>{const d=new Date(s),h=new Date();const y=new Date();y.setDate(h.getDate()-1);if(d.toDateString()===h.toDateString())return 'HOY';if(d.toDateString()===y.toDateString())return 'AYER';return d.toLocaleDateString('es-PE',{weekday:'long',day:'numeric',month:'long'}).toUpperCase();};
const corto=s=>{const d=new Date(s);return d.toDateString()===new Date().toDateString()?hh(s):d.toLocaleDateString('es-PE',{day:'2-digit',month:'2-digit'});};
const fmtTel=t=>'+51 '+t.slice(0,3)+' '+t.slice(3,6)+' '+t.slice(6);
const ini=n=>(n||'').split(/\\s+/).filter(Boolean).slice(0,2).map(x=>x[0]).join('').toUpperCase()||'👤';
const tk=m=>m.estado==='read'?'<span class="tk read">✓✓</span>':m.estado==='delivered'?'<span class="tk">✓✓</span>':m.estado==='sent'?'<span class="tk">✓</span>':m.estado==='failed'?'<span class="tk fail">!</span>':'<span class="tk">🕓</span>';
const est=m=>m.estado==='read'?'Leído '+hh(m.leidoEn):m.estado==='delivered'?'Entregado '+hh(m.entregadoEn):m.estado==='sent'?'Enviado (sin entregar)':m.estado==='failed'?'No entregado':'Pendiente';
const noLeidos=c=>c.mensajes.filter(m=>m.dir==='out'&&(!c.vistoEn||m.creadoEn>c.vistoEn)).length;
async function api(p,b){const r=await fetch(p,b===undefined?{}:{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(b)});return r.json();}
async function load(){data=await api('sim/chats');const f=JSON.stringify(data)+filtro;if(f===firma)return;firma=f;render();}
function render(){
  document.querySelectorAll('[data-m]').forEach(b=>b.classList.toggle('on',b.dataset.m===data.modo));
  const all=data.chats.flatMap(c=>c.mensajes);const hoy=new Date().toDateString();const out=all.filter(m=>m.dir==='out'&&new Date(m.creadoEn).toDateString()===hoy);
  document.getElementById('stats').innerHTML=[[out.length,'enviados hoy'],[out.filter(m=>m.estado==='delivered'||m.estado==='read').length,'entregados'],[out.filter(m=>m.estado==='read').length,'leídos'],[all.filter(m=>m.dir==='in'&&new Date(m.creadoEn).toDateString()===hoy).length,'respuestas'],[out.filter(m=>m.estado==='failed').length,'fallidos']].map(([n,l])=>'<div class="st"><b>'+n+'</b><span>'+l+'</span></div>').join('');
  const q=filtro.toLowerCase();
  const cs=data.chats.filter(c=>!q||(c.nombre||'').toLowerCase().includes(q)||c.telefono.includes(q.replace(/\\D/g,'')||'§'));
  const l=document.getElementById('lista');
  l.innerHTML=cs.map(c=>{const u=c.mensajes[c.mensajes.length-1]||{};const n=c.telefono===sel?0:noLeidos(c);
    return '<div class="item'+(c.telefono===sel?' sel':'')+'" data-t="'+c.telefono+'"><div class="av">'+esc(ini(c.nombre))+'</div><div class="c"><div class="n"><b>'+esc(c.nombre||fmtTel(c.telefono))+'</b><small class="'+(n?'nu':'')+'">'+(u.creadoEn?corto(u.creadoEn):'')+'</small></div><div class="p">'+(u.dir==='out'?tk(u):'')+'<span>'+esc((u.texto||'').replace(/[*_~]/g,'').split('\\n').filter(Boolean)[0]||'')+'</span>'+(n?'<span class="badge">'+n+'</span>':'')+'</div></div></div>';}).join('')||'<div class="vacio">'+(q?'Sin resultados.':'Aún no hay conversaciones.<br>Programa una cita en el sistema y aparecerá aquí.')+'</div>';
  l.querySelectorAll('.item').forEach(i=>i.onclick=()=>abrir(i.dataset.t));
  pintarChat();
}
function pintarChat(){
  if(!sel)return;
  const box=document.getElementById('chat');
  const c=data.chats.find(x=>x.telefono===sel)||{telefono:sel,nombre:null,mensajes:[]};
  const m0=document.getElementById('msgs');const abajo=!m0||m0.scrollTop+m0.clientHeight>=m0.scrollHeight-40;
  const borrador=document.getElementById('txt')?.value||'';
  let dia='',html='';
  for(const m of c.mensajes){const d=dd(m.creadoEn);if(d!==dia){dia=d;html+='<div class="dia"><span>'+d+'</span></div>';}
    const f=m.estado==='failed';
    html+='<div class="fila"><div class="b '+m.dir+(f?' fail':'')+'" title="'+(m.dir==='out'?esc(est(m)):'Escrito por el paciente')+'">'+(m.plantilla?'<span class="tpl">Plantilla · '+esc(m.plantilla)+'</span><br>':'')+wa(m.texto)+(f?'<span class="err">⚠ '+esc(m.error||'No se pudo entregar')+'</span>':'')+'<span class="h">'+hh(m.creadoEn)+(m.dir==='out'?tk(m):'')+'</span>'+(m.botones&&m.botones.length?'<div class="bts">'+m.botones.map(b=>'<button class="bt" data-bid="'+esc(b.id)+'" data-bt="'+esc(b.titulo)+'">↩ '+esc(b.titulo)+'</button>').join('')+'</div>':'')+'</div></div>';}
  box.innerHTML='<div class="cab"><div class="av">'+esc(ini(c.nombre))+'</div><div class="q"><b>'+esc(c.nombre||'Paciente sin registrar')+'</b><small>'+fmtTel(c.telefono)+'</small></div>'+
    '<label class="chk" title="Los mensajes quedan con un solo check hasta que se encienda"><input type="checkbox" id="apag" '+(c.apagado?'checked':'')+'> Celular apagado</label>'+
    '<label class="chk" title="Meta acepta el envío y luego avisa que no se entregó"><input type="checkbox" id="sinwa" '+(c.sinWhatsapp?'checked':'')+'> No tiene WhatsApp</label></div>'+
    (c.apagado?'<div class="aviso">📴 Celular apagado: los mensajes nuevos quedan "enviados" (✓) hasta que lo enciendas.</div>':'')+
    (c.sinWhatsapp?'<div class="aviso">🚫 Este número no tiene WhatsApp: los mensajes llegan al sistema como fallidos.</div>':'')+
    '<div id="msgs">'+(html||'<div class="vacio">Sin mensajes todavía.</div>')+'</div>'+
    '<div class="compo"><span class="como">Respondiendo como <b>'+esc(c.nombre||'el paciente')+'</b>:</span><button class="rap" data-r="1">1 · Confirmo</button><button class="rap" data-r="2">2 · Reprogramar</button><button class="rap" data-r="Gracias">Gracias 🙌</button><input id="txt" placeholder="Escribe un mensaje" autocomplete="off"><button class="send" id="env" title="Enviar">➤</button></div>';
  const msgs=document.getElementById('msgs');if(abajo)msgs.scrollTop=msgs.scrollHeight;
  const txt=document.getElementById('txt');txt.value=borrador;
  const enviar=async t=>{if(!t.trim())return;const r=await api('sim/responder',{telefono:sel,texto:t});if(r.error)alert(r.error);else if(!r.entregadoAlSistema)alert('El mensaje quedó en el simulador, pero el sistema de la clínica no respondió (se reintentará).');txt.value='';firma='';setTimeout(load,300);setTimeout(load,1500);setTimeout(load,3500);};
  document.getElementById('env').onclick=()=>enviar(txt.value);
  txt.onkeydown=e=>{if(e.key==='Enter')enviar(txt.value);};
  box.querySelectorAll('[data-r]').forEach(b=>b.onclick=()=>enviar(b.dataset.r));
  box.querySelectorAll('[data-bid]').forEach(b=>b.onclick=async()=>{const r=await api('sim/responder',{telefono:sel,texto:b.dataset.bt,boton:{id:b.dataset.bid}});if(r.error)alert(r.error);firma='';setTimeout(load,300);setTimeout(load,1500);setTimeout(load,3500);});
  document.getElementById('apag').onchange=async e=>{await api('sim/telefono',{telefono:sel,apagado:e.target.checked});firma='';load();};
  document.getElementById('sinwa').onchange=async e=>{await api('sim/sin-whatsapp',{telefono:sel,activo:e.target.checked});firma='';load();};
}
async function abrir(t){sel=t;firma='';await api('sim/leer',{telefono:t});await load();document.getElementById('txt')?.focus();}
document.querySelectorAll('[data-m]').forEach(b=>b.onclick=async()=>{await api('sim/modo',{modo:b.dataset.m});firma='';load();});
document.getElementById('borrar').onclick=async()=>{if(confirm('¿Vaciar todas las conversaciones del simulador?')){await api('sim/borrar',{});sel=null;firma='';document.getElementById('chat').innerHTML='<div class="vacio">Conversaciones vaciadas.</div>';load();}};
document.getElementById('q').oninput=e=>{filtro=e.target.value;firma='';render();};
document.getElementById('nb').onclick=()=>{const t=document.getElementById('nt').value.replace(/\\D/g,'').replace(/^51(?=9\\d{8}$)/,'');if(/^9\\d{8}$/.test(t)){document.getElementById('nt').value='';abrir(t);}else alert('Escribe un celular de 9 dígitos que empiece con 9.');};
load();setInterval(()=>{if(!(document.activeElement?.id==='txt'&&document.getElementById('txt').value))load();},2000);
</script></body></html>`;
}

// ---------------------------------------------------------------- acceso al panel
const sesiones = new Map(); // hash(usuario:clave) → vence (ms)
async function panelAutorizado(req) {
  if (!AUTH_URL) return { ok: true };
  const h = req.headers.authorization || '';
  if (!h.startsWith('Basic ')) return { ok: false };
  const llave = crypto.createHash('sha256').update(h).digest('hex');
  if ((sesiones.get(llave) || 0) > Date.now()) return { ok: true };
  const [username, ...resto] = Buffer.from(h.slice(6), 'base64').toString('utf8').split(':');
  try {
    const r = await fetch(AUTH_URL, { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password: resto.join(':') }), signal: AbortSignal.timeout(8000) });
    const j = await r.json().catch(() => ({}));
    if (!r.ok) return { ok: false, motivo: j.code === 'REQUIERE_2FA' ? 'Tu cuenta usa verificación en 2 pasos: entra con una cuenta del personal sin 2 pasos o desactívala en Mi seguridad.' : (j.message || 'Usuario o contraseña incorrectos.') };
    const u = j.user || {};
    if (u.role !== 'ADMIN' && !(u.permissions || []).includes('NOTIFICACIONES')) return { ok: false, motivo: 'Tu usuario no tiene el permiso Notificaciones.' };
    sesiones.set(llave, Date.now() + 15 * 60_000);
    return { ok: true };
  } catch (e) { return { ok: false, motivo: 'No se pudo validar el usuario con el sistema (' + e.message + ').' }; }
}

// ---------------------------------------------------------------- servidor
function send(res, status, body, type = 'application/json; charset=utf-8') {
  res.writeHead(status, { 'Content-Type': type, 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET,POST,OPTIONS', 'Access-Control-Allow-Headers': 'Content-Type,Authorization' });
  res.end(typeof body === 'string' ? body : JSON.stringify(body));
}

function vistaChats() {
  return Object.values(db.chats)
    .map((c) => ({ ...c, nombre: nombreDe(c.telefono), apagado: db.apagados.includes(c.telefono), sinWhatsapp: db.sinWhatsapp.includes(c.telefono) }))
    .sort((a, b) => String(b.mensajes.at(-1)?.creadoEn || '').localeCompare(String(a.mensajes.at(-1)?.creadoEn || '')));
}

const server = http.createServer((req, res) => {
  if (req.method === 'OPTIONS') return send(res, 204, '');
  const url = new URL(req.url, 'http://localhost');
  const chunks = [];
  req.on('data', (c) => { chunks.push(c); if (chunks.reduce((n, x) => n + x.length, 0) > 1_000_000) req.destroy(); });
  req.on('end', async () => {
    let body = null;
    try { body = chunks.length ? JSON.parse(Buffer.concat(chunks).toString('utf8')) : null; }
    catch { return metaError(res, 400, 100, '(#100) Invalid JSON'); }
    const p = url.pathname;
    try {
      if (p === '/health') return send(res, 200, { status: 'UP', modo });
      if (p === '/' || p.startsWith('/sim/')) {
        const a = await panelAutorizado(req);
        if (!a.ok) {
          res.writeHead(401, { 'WWW-Authenticate': 'Basic realm="Simulador de WhatsApp - usuario del personal", charset="UTF-8"', 'Content-Type': 'text/plain; charset=utf-8' });
          return res.end(a.motivo || 'Ingresa con tu usuario del personal de la clínica (admin o con permiso Notificaciones).');
        }
      }
      if (p === '/' && req.method === 'GET') return send(res, 200, panel(), 'text/html; charset=utf-8');
      if (p === '/sim/chats' && req.method === 'GET') return send(res, 200, { modo, numero: NUMERO, chats: vistaChats() });
      if (p === '/sim/responder' && req.method === 'POST') { const r = await responder(body); return send(res, r.status, r.body); }
      if (p === '/sim/leer' && req.method === 'POST') {
        const tel = local(body && body.telefono);
        if (tel && db.chats[tel]) db.chats[tel].vistoEn = nowIso();
        if (tel && db.chats[tel] && !db.apagados.includes(tel)) for (const m of db.chats[tel].mensajes) if (m.dir === 'out' && m.estado && m.estado !== 'failed') { estado(m, 'delivered'); estado(m, 'read'); }
        return send(res, 200, { ok: true });
      }
      if (p === '/sim/telefono' && req.method === 'POST') {
        const tel = local(body && body.telefono);
        if (!tel) return send(res, 400, { error: 'Número inválido' });
        db.apagados = db.apagados.filter((t) => t !== tel);
        if (body.apagado) db.apagados.push(tel);
        else for (const m of db.chats[tel]?.mensajes || []) if (m.dir === 'out' && m.estado === 'sent') estado(m, 'delivered');
        save();
        return send(res, 200, { telefono: tel, apagado: Boolean(body.apagado) });
      }
      if (p === '/sim/sin-whatsapp' && req.method === 'POST') {
        const tel = local(body && body.telefono);
        if (!tel) return send(res, 400, { error: 'Número inválido' });
        db.sinWhatsapp = db.sinWhatsapp.filter((t) => t !== tel);
        if (body.activo) db.sinWhatsapp.push(tel);
        save();
        return send(res, 200, { telefono: tel, sinWhatsapp: Boolean(body.activo) });
      }
      if (p === '/sim/plantillas' && req.method === 'GET') return send(res, 200, Object.entries(PLANTILLAS).map(([name, t]) => ({ name, parametros: t.parametros, texto: t.texto })));
      if (p === '/sim/modo' && req.method === 'POST') {
        if (['normal', 'caido', 'lento'].includes(body && body.modo)) modo = body.modo;
        return send(res, 200, { modo });
      }
      if (p === '/sim/borrar' && req.method === 'POST') { db.chats = {}; save(); return send(res, 200, { ok: true }); }

      // ----- API compatible con WhatsApp Cloud API
      const mMsg = p.match(/^\/v\d+\.\d+\/([^/]+)\/messages$/);
      if (mMsg && req.method === 'POST') {
        if (mMsg[1] !== PHONE_ID) return metaError(res, 400, 100, `(#100) Unsupported post request. Object with ID '${mMsg[1]}' does not exist.`);
        return enviar(req, res, body);
      }
      const mTpl = p.match(/^\/v\d+\.\d+\/([^/]+)\/message_templates$/);
      if (mTpl && req.method === 'GET') {
        if ((req.headers.authorization || '') !== 'Bearer ' + TOKEN) return metaError(res, 401, 190, 'Invalid OAuth access token');
        const name = url.searchParams.get('name');
        const data = Object.entries(PLANTILLAS).filter(([n]) => !name || n === name)
          .map(([n, t], i) => ({ name: n, status: 'APPROVED', language: t.idioma || 'es', category: 'UTILITY', id: 'SIMTPL' + (i + 1), components: [{ type: 'BODY', text: t.texto }] }));
        return send(res, 200, { data });
      }
      if (mTpl && req.method === 'POST') {
        if ((req.headers.authorization || '') !== 'Bearer ' + TOKEN) return metaError(res, 401, 190, 'Invalid OAuth access token');
        const name = String(body && body.name || '');
        const cuerpo = (body && body.components || []).find((c) => String(c.type).toUpperCase() === 'BODY');
        if (!/^[a-z0-9_]{1,512}$/.test(name) || !cuerpo || !cuerpo.text) return metaError(res, 400, 100, '(#100) Invalid parameter', 'Faltan name o el BODY de la plantilla.');
        if (PLANTILLAS[name]) return metaError(res, 400, 2388023, 'Message template already exists', `Ya existe la plantilla "${name}".`);
        PLANTILLAS[name] = { idioma: body.language || 'es', texto: String(cuerpo.text), parametros: new Set(String(cuerpo.text).match(/\{\{\d+\}\}/g) || []).size };
        db.plantillas[name] = { idioma: PLANTILLAS[name].idioma, texto: PLANTILLAS[name].texto };
        save();
        return send(res, 200, { id: 'SIMTPL' + Object.keys(PLANTILLAS).length, status: 'APPROVED', category: 'UTILITY' });
      }
      const mNum = p.match(/^\/v\d+\.\d+\/([^/]+)$/);
      if (mNum && req.method === 'GET') {
        if ((req.headers.authorization || '') !== 'Bearer ' + TOKEN) return metaError(res, 401, 190, 'Invalid OAuth access token');
        if (mNum[1] !== PHONE_ID) return metaError(res, 400, 100, `(#100) Object with ID '${mNum[1]}' does not exist.`);
        return send(res, 200, { id: PHONE_ID, display_phone_number: NUMERO, verified_name: NOMBRE, quality_rating: 'GREEN' });
      }
      send(res, 404, { error: { message: 'Ruta no soportada por el simulador', code: 100 } });
    } catch (e) {
      console.error(e);
      if (!res.headersSent) send(res, 500, { error: { message: 'Error interno del simulador: ' + e.message, code: 1 } });
    }
  });
});

server.listen(PORT, () => console.log(`Simulador de WhatsApp en :${PORT} · número ${NUMERO} · webhook ${WEBHOOK_URL || '(desactivado)'}`));
