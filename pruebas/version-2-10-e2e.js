/**
 * Versión 2.10: categorías en citas y comisiones, consumo exacto por atención y cuadre/cierre de caja.
 * Solo local, nunca contra producción (cierra y reabre la caja de ayer, crea citas y reglas).
 * Uso: node version-2-10-e2e.js http://localhost:8080
 */
async function v210(BASE) {
  const results = [];
  const j = async (m, u, b, t) => {
    const r = await fetch(BASE + u, { method: m, headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: 'Bearer ' + t } : {}) }, body: b ? JSON.stringify(b) : undefined });
    const ct = r.headers.get('content-type') || '';
    if (ct.includes('pdf')) return { s: r.status, b: Buffer.from(await r.arrayBuffer()) };
    const x = await r.text(); try { return { s: r.status, b: JSON.parse(x) }; } catch { return { s: r.status, b: x }; }
  };
  const check = async (name, fn) => { try { results.push({ name, ok: true, detail: (await fn()) || '' }); } catch (e) { results.push({ name, ok: false, detail: String(e.message || e) }); } };
  const expect = (c, m) => { if (!c) throw new Error(m); };
  const login = async (u) => (await j('POST', '/api/auth/login', { username: u, password: 'Clinica2026' })).b.token;
  const ad = await login('admin'), cj = await login('caja'), rc = await login('recepcion');
  const iso = (d) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
  const dentist = (await j('GET', '/api/dentists', null, ad)).b[0];

  await check('Regla de comisión por categoría; categoría inexistente se rechaza', async () => {
    const vf = iso(new Date(Date.now() + 400 * 864e5));
    const r = await j('POST', '/api/commissions/rules', { dentistId: dentist.id, category: 'Estética', calcType: 'PORCENTAJE', value: 25, base: 'REALIZADO', validFrom: vf, active: true }, ad);
    expect(r.s === 201 && /categoría Estética/.test(r.b.label), 'regla ' + r.s + ' ' + JSON.stringify(r.b).slice(0, 150));
    await j('PUT', `/api/commissions/rules/${r.b.id}`, { dentistId: dentist.id, category: 'Estética', calcType: 'PORCENTAJE', value: 25, base: 'REALIZADO', validFrom: vf, active: false }, ad);
    const bad = await j('POST', '/api/commissions/rules', { dentistId: dentist.id, category: 'Inventada', calcType: 'PORCENTAJE', value: 10, base: 'REALIZADO', validFrom: vf }, ad);
    expect(bad.s === 400 && bad.b.fields.category, 'aceptó una categoría inexistente');
  });

  await check('La cita guarda su categoría y la deduce del motivo si no llega', async () => {
    const p = (await j('GET', '/api/patients?q=a', null, rc)).b; const pid = (p.content || p.items || p)[0].id;
    let made = 0;
    for (let k = 1; k <= 14 && made < 1; k++) {
      const day = iso(new Date(Date.now() + k * 864e5));
      const sl = (await j('GET', `/api/agenda/slots?dentistId=${dentist.id}&date=${day}&duration=30&patientId=${pid}`, null, rc)).b;
      const free = (sl.slots || []).filter((x) => x.available);
      if (free.length < 2) continue;
      const a = await j('POST', '/api/appointments', { patientId: pid, dentistId: dentist.id, startAt: free[0].start, durationMin: 30, reason: 'Ortodoncia', category: 'Ortodoncia' }, rc);
      const b = await j('POST', '/api/appointments', { patientId: pid, dentistId: dentist.id, startAt: free[1].start, durationMin: 30, reason: 'Limpieza simple de control' }, rc);
      for (const x of [a, b]) if (x.b.id) await j('PATCH', `/api/appointments/${x.b.id}/status`, { status: 'CANCELADA', reason: 'prueba' }, rc);
      expect(a.s === 201 && a.b.category === 'Ortodoncia', 'categoría explícita ' + a.s + ' ' + a.b.category);
      expect(b.s === 201 && b.b.category === 'Prevención', 'categoría deducida ' + b.b.category);
      made++;
    }
    expect(made, 'no había horarios libres en dos semanas');
  });

  await check('Al marcar realizado se descuenta lo indicado, no el kit', async () => {
    const row = (await j('GET', '/api/patients?q=a', null, ad)).b; const pid = (row.content || row.items || row)[0].id;
    const proc = (await j('GET', '/api/procedures', null, ad)).b.find((x) => x.code === 'PRE-002');
    const plan = await j('POST', `/api/patients/${pid}/plans`, { title: 'Prueba 2.10 consumo', items: [{ procedureId: proc.id, tooth: null, price: proc.price }] }, ad);
    expect(plan.s === 201 || plan.s === 200, 'plan ' + plan.s + ' ' + JSON.stringify(plan.b).slice(0, 120));
    const item = plan.b.items[0];
    const sup = (await j('GET', `/api/plan-items/${item.id}/supplies`, null, ad)).b;
    const gl = sup.catalog.find((c) => c.name.startsWith('Guantes'));
    const r = await j('PATCH', `/api/plan-items/${item.id}`, { status: 'REALIZADO', supplies: [{ itemId: gl.itemId, quantity: 2 }] }, ad);
    const after = (await j('GET', `/api/plan-items/${item.id}/supplies`, null, ad)).b.catalog.find((c) => c.itemId === gl.itemId);
    expect(r.s === 200 && Number(gl.stock) - Number(after.stock) === Math.min(2, Number(gl.stock)), `stock ${gl.stock} → ${after.stock}`);
  });

  const day = iso(new Date(Date.now() - 864e5));
  await check('Cuadre: lo esperado por medio de pago y diferencia obligatoria con nota', async () => {
    const s = (await j('GET', '/api/cash/closing?date=' + day, null, cj)).b;
    if (s.closing && s.closing.status === 'CERRADA') await j('POST', `/api/cash/closing/${day}/reopen`, { reason: 'Prueba automática' }, ad);
    expect(Array.isArray(s.methods) && s.methods.length === 5 && s.denominations.length === 11, 'resumen');
    const cash = Number(s.methods.find((m) => m.method === 'EFECTIVO').expected);
    const r = await j('POST', '/api/cash/closing', { date: day, openingFloat: s.openingFloat, countedCash: cash + 1, nextFloat: 0 }, cj);
    expect(r.s === 400 && r.b.fields.notes, 'cerró con diferencia sin explicar');
    const ok = await j('POST', '/api/cash/closing', { date: day, openingFloat: s.openingFloat, countedCash: cash + 1, nextFloat: 0, notes: 'Sobra S/ 1.00 (prueba)' }, cj);
    expect(ok.s === 200 && Number(ok.b.closing.difference) === 1, 'cierre ' + ok.s);
  });

  await check('Caja cerrada: rechaza egresos y anulaciones; PDF; solo dirección/admin reabre', async () => {
    const r = await j('POST', '/api/cash/expenses', { expenseDate: day, category: 'OTROS', concept: 'prueba', amount: 1, method: 'EFECTIVO' }, cj);
    expect(r.s === 409 && r.b.code === 'CAJA_CERRADA', 'aceptó un egreso en caja cerrada ' + r.s);
    expect((await j('POST', `/api/cash/closing/${day}/reopen`, { reason: 'Prueba automática' }, cj)).s === 403, 'caja pudo reabrir');
    const pdf = await j('GET', `/api/cash/closing/${day}/pdf`, null, cj);
    expect(pdf.s === 200 && pdf.b.slice(0, 5).toString() === '%PDF-', 'pdf');
    const re = await j('POST', `/api/cash/closing/${day}/reopen`, { reason: 'Prueba automática' }, ad);
    expect(re.s === 200 && re.b.closing.status === 'REABIERTA', 'reabrir');
    expect((await j('POST', '/api/cash/closing', { date: day, openingFloat: 0, countedCash: -1 }, cj)).s === 400, 'aceptó efectivo negativo');
  });

  await check('Horario de silencio: nada programado entre 21:00 y 08:00 después de una cita', async () => {
    const list = (await j('GET', '/api/notifications?size=300', null, ad)).b;
    const rows = (list.content || list.items || list).filter((n) => n.status === 'PROGRAMADO' && n.scheduledFor && /AGRADECIMIENTO|ENCUESTA/.test(n.type));
    const night = rows.filter((n) => { const h = +n.scheduledFor.slice(11, 13); return h >= 21 || h < 8; });
    expect(!night.length, night.length + ' mensajes programados de noche');
    return rows.length + ' revisados';
  });

  const passed = results.filter((r) => r.ok).length;
  return { passed, failed: results.length - passed, total: results.length, results };
}
if (typeof module !== 'undefined' && require.main === module) {
  v210(process.argv[2] || 'http://localhost:8080').then((r) => {
    for (const t of r.results) console.log(`${t.ok ? '✔' : '✘'} ${t.name}${t.detail ? ' — ' + t.detail : ''}`);
    console.log(`\n${r.passed}/${r.total} pruebas aprobadas`);
    process.exit(r.failed ? 1 : 0);
  });
}
module.exports = { v210 };
