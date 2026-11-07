import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from './models';

export const STATUS_LABEL: Record<string, string> = {
  PROGRAMADA: 'Programada', CONFIRMADA: 'Confirmada', EN_ESPERA: 'En espera', EN_ATENCION: 'En atención',
  ATENDIDA: 'Atendida', CANCELADA: 'Cancelada', NO_ASISTIO: 'No asistió',
};
export const STATUS_TONE: Record<string, string> = {
  PROGRAMADA: 'grey', CONFIRMADA: 'teal', EN_ESPERA: 'amber', EN_ATENCION: 'blue',
  ATENDIDA: 'green', CANCELADA: 'gum', NO_ASISTIO: 'gum',
};
export const INVOICE_LABEL: Record<string, string> = {
  GENERADO: 'Por enviar', ACEPTADO: 'Aceptado', OBSERVADO: 'Aceptado con observaciones', RECHAZADO: 'Rechazado', ERROR: 'Error de envío',
};
export const INVOICE_TONE: Record<string, string> = {
  GENERADO: 'amber', ACEPTADO: 'green', OBSERVADO: 'teal', RECHAZADO: 'gum', ERROR: 'gum',
};
export const ITEM_LABEL: Record<string, string> = { PENDIENTE: 'Pendiente', EN_PROCESO: 'En proceso', REALIZADO: 'Realizado' };
export const ITEM_TONE: Record<string, string> = { PENDIENTE: 'grey', EN_PROCESO: 'amber', REALIZADO: 'green' };

export const DOC_TYPES = [
  { code: 'DNI', label: 'DNI' },
  { code: 'CE', label: 'Carné de extranjería' },
  { code: 'PAS', label: 'Pasaporte' },
];

const money = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN', minimumFractionDigits: 2 });
export function soles(v: number | string | null | undefined): string {
  const n = typeof v === 'string' ? parseFloat(v) : v ?? 0;
  return money.format(isNaN(n as number) ? 0 : (n as number));
}

/** Fecha local en formato yyyy-MM-dd (sin desfase UTC). */
export function isoDate(d: Date = new Date()): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

export function parseLocal(iso: string): Date {
  // "2026-11-05T09:30:00" o "2026-11-05" interpretados como hora local
  const [d, t] = iso.split('T');
  const [y, m, day] = d.split('-').map(Number);
  const [hh, mm, ss] = (t || '00:00:00').split(':').map((x) => parseInt(x, 10) || 0);
  return new Date(y, m - 1, day, hh, mm, ss);
}

export function hhmm(iso: string | null | undefined): string {
  if (!iso) return '';
  const t = iso.includes('T') ? iso.split('T')[1] : iso;
  return t.substring(0, 5);
}

const dayFmt = new Intl.DateTimeFormat('es-PE', { weekday: 'long', day: 'numeric', month: 'long' });
const shortFmt = new Intl.DateTimeFormat('es-PE', { day: '2-digit', month: 'short', year: 'numeric' });
const dateTimeFmt = new Intl.DateTimeFormat('es-PE', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false });

export function longDay(iso: string): string {
  const s = dayFmt.format(parseLocal(iso));
  return s.charAt(0).toUpperCase() + s.slice(1);
}
export function shortDate(iso: string | null | undefined): string { return iso ? shortFmt.format(parseLocal(iso)) : '—'; }
export function dateTime(iso: string | null | undefined): string { return iso ? dateTimeFmt.format(parseLocal(iso)) : '—'; }

export function initials(name: string | null | undefined): string {
  if (!name) return '?';
  const parts = name.replace(/^(Dra?\.)\s+/i, '').trim().split(/\s+/);
  return ((parts[0]?.[0] || '') + (parts[1]?.[0] || '')).toUpperCase();
}

export function apiError(err: unknown): ApiError {
  const e = err as HttpErrorResponse;
  const body = e?.error;
  if (body && typeof body === 'object' && 'message' in body) return body as ApiError;
  return { status: e?.status ?? 0, code: 'DESCONOCIDO', message: 'No se pudo completar la operación.' };
}

/** RUC peruano: 11 dígitos, prefijo válido y dígito verificador (módulo 11). */
export function validRuc(ruc: string): boolean {
  if (!/^\d{11}$/.test(ruc)) return false;
  if (!['10', '15', '16', '17', '20'].includes(ruc.substring(0, 2))) return false;
  const w = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];
  let sum = 0;
  for (let i = 0; i < 10; i++) sum += Number(ruc[i]) * w[i];
  let check = 11 - (sum % 11);
  if (check === 10) check = 0;
  if (check === 11) check = 1;
  return check === Number(ruc[10]);
}

export const CUSTOMER_DOC: Record<string, string> = { '0': 'Sin documento', '1': 'DNI', '4': 'Carné de extranjería', '6': 'RUC', '7': 'Pasaporte' };

export function patientDocToSunat(docType: string): string {
  return ({ DNI: '1', CE: '4', PAS: '7' } as Record<string, string>)[docType] || '1';
}

