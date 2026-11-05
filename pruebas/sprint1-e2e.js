/*
 * Pruebas end-to-end del Sprint 1 · Clínica Dental Cárdenas
 * Recorre todas las historias contra el sistema en marcha (API real + MySQL).
 *
 * Uso con Node 18+:   node pruebas/sprint1-e2e.js http://localhost:8081 [http://localhost:8082]
 * (el segundo parámetro es la API simulada de SUNAT; por defecto el mismo host en el puerto 8082)
 * También se puede pegar en la consola del navegador estando en http://localhost:8081
 */
async function sprint1E2E(BASE, SUNAT = BASE.replace(/:\d+$/, ':8082')) {
  const PWD = 'Clinica2026';
  const results = [];
  const uid = Date.now().toString().slice(-6);

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
    try {
      const detail = await fn();
      results.push({ hu, name, ok: true, detail: detail || '' });
    } catch (e) {
      results.push({ hu, name, ok: false, detail: String(e.message || e) });
    }
  }
  function expect(cond, msg) { if (!cond) throw new Error(msg); }
  function expectStatus(r, s, ctx) {
    if (r.status !== s) throw new Error(`${ctx}: esperaba ${s}, llegó ${r.status} ${JSON.stringify(r.body).slice(0, 180)}`);
  }
  async function login(u, p = PWD) {
    const r = await api('POST', '/api/auth/login', { username: u, password: p });
    expectStatus(r, 200, 'login ' + u);
    return r.body.token;
  }
  const randDni = () => String(40000000 + Math.floor(Math.random() * 49999999)).slice(0, 8);
  const pad = (n) => String(n).padStart(2, '0');
  const iso = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

  // ---------------------------------------------------------------- HU-25 login, bloqueo, sesión
  let admin, recep, odo, caja, director;
  await check('HU-25', 'Inicio de sesión de los 5 roles', async () => {
    admin = await login('admin'); recep = await login('recepcion'); odo = await login('dra.cardenas');
    caja = await login('caja'); director = await login('director');
    const me = await api('GET', '/api/auth/me', undefined, odo);
    expect(me.body.role === 'ODONTOLOGO' && me.body.dentistId, 'perfil de odontóloga sin dentistId');
    return 'admin, recepción, odontóloga, caja y director';
  });
  await check('HU-25', 'Contraseña incorrecta informa intentos restantes', async () => {
    const r = await api('POST', '/api/auth/login', { username: 'director', password: 'malaClave1' });
    expectStatus(r, 401, 'login inválido');
    expect(/quedan? \d|queda 1/.test(r.body.message), 'mensaje sin intentos restantes: ' + r.body.message);
    await login('director'); // reinicia el contador
    return r.body.message;
  });
  await check('HU-25', 'Sin token la API responde 401', async () => {
    expectStatus(await api('GET', '/api/patients'), 401, 'sin token');
  });

  // usuario temporal para bloqueo / recuperación / desactivación
  const tmpUser = 'prueba' + uid;
  let tmpId;
  await check('HU-27', 'Crear cuenta de recepcionista', async () => {
    const r = await api('POST', '/api/users', { username: tmpUser, email: tmpUser + '@pruebas.pe', fullName: 'Usuario De Prueba', role: 'RECEPCION', password: 'Temporal123' }, admin);
    expectStatus(r, 201, 'crear usuario'); tmpId = r.body.id;
    return tmpUser;
  });
  await check('HU-27', 'Validaciones al crear cuenta (usuario repetido, correo, contraseña débil)', async () => {
    const r = await api('POST', '/api/users', { username: tmpUser, email: 'malcorreo', fullName: 'X', role: 'RECEPCION', password: 'abc' }, admin);
    expectStatus(r, 400, 'crear inválido');
    const f = r.body.fields || {};
    expect(f.username && f.email && f.password && f.fullName, 'faltan campos marcados: ' + Object.keys(f));
    return Object.keys(f).join(', ');
  });
  await check('HU-25', 'Bloqueo tras 5 intentos fallidos y desbloqueo por el administrador', async () => {
    let last;
    for (let i = 0; i < 5; i++) last = await api('POST', '/api/auth/login', { username: tmpUser, password: 'incorrecta9' });
    expectStatus(last, 423, '5º intento');
    const blocked = await api('POST', '/api/auth/login', { username: tmpUser, password: 'Temporal123' });
    expectStatus(blocked, 423, 'contraseña correcta durante bloqueo');
    expectStatus(await api('POST', `/api/users/${tmpId}/unlock`, {}, admin), 200, 'desbloquear');
    await login(tmpUser, 'Temporal123');
    return blocked.body.message;
  });
  await check('HU-25', 'Recuperar contraseña con código de 6 dígitos', async () => {
    const r = await api('POST', '/api/auth/recover', { login: tmpUser + '@pruebas.pe' });
    expectStatus(r, 200, 'solicitar código');
    expect(/^\d{6}$/.test(r.body.demoCode || ''), 'sin código en modo demo');
    const bad = await api('POST', '/api/auth/reset', { login: tmpUser, code: '000000', newPassword: 'NuevaClave77' });
    expect(bad.status === 400, 'código incorrecto aceptado');
    const weak = await api('POST', '/api/auth/reset', { login: tmpUser, code: r.body.demoCode, newPassword: 'corta' });
    expectStatus(weak, 400, 'contraseña débil');
    expectStatus(await api('POST', '/api/auth/reset', { login: tmpUser, code: r.body.demoCode, newPassword: 'NuevaClave77' }), 200, 'restablecer');
    expectStatus(await api('POST', '/api/auth/reset', { login: tmpUser, code: r.body.demoCode, newPassword: 'OtraClave88' }), 400, 'reusar código');
    await login(tmpUser, 'NuevaClave77');
    const unknown = await api('POST', '/api/auth/recover', { login: 'noexiste' + uid });
    expect(unknown.status === 200 && !unknown.body.demoCode, 'revela si la cuenta existe');
    return 'código ' + r.body.demoCode + ' enviado a ' + r.body.maskedEmail;
  });
  await check('HU-25', 'Cerrar sesión invalida el token', async () => {
    const t = await login(tmpUser, 'NuevaClave77');
    expectStatus(await api('POST', '/api/auth/logout', {}, t), 200, 'logout');
    expectStatus(await api('GET', '/api/auth/me', undefined, t), 401, 'token tras logout');
  });
  await check('HU-27', 'Desactivar cuenta corta el acceso al instante', async () => {
    const t = await login(tmpUser, 'NuevaClave77');
    expectStatus(await api('PATCH', `/api/users/${tmpId}/active`, { active: false }, admin), 200, 'desactivar');
    expectStatus(await api('GET', '/api/auth/me', undefined, t), 401, 'token vivo de cuenta desactivada');
    const r = await api('POST', '/api/auth/login', { username: tmpUser, password: 'NuevaClave77' });
    expectStatus(r, 403, 'login desactivado');
    const audit = await api('GET', `/api/users/${tmpId}/audit`, undefined, admin);
    expect(audit.body.length >= 4, 'auditoría incompleta');
    return audit.body.length + ' cambios registrados';
  });
  await check('HU-27', 'El administrador no puede desactivarse a sí mismo', async () => {
    const me = await api('GET', '/api/auth/me', undefined, admin);
    expectStatus(await api('PATCH', `/api/users/${me.body.id}/active`, { active: false }, admin), 400, 'autodesactivar');
  });
  await check('HU-25', 'Permisos por rol en el servidor', async () => {
    expectStatus(await api('GET', '/api/users', undefined, recep), 403, 'recepción → usuarios');
    expectStatus(await api('GET', '/api/patients/1/history', undefined, caja), 403, 'caja → historia clínica');
    expectStatus(await api('GET', '/api/patients/1/history', undefined, recep), 403, 'recepción → historia clínica');
    expectStatus(await api('GET', '/api/billing/invoices', undefined, recep), 403, 'recepción → comprobantes');
    expectStatus(await api('POST', '/api/patients', {}, odo), 403, 'odontóloga → registrar paciente');
    expectStatus(await api('POST', '/api/appointments', {}, director), 403, 'director → crear cita');
    return '6 accesos indebidos bloqueados';
  });

  // ---------------------------------------------------------------- HU-01 / HU-02 / HU-03 pacientes
  const dni = randDni();
  let patient;
  const patientBody = { docType: 'DNI', docNumber: dni, firstName: 'lucía fernanda', lastName: 'ñahui céspedes', birthDate: '1990-04-12', sex: 'F', phone: '987 111 222', email: 'lucia' + uid + '@correo.pe', district: 'Chaclacayo' };
  await check('HU-01', 'Registrar paciente con DNI válido', async () => {
    const r = await api('POST', '/api/patients', patientBody, recep);
    expectStatus(r, 201, 'registrar'); patient = r.body;
    expect(patient.firstName === 'Lucía Fernanda' && patient.lastName === 'Ñahui Céspedes', 'nombres no normalizados: ' + patient.fullName);
    expect(patient.phone === '987111222', 'teléfono no normalizado');
    return patient.fullName + ' · DNI ' + dni;
  });
  await check('HU-01', 'Bloquear duplicado por documento', async () => {
    const r = await api('POST', '/api/patients', patientBody, recep);
    expectStatus(r, 409, 'duplicado');
    expect(r.body.code === 'PACIENTE_DUPLICADO' && r.body.fields.existingId == patient.id, 'no indica el existente');
    const chk = await api('GET', `/api/patients/check-document?type=DNI&number=${dni}`, undefined, recep);
    expect(chk.body.exists, 'check-document no detecta');
    return r.body.message;
  });
  await check('HU-01', 'Validaciones: DNI de 7 dígitos, fecha futura, teléfono', async () => {
    const r = await api('POST', '/api/patients', { ...patientBody, docNumber: '1234567', birthDate: '2999-01-01', phone: '12' }, recep);
    expectStatus(r, 400, 'inválido');
    const f = r.body.fields;
    expect(f.docNumber && f.birthDate && f.phone, 'campos no marcados: ' + Object.keys(f));
    return Object.keys(f).join(', ');
  });
  await check('HU-02', 'Búsqueda exacta por DNI', async () => {
    const r = await api('GET', '/api/patients?q=' + dni, undefined, caja);
    expect(r.body.mode === 'documento' && r.body.content[0].id === patient.id, 'no encontró por DNI');
  });
  await check('HU-02', 'Búsqueda parcial por nombre (con tildes y varias palabras)', async () => {
    const r = await api('GET', '/api/patients?q=' + encodeURIComponent('ñahui lucía'), undefined, recep);
    expect(r.body.mode === 'nombre' && r.body.content.some((p) => p.id === patient.id), 'no encontró por nombre');
    const none = await api('GET', '/api/patients?q=zzqqxx', undefined, recep);
    expect(none.body.total === 0, 'búsqueda vacía devolvió resultados');
    return r.body.total + ' coincidencia(s)';
  });
  await check('HU-02', 'Tiempo de búsqueda menor a 2 s', async () => {
    const t0 = Date.now();
    await api('GET', '/api/patients?q=quispe', undefined, recep);
    const ms = Date.now() - t0;
    expect(ms < 2000, 'tardó ' + ms + ' ms');
    return ms + ' ms';
  });
  await check('HU-03', 'Actualizar datos registra usuario y fecha', async () => {
    const r = await api('PUT', '/api/patients/' + patient.id, { ...patientBody, phone: '999888777' }, recep);
    expectStatus(r, 200, 'actualizar');
    expect(r.body.updatedBy === 'recepcion' && r.body.updatedAt && r.body.docNumber === dni, 'sin trazabilidad');
    const a = await api('GET', `/api/patients/${patient.id}/audit`, undefined, recep);
    expect(a.body.some((x) => x.action === 'EDITAR'), 'sin auditoría');
  });

  // ---------------------------------------------------------------- HU-07 / HU-08 / HU-09 agenda
  const dentists = (await api('GET', '/api/dentists', undefined, recep)).body;
  const d1 = dentists[0], d2 = dentists[1];
  let day = new Date(); day.setDate(day.getDate() + 2);
  while (day.getDay() === 0 || day.getDay() === 6) day.setDate(day.getDate() + 1);
  const dayIso = iso(day);
  let free, appt;
  await check('HU-07', 'Horarios libres según horario del odontólogo', async () => {
    const r = await api('GET', `/api/agenda/slots?dentistId=${d1.id}&date=${dayIso}&duration=30`, undefined, recep);
    expect(r.body.works && r.body.freeCount > 0, 'sin horarios');
    free = r.body.slots.filter((s) => s.available);
    return r.body.freeCount + ' libres el ' + dayIso;
  });
  await check('HU-07', 'Programar cita en horario libre', async () => {
    const r = await api('POST', '/api/appointments', { patientId: patient.id, dentistId: d1.id, startAt: free[0].start, durationMin: 30, reason: 'Evaluación inicial' }, recep);
    expectStatus(r, 201, 'crear cita'); appt = r.body;
    const s = await api('GET', `/api/agenda/slots?dentistId=${d1.id}&date=${dayIso}&duration=30`, undefined, recep);
    expect(s.body.slots.find((x) => x.start === free[0].start).reason === 'OCUPADO', 'el horario sigue libre');
    return free[0].start.replace('T', ' ');
  });
  await check('HU-07', 'Impide cruce del mismo odontólogo', async () => {
    const other = (await api('GET', '/api/patients?q=quispe', undefined, recep)).body.content[0];
    const r = await api('POST', '/api/appointments', { patientId: other.id, dentistId: d1.id, startAt: free[0].start, durationMin: 30, reason: 'Control' }, recep);
    expect(r.status === 409 && r.body.code === 'CRUCE_HORARIO', 'permitió cruce: ' + r.status);
    return r.body.message;
  });
  await check('HU-07', 'Impide que el paciente tenga dos citas a la misma hora', async () => {
    const r = await api('POST', '/api/appointments', { patientId: patient.id, dentistId: d2.id, startAt: free[0].start, durationMin: 30, reason: 'Control' }, recep);
    expect(r.status === 409 && (r.body.code === 'CRUCE_PACIENTE' || r.body.code === 'FUERA_DE_HORARIO'), 'permitió cruce del paciente: ' + r.status);
    return r.body.message;
  });
  await check('HU-07', 'Rechaza fuera de horario y en el pasado', async () => {
    const out = await api('POST', '/api/appointments', { patientId: patient.id, dentistId: d1.id, startAt: dayIso + 'T07:00:00', durationMin: 30, reason: 'x' }, recep);
    expect(out.status === 409 && out.body.code === 'FUERA_DE_HORARIO', 'aceptó 07:00');
    const past = await api('POST', '/api/appointments', { patientId: patient.id, dentistId: d1.id, startAt: '2024-01-10T10:00:00', durationMin: 30, reason: 'x' }, recep);
    expect(past.status === 400, 'aceptó fecha pasada');
  });
  await check('HU-07', 'Concurrencia: dos reservas simultáneas del mismo horario', async () => {
    const ps = (await api('GET', '/api/patients?q=a&size=5', undefined, recep)).body.content.filter((p) => p.id !== patient.id);
    const slot = free[3] || free[1];
    const [a, b] = await Promise.all(ps.slice(0, 2).map((p) =>
      api('POST', '/api/appointments', { patientId: p.id, dentistId: d1.id, startAt: slot.start, durationMin: 30, reason: 'Concurrencia' }, recep)));
    const codes = [a.status, b.status].sort();
    expect(codes[0] === 201 && codes[1] === 409, 'resultado ' + codes.join('/'));
    return 'una aceptada y una rechazada (' + codes.join(' / ') + ')';
  });
  await check('HU-09', 'Flujo de estados: confirmar → llegada → atención → atendida', async () => {
    for (const s of ['CONFIRMADA', 'EN_ESPERA', 'EN_ATENCION', 'ATENDIDA']) {
      const r = await api('PATCH', `/api/appointments/${appt.id}/status`, { status: s }, s === 'EN_ATENCION' || s === 'ATENDIDA' ? odo : recep);
      expectStatus(r, 200, 'pasar a ' + s);
    }
    const bad = await api('PATCH', `/api/appointments/${appt.id}/status`, { status: 'CANCELADA', reason: 'x' }, recep);
    expectStatus(bad, 409, 'cancelar una atendida');
  });
  let appt2;
  await check('HU-08', 'Reprogramar exige motivo y revalida disponibilidad', async () => {
    appt2 = (await api('POST', '/api/appointments', { patientId: patient.id, dentistId: d1.id, startAt: free[5].start, durationMin: 30, reason: 'Control' }, recep)).body;
    const noReason = await api('PUT', '/api/appointments/' + appt2.id, { dentistId: d1.id, startAt: free[6].start, durationMin: 30 }, recep);
    expectStatus(noReason, 400, 'sin motivo');
    const ok = await api('PUT', '/api/appointments/' + appt2.id, { dentistId: d1.id, startAt: free[6].start, durationMin: 30, changeReason: 'Pidió más tarde' }, recep);
    expectStatus(ok, 200, 'reprogramar');
    const h = await api('GET', `/api/appointments/${appt2.id}/audit`, undefined, recep);
    expect(h.body.some((x) => x.action === 'REPROGRAMAR'), 'sin historial');
  });
  await check('HU-09', 'Cancelar exige motivo', async () => {
    expectStatus(await api('PATCH', `/api/appointments/${appt2.id}/status`, { status: 'CANCELADA' }, recep), 400, 'sin motivo');
    expectStatus(await api('PATCH', `/api/appointments/${appt2.id}/status`, { status: 'CANCELADA', reason: 'Viaje' }, recep), 200, 'con motivo');
  });
  await check('HU-07', 'Agenda diaria muestra las citas del día', async () => {
    const r = await api('GET', '/api/agenda/day?date=' + dayIso, undefined, director);
    expect(r.body.appointments.some((a) => a.id === appt.id) && r.body.columns.length >= 1, 'no aparece');
    return r.body.appointments.length + ' citas, ' + r.body.columns.length + ' odontólogos';
  });

  // ---------------------------------------------------------------- HU-04 / HU-05 / HU-06 clínica
  await check('HU-04', 'Historia clínica con antecedentes y alerta de alergias', async () => {
    expectStatus(await api('PUT', `/api/patients/${patient.id}/history`, { bloodType: 'A+', allergies: 'Látex', medicalBackground: 'Hipertensión controlada' }, odo), 200, 'guardar');
    const h = await api('GET', `/api/patients/${patient.id}/history`, undefined, odo);
    expect(h.body.alerts.some((a) => a.includes('Látex')), 'sin alerta');
  });
  await check('HU-04', 'Diagnóstico CIE-10 y validación de pieza', async () => {
    expectStatus(await api('POST', `/api/patients/${patient.id}/diagnoses`, { cie10Code: 'k02.1', description: 'Caries de dentina', tooth: 36 }, odo), 201, 'diagnóstico');
    expectStatus(await api('POST', `/api/patients/${patient.id}/diagnoses`, { description: 'x', tooth: 19 }, odo), 400, 'pieza 19');
  });
  await check('HU-05', 'Odontograma: guardar marcas por cara y pieza', async () => {
    const r = await api('PUT', `/api/patients/${patient.id}/odontogram`, { entries: [
      { tooth: 36, face: 'O', condition: 'CARIES' }, { tooth: 36, face: 'M', condition: 'CARIES' },
      { tooth: 11, face: 'X', condition: 'CORONA' }, { tooth: 75, face: 'O', condition: 'SELLANTE' } ] }, odo);
    expectStatus(r, 200, 'guardar');
    expect(r.body.entries.length === 4 && r.body.updatedBy, 'marcas incompletas');
  });
  await check('HU-05', 'Odontograma: rechaza combinaciones inválidas', async () => {
    for (const e of [[{ tooth: 19, face: 'O', condition: 'CARIES' }], [{ tooth: 36, face: 'X', condition: 'CARIES' }],
      [{ tooth: 36, face: 'O', condition: 'CARIES' }, { tooth: 36, face: 'O', condition: 'RESTAURACION' }]]) {
      expectStatus(await api('PUT', `/api/patients/${patient.id}/odontogram`, { entries: e }, odo), 400, JSON.stringify(e));
    }
    const still = await api('GET', `/api/patients/${patient.id}/odontogram`, undefined, odo);
    expect(still.body.entries.length === 4, 'un error borró el odontograma');
  });
  let plan;
  await check('HU-06', 'Plan de tratamiento con precios del catálogo', async () => {
    const procs = (await api('GET', '/api/procedures', undefined, odo)).body;
    const pr = (c) => procs.find((p) => p.code === c).id;
    const r = await api('POST', `/api/patients/${patient.id}/plans`, { title: 'Plan de prueba', items: [
      { procedureId: pr('CON-001') }, { procedureId: pr('OPE-002'), tooth: 36 } ] }, odo);
    expectStatus(r, 201, 'crear plan'); plan = r.body;
    expect(plan.total == 230 && plan.itemCount === 2, 'total ' + plan.total);
    return 'S/ ' + plan.total;
  });
  await check('HU-06', 'Evolución por sesión marca el procedimiento y completa el plan', async () => {
    const [i1, i2] = plan.items;
    let v = (await api('POST', `/api/plans/${plan.plan.id}/evolutions`, { itemId: i1.id, notes: 'Evaluación completa', markDone: true }, odo)).body;
    expect(v.items.find((i) => i.id === i1.id).status === 'REALIZADO', 'no marcó realizado');
    v = (await api('PATCH', `/api/plan-items/${i2.id}`, { status: 'REALIZADO' }, odo)).body;
    expect(v.plan.status === 'COMPLETADO' && v.doneCount === 2, 'plan no completado');
    const future = await api('POST', `/api/plans/${plan.plan.id}/evolutions`, { notes: 'x', sessionDate: '2999-01-01' }, odo);
    expect(future.status === 400 || future.status === 200, 'respuesta inesperada');
  });

  // ---------------------------------------------------------------- HU-10 / HU-11 / HU-12 facturación
  let boleta;
  await check('HU-10', 'Procedimientos realizados disponibles para facturar', async () => {
    const r = await api('GET', `/api/billing/patients/${patient.id}/billable-items`, undefined, caja);
    expect(r.body.length === 2, 'esperaba 2, hay ' + r.body.length);
  });
  await check('HU-10', 'Boleta con IGV incluido y numeración correlativa', async () => {
    const items = (await api('GET', `/api/billing/patients/${patient.id}/billable-items`, undefined, caja)).body;
    const r = await api('POST', '/api/billing/invoices', { docType: '03', patientId: patient.id, customerDocType: '1', customerDocNumber: dni, customerName: patient.fullName,
      items: items.map((i) => ({ description: i.description, quantity: 1, unitPrice: i.price, treatmentItemId: i.treatmentItemId })) }, caja);
    expectStatus(r, 201, 'boleta'); boleta = r.body.invoice;
    const t = boleta;
    expect(Math.abs(t.taxableAmount + t.igvAmount - t.totalAmount) < 0.001 && t.totalAmount == 230, 'importes ' + JSON.stringify([t.taxableAmount, t.igvAmount, t.totalAmount]));
    expect(r.body.amountInWords === 'SON: DOSCIENTOS TREINTA CON 00/100 SOLES', r.body.amountInWords);
    const again = await api('POST', '/api/billing/invoices', { docType: '03', patientId: patient.id, customerDocType: '1', customerDocNumber: dni, customerName: 'Cliente Prueba', items: [{ description: 'Repetido', quantity: 1, unitPrice: 50, treatmentItemId: items[0].treatmentItemId }] }, caja);
    expectStatus(again, 409, 'facturar dos veces el mismo procedimiento');
    return t.series + '-' + t.number + ' · gravada ' + t.taxableAmount + ' + IGV ' + t.igvAmount + ' = ' + t.totalAmount;
  });
  await check('HU-10', 'Factura valida RUC con dígito verificador', async () => {
    const base = { docType: '01', customerDocType: '6', customerName: 'EMPRESA DE PRUEBA S.A.C.', customerAddress: 'Av. Prueba 123', items: [{ description: 'Profilaxis', quantity: 2, unitPrice: 120 }] };
    expectStatus(await api('POST', '/api/billing/invoices', { ...base, customerDocNumber: '20131312954' }, caja), 400, 'RUC inválido');
    const ok = await api('POST', '/api/billing/invoices', { ...base, customerDocNumber: '20131312955' }, caja);
    expectStatus(ok, 201, 'factura');
    return ok.body.invoice.series + '-' + ok.body.invoice.number;
  });
  await check('HU-10', 'Boleta sin documento limitada a S/ 700', async () => {
    const r = await api('POST', '/api/billing/invoices', { docType: '03', customerDocType: '0', items: [{ description: 'Implante', quantity: 1, unitPrice: 2800 }] }, caja);
    expectStatus(r, 400, 'boleta anónima > 700');
  });
  await check('HU-11', 'Envío a SUNAT, CDR y protección contra reenvío', async () => {
    const r = await api('POST', `/api/billing/invoices/${boleta.id}/send`, {}, caja);
    expectStatus(r, 200, 'enviar');
    expect(r.body.invoice.status === 'ACEPTADO' && r.body.invoice.sunatCode === '0' && r.body.invoice.hasCdr, 'estado ' + r.body.invoice.status);
    expectStatus(await api('POST', `/api/billing/invoices/${boleta.id}/send`, {}, caja), 409, 'reenvío');
    return r.body.invoice.sunatDescription;
  });
  await check('HU-12', 'Descarga de XML UBL 2.1 y CDR', async () => {
    const x = await fetch(`${BASE}/api/billing/invoices/${boleta.id}/xml`, { headers: { Authorization: 'Bearer ' + caja } }).then((r) => r.text());
    expect(x.includes('<cbc:UBLVersionID>2.1</cbc:UBLVersionID>') && x.includes(`<cbc:ID>${boleta.series}-${boleta.number}</cbc:ID>`), 'XML incorrecto');
    const c = await fetch(`${BASE}/api/billing/invoices/${boleta.id}/cdr`, { headers: { Authorization: 'Bearer ' + caja } }).then((r) => r.text());
    expect(c.includes('<cbc:ResponseCode>0</cbc:ResponseCode>'), 'CDR incorrecto');
  });
  await check('HU-12', 'Consulta por número, cliente y estado', async () => {
    const byNum = await api('GET', `/api/billing/invoices?q=${boleta.series}-${boleta.number}`, undefined, caja);
    expect(byNum.body.content.length === 1, 'por número');
    const byDoc = await api('GET', `/api/billing/invoices?q=${dni}`, undefined, caja);
    expect(byDoc.body.content.some((i) => i.id === boleta.id), 'por documento');
    const acc = await api('GET', '/api/billing/invoices?status=ACEPTADO', undefined, caja);
    expect(acc.body.content.every((i) => i.status === 'ACEPTADO'), 'filtro de estado');
  });

  // ---------------------------------------------------------------- API simulada de SUNAT
  const sunatMode = (modo) => fetch(SUNAT + '/admin/modo', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ modo }) });
  const simpleBoleta = (price) => api('POST', '/api/billing/invoices', { docType: '03', customerDocType: '1', customerDocNumber: randDni(), customerName: 'Paciente Sunat ' + uid,
    items: [{ description: 'Consulta odontológica', quantity: 1, unitPrice: price }] }, caja);

  await check('SUNAT', 'XML firmado (XMLDSig RSA-SHA256) con el RUC de la clínica', async () => {
    const x = await fetch(`${BASE}/api/billing/invoices/${boleta.id}/xml`, { headers: { Authorization: 'Bearer ' + caja } }).then((r) => r.text());
    expect(/<ds:Signature[^>]*Id="SignatureSP"/.test(x), 'sin ds:Signature Id=SignatureSP');
    expect(x.includes('xmldsig-more#rsa-sha256') && x.includes('<ds:X509Certificate>'), 'algoritmo o certificado ausente');
    expect(x.includes('<cbc:ID schemeID="6">10102984841</cbc:ID>'), 'el emisor no es el RUC 10102984841');
    const digest = (x.match(/<ds:DigestValue>([^<]+)<\/ds:DigestValue>/) || [])[1];
    const d = await api('GET', `/api/billing/invoices/${boleta.id}`, undefined, caja);
    expect(digest && d.body.invoice.hash === digest, 'el código hash no es el DigestValue de la firma');
    return 'hash ' + digest.slice(0, 16) + '…';
  });
  await check('SUNAT', 'El comprobante llega a la API simulada (sendBill)', async () => {
    const html = await fetch(SUNAT + '/').then((r) => r.text());
    expect(html.includes(`10102984841-03-${boleta.series}-${boleta.number}`), 'la API simulada no registró el comprobante');
  });
  await check('SUNAT', 'Consulta RUC: activo, no habido, inexistente e inválido', async () => {
    const ok = await api('GET', '/api/billing/ruc/20100070970', undefined, caja);
    expectStatus(ok, 200, 'RUC activo');
    expect(ok.body.razonSocial && ok.body.estado === 'ACTIVO', 'datos del padrón');
    const baja = await api('GET', '/api/billing/ruc/20548777217', undefined, caja);
    expect(baja.body.condicion === 'NO HABIDO', 'condición');
    expectStatus(await api('GET', '/api/billing/ruc/20999999990', undefined, caja), 404, 'RUC sin padrón');
    expectStatus(await api('GET', '/api/billing/ruc/20131312954', undefined, caja), 400, 'RUC con dígito incorrecto');
    return ok.body.razonSocial;
  });
  await check('SUNAT', 'Factura a un RUC dado de baja: rechazo 2018 con CDR', async () => {
    const f = await api('POST', '/api/billing/invoices', { docType: '01', customerDocType: '6', customerDocNumber: '20548777217', customerName: 'TRANSPORTES RIMAC EXPRESS E.I.R.L.',
      customerAddress: 'Chaclacayo', items: [{ description: 'Profilaxis', quantity: 1, unitPrice: 120 }] }, caja);
    expectStatus(f, 201, 'factura');
    const r = await api('POST', `/api/billing/invoices/${f.body.invoice.id}/send`, {}, caja);
    expect(r.body.invoice.status === 'RECHAZADO' && r.body.invoice.sunatCode === '2018' && r.body.invoice.hasCdr, 'estado ' + r.body.invoice.status + ' ' + r.body.invoice.sunatCode);
    return r.body.invoice.sunatDescription;
  });
  await check('SUNAT', 'Servicio caído: queda en ERROR y el reintento es aceptado', async () => {
    const b = await simpleBoleta(80);
    expectStatus(b, 201, 'boleta');
    await sunatMode('caido');
    let r;
    try { r = await api('POST', `/api/billing/invoices/${b.body.invoice.id}/send`, {}, caja); } finally { await sunatMode('normal'); }
    expect(r.body.invoice.status === 'ERROR', 'con SUNAT caída esperaba ERROR, llegó ' + r.body.invoice.status);
    const again = await api('POST', `/api/billing/invoices/${b.body.invoice.id}/send`, {}, caja);
    expect(again.body.invoice.status === 'ACEPTADO', 'reintento: ' + again.body.invoice.status);
    expect(again.body.submissions.length === 2, 'debería registrar 2 intentos');
    return 'ERROR → ACEPTADO en el segundo intento';
  });
  await check('SUNAT', 'Consulta de estado (getStatusCdr)', async () => {
    const st = await api('POST', `/api/billing/invoices/${boleta.id}/status`, {}, caja);
    expectStatus(st, 200, 'consulta');
    expect(st.body.invoice.status === 'ACEPTADO' && /confirma/.test(st.body.statusMessage), st.body.statusMessage);
    const nuevo = await simpleBoleta(60);
    const st2 = await api('POST', `/api/billing/invoices/${nuevo.body.invoice.id}/status`, {}, caja);
    expect(/no tiene registrado/.test(st2.body.statusMessage) && st2.body.invoice.status === 'GENERADO', st2.body.statusMessage);
  });

  await check('Inicio', 'Resumen del día por rol', async () => {
    const r = await api('GET', '/api/dashboard', undefined, odo);
    expect(r.body.scopedToDentist === true, 'odontóloga no ve solo su agenda');
    const a = await api('GET', '/api/dashboard', undefined, admin);
    expect(a.body.patientsTotal > 0, 'sin pacientes');
  });

  const passed = results.filter((r) => r.ok).length;
  return { passed, failed: results.length - passed, total: results.length, results };
}

if (typeof module !== 'undefined' && require.main === module) {
  const base = process.argv[2] || 'http://localhost:8081';
  sprint1E2E(base, process.argv[3]).then((r) => {
    for (const t of r.results) console.log(`${t.ok ? '✔' : '✘'} [${t.hu}] ${t.name}${t.detail ? ' — ' + t.detail : ''}`);
    console.log(`\n${r.passed}/${r.total} pruebas aprobadas`);
    process.exit(r.failed ? 1 : 0);
  });
}
