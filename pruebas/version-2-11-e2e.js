/**
 * Versión 2.11: el administrador puede eliminar cualquier registro y administrar roles y permisos; nadie más.
 * Solo local, nunca contra producción (crea y elimina un paciente, roles y una tarea).
 * Uso: node version-2-11-e2e.js http://localhost:8080
 */
async function v211(BASE) {
  const results = [];
  const j = async (m, u, b, t) => {
    const r = await fetch(BASE + u, { method: m, headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: 'Bearer ' + t } : {}) }, body: b ? JSON.stringify(b) : undefined });
    const x = await r.text(); try { return { s: r.status, b: JSON.parse(x) }; } catch { return { s: r.status, b: x }; }
  };
  const check = async (name, fn) => { try { results.push({ name, ok: true, detail: (await fn()) || '' }); } catch (e) { results.push({ name, ok: false, detail: String(e.message || e) }); } };
  const expect = (c, m) => { if (!c) throw new Error(m); };
  const login = async (u) => (await j('POST', '/api/auth/login', { username: u, password: 'Clinica2026' })).b.token;
  const ad = await login('admin'), rc = await login('recepcion'), dr = await login('dra.cardenas');
  const dni = () => String(Math.floor(70000000 + Math.random() * 9999999));

  await check('Solo el administrador entra a roles y permisos', async () => {
    for (const t of [rc, dr]) {
      const r = await j('GET', '/api/permissions', null, t);
      expect(r.s === 403, 'otro rol vio la matriz: ' + r.s);
    }
    const m = await j('GET', '/api/permissions', null, ad);
    expect(m.s === 200 && m.b.adminOnly.includes('ROLES'), 'admin ' + m.s);
  });

  await check('Roles: crear sin permisos, renombrar, ocultar, eliminar pasando sus usuarios a otro rol', async () => {
    let m = (await j('POST', '/api/permissions/roles', { name: 'Prueba 211 ' + Date.now() % 100000, baseRole: 'RECEPCION', copyFrom: 'VACIO' }, ad)).b;
    const role = m.roles.find((r) => r.label.startsWith('Prueba 211'));
    expect(role && role.permissions.length === 0, 'rol vacío');
    const give = await j('PUT', `/api/permissions/${role.code}`, { permissions: ['ROLES'] }, ad);
    expect(give.s === 400, 'dejó dar ROLES a otro rol');
    m = (await j('PATCH', `/api/permissions/roles/${role.code}`, { name: role.label + ' b', description: 'x' }, ad)).b;
    expect(m.roles.find((r) => r.code === role.code).label.endsWith(' b'), 'no renombró');
    const u = await j('POST', '/api/users', { username: 'p211' + (Date.now() % 100000), fullName: 'Usuario Prueba', email: `p211.${Date.now()}@example.com`, role: 'RECEPCION', accessRole: role.code, password: 'Clinica2026!x' }, ad);
    expect(u.s === 200 || u.s === 201, 'usuario ' + u.s + ' ' + JSON.stringify(u.b).slice(0, 120));
    const hide = await j('PATCH', `/api/permissions/roles/${role.code}`, { hidden: true }, ad);
    expect(hide.s === 409, 'ocultó un rol con usuarios');
    const del = await j('DELETE', `/api/permissions/roles/${role.code}`, null, ad);
    expect(del.s === 409, 'eliminó un rol con usuarios sin decir a dónde pasarlos');
    const ok = await j('DELETE', `/api/permissions/roles/${role.code}?reassignTo=RECEPCION`, null, ad);
    expect(ok.s === 200, 'eliminar con reasignación ' + ok.s);
    const user = (await j('GET', '/api/users', null, ad)).b.find((x) => x.id === u.b.id);
    expect(user && !user.accessRole, 'el usuario no pasó al rol Recepcionista');
    const gone = await j('POST', `/api/admin/records/users/${u.b.id}/delete`, { confirm: 'ELIMINAR', reason: 'prueba' }, ad);
    expect(gone.s === 200, 'eliminar usuario ' + gone.s);
    const base = await j('DELETE', '/api/permissions/roles/CAJA', null, ad);
    expect(base.s === 400, 'eliminó un rol del sistema');
    const adm = await j('PATCH', '/api/permissions/roles/ADMIN', { hidden: true }, ad);
    expect(adm.s === 400, 'ocultó al administrador');
  });

  await check('Permisos: el administrador cambia nombre, grupo y descripción', async () => {
    const m = (await j('GET', '/api/permissions', null, ad)).b;
    const p = m.permissions.find((x) => x.code === 'REPORTES');
    const r = await j('PATCH', '/api/permissions/catalog/REPORTES', { label: p.label, group: p.group, description: 'Ver los indicadores de la clínica' }, ad);
    expect(r.s === 200 && r.b.permissions.find((x) => x.code === 'REPORTES').description === 'Ver los indicadores de la clínica', 'no guardó');
    expect((await j('PATCH', '/api/permissions/catalog/REPORTES', { label: 'x' }, rc)).s === 403, 'otro rol editó un permiso');
  });

  await check('Eliminar: solo el administrador, con revisión previa, confirmación y copia en la papelera', async () => {
    const p = (await j('POST', '/api/patients', { dataConsent: true, docType: 'DNI', docNumber: dni(), firstName: 'Borrar', lastName: 'Prueba Once', birthDate: '1990-01-01', sex: 'F', phone: '912345678' }, rc)).b;
    expect(p.id, 'paciente');
    const t = await j('POST', '/api/tasks', { patientId: p.id, kind: 'LLAMAR', title: 'Llamar prueba 211', dueDate: new Date().toISOString().slice(0, 10) }, rc);
    expect((await j('GET', `/api/admin/records/patients/${p.id}/preview`, null, rc)).s === 403, 'recepción vio la revisión');
    expect((await j('POST', `/api/admin/records/patients/${p.id}/delete`, { confirm: 'ELIMINAR' }, rc)).s === 403, 'recepción eliminó');
    const pv = (await j('GET', `/api/admin/records/patients/${p.id}/preview`, null, ad)).b;
    expect(pv.canDelete, 'no deja eliminar: ' + JSON.stringify(pv.blockers));
    if (t.b && t.b.id) expect(pv.alsoDeleted['tareas'] === 1, 'no avisa de la tarea (se borra con el paciente desde 2.11.2): ' + JSON.stringify(pv));
    expect((await j('POST', `/api/admin/records/patients/${p.id}/delete`, { confirm: 'si' }, ad)).s === 400, 'borró sin escribir ELIMINAR');
    const d = await j('POST', `/api/admin/records/patients/${p.id}/delete`, { confirm: 'ELIMINAR', reason: 'prueba 2.11' }, ad);
    expect(d.s === 200, 'eliminar ' + d.s);
    expect((await j('GET', `/api/patients/${p.id}`, null, ad)).s === 404, 'el paciente sigue');
    const trash = (await j('GET', '/api/admin/records/trash', null, ad)).b;
    expect(trash[0].label.includes('Borrar Prueba Once') && trash[0].reason === 'prueba 2.11', 'no está en la papelera');
  });

  await check('Lo que la ley obliga a conservar no se elimina', async () => {
    const pv = (await j('GET', '/api/admin/records/patients/1/preview', null, ad)).b;
    expect(!pv.canDelete && pv.blockers.length, 'dejó eliminar un paciente con pagos/comprobantes');
    const me = (await j('GET', '/api/users', null, ad)).b.find((u) => u.username === 'admin');
    const self = (await j('GET', `/api/admin/records/users/${me.id}/preview`, null, ad)).b;
    expect(!self.canDelete, 'dejó eliminar la propia cuenta');
  });

  const passed = results.filter((r) => r.ok).length;
  return { passed, failed: results.length - passed, total: results.length, results };
}
if (typeof module !== 'undefined' && require.main === module) {
  v211(process.argv[2] || 'http://localhost:8080').then((r) => {
    for (const t of r.results) console.log(`${t.ok ? '✔' : '✘'} ${t.name}${t.detail ? ' — ' + t.detail : ''}`);
    console.log(`\n${r.passed}/${r.total} pruebas aprobadas`);
    process.exit(r.failed ? 1 : 0);
  });
}
module.exports = { v211 };
