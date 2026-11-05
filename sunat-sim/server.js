/*
 * API simulada de SUNAT · Facturación electrónica (entorno beta)
 * ---------------------------------------------------------------
 * Imita los servicios que usa un emisor electrónico:
 *   POST /ol-ti-itcpfegem-beta/billService   SOAP sendBill      → recibe ZIP con XML firmado, devuelve CDR (ZIP)
 *   POST /ol-ti-itcpfegem-beta/billService   SOAP getStatusCdr  → consulta el CDR de un comprobante ya enviado
 *   GET  /v1/ruc/{ruc}                        Consulta RUC (padrón reducido simulado)
 *   GET  /                                    Panel web con lo recibido
 *   POST /admin/modo {"modo":"normal|caido|lento"}  Simula caída o lentitud del servicio
 *
 * Códigos según el catálogo de errores de SUNAT:
 *   0       aceptado
 *   0100-1999 excepciones (SOAP Fault, el comprobante NO queda registrado, se puede reenviar corregido)
 *   2000-3999 rechazo (CDR con código de rechazo)
 *   4000+   aceptado con observaciones
 *
 * Sin dependencias externas: Node 20 (http, zlib, crypto).
 */
const http = require('http');
const zlib = require('zlib');
const fs = require('fs');
const path = require('path');

const PORT = Number(process.env.PORT || 8090);
const DATA_FILE = process.env.DATA_FILE || '/data/registro.json';
const USUARIO_SOL = (process.env.SUNAT_USUARIO || 'MODDATOS').toUpperCase();
const CLAVE_SOL = process.env.SUNAT_CLAVE || 'MODDATOS';
const SUNAT_RUC = '20131312955';

let modo = 'normal';
let registro = cargar();

// ---------------------------------------------------------------- padrón simulado
const PADRON = {
  '10102984841': { razonSocial: 'CLÍNICA DENTAL CÁRDENAS', estado: 'ACTIVO', condicion: 'HABIDO', direccion: 'URB. LA FLORESTA', distrito: 'CHACLACAYO', provincia: 'LIMA', departamento: 'LIMA', tipo: 'PERSONA NATURAL CON NEGOCIO' },
  '20131312955': { razonSocial: 'SUPERINTENDENCIA NACIONAL DE ADUANAS Y DE ADMINISTRACION TRIBUTARIA - SUNAT', estado: 'ACTIVO', condicion: 'HABIDO', direccion: 'AV. GARCILASO DE LA VEGA NRO. 1472', distrito: 'LIMA', provincia: 'LIMA', departamento: 'LIMA', tipo: 'INSTITUCION PUBLICA' },
  '20100070970': { razonSocial: 'SUPERMERCADOS PERUANOS SOCIEDAD ANONIMA', estado: 'ACTIVO', condicion: 'HABIDO', direccion: 'CAL. MORELLI NRO. 181 INT. P-2', distrito: 'SAN BORJA', provincia: 'LIMA', departamento: 'LIMA', tipo: 'SOCIEDAD ANONIMA' },
  '20601234565': { razonSocial: 'MINERA ANDINA DEL CENTRO S.A.C.', estado: 'ACTIVO', condicion: 'HABIDO', direccion: 'AV. NICOLAS AYLLON NRO. 4520', distrito: 'ATE', provincia: 'LIMA', departamento: 'LIMA', tipo: 'SOCIEDAD ANONIMA CERRADA' },
  '20511465061': { razonSocial: 'COLEGIO SANTA MARIA DE CHOSICA S.A.C.', estado: 'ACTIVO', condicion: 'HABIDO', direccion: 'AV. LIMA SUR NRO. 870', distrito: 'LURIGANCHO', provincia: 'LIMA', departamento: 'LIMA', tipo: 'SOCIEDAD ANONIMA CERRADA' },
  '20548777217': { razonSocial: 'TRANSPORTES RIMAC EXPRESS E.I.R.L.', estado: 'BAJA DE OFICIO', condicion: 'NO HABIDO', direccion: '-', distrito: 'CHACLACAYO', provincia: 'LIMA', departamento: 'LIMA', tipo: 'EMPRESA INDIVIDUAL DE RESP. LTDA' },
  '10457812367': { razonSocial: 'QUISPE HUAMAN MARIA FERNANDA', estado: 'ACTIVO', condicion: 'HABIDO', direccion: 'JR. LOS PINOS NRO. 123', distrito: 'CHACLACAYO', provincia: 'LIMA', departamento: 'LIMA', tipo: 'PERSONA NATURAL CON NEGOCIO' },
};

