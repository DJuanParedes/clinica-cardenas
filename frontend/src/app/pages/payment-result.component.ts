import { Component, Input } from '@angular/core';
import { LogoComponent } from '../shared/logo.component';

/** Página a la que vuelve Mercado Pago después del pago (pública: el paciente puede no tener sesión aquí). */
@Component({
  selector: 'app-payment-result',
  standalone: true,
  imports: [LogoComponent],
  template: `
    <main class="wrap">
      <section class="card">
        <app-logo [size]="56" />
        @if (ok) {
          <h1>¡Gracias por tu pago!</h1>
          <p>Mercado Pago confirmó la operación{{ payment_id ? ' N.° ' + payment_id : '' }}. En unos segundos aparecerá registrado en la clínica y en tu app.</p>
        } @else if (pending) {
          <h1>Tu pago está en proceso</h1>
          <p>Mercado Pago aún lo está revisando. Te avisaremos cuando se apruebe; no necesitas pagar de nuevo.</p>
        } @else {
          <h1>El pago no se completó</h1>
          <p>No se realizó ningún cobro. Puedes volver a intentarlo desde el mismo link o pagar en la clínica.</p>
        }
        <p class="muted">Ya puedes cerrar esta página y volver a la app de Clínica Dental Cárdenas.</p>
        @if (ref) { <p class="muted small">Referencia {{ ref }}</p> }
      </section>
    </main>
  `,
  styles: [`
    .wrap { min-height: 100vh; display: grid; place-items: center; padding: 20px; background: var(--bg, #f4f8fb); }
    .card { max-width: 440px; width: 100%; background: #fff; border-radius: 22px; padding: 30px 26px; text-align: center; box-shadow: 0 10px 30px rgba(0,0,0,.06); display: flex; flex-direction: column; align-items: center; gap: 10px; }
    h1 { font-size: 22px; margin: 8px 0 0; }
    p { margin: 0; line-height: 1.5; }
  `],
})
export class PaymentResultComponent {
  @Input() status?: string;
  @Input() collection_status?: string;
  @Input() payment_id?: string;
  @Input() ref?: string;
  get st() { return (this.collection_status || this.status || '').toLowerCase(); }
  get ok() { return this.st === 'approved'; }
  get pending() { return this.st === 'pending' || this.st === 'in_process'; }
}
