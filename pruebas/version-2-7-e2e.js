/**
 * Versión 2.7.0: tareas, lista de pacientes con seguimiento, segmentos, cumpleaños, campañas y embudo de ventas.
 * Uso (solo local, nunca contra producción: crea pacientes y envía mensajes): node version-2-7-e2e.js http://localhost:8080
 */
async function v27(BASE, WEBHOOK_TOKEN = 'token-webhook-clinica-cardenas') {
  const results = [];
  const rnd = (n) => Math.floor(Math.random() * n);
  const j = async (m, u, b, t, extra = {}) => {
    const r = await fetch(BASE + u, { method: m, headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: 'Bearer ' + t } : {}), ...extra }, body: b ? JSON.stringify(b) : undefined });
    const x = await r.text(); try { return { s: r.status, b: JSON.parse(x) }; } catch { return { s: r.status, b: x }; }
  };
  const check = async (name, fn) => { try { results.push({ name, ok: true, detail: (await fn()) || '' }); } catch (e) { results.push({ name, ok: false, detail: String(e.message || e) }); } };
  const expect = (c, m) => { if (!c) throw new Error(m); };
  const login = async (u) => (await j('POST', '/api/auth/login', { username: u, password: 'Clinica2026' })).b.token;
  const ad = await login('admin'), rc = await login('recepcion'), cj = await login('caja'), dr = await login('dra.cardenas');
  const day = (n) => { const d = new Date(); d.setDate(d.getDate() + n); return d.toLocaleDateString('en-CA'); };
  const dni = () => String(72000000 + rnd(8999999));
  const cel = () => '9' + String(10000000 + rnd(89999999));
  const word = Array.from({ length: 6 }, () => 'abcdefghijklmnopqrstuvwxyz'[rnd(26)]).join('');
  const tag = 'E2E27-' + word;
  const newPatient = async (extra = {}) => {
    const r = await j('POST', '/api/patients', { dataConsent: true, docType: 'DNI', docNumber: dni(), firstName: 'Sofía', lastName: 'Embudo ' + word, birthDate: '1990-06-10', sex: 'F', phone: cel(), email: `e2e27.${rnd(1e6)}@correo.pe`, ...extra }, rc);
    if (r.s !== 200 && r.s !== 201) throw new Error('paciente ' + r.s + ' ' + JSON.stringify(r.b).slice(0, 200));
    return r.b;
  };
  const opts = (await j('GET', '/api/tasks/options', null, rc)).b;
  const me = opts.me;

  await check('Permisos: todos tienen tareas; caja no ve marketing ni embudo', async () => {
    expect((await j('GET', '/api/tasks', null, cj)).s === 200, 'caja sin tareas');
    expect((await j('GET', '/api/tasks', null, dr)).s === 200, 'odontóloga sin tareas');
    expect((await j('GET', '/api/leads', null, cj)).s === 403, 'caja ve el embudo');
    expect((await j('GET', '/api/marketing/segments', null, rc)).s === 403, 'recepción ve los segmentos');
    expect((await j('GET', '/api/leads', null, rc)).s === 200, 'recepción sin embudo');
  });

  const p1 = await newPatient();
  let t1;
  await check('Tareas: validan tipo, fecha y responsable; se crean, cierran y reabren', async () => {
    let r = await j('POST', '/api/tasks', { patientId: p1.id, kind: 'BAILAR', title: 'x', dueDate: day(-1), assigneeId: 999999 }, rc);
    expect(r.s === 400 && r.b.fields.kind && r.b.fields.title && r.b.fields.dueDate && r.b.fields.assigneeId, 'validación ' + JSON.stringify(r.b.fields));
    r = await j('POST', '/api/tasks', { patientId: p1.id, kind: 'LLAMAR', title: 'Llamar para agendar control', dueDate: day(0), dueTime: '10:30', assigneeId: me, priority: 'ALTA' }, rc);
    expect(r.s === 200 && r.b.assignee.id === me && r.b.dueTime === '10:30', 'crear ' + r.s);
    t1 = r.b;
    const mine = (await j('GET', '/api/tasks?scope=mine', null, rc)).b;
    expect(mine.items.some((x) => x.id === t1.id) && mine.counts.mineToday >= 1, 'no aparece en mis tareas de hoy');
    expect((await j('POST', `/api/tasks/${t1.id}/cancel`, { result: '' }, rc)).s === 400, 'canceló sin motivo');
    r = await j('POST', `/api/tasks/${t1.id}/complete`, { result: 'Contestó, agenda el lunes' }, rc);
    expect(r.s === 200 && r.b.status === 'HECHA' && r.b.completedBy === 'recepcion', 'completar');
    expect((await j('POST', `/api/tasks/${t1.id}/complete`, {}, rc)).s === 409, 'la cerró dos veces');
    expect((await j('POST', `/api/tasks/${t1.id}/reopen`, {}, rc)).b.status === 'PENDIENTE', 'no reabrió');
  });

  await check('Lista de pacientes: tarea pendiente, próxima cita y filtros de seguimiento', async () => {
    let s = (await j('GET', `/api/patients?q=${encodeURIComponent(p1.docNumber)}&task=CON`, null, rc)).b;
    expect(s.total === 1 && s.insights[p1.id]?.task?.id === t1.id, 'la fila no trae la tarea: ' + JSON.stringify(s.insights));
    s = (await j('GET', `/api/patients?q=${encodeURIComponent(p1.docNumber)}&task=SIN`, null, rc)).b;
    expect(s.total === 0, 'filtro sin tarea');
    s = (await j('GET', `/api/patients?q=${encodeURIComponent(p1.docNumber)}&next=SIN`, null, rc)).b;
    expect(s.total === 1 && !s.insights[p1.id].nextAppointment, 'sin próxima cita');
    expect((await j('GET', '/api/patients?visit=AYER', null, rc)).s === 400, 'aceptó un filtro inválido');
  });

  // dos pacientes con la etiqueta de la prueba: uno acepta publicidad, el otro no se le preguntó
  const pSi = await newPatient(), pNo = await newPatient();
  for (const p of [pSi, pNo]) await j('PUT', `/api/patients/${p.id}/profile`, { tags: [tag] }, rc);
  await check('Consentimiento de publicidad: se registra aparte y queda en la ficha', async () => {
    expect((await j('PUT', `/api/patients/${pSi.id}/marketing-consent`, { accepted: true, channel: 'PRESENCIAL' }, cj)).s === 403, 'caja cambió el consentimiento');
    const r = await j('PUT', `/api/patients/${pSi.id}/marketing-consent`, { accepted: true, channel: 'PRESENCIAL' }, rc);
    expect(r.s === 200 && r.b.marketingConsent === true && r.b.marketingConsentAt, 'consentimiento ' + r.s);
  });

  let seg;
  await check('Segmentos: vista previa, guardar, nombre único y tareas en lote sin repetir', async () => {
    expect((await j('POST', '/api/marketing/segments/preview', { ageMin: 40, ageMax: 20 }, ad)).s === 400, 'aceptó edades invertidas');
    const pv = (await j('POST', '/api/marketing/segments/preview', { tags: [tag] }, ad)).b;
    expect(pv.count === 2 && pv.reachable === 1, `preview ${pv.count}/${pv.reachable}`);
    let r = await j('POST', '/api/marketing/segments', { name: 'Prueba ' + tag, filters: { tags: [tag] } }, ad);
    expect(r.s === 200 && r.b.count === 2, 'guardar ' + r.s);
    seg = r.b;
    expect((await j('POST', '/api/marketing/segments', { name: 'prueba ' + tag, filters: {} }, ad)).s === 400, 'duplicó el nombre');
    r = await j('POST', '/api/marketing/segments/tasks', { segmentId: seg.id, kind: 'LLAMAR', title: 'Ofrecer control ' + tag, dueDate: day(1) }, ad);
    expect(r.s === 200 && r.b.created === 2 && r.b.skipped === 0, 'lote ' + JSON.stringify(r.b));
    r = await j('POST', '/api/marketing/segments/tasks', { segmentId: seg.id, kind: 'LLAMAR', title: 'Ofrecer control ' + tag, dueDate: day(1) }, ad);
    expect(r.b.created === 0 && r.b.skipped === 2, 'repitió tareas');
    const t = (await j('GET', `/api/tasks?patientId=${pNo.id}`, null, rc)).b.items;
    expect(t.some((x) => x.origin === 'SEGMENTO' && x.originRef === seg.name), 'la tarea no dice de qué segmento viene');
  });

  let camp;
  await check('Campañas: solo variables del paciente y solo a quien aceptó; mensaje con baja', async () => {
    let r = await j('POST', '/api/marketing/campaigns', { name: 'Campaña ' + tag, segmentId: seg.id, subject: 'Hola', body: 'Tu cita es el {fecha}, {paciente}' }, ad);
    expect(r.s === 400 && /fecha/.test(r.b.fields?.body || ''), 'aceptó {fecha}');
    r = await j('POST', '/api/marketing/campaigns', { name: 'Campaña ' + tag, segmentId: seg.id, channel: 'WHATSAPP', subject: 'Novedades de {clinica}', body: 'Hola {paciente}, este mes la limpieza tiene 20 % de descuento. Reserva: {enlace_citas}' }, ad);
    expect(r.s === 200 && r.b.status === 'BORRADOR' && r.b.estimated === 1, 'crear ' + r.s + ' ' + JSON.stringify(r.b).slice(0, 200));
    camp = r.b;
    expect((await j('POST', `/api/marketing/campaigns/${camp.id}/schedule`, { scheduledAt: day(3) + 'T06:00:00' }, ad)).s === 400, 'programó a las 6 a. m.');
    const h = new Date().getHours();
    if (h < 8 || h >= 20) {
      r = await j('POST', `/api/marketing/campaigns/${camp.id}/schedule`, { scheduledAt: day(1) + 'T10:00:00' }, ad);
      expect(r.b.status === 'PROGRAMADA', 'programar');
      await j('POST', `/api/marketing/campaigns/${camp.id}/cancel`, {}, ad);
      return 'fuera del horario de envío: solo se programó';
    }
    r = await j('POST', `/api/marketing/campaigns/${camp.id}/schedule`, { scheduledAt: null }, ad);
    expect(r.s === 200 && r.b.status === 'ENVIADA' && r.b.recipients === 1, 'enviar ' + r.s + ' ' + JSON.stringify(r.b).slice(0, 200));
    const nSi = (await j('GET', `/api/patients/${pSi.id}/notifications`, null, rc)).b.filter((x) => x.type === 'CAMPANA');
    const nNo = (await j('GET', `/api/patients/${pNo.id}/notifications`, null, rc)).b.filter((x) => x.type === 'CAMPANA');
    expect(nSi.length === 1 && /BAJA/.test(nSi[0].body) && nSi[0].body.includes('Sofía'), 'mensaje ' + JSON.stringify(nSi).slice(0, 200));
    expect(nNo.length === 0, 'le llegó a quien no aceptó');
    expect((await j('POST', `/api/marketing/campaigns/${camp.id}/cancel`, {}, ad)).s === 409, 'canceló una enviada');
  });

  await check('Responder BAJA por WhatsApp retira el consentimiento', async () => {
    const r = await j('POST', '/api/internal/whatsapp/reply', { telefono: '51' + pSi.phone, texto: 'BAJA' }, null, { 'X-Webhook-Token': WEBHOOK_TOKEN });
    expect(r.s === 200 && r.b.accion === 'BAJA_PUBLICIDAD', 'respuesta ' + JSON.stringify(r.b).slice(0, 160));
    const p = (await j('GET', `/api/patients/${pSi.id}`, null, rc)).b.patient;
    expect(p.marketingConsent === false, 'sigue aceptando');
    const pv = (await j('POST', '/api/marketing/segments/preview', { tags: [tag] }, ad)).b;
    expect(pv.reachable === 0, 'sigue en la audiencia');
    if (camp?.id) {
      const c = (await j('GET', `/api/marketing/campaigns/${camp.id}`, null, ad)).b;
      if (c.status === 'ENVIADA') expect(c.stats.optOuts === 1, 'la campaña no cuenta la baja');
    }
  });

  await check('Cumpleaños: saludo a quien no pidió la baja, sin repetir, y tarea opcional', async () => {
    const t = new Date();
    const bd = `1985-${String(t.getMonth() + 1).padStart(2, '0')}-${String(t.getDate()).padStart(2, '0')}`;
    const yes = await newPatient({ birthDate: bd, firstName: 'Cumple' }), no = await newPatient({ birthDate: bd, firstName: 'Nocumple' });
    await j('PUT', `/api/patients/${no.id}/marketing-consent`, { accepted: false, channel: 'TELEFONO' }, rc);
    const prev = (await j('GET', '/api/marketing/birthdays', null, ad)).b.settings;
    expect((await j('PUT', '/api/marketing/birthdays/settings', { time: '05:00' }, ad)).s === 400, 'aceptó las 5 a. m.');
    await j('PUT', '/api/marketing/birthdays/settings', { active: true, createTask: true }, ad);
    const r1 = (await j('POST', '/api/marketing/birthdays/run', {}, ad)).b;
    const r2 = (await j('POST', '/api/marketing/birthdays/run', {}, ad)).b;
    await j('PUT', '/api/marketing/birthdays/settings', { active: prev.active, createTask: prev.createTask, time: prev.time }, ad);
    const ny = (await j('GET', `/api/patients/${yes.id}/notifications`, null, rc)).b.filter((x) => x.type === 'CUMPLEANOS');
    const nn = (await j('GET', `/api/patients/${no.id}/notifications`, null, rc)).b.filter((x) => x.type === 'CUMPLEANOS');
    expect(ny.length >= 1 && ny[0].body.includes('Cumple'), 'sin saludo');
    expect(nn.length === 0, 'saludó a quien pidió la baja');
    expect(r2.sent === 0 && r2.tasks === 0, 'repitió: ' + JSON.stringify(r2));
    const tk = (await j('GET', `/api/tasks?patientId=${no.id}`, null, rc)).b.items;
    expect(tk.some((x) => x.origin === 'CUMPLEANOS'), 'sin tarea de cumpleaños');
    const list = (await j('GET', '/api/marketing/birthdays', null, ad)).b.patients.find((x) => x.id === yes.id);
    expect(list && list.greeted && list.tasked, 'la lista no marca el saludo');
    return `${r1.sent} saludos, ${r1.tasks} tareas`;
  });

  await check('Embudo: valida, evita duplicados y avanza solo al agendar la cita', async () => {
    let r = await j('POST', '/api/leads', { fullName: 'Lead sin contacto', source: 'INSTAGRAM' }, rc);
    expect(r.s === 400 && r.b.fields.phone, 'aceptó un lead sin celular ni correo');
    const phone = cel();
    r = await j('POST', '/api/leads', { fullName: 'Valentina Lead ' + tag, phone, source: 'INSTAGRAM', interest: 'Brackets', estimatedValue: 4200, assigneeId: me, followUpDate: day(1) }, rc);
    expect(r.s === 200 && r.b.stage === 'NUEVO' && r.b.tasks.length === 1, 'crear ' + r.s + ' ' + JSON.stringify(r.b).slice(0, 200));
    const lead = r.b;
    expect((await j('POST', '/api/leads', { fullName: 'Otra persona', phone, source: 'FACEBOOK' }, rc)).s === 409, 'duplicó el celular');
    expect((await j('POST', `/api/leads/${lead.id}/stage`, { stage: 'GANADO' }, rc)).s === 400, 'ganó sin paciente');
    expect((await j('POST', `/api/leads/${lead.id}/stage`, { stage: 'PERDIDO' }, rc)).s === 400, 'perdió sin motivo');
    r = await j('POST', `/api/leads/${lead.id}/stage`, { stage: 'CONTACTADO' }, rc);
    expect(r.b.stage === 'CONTACTADO', 'mover');
    const pt = await newPatient({ phone, firstName: 'Valentina' });
    r = await j('GET', `/api/leads/${lead.id}`, null, rc);
    expect((r.b.phoneMatches || []).some((x) => x.id === pt.id), 'no sugiere al paciente con ese celular');
    r = await j('POST', `/api/leads/${lead.id}/patient`, { patientId: pt.id }, rc);
    expect(r.b.patient?.id === pt.id, 'vincular');
    // agendar su cita mueve el lead a "Cita agendada"
    const dentists = (await j('GET', '/api/dentists', null, rc)).b;
    let slot = null;
    for (let d = 2; d < 15 && !slot; d++) for (const dn of dentists) {
      const s = (await j('GET', `/api/agenda/slots?dentistId=${dn.id}&date=${day(d)}&duration=30&patientId=${pt.id}`, null, rc)).b;
      const free = (s.slots || []).find((x) => x.available);
      if (free) { slot = { dId: dn.id, start: free.start }; break; }
    }
    expect(slot, 'sin horarios');
    const a = await j('POST', '/api/appointments', { patientId: pt.id, dentistId: slot.dId, startAt: slot.start, durationMin: 30, reason: 'Evaluación de ortodoncia' }, rc);
    expect(a.s === 200 || a.s === 201, 'cita ' + a.s);
    r = await j('GET', `/api/leads/${lead.id}`, null, rc);
    expect(r.b.stage === 'CITA_AGENDADA' && r.b.nextAppointment, 'no avanzó: ' + r.b.stage);
    // si cancela su única cita, vuelve a "Contactado"
    await j('PATCH', `/api/appointments/${a.b.id}/status`, { status: 'CANCELADA', reason: 'Prueba e2e' }, rc);
    r = await j('GET', `/api/leads/${lead.id}`, null, rc);
    expect(r.b.stage === 'CONTACTADO', 'no retrocedió al cancelar: ' + r.b.stage);
    r = await j('POST', `/api/leads/${lead.id}/stage`, { stage: 'GANADO' }, rc);
    expect(r.b.stage === 'GANADO' && r.b.closedAt, 'ganar');
    const m = (await j('GET', '/api/leads/metrics?days=30', null, rc)).b;
    expect(m.won >= 1 && m.funnel[0].reached >= m.funnel[4].reached, 'métricas');
  });

  await check('WhatsApp: un número desconocido entra al embudo una sola vez', async () => {
    const phone = cel();
    for (const texto of ['Hola, ¿cuánto cuesta un blanqueamiento?', 'sigo esperando']) await j('POST', '/api/internal/whatsapp/reply', { telefono: '51' + phone, texto }, null, { 'X-Webhook-Token': WEBHOOK_TOKEN });
    const b = (await j('GET', `/api/leads?q=${phone}`, null, rc)).b;
    const found = b.stages.flatMap((s) => s.items).filter((x) => x.phone === phone);
    expect(found.length === 1 && found[0].source === 'WHATSAPP' && /blanqueamiento/.test(found[0].interest), 'leads: ' + found.length);
  });

  await check('Prerregistro con casilla de promociones registra el consentimiento', async () => {
    const r = await j('POST', '/api/public/preregistro', { person: { docType: 'DNI', docNumber: dni(), firstName: 'Mateo', lastName: 'Promo ' + word, birthDate: '1992-02-02', sex: 'M', phone: cel(), dataConsent: true, marketingConsent: true }, answers: {} },
      null, { 'X-Forwarded-For': `10.${rnd(250)}.${rnd(250)}.${rnd(250)}` });
    expect(r.s === 200, 'prerregistro ' + r.s);
    const p = (await j('GET', `/api/patients/${r.b.id}`, null, rc)).b.patient;
    expect(p.marketingConsent === true, 'sin consentimiento de publicidad');
  });

  if (seg?.id) await j('DELETE', `/api/marketing/segments/${seg.id}`, null, ad);
  const passed = results.filter((r) => r.ok).length;
  return { passed, failed: results.length - passed, total: results.length, results };
}
if (typeof module !== 'undefined' && require.main === module) {
  v27(process.argv[2] || 'http://localhost:8080').then((r) => {
    for (const t of r.results) console.log(`${t.ok ? '✔' : '✘'} ${t.name}${t.detail ? ' — ' + t.detail : ''}`);
    console.log(`\n${r.passed}/${r.total} pruebas aprobadas`);
    process.exit(r.failed ? 1 : 0);
  });
}
