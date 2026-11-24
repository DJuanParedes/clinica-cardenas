import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { PERIODS, apiError, dateTime, downloadCsv, periodPreset, soles } from '../core/format';
import { IconComponent } from '../shared/icon.component';
import { PagerComponent, filterRows, pageOf } from '../shared/pager.component';
import { TableSearchComponent } from '../shared/table-search.component';

/** Vista del odontólogo: cuánto gana por tratamiento, por día y por mes, en soles. */
@Component({
  selector: 'app-my-earnings',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent, PagerComponent, TableSearchComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Mis ganancias</h1><p class="sub">Lo que ganas por cada tratamiento que realizas, según tus reglas de comisión. Solo ves lo tuyo.</p></div>
        <button class="btn" type="button" (click)="csv()" [disabled]="!r?.lines?.length"><app-icon name="download" [size]="16" /> Descargar CSV</button>
      </div>
      <div class="panel filters">
        <div class="seg">@for (p of periods; track p.key) { <button type="button" [class.on]="preset === p.key" (click)="setPreset(p.key)">{{ p.label }}</button> }</div>
        <div class="row"><input type="date" class="input" [(ngModel)]="from" (change)="preset = ''; load()" /> a <input type="date" class="input" [(ngModel)]="to" (change)="preset = ''; load()" /></div>
      </div>
      @if (error) { <div class="notice amber">{{ error }}</div> }
      @if (r) {
        <div class="kpis">
          <div class="kpi"><span class="muted small">Ganado en el período</span><strong class="big">{{ money(r.total) }}</strong><span class="muted small">{{ r.treatments }} tratamientos</span></div>
          <div class="kpi"><span class="muted small">Producción (precio de los tratamientos)</span><strong class="big">{{ money(r.produced) }}</strong></div>
          <div class="kpi"><span class="muted small">Por liquidar</span><strong class="big">{{ money(r.toSettle) }}</strong><span class="muted small">la clínica te lo paga al liquidar</span></div>
          <div class="kpi"><span class="muted small">Ya liquidado</span><strong class="big">{{ money(r.settled) }}</strong></div>
        </div>
        <div class="grid two">
          <section class="panel">
            <div class="panel-head"><h3>Por día</h3></div>
            <div class="bars">
              @for (d of r.byDay; track d.day) {
                <div class="bar-row"><span class="muted small tabular">{{ d.day.slice(8) }}/{{ d.day.slice(5, 7) }}</span>
                  <span class="bar"><span [style.width.%]="pct(d.amount)"></span></span><span class="tabular small strong">{{ money(d.amount) }}</span></div>
              } @empty { <p class="muted small pad">Sin tratamientos realizados en el período.</p> }
            </div>
          </section>
          <section class="panel">
            <div class="panel-head"><h3>Por mes</h3></div>
            <table class="table"><tbody>
              @for (m of r.byMonth; track m.month) { <tr><td>{{ monthLabel(m.month) }}</td><td class="num tabular strong">{{ money(m.amount) }}</td></tr> }
              @empty { <tr><td class="muted">—</td></tr> }
            </tbody></table>
            <div class="panel-foot small muted">
              <strong>Tus reglas vigentes:</strong>
              @for (x of r.rules; track x.id) { <div>{{ x.label }}</div> } @empty { <div>No tienes reglas de comisión; consulta con administración.</div> }
            </div>
          </section>
        </div>
        <section class="panel">
          <div class="panel-head"><h3>Detalle por tratamiento</h3></div>
          <app-table-search [(q)]="qL" (qChange)="pg = 1" placeholder="Buscar paciente o tratamiento" [total]="(r.lines || []).length" [shown]="filterRows(r.lines, qL).length" />
          <table class="table">
            <thead><tr><th>Fecha</th><th>Paciente</th><th>Tratamiento</th><th class="num">Precio</th><th>Regla</th><th class="num">Ganas</th><th>Estado</th></tr></thead>
            <tbody>
              @for (l of pageOf(filterRows(r.lines, qL), pg); track l.treatmentItemId) {
                <tr>
                  <td class="tabular nowrap small">{{ dt(l.doneAt) }}</td>
                  <td><a [routerLink]="['/pacientes', l.patientId]">{{ l.patientName }}</a></td>
                  <td>{{ l.procedure }}</td>
                  <td class="num tabular">{{ money(l.price) }}</td>
                  <td class="small muted">{{ l.rule || 'liquidado' }}</td>
                  <td class="num tabular strong">{{ money(l.commission) }}</td>
                  <td>@if (l.state === 'LIQUIDADO') { <span class="tag green">Liquidado</span> } @else { <span class="tag amber">Por liquidar</span> }</td>
                </tr>
              } @empty { <tr><td colspan="7" class="muted center">Sin tratamientos con comisión en el período.</td></tr> }
            </tbody>
          </table>
          <app-pager [total]="filterRows(r.lines, qL).length" [page]="pg" (pageChange)="pg = $event" />
        </section>
        @if (r.pendingCollection?.length || r.withoutRule?.length) {
          <section class="panel">
            <div class="panel-head"><h3>Aún no suman</h3></div>
            <div class="panel-body small">
              @for (l of r.pendingCollection; track l.treatmentItemId) { <div>{{ dt(l.doneAt) }} · {{ l.patientName }} · {{ l.procedure }} — <span class="muted">{{ l.reason }}</span></div> }
              @for (l of r.withoutRule; track l.treatmentItemId) { <div>{{ dt(l.doneAt) }} · {{ l.patientName }} · {{ l.procedure }} — <span class="muted">sin regla de comisión</span></div> }
            </div>
          </section>
        }
      }
    </div>
  `,
  styles: [`
    .filters { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; padding: 12px 14px; margin-bottom: 14px; }
    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; margin-bottom: 14px; }
    .kpi { display: flex; flex-direction: column; gap: 4px; padding: 16px; border-radius: 16px; background: var(--surface); border: 1px solid var(--line); }
    .big { font-size: 26px; }
    .grid.two { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-bottom: 14px; }
    .bars { padding: 10px 16px 14px; display: flex; flex-direction: column; gap: 6px; max-height: 320px; overflow: auto; }
    .bar-row { display: grid; grid-template-columns: 48px 1fr 90px; align-items: center; gap: 8px; }
    .bar { height: 10px; border-radius: 6px; background: var(--surface-2, #eef2f4); overflow: hidden; }
    .bar span { display: block; height: 100%; background: var(--teal); border-radius: 6px; }
    .pad { padding: 8px 0; }
    @media (max-width: 900px) { .grid.two { grid-template-columns: 1fr; } }
  `],
})
export class MyEarningsComponent implements OnInit {
  filterRows = filterRows;
  qL = '';
  private api = inject(Api);
  periods = PERIODS;
  preset = 'mes';
  from = periodPreset('mes').from;
  to = periodPreset('mes').to;
  r: any = null;
  error = '';
  pg = 1;
  pageOf = pageOf;

  ngOnInit() { this.load(); }
  setPreset(k: string) { this.preset = k; const p = periodPreset(k); this.from = p.from; this.to = p.to; this.load(); }
  load() {
    this.error = '';
    this.api.myEarnings(this.from, this.to).subscribe({ next: (r) => { this.r = r; this.pg = 1; }, error: (e) => (this.error = apiError(e).message) });
  }
  money(v: any) { return soles(v); }
  dt(v: string) { return dateTime(v); }
  pct(v: number) { const max = Math.max(...(this.r?.byDay || []).map((d: any) => +d.amount), 1); return (+v / max) * 100; }
  monthLabel(m: string) { const [y, mm] = m.split('-'); return ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'][+mm - 1] + ' ' + y; }
  csv() {
    downloadCsv(`mis-ganancias-${this.from}-a-${this.to}.csv`, ['Fecha', 'Paciente', 'Tratamiento', 'Precio', 'Regla', 'Ganas', 'Estado'],
      this.r.lines.map((l: any) => [l.doneAt, l.patientName, l.procedure, l.price, l.rule || '', l.commission, l.state]));
  }
}
