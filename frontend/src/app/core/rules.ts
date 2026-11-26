/**
 * Reglas de validación compartidas por los formularios. Son las mismas que aplica el backend
 * (PatientValidator, UserService, BillingService, ClinicalService, AgendaService) para que el
 * usuario vea el error antes de enviar.
 */

export const RE = {
  dni: /^\d{8}$/,
  ce: /^[A-Z0-9]{9,12}$/,
  pas: /^[A-Z0-9]{6,12}$/,
  ruc: /^\d{11}$/,
  name: /^\p{L}[\p{L} '.-]{1,79}$/u,
  phone: /^\+?\d{6,12}$/,
  email: /^[^@\s]+@[^@\s]+\.[^@\s]{2,}$/,
  username: /^[A-Za-z0-9._-]{3,40}$/,
  code6: /^\d{6}$/,
  cie10: /^[A-Z]\d{2}(\.[0-9A-Z]{1,2})?$/,
  cop: /^\d{3,6}$/,
};

/** Longitudes máximas de cada campo (coinciden con las columnas de la base de datos). */
export const MAX = {
  firstName: 80, lastName: 100, phone: 13, email: 120, address: 200, district: 80, contactName: 120, notes: 500,
  fullName: 120, username: 40, password: 72, specialty: 80, cop: 6,
  reason: 200, apptNotes: 500,
  customerName: 200, customerAddress: 200, itemDescription: 250,
  historyShort: 500, historyLong: 1000, dxDescription: 500, planTitle: 120, planNotes: 500, evolutionNotes: 1000,
  search: 100,
};

/** Cantidad máxima por línea de comprobante y precio máximo por procedimiento o concepto (S/). */
export const MAX_QTY = 999;
export const MAX_PRICE = 99999.99;

/** Mínimo 8 caracteres (máximo 72), con al menos una letra y un número. */
export function validPassword(p: string): boolean {
  return !!p && p.length >= 8 && p.length <= 72 && /\p{L}/u.test(p) && /\d/.test(p);
}

/** Pieza dental en notación FDI: 11–48 permanentes y 51–85 deciduas. */
export function validTooth(t: number | string | null | undefined): boolean {
  const n = Number(t);
  if (!Number.isInteger(n)) return false;
  const q = Math.floor(n / 10), d = n % 10;
  if (q >= 1 && q <= 4) return d >= 1 && d <= 8;
  if (q >= 5 && q <= 8) return d >= 1 && d <= 5;
  return false;
}

/** Valida un documento de identidad según su tipo (DNI, CE o PAS del paciente). */
export function docError(type: string, raw: string): string {
  const doc = (raw || '').replace(/\s/g, '').toUpperCase();
  if (type === 'DNI' && !RE.dni.test(doc)) return 'El DNI debe tener exactamente 8 dígitos numéricos.';
  if (type === 'CE' && !RE.ce.test(doc)) return 'El carné de extranjería tiene de 9 a 12 letras o números.';
  if (type === 'PAS' && !RE.pas.test(doc)) return 'El pasaporte tiene de 6 a 12 letras o números.';
  return '';
}

export function phoneDigits(p: string): string { return (p || '').replace(/[\s()-]/g, ''); }
