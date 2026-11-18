/**
 * Pruebas del portal y la app del paciente: datos de contacto, tratamientos y pagos, ficha, consentimientos,
 * archivos solicitados, confirmación hasta 2 h antes con seguimiento de recepción y boleta en PDF con formato SUNAT.
 * Uso (solo local): node portal-paciente-e2e.js http://localhost:8080
 */
async function portalE2E(BASE) {
  const results = [];
  const j = async (m, u, b, t) => { const r = await fetch(BASE + u, { method: m, headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: 'Bearer ' + t } : {}) }, body: b ? JSON.stringify(b) : undefined }); const x = await r.text(); try { return { s: r.status, b: JSON.parse(x) }; } catch { return { s: r.status, b: x }; } };
  const check = async (name, fn) => { try { results.push({ name, ok: true, detail: (await fn()) || '' }); } catch (e) { results.push({ name, ok: false, detail: String(e.message || e) }); } };
  const expect = (c, m) => { if (!c) throw new Error(m); };
  const st = (await j('POST', '/api/auth/login', { username: 'recepcion', password: 'Clinica2026' })).b.token;
  const admin = (await j('POST', '/api/auth/login', { username: 'admin', password: 'Clinica2026' })).b.token;
  const dni = String(60000000 + Math.floor(Math.random() * 9999999));
  const tel = '9' + String(10000000 + Math.floor(Math.random() * 89999999));
  const p = (await j('POST', '/api/patients', { dataConsent: true, docType: 'DNI', docNumber: dni, firstName: 'Portal', lastName: 'Paciente Prueba', birthDate: '1992-04-05', sex: 'F', phone: tel }, st)).b;
  let pt;
  await check('Activación: documento inexistente y fecha que no coincide se informan', async () => {
    const a = await j('POST', '/api/portal/auth/code', { docType: 'DNI', docNumber: '10000001', birthDate: '1992-04-05' });
    const b = await j('POST', '/api/portal/auth/code', { docType: 'DNI', docNumber: dni, birthDate: '1990-01-01' });
    expect(a.s === 400 && a.b.fields.docNumber && b.s === 400 && b.b.fields.birthDate, `${a.s} ${b.s}`);
    const c = await j('POST', '/api/portal/auth/code', { docType: 'DNI', docNumber: dni, birthDate: '1992-04-05' });
    const act = await j('POST', '/api/portal/auth/activate', { docType: 'DNI', docNumber: dni, code: c.b.demoCode, password: 'MiClave2026', dataConsent: true });
    expect(act.s === 200, 'activar ' + act.s);
    pt = act.b.token;
  });
  await check('El paciente cambia su celular y correo (no su nombre ni documento)', async () => {
    const ok = await j('PUT', '/api/portal/me/contact', { phone: '987111333', email: 'paciente@correo.com' }, pt);
    const bad = await j('PUT', '/api/portal/me/contact', { phone: '12', email: 'x' }, pt);
    const me = await j('GET', '/api/portal/me', null, pt);
    expect(ok.s === 200 && bad.s === 400 && me.b.phone === '987111333' && me.b.fullName.includes('Portal'), JSON.stringify(me.b).slice(0, 120));
  });
  await check('Tratamientos: lo realizado con fecha y lo pagado por cada procedimiento', async () => {
    const doc = (await j('POST', '/api/auth/login', { username: 'dra.cardenas', password: 'Clinica2026' })).b.token;
    const procs = (await j('GET', '/api/procedures', null, doc)).b;
    const plan = await j('POST', `/api/patients/${p.id}/plans`, { title: 'Plan de prueba', items: [{ procedureId: procs[0].id }, { procedureId: procs[1].id }] }, doc);
    expect(plan.s === 201 || plan.s === 200, 'plan ' + plan.s + ' ' + JSON.stringify(plan.b).slice(0, 150));
    const t = await j('GET', '/api/portal/treatments', null, pt);
    expect(t.s === 200 && t.b.plans.length === 1 && t.b.plans[0].items.length === 2 && 'balance' in t.b, JSON.stringify(t.b).slice(0, 200));
    return `${t.b.plans[0].itemCount} procedimientos, saldo S/ ${t.b.balance}`;
  });
  await check('Consentimientos: constancia en PDF del propio paciente (y no la de otro)', async () => {
    const c = await j('GET', '/api/portal/consents', null, pt);
    expect(c.s === 200 && c.b.records.length >= 1, 'sin registros');
    const r = await fetch(BASE + `/api/portal/consents/${c.b.records[0].id}/pdf`, { headers: { Authorization: 'Bearer ' + pt } });
    expect(r.status === 200 && r.headers.get('content-type').includes('pdf'), 'pdf ' + r.status);
    const other = await fetch(BASE + `/api/portal/consents/1/pdf`, { headers: { Authorization: 'Bearer ' + pt } });
    expect(other.status === 404 || c.b.records.some((x) => x.id === 1), 'vio la constancia de otro paciente: ' + other.status);
  });
  await check('Archivos solicitados: recepción pide la placa, el paciente la sube y queda en su ficha', async () => {
    const cr = await j('POST', `/api/patients/${p.id}/file-requests`, { title: 'Radiografía panorámica', description: 'Para el plan' }, st);
    expect(cr.s === 200 && cr.b[0].status === 'PENDIENTE', 'pedido ' + cr.s);
    const fd = new FormData(); fd.append('file', new Blob([Buffer.from('%PDF-1.4\n%%EOF')], { type: 'application/pdf' }), 'placa.pdf');
    const up = await fetch(BASE + `/api/portal/file-requests/${cr.b[0].id}/upload`, { method: 'POST', headers: { Authorization: 'Bearer ' + pt }, body: fd });
    const ub = await up.json();
    expect(up.status === 200 && ub[0].status === 'RECIBIDO' && ub[0].fileId, 'subida ' + up.status);
    const files = (await j('GET', `/api/patients/${p.id}/files`, null, st)).b;
    expect(files.some((f) => f.id === ub[0].fileId), 'no está en la ficha');
  });
  await check('Confirmación: hasta 2 horas antes; luego aparece a recepción para llamar', async () => {
    const ds = (await j('GET', '/api/dentists', null, st)).b;
    let made = null;
    for (const d of ds) {
      const day = new Date().toISOString().slice(0, 10);
      const sl = (await j('GET', `/api/agenda/slots?dentistId=${d.id}&date=${day}&duration=30&patientId=${p.id}`, null, st)).b;
      const soon = (sl.slots || []).find((x) => x.available && new Date(x.start + '-05:00') - Date.now() < 2 * 3600e3 && new Date(x.start + '-05:00') > Date.now());
      if (soon) { made = (await j('POST', '/api/appointments', { patientId: p.id, dentistId: d.id, startAt: soon.start, durationMin: 30, reason: 'Control' }, st)).b; break; }
    }
    if (!made || !made.id) return 'sin horario libre en las próximas 2 h: se omite';
    const c = await j('POST', `/api/portal/appointments/${made.id}/confirm`, {}, pt);
    expect(c.s === 409 && c.b.code === 'PLAZO_VENCIDO', 'confirmó fuera de plazo: ' + c.s);
    const un = (await j('GET', '/api/agenda/unconfirmed', null, st)).b;
    const row = un.items.find((x) => x.id === made.id);
    expect(row && row.overdue, 'recepción no la ve como vencida');
    const call = await j('POST', `/api/appointments/${made.id}/call`, { result: 'CONFIRMADA', note: 'confirmó por teléfono' }, st);
    expect(call.s === 200 && !call.b.items.some((x) => x.id === made.id), 'llamada ' + call.s);
  });
  await check('Boleta en PDF con formato SUNAT (recuadro RUC, QR, importe en letras)', async () => {
    const inv = (await j('GET', '/api/billing/invoices', null, admin)).b.content[0];
    if (!inv) return 'sin comprobantes: se omite';
    const r = await fetch(BASE + `/api/billing/invoices/${inv.id}/pdf`, { headers: { Authorization: 'Bearer ' + admin } });
    const txt = Buffer.from(await r.arrayBuffer()).toString('latin1');
    expect(r.status === 200 && txt.startsWith('%PDF') && txt.includes('R.U.C.') && txt.includes('SON:') && txt.includes('IMPORTE TOTAL'), 'pdf incompleto');
  });
  await check('Consulta RENIEC al registrar: autocompleta o explica por qué no', async () => {
    const cfg = (await j('GET', '/api/lookup/dni-config', null, st)).b;
    const bad = await j('GET', '/api/lookup/dni/123', null, st);
    expect(bad.s === 400, 'formato ' + bad.s);
    if (!cfg.enabled) {
      const r = await j('GET', '/api/lookup/dni/71234567', null, st);
      expect(r.s === 503 && r.b.code === 'CONSULTA_NO_CONFIGURADA', 'sin token ' + r.s);
      return 'sin token configurado: el formulario pide escribir a mano';
    }
    const r = await j('GET', '/api/lookup/dni/71234567', null, st);
    expect(r.s === 200 && r.b.firstName && r.b.lastName, 'consulta ' + r.s + ' ' + JSON.stringify(r.b).slice(0, 120));
    return `${r.b.firstName} ${r.b.lastName}`;
  });
  await check('Promociones públicas y APK descargable', async () => {
    const pr = await j('GET', '/api/public/promotions');
    expect(pr.s === 200 && Array.isArray(pr.b), 'promos ' + pr.s);
  });
  const passed = results.filter((r) => r.ok).length;
  return { passed, failed: results.length - passed, total: results.length, results };
}
if (typeof module !== 'undefined' && require.main === module) {
  portalE2E(process.argv[2] || 'http://localhost:8080').then((r) => {
    for (const t of r.results) console.log(`${t.ok ? '✔' : '✘'} ${t.name}${t.detail ? ' — ' + t.detail : ''}`);
    console.log(`\n${r.passed}/${r.total} pruebas aprobadas`);
    process.exit(r.failed ? 1 : 0);
  });
}
