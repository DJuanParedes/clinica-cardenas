/**
 * Versión 2.6.0: plantillas de mensajes, automatizaciones (5 días, 1 día, el día, después) con botones Confirmar /
 * No podré asistir, enlace público de reserva, prerregistro y encuesta de satisfacción.
 * Uso (solo local, nunca contra producción: crea pacientes y envía mensajes): node version-2-6-e2e.js http://localhost:8080
 */
async function v26(BASE) {
  const results = [];
  const ip = `10.${rnd(250)}.${rnd(250)}.${rnd(250)}`;
  function rnd(n) { return Math.floor(Math.random() * n); }
  const j = async (m, u, b, t, extra = {}) => {
    const r = await fetch(BASE + u, { method: m, headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: 'Bearer ' + t } : {}), ...extra }, body: b ? JSON.stringify(b) : undefined });
    const x = await r.text(); try { return { s: r.status, b: JSON.parse(x) }; } catch { return { s: r.status, b: x }; }
  };
  const pub = (m, u, b) => j(m, u, b, null, { 'X-Forwarded-For': ip });
  const check = async (name, fn) => { try { results.push({ name, ok: true, detail: (await fn()) || '' }); } catch (e) { results.push({ name, ok: false, detail: String(e.message || e) }); } };
  const expect = (c, m) => { if (!c) throw new Error(m); };
  const login = async (u) => (await j('POST', '/api/auth/login', { username: u, password: 'Clinica2026' })).b.token;
  const ad = await login('admin'), rc = await login('recepcion'), dr = await login('dra.cardenas');
  const dni = () => String(71000000 + rnd(8999999));
  const cel = () => '9' + String(10000000 + rnd(89999999));
  const day = (n) => { const d = new Date(); d.setDate(d.getDate() + n); return d.toLocaleDateString('en-CA'); };

  await check('Solo administración gestiona plantillas y automatizaciones', async () => {
    expect((await j('GET', '/api/messaging/templates', null, rc)).s === 403, 'recepción entró a plantillas');
    expect((await j('PUT', '/api/messaging/automations/AUTO_5D', { active: false }, rc)).s === 403, 'recepción cambió una automatización');
    expect((await j('GET', '/api/messaging/history', null, rc)).s === 200, 'recepción no ve el historial de envíos');
  });

  await check('Plantillas: valida variables, previsualiza y restaura el texto original', async () => {
    const t = (await j('GET', '/api/messaging/templates', null, ad)).b;
    expect(t.templates.length >= 9 && t.variables.includes('paciente'), 'faltan plantillas o variables');
    const bad = await j('PUT', '/api/messaging/templates/AGRADECIMIENTO', { subject: 'Gracias', body: 'Hola {pacinte}, gracias por venir hoy.' }, ad);
    expect(bad.s === 400 && /pacinte/.test(JSON.stringify(bad.b)), 'aceptó una variable desconocida');
    const many = await j('PUT', '/api/messaging/templates/RECORDATORIO', { subject: 'Recordatorio', body: 'Hola {paciente}, te esperamos el {fecha_hora}.', buttons: ['Sí', 'No', 'Tal vez', 'Otro'] }, ad);
    expect(many.s === 400, 'aceptó 4 botones');
    const ok = await j('PUT', '/api/messaging/templates/AGRADECIMIENTO', { subject: 'Gracias · {clinica}', body: 'Hola {paciente}, gracias por venir con {odontologo}. ¡Te esperamos!' }, ad);
    expect(ok.s === 200, 'no guardó ' + ok.s);
    const pv = await j('POST', '/api/messaging/templates/AGRADECIMIENTO/preview', { subject: 'x {clinica}', body: 'Hola {paciente} ({fecha})' }, ad);
    expect(pv.s === 200 && !pv.b.body.includes('{paciente}'), 'la vista previa no reemplaza variables');
    const rs = await j('POST', '/api/messaging/templates/AGRADECIMIENTO/restore', {}, ad);
    expect(rs.s === 200, 'no restauró');
    const again = (await j('GET', '/api/messaging/templates', null, ad)).b.templates.find((x) => x.code === 'AGRADECIMIENTO');
    expect(!again.customized, 'sigue marcada como editada');
  });

  await check('Automatizaciones: 5 reglas, validan horario y rango', async () => {
    const r = (await j('GET', '/api/messaging/automations', null, ad)).b;
    expect(r.length === 5, 'reglas: ' + r.length);
    expect((await j('PUT', '/api/messaging/automations/AUTO_5D', { active: true, amount: 5, unit: 'DIAS', sendTime: '05:00' }, ad)).s === 400, 'aceptó enviar a las 5 a. m.');
    expect((await j('PUT', '/api/messaging/automations/AUTO_5D', { active: true, amount: 40, unit: 'DIAS', sendTime: '10:00' }, ad)).s === 400, 'aceptó 40 días');
    const five = r.find((x) => x.code === 'AUTO_5D');
    const ok = await j('PUT', '/api/messaging/automations/AUTO_5D', { active: five.active, amount: five.amount, unit: five.unit, sendTime: five.sendTime }, ad);
    expect(ok.s === 200, 'no guardó ' + ok.s);
  });

  const opts = (await pub('GET', '/api/public/booking/options')).b;
  const dentist = opts.dentists?.[0];
  let booked = null, person = null;

  await check('Reserva pública: opciones y horarios libres sin iniciar sesión', async () => {
    expect(opts.enabled && opts.dentists.length && opts.reasons.length, 'sin opciones');
    let d = 6, s = null;
    for (; d < 20 && !(s && s.slots.length); d++) s = (await pub('GET', `/api/public/booking/slots?dentistId=${dentist.id}&date=${day(d)}&reasonCode=${opts.reasons[0].code}`)).b;
    expect(s.slots.length, 'no hay horarios libres en 2 semanas');
    booked = { date: day(d - 1), startAt: s.slots[0] };
    return `${s.slots.length} horarios el ${booked.date}`;
  });

  await check('Reserva pública: exige consentimiento y crea paciente + cita con aviso de reserva', async () => {
    person = { docType: 'DNI', docNumber: dni(), firstName: 'Valeria', lastName: 'Web Dos Seis', birthDate: '1990-03-15', sex: 'F', phone: cel(), source: 'INSTAGRAM', dataConsent: false };
    const req = { person, dentistId: dentist.id, startAt: booked.startAt, reasonCode: opts.reasons[0].code };
    const no = await pub('POST', '/api/public/booking', req);
    expect(no.s === 400 && no.b.fields?.dataConsent, 'reservó sin consentimiento');
    person.dataConsent = true;
    const r = await pub('POST', '/api/public/booking', req);
    expect(r.s === 200 && r.b.id, 'reserva ' + r.s + ' ' + JSON.stringify(r.b).slice(0, 200));
    booked.id = r.b.id;
    const a = (await j('GET', `/api/appointments/${r.b.id}`, null, rc)).b;
    booked.patientId = a.patient?.id || a.patientId;
    expect(a.bookedVia === 'WEB', 'bookedVia ' + a.bookedVia);
    const p = (await j('GET', `/api/patients/${booked.patientId}`, null, rc)).b.patient;
    expect(p.source === 'INSTAGRAM' && p.hcNumber, 'paciente sin fuente o N° HC');
  });

  await check('Automatizaciones programadas: aviso previo, recordatorio con botones y el día de la cita', async () => {
    const n = (await j('GET', `/api/patients/${booked.patientId}/notifications`, null, rc)).b.filter((x) => x.appointmentId === booked.id);
    expect(n.some((x) => x.templateCode === 'RESERVA_WEB' && x.operation === 'ENVIAR'), 'sin mensaje de reserva web');
    for (const t of ['AVISO_PREVIO', 'RECORDATORIO', 'RECORDATORIO_DIA']) expect(n.some((x) => x.type === t && x.operation === 'PROGRAMAR'), 'falta ' + t);
    const rem = n.find((x) => x.type === 'RECORDATORIO' && x.channel === 'WHATSAPP');
    expect(rem && /Confirmar/.test(rem.waButtons || ''), 'el recordatorio no lleva botones');
    return n.length + ' avisos';
  });

  await check('Reserva pública: paciente existente con fecha de nacimiento distinta es rechazado', async () => {
    let s = null;
    for (let d = 7; d < 25 && !(s && s.slots.length > 1); d++) s = (await pub('GET', `/api/public/booking/slots?dentistId=${dentist.id}&date=${day(d)}&reasonCode=${opts.reasons[0].code}`)).b;
    const r = await pub('POST', '/api/public/booking', { person: { ...person, birthDate: '1991-03-15' }, dentistId: dentist.id, startAt: s.slots[s.slots.length - 1], reasonCode: opts.reasons[0].code });
    expect(r.s === 400 && r.b.fields?.birthDate, 'aceptó datos que no coinciden: ' + r.s);
  });

  await check('Pausar el enlace de reservas lo cierra al público', async () => {
    const cur = (await j('GET', '/api/messaging/public-links', null, ad)).b;
    expect((await j('PUT', '/api/messaging/public-links', { bookingEnabled: false, registerEnabled: cur.registerEnabled }, ad)).s === 200, 'no pausó');
    const r = await pub('GET', `/api/public/booking/slots?dentistId=${dentist.id}&date=${booked.date}&reasonCode=${opts.reasons[0].code}`);
    await j('PUT', '/api/messaging/public-links', { bookingEnabled: true, registerEnabled: cur.registerEnabled }, ad);
    expect(r.s === 503, 'siguió abierto: ' + r.s);
  });

  await check('Prerregistro: crea la ficha con alertas de salud y no duplica documentos', async () => {
    const f = (await pub('GET', '/api/public/preregistro')).b;
    expect(f.enabled && f.questions.length, 'formulario sin preguntas');
    const q = f.questions[0];
    const p = { docType: 'DNI', docNumber: dni(), firstName: 'Renzo', lastName: 'Ficha Web', birthDate: '1979-08-01', sex: 'M', phone: cel(), dataConsent: true };
    const r = await pub('POST', '/api/public/preregistro', { person: p, answers: { [q.id]: { value: 'SI', note: 'desde 2020' } }, allergies: 'Penicilina', medication: '' });
    expect(r.s === 200 && r.b.id, 'prerregistro ' + r.s);
    const sum = (await j('GET', `/api/patients/${r.b.id}`, null, dr)).b;
    expect((sum.anamnesisAlerts || []).length >= 1, 'la alerta no llegó a la ficha');
    const dup = await pub('POST', '/api/public/preregistro', { person: p, answers: {} });
    expect(dup.s === 409, 'duplicó el documento: ' + dup.s);
  });

  await check('Cita atendida: agradecimiento y encuesta; la encuesta se califica una sola vez', async () => {
    const now = new Date();
    const pt = (await j('POST', '/api/patients', { dataConsent: true, docType: 'DNI', docNumber: dni(), firstName: 'Lucía', lastName: 'Encuesta E2E', birthDate: '1988-01-20', sex: 'F', phone: cel() }, rc)).b;
    let slot = null;
    for (const dId of opts.dentists.map((d) => d.id)) {
      const s = (await j('GET', `/api/agenda/slots?dentistId=${dId}&date=${now.toLocaleDateString('en-CA')}&duration=30&patientId=${pt.id}`, null, rc)).b;
      const free = (s.slots || []).find((x) => x.available);
      if (free) { slot = { dId, start: free.start }; break; }
    }
    let a;
    if (slot) {
      a = await j('POST', '/api/appointments', { patientId: pt.id, dentistId: slot.dId, startAt: slot.start, durationMin: 30, reason: 'Control' }, rc);
      expect(a.s === 200, 'cita ' + a.s + JSON.stringify(a.b).slice(0, 200));
    } else {
      // Sin horarios libres hoy: se usa una cita pendiente de hoy (solo en la base local).
      const list = (await j('GET', `/api/agenda/day?date=${now.toLocaleDateString('en-CA')}`, null, rc)).b;
      const all = (list.appointments || list.items || list).flat ? (list.appointments || list.items || list) : [];
      const pend = all.find((x) => ['PROGRAMADA', 'CONFIRMADA'].includes(x.status) && x.patient?.phone);
      if (!pend) return 'omitida: sin horarios libres ni citas pendientes hoy';
      a = { b: pend }; pt.id = pend.patient.id;
    }
    for (const st of ['EN_ESPERA', 'EN_ATENCION', 'ATENDIDA']) {
      const r = await j('PATCH', `/api/appointments/${a.b.id}/status`, { status: st }, st === 'EN_ESPERA' ? rc : dr);
      expect(r.s === 200, st + ' ' + r.s + ' ' + JSON.stringify(r.b).slice(0, 160));
    }
    const n = (await j('GET', `/api/patients/${pt.id}/notifications`, null, rc)).b;
    expect(n.some((x) => x.templateCode === 'AGRADECIMIENTO'), 'sin agradecimiento');
    const enc = n.find((x) => x.templateCode === 'ENCUESTA');
    const token = (enc?.body || '').match(/encuesta\/([\w.-]+)/)?.[1];
    expect(token, 'la encuesta no trae enlace');
    expect((await pub('GET', `/api/public/survey/${token.slice(0, -1)}x`)).s === 404, 'aceptó un enlace alterado');
    const g = await pub('GET', `/api/public/survey/${token}`);
    expect(g.s === 200 && !g.b.rated, 'encuesta ' + g.s);
    expect((await pub('POST', `/api/public/survey/${token}`, { score: 5, comment: 'Muy buena atención' })).s === 200, 'no guardó la calificación');
    expect((await pub('POST', `/api/public/survey/${token}`, { score: 1 })).s === 409, 'permitió calificar dos veces');
  });

  await check('Historial de envíos filtra por plantilla', async () => {
    const h = (await j('GET', '/api/messaging/history?template=RESERVA_WEB', null, ad)).b;
    expect(h.length && h.every((x) => x.templateCode === 'RESERVA_WEB'), 'filtro');
  });

  const passed = results.filter((r) => r.ok).length;
  return { passed, failed: results.length - passed, total: results.length, results };
}
if (typeof module !== 'undefined' && require.main === module) {
  v26(process.argv[2] || 'http://localhost:8080').then((r) => {
    for (const t of r.results) console.log(`${t.ok ? '✔' : '✘'} ${t.name}${t.detail ? ' — ' + t.detail : ''}`);
    console.log(`\n${r.passed}/${r.total} pruebas aprobadas`);
    process.exit(r.failed ? 1 : 0);
  });
}