export function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export const METHOD_LABEL: Record<string, string> = { EFECTIVO: 'Efectivo', TARJETA: 'Tarjeta', YAPE: 'Yape', PLIN: 'Plin', TRANSFERENCIA: 'Transferencia' };
export const CATEGORY_LABEL: Record<string, string> = {
  INSUMOS: 'Insumos', LABORATORIO: 'Laboratorio', ALQUILER: 'Alquiler', SERVICIOS: 'Servicios', PLANILLA: 'Planilla',
  MANTENIMIENTO: 'Mantenimiento', MARKETING: 'Publicidad', IMPUESTOS: 'Impuestos', OTROS: 'Otros',
};
export const NOTIF_TYPE: Record<string, string> = { RECORDATORIO: 'Recordatorio', CAMBIO_CITA: 'Cambio de cita', CANCELACION: 'Cancelación', CODIGO_ACCESO: 'Código de acceso',
  MENSAJE: 'Mensaje', CONTROL: 'Control preventivo', LISTA_ESPERA: 'Lista de espera', CUOTA: 'Cuota por vencer' };
export const NOTIF_STATUS: Record<string, string> = {
  PENDIENTE: 'En cola', PROGRAMADO: 'Programado', ENVIADO: 'Enviado', ENTREGADO: 'Entregado', LEIDO: 'Leído', FALLIDO: 'Fallido', ANULADO: 'Anulado', DUPLICADO: 'Duplicado evitado',
};
export const NOTIF_TONE: Record<string, string> = { PENDIENTE: 'amber', PROGRAMADO: 'blue', ENVIADO: 'green', ENTREGADO: 'green', LEIDO: 'teal', FALLIDO: 'gum', ANULADO: 'grey', DUPLICADO: 'teal' };
export const CHANNEL_LABEL: Record<string, string> = { WHATSAPP: 'WhatsApp', EMAIL: 'Correo', AMBOS: 'WhatsApp y correo', NINGUNO: 'No recibir avisos' };
export const SETTLEMENT_LABEL: Record<string, string> = { PENDIENTE: 'Por pagar', PARCIAL: 'Pago parcial', PAGADA: 'Pagada', ANULADA: 'Anulada' };
export const SETTLEMENT_TONE: Record<string, string> = { PENDIENTE: 'amber', PARCIAL: 'blue', PAGADA: 'green', ANULADA: 'grey' };

/** Períodos rápidos para filtros de reportes. */
export function periodPreset(key: string): { from: string; to: string } {
  const t = new Date();
  const d = (y: number, m: number, day: number) => isoDate(new Date(y, m, day));
  const y = t.getFullYear(), m = t.getMonth();
  switch (key) {
    case 'hoy': return { from: isoDate(t), to: isoDate(t) };
    case 'semana': {
      const dow = (t.getDay() + 6) % 7;
      const s = new Date(t); s.setDate(t.getDate() - dow);
      return { from: isoDate(s), to: isoDate(t) };
    }
    case 'mes': return { from: d(y, m, 1), to: isoDate(t) };
    case 'mes-anterior': return { from: d(y, m - 1, 1), to: d(y, m, 0) };
    case '3-meses': return { from: d(y, m - 2, 1), to: isoDate(t) };
    case 'anio': return { from: d(y, 0, 1), to: isoDate(t) };
    default: { const s = new Date(t); s.setDate(t.getDate() - 29); return { from: isoDate(s), to: isoDate(t) }; }
  }
}
export const PERIODS = [
  { key: 'hoy', label: 'Hoy' }, { key: 'semana', label: 'Esta semana' }, { key: 'mes', label: 'Este mes' },
  { key: 'mes-anterior', label: 'Mes anterior' }, { key: '3-meses', label: 'Últimos 3 meses' }, { key: 'anio', label: 'Este año' },
];

/** Descarga una tabla como CSV (Excel en español usa punto y coma). */
export function downloadCsv(filename: string, header: string[], rows: (string | number | null | undefined)[][]) {
  const cell = (v: unknown) => {
    const s = v === null || v === undefined ? '' : String(v);
    return /[";\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
  };
  const csv = '\ufeff' + [header, ...rows].map((r) => r.map(cell).join(';')).join('\r\n');
  saveBlob(new Blob([csv], { type: 'text/csv;charset=utf-8' }), filename);
}

export const FILE_KIND: Record<string, string> = {
  FOTO: 'Foto', RADIOGRAFIA: 'Radiografía', DOCUMENTO: 'Documento', CONSENTIMIENTO: 'Consentimiento', RECETA: 'Receta', PRESUPUESTO: 'Presupuesto',
};
export function fileSize(b: number): string {
  if (b < 1024) return b + ' B';
  if (b < 1024 * 1024) return (b / 1024).toFixed(0) + ' KB';
  return (b / 1024 / 1024).toFixed(1) + ' MB';
}
