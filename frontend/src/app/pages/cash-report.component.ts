import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api, CashReport } from '../core/api.service';
import { ToastService } from '../core/toast.service';
import { CATEGORY_LABEL, METHOD_LABEL, PERIODS, apiError, dateTime, downloadCsv, periodPreset, shortDate, soles } from '../core/format';
import { BarsComponent, BarSeries } from '../shared/bars.component';
import { HBarsComponent } from '../shared/hbars.component';
import { IconComponent } from '../shared/icon.component';

/** HU-15: ingresos y egresos de caja por período. */
@Component({
  selector: 'app-cash-report',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent, BarsComponent, HBarsComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <h1>Flujo de caja</h1>
          <p class="sub">Ingresos (cobros) y egresos registrados en el período.</p>
        </div>
        <div class="actions">
          @if (r) { <button class="btn" type="button" (click)="csv()"><app-icon name="download" [size]="16" /> Descargar CSV</button> }
        </div>
      </div>

      <section class="panel filters">
        <div class="seg">
          @for (p of periods; track p.key) { <button type="button" [class.on]="preset === p.key" (click)="usePreset(p.key)">{{ p.label }}</button> }
        </div>
        <div class="row">
          <input type="date" class="input" [(ngModel)]="from" (change)="preset = ''; load()" aria-label="Desde" />
          <span class="muted">a</span>
          <input type="date" class="input" [(ngModel)]="to" (change)="preset = ''; load()" aria-label="Hasta" />
        </div>
      </section>

      @if (!r) {
        <div class="skeleton" style="height: 380px"></div>
      } @else {
        <section class="figures panel">
          <div class="fig"><span class="l">Ingresos</span><span class="v tabular in">{{ money(r.income) }}</span><span class="meta">{{ r.incomeCount }} cobros</span></div>
          <div class="fig"><span class="l">Egresos</span><span class="v tabular out">{{ money(r.outgo) }}</span><span class="meta">{{ r.outgoCount }} registros</span></div>
          <div class="fig"><span class="l">Saldo del período</span><span class="v tabular" [class.out]="r.net < 0">{{ money(r.net) }}</span>
            <span class="meta">{{ r.income > 0 ? margin + ' % de los ingresos' : '—' }}</span></div>
        </section>

        <section class="panel">
          <div class="panel-head"><h3>Ingresos y egresos por día</h3><span class="muted small">{{ date(r.from) }} – {{ date(r.to) }}</span></div>
          <div class="panel-body"><app-bars [labels]="labels" [series]="series" [money]="true" [legend]="true" ariaLabel="Ingresos y egresos por día" /></div>
        </section>

        <div class="two">
          <section class="panel">
            <div class="panel-head"><h3>Por medio de pago</h3></div>
            <table class="table">
              <thead><tr><th>Medio</th><th class="right">Ingresos</th><th class="right">Egresos</th><th class="right">Neto</th></tr></thead>
              <tbody>
                @for (mm of r.byMethod; track mm.method) {
                  <tr><td class="strong">{{ mm.label }}</td><td class="right tabular in">{{ money(mm.income) }}</td><td class="right tabular out">{{ money(mm.outgo) }}</td><td class="right tabular strong">{{ money(mm.net) }}</td></tr>
                } @empty { <tr><td colspan="4" class="muted">Sin movimientos.</td></tr> }
              </tbody>
            </table>
          </section>
          <section class="panel">
            <div class="panel-head"><h3>Egresos por categoría</h3></div>
            <div class="panel-body"><app-hbars [items]="cats" [money]="true" color="var(--gum)" /></div>
          </section>
        </div>

        <section class="panel">
          <div class="panel-head">
            <h3>Detalle de movimientos</h3>
            <div class="seg sm">
              <button type="button" [class.on]="kind === ''" (click)="kind = ''">Todos</button>
              <button type="button" [class.on]="kind === 'INGRESO'" (click)="kind = 'INGRESO'">Ingresos</button>
              <button type="button" [class.on]="kind === 'EGRESO'" (click)="kind = 'EGRESO'">Egresos</button>
            </div>
          </div>
          <div class="scroll">
            <table class="table">
              <thead><tr><th>Fecha</th><th>Concepto</th><th>Paciente / proveedor</th><th>Medio</th><th>Registró</th><th class="right">Importe</th></tr></thead>
              <tbody>
                @for (d of rows; track d.type + d.id) {
                  <tr>
                    <td class="tabular nowrap">{{ d.type === 'INGRESO' ? dt(d.date) : date(d.date) }}</td>
                    <td><span class="tag plain" [class]="'tag plain ' + (d.type === 'INGRESO' ? 'green' : 'gum')">{{ d.type === 'INGRESO' ? 'Ingreso' : cat(d.category) }}</span> {{ d.concept }}</td>
                    <td>@if (d.patientId) { <a [routerLink]="['/pacientes', d.patientId]">{{ d.party }}</a> } @else { {{ d.party || '—' }} }</td>
                    <td>{{ method(d.method) }}</td>
                    <td class="muted">{{ d.user }}</td>
                    <td class="right tabular strong" [class.in]="d.type === 'INGRESO'" [class.out]="d.type === 'EGRESO'">{{ d.type === 'EGRESO' ? '−' : '' }}{{ money(d.amount) }}</td>
                  </tr>
                } @empty { <tr><td colspan="6"><div class="empty small"><p>No hay movimientos en el período.</p></div></td></tr> }
              </tbody>
            </table>
          </div>
          @if (r.voided) { <div class="panel-foot small muted">{{ r.voided }} movimiento(s) anulado(s) no se incluyen.</div> }
        </section>
      }
    </div>
  `,
  styles: [`
    .filters { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; justify-content: space-between; padding: 12px 16px; margin-bottom: 16px; }
    .filters .input { width: 160px; height: 36px; }
    .figures { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); margin-bottom: 18px; }
    .fig { display: flex; flex-direction: column; padding: 18px 22px; border-right: 1px solid var(--line); }
    .fig:last-child { border-right: 0; }
    .fig .v { font-size: var(--step-3); font-weight: 800; letter-spacing: -.02em; line-height: 1.15; }
    .fig .l { font-weight: 700; color: var(--ink-2); font-size: var(--step--1); }
    .fig .meta { font-size: 12px; color: var(--muted); margin-top: 6px; }
    .in { color: var(--green); }
    .out { color: var(--gum); }
    .two { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; margin: 18px 0; }
    .panel + .panel, .two + .panel { margin-top: 18px; }
    .scroll { max-height: 520px; overflow: auto; }
    .nowrap { white-space: nowrap; }
    .seg.sm button { height: 28px; font-size: 12.5px; }
    @media (max-width: 900px) { .two { grid-template-columns: 1fr; } .figures { grid-template-columns: 1fr; } .fig { border-right: 0; border-bottom: 1px solid var(--line); } }
  `],
})
export class CashReportComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);

  periods = PERIODS;
  preset = 'mes';
  from = periodPreset('mes').from;
  to = periodPreset('mes').to;
  kind = '';
  r: CashReport | null = null;
  labels: string[] = [];
  series: BarSeries[] = [];
  cats: { name: string; value: number }[] = [];

  ngOnInit() { this.load(); }

  usePreset(k: string) {
    this.preset = k;
    const p = periodPreset(k);
    this.from = p.from;
    this.to = p.to;
    this.load();
  }

  load() {
    if (!this.from || !this.to) return;
    this.api.cashReport(this.from, this.to).subscribe({
      next: (r) => {
        this.r = r;
        this.labels = r.byDay.map((d) => shortDate(d.date).replace(/\s\d{4}$/, '').replace('.', ''));
        this.series = [
          { name: 'Ingresos', color: '#00a3d4', values: r.byDay.map((d) => d.income) },
          { name: 'Egresos', color: '#f08a00', values: r.byDay.map((d) => d.outgo) },
        ];
        this.cats = r.byCategory.map((c) => ({ name: this.cat(c.category), value: c.amount }));
      },
      error: (e) => this.toast.error(apiError(e).message),
    });
  }

  get rows() { return (this.r?.detail || []).filter((d) => !this.kind || d.type === this.kind); }
  get margin(): string { return this.r && this.r.income ? ((this.r.net / this.r.income) * 100).toFixed(1) : '0'; }

  csv() {
    if (!this.r) return;
    downloadCsv(`flujo-caja_${this.from}_${this.to}.csv`, ['Tipo', 'Fecha', 'Concepto', 'Categoría', 'Paciente / proveedor', 'Medio', 'Referencia', 'Registró', 'Importe'],
      this.r.detail.map((d) => [d.type, d.date.replace('T', ' '), d.concept, d.category ? this.cat(d.category) : '', d.party || '', this.method(d.method),
        d.reference || '', d.user, (d.type === 'EGRESO' ? -d.amount : d.amount).toFixed(2)]));
  }

  money = soles;
  dt = dateTime;
  date = shortDate;
  method(m: string) { return METHOD_LABEL[m] || m; }
  cat(c?: string) { return c ? CATEGORY_LABEL[c] || c : ''; }
}
