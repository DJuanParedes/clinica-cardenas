import { Component, Input, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { ToastService } from '../core/toast.service';
import { Dentist } from '../core/models';
import { ITEM_LABEL, ITEM_TONE, PERIODS, STATUS_LABEL, STATUS_TONE, apiError, dateTime, downloadCsv, periodPreset, shortDate, soles } from '../core/format';
import { BarsComponent, BarSeries } from '../shared/bars.component';
import { HBarsComponent } from '../shared/hbars.component';
import { IconComponent } from '../shared/icon.component';

type Tab = 'ingresos' | 'citas' | 'tratamientos';

/** HU-19 ingresos · HU-20 citas · HU-21 tratamientos: indicadores para la dirección de la clínica. */
@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent, BarsComponent, HBarsComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <h1>Indicadores de la clínica</h1>
          <p class="sub">Desempeño financiero, funcionamiento de la agenda y actividad asistencial.</p>
        </div>
        <div class="actions">
          <button class="btn" type="button" (click)="csv()" [disabled]="!data"><app-icon name="download" [size]="16" /> Descargar CSV</button>
          <button class="btn" type="button" (click)="print()"><app-icon name="print" [size]="16" /> Imprimir</button>
        </div>
      </div>

      <nav class="tabs no-print" aria-label="Indicadores">
        <a [class.on]="tab === 'ingresos'" (click)="go('ingresos')">Ingresos</a>
        <a [class.on]="tab === 'citas'" (click)="go('citas')">Citas</a>
        <a [class.on]="tab === 'tratamientos'" (click)="go('tratamientos')">Tratamientos</a>
      </nav>

      <section class="panel filters no-print">
        <div class="seg">
          @for (p of periods; track p.key) { <button type="button" [class.on]="preset === p.key" (click)="usePreset(p.key)">{{ p.label }}</button> }
        </div>
        <div class="row wrap">
          <input type="date" class="input" [(ngModel)]="from" (change)="preset = ''; load()" aria-label="Desde" />
          <span class="muted">a</span>
          <input type="date" class="input" [(ngModel)]="to" (change)="preset = ''; load()" aria-label="Hasta" />
          @if (tab !== 'ingresos') {
            <select class="input dsel" [(ngModel)]="dentistId" (ngModelChange)="load()" aria-label="Odontólogo">
              <option [ngValue]="null">Todos los odontólogos</option>
              @for (d of dentists; track d.id) { <option [ngValue]="d.id">{{ d.fullName }}</option> }
            </select>
          }
        </div>
      </section>

      @if (loading && !data) {
        <div class="skeleton" style="height: 420px"></div>
      } @else if (data) {
        <p class="muted small period">Período: {{ date(data.from) }} – {{ date(data.to) }}</p>
        @switch (tab) {
          @case ('ingresos') {
            <section class="figures panel">
              <div class="fig"><span class="l">Ingresos cobrados</span><span class="v tabular">{{ money(data.total) }}</span>
                <span class="meta">
                  @if (data.variation !== null) {
                    <span [class.up]="data.variation >= 0" [class.down]="data.variation < 0"><app-icon [name]="data.variation >= 0 ? 'arrowUp' : 'arrowDown'" [size]="12" /> {{ abs(data.variation) }} %</span>
                    vs. período anterior ({{ money(data.previousTotal) }})
                  } @else { sin datos del período anterior }
                </span></div>
              <div class="fig"><span class="l">Cobros</span><span class="v tabular">{{ data.count }}</span><span class="meta">ticket promedio {{ money(data.averageTicket) }}</span></div>
              <div class="fig"><span class="l">Facturado y aceptado por SUNAT</span><span class="v tabular">{{ money(data.invoiced) }}</span><span class="meta">boletas y facturas</span></div>
              <div class="fig"><span class="l">Resultado de caja</span><span class="v tabular" [class.down]="data.net < 0">{{ money(data.net) }}</span><span class="meta">ingresos − egresos ({{ money(data.expenses) }})</span></div>
            </section>
            <section class="panel">
              <div class="panel-head"><h3>Ingresos por {{ groupLabel(data.grouping) }}</h3></div>
              <div class="panel-body"><app-bars [labels]="labels" [series]="series" [money]="true" ariaLabel="Ingresos por período" /></div>
            </section>
            <div class="three">
              <section class="panel"><div class="panel-head"><h3>Por odontólogo</h3></div><div class="panel-body"><app-hbars [items]="rank(data.byDentist)" [money]="true" /></div></section>
              <section class="panel"><div class="panel-head"><h3>Por especialidad</h3></div><div class="panel-body"><app-hbars [items]="rank(data.byCategory)" [money]="true" color="var(--orange)" /></div></section>
              <section class="panel"><div class="panel-head"><h3>Por medio de pago</h3></div><div class="panel-body"><app-hbars [items]="rank(data.byMethod)" [money]="true" color="var(--teal)" /></div></section>
            </div>
          }

          @case ('citas') {
            <section class="figures panel">
              <div class="fig"><span class="l">Citas en el período</span><span class="v tabular">{{ data.total }}</span><span class="meta">{{ data.hoursAttended }} h de atención</span></div>
              <div class="fig"><span class="l">Atendidas</span><span class="v tabular up">{{ data.attended }}</span><span class="meta">asistencia {{ data.attendanceRate }} %</span></div>
              <div class="fig"><span class="l">Canceladas · no asistió</span><span class="v tabular down">{{ data.cancelled }} · {{ data.noShow }}</span><span class="meta">cancelación {{ data.cancellationRate }} %</span></div>
              <div class="fig"><span class="l">Programadas</span><span class="v tabular">{{ data.scheduled }}</span><span class="meta">pendientes de atender</span></div>
            </section>
            <section class="panel">
              <div class="panel-head"><h3>Citas por {{ groupLabel(data.grouping) }}</h3></div>
              <div class="panel-body"><app-bars [labels]="labels" [series]="series" [stacked]="true" ariaLabel="Citas por estado" /></div>
            </section>
            <div class="two">
              <section class="panel">
                <div class="panel-head"><h3>Por odontólogo</h3></div>
                <table class="table">
                  <thead><tr><th>Odontólogo</th><th class="right">Citas</th><th class="right">Atendidas</th><th class="right">Canceladas</th><th class="right">No asistió</th><th class="right">Asistencia</th></tr></thead>
                  <tbody>
                    @for (d of data.byDentist; track d.dentistId) {
                      <tr><td class="strong">{{ d.name }}</td><td class="right tabular">{{ d.total }}</td><td class="right tabular">{{ d.attended }}</td>
                        <td class="right tabular">{{ d.cancelled }}</td><td class="right tabular">{{ d.noShow }}</td><td class="right tabular strong">{{ d.attendanceRate }} %</td></tr>
                    } @empty { <tr><td colspan="6" class="muted">Sin citas.</td></tr> }
                  </tbody>
                </table>
              </section>
              <section class="panel">
                <div class="panel-head"><h3>Motivos de cancelación</h3></div>
                <div class="panel-body"><app-hbars [items]="reasons" color="var(--gum)" /></div>
              </section>
            </div>
            <section class="panel">
              <div class="panel-head"><h3>Detalle</h3>
                <select class="input dsel" [(ngModel)]="statusFilter" aria-label="Estado">
                  <option value="">Todos los estados</option>
                  @for (s of statusKeys; track s) { <option [value]="s">{{ stLabel(s) }}</option> }
                </select>
              </div>
              <div class="scroll"><table class="table">
                <thead><tr><th>Fecha y hora</th><th>Paciente</th><th>Odontólogo</th><th>Motivo</th><th>Estado</th></tr></thead>
                <tbody>
                  @for (a of apptRows; track a.id) {
                    <tr><td class="tabular nowrap">{{ dt(a.startAt) }}</td><td><a [routerLink]="['/pacientes', a.patientId]">{{ a.patient }}</a></td><td>{{ a.dentist }}</td>
                      <td>{{ a.reason }}@if (a.statusReason && a.status === 'CANCELADA') { <div class="muted small">{{ a.statusReason }}</div> }</td>
                      <td><span class="tag" [class]="'tag ' + stTone(a.status)">{{ stLabel(a.status) }}</span></td></tr>
                  } @empty { <tr><td colspan="5" class="muted">Sin citas con ese filtro.</td></tr> }
                </tbody>
              </table></div>
            </section>
          }

          @case ('tratamientos') {
            <section class="figures panel">
              <div class="fig"><span class="l">Realizados</span><span class="v tabular up">{{ data.doneCount }}</span><span class="meta">{{ money(data.doneAmount) }} en procedimientos</span></div>
              <div class="fig"><span class="l">Pendientes</span><span class="v tabular">{{ data.pendingCount }}</span><span class="meta">{{ money(data.pendingAmount) }} por realizar</span></div>
              <div class="fig"><span class="l">Avance</span><span class="v tabular">{{ data.completionRate }} %</span><span class="meta">realizados sobre el total</span></div>
              <div class="fig"><span class="l">Planes</span><span class="v tabular">{{ data.activePlans }}</span><span class="meta">activos · {{ data.completedPlans }} completados en el período</span></div>
            </section>
            <div class="two">
              <section class="panel">
                <div class="panel-head"><h3>Por procedimiento</h3></div>
                <div class="scroll"><table class="table">
                  <thead><tr><th>Procedimiento</th><th class="right">Realizados</th><th class="right">Monto</th><th class="right">Pendientes</th><th class="right">Monto</th></tr></thead>
                  <tbody>
                    @for (p of data.byProcedure; track p.procedure) {
                      <tr><td><div class="strong">{{ p.procedure }}</div><div class="muted small">{{ p.category }}</div></td>
                        <td class="right tabular">{{ p.done }}</td><td class="right tabular">{{ money(p.doneAmount) }}</td>
                        <td class="right tabular">{{ p.pending }}</td><td class="right tabular muted">{{ money(p.pendingAmount) }}</td></tr>
                    } @empty { <tr><td colspan="5" class="muted">Sin tratamientos.</td></tr> }
                  </tbody>
                </table></div>
              </section>
              <section class="panel">
                <div class="panel-head"><h3>Por odontólogo</h3></div>
                <div class="panel-body"><app-bars [labels]="labels" [series]="series" [legend]="true" ariaLabel="Tratamientos por odontólogo" /></div>
              </section>
            </div>
            <section class="panel">
              <div class="panel-head"><h3>Detalle</h3>
                <div class="seg sm">
                  <button type="button" [class.on]="treatView === 'done'" (click)="treatView = 'done'">Realizados ({{ data.doneDetail.length }})</button>
                  <button type="button" [class.on]="treatView === 'pending'" (click)="treatView = 'pending'">Pendientes ({{ data.pendingDetail.length }})</button>
                </div>
              </div>
              <div class="scroll"><table class="table">
                <thead><tr><th>{{ treatView === 'done' ? 'Realizado' : 'Plan desde' }}</th><th>Paciente</th><th>Procedimiento</th><th>Odontólogo</th><th>Estado</th><th class="right">Precio</th></tr></thead>
                <tbody>
                  @for (t of (treatView === 'done' ? data.doneDetail : data.pendingDetail); track t.id) {
                    <tr><td class="tabular nowrap">{{ date(t.date) }}</td><td><a [routerLink]="['/pacientes', t.patientId]">{{ t.patient }}</a></td>
                      <td>{{ t.procedure }}@if (t.plan) { <div class="muted small">{{ t.plan }}</div> }</td><td>{{ t.dentist }}</td>
                      <td><span class="tag" [class]="'tag ' + itTone(t.status)">{{ itLabel(t.status) }}</span></td><td class="right tabular">{{ money(t.price) }}</td></tr>
                  } @empty { <tr><td colspan="6" class="muted">Sin registros.</td></tr> }
                </tbody>
              </table></div>
            </section>
          }
        }
      }
    </div>
  `,
  styles: [`
    .filters { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; justify-content: space-between; padding: 12px 16px; margin-bottom: 10px; }
    .filters .input { width: 155px; height: 36px; }
    .filters .dsel { width: 230px; }
    .dsel { width: 200px; height: 34px; font-size: 13px; }
    .period { margin: 0 0 10px; }
    .figures { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin-bottom: 18px; }
    .fig { display: flex; flex-direction: column; padding: 18px 20px; border-right: 1px solid var(--line); }
    .fig:last-child { border-right: 0; }
    .fig .v { font-size: var(--step-3); font-weight: 800; letter-spacing: -.02em; line-height: 1.15; }
    .fig .l { font-weight: 700; color: var(--ink-2); font-size: var(--step--1); }
    .fig .meta { font-size: 12px; color: var(--muted); margin-top: 6px; }
    .up { color: var(--green); }
    .down { color: var(--gum); }
    .meta .up, .meta .down { font-weight: 800; display: inline-flex; align-items: center; gap: 2px; }
    .two { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; margin: 18px 0; }
    .three { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18px; margin-top: 18px; }
    .scroll { max-height: 460px; overflow: auto; }
    .nowrap { white-space: nowrap; }
    .seg.sm button { height: 28px; font-size: 12.5px; }
    @media (max-width: 1100px) { .three { grid-template-columns: 1fr; } }
    @media (max-width: 900px) { .two { grid-template-columns: 1fr; } .figures { grid-template-columns: 1fr 1fr; } }
  `],
})
export class ReportsComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  private router = inject(Router);

  @Input() tab: Tab = 'ingresos';

  periods = PERIODS;
  preset = '3-meses';
  from = periodPreset('3-meses').from;
  to = periodPreset('3-meses').to;
  dentistId: number | null = null;
  dentists: Dentist[] = [];
  data: any = null;
  loading = false;
  labels: string[] = [];
  series: BarSeries[] = [];
  reasons: { name: string; value: number }[] = [];
  statusFilter = '';
  statusKeys = ['PROGRAMADA', 'CONFIRMADA', 'ATENDIDA', 'CANCELADA', 'NO_ASISTIO'];
  treatView: 'done' | 'pending' = 'done';

  ngOnInit() {
    if (!['ingresos', 'citas', 'tratamientos'].includes(this.tab)) this.tab = 'ingresos';
    this.api.dentists().subscribe((d) => (this.dentists = d));
    this.load();
  }

  go(t: Tab) {
    this.tab = t;
    this.data = null;
    this.router.navigate([], { queryParams: { tab: t === 'ingresos' ? null : t }, replaceUrl: true });
    this.load();
  }

  usePreset(k: string) {
    this.preset = k;
    const p = periodPreset(k);
    this.from = p.from;
    this.to = p.to;
    this.load();
  }

  load() {
    if (!this.from || !this.to) return;
    this.loading = true;
    const req = this.tab === 'ingresos' ? this.api.incomeReport(this.from, this.to)
      : this.tab === 'citas' ? this.api.appointmentsReport(this.from, this.to, this.dentistId)
      : this.api.treatmentsReport(this.from, this.to, this.dentistId);
    const tab = this.tab;
    req.subscribe({
      next: (d) => {
        if (tab !== this.tab) return;
        this.loading = false;
        this.data = d;
        if (tab === 'ingresos') {
          this.labels = d.series.map((s: any) => s.label);
          this.series = [{ name: 'Ingresos', color: '#00a3d4', values: d.series.map((s: any) => Number(s.amount)) }];
        } else if (tab === 'citas') {
          this.labels = d.series.map((s: any) => s.label);
          this.series = [
            { name: 'Atendidas', color: '#217a4a', values: d.series.map((s: any) => s.attended) },
            { name: 'Canceladas / no asistió', color: '#f08a00', values: d.series.map((s: any) => s.lost) },
            { name: 'Programadas', color: '#9ec9da', values: d.series.map((s: any) => s.scheduled) },
          ];
          this.reasons = d.cancelReasons.map((r: any) => ({ name: r.reason, value: r.count }));
        } else {
          this.labels = d.byDentist.map((x: any) => x.name.replace(/^(Dra?\.)\s+/, '$1 ').split(' ').slice(0, 3).join(' '));
          this.series = [
            { name: 'Realizados', color: '#00a3d4', values: d.byDentist.map((x: any) => x.done) },
            { name: 'Pendientes', color: '#f08a00', values: d.byDentist.map((x: any) => x.pending) },
          ];
        }
      },
      error: (e) => { this.loading = false; this.toast.error(apiError(e).message); },
    });
  }

  get apptRows() { return (this.data?.detail || []).filter((a: any) => !this.statusFilter || a.status === this.statusFilter); }

  rank(list: any[]) { return (list || []).map((x) => ({ name: x.name, value: Number(x.amount), note: x.share + ' %' })); }
  groupLabel(g: string) { return ({ day: 'día', week: 'semana', month: 'mes' } as Record<string, string>)[g] || g; }
  abs(v: number) { return Math.abs(v); }

  csv() {
    const d = this.data;
    if (!d) return;
    const f = `${this.tab}_${this.from}_${this.to}.csv`;
    if (this.tab === 'ingresos') {
      downloadCsv(f, ['Período', 'Ingresos'], d.series.map((s: any) => [s.key, s.amount]));
    } else if (this.tab === 'citas') {
      downloadCsv(f, ['Fecha', 'Paciente', 'Odontólogo', 'Motivo', 'Estado', 'Observación'],
        d.detail.map((a: any) => [String(a.startAt).replace('T', ' '), a.patient, a.dentist, a.reason, this.stLabel(a.status), a.statusReason || '']));
    } else {
      downloadCsv(f, ['Procedimiento', 'Especialidad', 'Realizados', 'Monto realizado', 'Pendientes', 'Monto pendiente'],
        d.byProcedure.map((p: any) => [p.procedure, p.category, p.done, p.doneAmount, p.pending, p.pendingAmount]));
    }
  }

  print() { window.print(); }
  money = soles;
  date = shortDate;
  dt = dateTime;
  stLabel(s: string) { return STATUS_LABEL[s] || s; }
  stTone(s: string) { return STATUS_TONE[s] || 'grey'; }
  itLabel(s: string) { return ITEM_LABEL[s] || s; }
  itTone(s: string) { return ITEM_TONE[s] || 'grey'; }
}