function rucValido(ruc) {
  if (!/^\d{11}$/.test(ruc) || !['10', '15', '16', '17', '20'].includes(ruc.slice(0, 2))) return false;
  const w = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];
  let s = 0;
  for (let i = 0; i < 10; i++) s += Number(ruc[i]) * w[i];
  let c = 11 - (s % 11);
  if (c === 10) c = 0;
  if (c === 11) c = 1;
  return c === Number(ruc[10]);
}

// ---------------------------------------------------------------- persistencia
function cargar() {
  try { return JSON.parse(fs.readFileSync(DATA_FILE, 'utf8')); } catch { return { comprobantes: {} }; }
}
function guardar() {
  try {
    fs.mkdirSync(path.dirname(DATA_FILE), { recursive: true });
    fs.writeFileSync(DATA_FILE, JSON.stringify(registro));
  } catch (e) { console.warn('No se pudo guardar el registro:', e.message); }
}

// ---------------------------------------------------------------- ZIP mínimo (lectura con directorio central, escritura sin compresión)
const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; }
  return t;
})();
function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function unzip(buf) {
  let eocd = -1;
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 65557); i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('ZIP sin directorio central');
  const count = buf.readUInt16LE(eocd + 10);
  let p = buf.readUInt32LE(eocd + 16);
  const files = [];
  for (let n = 0; n < count; n++) {
    if (buf.readUInt32LE(p) !== 0x02014b50) throw new Error('ZIP dañado');
    const method = buf.readUInt16LE(p + 10);
    const csize = buf.readUInt32LE(p + 20);
    const nlen = buf.readUInt16LE(p + 28), elen = buf.readUInt16LE(p + 30), clen = buf.readUInt16LE(p + 32);
    const local = buf.readUInt32LE(p + 42);
    const name = buf.slice(p + 46, p + 46 + nlen).toString('utf8');
    const lnlen = buf.readUInt16LE(local + 26), lelen = buf.readUInt16LE(local + 28);
    const start = local + 30 + lnlen + lelen;
    const raw = buf.slice(start, start + csize);
    const data = method === 0 ? raw : method === 8 ? zlib.inflateRawSync(raw) : null;
    if (!data) throw new Error('Compresión no soportada');
    files.push({ name, data });
    p += 46 + nlen + elen + clen;
  }
  return files;
}
function zip(name, data) {
  const nameBuf = Buffer.from(name, 'utf8');
  const crc = crc32(data);
  const local = Buffer.alloc(30);
  local.writeUInt32LE(0x04034b50, 0); local.writeUInt16LE(20, 4); local.writeUInt16LE(0x0800, 6); local.writeUInt16LE(0, 8);
  local.writeUInt32LE(crc, 14); local.writeUInt32LE(data.length, 18); local.writeUInt32LE(data.length, 22);
  local.writeUInt16LE(nameBuf.length, 26);
  const central = Buffer.alloc(46);
  central.writeUInt32LE(0x02014b50, 0); central.writeUInt16LE(20, 4); central.writeUInt16LE(20, 6); central.writeUInt16LE(0x0800, 8);
  central.writeUInt32LE(crc, 16); central.writeUInt32LE(data.length, 20); central.writeUInt32LE(data.length, 24);
  central.writeUInt16LE(nameBuf.length, 28); central.writeUInt32LE(0, 42);
  const cdOffset = 30 + nameBuf.length + data.length;
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(1, 8); end.writeUInt16LE(1, 10);
  end.writeUInt32LE(46 + nameBuf.length, 12); end.writeUInt32LE(cdOffset, 16);
  return Buffer.concat([local, nameBuf, data, central, nameBuf, end]);
}

