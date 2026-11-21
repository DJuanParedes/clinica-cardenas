/**
 * Crea en Meta (WhatsApp Manager) las plantillas con formato que usa el sistema. Se ejecuta una vez en el servidor:
 *   sudo docker exec clinica-cardenas-completo-notificaciones-1 node crear-plantillas.js
 * Usa WA_TOKEN y WA_WABA_ID del entorno (no los muestra). Meta las revisa en minutos; mientras tanto el sistema
 * sigue enviando con la plantilla general (WA_PLANTILLA).
 */
const TOKEN = process.env.WA_TOKEN;
const WABA = process.env.WA_WABA_ID;
const VERSION = process.env.WA_API_VERSION || 'v21.0';
const IDIOMA = process.env.WA_PLANTILLA_IDIOMA || 'es';

const PLANTILLAS = [
  {
    name: 'recordatorio_cita',
    text: 'Hola {{1}} 👋\n\nTe recordamos tu cita en *Clínica Dental Cárdenas*:\n\n📅 *{{2}}*\n🦷 {{3}}\n📍 {{4}}\n\n'
      + 'Responde *1* para confirmar tu asistencia o *2* si necesitas reprogramar.',
    ejemplo: ['María', 'Miércoles 11 de noviembre a las 10:30 a. m.', 'Dra. Rosa Cárdenas (Ortodoncia)', 'Urb. La Floresta, Chaclacayo'],
  },
  {
    name: 'cita_registrada',
    text: 'Hola {{1}} 👋\n\nTu cita en *Clínica Dental Cárdenas* quedó registrada ✅\n\n📅 *{{2}}*\n🦷 {{3}}\n📍 {{4}}\n\n'
      + 'Te enviaremos un recordatorio antes de tu cita.',
    ejemplo: ['María', 'Miércoles 11 de noviembre a las 10:30 a. m.', 'Dra. Rosa Cárdenas (Ortodoncia)', 'Urb. La Floresta, Chaclacayo'],
  },
  {
    name: 'cita_reprogramada',
    text: 'Hola {{1}}, tu cita en *Clínica Dental Cárdenas* fue reprogramada 🔁\n\nAntes: {{2}}\n📅 Nuevo horario: *{{3}}*\n🦷 {{4}}\n📍 {{5}}\n\n'
      + 'Si el nuevo horario no te acomoda, respóndenos por aquí.',
    ejemplo: ['María', 'Martes 10 de noviembre a las 9:00 a. m.', 'Jueves 12 de noviembre a las 4:00 p. m.', 'Dra. Rosa Cárdenas (Ortodoncia)', 'Urb. La Floresta, Chaclacayo'],
  },
  {
    name: 'cita_cancelada',
    text: 'Hola {{1}}, tu cita del *{{2}}* con {{3}} en *Clínica Dental Cárdenas* fue cancelada.\n\n'
      + 'Para programar una nueva cita, reserva desde la app o llámanos al {{4}}. ¡Te esperamos!',
    ejemplo: ['María', 'Miércoles 11 de noviembre a las 10:30 a. m.', 'Dra. Rosa Cárdenas', '987 654 000'],
  },
];

(async () => {
  if (!TOKEN || !WABA) { console.error('Faltan WA_TOKEN o WA_WABA_ID en el .env'); process.exit(1); }
  const existentes = await fetch(`https://graph.facebook.com/${VERSION}/${WABA}/message_templates?fields=name,status&limit=100`, {
    headers: { Authorization: `Bearer ${TOKEN}` },
  }).then((r) => r.json());
  const estado = Object.fromEntries((existentes.data || []).map((t) => [t.name, t.status]));
  for (const p of PLANTILLAS) {
    if (estado[p.name]) { console.log(`${p.name}: ya existe (${estado[p.name]})`); continue; }
    const res = await fetch(`https://graph.facebook.com/${VERSION}/${WABA}/message_templates`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: p.name,
        language: IDIOMA,
        category: 'UTILITY',
        components: [{ type: 'BODY', text: p.text, example: { body_text: [p.ejemplo] } }],
      }),
    });
    const j = await res.json().catch(() => ({}));
    console.log(`${p.name}: ${res.ok ? 'enviada a revisión (' + (j.status || 'PENDING') + ')' : 'error — ' + (j.error?.error_user_msg || j.error?.message || res.status)}`);
  }
})();
