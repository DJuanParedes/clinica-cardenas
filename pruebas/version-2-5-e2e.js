/**
 * Versión 2.5.0: ficha ampliada (captación, etiquetas, N° HC, RUC, familiares), anamnesis por plantillas, signos
 * vitales, periodontograma y órdenes de laboratorio con pago por caja.
 * Uso (solo local, nunca contra producción): node version-2-5-e2e.js http://localhost:8080
 */
async function v25(BASE) {
  const results = [];
  const j = async (m, u, b, t) => { const r = await fetch(BASE + u, { method: m, headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: 'Bearer ' + t } : {}) }, body: b ? JSON.stringify(b) : undefined }); const x = await r.text(); try { return { s: r.status, b: JSON.parse(x) }; } catch { return { s: r.status, b: x }; } };
  const check = async (name, fn) => { try { results.push({ name, ok: true, detail: (await fn()) || '' }); } catch (e) { results.push({ name, ok: false, detail: String(e.message || e) }); } };
  const expect = (c, m) => { if (!c) throw new Error(m); };
  const login = async (u) => (await j('POST', '/api/auth/login', { username: u, password: 'Clinica2026' })).b.token;
  const rc = await login('recepcion'), dr = await login('dra.cardenas'), cj = await login('caja');
  const mk = async (fn) => {
    const dni = String(70000000 + Math.floor(Math.random() * 9999999));
    return (await j('POST', '/api/patients', { dataConsent: true, docType: 'DNI', docNumber: dni, firstName: fn, lastName: 'Prueba Dos Cinco', birthDate: '1985-06-10', sex: 'F', phone: '9' + String(10000000 + Math.floor(Math.random() * 89999999)) }, rc)).b;
  };
  const a = await mk('Ana'), b = await mk('Beto');

  await check('Al registrar, el paciente recibe su N° de historia clínica', async () => {
    expect(/^\d{6}$/.test(a.hcNumber || ''), 'sin N° HC: ' + a.hcNumber);
    const r = await j('GET', '/api/patients?q=' + a.hcNumber, null, rc);
    expect(r.b.content.some((p) => p.id === a.id), 'no se encuentra por N° HC');
    return 'HC ' + a.hcNumber;
  });
  await check('Fuente de captación, etiquetas y RUC: se validan y sirven para filtrar', async () => {
    const tag = 'E2E-' + Date.now() % 100000;
    const ok = await j('PUT', `/api/patients/${a.id}/profile`, { source: 'INSTAGRAM', tags: [tag, tag.toLowerCase(), 'VIP'], fiscalRuc: '20601928630', fiscalName: 'EMPRESA PRUEBA SAC' }, rc);
    expect(ok.s === 200 && ok.b.tags.length === 2, 'etiquetas duplicadas o error ' + ok.s);
    const bad = await j('PUT', `/api/patients/${b.id}/profile`, { fiscalRuc: '20601928631', fiscalName: 'X', hcNumber: a.hcNumber }, rc);
    expect(bad.s === 400 && bad.b.fields.fiscalRuc && bad.b.fields.hcNumber, 'aceptó RUC inválido o N° HC repetido');
    const f = await j('GET', `/api/patients?tag=${tag}&source=INSTAGRAM`, null, rc);
    expect(f.b.total === 1 && f.b.content[0].id === a.id, 'filtro ' + f.b.total);
    const caja = await j('GET', `/api/patients/${a.id}`, null, cj);
    expect(caja.b.patient.fiscalRuc === '20601928630' && caja.b.patient.restricted, 'caja no ve el RUC para facturar');
  });
  await check('Familiares: vínculo con otro paciente se ve desde ambos lados', async () => {
    const r = await j('POST', `/api/patients/${a.id}/relations`, { relatedPatientId: b.id, relationship: 'HIJO', pays: false }, rc);
    expect(r.s === 200 && r.b.relations.length === 1, 'vincular ' + r.s);
    const dup = await j('POST', `/api/patients/${a.id}/relations`, { relatedPatientId: b.id, relationship: 'HIJO' }, rc);
    expect(dup.s === 400, 'permitió duplicado');
    const inv = await j('GET', `/api/patients/${b.id}/profile`, null, rc);
    expect(inv.b.relations.some((x) => x.relatedPatientId === a.id && x.inverse), 'no se ve el vínculo inverso');
  });
  await check('Anamnesis: alertas suben a la ficha y recepción no puede verla', async () => {
    const t = (await j('GET', '/api/anamnesis/templates', null, dr)).b;
    expect(t.length >= 3, 'faltan plantillas');
    const r = await j('POST', `/api/patients/${a.id}/anamnesis`, { templateId: t[0].id, answers: { motivo: { value: 'Control' }, diabetes: { value: 'SI', note: 'metformina' }, hta: { value: 'NO' } } }, dr);
    expect(r.s === 200 && r.b.alerts.some((x) => x.includes('Diabetes')), 'sin alerta');
    expect((await j('GET', `/api/patients/${a.id}/anamnesis`, null, rc)).s === 403, 'recepción vio datos de salud');
    const sum = await j('GET', `/api/patients/${a.id}`, null, dr);
    expect((sum.b.anamnesisAlerts || []).length >= 1, 'la ficha no muestra la alerta');
    const sumRc = await j('GET', `/api/patients/${a.id}`, null, rc);
    expect(!('anamnesisAlerts' in sumRc.b), 'recepción ve alertas clínicas');
  });
  await check('Signos vitales: valida rangos y marca presión alta', async () => {
    const bad = await j('POST', `/api/patients/${a.id}/vitals`, { systolic: 120 }, dr);
    expect(bad.s === 400, 'aceptó presión incompleta');
    const r = await j('POST', `/api/patients/${a.id}/vitals`, { systolic: 185, diastolic: 112, heartRate: 105, weight: 60, height: 160 }, dr);
    expect(r.s === 200 && r.b[0].flags.some((f) => f.includes('muy alta')) && r.b[0].bmi === 23.4, JSON.stringify(r.b[0]));
  });
  await check('Periodontograma: calcula índices solo con piezas presentes', async () => {
    const data = { 11: { v: { ps: [5, 2, 2], bleed: [true, false, false], plaque: [true, true, false], mg: [0, 0, 0] } }, 18: { missing: true } };
    const r = await j('POST', `/api/patients/${a.id}/periodontograms`, { data }, dr);
    expect(r.s === 200 && r.b.teethPresent === 31 && r.b.deepSites === 1 && Number(r.b.plaqueIndex) === 1.08, JSON.stringify(r.b).slice(0, 200));
    const bad = await j('POST', `/api/patients/${a.id}/periodontograms`, { data: { 21: { v: { ps: [30, 0, 0] } } } }, dr);
    expect(bad.s === 400, 'aceptó sondaje de 30 mm');
  });
  await check('Laboratorio: estados en orden, pago por caja como egreso y saldo', async () => {
    const lab = (await j('POST', '/api/laboratories', { name: 'Lab E2E ' + Date.now() }, rc)).b;
    const o = (await j('POST', '/api/lab-orders', { patientId: a.id, laboratoryId: lab.id, workType: 'CORONA', description: 'Corona zirconio', teeth: '16', cost: 300, send: true }, dr)).b;
    expect(o.status === 'ENVIADA' && o.balance === 300, 'orden ' + JSON.stringify(o).slice(0, 120));
    expect((await j('POST', `/api/lab-orders/${o.id}/status`, { status: 'ENTREGADA' }, dr)).s === 400, 'saltó estados');
    expect((await j('POST', `/api/lab-orders/${o.id}/payments`, { amount: 100, method: 'YAPE' }, dr)).s === 403, 'odontólogo pagó');
    expect((await j('POST', `/api/lab-orders/${o.id}/payments`, { amount: 100, method: 'YAPE' }, cj)).s === 400, 'Yape sin número de operación');
    const p = await j('POST', `/api/lab-orders/${o.id}/payments`, { amount: 100, method: 'YAPE', reference: 'YP-100' }, cj);
    expect(p.s === 200 && p.b.balance === 200 && p.b.payments.length === 1, 'pago ' + p.s);
    expect((await j('POST', `/api/lab-orders/${o.id}/payments`, { amount: 250, method: 'YAPE' }, cj)).s === 400, 'pagó más del saldo');
    for (const s of ['RECIBIDA', 'ENTREGADA']) expect((await j('POST', `/api/lab-orders/${o.id}/status`, { status: s }, rc)).s === 200, s);
    const list = await j('GET', `/api/lab-orders?patientId=${a.id}`, null, rc);
    expect(list.b.items.length === 1 && Number(list.b.summary.porPagar) === 200, 'resumen ' + JSON.stringify(list.b.summary));
  });
  const passed = results.filter((r) => r.ok).length;
  return { passed, failed: results.length - passed, total: results.length, results };
}
if (typeof module !== 'undefined' && require.main === module) {
  v25(process.argv[2] || 'http://localhost:8080').then((r) => {
    for (const t of r.results) console.log(`${t.ok ? '✔' : '✘'} ${t.name}${t.detail ? ' — ' + t.detail : ''}`);
    console.log(`\n${r.passed}/${r.total} pruebas aprobadas`);
    process.exit(r.failed ? 1 : 0);
  });
}
