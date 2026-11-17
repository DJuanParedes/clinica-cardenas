import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Api } from '../core/api.service';
import { dateTime, soles } from '../core/format';

/** HU-22 (complemento de HU-14): el paciente ve su saldo y sus pagos. */
@Component({
  selector: 'app-portal-account',
  standalone: true,
  template: `
    @if (!a) {
      <div class="skeleton" style="height: 200px; border-radius: 20px"></div>
    } @else {
      <section class="bal" [class.ok]="a.balance <= 0">
        <div class="lbl">{{ a.balance > 0 ? 'Saldo pendiente' : 'Estás al día' }}</div>
        <div class="v tabular">{{ money(a.balance > 0 ? a.balance : 0) }}</div>
        @if (a.inFavor > 0) { <div class="sub">Tienes {{ money(a.inFavor) }} a favor</div> }
        <div class="row2"><div><span>Tratamientos</span><strong class="tabular">{{ money(a.charges) }}</strong></div><div><span>Pagado</span><strong class="tabular">{{ money(a.credits) }}</strong></div></div>
      </section>
      <h3>Movimientos</h3>
      <ul class="list">
        @for (m of a.movements; track $index) {
          <li>
            <div class="grow"><div class="strong">{{ m.concept }}</div><div class="muted small">{{ dt(m.date) }} · {{ m.detail }}</div></div>
            <span class="tabular strong" [class.c]="m.charge > 0" [class.p]="m.credit > 0">{{ m.charge > 0 ? money(m.charge) : '− ' + money(m.credit) }}</span>
          </li>
        } @empty { <li class="muted small">Aún no hay movimientos.</li> }
      </ul>
      @if (a.balance > 0 && online) {
        <button class="pay" type="button" (click)="payOnline()" [disabled]="paying">{{ paying ? 'Abriendo Mercado Pago…' : 'Pagar ' + money(a.balance) + ' con tarjeta o Yape' }}</button>
        @if (charge && charge.status === 'PENDIENTE') { <p class="muted small foot">Cuando termines de pagar en Mercado Pago vuelve aquí: el pago se refleja solo.</p> }
        @if (msg) { <p class="small foot okmsg">{{ msg }}</p> }
        @if (online.testMode) { <p class="muted small foot">Modo de pruebas: no se cobra dinero real.</p> }
      }
      <p class="muted small foot">También puedes pagar en la clínica en efectivo, tarjeta, Yape o Plin.</p>
    }
  `,
  styles: [`
    .bal { background: #fff; border: 1px solid var(--line); border-top: 5px solid var(--orange); border-radius: 20px; padding: 18px 20px; }
    .bal.ok { border-top-color: var(--green); }
    .lbl { font-weight: 800; color: var(--ink-2); font-size: 13px; }
    .v { font-size: 36px; font-weight: 900; color: var(--ink); }
    .sub { color: var(--green); font-weight: 700; font-size: 13px; }
    .row2 { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 12px; }
    .row2 div { background: var(--surface-2); border-radius: 12px; padding: 8px 12px; display: flex; flex-direction: column; font-size: 12px; color: var(--muted); }
    .row2 strong { color: var(--ink); font-size: 15px; }
    h3 { font-size: 15px; margin: 18px 4px 8px; }
    .list { list-style: none; margin: 0; padding: 0; background: #fff; border: 1px solid var(--line); border-radius: 18px; overflow: hidden; }
    .list li { display: flex; align-items: center; gap: 10px; padding: 12px 14px; border-bottom: 1px solid var(--line); }
    .list li:last-child { border-bottom: 0; }
    .c { color: var(--ink); }
    .p { color: var(--green); }
    .foot { text-align: center; margin-top: 14px; }
    .pay { display: block; width: 100%; margin-top: 16px; height: 50px; border: 0; border-radius: 14px; background: var(--teal); color: #fff; font: inherit; font-weight: 800; font-size: 15px; cursor: pointer; }
    .pay:disabled { opacity: .6; }
    .okmsg { color: var(--green); font-weight: 700; }
  `],
})
export class PortalAccountComponent implements OnInit, OnDestroy {
  private api = inject(Api);
  a: any = null;
  online: any = null;
  charge: any = null;
  paying = false;
  msg = '';
  private poll: ReturnType<typeof setInterval> | null = null;
  ngOnInit() {
    this.load();
    this.api.portalOnlineConfig().subscribe({ next: (c) => (this.online = c.enabled ? c : null), error: () => {} });
  }
  ngOnDestroy() { if (this.poll) clearInterval(this.poll); }
  load() { this.api.portalAccount().subscribe((a) => (this.a = a)); }
  payOnline() {
    this.paying = true;
    this.msg = '';
    const win = window.open('', '_blank');
    this.api.portalOnlineCreate(this.a.balance).subscribe({
      next: (c) => {
        this.paying = false;
        this.charge = c;
        if (win && c.checkoutUrl) win.location.href = c.checkoutUrl; else if (c.checkoutUrl) window.location.href = c.checkoutUrl;
        if (this.poll) clearInterval(this.poll);
        this.poll = setInterval(() => this.api.portalOnlineStatus(c.id).subscribe((s) => {
          this.charge = s;
          if (s.status !== 'PENDIENTE') {
            if (this.poll) clearInterval(this.poll);
            if (s.status === 'APROBADO') { this.msg = 'Pago aprobado. ¡Gracias!'; this.load(); }
          }
        }), 4000);
      },
      error: (e) => { this.paying = false; win?.close(); this.msg = e?.error?.message || 'No se pudo iniciar el pago.'; },
    });
  }
  money = soles;
  dt = dateTime;
}
