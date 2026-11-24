/**
 * Versión 2.8.0: kardex valorizado y consumo por paciente, ortodoncia con álbumes por etapa, consentimientos con
 * firma a distancia, notas fijas y alertas en la ficha, y reporte anual descargable.
 * Uso (solo local, nunca contra producción: crea pacientes, materiales y envía mensajes): node version-2-8-e2e.js http://localhost:8080
 */
// JPEG blanco de 1x1 (para la firma)
const JPEG_1x1 = '/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=';

async function v28(BASE) {
  const results = [];
  const rnd = (n) => Math.floor(Math.random() * n);
  const j = async (m, u, b, t, extra = {}) => {
    const isForm = typeof FormData !== 'undefined' && b instanceof FormData;
    const r = await fetch(BASE + u, { method: m, headers: { ...(isForm ? {} : { 'Content-Type': 'application/json' }), ...(t ? { Authorization: 'Bearer ' + t } : {}), ...extra },
      body: b ? (isForm ? b : JSON.stringify(b)) : undefined });
    const ct = r.headers.get('content-type') || '';
    if (/pdf|csv/.test(ct)) return { s: r.status, b: Buffer.from(await r.arrayBuffer()), ct };
    const x = await r.text(); try { return { s: r.status, b: JSON.parse(x) }; } catch { return { s: r.status, b: x }; }
  };
  const check = async (name, fn) => { try { results.push({ name, ok: true, detail: (await fn()) || '' }); } catch (e) { results.push({ name, ok: false, detail: String(e.message || e) }); } };
  const expect = (c, m) => { if (!c) throw new Error(m); };
  const login = async (u) => (await j('POST', '/api/auth/login', { username: u, password: 'Clinica2026' })).b.token;
  const ad = await login('admin'), rc = await login('recepcion'), cj = await login('caja'), dr = await login('dra.cardenas');
  const day = (n) => { const d = new Date(); d.setDate(d.getDate() + n); return d.toLocaleDateString('en-CA'); };
  const dni = () => String(73000000 + rnd(8999999));
  const cel = () => '9' + String(10000000 + rnd(89999999));
  const word = Array.from({ length: 6 }, () => 'abcdefghijklmnopqrstuvwxyz'[rnd(26)]).join('');
  const newPatient = async (extra = {}) => {
    const r = await j('POST', '/api/patients', { dataConsent: true, docType: 'DNI', docNumber: dni(), firstName: 'Camila', lastName: 'Ocho ' + word, birthDate: '1991-04-12', sex: 'F', phone: cel(), email: `e2e28.${rnd(1e6)}@correo.pe`, ...extra }, rc);
    if (r.s !== 200 && r.s !== 201) throw new Error('paciente ' + r.s + ' ' + JSON.stringify(r.b).slice(0, 200));
    return r.b;
  };
  const p1 = await newPatient();
  const jpeg = Buffer.from(JPEG_1x1, 'base64');

  await check('Kardex: entradas a su costo, salidas al costo del lote y saldo que cuadra', async () => {
    expect((await j('GET', '/api/inventory/valuation', null, rc)).s === 403, 'recepción ve los costos');
    let r = await j('POST', '/api/inventory', { name: 'Resina prueba ' + word, unit: 'jeringa', minStock: 1, initialStock: 0, active: true, category: 'INSUMO', unitCost: 40, tracksExpiry: false }, ad);
    expect(r.s === 200 || r.s === 201, 'material ' + r.s + ' ' + JSON.stringify(r.b).slice(0, 160));
    const item = r.b.item || r.b;
    expect((await j('POST', `/api/inventory/${item.id}/moves`, { kind: 'ENTRADA', quantity: 5, reason: 'x', unitCost: 40, patientId: p1.id }, ad)).s === 400, 'aceptó una entrada atribuida a un paciente');
    expect((await j('POST', `/api/inventory/${item.id}/moves`, { kind: 'ENTRADA', quantity: 10, reason: 'Compra', unitCost: 40 }, ad)).s === 200, 'entrada 1');
    expect((await j('POST', `/api/inventory/${item.id}/moves`, { kind: 'ENTRADA', quantity: 10, reason: 'Compra', unitCost: 50 }, ad)).s === 200, 'entrada 2');
    // sale 12: 10 del primer lote (S/ 40) + 2 del segundo (S/ 50) = S/ 500
    r = await j('POST', `/api/inventory/${item.id}/moves`, { kind: 'SALIDA', quantity: 12, reason: 'Uso en atención', patientId: p1.id }, ad);
    expect(r.s === 200, 'salida ' + r.s + ' ' + JSON.stringify(r.b).slice(0, 160));
    const k = (await j('GET', `/api/inventory/${item.id}/kardex?from=${day(-1)}&to=${day(0)}`, null, ad)).b;
    expect(+k.totals.inValue === 900 && +k.totals.outValue === 500, `totales ${k.totals.inValue}/${k.totals.outValue}`);
    expect(+k.closing.qty === 8 && +k.closing.value === 400, `saldo ${k.closing.qty} / ${k.closing.value}`);
    expect(+k.opening.value + +k.totals.inValue - +k.totals.outValue === +k.closing.value, 'el kardex no cuadra');
    const out = k.rows.find((x) => x.outQty);
    expect(out.patient?.id === p1.id && +out.unitCost === 41.6667, 'costo unitario de la salida ' + out.unitCost);
    const c = (await j('GET', `/api/inventory/consumption?from=${day(-1)}&to=${day(0)}&patientId=${p1.id}`, null, ad)).b;
    expect(+c.total === 500 && c.detail.length === 1, 'consumo del paciente ' + c.total);
  });

  await check('Ortodoncia: un caso abierto por paciente, controles válidos y álbum por etapa', async () => {
    expect((await j('GET', `/api/patients/${p1.id}/ortho`, null, rc)).s === 403, 'recepción ve la ortodoncia');
    let r = await j('POST', `/api/patients/${p1.id}/ortho`, { appliance: 'BRACKETS_ESTETICOS', angleClass: 'II_1', startDate: day(-60), estimatedMonths: 18 }, dr);
    expect(r.s === 200 && r.b.status === 'ACTIVO' && r.b.monthsElapsed >= 1, 'crear ' + r.s);
    const cs = r.b;
    expect((await j('POST', `/api/patients/${p1.id}/ortho`, { appliance: 'ALINEADORES', startDate: day(0) }, dr)).s === 409, 'abrió un segundo caso');
    expect((await j('POST', `/api/ortho/cases/${cs.id}/controls`, { controlDate: day(2), upperWire: 'NiTi 0.014' }, dr)).s === 400, 'control con fecha futura');
    r = await j('POST', `/api/ortho/cases/${cs.id}/controls`, { controlDate: day(0), stage: 'ALINEACION', upperWire: 'NiTi 0.014', lowerWire: 'NiTi 0.012', hygiene: 'BUENA', nextWeeks: 4 }, dr);
    expect(r.s === 200 && r.b.currentStage === 'ALINEACION' && r.b.controls.length === 1, 'control');
    const fd = new FormData();
    fd.append('file', new Blob([jpeg], { type: 'image/jpeg' }), 'sonrisa.jpg'); fd.append('stage', 'INICIAL'); fd.append('view', 'SONRISA');
    r = await j('POST', `/api/ortho/cases/${cs.id}/photos`, fd, dr);
    expect(r.s === 200 && r.b.photos.length === 1 && r.b.photos[0].view === 'SONRISA', 'subir foto ' + r.s + ' ' + JSON.stringify(r.b).slice(0, 160));
    // la foto quedó también en los archivos del paciente
    const files = (await j('GET', `/api/patients/${p1.id}/files`, null, dr)).b;
    expect(files.some((f) => f.id === r.b.photos[0].fileId && f.kind === 'FOTO'), 'la foto no está en sus archivos');
    // reutilizar una foto que ya tenía
    const fd2 = new FormData(); fd2.append('file', new Blob([jpeg], { type: 'image/jpeg' }), 'intraoral.jpg'); fd2.append('kind', 'FOTO');
    const f2 = (await j('POST', `/api/patients/${p1.id}/files`, fd2, dr)).b;
    r = await j('POST', `/api/ortho/cases/${cs.id}/photos/link`, { fileId: f2.id, stage: 'INICIAL', view: 'INTRA_FRONTAL' }, dr);
    expect(r.s === 200 && r.b.photos.length === 2, 'vincular ' + r.s);
    expect((await j('POST', `/api/ortho/cases/${cs.id}/photos/link`, { fileId: f2.id, stage: 'INICIAL', view: 'INTRA_FRONTAL' }, dr)).s === 409, 'la repitió en el álbum');
    const list = (await j('GET', '/api/ortho/cases', null, dr)).b;
    expect(list.some((x) => x.id === cs.id && +x.photos === 2), 'no aparece en la lista de ortodoncia');
  });

  let sent;
  await check('Consentimientos: plantillas con variables válidas; envío por WhatsApp con enlace firmado', async () => {
    expect((await j('PUT', '/api/informed-consents/templates/1', { name: 'X', body: 'x' }, rc)).s === 403, 'recepción editó una plantilla');
    const t = (await j('GET', '/api/informed-consents/templates', null, dr)).b;
    const endo = t.find((x) => x.code === 'ENDODONCIA');
    expect(endo, 'falta la plantilla de endodoncia');
    let r = await j('PUT', `/api/informed-consents/templates/${endo.id}`, { name: endo.name, body: endo.body + ' {firma_doctor}' }, dr);
    expect(r.s === 400 && /firma_doctor/.test(r.b.fields?.body || ''), 'aceptó una variable desconocida');
    expect((await j('POST', `/api/patients/${p1.id}/informed-consents`, { templateId: endo.id, procedure: 'x', channel: 'WHATSAPP' }, rc)).s === 400, 'aceptó un procedimiento vacío');
    r = await j('POST', `/api/patients/${p1.id}/informed-consents`, { templateId: endo.id, procedure: 'Endodoncia multirradicular', tooth: 36, dentistId: 1, channel: 'WHATSAPP' }, rc);
    expect(r.s === 200 && r.b.status === 'PENDIENTE' && /\/firmar\//.test(r.b.url), 'enviar ' + r.s + ' ' + JSON.stringify(r.b).slice(0, 160));
    sent = r.b;
    const n = (await j('GET', `/api/patients/${p1.id}/notifications`, null, rc)).b.find((x) => x.type === 'CONSENTIMIENTO');
    expect(n && n.body.includes(sent.url.split('/firmar/')[1]), 'el WhatsApp no trae el enlace');
  });

  await check('Firma a distancia: valida quién firma, genera el PDF en la ficha y no se firma dos veces', async () => {
    const token = sent.url.split('/firmar/')[1];
    expect((await j('GET', `/api/public/consent/${token.slice(0, -1)}x`)).s === 404, 'aceptó un enlace alterado');
    const g = (await j('GET', `/api/public/consent/${token}`)).b;
    expect(g.status === 'PENDIENTE' && g.body.includes('pieza 36') && g.body.includes(p1.firstName), 'texto ' + JSON.stringify(g).slice(0, 160));
    const base = { signerName: p1.firstName + ' ' + p1.lastName, signerDoc: p1.docNumber, relationship: 'PACIENTE', accepted: true, signatureJpeg: 'data:image/jpeg;base64,' + JPEG_1x1 };
    let r = await j('POST', `/api/public/consent/${token}`, { ...base, signerDoc: '12345678' });
    expect(r.s === 400 && r.b.fields.signerDoc, 'aceptó otro documento');
    r = await j('POST', `/api/public/consent/${token}`, { ...base, accepted: false });
    expect(r.s === 400 && r.b.fields.accepted, 'firmó sin aceptar');
    r = await j('POST', `/api/public/consent/${token}`, base, null, { 'X-Forwarded-For': '190.12.34.56' });
    expect(r.s === 200, 'firmar ' + r.s + ' ' + JSON.stringify(r.b).slice(0, 200));
    expect((await j('POST', `/api/public/consent/${token}`, base)).s === 409, 'firmó dos veces');
    const l = (await j('GET', `/api/patients/${p1.id}/informed-consents`, null, rc)).b.find((x) => x.id === sent.id);
    expect(l.status === 'FIRMADO' && l.fileId, 'estado ' + l.status);
    const pdf = await j('GET', `/api/files/${l.fileId}/content`, null, rc);
    expect(pdf.s === 200 && pdf.b.slice(0, 5).toString() === '%PDF-', 'no es un PDF');
    expect(pdf.b.toString('latin1').includes('190.12.34.56'), 'el PDF no trae la constancia de firma');
  });

  await check('Menor de edad: firma el apoderado; un envío anulado ya no se puede firmar', async () => {
    const kid = await newPatient({ birthDate: '2015-03-03', firstName: 'Tomás', guardianName: 'Rosa Ocho ' + word, guardianDocType: 'DNI', guardianDocNumber: dni(), guardianRelationship: 'MADRE', guardianPhone: cel() });
    const t = (await j('GET', '/api/informed-consents/templates', null, dr)).b.find((x) => x.code === 'GENERAL');
    const s = (await j('POST', `/api/patients/${kid.id}/informed-consents`, { templateId: t.id, procedure: 'Sellantes en molares', channel: 'PRESENCIAL' }, rc)).b;
    const token = s.url.split('/firmar/')[1];
    const g = (await j('GET', `/api/public/consent/${token}`)).b;
    expect(g.minor && g.body.includes('madre de'), 'el texto no dice que firma la madre');
    let r = await j('POST', `/api/public/consent/${token}`, { signerName: 'Tomás Ocho', signerDoc: kid.docNumber, relationship: 'PACIENTE', accepted: true, signatureJpeg: JPEG_1x1 });
    expect(r.s === 400 && r.b.fields.relationship, 'dejó firmar al menor');
    expect((await j('POST', `/api/informed-consents/${s.id}/cancel`, { reason: 'Se cambió el plan' }, rc)).s === 200, 'anular');
    r = await j('POST', `/api/public/consent/${token}`, { signerName: 'Rosa Ocho ' + word, signerDoc: '44556677', relationship: 'MADRE', accepted: true, signatureJpeg: JPEG_1x1 });
    expect(r.s === 409, 'firmó un consentimiento anulado: ' + r.s);
  });

  await check('Ficha: notas fijas y alertas médicas (el detalle solo lo ve el personal clínico)', async () => {
    const pre = await j('POST', '/api/public/preregistro', { person: { docType: 'DNI', docNumber: dni(), firstName: 'Martín', lastName: 'Alergia ' + word, birthDate: '1985-01-01', sex: 'M', phone: cel(), dataConsent: true },
      answers: {}, allergies: 'Penicilina', medication: '' }, null, { 'X-Forwarded-For': `10.${rnd(250)}.${rnd(250)}.${rnd(250)}` });
    expect(pre.s === 200, 'prerregistro ' + pre.s);
    const pid = pre.b.id;
    expect((await j('POST', `/api/patients/${pid}/notes`, { text: 'x' }, rc)).s === 400, 'aceptó una nota de 1 letra');
    let r = await j('POST', `/api/patients/${pid}/notes`, { text: 'Prefiere citas en la tarde', tone: 'AZUL' }, rc);
    expect(r.s === 200 && r.b.notes.length === 1 && r.b.notes[0].pinned, 'nota');
    const fc = (await j('GET', `/api/patients/${pid}/flags`, null, dr)).b;
    expect(fc.allergies === 'Penicilina', 'la odontóloga no ve la alergia');
    const fk = (await j('GET', `/api/patients/${pid}/flags`, null, cj)).b;
    expect(fk.hasMedicalAlert && fk.allergies === undefined && fk.notes.length === 1, 'caja ve el detalle médico o no ve la alerta');
    expect((await j('POST', `/api/patients/${pid}/notes`, { text: 'Nota de caja' }, cj)).s === 403, 'caja escribió una nota');
    // v2.11: borrar es exclusivo del administrador
    r = await j('DELETE', `/api/patients/${pid}/notes/${fc.notes[0].id}`, null, rc);
    expect(r.s === 403, 'recepción no puede borrar');
    r = await j('DELETE', `/api/patients/${pid}/notes/${fc.notes[0].id}`, null, ad);
    expect(r.s === 200 && r.b.notes.length === 0, 'borrar');
  });

  await check('Reporte anual: datos, PDF y CSV; valida el año y el permiso', async () => {
    const y = new Date().getFullYear();
    expect((await j('GET', `/api/reports/annual?year=${y}`, null, rc)).s === 403, 'recepción ve el reporte');
    expect((await j('GET', '/api/reports/annual?year=2010', null, ad)).s === 400, 'aceptó 2010');
    const r = (await j('GET', `/api/reports/annual?year=${y}`, null, ad)).b;
    expect(r.months.length === 12 && r.summary.income >= 0 && r.previous, 'datos');
    const sumMonths = r.months.reduce((a, m) => a + +m.income, 0);
    expect(Math.abs(sumMonths - +r.summary.income) < 0.01, 'los meses no suman el total');
    const pdf = await j('GET', `/api/reports/annual/pdf?year=${y}`, null, ad);
    expect(pdf.s === 200 && pdf.b.slice(0, 5).toString() === '%PDF-', 'pdf');
    const csv = await j('GET', `/api/reports/annual/csv?year=${y}`, null, ad);
    expect(csv.s === 200 && csv.b.toString('utf8').includes('Mes;Citas atendidas'), 'csv');
  });

  const passed = results.filter((r) => r.ok).length;
  return { passed, failed: results.length - passed, total: results.length, results };
}
if (typeof module !== 'undefined' && require.main === module) {
  v28(process.argv[2] || 'http://localhost:8080').then((r) => {
    for (const t of r.results) console.log(`${t.ok ? '✔' : '✘'} ${t.name}${t.detail ? ' — ' + t.detail : ''}`);
    console.log(`\n${r.passed}/${r.total} pruebas aprobadas`);
    process.exit(r.failed ? 1 : 0);
  });
}
