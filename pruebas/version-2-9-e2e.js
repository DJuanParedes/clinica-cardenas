/**
 * Versión 2.9.0: Libro de Reclamaciones virtual (registro público, número correlativo, código de consulta,
 * plazo en días hábiles, tarea de seguimiento, respuesta, PDF y reporte anual).
 * Uso (solo local, nunca contra producción: crea hojas de reclamación reales): node version-2-9-e2e.js http://localhost:8080
 */
async function v29(BASE) {
  const results = [];
  const rnd = (n) => Math.floor(Math.random() * n);
  const j = async (m, u, b, t) => {
    const r = await fetch(BASE + u, { method: m, headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': '10.29.' + rnd(250) + '.' + rnd(250), ...(t ? { Authorization: 'Bearer ' + t } : {}) },
      body: b ? JSON.stringify(b) : undefined });
    const ct = r.headers.get('content-type') || '';
    if (/pdf|csv/.test(ct)) return { s: r.status, b: Buffer.from(await r.arrayBuffer()), ct };
    const x = await r.text(); try { return { s: r.status, b: JSON.parse(x) }; } catch { return { s: r.status, b: x }; }
  };
  const check = async (name, fn) => { try { results.push({ name, ok: true, detail: (await fn()) || '' }); } catch (e) { results.push({ name, ok: false, detail: String(e.message || e) }); } };
  const expect = (c, m) => { if (!c) throw new Error(m); };
  const login = async (u) => (await j('POST', '/api/auth/login', { username: u, password: 'Clinica2026' })).b.token;
  const ad = await login('admin'), rc = await login('recepcion'), cj = await login('caja');
  const dni = String(74000000 + rnd(8999999));
  const base = { kind: 'RECLAMO', fullName: 'Rosa Elena Quispe Mamani', docType: 'DNI', docNumber: dni, address: 'Av. Los Álamos 123, Chaclacayo',
    phone: '987654321', email: `reclamo.${rnd(1e6)}@correo.pe`, service: 'Curación de resina', amount: 120, detail: 'La resina se cayó a la semana de colocada y no me dieron cita pronto.',
    request: 'Que me la vuelvan a colocar sin costo.' };
  let filed;

  await check('Formulario público: datos del proveedor y número que sigue, sin iniciar sesión', async () => {
    const r = await j('GET', '/api/public/complaints/info');
    expect(r.s === 200 && /^\d{11}$/.test(r.b.ruc) && r.b.businessName && r.b.address, 'datos del proveedor ' + JSON.stringify(r.b));
    expect(/^\d{4}-\d{6}$/.test(r.b.nextNumber) && r.b.termDays >= 1, 'número o plazo');
  });

  await check('Valida campos obligatorios, documento y apoderado de menores', async () => {
    let r = await j('POST', '/api/public/complaints', { ...base, kind: 'OTRO', docNumber: '123', detail: 'corto', address: '' });
    expect(r.s === 400 && r.b.fields && r.b.fields.kind && r.b.fields.docNumber && r.b.fields.detail && r.b.fields.address, 'no marcó los campos ' + JSON.stringify(r.b).slice(0, 200));
    r = await j('POST', '/api/public/complaints', { ...base, minor: true });
    expect(r.s === 400 && r.b.fields.guardianName && r.b.fields.guardianDoc, 'aceptó un menor sin apoderado');
  });

  await check('Registra con correlativo, código, plazo en días hábiles y tarea', async () => {
    const before = (await j('GET', '/api/public/complaints/info')).b.nextNumber;
    const r = await j('POST', '/api/public/complaints', base);
    expect(r.s === 201, 'registro ' + r.s + ' ' + JSON.stringify(r.b).slice(0, 200));
    filed = r.b;
    expect(filed.number === before, `número ${filed.number} ≠ ${before}`);
    expect(/^[A-Z2-9]{8}$/.test(filed.code), 'código ' + filed.code);
    const due = new Date(filed.dueDate + 'T12:00:00'), now = new Date();
    const days = (due - now) / 864e5;
    expect(days > 15 && days < 30 && due.getDay() !== 0 && due.getDay() !== 6, 'plazo ' + filed.dueDate);
    const second = (await j('POST', '/api/public/complaints', { ...base, kind: 'QUEJA', minor: true, guardianName: 'Elena Mamani Ríos', guardianDoc: '40123456', amount: null })).b;
    expect(+second.number.slice(5) === +filed.number.slice(5) + 1, 'no es correlativo');
    const tasks = (await j('GET', '/api/tasks?scope=unassigned&status=PENDIENTE', null, ad)).b;
    const list = Array.isArray(tasks) ? tasks : tasks.items || tasks.content || [];
    expect(list.some((t) => t.origin === 'RECLAMO' && t.originRef === filed.number && t.priority === 'ALTA'), 'no creó la tarea');
    return `${filed.number} · ${filed.code} · vence ${filed.dueDate}`;
  });

  await check('Consulta pública solo con código + documento, y PDF de la hoja', async () => {
    expect((await j('GET', `/api/public/complaints/${filed.code}?doc=11111111`)).s === 404, 'mostró la hoja con otro documento');
    const r = await j('GET', `/api/public/complaints/${filed.code}?doc=${dni}`);
    expect(r.s === 200 && r.b.status === 'PENDIENTE' && r.b.response === null && !('email' in r.b) && !('address' in r.b), 'consulta ' + JSON.stringify(r.b).slice(0, 200));
    const pdf = await j('GET', `/api/public/complaints/${filed.code}/pdf?doc=${dni}`);
    expect(pdf.s === 200 && pdf.b.slice(0, 5).toString() === '%PDF-', 'pdf');
  });

  await check('Módulo interno: permisos, lista con plazo y resumen', async () => {
    expect((await j('GET', '/api/complaints', null, cj)).s === 403, 'caja ve el libro');
    const l = (await j('GET', '/api/complaints?status=ABIERTOS', null, rc)).b;
    const c = l.find((x) => x.number === filed.number);
    expect(c && c.daysLeft >= 15 && c.overdue === false && c.email === base.email, 'no aparece en la lista ' + JSON.stringify(c || {}).slice(0, 200));
    const s = (await j('GET', '/api/complaints/summary', null, rc)).b;
    expect(s.open >= 2 && s.year >= 2, 'resumen ' + JSON.stringify(s));
  });

  await check('Respuesta: valida, cierra la tarea y queda en la hoja y en la consulta pública', async () => {
    const id = (await j('GET', `/api/complaints?q=${filed.code}`, null, rc)).b[0].id;
    expect((await j('POST', `/api/complaints/${id}/respond`, { response: 'ok', channel: 'EMAIL' }, rc)).s === 400, 'aceptó una respuesta corta');
    expect((await j('PUT', `/api/complaints/${id}`, { status: 'EN_PROCESO', providerNotes: 'Se revisó la historia clínica.' }, rc)).b.status === 'EN_PROCESO', 'en proceso');
    const r = await j('POST', `/api/complaints/${id}/respond`, { response: 'Revisamos su caso: la resina será recolocada sin costo. La esperamos el lunes a las 10:00.', channel: 'EMAIL' }, ad);
    expect(r.s === 200 && r.b.status === 'RESPONDIDO' && r.b.taskStatus === 'HECHA' && r.b.providerNotes, 'respuesta ' + JSON.stringify(r.b).slice(0, 200));
    expect((await j('POST', `/api/complaints/${id}/respond`, { response: 'Otra respuesta más larga de veinte', channel: 'PRESENCIAL' }, ad)).s === 400, 'respondió dos veces');
    const pub = (await j('GET', `/api/public/complaints/${filed.code}?doc=${dni}`)).b;
    expect(pub.status === 'RESPONDIDO' && pub.response.includes('recolocada'), 'la consulta pública no muestra la respuesta');
  });

  await check('Reporte anual CSV y ajustes del plazo (1 a 30 días hábiles)', async () => {
    const y = new Date().getFullYear();
    const csv = await j('GET', `/api/complaints/export?year=${y}`, null, ad);
    expect(csv.s === 200 && csv.b.toString('utf8').includes(filed.number) && csv.b.toString('utf8').includes('Observaciones del proveedor'), 'csv');
    expect((await j('PUT', '/api/complaints/settings', { termDays: '45' }, ad)).s === 400, 'aceptó 45 días');
    expect((await j('PUT', '/api/complaints/settings', { termDays: '15', noticeEmail: 'no-es-correo' }, ad)).s === 400, 'aceptó un correo inválido');
    expect((await j('PUT', '/api/complaints/settings', { termDays: '15' }, ad)).b.termDays === 15, 'guardar plazo');
  });

  const passed = results.filter((r) => r.ok).length;
  return { passed, failed: results.length - passed, total: results.length, results };
}
if (typeof module !== 'undefined' && require.main === module) {
  v29(process.argv[2] || 'http://localhost:8080').then((r) => {
    for (const t of r.results) console.log(`${t.ok ? '✔' : '✘'} ${t.name}${t.detail ? ' — ' + t.detail : ''}`);
    console.log(`\n${r.passed}/${r.total} pruebas aprobadas`);
    process.exit(r.failed ? 1 : 0);
  });
}
module.exports = { v29 };
