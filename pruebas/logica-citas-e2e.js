/*
 * Pruebas de coherencia de la agenda · Clínica Dental Cárdenas
 * Verifica que un paciente nunca quede en dos consultorios a la vez, que un odontólogo no tenga dos citas cruzadas
 * y que los estados respeten el reloj. Crea pacientes de prueba con DNI inventado.
 *
 * NO ejecutar en producción (crea pacientes y citas). Uso:
 *   node pruebas/logica-citas-e2e.js http://localhost:9081
 * o pegar en la consola del navegador estando en http://localhost:9081 y ejecutar: await logicaCitasE2E(location.origin)
 */
async function logicaCitasE2E(BASE) {
  const PWD = 'Clinica2026';
  const results = [];
  const api = async (method, path, body, token) => {
    const res = await fetch(BASE + path, {
      method,
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
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
  const login = async (u) => { const r = await api('POST', '/api/auth/login', { username: u, password: PWD }); expect(r.status === 200, 'login ' + u + ': ' + r.status); return r.body.token; };
  const pad = (n) => String(n).padStart(2, '0');
  const iso = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  const at = (dayIso, hm) => `${dayIso}T${hm}:00`;
  const letters = (n) => String(n).split('').map((d) => 'abcdefghij'[Number(d)]).join('');
  const uid = Date.now().toString().slice(-6);

  const recep = await login('recepcion');
  const dentists = (await api('GET', '/api/dentists', undefined, recep)).body;
  expect(dentists.length >= 2, 'se necesitan dos odontólogos activos');
  const [d1, d2] = dentists;

  // Día hábil en el que ambos odontólogos atienden a las 15:00-17:00.
  let dayIso = null;
  for (let i = 3; i < 20 && !dayIso; i++) {
    const d = new Date(); d.setDate(d.getDate() + i);
    if (d.getDay() === 0 || d.getDay() === 6) continue;
    const s1 = (await api('GET', `/api/agenda/slots?dentistId=${d1.id}&date=${iso(d)}&duration=30`, undefined, recep)).body;
    const s2 = (await api('GET', `/api/agenda/slots?dentistId=${d2.id}&date=${iso(d)}&duration=30`, undefined, recep)).body;
    const free = (s, hm) => s.slots.some((x) => x.start.startsWith(at(iso(d), hm)) && x.available);
    if (['15:00', '15:30', '16:00', '16:30'].every((h) => free(s1, h) && free(s2, h))) dayIso = iso(d);
  }
  expect(dayIso, 'no hay un día con la tarde libre para ambos odontólogos');

  const newPatient = async (k) => {
    const dni = String(60000000 + Math.floor(Math.random() * 9999999));
    const r = await api('POST', '/api/patients', { dataConsent: true,
      docType: 'DNI', docNumber: dni, firstName: 'Prueba' + letters(uid) + 'abcdefghij'[k], lastName: 'Agenda Logica',
      birthDate: '1990-05-10', sex: 'F', phone: '9' + String(10000000 + Math.floor(Math.random() * 89999999)), district: 'Chaclacayo',
    }, recep);
    expect(r.status === 201 || r.status === 200, 'crear paciente: ' + r.status + ' ' + JSON.stringify(r.body).slice(0, 200));
    return r.body.patient || r.body;
  };
  const book = (p, d, hm, min = 30) => api('POST', '/api/appointments', { patientId: p.id, dentistId: d.id, startAt: at(dayIso, hm), durationMin: min, reason: 'Prueba de lógica' }, recep);

  const pa = await newPatient(1), pb = await newPatient(2), pc = await newPatient(3);
  let base;
  await check('Cita base: 15:00 con el odontólogo 1, 60 minutos', async () => {
    const r = await book(pa, d1, '15:00', 60);
    expect(r.status === 201, 'no se creó: ' + r.status + ' ' + JSON.stringify(r.body).slice(0, 200));
    base = r.body;
    return `${d1.fullName || d1.user?.fullName || 'odontólogo 1'} 15:00-16:00`;
  });
  await check('El paciente NO puede tener otra cita a la misma hora con otro odontólogo', async () => {
    const r = await book(pa, d2, '15:00');
    expect(r.status === 409 && r.body.code === 'CRUCE_PACIENTE', 'aceptó: ' + r.status);
    return r.body.message;
  });
  await check('El paciente NO puede tener otra cita 30 minutos después mientras sigue en la primera (15:30)', async () => {
    const r = await book(pa, d2, '15:30');
    expect(r.status === 409 && r.body.code === 'CRUCE_PACIENTE', 'aceptó: ' + r.status);
  });
  await check('Una cita que empieza antes y termina dentro de la otra también se rechaza (14:30-15:30)', async () => {
    const r = await api('GET', `/api/agenda/slots?dentistId=${d2.id}&date=${dayIso}&duration=60&patientId=${pa.id}`, undefined, recep);
    const s = r.body.slots.find((x) => x.start.startsWith(at(dayIso, '14:30')));
    if (s) {
      const b = await book(pa, d2, '14:30', 60);
      expect(b.status === 409, 'aceptó un cruce parcial: ' + b.status);
    }
    return s ? 'rechazada' : 'el odontólogo 2 no atiende a las 14:30 (se omite)';
  });
  await check('Al terminar la primera (16:00) sí puede ir con otro odontólogo', async () => {
    const r = await book(pa, d2, '16:00');
    expect(r.status === 201, 'rechazó una cita sin cruce: ' + r.status + ' ' + JSON.stringify(r.body).slice(0, 200));
  });
  await check('Los horarios para ese paciente ocultan las horas en que ya está ocupado', async () => {
    const r = await api('GET', `/api/agenda/slots?dentistId=${d2.id}&date=${dayIso}&duration=30&patientId=${pa.id}`, undefined, recep);
    const s1530 = r.body.slots.find((x) => x.start.startsWith(at(dayIso, '15:30')));
    expect(s1530 && !s1530.available, 'ofrece 15:30 aunque el paciente está con otro odontólogo');
    const libre = await api('GET', `/api/agenda/slots?dentistId=${d2.id}&date=${dayIso}&duration=30&patientId=${pb.id}`, undefined, recep);
    const s2 = libre.body.slots.find((x) => x.start.startsWith(at(dayIso, '15:30')));
    expect(s2 && s2.available, 'a otro paciente libre no le ofrece 15:30');
  });
  await check('El odontólogo NO puede atender a otro paciente en un horario cruzado (15:30)', async () => {
    const r = await book(pb, d1, '15:30');
    expect(r.status === 409 && r.body.code === 'CRUCE_HORARIO', 'aceptó: ' + r.status);
  });
  await check('Reprogramar a una hora cruzada con otra cita del mismo paciente se rechaza', async () => {
    const second = (await api('GET', `/api/agenda/day?date=${dayIso}&dentistId=${d2.id}`, undefined, recep)).body.appointments
      .find((a) => a.patient.id === pa.id);
    expect(second, 'no se encontró la cita de las 16:00');
    const r = await api('PUT', '/api/appointments/' + second.id, { dentistId: d2.id, startAt: at(dayIso, '15:30'), durationMin: 30, changeReason: 'Prueba' }, recep);
    expect(r.status === 409 && r.body.code === 'CRUCE_PACIENTE', 'permitió reprogramar encima: ' + r.status);
  });
  await check('Al cancelar una cita, su horario queda libre otra vez', async () => {
    const c = await api('PATCH', `/api/appointments/${base.id}/status`, { status: 'CANCELADA', reason: 'Prueba' }, recep);
    expect(c.status === 200, 'cancelar: ' + c.status);
    const r = await book(pc, d1, '15:00');
    expect(r.status === 201, 'el horario cancelado sigue ocupado: ' + r.status);
  });
  await check('Concurrencia: el mismo paciente reservado a la vez con dos odontólogos → solo una se acepta', async () => {
    const [x, y] = await Promise.all([book(pb, d1, '16:30'), book(pb, d2, '16:30')]);
    const codes = [x.status, y.status].sort();
    expect(codes[0] === 201 && codes[1] === 409, 'resultado ' + codes.join('/'));
    return codes.join(' / ');
  });
  await check('No se registra la llegada de una cita de otro día', async () => {
    const r = await book(pc, d2, '15:30');
    expect(r.status === 201, 'crear: ' + r.status + ' ' + JSON.stringify(r.body).slice(0, 160));
    const s = await api('PATCH', `/api/appointments/${r.body.id}/status`, { status: 'EN_ESPERA' }, recep);
    expect(s.status === 409 && s.body.code === 'FUERA_DE_FECHA', 'registró la llegada: ' + s.status);
    const n = await api('PATCH', `/api/appointments/${r.body.id}/status`, { status: 'NO_ASISTIO' }, recep);
    expect(n.status === 409 && n.body.code === 'CITA_NO_INICIADA', 'marcó inasistencia futura: ' + n.status);
  });
  await check('Duración inválida y hora fuera del bloque de 15 minutos se rechazan', async () => {
    const r1 = await api('POST', '/api/appointments', { patientId: pc.id, dentistId: d1.id, startAt: at(dayIso, '17:00'), durationMin: 20, reason: 'x' }, recep);
    const r2 = await api('POST', '/api/appointments', { patientId: pc.id, dentistId: d1.id, startAt: at(dayIso, '17:10'), durationMin: 30, reason: 'x' }, recep);
    expect(r1.status === 400 && r2.status === 400, `duración 20 → ${r1.status}, hora 17:10 → ${r2.status}`);
  });

  // Limpieza: cancela las citas de prueba que quedaron.
  for (const d of [d1, d2]) {
    const day = (await api('GET', `/api/agenda/day?date=${dayIso}&dentistId=${d.id}`, undefined, recep)).body;
    for (const a of day.appointments) {
      if ([pa.id, pb.id, pc.id].includes(a.patient.id) && ['PROGRAMADA', 'CONFIRMADA'].includes(a.status)) {
        await api('PATCH', `/api/appointments/${a.id}/status`, { status: 'CANCELADA', reason: 'Fin de la prueba' }, recep);
      }
    }
  }
  const passed = results.filter((r) => r.ok).length;
  return { dia: dayIso, passed, failed: results.length - passed, total: results.length, results };
}

if (typeof module !== 'undefined' && require.main === module) {
  logicaCitasE2E(process.argv[2] || 'http://localhost:9081').then((r) => {
    for (const t of r.results) console.log(`${t.ok ? '✔' : '✘'} ${t.name}${t.detail ? ' — ' + t.detail : ''}`);
    console.log(`\n${r.passed}/${r.total} pruebas aprobadas (día ${r.dia})`);
    process.exit(r.failed ? 1 : 0);
  });
}
