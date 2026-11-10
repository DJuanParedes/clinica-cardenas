/*
 * Pruebas end-to-end de las historias de la versión completa · Clínica Dental Cárdenas
 * HU-13/14/15 caja · HU-16/17/18 comisiones · HU-19/20/21 indicadores · HU-22 app del paciente
 * HU-23/24 notificaciones · HU-26 roles y permisos
 *
 * Uso con Node 18+:   node pruebas/completo-e2e.js http://localhost:9081
 * También se puede pegar en la consola del navegador estando en http://localhost:9081
 * (las pruebas que apagan el microservicio de notificaciones solo corren desde Node).
 */
async function completoE2E(BASE, NOTIF = BASE.replace(/:(\d+)$/, (m, p) => ':' + (Number(p) + 2))) {
  const PWD = 'Clinica2026';
  const results = [];
  const uid = Date.now().toString().slice(-6);
  const tag = uid.split('').map((d) => 'abcdefghij'[Number(d)]).join(''); // los nombres solo admiten letras
  const inNode = typeof window === 'undefined';
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

  async function api(method, path, body, token) {
    const res = await fetch(BASE + path, {
      method,
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await res.text();
    let json = null;
    try { json = text ? JSON.parse(text) : null; } catch { json = text; }
    return { status: res.status, body: json };
  }
  async function check(hu, name, fn) {
    try { results.push({ hu, name, ok: true, detail: (await fn()) || '' }); }
    catch (e) { results.push({ hu, name, ok: false, detail: String(e.message || e) }); }
  }
  const expect = (c, m) => { if (!c) throw new Error(m); };
  const expectStatus = (r, s, ctx) => { if (r.status !== s) throw new Error(`${ctx}: esperaba ${s}, llegó ${r.status} ${JSON.stringify(r.body).slice(0, 200)}`); };
  const login = async (u, p = PWD) => { const r = await api('POST', '/api/auth/login', { username: u, password: p }); expectStatus(r, 200, 'login ' + u); return r.body.token; };
  const randDni = () => String(40000000 + Math.floor(Math.random() * 49999999)).slice(0, 8);
  const pad = (n) => String(n).padStart(2, '0');
  const iso = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  const today = iso(new Date());
  const daysAgo = (n) => { const d = new Date(); d.setDate(d.getDate() - n); return iso(d); };

  const admin = await login('admin'), recep = await login('recepcion'), odo = await login('dra.cardenas');
  const caja = await login('caja'), director = await login('director');
  const me = (await api('GET', '/api/auth/me', undefined, odo)).body;

  // ---------------------------------------------------------------- HU-26 roles y permisos
  await check('HU-26', 'Matriz de permisos: catálogo y administrador protegido', async () => {
    const r = await api('GET', '/api/permissions', undefined, admin);
    expectStatus(r, 200, 'matriz');
    expect(r.body.permissions.length >= 17, 'catálogo incompleto');
    const adm = r.body.roles.find((x) => x.code === 'ADMIN');
    expect(adm.locked && adm.permissions.length === r.body.permissions.length, 'admin sin todos los permisos');
    expectStatus(await api('PUT', '/api/permissions/ADMIN', { permissions: [] }, admin), 400, 'quitar permisos al admin');
    return r.body.permissions.length + ' permisos en ' + r.body.roles.length + ' roles';
  });
  await check('HU-26', 'Quitar un permiso aplica al instante en el servidor y se puede restaurar', async () => {
    const m = (await api('GET', '/api/permissions', undefined, admin)).body;
    const cajaPerms = m.roles.find((x) => x.code === 'CAJA').permissions;
    expectStatus(await api('GET', '/api/patients?q=quispe', undefined, caja), 200, 'antes');
    expectStatus(await api('PUT', '/api/permissions/CAJA', { permissions: cajaPerms.filter((p) => p !== 'PACIENTES_VER') }, admin), 200, 'quitar');
    try {
      expectStatus(await api('GET', '/api/patients?q=quispe', undefined, caja), 403, 'con el mismo token, sin permiso');
      const prof = (await api('GET', '/api/auth/me', undefined, caja)).body;
      expect(!prof.permissions.includes('PACIENTES_VER'), 'el perfil sigue mostrando el permiso');
    } finally {
      expectStatus(await api('PUT', '/api/permissions/CAJA', { permissions: cajaPerms }, admin), 200, 'restaurar');
    }
    expectStatus(await api('GET', '/api/patients?q=quispe', undefined, caja), 200, 'después de restaurar');
    const audit = await api('GET', '/api/audit', undefined, admin);
    expect(audit.body.some((a) => a.entity === 'ROL'), 'sin auditoría del cambio');
  });
  await check('HU-26', 'Accesos indebidos por permiso devuelven 403', async () => {
    expectStatus(await api('PUT', '/api/permissions/CAJA', { permissions: [] }, recep), 403, 'recepción → permisos');
    expectStatus(await api('GET', '/api/reports/income?from=' + today + '&to=' + today, undefined, recep), 403, 'recepción → indicadores');
    expectStatus(await api('GET', '/api/commissions/summary', undefined, caja), 403, 'caja → comisiones');
    expectStatus(await api('GET', '/api/cash/report?from=' + today + '&to=' + today, undefined, caja), 403, 'caja → flujo por período');
    expectStatus(await api('GET', '/api/notifications', undefined, caja), 403, 'caja → notificaciones');
    expectStatus(await api('POST', '/api/cash/payments', {}, odo), 403, 'odontóloga → cobrar');
    return '6 accesos bloqueados';
  });

  // ---------------------------------------------------------------- preparación: paciente con plan
  const dni = randDni();
  const birth = '1990-06-15';
  const patRes = await api('POST', '/api/patients', { docType: 'DNI', docNumber: dni, firstName: 'Prueba', lastName: 'Completa ' + tag,
    birthDate: birth, sex: 'F', phone: '987' + uid.slice(0, 6), email: 'prueba' + uid + '@correo.pe', district: 'Chaclacayo' }, recep);
  if (patRes.status !== 201) throw new Error('No se pudo crear el paciente de prueba: ' + JSON.stringify(patRes.body));
  const pat = patRes.body;
  // Avisos por WhatsApp y correo para este paciente (HU-23).
  await api('PATCH', `/api/patients/${pat.id}/notification-channel`, { channel: 'AMBOS' }, recep);
  const procs = (await api('GET', '/api/procedures', undefined, odo)).body;
  const pid = (c) => procs.find((p) => p.code === c).id;
  const plan = (await api('POST', `/api/patients/${pat.id}/plans`, { title: 'Plan E2E', items: [{ procedureId: pid('CON-001') }, { procedureId: pid('OPE-002'), tooth: 36 }] }, odo)).body;
  const [item1, item2] = plan.items;
  await api('PATCH', `/api/plan-items/${item1.id}`, { status: 'REALIZADO' }, odo);

  // ---------------------------------------------------------------- HU-13 / HU-14 caja
  await check('HU-14', 'Saldo pendiente: cargos de lo realizado y obligaciones', async () => {
    const a = (await api('GET', `/api/cash/patients/${pat.id}/account`, undefined, caja)).body;
    expect(a.charges == 50 && a.balance == 50 && a.credits == 0, 'saldo ' + JSON.stringify([a.charges, a.credits, a.balance]));
    expect(a.obligations.length === 2 && a.obligations.find((o) => o.treatmentItemId === item1.id).pending == 50, 'obligaciones');
    return 'saldo S/ ' + a.balance;
  });
  await check('HU-13', 'Validaciones del cobro: operación obligatoria y tope del pendiente', async () => {
    const noRef = await api('POST', '/api/cash/payments', { patientId: pat.id, treatmentItemId: item1.id, amount: 20, method: 'YAPE' }, caja);
    expect(noRef.status === 400 && noRef.body.fields.reference, 'Yape sin número de operación');
    const over = await api('POST', '/api/cash/payments', { patientId: pat.id, treatmentItemId: item1.id, amount: 80, method: 'EFECTIVO' }, caja);
    expectStatus(over, 409, 'importe mayor al pendiente');
    expectStatus(await api('POST', '/api/cash/payments', { patientId: pat.id, amount: 0, method: 'EFECTIVO' }, caja), 400, 'importe cero');
    return over.body.message;
  });
  let pay1;
  await check('HU-13', 'Cobro parcial con medio de pago, fecha y responsable', async () => {
    const r = await api('POST', '/api/cash/payments', { patientId: pat.id, treatmentItemId: item1.id, amount: 20, method: 'YAPE', reference: '778899' }, caja);
    expectStatus(r, 201, 'cobro');
    pay1 = r.body.payment;
    expect(pay1.cashier === 'caja' && pay1.paidAt && pay1.method === 'YAPE', 'sin trazabilidad');
    expect(r.body.account.balance == 30, 'saldo ' + r.body.account.balance);
    return 'S/ 20 por Yape · saldo S/ 30';
  });
  await check('HU-13', 'Cobros simultáneos del mismo saldo: solo uno se acepta', async () => {
    const body = { patientId: pat.id, treatmentItemId: item1.id, amount: 30, method: 'EFECTIVO' };
    const rs = await Promise.all([1, 2, 3].map(() => api('POST', '/api/cash/payments', body, caja)));
    const codes = rs.map((r) => r.status).sort();
    expect(codes.filter((c) => c === 201).length === 1, 'resultado ' + codes.join('/'));
    return codes.join(' / ');
  });
  await check('HU-14', 'Pago a cuenta y saldo a favor', async () => {
    expectStatus(await api('POST', '/api/cash/payments', { patientId: pat.id, amount: 100, method: 'TARJETA', reference: 'V-12345' }, caja), 201, 'a cuenta');
    const a = (await api('GET', `/api/cash/patients/${pat.id}/account`, undefined, caja)).body;
    expect(a.balance == 0 && a.inFavor == 100 && a.unapplied == 100, JSON.stringify([a.balance, a.inFavor, a.unapplied]));
    expect(a.movements.length >= 4 && a.movements[0].balance !== undefined, 'movimientos');
    return 'a favor S/ ' + a.inFavor;
  });
  await check('HU-13', 'Un procedimiento cobrado no puede volver a pendiente', async () => {
    const r = await api('PATCH', `/api/plan-items/${item1.id}`, { status: 'PENDIENTE' }, odo);
    expect(r.status === 409 && r.body.code === 'ITEM_COBRADO', 'permitió revertir: ' + r.status);
  });
  await check('HU-13', 'Anular un cobro exige motivo y actualiza el saldo', async () => {
    expectStatus(await api('POST', `/api/cash/payments/${pay1.id}/void`, { reason: '' }, caja), 400, 'sin motivo');
    expectStatus(await api('POST', `/api/cash/payments/${pay1.id}/void`, { reason: 'Registrado por error' }, caja), 200, 'anular');
    expectStatus(await api('POST', `/api/cash/payments/${pay1.id}/void`, { reason: 'Otra vez' }, caja), 409, 'anular dos veces');
    const a = (await api('GET', `/api/cash/patients/${pat.id}/account`, undefined, caja)).body;
    expect(a.credits == 130 && a.inFavor == 80, JSON.stringify([a.credits, a.inFavor]));
  });

  // ---------------------------------------------------------------- HU-15 egresos y flujo
  let exp;
  await check('HU-15', 'Registrar egreso con concepto y validaciones', async () => {
    const bad = await api('POST', '/api/cash/expenses', { expenseDate: '2999-01-01', category: 'XX', concept: 'a', amount: -1, method: 'EFECTIVO' }, caja);
    expect(bad.status === 400 && bad.body.fields.expenseDate && bad.body.fields.category && bad.body.fields.concept && bad.body.fields.amount, 'validaciones');
    const r = await api('POST', '/api/cash/expenses', { expenseDate: today, category: 'INSUMOS', concept: 'Guantes y mascarillas E2E', amount: 45.5, method: 'EFECTIVO', supplier: 'Distribuidora' }, caja);
    expectStatus(r, 201, 'egreso'); exp = r.body;
  });
  await check('HU-15', 'Flujo de caja por período: ingresos, egresos, medios y categorías', async () => {
    const t = (await api('GET', '/api/cash/today', undefined, caja)).body;
    expect(t.detail.some((d) => d.type === 'EGRESO' && d.id === exp.id), 'el egreso no aparece hoy');
    const r = await api('GET', `/api/cash/report?from=${daysAgo(60)}&to=${today}`, undefined, director);
    expectStatus(r, 200, 'reporte');
    const b = r.body;
    expect(b.income > 0 && b.outgo > 0 && Math.abs(b.income - b.outgo - b.net) < 0.01, 'totales');
    expect(b.byDay.length === 61 && b.byMethod.length > 0 && b.byCategory.some((c) => c.category === 'ALQUILER'), 'agrupaciones');
    expectStatus(await api('POST', `/api/cash/expenses/${exp.id}/void`, { reason: 'Prueba de anulación' }, caja), 200, 'anular egreso');
    const t2 = (await api('GET', '/api/cash/today', undefined, caja)).body;
    expect(!t2.detail.some((d) => d.type === 'EGRESO' && d.id === exp.id), 'el egreso anulado sigue sumando');
    expectStatus(await api('GET', `/api/cash/report?from=${today}&to=${daysAgo(3)}`, undefined, director), 400, 'período invertido');
    return `60 días: ingresos ${b.income} · egresos ${b.outgo}`;
  });

  // ---------------------------------------------------------------- HU-16 / 17 / 18 comisiones
  const dra = me.dentistId;
  await check('HU-16', 'Reglas: validación, prioridad por procedimiento y sin superposición', async () => {
    const bad = await api('POST', '/api/commissions/rules', { dentistId: dra, calcType: 'PORCENTAJE', value: 150, base: 'REALIZADO', validFrom: today }, admin);
    expect(bad.status === 400 && bad.body.fields.value, 'porcentaje > 100');
    const over = await api('POST', '/api/commissions/rules', { dentistId: dra, calcType: 'PORCENTAJE', value: 30, base: 'REALIZADO', validFrom: today }, admin);
    expect(over.status === 409 && over.body.code === 'REGLA_SUPERPUESTA', 'regla general superpuesta: ' + over.status);
    const ok = await api('POST', '/api/commissions/rules', { dentistId: dra, procedureId: pid('OPE-002'), calcType: 'MONTO_FIJO', value: 70, base: 'REALIZADO',
      validFrom: daysAgo(1), notes: 'E2E' }, admin);
    expectStatus(ok, 201, 'regla por procedimiento');
    return ok.body.label;
  });
  let calc;
  await check('HU-17', 'Cálculo por odontólogo y período con la regla vigente', async () => {
    await api('PATCH', `/api/plan-items/${item2.id}`, { status: 'REALIZADO' }, odo);
    const r = await api('GET', `/api/commissions/calculate?dentistId=${dra}&from=${today}&to=${today}`, undefined, admin);
    expectStatus(r, 200, 'calcular'); calc = r.body;
    const l1 = calc.lines.find((l) => l.treatmentItemId === item1.id), l2 = calc.lines.find((l) => l.treatmentItemId === item2.id);
    expect(l1 && l1.commission == 20, 'general 40 % de S/ 50: ' + (l1 && l1.commission));
    expect(l2 && l2.commission == 70, 'monto fijo de la regla del procedimiento: ' + (l2 && l2.commission));
    return `${calc.lines.length} procedimientos · S/ ${calc.total}`;
  });
  let set;
  await check('HU-18', 'Liquidar lo seleccionado: cada procedimiento se liquida una sola vez', async () => {
    const r = await api('POST', '/api/commissions/settlements', { dentistId: dra, from: today, to: today, itemIds: [item1.id, item2.id] }, admin);
    expectStatus(r, 201, 'liquidar'); set = r.body.settlement;
    expect(set.totalAmount == 90 && set.status === 'PENDIENTE', 'total ' + set.totalAmount);
    const again = await api('POST', '/api/commissions/settlements', { dentistId: dra, from: today, to: today, itemIds: [item1.id] }, admin);
    expect(again.status === 409 || again.status === 400, 'liquidó dos veces: ' + again.status);
    const c2 = (await api('GET', `/api/commissions/calculate?dentistId=${dra}&from=${today}&to=${today}`, undefined, admin)).body;
    expect(c2.alreadySettled.some((l) => l.treatmentItemId === item1.id), 'no figura como liquidado');
    expectStatus(await api('PATCH', `/api/plan-items/${item2.id}`, { status: 'PENDIENTE' }, odo), 409, 'revertir procedimiento liquidado');
  });
  await check('HU-18', 'Pagos de la liquidación: parcial, total y bloqueo de anulación', async () => {
    expect((await api('POST', `/api/commissions/settlements/${set.id}/payouts`, { amount: 500, method: 'EFECTIVO' }, admin)).status === 400, 'pago mayor al pendiente');
    let r = await api('POST', `/api/commissions/settlements/${set.id}/payouts`, { amount: 40, method: 'TRANSFERENCIA', reference: 'OP-1' }, admin);
    expect(r.body.settlement.status === 'PARCIAL' && r.body.settlement.pendingAmount == 50, 'parcial');
    r = await api('POST', `/api/commissions/settlements/${set.id}/payouts`, { amount: 50, method: 'EFECTIVO' }, admin);
    expect(r.body.settlement.status === 'PAGADA' && r.body.payouts.length === 2, 'pagada');
    expectStatus(await api('POST', `/api/commissions/settlements/${set.id}/annul`, { reason: 'Prueba' }, admin), 409, 'anular pagada');
    const s = (await api('GET', '/api/commissions/summary', undefined, admin)).body;
    expect(s.length >= 2 && s.every((x) => x.pending !== undefined), 'resumen');
    return 'S/ 40 + S/ 50 → pagada';
  });

  // ---------------------------------------------------------------- HU-19 / 20 / 21 indicadores
  await check('HU-19', 'Ingresos por período con comparación y desgloses', async () => {
    const r = await api('GET', `/api/reports/income?from=${daysAgo(89)}&to=${today}`, undefined, director);
    expectStatus(r, 200, 'ingresos');
    const b = r.body;
    expect(b.total > 0 && b.series.length > 0 && b.grouping === 'week', 'serie ' + b.grouping);
    const sum = b.series.reduce((s, x) => s + Number(x.amount), 0);
    expect(Math.abs(sum - b.total) < 0.05, 'la serie no suma el total');
    expect(b.byMethod.length > 0 && b.byDentist.length > 0 && b.byCategory.length > 0, 'desgloses');
    return `S/ ${b.total} · ${b.count} cobros · ticket S/ ${b.averageTicket}`;
  });
  await check('HU-20', 'Citas programadas, atendidas y canceladas por odontólogo', async () => {
    const r = (await api('GET', `/api/reports/appointments?from=${daysAgo(60)}&to=${today}`, undefined, director)).body;
    expect(r.total === r.attended + r.cancelled + r.noShow + r.scheduled, 'conteos no cuadran');
    expect(r.attendanceRate > 0 && r.attendanceRate <= 100 && r.byDentist.length >= 2, 'indicadores');
    const f = (await api('GET', `/api/reports/appointments?from=${daysAgo(60)}&to=${today}&dentistId=${dra}`, undefined, director)).body;
    expect(f.byDentist.length === 1 && f.total < r.total, 'filtro por odontólogo');
    return `${r.total} citas · asistencia ${r.attendanceRate} % · cancelación ${r.cancellationRate} %`;
  });
  await check('HU-21', 'Tratamientos realizados y pendientes', async () => {
    const r = (await api('GET', `/api/reports/treatments?from=${daysAgo(60)}&to=${today}`, undefined, director)).body;
    expect(r.doneCount > 0 && r.pendingCount > 0 && r.byProcedure.length > 0, 'datos');
    expect(r.byProcedure.reduce((s, p) => s + p.done, 0) === r.doneCount, 'el detalle no suma');
    return `${r.doneCount} realizados · ${r.pendingCount} pendientes · avance ${r.completionRate} %`;
  });

  // ---------------------------------------------------------------- HU-22 app del paciente
  let pacToken;
  await check('HU-22', 'Activar la cuenta con código y fecha de nacimiento', async () => {
    const wrong = await api('POST', '/api/portal/auth/code', { docType: 'DNI', docNumber: dni, birthDate: '1980-01-01' });
    expect(wrong.status === 200 && !wrong.body.demoCode, 'fecha incorrecta revela datos');
    const c = await api('POST', '/api/portal/auth/code', { docType: 'DNI', docNumber: dni, birthDate: birth });
    expect(c.status === 200 && /^\d{6}$/.test(c.body.demoCode || ''), 'sin código');
    expectStatus(await api('POST', '/api/portal/auth/activate', { docType: 'DNI', docNumber: dni, code: '000000', password: 'MiClave2026' }), 400, 'código incorrecto');
    const weak = await api('POST', '/api/portal/auth/activate', { docType: 'DNI', docNumber: dni, code: c.body.demoCode, password: 'corta' });
    expectStatus(weak, 400, 'contraseña débil');
    const ok = await api('POST', '/api/portal/auth/activate', { docType: 'DNI', docNumber: dni, code: c.body.demoCode, password: 'MiClave2026' });
    expectStatus(ok, 200, 'activar'); pacToken = ok.body.token;
    expectStatus(await api('POST', '/api/portal/auth/login', { docType: 'DNI', docNumber: dni, password: 'mala' }), 401, 'contraseña incorrecta');
    expectStatus(await api('POST', '/api/portal/auth/login', { docType: 'DNI', docNumber: dni, password: 'MiClave2026' }), 200, 'login');
    return 'código enviado a ' + c.body.sentTo;
  });
  let appt;
  await check('HU-22', 'Consulta de próximas citas con fecha, hora y odontólogo, y confirmación', async () => {
    let day = new Date(); day.setDate(day.getDate() + 3);
    while (day.getDay() === 0) day.setDate(day.getDate() + 1);
    const slots = (await api('GET', `/api/agenda/slots?dentistId=${dra}&date=${iso(day)}&duration=30`, undefined, recep)).body.slots.filter((s) => s.available);
    appt = (await api('POST', '/api/appointments', { patientId: pat.id, dentistId: dra, startAt: slots[2].start, durationMin: 30, reason: 'Control E2E' }, recep)).body;
    const r = await api('GET', '/api/portal/appointments', undefined, pacToken);
    expectStatus(r, 200, 'citas');
    const n = r.body.upcoming.find((a) => a.id === appt.id);
    expect(n && n.dentist.fullName && n.startAt && n.canConfirm, 'la cita no aparece');
    const cf = await api('POST', `/api/portal/appointments/${appt.id}/confirm`, {}, pacToken);
    expect(cf.body.upcoming.find((a) => a.id === appt.id).status === 'CONFIRMADA', 'no se confirmó');
    return 'próxima: ' + n.startAt.replace('T', ' ') + ' con ' + n.dentist.fullName;
  });
  await check('HU-22', 'El paciente solo accede a su información (SP-03)', async () => {
    expectStatus(await api('GET', '/api/patients?q=a', undefined, pacToken), 403, 'paciente → API del personal');
    expectStatus(await api('GET', '/api/portal/me', undefined, recep), 403, 'personal → API del paciente');
    const other = (await api('GET', '/api/agenda/day?date=' + today, undefined, recep)).body.appointments.find((a) => a.patient.id !== pat.id);
    if (other) expectStatus(await api('POST', `/api/portal/appointments/${other.id}/confirm`, {}, pacToken), 404, 'cita ajena');
    const acc = await api('GET', '/api/portal/account', undefined, pacToken);
    expect(acc.status === 200 && acc.body.inFavor !== undefined, 'cuenta');
  });

  // ---------------------------------------------------------------- HU-23 / HU-24 notificaciones
  const notifs = async () => (await api('GET', `/api/patients/${pat.id}/notifications`, undefined, recep)).body;
  const waitFor = async (pred, ms = 8000) => { const t = Date.now(); let n; while (Date.now() - t < ms) { n = await notifs(); if (pred(n)) return n; await sleep(500); } return n; };
  await check('HU-23', 'Recordatorio programado 24 h antes en el microservicio', async () => {
    const n = await waitFor((l) => l.some((x) => x.type === 'RECORDATORIO' && x.appointmentId === appt.id && x.status === 'PROGRAMADO'));
    const r = n.find((x) => x.type === 'RECORDATORIO' && x.appointmentId === appt.id && x.operation === 'PROGRAMAR');
    expect(r && r.status === 'PROGRAMADO', 'estado ' + (r && r.status));
    const start = new Date(appt.startAt), at = new Date(r.scheduledFor);
    expect(Math.abs((start - at) / 3600000 - 24) < 0.01, 'no es 24 h antes');
    return 'programado para ' + r.scheduledFor.replace('T', ' ');
  });
  await check('HU-23', 'Recordatorio manual sin envíos duplicados', async () => {
    const a = (await api('POST', `/api/appointments/${appt.id}/remind`, {}, recep)).body;
    const b = (await api('POST', `/api/appointments/${appt.id}/remind`, {}, recep)).body;
    expect(a.queued === 2 && b.queued === 0 && b.duplicated === 2, JSON.stringify([a, b]));
    const manual = (l) => l.filter((x) => x.idempotencyKey.startsWith('recordatorio-manual') && x.status === 'ENVIADO');
    const n = await waitFor((l) => manual(l).length === 2);
    expect(manual(n).length === 2, 'no se envió por los dos canales');
    return b.message;
  });
  await check('HU-24', 'Aviso al reprogramar: mensaje enviado, recordatorio anterior anulado y uno nuevo programado', async () => {
    const d = new Date(appt.startAt); d.setDate(d.getDate() + 1);
    while (d.getDay() === 0) d.setDate(d.getDate() + 1);
    const slots = (await api('GET', `/api/agenda/slots?dentistId=${dra}&date=${iso(d)}&duration=30`, undefined, recep)).body.slots.filter((s) => s.available);
    expectStatus(await api('PUT', `/api/appointments/${appt.id}`, { dentistId: dra, startAt: slots[1].start, durationMin: 30, changeReason: 'Pidió otro día' }, recep), 200, 'reprogramar');
    const n = await waitFor((l) => l.filter((x) => x.type === 'CAMBIO_CITA' && x.status === 'ENVIADO').length === 2
      && l.filter((x) => x.type === 'RECORDATORIO' && x.operation === 'PROGRAMAR' && x.status === 'PROGRAMADO').length >= 1);
    const change = n.filter((x) => x.type === 'CAMBIO_CITA');
    expect(change.length === 2 && change.every((x) => x.status === 'ENVIADO'), 'aviso de cambio (WhatsApp y correo): ' + change.map((x) => x.status));
    const progs = n.filter((x) => x.type === 'RECORDATORIO' && x.operation === 'PROGRAMAR');
    expect(progs.some((x) => x.status === 'ANULADO') && progs.some((x) => x.status === 'PROGRAMADO'), 'recordatorios: ' + progs.map((x) => x.status));
    expect(n.some((x) => x.operation === 'ANULAR' && x.status === 'ANULADO'), 'no se anuló en el microservicio');
    expect(change[0].body.includes('cambió'), 'texto del aviso');
  });
  await check('HU-24', 'Aviso al cancelar la cita', async () => {
    expectStatus(await api('PATCH', `/api/appointments/${appt.id}/status`, { status: 'CANCELADA', reason: 'Viaje' }, recep), 200, 'cancelar');
    const n = await waitFor((l) => l.filter((x) => x.type === 'CANCELACION' && x.status === 'ENVIADO').length === 2);
    expect(n.filter((x) => x.type === 'CANCELACION' && x.status === 'ENVIADO').length === 2, 'cancelación no enviada');
    expect(!n.some((x) => x.type === 'RECORDATORIO' && x.operation === 'PROGRAMAR' && x.status === 'PROGRAMADO'), 'quedó un recordatorio activo');
  });
  await check('HU-23', 'Preferencia de canal: sin correo no se puede elegir correo', async () => {
    const p2 = (await api('POST', '/api/patients', { docType: 'DNI', docNumber: randDni(), firstName: 'Sin', lastName: 'Correo ' + tag, birthDate: '1985-01-01', sex: 'M', phone: '912345678' }, recep)).body;
    expectStatus(await api('PATCH', `/api/patients/${p2.id}/notification-channel`, { channel: 'EMAIL' }, recep), 400, 'correo sin email');
    const r = await api('PATCH', `/api/patients/${p2.id}/notification-channel`, { channel: 'NINGUNO' }, recep);
    expect(r.status === 200 && r.body.notifyChannel === 'NINGUNO', 'canal');
  });
  if (inNode) {
    await check('HU-23', 'Microservicio caído: el aviso queda en cola y se reintenta solo', async () => {
      await fetch(NOTIF + '/admin/modo', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{"modo":"caido"}' });
      let a2;
      try {
        const d = new Date(); d.setDate(d.getDate() + 5); while (d.getDay() === 0) d.setDate(d.getDate() + 1);
        const slots = (await api('GET', `/api/agenda/slots?dentistId=${dra}&date=${iso(d)}&duration=30`, undefined, recep)).body.slots.filter((s) => s.available);
        a2 = (await api('POST', '/api/appointments', { patientId: pat.id, dentistId: dra, startAt: slots[0].start, durationMin: 30, reason: 'Prueba caída' }, recep)).body;
        await sleep(2500);
        const n = (await notifs()).filter((x) => x.appointmentId === a2.id);
        expect(n.length && n.every((x) => x.status === 'PENDIENTE' && x.attempts >= 1), 'estado con el servicio caído: ' + n.map((x) => x.status + '/' + x.attempts));
      } finally {
        await fetch(NOTIF + '/admin/modo', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{"modo":"normal"}' });
      }
      const n = await waitFor((l) => l.filter((x) => x.appointmentId === a2.id).every((x) => x.status === 'PROGRAMADO'), 70000);
      expect(n.filter((x) => x.appointmentId === a2.id).every((x) => x.status === 'PROGRAMADO'), 'no se reintentó');
      return 'PENDIENTE → PROGRAMADO tras el reintento';
    });
  }

  const passed = results.filter((r) => r.ok).length;
  return { passed, failed: results.length - passed, total: results.length, results };
}

if (typeof module !== 'undefined' && require.main === module) {
  const base = process.argv[2] || 'http://localhost:9081';
  completoE2E(base, process.argv[3]).then((r) => {
    for (const t of r.results) console.log(`${t.ok ? '✔' : '✘'} [${t.hu}] ${t.name}${t.detail ? ' — ' + t.detail : ''}`);
    console.log(`\n${r.passed}/${r.total} pruebas aprobadas`);
    process.exit(r.failed ? 1 : 0);
  });
}
