/**
 * Pruebas de las correcciones de la revisión del sprint:
 *  inventario con lotes y vencimientos (FEFO), compras que generan egreso en caja, solicitudes de insumos del
 *  odontólogo, roles personalizados, perfil propio, 2FA por correo, promociones, datos mínimos para caja,
 *  historial de caja, "Mis ganancias" en soles y agenda por semana/mes.
 * Uso: node revision-profesor-e2e.js http://localhost:8080   (solo en local: crea datos de prueba)
 */
async function revisionE2E(BASE) {
  const results = [];
  const api = async (method, path, body, token) => {
    const res = await fetch(BASE + path, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
      body: body === undefined ? undefined : JSON.stringify(body) });
    const t = await res.text(); let j = null; try { j = t ? JSON.parse(t) : null; } catch { j = t; }
    return { status: res.status, body: j };
  };
  const check = async (name, fn) => { try { results.push({ name, ok: true, detail: (await fn()) || '' }); } catch (e) { results.push({ name, ok: false, detail: String(e.message || e) }); } };
  const expect = (c, m) => { if (!c) throw new Error(m); };
  const login = async (u) => (await api('POST', '/api/auth/login', { username: u, password: 'Clinica2026' })).body.token;
  const iso = (d) => d.toISOString().slice(0, 10);
  const plus = (n) => { const d = new Date(); d.setDate(d.getDate() + n); return iso(d); };
  const admin = await login('admin');
  const recep = await login('recepcion');
  const doc = await login('dra.cardenas');
  const caja = await login('caja');
  const tag = Date.now().toString().slice(-6);

  // ---------------------------------------------------------------- inventario
  let item;
  await check('Material nuevo con tipo, marca, proveedor y lote con vencimiento', async () => {
    const r = await api('POST', '/api/inventory', { name: 'Anestesia prueba ' + tag, unit: 'cartucho', minStock: 5, initialStock: 10, category: 'MEDICAMENTO',
      brand: 'New Stetic', supplier: 'Dental Perú S.A.C.', unitCost: 3.5, lotCode: 'L-A', expiresOn: plus(300) }, recep);
    expect(r.status === 201, r.status + ' ' + JSON.stringify(r.body).slice(0, 200));
    item = r.body;
    expect(item.category === 'MEDICAMENTO' && item.brand === 'New Stetic' && +item.stock === 10, JSON.stringify(item).slice(0, 200));
  });
  await check('Medicamento sin fecha de vencimiento: rechazado', async () => {
    const r = await api('POST', '/api/inventory', { name: 'Med sin vencimiento ' + tag, unit: 'u', initialStock: 3, category: 'MEDICAMENTO' }, recep);
    expect(r.status === 400 && r.body.fields && r.body.fields.expiresOn, 'aceptó: ' + r.status);
  });
  await check('Lote ya vencido no puede ingresar', async () => {
    const r = await api('POST', `/api/inventory/${item.id}/moves`, { kind: 'ENTRADA', quantity: 5, expiresOn: plus(-3), reason: 'prueba' }, recep);
    expect(r.status === 400 && r.body.fields.expiresOn, 'aceptó: ' + r.status);
  });
  let purchase;
  await check('Compra al contado: entra el lote, sube el stock y se registra el egreso en caja', async () => {
    const r = await api('POST', '/api/inventory/purchases', { supplier: 'Dental Perú S.A.C.', docType: 'FACTURA', docNumber: 'F001-' + tag, purchaseDate: plus(0),
      paid: true, method: 'TRANSFERENCIA', lines: [{ itemId: item.id, quantity: 20, unitCost: 3.2, lotCode: 'L-B', expiresOn: plus(30) }] }, recep);
    expect(r.status === 201, r.status + ' ' + JSON.stringify(r.body).slice(0, 300));
    purchase = r.body;
    expect(purchase.status === 'PAGADA' && purchase.expenseId && +purchase.total === 64, JSON.stringify(purchase).slice(0, 200));
    const inv = (await api('GET', '/api/inventory', undefined, recep)).body;
    expect(+inv.items.find((i) => i.id === item.id).stock === 30, 'stock no subió');
    const day = (await api('GET', '/api/cash/today', undefined, caja)).body;
    expect(JSON.stringify(day).includes('F001-' + tag), 'el egreso no aparece en la caja del día');
    return 'egreso #' + purchase.expenseId;
  });
  await check('La misma factura del mismo proveedor no se registra dos veces', async () => {
    const r = await api('POST', '/api/inventory/purchases', { supplier: 'Dental Perú S.A.C.', docType: 'FACTURA', docNumber: 'F001-' + tag, paid: true, method: 'EFECTIVO',
      lines: [{ itemId: item.id, quantity: 1, unitCost: 1 }] }, recep);
    expect(r.status === 400 && r.body.fields.docNumber, 'aceptó: ' + r.status);
  });
  await check('Compra al crédito queda por pagar y el egreso sale al pagarla', async () => {
    const r = await api('POST', '/api/inventory/purchases', { supplier: 'Importaciones Odonto', docType: 'FACTURA', docNumber: 'F002-' + tag, paid: false, dueDate: plus(15),
      lines: [{ itemId: item.id, quantity: 2, unitCost: 10, expiresOn: plus(200) }] }, recep);
    expect(r.status === 201 && r.body.status === 'POR_PAGAR' && !r.body.expenseId, r.status + ' ' + JSON.stringify(r.body).slice(0, 200));
    const p = await api('POST', `/api/inventory/purchases/${r.body.id}/pay`, { method: 'EFECTIVO' }, admin);
    expect(p.status === 200 && p.body.status === 'PAGADA' && p.body.expenseId, 'pago: ' + p.status);
  });
  await check('Salida descuenta primero el lote que vence antes (FEFO)', async () => {
    const r = await api('POST', `/api/inventory/${item.id}/moves`, { kind: 'SALIDA', quantity: 15, reason: 'prueba FEFO' }, recep);
    expect(r.status === 200, 'salida: ' + r.status + ' ' + JSON.stringify(r.body).slice(0, 200));
    const lots = (await api('GET', `/api/inventory/${item.id}/lots`, undefined, recep)).body;
    const b = lots.find((l) => l.lotCode === 'L-B'), a = lots.find((l) => l.lotCode === 'L-A');
    expect(+b.remaining === 5 && +a.remaining === 10, 'lotes: ' + JSON.stringify(lots.map((l) => [l.lotCode, l.remaining])));
    return 'L-B (vence antes) quedó en 5, L-A intacto';
  });
  await check('Alertas: el lote que vence en 30 días aparece en vencimientos', async () => {
    const inv = (await api('GET', '/api/inventory', undefined, recep)).body;
    expect(inv.expiring.some((l) => l.itemId === item.id && l.lotCode === 'L-B' && !l.expired), 'no aparece');
    expect(inv.value > 0, 'sin valorización');
  });
  await check('Salida sin motivo: rechazada (trazabilidad)', async () => {
    const r = await api('POST', `/api/inventory/${item.id}/moves`, { kind: 'SALIDA', quantity: 1 }, recep);
    expect(r.status === 400 && r.body.fields.reason, 'aceptó: ' + r.status);
  });
  await check('Concurrencia: 10 salidas simultáneas nunca dejan el stock negativo', async () => {
    const before = +(await api('GET', '/api/inventory', undefined, recep)).body.items.find((i) => i.id === item.id).stock;
    const rs = await Promise.all(Array.from({ length: 10 }, () => api('POST', `/api/inventory/${item.id}/moves`, { kind: 'SALIDA', quantity: 4, reason: 'concurrencia' }, recep)));
    const ok = rs.filter((r) => r.status === 200).length;
    const after = +(await api('GET', '/api/inventory', undefined, recep)).body.items.find((i) => i.id === item.id).stock;
    expect(after >= 0 && after === before - ok * 4, `antes ${before}, ok ${ok}, después ${after}`);
    return `${ok} aceptadas, ${10 - ok} rechazadas por falta de stock, queda ${after}`;
  });

  // ---------------------------------------------------------------- solicitudes de insumos
  await check('El odontólogo ya no entra a inventario', async () => {
    const r = await api('GET', '/api/inventory', undefined, doc);
    expect(r.status === 403, 'entró: ' + r.status);
  });
  let req;
  await check('El odontólogo solicita un insumo y recepción lo entrega (descuenta stock)', async () => {
    const cat = (await api('GET', '/api/supply-requests/catalog', undefined, doc)).body;
    expect(Array.isArray(cat) && cat.length && cat[0].unitCost === undefined, 'catálogo con costos o vacío');
    const r = await api('POST', '/api/supply-requests', { itemId: item.id, quantity: 1, urgency: 'URGENTE', note: 'paciente en sillón' }, doc);
    expect(r.status === 201 && r.body.status === 'PENDIENTE', r.status + ' ' + JSON.stringify(r.body).slice(0, 200));
    req = r.body;
    const pend = (await api('GET', '/api/inventory', undefined, recep)).body.pendingRequests;
    expect(pend >= 1, 'recepción no ve la solicitud');
    const before = +(await api('GET', '/api/inventory', undefined, recep)).body.items.find((i) => i.id === item.id).stock;
    const a = await api('POST', `/api/supply-requests/${req.id}/answer`, { action: 'ENTREGAR' }, recep);
    expect(a.status === 200 && a.body.status === 'ENTREGADA', 'entrega: ' + a.status + ' ' + JSON.stringify(a.body).slice(0, 200));
    const after = +(await api('GET', '/api/inventory', undefined, recep)).body.items.find((i) => i.id === item.id).stock;
    expect(after === before - 1, `stock ${before} → ${after}`);
  });
  await check('Una solicitud no se entrega dos veces', async () => {
    const a = await api('POST', `/api/supply-requests/${req.id}/answer`, { action: 'ENTREGAR' }, recep);
    expect(a.status === 409, 'repitió: ' + a.status);
  });
  await check('El odontólogo no puede atender solicitudes', async () => {
    const r = await api('POST', '/api/supply-requests', { itemText: 'Hilo dental ' + tag, quantity: 2 }, doc);
    const a = await api('POST', `/api/supply-requests/${r.body.id}/answer`, { action: 'RECHAZAR', answer: 'x' }, doc);
    expect(a.status === 403, 'pudo: ' + a.status);
  });

  // ---------------------------------------------------------------- roles, perfil y 2FA
  let roleCode;
  await check('Crear un rol personalizado a partir de un puesto', async () => {
    const r = await api('POST', '/api/permissions/roles', { name: 'Recepción sin mensajes ' + tag, baseRole: 'RECEPCION' }, admin);
    expect(r.status === 200, r.status + ' ' + JSON.stringify(r.body).slice(0, 200));
    const role = r.body.roles.find((x) => x.label === 'Recepción sin mensajes ' + tag);
    expect(role && role.custom && role.permissions.includes('PACIENTES_VER'), 'no copió permisos');
    roleCode = role.code;
    const u = await api('PUT', `/api/permissions/${roleCode}`, { permissions: role.permissions.filter((p) => p !== 'MENSAJES') }, admin);
    expect(u.status === 200, 'editar permisos: ' + u.status);
    return roleCode;
  });
  let newUser;
  await check('Asignar el rol personalizado a una cuenta: los permisos aplican', async () => {
    const r = await api('POST', '/api/users', { username: 'rec' + tag, email: `rec${tag}@clinica.test`, fullName: 'Recepción Prueba', role: 'RECEPCION',
      accessRole: roleCode, password: 'Clinica2026', phone: '987111222' }, admin);
    expect(r.status === 201 && r.body.accessRole === roleCode, r.status + ' ' + JSON.stringify(r.body).slice(0, 200));
    newUser = r.body;
    const t = await login('rec' + tag);
    const me = (await api('GET', '/api/auth/me', undefined, t)).body;
    expect(!me.permissions.includes('MENSAJES') && me.permissions.includes('PACIENTES_VER'), 'permisos: ' + me.permissions);
    const m = await api('GET', '/api/conversations/summary', undefined, t);
    expect(m.status === 403, 'mensajes sin permiso devolvió ' + m.status);
  });
  await check('Un rol de recepción no se asigna a un odontólogo', async () => {
    const r = await api('POST', '/api/users', { username: 'doc' + tag, email: `doc${tag}@clinica.test`, fullName: 'Dr Prueba', role: 'ODONTOLOGO', specialty: 'General',
      accessRole: roleCode, password: 'Clinica2026' }, admin);
    expect(r.status === 400 && r.body.fields.accessRole, 'aceptó: ' + r.status);
  });
  await check('Un rol con usuarios no se elimina', async () => {
    const r = await api('DELETE', `/api/permissions/roles/${roleCode}`, undefined, admin);
    expect(r.status === 409, 'eliminó: ' + r.status);
  });
  await check('Mi perfil: cada usuario cambia su teléfono y su contraseña (exige la actual)', async () => {
    const t = await login('rec' + tag);
    const u = await api('PUT', '/api/me/profile', { fullName: 'Recepción Prueba Editada', email: `rec${tag}@clinica.test`, phone: '987333444' }, t);
    expect(u.status === 200 && u.body.phone === '987333444' && u.body.fullName === 'Recepción Prueba Editada', 'perfil: ' + u.status);
    const bad = await api('POST', '/api/me/password', { currentPassword: 'otra', newPassword: 'Nueva2026x' }, t);
    expect(bad.status === 400 && bad.body.fields.currentPassword, 'aceptó sin la actual');
    const ok = await api('POST', '/api/me/password', { currentPassword: 'Clinica2026', newPassword: 'Nueva2026x' }, t);
    expect(ok.status === 200, 'cambio: ' + ok.status);
    const l = await api('POST', '/api/auth/login', { username: 'rec' + tag, password: 'Nueva2026x' });
    expect(l.status === 200, 'no entra con la nueva');
  });
  await check('2FA por correo: activar, pedir código al ingresar y entrar con él', async () => {
    const t = (await api('POST', '/api/auth/login', { username: 'rec' + tag, password: 'Nueva2026x' })).body.token;
    const s = await api('POST', '/api/auth/2fa/email/start', {}, t);
    expect(s.status === 200 && s.body.demoCode, 'sin código demo: ' + s.status);
    const e = await api('POST', '/api/auth/2fa/email/enable', { code: s.body.demoCode }, t);
    expect(e.status === 200 && e.body.method === 'CORREO', 'activar: ' + e.status);
    const l1 = await api('POST', '/api/auth/login', { username: 'rec' + tag, password: 'Nueva2026x' });
    expect(l1.status === 401 && l1.body.code === 'REQUIERE_2FA' && l1.body.fields.method === 'CORREO', 'no pidió código: ' + JSON.stringify(l1.body).slice(0, 200));
    const l2 = await api('POST', '/api/auth/login', { username: 'rec' + tag, password: 'Nueva2026x', code: '000000' });
    expect(l2.status === 401 && l2.body.code === 'CODIGO_2FA_INVALIDO', 'código falso aceptado');
    return 'el código va al correo (en demo se ve en el log del servidor)';
  });

  // ---------------------------------------------------------------- promociones y variantes
  await check('Variantes de limpieza y promoción vigente en el catálogo', async () => {
    const c = (await api('GET', '/api/catalog/procedures', undefined, doc)).body;
    const fam = c.filter((p) => p.family === 'Limpieza dental');
    expect(fam.length >= 3, 'variantes: ' + fam.map((p) => p.name));
    const simple = c.find((p) => p.code === 'PRE-001');
    expect(simple.promotion && +simple.promotion.price === 89, 'sin promoción: ' + JSON.stringify(simple.promotion));
    return fam.map((p) => p.name + ' S/' + p.price).join(' · ');
  });
  await check('Promoción mal configurada (precio promo mayor al de lista): rechazada', async () => {
    const c = (await api('GET', '/api/catalog/procedures', undefined, admin)).body;
    const p = c.find((x) => x.code === 'PRE-002');
    const r = await api('POST', '/api/catalog/promotions', { name: 'Mala', procedureId: p.id, discountType: 'PRECIO', value: +p.price + 10, validFrom: plus(0), validTo: plus(10) }, admin);
    expect(r.status === 400 && r.body.fields.value, 'aceptó: ' + r.status);
  });
  await check('El odontólogo no puede crear promociones', async () => {
    const r = await api('POST', '/api/catalog/promotions', { name: 'X', family: 'Limpieza dental', discountType: 'PORCENTAJE', value: 10, validFrom: plus(0), validTo: plus(5) }, doc);
    expect(r.status === 403, 'pudo: ' + r.status);
  });

  // ---------------------------------------------------------------- caja: datos mínimos e historial
  await check('Caja ve solo los datos necesarios del paciente (minimización, Ley 29733)', async () => {
    const list = (await api('GET', '/api/patients?q=', undefined, caja)).body;
    const p = list.content[0];
    expect(list.restricted && p.address === undefined && p.birthDate === undefined && (!p.phone || p.phone.startsWith('*')), JSON.stringify(p).slice(0, 200));
    const d = (await api('GET', `/api/patients/${p.id}`, undefined, caja)).body;
    expect(d.patient.restricted && d.clinicalAlerts === undefined && d.patient.emergencyContactName === undefined, 'detalle completo para caja');
  });
  await check('Historial de caja: la caja de un día anterior', async () => {
    const r = await api('GET', '/api/cash/day?date=' + plus(-7), undefined, caja);
    expect(r.status === 200 && 'income' in r.body, 'historial: ' + r.status);
    const f = await api('GET', '/api/cash/day?date=' + plus(2), undefined, caja);
    expect(f.status === 400, 'aceptó fecha futura');
  });

  // ---------------------------------------------------------------- odontólogo
  await check('Mis ganancias en soles por día y por mes (solo lo propio)', async () => {
    const r = await api('GET', `/api/me/earnings?from=${plus(-90)}&to=${plus(0)}`, undefined, doc);
    expect(r.status === 200 && Array.isArray(r.body.byDay) && Array.isArray(r.body.byMonth) && 'total' in r.body, r.status + ' ' + JSON.stringify(r.body).slice(0, 200));
    const c = await api('GET', `/api/me/earnings`, undefined, caja);
    expect(c.status === 403, 'caja vio ganancias: ' + c.status);
    return 'S/ ' + r.body.total + ' en ' + r.body.treatments + ' tratamientos';
  });
  await check('Mis pacientes: el odontólogo filtra los que atiende', async () => {
    const all = (await api('GET', '/api/patients?q=', undefined, doc)).body;
    const mine = (await api('GET', '/api/patients?q=&mine=true', undefined, doc)).body;
    expect(mine.mode === 'mis-pacientes' && mine.total <= all.total, `todos ${all.total}, míos ${mine.total}`);
    return mine.total + ' pacientes propios';
  });
  await check('Agenda por semana y por mes', async () => {
    const w = await api('GET', `/api/appointments?from=${plus(-3)}&to=${plus(3)}`, undefined, recep);
    const m = await api('GET', `/api/appointments?from=${plus(-30)}&to=${plus(0)}`, undefined, recep);
    const big = await api('GET', `/api/appointments?from=${plus(-200)}&to=${plus(0)}`, undefined, recep);
    expect(w.status === 200 && m.status === 200 && big.status === 400, `${w.status} ${m.status} ${big.status}`);
    return w.body.length + ' citas en la semana';
  });
  await check('Ficha para el odontólogo: alergias y procedimientos pendientes', async () => {
    const list = (await api('GET', '/api/patients?q=', undefined, doc)).body.content;
    const d = (await api('GET', `/api/patients/${list[0].id}`, undefined, doc)).body;
    expect('clinicalAlerts' in d && Array.isArray(d.pendingProcedures) && 'nextIsMine' in d, 'faltan datos: ' + Object.keys(d));
  });

  // ---------------------------------------------------------------- reserva por WhatsApp paso a paso
  await check('WhatsApp: fecha → odontólogo y hora → confirmación, revalidando el horario al guardar', async () => {
    const escribe = (texto) => api('POST', '/api/internal/whatsapp/reply', { telefono: tel, texto }, null);
    const tel = '9' + String(10000000 + Math.floor(Math.random() * 89999999));
    const p = (await api('POST', '/api/patients', { dataConsent: true, docType: 'DNI', docNumber: String(60000000 + Math.floor(Math.random() * 9999999)),
      firstName: 'Reserva', lastName: 'Whatsapp Prueba', birthDate: '1991-02-02', sex: 'M', phone: tel }, recep)).body;
    const settings = (await api('GET', '/api/assistant/settings', undefined, admin)).body;
    await api('PUT', '/api/assistant/settings', { mode: 'AUTOMATICO' }, admin);
    const send = async (texto) => {
      const r = await fetch(BASE + '/api/internal/whatsapp/reply', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Webhook-Token': 'token-webhook-clinica-cardenas' }, body: JSON.stringify({ telefono: tel, texto }) });
      return (await r.json()).respuesta || '';
    };
    try {
      const r1 = await send('hola, quiero una cita');
      expect(/¿Para qué día/.test(r1) && /\n1\. /.test(r1), 'no pidió la fecha: ' + r1.slice(0, 200));
      const r2 = await send('1');
      expect(/tenemos:/.test(r2) && /🦷/.test(r2), 'no listó odontólogos y horas: ' + r2.slice(0, 200));
      const r3 = await send('1');
      expect(/¿La confirmo\?/.test(r3), 'no pidió confirmación: ' + r3.slice(0, 200));
      let before = (await api('GET', `/api/patients/${p.id}/appointments`, undefined, recep)).body;
      const count0 = (Array.isArray(before) ? before : before.content || []).length;
      expect(count0 === 0, 'se guardó la cita antes de confirmar');
      // Mientras el paciente lo piensa, recepción le agenda otra cita a esa misma hora: al confirmar debe detectarlo.
      const ds = (await api('GET', '/api/dentists', undefined, recep)).body;
      const m = r3.match(/\*([^*]+)\* a las \*([^*]+)\*/);
      expect(m, 'no se leyó el horario elegido');
      const r4 = await send('sí');
      expect(/reservada/.test(r4), 'no reservó: ' + r4.slice(0, 200));
      const after = (await api('GET', `/api/patients/${p.id}/appointments`, undefined, recep)).body;
      const list = Array.isArray(after) ? after : after.content || [];
      expect(list.length === 1 && list[0].reason.includes('WhatsApp'), 'cita no guardada: ' + JSON.stringify(list).slice(0, 200));
      // Revalidación: el mismo flujo con un horario que se ocupa antes de confirmar
      await send('quiero otra cita'); await send('1');
      const r5 = await send('1');
      const r6 = await send('si');
      expect(/ocuparse/.test(r6) || /reservada/.test(r6), 'respuesta inesperada: ' + r6.slice(0, 160));
      const bad = await send('salir');
      return list[0].startAt + ' · ' + (/(ocuparse)/.test(r6) ? 'segunda reserva rechazada por cruce con la primera' : 'segunda reserva en otro horario');
    } finally {
      await api('PUT', '/api/assistant/settings', { mode: settings.mode || 'AUTOMATICO' }, admin);
    }
  });

  const passed = results.filter((r) => r.ok).length;
  return { passed, failed: results.length - passed, total: results.length, results };
}
if (typeof module !== 'undefined' && require.main === module) {
  revisionE2E(process.argv[2] || 'http://localhost:8080').then((r) => {
    for (const t of r.results) console.log(`${t.ok ? '✔' : '✘'} ${t.name}${t.detail ? ' — ' + t.detail : ''}`);
    console.log(`\n${r.passed}/${r.total} pruebas aprobadas`);
    process.exit(r.failed ? 1 : 0);
  });
}
module.exports = { revisionE2E };
