import { Component, Input, OnChanges, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Account, Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ITEM_LABEL, ITEM_TONE, METHOD_LABEL, dateTime, shortDate, soles } from '../core/format';
import { IconComponent } from '../shared/icon.component';

/** HU-14: saldo pendiente del paciente con el detalle de cargos y pagos. */
@Component({
  selector: 'app-account-tab',
  standalone: true,
  imports: [RouterLink, IconComponent],
  template: `
    @if (!a) {
      <div class="skeleton" style="height: 300px"></div>
    } @else {
      <section class="figures panel">
        <div class="fig"><span class="l">Saldo pendiente</span><span class="v tabular" [class.owe]="a.balance > 0">{{ money(a.balance) }}</span>
          <span class="meta">@if (a.inFavor > 0) { Saldo a favor {{ money(a.inFavor) }} } @else { por cobrar al paciente }</span></div>
        <div class="fig"><span class="l">Tratamientos realizados</span><span class="v tabular">{{ money(a.charges) }}</span><span class="meta">cargos acumulados</span></div>
        <div class="fig"><span class="l">Pagado</span><span class="v tabular paid">{{ money(a.credits) }}</span><span class="meta">{{ validPayments }} pagos{{ a.unapplied > 0 ? ' · ' + money(a.unapplied) + ' a cuenta' : '' }}</span></div>
        @if (auth.can('CAJA_COBRAR')) {
          <div class="fig act"><a class="btn primary" routerLink="/caja/cobro" [queryParams]="{ paciente: a.patient.id }"><app-icon name="wallet" [size]="16" /> Registrar cobro</a></div>
        }
      </section>

      <div class="two">
        <section class="panel">
          <div class="panel-head"><h3>Obligaciones</h3><span class="muted small">procedimientos con su saldo</span></div>
          <table class="table">
            <thead><tr><th>Procedimiento</th><th class="right">Precio</th><th class="right">Pendiente</th><th></th></tr></thead>
            <tbody>
              @for (o of a.obligations; track o.treatmentItemId) {
                <tr>
                  <td><div class="strong">{{ o.procedure }}{{ o.tooth ? ' · pieza ' + o.tooth : '' }}</div>
                    <div class="muted small"><span class="tag" [class]="'tag ' + itemTone(o.status)">{{ itemLabel(o.status) }}</span> {{ o.doneAt ? date(o.doneAt) : o.planTitle }}</div></td>
                  <td class="right tabular">{{ money(o.price) }}</td>
                  <td class="right tabular strong" [class.owe]="o.pending > 0 && o.status === 'REALIZADO'">{{ o.pending > 0 ? money(o.pending) : 'Pagado' }}</td>
                  <td class="right">
                    @if (o.pending > 0 && auth.can('CAJA_COBRAR')) {
                      <a class="btn sm" routerLink="/caja/cobro" [queryParams]="{ paciente: a.patient.id, item: o.treatmentItemId }">Cobrar</a>
                    }
                  </td>
                </tr>
              } @empty { <tr><td colspan="4" class="muted">Sin procedimientos registrados.</td></tr> }
            </tbody>
          </table>
        </section>

        <section class="panel">
          <div class="panel-head"><h3>Estado de cuenta</h3><span class="muted small">cargos y abonos</span></div>
          <table class="table">
            <thead><tr><th>Fecha</th><th>Movimiento</th><th class="right">Cargo</th><th class="right">Abono</th><th class="right">Saldo</th></tr></thead>
            <tbody>
              @for (m of a.movements; track $index) {
                <tr>
                  <td class="tabular nowrap muted">{{ dt(m.date) }}</td>
                  <td><div class="strong">{{ m.concept }}</div><div class="muted small">{{ m.detail }}</div></td>
                  <td class="right tabular">{{ m.charge > 0 ? money(m.charge) : '' }}</td>
                  <td class="right tabular paid">{{ m.credit > 0 ? money(m.credit) : '' }}</td>
                  <td class="right tabular strong">{{ money(m.balance) }}</td>
                </tr>
              } @empty { <tr><td colspan="5" class="muted">Sin movimientos.</td></tr> }
            </tbody>
          </table>
          @if (voided.length) {
            <div class="panel-foot small muted">
              Pagos anulados: @for (p of voided; track p.id) { <span>{{ money(p.amount) }} ({{ p.voidReason }}){{ $last ? '' : ', ' }}</span> }
            </div>
          }
        </section>
      </div>
    }
  `,
  styles: [`
    .figures { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin-bottom: 18px; }
    .fig { display: flex; flex-direction: column; padding: 16px 20px; border-right: 1px solid var(--line); }
    .fig:last-child { border-right: 0; }
    .fig.act { justify-content: center; align-items: flex-start; }
    .fig .v { font-size: var(--step-2); font-weight: 800; line-height: 1.15; }
    .fig .l { font-weight: 700; color: var(--ink-2); font-size: var(--step--1); }
    .fig .meta { font-size: 12px; color: var(--muted); margin-top: 6px; }
    .owe { color: var(--gum); }
    .paid { color: var(--green); }
    .two { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr); gap: 18px; align-items: start; }
    .nowrap { white-space: nowrap; }
    @media (max-width: 1000px) { .two { grid-template-columns: 1fr; } .figures { grid-template-columns: 1fr 1fr; } }
  `],
})
export class AccountTabComponent implements OnChanges {
  private api = inject(Api);
  auth = inject(AuthService);
  @Input() patientId!: number;
  a: Account | null = null;

  ngOnChanges() { this.api.account(this.patientId).subscribe((a) => (this.a = a)); }

  get validPayments() { return (this.a?.payments || []).filter((p) => p.status === 'VALIDO').length; }
  get voided() { return (this.a?.payments || []).filter((p) => p.status === 'ANULADO'); }

  money = soles;
  dt = dateTime;
  date = shortDate;
  method(m: string) { return METHOD_LABEL[m] || m; }
  itemLabel(s: string) { return ITEM_LABEL[s] || s; }
  itemTone(s: string) { return ITEM_TONE[s] || 'grey'; }
}
