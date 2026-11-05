/*
 * Microservicio de notificaciones · Clínica Dental Cárdenas (HU-23 / HU-24, SP-02)
 * -----------------------------------------------------------------------------
 * Envía mensajes por WhatsApp y correo electrónico. Los proveedores están simulados (como el entorno de
 * pruebas de WhatsApp Cloud API y un servidor SMTP de pruebas): los mensajes se ven en el panel web.
 *
 *   POST   /v1/mensajes                 envío inmediato            { clave, canal, destino, asunto, cuerpo, tipo, referencia }
 *   POST   /v1/recordatorios            programa un envío futuro   { ...lo anterior, enviarEn (ISO-8601) }
 *   DELETE /v1/recordatorios/{clave}    anula un recordatorio programado
 *   GET    /v1/mensajes                 lista (para el panel)
 *   POST   /admin/modo                  {"modo":"normal|caido"} simula caída del servicio
 *   POST   /admin/enviar-pendientes     envía ya los recordatorios programados (demostración)
 *   GET    /                            panel: bandeja de WhatsApp y de correo
 *
 * Reglas:
 *   - Idempotencia: la "clave" identifica el mensaje. Si llega de nuevo, NO se reenvía: se responde duplicado=true.
 *   - Los recordatorios se envían a su hora con un planificador interno y el resultado se informa al sistema
 *     central por webhook (X-Webhook-Token). Si el webhook falla, se reintenta.
 * Sin dependencias externas: Node 20.
 */
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = Number(process.env.PORT || 8093);
const DATA_FILE = process.env.DATA_FILE || '/data/notificaciones.json';
const WEBHOOK_URL = process.env.WEBHOOK_URL || '';
const WEBHOOK_TOKEN = process.env.WEBHOOK_TOKEN || 'token-webhook-clinica-cardenas';

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
const now = () => new Date();

// ---------------------------------------------------------------- proveedores simulados
function normalizePhone(v) {
  const d = String(v || '').replace(/\D/g, '');
  const local = d.startsWith('51') && d.length === 11 ? d.slice(2) : d;
  if (!/^9\d{8}$/.test(local)) return null;
  return '+51 ' + local.slice(0, 3) + ' ' + local.slice(3, 6) + ' ' + local.slice(6);
}
const validEmail = (v) => /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(String(v || ''));

function deliver(m) {
  m.intentos = (m.intentos || 0) + 1;
  if (m.canal === 'WHATSAPP') {
    const phone = normalizePhone(m.destino);
    if (!phone) { m.estado = 'FALLIDO'; m.error = 'El número no es un celular peruano válido para WhatsApp'; return m; }
    m.destinoNormalizado = phone;
    m.proveedorId = 'wamid.' + crypto.randomBytes(9).toString('hex');
  } else if (m.canal === 'EMAIL') {
    if (!validEmail(m.destino)) { m.estado = 'FALLIDO'; m.error = 'Dirección de correo inválida'; return m; }
    m.destinoNormalizado = String(m.destino).toLowerCase();
    m.proveedorId = '<' + crypto.randomBytes(8).toString('hex') + '@smtp.clinicacardenas.pe>';
  } else {
    m.estado = 'FALLIDO'; m.error = 'Canal no soportado'; return m;
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
  };
}

const publicView = (m) => ({ id: m.id, clave: m.clave, estado: m.estado, error: m.error || null, proveedorId: m.proveedorId || null,
  programadoPara: m.programadoPara, enviadoEn: m.enviadoEn || null });

