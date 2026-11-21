import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { apiError, saveBlob, soles } from '../core/format';
import { ToastService } from '../core/toast.service';

/** v2.8: reporte anual de la clínica, para ver y descargar en PDF o Excel (CSV). */
@Component({
  selector: 'app-annual-report',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Reporte anual</h1><p class="sub">Resumen del año comparado con el anterior, listo para descargar.</p></div>
        <div class="actions">
          <select class="input auto" [(ngModel)]="year" (ngModelChange)="load()" aria-label="Año">@for (y of years; track y) { <option [ngValue]="y">{{ y }}</option> }</select>
          <button class="btn primary" type="button" [disabled]="busy" (click)="download('pdf')">Descargar PDF</button>
          <button class="btn" type="button" [disabled]="busy" (click)="download('csv')">Descargar Excel (CSV)</button>
        </div>
      </div>
      <nav class="tabs"><a routerLink="/reportes">Ingresos</a><a routerLink="/reportes" [queryParams]="{ tab: 'citas' }">Citas</a>
        <a routerLink="/reportes" [queryParams]="{ tab: 'tratamientos' }">Tratamientos</a><a class="on">Reporte anual</a></nav>
      @if (error) { <p class="err">{{ error }}</p> }
      @if (r) {
        @if (r.partial) { <p class="notice small"><span>El año {{ r.year }} aún no termina: las cifras van hasta hoy.</span></p> }
        <div class="kpis">
          @for (k of kpis(); track k.label) {
            <div><span class="l">{{ k.label }}</span><b>{{ k.value }}</b><span class="d" [class.up]="k.delta?.startsWith('+')" [class.down]="k.delta?.startsWith('-')">{{ k.delta ? k.delta + ' vs ' + (r.year - 1) : '' }}</span></div>
          }
        </div>
        <section class="panel">
          <div class="panel-head"><h3>Mes a mes</h3></div>
          <table class="table">
            <thead><tr><th>Mes</th><th class="num">Citas atendidas</th><th class="num">Pacientes nuevos</th><th class="num">Ingresos</th><th class="num">Egresos</th><th class="num">Resultado</th></tr></thead>
            <tbody>
              @for (m of r.months; track m.month) {
                <tr><td>{{ m.label }}</td><td class="num">{{ m.attended }}</td><td class="num">{{ m.newPatients }}</td><td class="num">{{ soles(m.income) }}</td><td class="num">{{ soles(m.expenses) }}</td>
                  <td class="num" [class.neg]="m.result < 0">{{ soles(m.result) }}</td></tr>
              }
              <tr class="tot"><td>Total</td><td class="num">{{ r.summary.attended }}</td><td class="num">{{ r.summary.newPatients }}</td><td class="num">{{ soles(r.summary.income) }}</td>
                <td class="num">{{ soles(r.summary.expenses) }}</td><td class="num">{{ soles(r.summary.result) }}</td></tr>
            </tbody>
          </table>
        </section>
        <div class="two">
          <section class="panel"><div class="panel-head"><h3>Procedimientos más realizados</h3></div>
            <table class="table"><tbody>@for (p of r.procedures.slice(0, 10); track p.name) { <tr><td>{{ p.name }}</td><td class="num">{{ p.times }}</td><td class="num">{{ soles(p.amount) }}</td></tr> }</tbody></table></section>
          <section class="panel"><div class="panel-head"><h3>Producción por odontólogo</h3></div>
            <table class="table"><tbody>@for (d of r.dentists; track d.name) { <tr><td>{{ d.name }}</td><td class="num">{{ d.appointments }} citas</td><td class="num">{{ soles(d.production) }}</td></tr> }</tbody></table></section>
        </div>
      } @else if (!error) { <div class="skeleton" style="height: 300px"></div> }
    </div>
  `,
  styles: [`
    .auto { width: auto; } .err { color: var(--gum); }
    .kpis { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 10px; margin-bottom: 14px; }
    .kpis div { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius); padding: 12px 14px; display: grid; gap: 2px; }
    .kpis .l { color: var(--muted); font-size: 12.5px; } .kpis b { font-size: 21px; } .kpis .d { font-size: 12px; color: var(--muted); }
    .kpis .d.up { color: var(--green); } .kpis .d.down { color: var(--gum); }
    .num { text-align: right; font-variant-numeric: tabular-nums; } .neg { color: var(--gum); }
    .tot td { font-weight: 800; background: var(--surface-2); }
    .two { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-top: 14px; }
    @media (max-width: 900px) { .two { grid-template-columns: 1fr; } }
  `],
})
export class AnnualReportComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  year = new Date().getFullYear();
  years = Array.from({ length: 5 }, (_, i) => new Date().getFullYear() - i);
  r: any = null;
  error = '';
  busy = false;
  ngOnInit() { this.load(); }
  load() { this.r = null; this.error = ''; this.api.annualReport(this.year).subscribe({ next: (r) => (this.r = r), error: (e) => (this.error = apiError(e).message) }); }
  kpis() {
    const s = this.r.summary, p = this.r.previous;
    const d = (a: number, b: number) => (!b ? (a ? 'nuevo' : '') : ((a - b) / Math.abs(b) * 100 >= 0 ? '+' : '') + Math.round((a - b) / Math.abs(b) * 100) + ' %');
    return [
      { label: 'Ingresos cobrados', value: soles(s.income), delta: d(+s.income, +p.income) },
      { label: 'Egresos', value: soles(s.expenses), delta: d(+s.expenses, +p.expenses) },
      { label: 'Resultado', value: soles(s.result), delta: d(+s.result, +p.result) },
      { label: 'Pacientes nuevos', value: String(s.newPatients), delta: d(s.newPatients, p.newPatients) },
      { label: 'Citas atendidas', value: String(s.attended), delta: d(s.attended, p.attended) },
      { label: 'Inasistencia', value: s.noShowRate + ' %', delta: '' },
      { label: 'Ticket promedio', value: soles(s.averageTicket), delta: d(+s.averageTicket, +p.averageTicket) },
      { label: 'Satisfacción', value: s.satisfaction != null ? s.satisfaction + ' / 5' : '—', delta: s.surveys ? s.surveys + ' encuestas' : '' },
    ];
  }
  download(kind: 'pdf' | 'csv') {
    this.busy = true;
    this.api.annualReportFile(this.year, kind).subscribe({
      next: (b) => { this.busy = false; saveBlob(b, `reporte-anual-${this.year}.${kind}`); },
      error: () => { this.busy = false; this.toast.error('No se pudo generar el reporte.'); },
    });
  }
  soles = soles;
}
