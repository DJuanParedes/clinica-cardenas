/*
 * Proveedores reales de envío.
 *   - WhatsApp: WhatsApp Cloud API oficial de Meta (graph.facebook.com).
 *   - Correo: SMTP con nodemailer (Gmail con contraseña de aplicación, Outlook, Brevo, etc.).
 *
 * Si un proveedor no está configurado, el canal sigue en modo de prueba: el mensaje solo se ve en el panel.
 *
 * Por seguridad, los datos de ejemplo tienen números y correos que pueden ser de personas reales. Por eso solo se envía
 * de verdad a los destinos de WA_NUMEROS_PERMITIDOS / CORREOS_PERMITIDOS. Para enviar a todos los pacientes
 * (producción) se pone ENVIAR_A_TODOS=true.
 */
const env = (k, d = '') => (process.env[k] ?? d).trim();
const list = (k) => env(k).split(/[,;\s]+/).map((s) => s.trim()).filter(Boolean);

const TIMEOUT_MS = Number(env('PROVEEDOR_TIMEOUT_MS', '8000'));
const ENVIAR_A_TODOS = env('ENVIAR_A_TODOS', 'false').toLowerCase() === 'true';

// ---------------------------------------------------------------- WhatsApp Cloud API
const WA = {
  token: env('WA_TOKEN'),
  phoneId: env('WA_PHONE_NUMBER_ID'),
  version: env('WA_API_VERSION', 'v21.0'),
  base: env('WA_API_BASE', 'https://graph.facebook.com'),
  plantilla: env('WA_PLANTILLA'),
  idioma: env('WA_PLANTILLA_IDIOMA', 'es'),
  modo: env('WA_MODO').toLowerCase(), // plantilla | texto (por defecto: plantilla si hay WA_PLANTILLA)
  permitidos: new Set(list('WA_NUMEROS_PERMITIDOS').map((n) => localPhone(n)).filter(Boolean)),
};
const waActivo = () => Boolean(WA.token && WA.phoneId);

/** Devuelve los 9 dígitos de un celular peruano (9XXXXXXXX) o null. */
function localPhone(v) {
  const d = String(v || '').replace(/\D/g, '');
  const local = d.startsWith('51') && d.length === 11 ? d.slice(2) : d;
  return /^9\d{8}$/.test(local) ? local : null;
}

/** Los parámetros de plantilla no admiten saltos de línea ni más de 4 espacios seguidos. */
const flatten = (s) => String(s).replace(/\s*\n+\s*/g, ' · ').replace(/\s{4,}/g, '   ').slice(0, 1000);

async function graph(body) {
  const res = await fetch(`${WA.base}/${WA.version}/${WA.phoneId}/messages`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${WA.token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(TIMEOUT_MS),
  });
  const json = await res.json().catch(() => ({}));
  if (!res.ok || json.error) {
    const e = json.error || {};
    const detail = e.error_data?.details || e.message || `HTTP ${res.status}`;
    throw new Error(`WhatsApp (Meta) rechazó el envío: ${detail}${e.code ? ` [código ${e.code}]` : ''}`);
  }
  return json.messages?.[0]?.id || null;
}

/**
 * Envía por WhatsApp. Meta solo deja enviar texto libre si el paciente escribió en las últimas 24 h; fuera de esa
 * ventana hay que usar una plantilla aprobada. Por eso, para recordatorios, el modo recomendado es "plantilla".
 * @param plantillaForzada nombre de plantilla para la prueba del panel (p. ej. hello_world)
 */
async function enviarWhatsApp(local, cuerpo, plantillaForzada) {
  const to = '51' + local;
  const nombre = plantillaForzada || WA.plantilla;
  const usarPlantilla = plantillaForzada || (WA.modo ? WA.modo === 'plantilla' : Boolean(WA.plantilla));
  if (usarPlantilla && !nombre) throw new Error('WA_MODO=plantilla pero falta WA_PLANTILLA');
  if (usarPlantilla) {
    const template = nombre === 'hello_world'
      ? { name: 'hello_world', language: { code: 'en_US' } }
      : { name: nombre, language: { code: WA.idioma }, components: [{ type: 'body', parameters: [{ type: 'text', text: flatten(cuerpo) }] }] };
    return { id: await graph({ messaging_product: 'whatsapp', to, type: 'template', template }), via: `plantilla ${nombre}` };
  }
  const id = await graph({ messaging_product: 'whatsapp', recipient_type: 'individual', to, type: 'text', text: { preview_url: false, body: String(cuerpo).slice(0, 4096) } });
  return { id, via: 'texto' };
}

