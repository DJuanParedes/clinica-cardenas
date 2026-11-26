import { Directive, ElementRef, HostListener, Input, inject } from '@angular/core';

/**
 * Tipos de dato que acepta una casilla.
 *  - digits:   solo números (DNI, RUC, código de 6 dígitos, pieza dental, colegiatura, cantidad)
 *  - letters:  letras con tildes y ñ, espacio, apóstrofo, punto y guion (nombres y apellidos)
 *  - alnum:    letras y números en mayúscula, sin espacios (carné de extranjería, pasaporte)
 *  - phone:    números y un "+" opcional al inicio
 *  - decimal:  importe con punto decimal y hasta 2 decimales
 *  - cie10:    código CIE-10 con forma K02 o K02.1
 *  - username: letras, números, punto, guion y guion bajo
 *  - email:    correo sin espacios
 *  - nospace:  cualquier carácter excepto espacios (usuario o correo)
 *  - search:   letras, números, espacio, apóstrofo, punto y guion (búsqueda por documento o nombre)
 *  - text:     texto libre; solo quita espacios al inicio
 */
export type OnlyRule = 'digits' | 'letters' | 'alnum' | 'phone' | 'decimal' | 'cie10' | 'username' | 'email' | 'nospace' | 'search' | 'text';

export function sanitize(rule: OnlyRule, v: string, maxInt = 8): string {
  if (!v) return '';
  switch (rule) {
    case 'digits':
      return v.replace(/\D/g, '');
    case 'letters':
      return v.replace(/[^\p{L} '.-]/gu, '').replace(/^\s+/, '').replace(/\s{2,}/g, ' ');
    case 'alnum':
      return v.toUpperCase().replace(/[^A-Z0-9]/g, '');
    case 'phone': {
      const plus = v.trimStart().startsWith('+');
      return (plus ? '+' : '') + v.replace(/\D/g, '');
    }
    case 'decimal': {
      const s = v.replace(/,/g, '.').replace(/[^\d.]/g, '');
      const dot = s.indexOf('.');
      let int = dot === -1 ? s : s.slice(0, dot);
      int = int.replace(/^0+(?=\d)/, '').slice(0, maxInt);
      if (dot === -1) return int;
      const dec = s.slice(dot + 1).replace(/\./g, '').slice(0, 2);
      return (int || '0') + '.' + dec;
    }
    case 'cie10': {
      const s = v.toUpperCase().replace(/[^A-Z0-9]/g, '');
      let out = '';
      for (const ch of s) {
        const i = out.replace('.', '').length;
        if (i === 0) { if (/[A-Z]/.test(ch)) out += ch; }
        else if (i < 3) { if (/\d/.test(ch)) out += ch; }
        else if (i < 5) { if (/[0-9A-Z]/.test(ch)) out += (i === 3 ? '.' : '') + ch; }
      }
      return out;
    }
    case 'search':
      return v.replace(/[^\p{L}\d '.-]/gu, '').replace(/^\s+/, '').replace(/\s{2,}/g, ' ');
    case 'username':
      return v.replace(/[^A-Za-z0-9._-]/g, '');
    case 'email':
    case 'nospace':
      return v.replace(/\s/g, '');
    default:
      return v.replace(/^\s+/, '');
  }
}

/**
 * Restringe lo que se puede escribir o pegar en un input/textarea.
 * Uso: <input appOnly="digits" maxlength="8" [(ngModel)]="dni" />
 * Funciona con teclado, pegado, autocompletado y teclados móviles: si el valor trae caracteres
 * no permitidos los quita y vuelve a notificar a ngModel con el valor limpio.
 */
@Directive({ selector: 'input[appOnly], textarea[appOnly]', standalone: true })
export class OnlyDirective {
  private el = inject<ElementRef<HTMLInputElement | HTMLTextAreaElement>>(ElementRef).nativeElement;
  private composing = false;

  @Input('appOnly') rule: OnlyRule | '' = 'text';
  /** Para "decimal": máximo de dígitos en la parte entera. */
  @Input() maxInt = 8;

  @HostListener('compositionstart') onCompStart() { this.composing = true; }
  @HostListener('compositionend') onCompEnd() { this.composing = false; this.apply(); }
  @HostListener('input') onInput() { if (!this.composing) this.apply(); }
  @HostListener('blur') onBlur() {
    // Quita espacios al final al salir del campo
    const t = this.el.value.replace(/\s+$/, '');
    if (t !== this.el.value) this.write(t, t.length);
  }

  private apply() {
    const raw = this.el.value;
    const rule = (this.rule || 'text') as OnlyRule;
    let clean = sanitize(rule, raw, this.maxInt);
    const max = this.el.maxLength;
    if (max > 0 && clean.length > max) clean = clean.slice(0, max);
    if (clean === raw) return;
    let caret = clean.length;
    try {
      const pos = this.el.selectionStart ?? raw.length;
      caret = Math.min(sanitize(rule, raw.slice(0, pos), this.maxInt).length, clean.length);
    } catch { /* tipos sin selección */ }
    this.write(clean, caret);
  }

  private write(value: string, caret: number) {
    this.el.value = value;
    try { this.el.setSelectionRange(caret, caret); } catch { /* type=email no admite selección */ }
    // Notifica a ngModel con el valor ya limpio
    this.el.dispatchEvent(new Event('input', { bubbles: true }));
  }
}
