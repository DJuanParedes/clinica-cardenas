import { Component, OnInit, inject } from '@angular/core';
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
      <p class="muted small foot">Puedes pagar en la clínica en efectivo, tarjeta, Yape o Plin.</p>
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
  `],
})
export class PortalAccountComponent implements OnInit {
  private api = inject(Api);
  a: any = null;
  ngOnInit() { this.api.portalAccount().subscribe((a) => (this.a = a)); }
  money = soles;
  dt = dateTime;
}