// ---------------------------------------------------------------- planificador y webhook
async function notifyCentral(m) {
  if (!WEBHOOK_URL) return;
  try {
    const res = await fetch(WEBHOOK_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Webhook-Token': WEBHOOK_TOKEN },
      body: JSON.stringify({ clave: m.clave, estado: m.estado, error: m.error || null, proveedorId: m.proveedorId || null }),
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

async function tick(force = false) {
  if (modo === 'caido') return 0;
  const t = now();
  let sent = 0;
  for (const m of db.mensajes) {
    if (m.estado === 'PROGRAMADO' && (force || new Date(m.programadoPara) <= t)) {
      deliver(m);
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
.bub{background:var(--wa2);border-radius:8px 8px 2px 8px;padding:8px 10px;max-width:92%;align-self:flex-end;white-space:pre-wrap;font-size:13.5px;box-shadow:0 1px 1px rgba(0,0,0,.1)}
.bub .meta{font-size:11px;color:#667;text-align:right;margin-top:4px}.bub.fail{background:#fde2e2}
.mail{display:grid;grid-template-columns:300px 1fr;height:560px}
.inbox{border-right:1px solid var(--line);overflow:auto}.inbox div{padding:10px 14px;border-bottom:1px solid var(--line);cursor:pointer;font-size:13px}
.inbox div.on,.inbox div:hover{background:#e2f6fc}.inbox b{display:block}.inbox span{color:var(--muted);font-size:12px}
.read{padding:18px 22px;overflow:auto;white-space:pre-wrap;font-size:14px;line-height:1.5}.read h3{margin:0 0 4px}
table{width:100%;border-collapse:collapse;font-size:13px}th,td{padding:8px 12px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top}
th{background:#f6fafc;color:var(--muted)}.tag{display:inline-block;padding:2px 8px;border-radius:999px;font-size:12px;font-weight:700}
.PROGRAMADO{background:#e2f4fb;color:#055f88}.ENVIADO{background:#e1f4e9;color:#217a4a}.FALLIDO{background:#fdece4;color:#c2410c}.ANULADO{background:#eef1f4;color:#688494}
.wide{grid-column:1/-1}.empty{padding:30px;text-align:center;color:var(--muted)}
.stats{display:flex;gap:18px;font-size:13px;color:var(--muted)}.stats b{color:var(--ink);font-size:16px}
@media (max-width:900px){main{grid-template-columns:1fr}.mail{grid-template-columns:1fr;height:auto}.inbox{max-height:260px}}
</style></head><body>
<header>
  <svg width="38" height="38" viewBox="40 40 332 332" aria-hidden="true"><path d="M334 102 A154 154 0 1 0 342 300 A149.5 149.5 0 1 1 334 102 Z" fill="#F89818"/><circle cx="206" cy="206" r="78" fill="#00B0E0"/></svg>
  <div><h1>Microservicio de notificaciones</h1><div class="sub">Clínica Dental Cárdenas · WhatsApp y correo (proveedores de prueba)</div></div>
  <span class="sp"></span>
  <div class="stats" id="stats"></div>
  <span>Servicio:</span><button class="btn" data-m="normal">Normal</button><button class="btn" data-m="caido">Caído</button>
  <button class="btn orange" id="force">Enviar recordatorios ahora</button>
</header>
<main>
  <section class="card"><h2>WhatsApp <span class="sub" id="wacount"></span></h2>
    <div class="phone"><div class="top">💬 <select id="who"></select></div><div class="chat" id="chat"></div></div></section>
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
const TYPE={RECORDATORIO:'Recordatorio',CAMBIO_CITA:'Cambio de cita',CANCELACION:'Cancelación',CODIGO_ACCESO:'Código de acceso'};
async function load(){ const r=await fetch('/api/estado'); data=await r.json(); render(); }
function render(){
  document.querySelectorAll('[data-m]').forEach(b=>b.classList.toggle('on',b.dataset.m===data.modo));
  const ms=data.mensajes;
  const c=s=>ms.filter(m=>m.estado===s).length;
  document.getElementById('stats').innerHTML='<span><b>'+c('ENVIADO')+'</b> enviados</span><span><b>'+c('PROGRAMADO')+'</b> programados</span><span><b>'+data.duplicadosEvitados+'</b> duplicados evitados</span><span><b>'+c('FALLIDO')+'</b> fallidos</span>';
  const wa=ms.filter(m=>m.canal==='WHATSAPP'&&(m.estado==='ENVIADO'||m.estado==='FALLIDO'));
  const nums=[...new Set(wa.map(m=>m.destinoNormalizado||m.destino))];
  if(!who||!nums.includes(who)) who=nums[nums.length-1]||null;
  const sel=document.getElementById('who'); sel.innerHTML=nums.map(n=>'<option '+(n===who?'selected':'')+'>'+esc(n)+'</option>').join('')||'<option>Sin conversaciones</option>';
  document.getElementById('wacount').textContent=wa.length+' mensajes';
  const chat=document.getElementById('chat');
  chat.innerHTML=wa.filter(m=>(m.destinoNormalizado||m.destino)===who).map(m=>'<div class="bub '+(m.estado==='FALLIDO'?'fail':'')+'">'+esc(m.cuerpo)+'<div class="meta">'+(TYPE[m.tipo]||m.tipo)+' · '+fmt(m.enviadoEn||m.recibidoEn)+(m.estado==='ENVIADO'?' ✓✓':' ✗ '+esc(m.error))+'</div></div>').join('')||'<div class="empty">Aún no hay mensajes.</div>';
  chat.scrollTop=chat.scrollHeight;
  const em=ms.filter(m=>m.canal==='EMAIL'&&(m.estado==='ENVIADO'||m.estado==='FALLIDO')).reverse();
  document.getElementById('mailcount').textContent=em.length+' correos';
  document.getElementById('inbox').innerHTML=em.map(m=>'<div data-id="'+m.id+'" class="'+(m.id===mailSel?'on':'')+'"><b>'+esc(m.asunto||'(sin asunto)')+'</b><span>'+esc(m.destino)+' · '+fmt(m.enviadoEn||m.recibidoEn)+'</span></div>').join('')||'<div class="empty">Bandeja vacía.</div>';
  const cur=em.find(m=>m.id===mailSel);
  if(cur) document.getElementById('read').innerHTML='<h3>'+esc(cur.asunto)+'</h3><div class="sub">De: notificaciones@clinicacardenas.pe · Para: '+esc(cur.destino)+' · '+fmt(cur.enviadoEn)+'</div><hr style="border:0;border-top:1px solid var(--line)">'+esc(cur.cuerpo);
  document.getElementById('log').innerHTML=ms.slice().reverse().slice(0,120).map(m=>'<tr><td><span class="tag '+m.estado+'">'+m.estado+'</span>'+(m.error?'<div class="sub">'+esc(m.error)+'</div>':'')+'</td><td>'+(TYPE[m.tipo]||m.tipo)+'</td><td>'+m.canal+'</td><td>'+esc(m.destino)+'</td><td>'+fmt(m.programadoPara)+'</td><td>'+fmt(m.enviadoEn)+'</td><td style="font-family:monospace;font-size:11.5px">'+esc(m.clave)+'</td><td>'+(m.programadoPara?(m.webhookRespuesta??'—'):'—')+'</td></tr>').join('')||'<tr><td colspan="8" class="empty">Sin mensajes.</td></tr>';
}
document.getElementById('who').onchange=e=>{who=e.target.value;render();};
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
    if (p === '/' && req.method === 'GET') return send(res, 200, panel(), 'text/html; charset=utf-8');
    if (p === '/api/estado' && req.method === 'GET') return send(res, 200, { modo, mensajes: db.mensajes, duplicadosEvitados: db.duplicados || 0 });
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
      const m = deliver(create(body, false));
      db.mensajes.push(m); save(); log(m.estado);
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
