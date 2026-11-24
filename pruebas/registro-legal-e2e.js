/**
 * Pruebas del registro de pacientes según la normativa peruana:
 * - Ley 29733: sin consentimiento expreso no se registra; queda constancia (versión, canal, quién aceptó).
 * - Código Civil: menores de 18 años exigen representante legal (padre, madre o tutor) y no "contacto de emergencia".
 * - UBIGEO INEI: el distrito se guarda con su código y su departamento/provincia.
 * Uso: node registro-legal-e2e.js http://localhost:8080
 */
async function registroLegalE2E(BASE) {
  const results = [];
  const api = async (method, path, body, token) => {
    const res = await fetch(BASE + path, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
      body: body === undefined ? undefined : JSON.stringify(body) });
    const t = await res.text(); let j = null; try { j = t ? JSON.parse(t) : null; } catch { j = t; }
    return { status: res.status, body: j };
  };
  const check = async (name, fn) => { try { results.push({ name, ok: true, detail: (await fn()) || '' }); } catch (e) { results.push({ name, ok: false, detail: String(e.message || e) }); } };
  const expect = (c, m) => { if (!c) throw new Error(m); };
  const dni = () => String(60000000 + Math.floor(Math.random() * 9999999));
  const recep = (await api('POST', '/api/auth/login', { username: 'recepcion', password: 'Clinica2026' })).body.token;
  const yearsAgo = (n) => { const d = new Date(); d.setFullYear(d.getFullYear() - n); return d.toISOString().slice(0, 10); };
  const base = (extra) => ({ docType: 'DNI', docNumber: dni(), firstName: 'Prueba', lastName: 'Legal Registro', sex: 'F', phone: '987000111', ...extra });

  await check('Política vigente pública y versionada', async () => {
    const r = await api('GET', '/api/public/legal/PRIVACIDAD');
    expect(r.status === 200 && r.body.version && /29733/.test(r.body.body), 'sin política: ' + r.status);
    return 'versión ' + r.body.version;
  });
  await check('Sin consentimiento no se registra (datos de salud = sensibles)', async () => {
    const r = await api('POST', '/api/patients', base({ birthDate: yearsAgo(30) }), recep);
    expect(r.status === 400 && r.body.code === 'SIN_CONSENTIMIENTO', 'aceptó: ' + r.status);
  });
  await check('Menor sin representante legal: rechazado', async () => {
    const r = await api('POST', '/api/patients', base({ birthDate: yearsAgo(10), dataConsent: true, emergencyContactName: 'Tía Rosa', emergencyContactPhone: '988777666' }), recep);
    expect(r.status === 400 && r.body.fields && r.body.fields.guardianName && r.body.fields.guardianDocNumber, 'aceptó: ' + r.status + ' ' + JSON.stringify(r.body).slice(0, 200));
  });
  let minor;
  await check('Menor con madre como representante: registrado y consentimiento a nombre de la madre', async () => {
    const r = await api('POST', '/api/patients', base({ birthDate: yearsAgo(9), dataConsent: true, guardianName: 'rosa elena huamán', guardianDocType: 'DNI',
      guardianDocNumber: dni(), guardianRelationship: 'MADRE', guardianPhone: '987654000', ubigeo: '150107' }), recep);
    expect(r.status === 201, 'no se registró: ' + r.status + ' ' + JSON.stringify(r.body).slice(0, 300));
    minor = r.body;
    expect(minor.minor === true && minor.guardianName === 'Rosa Elena Huamán', 'datos del menor: ' + JSON.stringify(minor).slice(0, 200));
    const c = (await api('GET', `/api/patients/${minor.id}/consents`, undefined, recep)).body;
    expect(c.accepted && /Rosa Elena Huamán \(madre/.test(c.acceptedBy) && c.records[0].channel === 'PRESENCIAL', 'consentimiento: ' + JSON.stringify(c).slice(0, 300));
    return c.acceptedBy;
  });
  await check('UBIGEO: guarda código, departamento, provincia y distrito', async () => {
    expect(minor && minor.ubigeo === '150107' && minor.department === 'Lima' && minor.province === 'Lima' && minor.district === 'Chaclacayo', JSON.stringify(minor).slice(0, 200));
  });
  await check('UBIGEO inválido: rechazado', async () => {
    const r = await api('POST', '/api/patients', base({ birthDate: yearsAgo(40), dataConsent: true, ubigeo: '999999' }), recep);
    expect(r.status === 400 && r.body.fields && r.body.fields.ubigeo, 'aceptó: ' + r.status);
  });
  await check('El representante no puede ser el mismo menor', async () => {
    const d = dni();
    const r = await api('POST', '/api/patients', base({ docNumber: d, birthDate: yearsAgo(12), dataConsent: true, guardianName: 'Juan Pérez', guardianDocNumber: d, guardianRelationship: 'PADRE', guardianPhone: '987654111' }), recep);
    expect(r.status === 400 && r.body.fields.guardianDocNumber, 'aceptó: ' + r.status);
  });
  await check('Adulto: contacto de emergencia opcional con parentesco', async () => {
    const r = await api('POST', '/api/patients', base({ birthDate: yearsAgo(35), dataConsent: true, emergencyContactName: 'Carlos Ríos', emergencyContactRelationship: 'Cónyuge o pareja', emergencyContactPhone: '988111222' }), recep);
    expect(r.status === 201 && r.body.minor === false && r.body.emergencyContactRelationship === 'Cónyuge o pareja' && !r.body.guardianName, r.status + ' ' + JSON.stringify(r.body).slice(0, 200));
  });
  await check('Consulta de DNI: si no hay token, avisa sin romper el registro', async () => {
    const c = (await api('GET', '/api/lookup/dni-config', undefined, recep)).body;
    const r = await api('GET', '/api/lookup/dni/45872196', undefined, recep);
    // con token: 200 si RENIEC lo tiene o 404 DNI_NO_ENCONTRADO si no; sin token: 503 con aviso
    expect(c.enabled ? (r.status === 200 || (r.status === 404 && r.body.code === 'DNI_NO_ENCONTRADO')) : r.status === 503, `config ${c.enabled}, respuesta ${r.status}`);
  });
  const passed = results.filter((r) => r.ok).length;
  return { passed, failed: results.length - passed, total: results.length, results };
}
if (typeof module !== 'undefined' && require.main === module) {
  registroLegalE2E(process.argv[2] || 'http://localhost:9081').then((r) => {
    for (const t of r.results) console.log(`${t.ok ? '✔' : '✘'} ${t.name}${t.detail ? ' — ' + t.detail : ''}`);
    console.log(`\n${r.passed}/${r.total} pruebas aprobadas`);
    process.exit(r.failed ? 1 : 0);
  });
}