// ---------------------------------------------------------------- utilidades XML (simples: el simulador solo lee los nodos que valida)
function tag(xml, name) {
  const m = xml.match(new RegExp(`<(?:[\\w-]+:)?${name}(?:\\s[^>]*)?>([\\s\\S]*?)</(?:[\\w-]+:)?${name}>`));
  return m ? m[1].trim() : null;
}
function block(xml, name) {
  const m = xml.match(new RegExp(`<(?:[\\w-]+:)?${name}(?:\\s[^>]*)?>[\\s\\S]*?</(?:[\\w-]+:)?${name}>`));
  return m ? m[0] : '';
}
function esc(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
const nowParts = () => {
  const d = new Date(Date.now() - 5 * 3600 * 1000); // hora de Lima
  const iso = d.toISOString();
  return { date: iso.slice(0, 10), time: iso.slice(11, 19) };
};

// ---------------------------------------------------------------- respuestas SOAP
function soapEnvelope(body) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<soap-env:Envelope xmlns:soap-env="http://schemas.xmlsoap.org/soap/envelope/">
  <soap-env:Header/>
  <soap-env:Body>${body}</soap-env:Body>
</soap-env:Envelope>`;
}
function fault(code, message) {
  return soapEnvelope(`<soap-env:Fault><faultcode>soap-env:Client.${code}</faultcode><faultstring>${esc(message)}</faultstring></soap-env:Fault>`);
}
function cdrXml({ id, emisor, receptor, code, description, notes, docId }) {
  const { date, time } = nowParts();
  const notesXml = (notes || []).map((n) => `\n  <cbc:Note>${esc(n)}</cbc:Note>`).join('');
  return `<?xml version="1.0" encoding="UTF-8"?>
<ar:ApplicationResponse xmlns:ar="urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2"
    xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
    xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
    xmlns:ext="urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2">
  <ext:UBLExtensions><ext:UBLExtension><ext:ExtensionContent/></ext:UBLExtension></ext:UBLExtensions>
  <cbc:UBLVersionID>2.0</cbc:UBLVersionID>
  <cbc:CustomizationID>1.0</cbc:CustomizationID>
  <cbc:ID>${id}</cbc:ID>
  <cbc:IssueDate>${date}</cbc:IssueDate>
  <cbc:IssueTime>${time}</cbc:IssueTime>
  <cbc:ResponseDate>${date}</cbc:ResponseDate>
  <cbc:ResponseTime>${time}</cbc:ResponseTime>${notesXml}
  <cac:SenderParty><cac:PartyIdentification><cbc:ID>${SUNAT_RUC}</cbc:ID></cac:PartyIdentification></cac:SenderParty>
  <cac:ReceiverParty><cac:PartyIdentification><cbc:ID>${emisor}</cbc:ID></cac:PartyIdentification></cac:ReceiverParty>
  <cac:DocumentResponse>
    <cac:Response><cbc:ReferenceID>${docId}</cbc:ReferenceID><cbc:ResponseCode>${code}</cbc:ResponseCode><cbc:Description>${esc(description)}</cbc:Description></cac:Response>
    <cac:DocumentReference><cbc:ID>${docId}</cbc:ID></cac:DocumentReference>
    <cac:RecipientParty><cac:PartyIdentification><cbc:ID>${esc(receptor)}</cbc:ID></cac:PartyIdentification></cac:RecipientParty>
  </cac:DocumentResponse>
</ar:ApplicationResponse>`;
}

// ---------------------------------------------------------------- validación del comprobante
function validar(fileName, xml) {
  const [ruc, tipo, serie, numero] = fileName.replace(/\.(zip|xml)$/i, '').split('-');
  const docId = `${serie}-${Number(numero)}`;
  const etiqueta = tipo === '01' ? 'Factura' : 'Boleta';

  // Excepciones (0100-1999): el comprobante no se registra.
  if (!xml.includes('<Invoice')) return { exception: ['0306', 'No se puede leer (parsear) el archivo XML'] };
  const xmlId = tag(xml, 'ID');
  const supplier = block(xml, 'AccountingSupplierParty');
  const supplierRuc = tag(supplier, 'ID');
  if (supplierRuc !== ruc) return { exception: ['1034', 'Número de RUC del nombre del archivo no coincide con el consignado en el contenido del archivo XML'] };
  const idSinUbl = (xml.split('</cbc:CustomizationID>')[1] || '');
  const docInXml = tag(idSinUbl, 'ID');
  if (docInXml !== docId) return { exception: ['1035', 'Número de serie del nombre del archivo no coincide con el consignado en el contenido del archivo XML'] };
  if (tag(xml, 'InvoiceTypeCode') !== tipo) return { exception: ['1003', 'El tipo de comprobante del nombre del archivo no coincide con el del XML'] };
  const sig = block(xml, 'Signature');
  const sigOk = /<ds:Signature[\s\S]*<ds:DigestValue>[^<]{20,}<\/ds:DigestValue>[\s\S]*<ds:SignatureValue>[\s\S]{100,}?<\/ds:SignatureValue>[\s\S]*<ds:X509Certificate>/.test(xml);
  if (!sigOk) return { exception: ['2335', 'El documento electrónico ingresado ha sido alterado (no tiene firma digital válida)'] };
  void sig;
  void xmlId;

  // Rechazos (2000-3999): se registra con CDR de rechazo.
  const customer = block(xml, 'AccountingCustomerParty');
  const custIdMatch = customer.match(/schemeID="(\d)"[^>]*>([^<]+)</);
  const custType = custIdMatch ? custIdMatch[1] : '';
  const custDoc = custIdMatch ? custIdMatch[2].trim() : '';
  const reject = (code, msg) => ({ reject: [code, msg], docId, etiqueta, receptor: custDoc });
  if (tipo === '01' && serie[0] !== 'F') return reject('2116', 'El tipo de documento modificado por la factura debe tener serie que empiece con F');
  if (tipo === '03' && serie[0] !== 'B') return reject('2116', 'La serie de la boleta de venta debe empezar con B');
  if (tipo === '01' && custType !== '6') return reject('2800', 'El dato ingresado en el tipo de documento de identidad del receptor no está permitido para facturas');
  if (custType === '6' && !rucValido(custDoc)) return reject('2017', 'El número de documento de identidad del receptor debe ser RUC válido');
  if (custType === '6' && PADRON[custDoc] && PADRON[custDoc].estado !== 'ACTIVO') return reject('2018', `El RUC del receptor no está activo (${PADRON[custDoc].estado})`);
  const payable = Number(tag(block(xml, 'LegalMonetaryTotal'), 'PayableAmount'));
  const lineExt = Number(tag(block(xml, 'LegalMonetaryTotal'), 'LineExtensionAmount'));
  const igv = Number(tag(block(xml, 'TaxTotal'), 'TaxAmount'));
  if (Math.abs(lineExt + igv - payable) > 0.01) return reject('3277', 'El importe total no coincide con la suma del valor de venta más los tributos');
  if (tipo === '03' && custType === '0' && payable > 700) return reject('2023', 'Para boletas mayores a S/ 700.00 se debe consignar el documento del adquirente');

  // Observaciones (4000+): aceptado con notas.
  const notes = [];
  const igvEsperado = Math.round(lineExt * 0.18 * 100) / 100;
  if (Math.abs(igvEsperado - igv) > 0.05) notes.push('4290 - El cálculo del IGV es incorrecto (diferencia por redondeo)');
  if (custType === '6' && !PADRON[custDoc]) notes.push('4093 - El RUC del receptor no se encuentra en el padrón reducido (validado solo por dígito verificador)');

  return { ok: true, docId, etiqueta, receptor: custDoc, notes };
}

// ---------------------------------------------------------------- operaciones SOAP
function autenticar(body) {
  const user = (tag(body, 'Username') || '').toUpperCase();
  const pass = tag(body, 'Password') || '';
  const ruc = user.slice(0, 11);
  if (!/^\d{11}/.test(user) || user.slice(11) !== USUARIO_SOL || pass !== CLAVE_SOL) {
    return { error: fault('0102', 'Usuario o contraseña incorrectos') };
  }
  return { ruc };
}

function sendBill(body) {
  const auth = autenticar(body);
  if (auth.error) return { status: 500, body: auth.error };
  const fileName = tag(body, 'fileName');
  const content = tag(body, 'contentFile');
  if (!fileName || !/^\d{11}-(01|03)-[A-Z0-9]{4}-\d{1,8}\.zip$/i.test(fileName)) {
    return { status: 500, body: fault('0151', 'El nombre del archivo ZIP no cumple el formato RUC-TIPO-SERIE-NUMERO.zip') };
  }
  if (fileName.slice(0, 11) !== auth.ruc) {
    return { status: 500, body: fault('0154', 'El RUC del usuario SOL no coincide con el RUC emisor del archivo') };
  }
  let xml;
  try {
    const files = unzip(Buffer.from(content || '', 'base64'));
    const f = files.find((x) => /\.xml$/i.test(x.name));
    if (!f) return { status: 500, body: fault('0155', 'El archivo ZIP no contiene un XML') };
    if (f.name.replace(/\.xml$/i, '') !== fileName.replace(/\.zip$/i, '')) {
      return { status: 500, body: fault('0157', 'El nombre del XML no coincide con el nombre del ZIP') };
    }
    xml = f.data.toString('utf8');
  } catch (e) {
    return { status: 500, body: fault('0156', 'El archivo ZIP está corrupto: ' + e.message) };
  }
  const key = fileName.replace(/\.zip$/i, '');
  if (registro.comprobantes[key]) {
    return { status: 500, body: fault('1033', 'El comprobante fue registrado previamente con otros datos') };
  }
  const v = validar(fileName, xml);
  if (v.exception) {
    anotar(key, { estado: 'EXCEPCION', codigo: v.exception[0], descripcion: v.exception[1], registrado: false });
    return { status: 500, body: fault(v.exception[0], v.exception[1]) };
  }
  const [code, description] = v.reject
    ? v.reject
    : ['0', `La ${v.etiqueta} numero ${v.docId}, ha sido aceptada`];
  const cdr = cdrXml({ id: String(Date.now()), emisor: auth.ruc, receptor: v.receptor, code, description, notes: v.notes, docId: v.docId });
  const cdrZip = zip(`R-${key}.xml`, Buffer.from(cdr, 'utf8')).toString('base64');
  const estado = v.reject ? 'RECHAZADO' : v.notes && v.notes.length ? 'OBSERVADO' : 'ACEPTADO';
  registro.comprobantes[key] = {
    archivo: key, estado, codigo: code, descripcion: description, notas: v.notes || [],
    recibido: new Date().toISOString(), cdr: cdrZip, total: tag(block(xml, 'LegalMonetaryTotal'), 'PayableAmount'), receptor: v.receptor,
  };
  guardar();
  return {
    status: 200,
    body: soapEnvelope(`<br:sendBillResponse xmlns:br="http://service.sunat.gob.pe"><applicationResponse>${cdrZip}</applicationResponse></br:sendBillResponse>`),
  };
}

function getStatusCdr(body) {
  const auth = autenticar(body);
  if (auth.error) return { status: 500, body: auth.error };
  const ruc = tag(body, 'rucComprobante');
  const tipo = tag(body, 'tipoComprobante');
  const serie = tag(body, 'serieComprobante');
  const numero = tag(body, 'numeroComprobante');
  const key = `${ruc}-${tipo}-${serie}-${Number(numero)}`;
  const c = registro.comprobantes[key];
  const statusXml = c
    ? `<statusCdr><statusCode>0004</statusCode><statusMessage>La constancia existe</statusMessage><content>${c.cdr}</content></statusCdr>`
    : `<statusCdr><statusCode>0127</statusCode><statusMessage>El ticket no existe</statusMessage></statusCdr>`;
  return { status: 200, body: soapEnvelope(`<br:getStatusCdrResponse xmlns:br="http://service.sunat.gob.pe">${statusXml}</br:getStatusCdrResponse>`) };
}

function anotar(key, data) {
  registro.excepciones = registro.excepciones || [];
  registro.excepciones.unshift({ archivo: key, fecha: new Date().toISOString(), ...data });
  registro.excepciones = registro.excepciones.slice(0, 50);
  guardar();
}

// ---------------------------------------------------------------- panel web
function panel() {
  const filas = Object.values(registro.comprobantes).sort((a, b) => b.recibido.localeCompare(a.recibido));
  const color = { ACEPTADO: '#217a4a', OBSERVADO: '#0679ac', RECHAZADO: '#c2410c' };
  const rows = filas.map((c) => `<tr><td>${esc(c.archivo)}</td><td><b style="color:${color[c.estado] || '#333'}">${c.estado}</b></td>
    <td>${esc(c.codigo)}</td><td>${esc(c.descripcion)}${c.notas.length ? '<br><small>' + c.notas.map(esc).join('<br>') + '</small>' : ''}</td>
    <td>${esc(c.receptor || '')}</td><td style="text-align:right">${esc(c.total || '')}</td><td>${new Date(c.recibido).toLocaleString('es-PE', { timeZone: 'America/Lima' })}</td></tr>`).join('');
  const exc = (registro.excepciones || []).slice(0, 10).map((e) => `<tr><td>${esc(e.archivo)}</td><td>${esc(e.codigo)}</td><td>${esc(e.descripcion)}</td><td>${new Date(e.fecha).toLocaleString('es-PE', { timeZone: 'America/Lima' })}</td></tr>`).join('');
  return `<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>SUNAT simulada · Beta</title>
<style>
body{font-family:system-ui,Segoe UI,sans-serif;margin:0;background:#f3f6f9;color:#1b2b3a}
header{background:#c8102e;color:#fff;padding:16px 24px;display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:10px}
header h1{font-size:18px;margin:0}main{padding:20px 24px;max-width:1200px}
.card{background:#fff;border:1px solid #dde3ea;border-radius:10px;margin-bottom:18px;overflow:auto}
.card h2{font-size:15px;margin:0;padding:12px 16px;border-bottom:1px solid #dde3ea}
table{width:100%;border-collapse:collapse;font-size:13px}th,td{padding:8px 12px;border-bottom:1px solid #eef1f4;text-align:left;vertical-align:top}
th{background:#f7f9fb;color:#5b6b7a}.modo button{border:1px solid #fff;background:transparent;color:#fff;padding:6px 12px;border-radius:999px;cursor:pointer;margin-left:6px}
.modo button.on{background:#fff;color:#c8102e;font-weight:700}code{background:#eef1f4;padding:2px 6px;border-radius:4px}
</style></head><body>
<header><h1>SUNAT · Servicio de facturación electrónica (simulado, entorno beta)</h1>
<div class="modo">Estado del servicio:
${['normal', 'lento', 'caido'].map((m) => `<button class="${modo === m ? 'on' : ''}" onclick="fetch('/admin/modo',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({modo:'${m}'})}).then(()=>location.reload())">${m === 'caido' ? 'Caído' : m[0].toUpperCase() + m.slice(1)}</button>`).join('')}
</div></header>
<main>
<div class="card"><h2>Comprobantes registrados (${filas.length})</h2>
<table><thead><tr><th>Archivo</th><th>Estado</th><th>Código</th><th>Respuesta</th><th>Receptor</th><th>Total</th><th>Recibido</th></tr></thead>
<tbody>${rows || '<tr><td colspan="7">Aún no se recibe ningún comprobante.</td></tr>'}</tbody></table></div>
<div class="card"><h2>Excepciones recientes (no registradas)</h2>
<table><thead><tr><th>Archivo</th><th>Código</th><th>Descripción</th><th>Fecha</th></tr></thead>
<tbody>${exc || '<tr><td colspan="4">Sin excepciones.</td></tr>'}</tbody></table></div>
<div class="card"><h2>Servicios</h2><table><tbody>
<tr><td><code>POST /ol-ti-itcpfegem-beta/billService</code></td><td>SOAP <b>sendBill</b> y <b>getStatusCdr</b> · usuario SOL <code>RUC + ${USUARIO_SOL}</code></td></tr>
<tr><td><code>GET /v1/ruc/{ruc}</code></td><td>Consulta RUC (padrón simulado). Prueba: <a href="/v1/ruc/10102984841">10102984841</a>, <a href="/v1/ruc/20100070970">20100070970</a>, <a href="/v1/ruc/20548777217">20548777217</a> (no habido)</td></tr>
</tbody></table></div>
</main></body></html>`;
}

// ---------------------------------------------------------------- servidor
function cors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
}
function send(res, status, body, type) {
  res.writeHead(status, { 'Content-Type': type });
  res.end(body);
}

const server = http.createServer((req, res) => {
  cors(res);
  if (req.method === 'OPTIONS') return send(res, 204, '', 'text/plain');
  const url = new URL(req.url, 'http://localhost');
  let chunks = [];
  req.on('data', (c) => chunks.push(c));
  req.on('end', () => {
    const body = Buffer.concat(chunks).toString('utf8');
    const t0 = Date.now();
    const done = (status, payload, type) => {
      console.log(`${new Date().toISOString()} ${req.method} ${url.pathname} → ${status} (${Date.now() - t0} ms)`);
      send(res, status, payload, type);
    };

    if (url.pathname === '/health') return done(200, '{"status":"UP"}', 'application/json');
    if (url.pathname === '/' && req.method === 'GET') return done(200, panel(), 'text/html; charset=utf-8');
    if (url.pathname === '/admin/modo' && req.method === 'POST') {
      try { const m = JSON.parse(body).modo; if (['normal', 'lento', 'caido'].includes(m)) modo = m; } catch {}
      return done(200, JSON.stringify({ modo }), 'application/json');
    }
    if (url.pathname === '/admin/modo' && req.method === 'GET') return done(200, JSON.stringify({ modo }), 'application/json');

    const m = url.pathname.match(/^\/v1\/ruc\/(\d+)$/);
    if (m && req.method === 'GET') {
      const ruc = m[1];
      if (!rucValido(ruc)) return done(422, JSON.stringify({ error: 'RUC inválido' }), 'application/json; charset=utf-8');
      const d = PADRON[ruc];
      if (!d) return done(404, JSON.stringify({ error: 'RUC no encontrado en el padrón' }), 'application/json; charset=utf-8');
      return done(200, JSON.stringify({ ruc, ...d }), 'application/json; charset=utf-8');
    }

    if (url.pathname === '/ol-ti-itcpfegem-beta/billService' && req.method === 'POST') {
      if (modo === 'caido') return done(503, '<html><body>Servicio no disponible</body></html>', 'text/html');
      const run = () => {
        let r;
        if (/getStatusCdr/.test(body)) r = getStatusCdr(body);
        else if (/sendBill/.test(body)) r = sendBill(body);
        else r = { status: 500, body: fault('0109', 'Operación no soportada') };
        done(r.status, r.body, 'text/xml; charset=utf-8');
      };
      return modo === 'lento' ? setTimeout(run, 8000) : setTimeout(run, 150 + Math.random() * 350);
    }
    done(404, 'No encontrado', 'text/plain');
  });
});

server.listen(PORT, () => console.log(`SUNAT simulada escuchando en :${PORT} · usuario SOL RUC+${USUARIO_SOL}`));
