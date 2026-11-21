import { Component, Input, OnChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { apiError, dateTime, downloadCsv, isoDate, soles } from '../core/format';

/** v2.8: kardex valorizado de un material y consumo de materiales por servicio, paciente y material. */
@Component({
  selector: 'app-inventory-costs',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="bar">
      <label class="field"><span class="small strong">Desde</span><input class="input" type="date" [(ngModel)]="from" (change)="load()" /></label>
      <label class="field"><span class="small strong">Hasta</span><input class="input" type="date" [(ngModel)]="to" (change)="load()" /></label>
      @if (view === 'kardex') {
        <label class="field grow"><span class="small strong">Material</span>
          <select class="input" [(ngModel)]="itemId" (ngModelChange)="load()"><option [ngValue]="null">Elige un material…</option>
            @for (i of items; track i.id) { <option [ngValue]="i.id">{{ i.name }} ({{ i.unit }})</option> }</select></label>
      }
      <button class="btn" type="button" (click)="csv()" [disabled]="!data">Descargar Excel (CSV)</button>
    </div>
    @if (error) { <p class="err">{{ error }}</p> }

    @if (view === 'kardex') {
      @if (val) {
        <div class="kpis"><div><b>{{ soles(val.total) }}</b><span>valor del inventario hoy</span></div>
          @for (c of cats(); track c[0]) { <div><b>{{ soles(c[1]) }}</b><span>{{ catLabel[c[0]] || c[0] }}</span></div> }</div>
      }
      @if (!itemId) { <p class="muted pad">Elige un material para ver su kardex valorizado (costo promedio; las salidas se valorizan al costo de los lotes que consumen).</p> }
      @else if (data) {
        <section class="panel">
          <table class="table k">
            <thead>
              <tr><th rowspan="2">Fecha</th><th rowspan="2">Concepto</th><th colspan="3" class="c in">Entradas</th><th colspan="3" class="c out">Salidas</th><th colspan="3" class="c">Saldo</th></tr>
              <tr><th class="num in">Cant.</th><th class="num in">C. unit.</th><th class="num in">Total</th><th class="num out">Cant.</th><th class="num out">C. unit.</th><th class="num out">Total</th>
                <th class="num">Cant.</th><th class="num">C. prom.</th><th class="num">Valor</th></tr>
            </thead>
            <tbody>
              <tr class="open"><td>{{ data.from }}</td><td>Saldo inicial</td><td colspan="6"></td><td class="num">{{ n(data.opening.qty) }}</td><td></td><td class="num">{{ soles(data.opening.value) }}</td></tr>
              @for (r of data.rows; track r.id) {
                <tr>
                  <td class="small nowrap">{{ dt(r.date) }}</td>
                  <td class="small">{{ r.kindLabel }}@if (r.patient) { · <a [routerLink]="['/pacientes', r.patient.id]">{{ r.patient.fullName }}</a> }
                    @if (r.procedure) { · {{ r.procedure }} }<div class="muted">{{ r.detail }}</div></td>
                  <td class="num in">{{ r.inQty != null ? n(r.inQty) : '' }}</td><td class="num in">{{ r.inQty != null ? c4(r.unitCost) : '' }}</td><td class="num in">{{ r.inQty != null ? soles(r.inValue) : '' }}</td>
                  <td class="num out">{{ r.outQty != null ? n(r.outQty) : '' }}</td><td class="num out">{{ r.outQty != null ? c4(r.unitCost) : '' }}</td><td class="num out">{{ r.outQty != null ? soles(r.outValue) : '' }}</td>
                  <td class="num strong">{{ n(r.balanceQty) }}</td><td class="num">{{ r.averageCost != null ? c4(r.averageCost) : '—' }}</td><td class="num">{{ soles(r.balanceValue) }}</td>
                </tr>
              } @empty { <tr><td colspan="11" class="muted">Sin movimientos en el período.</td></tr> }
              <tr class="tot"><td colspan="2">Totales del período</td><td class="num">{{ n(data.totals.inQty) }}</td><td></td><td class="num">{{ soles(data.totals.inValue) }}</td>
                <td class="num">{{ n(data.totals.outQty) }}</td><td></td><td class="num">{{ soles(data.totals.outValue) }}</td><td class="num">{{ n(data.closing.qty) }}</td><td></td><td class="num">{{ soles(data.closing.value) }}</td></tr>
            </tbody>
          </table>
        </section>
      }
    }

    @if (view === 'consumo' && data) {
      <div class="kpis"><div><b>{{ soles(data.total) }}</b><span>en materiales usados con pacientes</span></div>
        <div><b>{{ data.byPatient.length }}</b><span>pacientes</span></div><div><b>{{ servicesTotal() }}</b><span>servicios realizados con materiales</span></div></div>
      <section class="panel">
        <div class="panel-head"><h3>Por servicio</h3><span class="muted small">Costo de materiales frente a lo cobrado por el procedimiento</span></div>
        <table class="table">
          <thead><tr><th>Procedimiento</th><th class="num">Veces</th><th class="num">Materiales</th><th class="num">Por servicio</th><th class="num">Precio cobrado</th><th class="num">% en materiales</th></tr></thead>
          <tbody>@for (r of data.byProcedure; track r.id) {
            <tr><td>{{ r.name }}</td><td class="num">{{ r.times }}</td><td class="num">{{ soles(r.materials) }}</td><td class="num">{{ soles(r.perService) }}</td><td class="num">{{ soles(r.revenue) }}</td>
              <td class="num" [class.bad]="r.materialShare > 35">{{ r.materialShare != null ? r.materialShare + ' %' : '—' }}</td></tr>
          } @empty { <tr><td colspan="6" class="muted">Sin consumo en el período.</td></tr> }</tbody>
        </table>
      </section>
      <div class="two">
        <section class="panel">
          <div class="panel-head"><h3>Por paciente</h3></div>
          <table class="table"><thead><tr><th>Paciente</th><th class="num">Servicios</th><th class="num">Materiales</th></tr></thead>
            <tbody>@for (r of data.byPatient.slice(0, 30); track r.id) { <tr><td><a [routerLink]="['/pacientes', r.id]">{{ r.fullName }}</a></td><td class="num">{{ r.services }}</td><td class="num">{{ soles(r.materials) }}</td></tr> }
              @empty { <tr><td colspan="3" class="muted">—</td></tr> }</tbody></table>
        </section>
        <section class="panel">
          <div class="panel-head"><h3>Por material</h3></div>
          <table class="table"><thead><tr><th>Material</th><th class="num">Cantidad</th><th class="num">Costo</th></tr></thead>
            <tbody>@for (r of data.byItem; track r.id) { <tr><td>{{ r.name }}</td><td class="num">{{ n(r.qty) }} {{ r.unit }}</td><td class="num">{{ soles(r.materials) }}</td></tr> }
              @empty { <tr><td colspan="3" class="muted">—</td></tr> }</tbody></table>
        </section>
      </div>
    }
  `,
  styles: [`
    .bar { display: flex; flex-wrap: wrap; gap: 10px; align-items: flex-end; margin: 14px 0; }
    .bar .field { display: grid; gap: 4px; } .bar .grow { flex: 1; min-width: 220px; }
    .kpis { display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: 10px; margin-bottom: 14px; }
    .kpis div { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius); padding: 10px 14px; display: grid; }
    .kpis b { font-size: 20px; } .kpis span { color: var(--muted); font-size: 12.5px; }
    .k th, .k td { font-size: 12.5px; padding: 6px 8px; }
    .k th.c { text-align: center; } .in { background: #f1faf4; } .out { background: #fdf3f5; }
    .open td, .tot td { font-weight: 700; background: var(--surface-2); }
    .num { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
    .nowrap { white-space: nowrap; } .pad { padding: 16px 4px; } .bad { color: var(--gum); font-weight: 700; }
    .two { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-top: 14px; }
    @media (max-width: 900px) { .two { grid-template-columns: 1fr; } }
    .err { color: var(--gum); }
  `],
})
export class InventoryCostsComponent implements OnChanges {
  @Input() view: 'kardex' | 'consumo' = 'kardex';
  @Input() items: any[] = [];
  private api = inject(Api);
  from = isoDate(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  to = isoDate();
  itemId: number | null = null;
  data: any = null;
  val: any = null;
  error = '';
  catLabel: Record<string, string> = { INSUMO: 'Insumos', INSTRUMENTAL: 'Instrumental', MEDICAMENTO: 'Medicamentos' };
  ngOnChanges() { this.data = null; this.load(); if (this.view === 'kardex' && !this.val) this.api.inventoryValuation().subscribe((v) => (this.val = v)); }
  load() {
    this.error = '';
    if (this.view === 'kardex') {
      if (!this.itemId) { this.data = null; return; }
      this.api.kardex(this.itemId, this.from, this.to).subscribe({ next: (d) => (this.data = d), error: (e) => (this.error = apiError(e).message) });
    } else this.api.inventoryConsumption(this.from, this.to).subscribe({ next: (d) => (this.data = d), error: (e) => (this.error = apiError(e).message) });
  }
  cats() { return Object.entries(this.val?.byCategory || {}); }
  servicesTotal() { return (this.data?.byProcedure || []).reduce((a: number, r: any) => a + r.times, 0); }
  csv() {
    if (this.view === 'kardex' && this.data) {
      downloadCsv(`kardex-${this.data.item.name}-${this.from}-a-${this.to}.csv`,
        ['Fecha', 'Concepto', 'Detalle', 'Paciente', 'Entrada cant.', 'Entrada c.u.', 'Entrada total', 'Salida cant.', 'Salida c.u.', 'Salida total', 'Saldo cant.', 'Costo promedio', 'Saldo valor'],
        [['', 'Saldo inicial', '', '', '', '', '', '', '', '', this.data.opening.qty, '', this.data.opening.value],
          ...this.data.rows.map((r: any) => [r.date, r.kindLabel, r.detail, r.patient?.fullName || '', r.inQty ?? '', r.inQty != null ? r.unitCost : '', r.inValue ?? '',
            r.outQty ?? '', r.outQty != null ? r.unitCost : '', r.outValue ?? '', r.balanceQty, r.averageCost ?? '', r.balanceValue])]);
    } else if (this.data) {
      downloadCsv(`consumo-materiales-${this.from}-a-${this.to}.csv`, ['Procedimiento', 'Veces', 'Materiales', 'Por servicio', 'Precio cobrado', '% en materiales'],
        this.data.byProcedure.map((r: any) => [r.name, r.times, r.materials, r.perService, r.revenue, r.materialShare ?? '']));
    }
  }
  n(v: any) { return v == null ? '' : (+v).toLocaleString('es-PE', { maximumFractionDigits: 2 }); }
  c4(v: any) { return v == null ? '' : 'S/ ' + (+v).toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 4 }); }
  soles = soles;
  dt = dateTime;
}