// ---------------------------------------------------------------- Correo SMTP
const SMTP = {
  host: env('SMTP_HOST'),
  port: Number(env('SMTP_PORT', '587')),
  user: env('SMTP_USUARIO'),
  pass: env('SMTP_CLAVE').replace(/\s+/g, ''), // las contraseñas de aplicación de Google se copian con espacios
  from: env('SMTP_REMITENTE'),
  nombre: env('SMTP_NOMBRE', 'Clínica Dental Cárdenas'),
  permitidos: new Set(list('CORREOS_PERMITIDOS').map((c) => c.toLowerCase())),
};
const correoActivo = () => Boolean(SMTP.host && SMTP.user && SMTP.pass);

let transporter = null;
function smtp() {
  if (!transporter) {
    const nodemailer = require('nodemailer');
    transporter = nodemailer.createTransport({
      host: SMTP.host,
      port: SMTP.port,
      secure: SMTP.port === 465,
      auth: { user: SMTP.user, pass: SMTP.pass },
      connectionTimeout: TIMEOUT_MS,
      greetingTimeout: TIMEOUT_MS,
      socketTimeout: TIMEOUT_MS,
    });
  }
  return transporter;
}

const escapeHtml = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

function htmlCorreo(asunto, cuerpo) {
  return `<!doctype html><html><body style="margin:0;background:#f3f8fb;font-family:Segoe UI,Arial,sans-serif;color:#0e2a3b">
<table width="100%" cellpadding="0" cellspacing="0" style="padding:24px 12px"><tr><td align="center">
<table width="100%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#fff;border-radius:14px;border:1px solid #dbe7ee">
<tr><td style="padding:18px 24px;border-bottom:3px solid #00b0e0;font-weight:700;font-size:17px">Clínica Dental Cárdenas</td></tr>
<tr><td style="padding:22px 24px"><h2 style="margin:0 0 12px;font-size:18px">${escapeHtml(asunto)}</h2>
<div style="white-space:pre-wrap;font-size:15px;line-height:1.55">${escapeHtml(cuerpo)}</div></td></tr>
<tr><td style="padding:14px 24px;background:#f6fafc;color:#688494;font-size:12px;border-radius:0 0 14px 14px">
Este es un mensaje automático de Clínica Dental Cárdenas. Si no reconoces esta cita, comunícate con recepción.</td></tr>
</table></td></tr></table></body></html>`;
}

async function enviarCorreo(destino, asunto, cuerpo) {
  const info = await smtp().sendMail({
    from: { name: SMTP.nombre, address: SMTP.from || SMTP.user },
    to: destino,
    subject: asunto || 'Clínica Dental Cárdenas',
    text: cuerpo,
    html: htmlCorreo(asunto || 'Clínica Dental Cárdenas', cuerpo),
  });
  return { id: info.messageId, via: 'smtp' };
}

// ---------------------------------------------------------------- decisión
/** ¿Este mensaje se envía de verdad o se queda en la bandeja de prueba? */
function destinoReal(canal, destinoNormalizado) {
  if (canal === 'WHATSAPP') {
    if (!waActivo()) return { real: false, motivo: 'WhatsApp en modo de prueba (sin credenciales de Meta)' };
    if (ENVIAR_A_TODOS || WA.permitidos.has(destinoNormalizado)) return { real: true };
    return { real: false, motivo: 'Número fuera de WA_NUMEROS_PERMITIDOS: se dejó solo en la bandeja de prueba' };
  }
  if (!correoActivo()) return { real: false, motivo: 'Correo en modo de prueba (sin SMTP configurado)' };
  if (ENVIAR_A_TODOS || SMTP.permitidos.has(destinoNormalizado)) return { real: true };
  return { real: false, motivo: 'Correo fuera de CORREOS_PERMITIDOS: se dejó solo en la bandeja de prueba' };
}

function estadoProveedores() {
  return {
    enviarATodos: ENVIAR_A_TODOS,
    whatsapp: waActivo()
      ? { modo: 'real', proveedor: 'WhatsApp Cloud API (Meta)', envio: WA.plantilla && WA.modo !== 'texto' ? `plantilla ${WA.plantilla}` : 'texto libre (ventana de 24 h)', permitidos: [...WA.permitidos].map((n) => '+51 ' + n) }
      : { modo: 'prueba', proveedor: 'Simulado' },
    correo: correoActivo()
      ? { modo: 'real', proveedor: `SMTP ${SMTP.host}:${SMTP.port}`, remitente: SMTP.from || SMTP.user, permitidos: [...SMTP.permitidos] }
      : { modo: 'prueba', proveedor: 'Simulado' },
  };
}

module.exports = { localPhone, enviarWhatsApp, enviarCorreo, destinoReal, estadoProveedores, waActivo, correoActivo };
