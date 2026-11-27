/**
 * Documentos imprimibles con membrete de la clínica (logo, RUC, dirección) y pie de página.
 * Se abren en una ventana nueva lista para imprimir o guardar como PDF.
 */
export const CLINIC = {
  name: 'Clínica Dental Cárdenas',
  ruc: '10102984841',
  address: 'Urb. La Floresta, Chaclacayo, Lima',
  city: 'Chaclacayo',
};

export const esc = (v: unknown) =>
  String(v ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

/** Convierte un texto legal (párrafos separados por línea en blanco) en HTML. */
export const paragraphs = (text: string) =>
  esc(text).split(/\n{2,}/).map((x) => '<p>' + x.replace(/\n/g, '<br>') + '</p>').join('');

export function openPrintWindow(): Window | null {
  const w = window.open('', '_blank');
  w?.document.write('<p style="font-family:sans-serif">Preparando el documento…</p>');
  return w;
}

/** Escribe el documento con membrete en la ventana y lanza la impresión. */
export function writePrintDoc(w: Window, o: { title: string; subtitle?: string; code?: string; body: string }) {
  const now = new Date().toLocaleString('es-PE', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  const logo = location.origin + '/favicon.svg';
  w.document.open();
  w.document.write(`<!doctype html><html lang="es"><head><meta charset="utf-8"><title>${esc(o.title)}</title>
<style>
@page{size:A4;margin:16mm 16mm 18mm}
*{box-sizing:border-box}
body{font-family:'Segoe UI',Roboto,Arial,sans-serif;color:#1d2b33;max-width:760px;margin:24px auto;padding:0 24px;font-size:12.5px;line-height:1.55}
.head{display:flex;align-items:center;gap:14px;border-bottom:3px solid #00B0E0;padding-bottom:10px}
.head img{width:58px;height:58px}
.head .n{font-size:18px;font-weight:700;color:#0b5e7a;letter-spacing:.3px}
.head .d{font-size:11px;color:#5b6b73}
.head .code{margin-left:auto;text-align:right;font-size:10.5px;color:#5b6b73;border-left:1px solid #d5dde1;padding-left:12px}
.bar{height:3px;background:#F89818;width:120px;margin:0 0 14px}
h1{font-size:16px;text-align:center;margin:18px 0 2px;text-transform:uppercase;letter-spacing:.6px;color:#0b5e7a}
.sub{text-align:center;color:#5b6b73;font-size:11px;margin-bottom:12px}
h2{font-size:12.5px;margin:16px 0 6px;padding:4px 8px;background:#eef7fb;border-left:4px solid #00B0E0;text-transform:uppercase;letter-spacing:.4px}
p{margin:6px 0;text-align:justify}
table{width:100%;border-collapse:collapse;margin:6px 0}
td{border:1px solid #c9d3d8;padding:5px 8px;font-size:12px;vertical-align:top}td.k{width:30%;background:#f4f8fa;font-weight:600}
.firmas{display:flex;gap:28px;margin-top:60px;align-items:flex-end;page-break-inside:avoid}
.f{flex:1;text-align:center;border-top:1px solid #333;padding-top:6px;font-size:11px}
.huella{width:86px;height:104px;border:1px solid #333;text-align:center;font-size:10px;padding-top:86px}
.foot{margin-top:28px;border-top:1px solid #d5dde1;padding-top:6px;font-size:9.5px;color:#7a8a92;display:flex;justify-content:space-between}
.btn{position:fixed;top:12px;right:12px;padding:8px 14px;font-size:14px;background:#0b5e7a;color:#fff;border:0;border-radius:6px;cursor:pointer}
@media print{.btn{display:none}body{margin:0 auto;padding:0}}
</style></head><body>
<button class="btn" onclick="window.print()">Imprimir</button>
<div class="head"><img src="${logo}" alt="">
  <div><div class="n">${esc(CLINIC.name.toUpperCase())}</div>
  <div class="d">RUC ${CLINIC.ruc} · ${esc(CLINIC.address)}</div></div>
  ${o.code ? `<div class="code">${o.code}</div>` : ''}</div>
<div class="bar"></div>
<h1>${esc(o.title)}</h1>
${o.subtitle ? `<div class="sub">${o.subtitle}</div>` : ''}
${o.body}
<div class="foot"><span>${esc(CLINIC.name)} · Documento generado el ${now}</span><span>Conserve una copia firmada en la historia clínica</span></div>
<script>var i=document.querySelector('.head img');function p(){setTimeout(function(){window.print()},300)}if(i.complete)p();else{i.onload=p;i.onerror=p}</script>
</body></html>`);
  w.document.close();
}
