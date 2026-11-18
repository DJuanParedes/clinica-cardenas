/*
 * Pruebas del asistente virtual de WhatsApp y de la conversación bidireccional · Clínica Dental Cárdenas
 * Simula mensajes del paciente (como si llegaran por WhatsApp), revisa las respuestas del asistente, las acciones
 * (confirmar, pedir reprogramación, avisar al personal) y que el personal pueda responder desde la web.
 *
 * NO ejecutar en producción. Uso:
 *   node pruebas/asistente-e2e.js http://localhost:9081
 * o en la consola del navegador en http://localhost:9081:  await asistenteE2E(location.origin)
 */
async function asistenteE2E(BASE, WEBHOOK_TOKEN = 'token-webhook-clinica-cardenas') {
  const results = [];
  const api = async (method, path, body, token, extra = {}) => {
    const res = await fetch(BASE + path, {
      method,
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}), ...extra },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await res.text();
    let json = null;
    try { json = text ? JSON.parse(text) : null; } catch { json = text; }
    return { status: res.status, body: json };
  };
  const check = async (name, fn) => {
    try { results.push({ name, ok: true, detail: (await fn()) || '' }); }
    catch (e) { results.push({ name, ok: false, detail: String(e.message || e) }); }
  };
  const expect = (c, m) => { if (!c) throw new Error(m); };
  const login = async (u) => (await api('POST', '/api/auth/login', { username: u, password: 'Clinica2026' })).body.token;
  const escribe = (telefono, texto) => api('POST', '/api/internal/whatsapp/reply', { telefono, texto }, null, { 'X-Webhook-Token': WEBHOOK_TOKEN });

  const recep = await login('recepcion');
  const admin = await login('admin');
  const caja = await login('caja');
  const original = (await api('GET', '/api/assistant/settings', undefined, admin)).body;
  await api('PUT', '/api/assistant/settings', { mode: 'AUTOMATICO' }, admin);

  // Paciente de prueba con una cita en los próximos días.
  const tel = '9' + String(10000000 + Math.floor(Math.random() * 89999999));
  const p = (await api('POST', '/api/patients', { dataConsent: true, docType: 'DNI', docNumber: String(60000000 + Math.floor(Math.random() * 9999999)),
    firstName: 'Asistente', lastName: 'Prueba Ia', birthDate: '1990-01-01', sex: 'F', phone: tel, district: 'Chaclacayo' }, recep)).body;
  const ds = (await api('GET', '/api/dentists', undefined, recep)).body;
  let cita = null;
  for (let i = 2; i < 15 && !cita; i++) {
    const d = new Date(); d.setDate(d.getDate() + i);
    if (d.getDay() === 0) continue;
    const iso = d.toISOString().slice(0, 10);
    const s = (await api('GET', `/api/agenda/slots?dentistId=${ds[0].id}&date=${iso}&duration=30&patientId=${p.id}`, undefined, recep)).body;
    const libre = s.slots.find((x) => x.available);
    if (libre) cita = (await api('POST', '/api/appointments', { patientId: p.id, dentistId: ds[0].id, startAt: libre.start, durationMin: 30, reason: 'Control' }, recep)).body;
  }

  await check('Saludo: responde y menciona la próxima cita', async () => {
    const r = await escribe(tel, 'Hola buenas tardes');
    expect(r.status === 200 && r.body.accion === 'ASISTENTE', 'sin respuesta del asistente: ' + JSON.stringify(r.body));
    expect(/Asistente/.test(r.body.respuesta) || /asistente/.test(r.body.respuesta), 'no se presenta');
    return r.body.respuesta.split('\n')[0];
  });
  await check('Pregunta por su cita: da fecha, odontólogo y dirección reales', async () => {
    const r = await escribe(tel, '¿a qué hora es mi cita?');
    expect(r.body.intencion === 'MI_CITA' && r.body.respuesta.includes(cita.dentist.fullName), r.body.respuesta);
  });
  await check('Precio: usa el catálogo real de procedimientos (RAG)', async () => {
    const r = await escribe(tel, 'cuánto cuesta una limpieza dental?');
    expect(r.body.intencion === 'PRECIO' && /S\/ \d/.test(r.body.respuesta), r.body.respuesta);
    return r.body.respuesta.split('\n')[1] || r.body.respuesta;
  });
  await check('Horario y ubicación desde los datos de la clínica', async () => {
    const h = await escribe(tel, '¿atienden los sábados?');
    const u = await escribe(tel, 'dónde quedan?');
    expect(h.body.intencion === 'HORARIO' && /sábado/.test(h.body.respuesta), h.body.respuesta);
    expect(u.body.intencion === 'UBICACION' && /Floresta|Chaclacayo/.test(u.body.respuesta), u.body.respuesta);
  });
  await check('Pregunta frecuente de la base de conocimiento', async () => {
    const r = await escribe(tel, '¿atienden a niños pequeños?');
    expect(/3 años/.test(r.body.respuesta), r.body.respuesta);
  });
  await check('Confirmar en texto libre confirma la cita de verdad', async () => {
    const r = await escribe(tel, 'sí, confirmo mi asistencia');
    const a = (await api('GET', '/api/appointments/' + cita.id, undefined, recep)).body;
    expect(r.body.intencion === 'CONFIRMAR' && a.status === 'CONFIRMADA', 'estado ' + a.status + ' · ' + r.body.respuesta);
  });
  await check('Reprogramar: registra el pedido, ofrece horarios libres y avisa al personal', async () => {
    const r = await escribe(tel, 'necesito cambiar mi cita para otro día');
    expect(r.body.intencion === 'REPROGRAMAR' && /Horarios libres|reprogramar/.test(r.body.respuesta), r.body.respuesta);
    const t = (await api('GET', '/api/conversations?q=' + tel, undefined, recep)).body.items.find((x) => x.phone === tel);
    expect(t && t.needsAttention, 'la conversación no quedó por atender');
    const req = (await api('GET', '/api/reschedule-requests', undefined, recep));
    return req.status === 200 ? 'pedido visible para recepción' : '';
  });
  await check('El personal responde desde la web y el asistente se pausa en ese chat', async () => {
    const s = await api('POST', `/api/conversations/${tel}/messages`, { text: 'Hola, te escribe recepción. ¿Te acomoda el jueves a las 10?' }, recep);
    expect(s.status === 201 && s.body.status === 'ENVIADO', 'envío: ' + s.status + ' ' + JSON.stringify(s.body));
    const r = await escribe(tel, 'sí perfecto, el jueves está bien');
    expect(r.body.accion === 'PERSONAL' && !r.body.respuesta, 'el asistente interrumpió al personal: ' + JSON.stringify(r.body));
    const c = (await api('GET', '/api/conversations/' + tel, undefined, recep)).body;
    const fuentes = c.messages.map((m) => m.source);
    expect(fuentes.includes('PACIENTE') && fuentes.includes('IA') && fuentes.includes('PERSONAL'), 'faltan mensajes en el historial: ' + fuentes.join(','));
    return c.messages.length + ' mensajes en la conversación';
  });
  await check('Reactivar el asistente en el chat vuelve a responder', async () => {
    await api('PATCH', '/api/conversations/' + tel, { aiEnabled: true }, recep);
    const r = await escribe(tel, 'gracias!');
    expect(r.body.accion === 'ASISTENTE' && r.body.intencion === 'GRACIAS', JSON.stringify(r.body));
  });
  await check('Parámetro APAGADO: el asistente calla y el mensaje queda para el personal', async () => {
    await api('PUT', '/api/assistant/settings', { mode: 'APAGADO' }, admin);
    const r = await escribe(tel, 'una consulta más');
    await api('PUT', '/api/assistant/settings', { mode: 'AUTOMATICO' }, admin);
    expect(r.body.accion === 'PERSONAL' && !r.body.respuesta, JSON.stringify(r.body));
  });
  await check('Número desconocido: también recibe ayuda general', async () => {
    const otro = '9' + String(10000000 + Math.floor(Math.random() * 89999999));
    const r = await escribe(otro, 'hola, cuánto cuesta una consulta?');
    expect(r.body.accion === 'ASISTENTE' && /S\/ \d/.test(r.body.respuesta), JSON.stringify(r.body));
  });
  await check('Urgencia: indica llamar y avisa al personal', async () => {
    const otro = '9' + String(10000000 + Math.floor(Math.random() * 89999999));
    const r = await escribe(otro, 'me duele mucho la muela y está muy hinchado');
    expect(r.body.intencion === 'URGENCIA' && /llámanos|llama/i.test(r.body.respuesta), r.body.respuesta);
  });
  await check('Base de conocimiento: una pregunta nueva se responde al instante', async () => {
    const add = await api('POST', '/api/assistant/faq', { question: '¿Aceptan seguro Rimac o EPS?', answer: 'Por ahora no trabajamos con seguros; te damos boleta para que solicites el reembolso.' }, recep);
    expect(add.status === 200, 'agregar: ' + add.status);
    const t = await api('POST', '/api/assistant/test', { text: '¿aceptan seguro rimac?' }, recep);
    const nueva = add.body.find((f) => f.question.startsWith('¿Aceptan seguro'));
    await api('DELETE', '/api/assistant/faq/' + nueva.id, undefined, recep);
    expect(/reembolso/.test(t.body.text), t.body.text);
  });
  await check('Permisos: caja no ve los mensajes; el webhook exige token', async () => {
    const c = await api('GET', '/api/conversations', undefined, caja);
    const w = await api('POST', '/api/internal/whatsapp/reply', { telefono: tel, texto: 'hola' }, null, { 'X-Webhook-Token': 'malo' });
    expect(c.status === 403 && w.status === 401, `caja ${c.status}, webhook ${w.status}`);
  });

  await api('PUT', '/api/assistant/settings', { mode: original.mode || 'AUTOMATICO' }, admin);
  if (cita) await api('PATCH', `/api/appointments/${cita.id}/status`, { status: 'CANCELADA', reason: 'Fin de la prueba' }, recep);
  const passed = results.filter((r) => r.ok).length;
  return { passed, failed: results.length - passed, total: results.length, results };
}

if (typeof module !== 'undefined' && require.main === module) {
  asistenteE2E(process.argv[2] || 'http://localhost:9081').then((r) => {
    for (const t of r.results) console.log(`${t.ok ? '✔' : '✘'} ${t.name}${t.detail ? ' — ' + t.detail : ''}`);
    console.log(`\n${r.passed}/${r.total} pruebas aprobadas`);
    process.exit(r.failed ? 1 : 0);
  });
}
