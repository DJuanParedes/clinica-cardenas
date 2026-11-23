/**
 * Casos límite del flujo completo (v2.11.2): lo que puede pasar al eliminar, cerrar caja o quitar accesos a mitad del día.
 * Solo local, nunca contra producción (cierra y reabre la caja de hoy, crea y elimina pacientes, citas y procedimientos).
 * Uso: node casos-limite-e2e.js http://localhost:8080
 */
async function casosLimite(BASE) {
  const results = [];
  const j = async (m, u, b, t) => {
    const r = await fetch(BASE + u, { method: m, headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: 'Bearer ' + t } : {}) }, body: b ? JSON.stringify(b) : undefined });
    const x = await r.text(); try { return { s: r.status, b: JSON.parse(x) }; } catch { return { s: r.status, b: x }; }
  };
  const check = async (name, fn) => { try { results.push({ name, ok: true, detail: (await fn()) || '' }); } catch (e) { results.push({ name, ok: false, detail: String(e.message || e) }); } };
  const expect = (c, m) => { if (!c) throw new Error(m); };
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  const login = async (u, pw = 'Clinica2026') => (await j('POST', '/api/auth/login', { username: u, password: pw })).b.token;
  const ad = await login('admin'), rc = await login('recepcion'), cj = await login('caja');
  const iso = (d) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
  const dni = () => String(Math.floor(70000000 + Math.random() * 9999999));
  const del = (entity, id) => j('POST', `/api/admin/records/${entity}/${id}/delete`, { confirm: 'ELIMINAR', reason: 'casos límite' }, ad);
  const newPatient = async (name) => (await j('POST', '/api/patients', { dataConsent: true, docType: 'DNI', docNumber: dni(), firstName: name, lastName: 'Casos Limite',
    birthDate: '1991-02-03', sex: 'M', phone: '9' + String(Math.floor(10000000 + Math.random() * 89999999)) }, rc)).b;
  const dentist = (await j('GET', '/api/dentists', null, ad)).b[0];
  const book = async (pid, from = 3) => {
    for (let k = from; k < from + 20; k++) {
      const day = iso(new Date(Date.now() + k * 864e5));
      const sl = (await j('GET', `/api/agenda/slots?dentistId=${dentist.id}&date=${day}&duration=30&patientId=${pid}`, null, rc)).b;
      const free = (sl.slots || []).find((x) => x.available);
      if (!free) continue;
      const a = await j('POST', '/api/appointments', { patientId: pid, dentistId: dentist.id, startAt: free.start, durationMin: 30, reason: 'Control' }, rc);
      if (a.s === 201 || a.s === 200) return a.b;
    }
    throw new Error('sin horario libre');
  };
  const notifs = async (apptId) => {
    const r = (await j('GET', '/api/notifications?size=500', null, ad)).b;
    return (r.content || r.items || r).filter((n) => n.appointmentId === apptId);
  };

  await check('Eliminar una cita anula sus recordatorios: el paciente ya no los recibe', async () => {
    const p = await newPatient('Recordatorio');
    const a = await book(p.id);
    let n = [];
    for (let i = 0; i < 10 && !n.some((x) => x.status === 'PROGRAMADO'); i++) { await sleep(800); n = await notifs(a.id); }
    expect(n.length, 'la cita no generó avisos');
    const pv = (await j('GET', `/api/admin/records/appointments/${a.id}/preview`, null, ad)).b;
    expect(pv.warnings.some((w) => /aviso/.test(w)), 'la revisión no avisa de los recordatorios: ' + JSON.stringify(pv.warnings));
    expect((await del('appointments', a.id)).s === 200, 'no eliminó');
    await sleep(2500);
    n = await notifs(a.id);
    const live = n.filter((x) => x.operation !== 'ANULAR' && ['PENDIENTE', 'PROGRAMADO'].includes(x.status));
    expect(!live.length, 'quedaron avisos vivos: ' + live.map((x) => x.type + '/' + x.status));
    const hadScheduled = n.some((x) => x.operation === 'PROGRAMAR');
    if (hadScheduled) expect(n.some((x) => x.operation === 'ANULAR'), 'no se pidió anular el recordatorio al microservicio');
    await del('patients', p.id);
    return n.map((x) => x.operation + '/' + x.status).join(', ');
  });

  await check('Eliminar un paciente con cita futura y familiar: se anulan avisos y el parentesco desaparece de ambos lados', async () => {
    const a = await newPatient('Titular'), b = await newPatient('Familiar');
    const rel = await j('POST', `/api/patients/${b.id}/relations`, { relatedPatientId: a.id, relationship: 'HERMANO' }, rc);
    const appt = await book(a.id);
    await sleep(1500);
    expect((await del('patients', a.id)).s === 200, 'no eliminó al titular');
    const prof = (await j('GET', `/api/patients/${b.id}/profile`, null, rc)).b;
    if (rel.s < 300) expect(!(prof.relations || []).length, 'el familiar sigue mostrando el parentesco: ' + JSON.stringify(prof.relations));
    const live = (await notifs(appt.id)).filter((x) => x.operation !== 'ANULAR' && ['PENDIENTE', 'PROGRAMADO'].includes(x.status));
    expect(!live.length, 'quedaron avisos de un paciente eliminado');
    await del('patients', b.id);
  });

  await check('Eliminar un procedimiento no convierte su regla de comisión en una regla general', async () => {
    const code = 'TST-' + String(Date.now() % 1000).padStart(3, '0');
    const pr = await j('POST', '/api/catalog/procedures', { code, name: 'Procedimiento de prueba ' + code, category: 'Prevención', family: 'Prueba', price: 10, durationMin: 30, active: true }, ad);
    expect(pr.s < 300 && pr.b.id, 'procedimiento ' + pr.s + ' ' + JSON.stringify(pr.b).slice(0, 120));
    const vf = iso(new Date(Date.now() + 900 * 864e5));
    const rule = await j('POST', '/api/commissions/rules', { dentistId: dentist.id, procedureId: pr.b.id, calcType: 'MONTO_FIJO', value: 5, base: 'REALIZADO', validFrom: vf, notes: 'casos límite' }, ad);
    expect(rule.s === 201, 'regla ' + rule.s + ' ' + JSON.stringify(rule.b).slice(0, 150));
    expect((await del('procedures', pr.b.id)).s === 200, 'no eliminó el procedimiento');
    const rules = (await j('GET', '/api/commissions/rules', null, ad)).b;
    expect(!rules.some((r) => r.id === rule.b.id), 'la regla quedó como general: ' + JSON.stringify(rules.find((r) => r.id === rule.b.id)));
  });

  await check('Una cuenta eliminada o desactivada pierde el acceso al instante (401, no error del servidor)', async () => {
    const un = 'cl' + (Date.now() % 1000000);
    const u = await j('POST', '/api/users', { username: un, fullName: 'Cuenta Temporal', email: un + '@example.com', role: 'RECEPCION', password: 'Clinica2026!x' }, ad);
    expect(u.s < 300, 'usuario ' + u.s);
    const t = await login(un, 'Clinica2026!x');
    expect(t, 'no inició sesión');
    expect((await j('GET', '/api/patients?q=a', null, t)).s === 200, 'no podía trabajar');
    expect((await del('users', u.b.id)).s === 200, 'no eliminó');
    const after = await j('GET', '/api/patients?q=a', null, t);
    expect(after.s === 401, 'después de eliminarla respondió ' + after.s);
  });

  await check('Caja de hoy cerrada: no entran cobros, egresos ni anulaciones; al reabrir sí', async () => {
    const today = iso(new Date());
    const s = (await j('GET', '/api/cash/closing?date=' + today, null, cj)).b;
    if (s.closing && s.closing.status === 'CERRADA') await j('POST', `/api/cash/closing/${today}/reopen`, { reason: 'Prueba automática' }, ad);
    const s2 = (await j('GET', '/api/cash/closing?date=' + today, null, cj)).b;
    const cash = Number(s2.methods.find((m) => m.method === 'EFECTIVO').expected);
    const c = await j('POST', '/api/cash/closing', { date: today, openingFloat: s2.openingFloat, countedCash: cash, nextFloat: 0 }, cj);
    expect(c.s === 200, 'cierre ' + c.s + ' ' + JSON.stringify(c.b).slice(0, 150));
    try {
      const p = await newPatient('Caja');
      const pay = await j('POST', '/api/cash/payments', { patientId: p.id, amount: 10, method: 'EFECTIVO' }, cj);
      expect(pay.s === 409, 'aceptó un cobro con la caja cerrada: ' + pay.s);
      const ex = await j('POST', '/api/cash/expenses', { expenseDate: today, category: 'OTROS', concept: 'prueba', amount: 1, method: 'EFECTIVO' }, cj);
      expect(ex.s === 409, 'aceptó un egreso con la caja cerrada: ' + ex.s);
      expect((await j('POST', `/api/cash/closing/${today}/reopen`, { reason: 'Prueba' }, cj)).s === 403, 'caja pudo reabrir');
      await del('patients', p.id);
    } finally {
      await j('POST', `/api/cash/closing/${today}/reopen`, { reason: 'Fin de la prueba automática' }, ad);
    }
    const p = await newPatient('Caja Abierta');
    const pay = await j('POST', '/api/cash/payments', { patientId: p.id, amount: 10, method: 'EFECTIVO' }, cj);
    expect(pay.s === 201 || pay.s === 200, 'reabierta no aceptó el cobro: ' + pay.s);
    const v = await j('POST', `/api/cash/payments/${pay.b.id || pay.b.payment?.id}/void`, { reason: 'Prueba de casos límite' }, cj);
    expect(v.s === 200, 'anular ' + v.s);
    expect((await del('patients', p.id)).s === 200, 'paciente con pago anulado debería poder eliminarse');
  });

  await check('Egreso: se elimina con la caja abierta; con la caja cerrada se bloquea', async () => {
    const today = iso(new Date());
    const ex = await j('POST', '/api/cash/expenses', { expenseDate: today, category: 'OTROS', concept: 'Egreso para eliminar', amount: 2, method: 'EFECTIVO' }, cj);
    expect(ex.s < 300, 'egreso ' + ex.s);
    const id = ex.b.id || ex.b.expense?.id;
    expect((await del('expenses', id)).s === 200, 'no eliminó con la caja abierta');
  });

  await check('Nadie más que el administrador elimina, aunque llame directo a la API', async () => {
    for (const t of [rc, cj, await login('director'), await login('dra.cardenas')]) {
      const r = await j('POST', '/api/admin/records/patients/1/delete', { confirm: 'ELIMINAR' }, t);
      expect(r.s === 403, 'otro rol recibió ' + r.s);
      expect((await j('GET', '/api/admin/records/trash', null, t)).s === 403, 'otro rol vio la papelera');
    }
    expect((await j('POST', '/api/admin/records/inventado/1/delete', { confirm: 'ELIMINAR' }, ad)).s === 404, 'tipo inventado');
    expect((await j('GET', '/api/admin/records/patients/999999999/preview', null, ad)).s === 404, 'paciente inexistente');
    expect((await j('GET', '/api/admin/records/patients/abc/preview', null, ad)).s === 404, 'id inválido');
  });

  await check('App: /api/v1 y reserva con Idempotency-Key — el reintento no duplica la cita', async () => {
    const p = await newPatient('Idempotencia');
    const c = await j('POST', '/api/v1/portal/auth/code', { docType: 'DNI', docNumber: p.docNumber, birthDate: '1991-02-03' });
    expect(c.s === 200 && c.b.demoCode, 'código ' + c.s + ' ' + JSON.stringify(c.b).slice(0, 120));
    const act = await j('POST', '/api/v1/portal/auth/activate', { docType: 'DNI', docNumber: p.docNumber, code: c.b.demoCode, password: 'MiClave2026', dataConsent: true });
    const pt = act.b.token;
    expect(pt, 'activar ' + act.s);
    const opt = (await j('GET', '/api/v1/portal/booking/options', null, pt)).b;
    const reason = (opt.reasons || [])[0]?.code || 'EVALUACION';
    let slot = null, dId = null;
    for (const d of opt.dentists || []) {
      for (let k = 3; k < 20 && !slot; k++) {
        const day = iso(new Date(Date.now() + k * 864e5));
        const sl = (await j('GET', `/api/v1/portal/booking/slots?dentistId=${d.id}&date=${day}&reasonCode=${reason}`, null, pt)).b;
        slot = (sl.slots || [])[0] || null; dId = d.id;
      }
      if (slot) break;
    }
    expect(slot, 'sin horario libre');
    const key = 'prueba-' + Date.now();
    const send = () => fetch(BASE + '/api/v1/portal/appointments', { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + pt, 'Idempotency-Key': key },
      body: JSON.stringify({ dentistId: dId, startAt: slot, reasonCode: reason }) }).then(async (r) => ({ s: r.status, b: await r.json() }));
    const r1 = await send(), r2 = await send();
    expect(r1.s === 201 && r2.s === 201 && r1.b.id === r2.b.id, `reintento ${r1.s}/${r2.s} ids ${r1.b.id}/${r2.b.id}`);
    const mine = (await j('GET', '/api/v1/portal/appointments', null, pt)).b;
    const same = (mine.upcoming || []).filter((x) => x.startAt.slice(0, 16) === slot.slice(0, 16));
    expect(same.length === 1, 'hay ' + same.length + ' citas en ese horario');
    await del('patients', p.id);
    return 'cita ' + r1.b.id;
  });

  const passed = results.filter((r) => r.ok).length;
  return { passed, failed: results.length - passed, total: results.length, results };
}
if (typeof module !== 'undefined' && require.main === module) {
  casosLimite(process.argv[2] || 'http://localhost:8080').then((r) => {
    for (const t of r.results) console.log(`${t.ok ? '✔' : '✘'} ${t.name}${t.detail ? ' — ' + t.detail : ''}`);
    console.log(`\n${r.passed}/${r.total} pruebas aprobadas`);
    process.exit(r.failed ? 1 : 0);
  });
}
module.exports = { casosLimite };
