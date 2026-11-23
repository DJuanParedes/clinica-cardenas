import { Component, Input, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ToastService } from '../core/toast.service';
import { apiError, dateTime, isoDate, longDay, saveBlob, soles } from '../core/format';
import { IconComponent } from '../shared/icon.component';

/**
 * v2.10: cuadre y cierre de caja. El sistema calcula lo esperado por medio de pago; el cajero cuenta el efectivo
 * (con ayuda de billetes y monedas) y anota lo que muestran Yape, Plin, el POS y el banco. Con diferencia, se explica.
 */
@Component({
  selector: 'app-cash-closing',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent],
  template: `
    <div class="page">
      <a class="back" routerLink="/caja"><app-icon name="chevronLeft" [size]="16" /> Caja del día</a>
      <div class="page-head">
        <div>
          <h1>Cuadre y cierre de caja</h1>
          <p class="sub">{{ dayLabel }}. Cuenta el efectivo, revisa Yape, Plin, POS y banco, y cierra el día.</p>
        </div>
        <div class="actions">
          <input class="input" type="date" [max]="todayIso" [(ngModel)]="day" (ngModelChange)="load()" aria-label="Día a cuadrar" />
        </div>
      </div>

      @if (s?.pendingDays?.length) {
        <div class="notice amber" role="status"><app-icon name="alert" />
          <span>Hay días anteriores con movimientos sin cerrar:
            @for (d of s.pendingDays; track d) { <button type="button" class="link" (click)="day = d; load()">{{ short(d) }}</button>{{ $last ? '' : ', ' }} }
          </span></div>
      }

      @if (!s) { <div class="skeleton" style="height: 360px"></div> }
      @else if (closed) {
        <section class="panel">
          <div class="panel-head spread">
            <h3>Caja cerrada <span class="tag green">Cerrada</span></h3>
            <div class="row">
              <button class="btn sm" type="button" (click)="pdf()"><app-icon name="download" [size]="14" /> PDF para firmar</button>
              @if (auth.can('CAJA_REABRIR')) { <button class="btn sm ghost" type="button" (click)="reopenOpen = !reopenOpen">Reabrir</button> }
            </div>
          </div>
          <div class="panel-body stack">
            <p class="muted small">Cerrada por {{ c.closedBy }} el {{ dt(c.closedAt) }}.
              @if (s.paymentsAfterClose) { <b class="gum">Ojo: hay {{ s.paymentsAfterClose }} cobro(s) en línea registrados después del cierre.</b> }</p>
            <table class="table keep sum">
              <thead><tr><th>Medio</th><th class="right">Esperado</th><th class="right">Contado / declarado</th><th class="right">Diferencia</th></tr></thead>
              <tbody>
                @for (l of c.methods; track l.method) {
                  @if (+l.expected || +l.declared) {
                    <tr><td>{{ l.label }}</td><td class="right tabular">{{ money(l.expected) }}</td><td class="right tabular">{{ money(l.declared) }}</td>
                      <td class="right tabular" [class.gum]="+l.difference < 0" [class.green-ink]="+l.difference > 0">{{ signed(l.difference) }}</td></tr>
                  }
                }
              </tbody>
              <tfoot><tr><td>Total</td><td></td><td></td><td class="right tabular strong" [class.gum]="+c.difference < 0" [class.green-ink]="+c.difference > 0">{{ signed(c.difference) }}</td></tr></tfoot>
            </table>
            <div class="kpis">
              <div><span>Fondo inicial</span><b>{{ money(c.openingFloat) }}</b></div>
              <div><span>Cobros ({{ c.paymentsCount }})</span><b>{{ money(c.income) }}</b></div>
              <div><span>Egresos</span><b>{{ money(c.outgo) }}</b></div>
              <div><span>Queda de fondo</span><b>{{ money(c.nextFloat) }}</b></div>
              <div><span>Efectivo a depositar</span><b>{{ money(c.deposit) }}</b></div>
            </div>
            @if (c.notes) { <p class="small"><b>Observaciones:</b> {{ c.notes }}</p> }
            @if (reopenOpen) {
              <div class="reopen">
                <label>Motivo de la reapertura<input class="input" [(ngModel)]="reopenReason" placeholder="Ej.: faltó registrar un cobro en efectivo" /></label>
                <button class="btn" type="button" (click)="reopen()">Reabrir caja</button>
              </div>
            }
          </div>
        </section>
      } @else {
        @if (c?.status === 'REABIERTA') {
          <div class="notice amber"><span>Esta caja se reabrió ({{ c.reopenedBy }}: {{ c.reopenReason }}). Vuelve a contarla y ciérrala.</span></div>
        }
        <div class="layout">
          <section class="panel">
            <div class="panel-head"><h3><span class="n">1</span> Efectivo</h3></div>
            <div class="panel-body stack">
              <div class="grid cols-2">
                <label class="field">Fondo inicial (sencillo con que se abrió)<input class="input tabular" type="number" min="0" step="0.1" [(ngModel)]="openingFloat" /></label>
                <div class="field"><span class="label">Efectivo esperado</span><b class="big tabular">{{ money(expectedCash) }}</b>
                  <span class="muted small">fondo {{ money(openingFloat) }} + cobros {{ money(line('EFECTIVO')?.income) }} − egresos {{ money(line('EFECTIVO')?.outgo) }}</span></div>
              </div>
              <details [open]="useDenoms" (toggle)="useDenoms = $any($event.target).open">
                <summary>Contar por billetes y monedas</summary>
                <div class="denoms">
                  @for (d of s.denominations; track d) {
                    <label><span>{{ d >= 10 ? 'Billete' : 'Moneda' }} S/ {{ d }}</span>
                      <input class="input tabular" type="number" min="0" step="1" [(ngModel)]="den[d]" (ngModelChange)="sumDenoms()" [attr.aria-label]="'Cantidad de S/ ' + d" /></label>
                  }
                </div>
              </details>
              <div class="grid cols-2">
                <label class="field">Efectivo contado<input class="input tabular big" type="number" min="0" step="0.1" [(ngModel)]="counted" [readonly]="useDenoms && denTotal > 0" />
                  @if (e['countedCash']) { <span class="err">{{ e['countedCash'] }}</span> }</label>
                <div class="field"><span class="label">Diferencia en efectivo</span>
                  <b class="big tabular" [class.gum]="cashDiff < 0" [class.green-ink]="cashDiff > 0">{{ counted === null ? '—' : signed(cashDiff) }}</b>
                  <span class="muted small">{{ counted === null ? '' : cashDiff === 0 ? 'Cuadra' : cashDiff > 0 ? 'Sobrante' : 'Faltante' }}</span></div>
              </div>
            </div>
          </section>

          <section class="panel">
            <div class="panel-head"><h3><span class="n">2</span> Otros medios</h3></div>
            <div class="panel-body stack">
              <p class="muted small">Revisa el total del día en la app de Yape/Plin, el cierre del POS y el banco. Si coincide, déjalo como está.</p>
              @for (l of others; track l.method) {
                <div class="om">
                  <span><b>{{ l.label }}</b><small class="muted">esperado {{ money(l.expected) }}</small></span>
                  <input class="input tabular" type="number" min="0" step="0.1" [(ngModel)]="declared[l.method]" [attr.aria-label]="'Total ' + l.label" />
                  <span class="tabular" [class.gum]="diffOf(l) < 0" [class.green-ink]="diffOf(l) > 0">{{ signed(diffOf(l)) }}</span>
                </div>
              }
            </div>
          </section>

          <section class="panel span">
            <div class="panel-head"><h3><span class="n">3</span> Cerrar</h3></div>
            <div class="panel-body stack">
              <div class="kpis">
                <div><span>Cobros del día ({{ s.incomeCount }})</span><b>{{ money(s.income) }}</b></div>
                <div><span>Egresos ({{ s.outgoCount }})</span><b>{{ money(s.outgo) }}</b></div>
                <div><span>Diferencia total</span><b [class.gum]="totalDiff < 0" [class.green-ink]="totalDiff > 0">{{ signed(totalDiff) }}</b></div>
              </div>
              <div class="grid cols-2">
                <label class="field">Dejar de fondo para mañana<input class="input tabular" type="number" min="0" step="0.1" [(ngModel)]="nextFloat" />
                  @if (e['nextFloat']) { <span class="err">{{ e['nextFloat'] }}</span> }
                  <span class="muted small">Se deposita o retira: {{ money(deposit) }}</span></label>
                <label class="field">Observaciones @if (totalDiff !== 0) { <span class="req-mark">(obligatorio si hay diferencia)</span> }
                  <input class="input" [(ngModel)]="notes" maxlength="500" placeholder="Ej.: faltan S/ 2.00 de vuelto" />
                  @if (e['notes']) { <span class="err">{{ e['notes'] }}</span> }</label>
              </div>
              @if (error) { <div class="err">{{ error }}</div> }
              <div class="row end">
                <button class="btn primary lg" type="button" [disabled]="busy || counted === null || !auth.can('CAJA_CIERRE')" (click)="close()">
                  <app-icon name="check" [size]="16" /> Cerrar caja del {{ short(day) }}</button>
              </div>
              <p class="muted small">Después del cierre no se pueden registrar cobros ni egresos manuales de este día; solo el administrador puede reabrirla.</p>
            </div>
          </section>
        </div>
      }
    </div>
  `,
  styles: [`
    .layout { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr); gap: 16px; align-items: start; }
    .layout .span { grid-column: 1 / -1; }
    @media (max-width: 980px) { .layout { grid-template-columns: minmax(0, 1fr); } }
    .n { display: inline-grid; place-items: center; width: 22px; height: 22px; border-radius: 50%; background: var(--night, #12333a); color: #fff; font-size: 12px; margin-right: 6px; }
    .field { display: grid; gap: 4px; font-size: 13.5px; }
    .big { font-size: 22px; }
    input.big { font-size: 20px; font-weight: 700; }
    .denoms { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 8px; margin-top: 10px; }
    .denoms label { display: grid; gap: 3px; font-size: 12.5px; color: var(--muted); }
    details summary { cursor: pointer; font-weight: 700; color: var(--teal-strong); }
    .om { display: grid; grid-template-columns: minmax(0, 1fr) 130px 90px; gap: 10px; align-items: center; padding: 6px 0; border-bottom: 1px solid var(--line); }
    .om span small { display: block; }
    .om > span:last-child { text-align: right; }
    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px; }
    .kpis > div { background: #f6fafb; border-radius: 12px; padding: 10px 12px; display: grid; gap: 2px; }
    .kpis span { color: var(--muted); font-size: 12.5px; }
    .kpis b { font-size: 18px; }
    .gum { color: var(--gum); } .green-ink { color: #1f8a55; }
    .row.end { justify-content: flex-end; }
    .req-mark { color: var(--gum); font-size: 12px; }
    .err { color: var(--gum); font-size: 13px; }
    .sum tfoot td { border-top: 2px solid var(--line); font-weight: 700; padding: 10px; }
    .reopen { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 10px; align-items: end; }
    .reopen label { display: grid; gap: 4px; font-size: 13px; }
    .link { background: none; border: 0; color: var(--teal-strong); font: inherit; font-weight: 700; cursor: pointer; padding: 0; text-decoration: underline; }
    @media (max-width: 520px) { .om { grid-template-columns: minmax(0, 1fr) 110px; } .om > span:last-child { grid-column: 2; } }
  `],
})
export class CashClosingComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  auth = inject(AuthService);
  @Input() fecha?: string;
  todayIso = isoDate();
  day = this.todayIso;
  s: any = null;
  openingFloat = 0;
  counted: number | null = null;
  declared: Record<string, number> = {};
  den: Record<string, number> = {};
  denTotal = 0;
  useDenoms = false;
  nextFloat = 0;
  notes = '';
  e: Record<string, string> = {};
  error = '';
  busy = false;
  reopenOpen = false;
  reopenReason = '';

  ngOnInit() {
    if (this.fecha && /^\d{4}-\d{2}-\d{2}$/.test(this.fecha) && this.fecha <= this.todayIso) this.day = this.fecha;
    this.load();
  }

  load() {
    this.s = null; this.e = {}; this.error = '';
    this.api.cashClosing(this.day).subscribe({
      next: (s) => {
        this.s = s;
        this.openingFloat = Number(s.openingFloat);
        this.nextFloat = Number(s.closing?.nextFloat ?? s.openingFloat);
        this.declared = {};
        for (const l of s.methods) if (l.method !== 'EFECTIVO') this.declared[l.method] = Number(l.expected);
        this.counted = null; this.den = {}; this.denTotal = 0; this.notes = '';
      },
      error: (e) => this.toast.error(apiError(e).message),
    });
  }

  get c() { return this.s?.closing; }
  get closed() { return this.c?.status === 'CERRADA'; }
  get dayLabel() { return longDay(this.day) + (this.day === this.todayIso ? ' · hoy' : ''); }
  get others() { return (this.s?.methods || []).filter((l: any) => l.method !== 'EFECTIVO' && (+l.expected !== 0 || +l.income !== 0)); }
  line(m: string) { return this.s?.methods.find((l: any) => l.method === m); }
  get expectedCash() { const l = this.line('EFECTIVO'); return this.openingFloat + Number(l?.income || 0) - Number(l?.outgo || 0); }
  get cashDiff() { return this.round((this.counted ?? 0) - this.expectedCash); }
  diffOf(l: any) { return this.round(Number(this.declared[l.method] ?? l.expected) - Number(l.expected)); }
  get totalDiff() { return this.round((this.counted === null ? 0 : this.cashDiff) + this.others.reduce((a: number, l: any) => a + this.diffOf(l), 0)); }
  get deposit() { return Math.max(0, (this.counted ?? 0) - this.nextFloat); }

  sumDenoms() {
    this.denTotal = this.round(Object.entries(this.den).reduce((a, [k, v]) => a + Number(k) * (Number(v) || 0), 0));
    if (this.denTotal > 0) this.counted = this.denTotal;
  }

  close() {
    this.busy = true; this.e = {}; this.error = '';
    const denominations: Record<string, number> = {};
    if (this.useDenoms) for (const [k, v] of Object.entries(this.den)) if (Number(v) > 0) denominations[k] = Number(v);
    this.api.closeCash({ date: this.day, openingFloat: this.openingFloat, countedCash: this.counted, declared: this.declared,
      denominations: Object.keys(denominations).length ? denominations : null, nextFloat: this.nextFloat, notes: this.notes }).subscribe({
      next: (s) => { this.busy = false; this.s = s; this.toast.ok('Caja cerrada. Descarga el PDF para firmarlo.'); },
      error: (err) => { this.busy = false; const a = apiError(err); this.error = a.message; this.e = (a as any).fields || {}; },
    });
  }

  reopen() {
    this.api.reopenCash(this.day, this.reopenReason).subscribe({
      next: () => { this.toast.ok('Caja reabierta.'); this.reopenOpen = false; this.reopenReason = ''; this.load(); },
      error: (err) => this.toast.error(apiError(err).message),
    });
  }

  pdf() { this.api.cashClosingPdf(this.day).subscribe((b) => saveBlob(b, `cierre-caja-${this.day}.pdf`)); }

  private round(n: number) { return Math.round(n * 100) / 100; }
  signed(v: any) { const n = Number(v); return (n > 0 ? '+' : '') + soles(n); }
  money(v: any) { return soles(Number(v || 0)); }
  short(d: string) { const [y, m, dd] = d.split('-'); return `${dd}/${m}`; }
  dt = dateTime;
}
