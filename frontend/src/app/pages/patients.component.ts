import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, Subscription, debounceTime, distinctUntilChanged, switchMap, catchError, of } from 'rxjs';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Page, Patient } from '../core/models';
import { hhmm, initials, parseLocal, shortDate, soles } from '../core/format';
import { IconComponent } from '../shared/icon.component';

import { OnlyDirective } from '../shared/only.directive';
/** HU-02: búsqueda por documento (exacta / prefijo) o por nombre (parcial). */
@Component({
  selector: 'app-patients',
  standalone: true,
  imports: [OnlyDirective, FormsModule, RouterLink, IconComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <h1>Pacientes</h1>
          <p class="sub">Busca por número de documento, N° de historia clínica o por nombres y apellidos.</p>
        </div>
        @if (auth.can('PACIENTES_EDITAR')) {
          <div class="actions"><a class="btn primary" routerLink="/pacientes/nuevo"><app-icon name="plus" [size]="16" /> Registrar paciente</a></div>
        }
      </div>

      <div class="search-box">
        <app-icon class="ico" name="search" [size]="20" />
        <input class="input" [(ngModel)]="q" (ngModelChange)="search$.next($event)" placeholder="Ej. 45872196 o Quispe Huamán"
               aria-label="Buscar paciente" autofocus appOnly="search" maxlength="100" />
      </div>
      <div class="filters row wrap">
        <select class="input" aria-label="Filtrar por fuente de captación" [(ngModel)]="source" (ngModelChange)="go(0)">
          <option value="">Todas las fuentes</option>
          @for (o of sources; track o.code) { <option [value]="o.code">{{ o.label }}</option> }
        </select>
        <select class="input" aria-label="Filtrar por etiqueta" [(ngModel)]="tag" (ngModelChange)="go(0)">
          <option value="">Todas las etiquetas</option>
          @for (t of tags; track t.tag) { <option [value]="t.tag">{{ t.tag }} ({{ t.patients }})</option> }
        </select>
        <select class="input" aria-label="Filtrar por seguimiento" [(ngModel)]="follow" (ngModelChange)="go(0)">
          <option value="">Todo el seguimiento</option>
          @for (o of followOptions; track o.code) { <option [value]="o.code">{{ o.label }}</option> }
        </select>
        @if (tag || source || follow) {
          <button type="button" class="btn sm ghost" (click)="tag = ''; source = ''; follow = ''; go(0)">Quitar filtros</button>
          @if (auth.can('MARKETING')) { <a class="btn sm ghost" routerLink="/marketing" [queryParams]="segmentParams()">Usar como segmento →</a> }
        }
      </div>
      @if (isDentist) {
        <div class="seg mine">
          <button type="button" [class.on]="mine" (click)="setMine(true)">Mis pacientes</button>
          <button type="button" [class.on]="!mine" (click)="setMine(false)">Todos</button>
        </div>
      }
      <p class="mode muted small">
        @if (loading) { Buscando… }
        @else if (page) {
          @if (page.mode === 'mis-pacientes') { {{ page.total }} pacientes que atiendes o atendiste }
          @else if (page.mode === 'recientes') { Últimos pacientes registrados }
          @else if (page.mode === 'documento') { {{ page.total }} {{ page.total === 1 ? 'resultado' : 'resultados' }} por número de documento }
          @else { {{ page.total }} {{ page.total === 1 ? 'resultado' : 'resultados' }} por nombre }
          @if (elapsed !== null) { · {{ elapsed }} ms }
        }
      </p>

      <section class="panel">
        @if (page && page.content.length === 0) {
          <div class="empty">
            <h3>Sin resultados para "{{ q }}"</h3>
            <p>Revisa el número o prueba solo con el apellido.</p>
            @if (auth.can('PACIENTES_EDITAR')) {
              <a class="btn primary" routerLink="/pacientes/nuevo" [queryParams]="{ doc: q }"><app-icon name="plus" [size]="16" /> Registrar paciente nuevo</a>
            }
          </div>
        } @else {
          <table class="table">
            <thead>
              <tr><th>Paciente</th><th>Teléfono</th><th>Última cita</th><th>Próxima cita</th><th>Tarea pendiente</th><th>Presupuesto</th></tr>
            </thead>
            <tbody>
              @if (!page) {
                @for (i of [1,2,3,4,5]; track i) { <tr><td colspan="6"><div class="skeleton" style="height: 20px"></div></td></tr> }
              }
              @for (p of page?.content || []; track p.id) {
                <tr class="click" (click)="open(p)" tabindex="0" (keydown.enter)="open(p)">
                  <td>
                    <div class="row">
                      <span class="avatar">{{ ini(p.fullName) }}</span>
                      <div><div class="strong">{{ p.lastName }}, {{ p.firstName }}</div>
                        <div class="muted small tabular nowrap">{{ p.docType }} {{ p.docNumber }}{{ p.hcNumber ? ' · HC ' + p.hcNumber : '' }}{{ p.age != null ? ' · ' + p.age + ' años' : '' }}</div></div>
                    </div>
                  </td>
                  <td class="tabular">{{ p.phone }}</td>
                  @let i = ins(p.id);
                  <td>@if (i.lastVisit) { {{ date(i.lastVisit.startAt) }} <div class="muted small">{{ ago(i.lastVisit.startAt) }}</div> } @else { <span class="muted">Nunca</span> }</td>
                  <td>@if (i.nextAppointment) { <span class="strong">{{ date(i.nextAppointment.startAt) }}</span> {{ hhmm(i.nextAppointment.startAt) }}
                        <div class="muted small">{{ i.nextAppointment.reason }}</div> } @else { <span class="tag amber plain">Sin cita</span> }</td>
                  <td>@if (i.task) {
                        <div class="task" [class.over]="i.task.overdue">{{ i.task.title }}</div>
                        <div class="muted small" [class.bad]="i.task.overdue">{{ i.task.overdue ? 'Venció ' : '' }}{{ date(i.task.dueDate) }}{{ i.task.pending > 1 ? ' · +' + (i.task.pending - 1) + ' más' : '' }}</div>
                      } @else { <span class="muted">—</span> }</td>
                  <td>@if (i.budget) {
                        <div class="bar" [attr.aria-label]="'Avance ' + i.budget.percent + '%'"><span [style.width.%]="i.budget.percent"></span></div>
                        <div class="muted small">{{ i.budget.percent }} % · {{ soles(i.budget.done) }} de {{ soles(i.budget.total) }}</div>
                      } @else { <span class="muted">—</span> }
                      @if (i.balance) { <div class="small bad">Debe {{ soles(i.balance) }}</div> }</td>
                </tr>
              }
            </tbody>
          </table>
          @if (page && page.pages > 1) {
            <div class="panel-foot spread">
              <span class="muted small">Página {{ page.page + 1 }} de {{ page.pages }}</span>
              <div class="row">
                <button class="btn sm" [disabled]="page.page === 0" (click)="go(page.page - 1)">Anterior</button>
                <button class="btn sm" [disabled]="page.page + 1 >= page.pages" (click)="go(page.page + 1)">Siguiente</button>
              </div>
            </div>
          }
        }
      </section>
    </div>
  `,
  styles: [`
    .mode { margin: 10px 2px 14px; min-height: 20px; }
    .mine { margin-top: 10px; }
    .filters { margin-top: 10px; gap: 8px; }
    .filters .input { width: auto; min-width: 200px; }
    .bar { height: 6px; width: 110px; border-radius: 99px; background: var(--line); overflow: hidden; margin-bottom: 3px; }
    .bar span { display: block; height: 100%; background: var(--teal); border-radius: 99px; }
    .task { font-size: var(--step--1); font-weight: 600; max-width: 220px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .task.over, .bad { color: var(--gum); }
    .nowrap { white-space: nowrap; }
  `],
})
export class PatientsComponent implements OnInit, OnDestroy {
  private api = inject(Api);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  auth = inject(AuthService);

  q = '';
  page: Page<Patient> | null = null;
  loading = false;
  elapsed: number | null = null;
  search$ = new Subject<string>();
  private sub?: Subscription;
  private started = 0;

  get isDentist() { return this.auth.user()?.role === 'ODONTOLOGO'; }
  mine = false;
  tag = '';
  source = '';
  follow = '';
  insights: Record<string, any> = {};
  followOptions = [
    { code: 'task=CON', label: 'Con tarea pendiente' },
    { code: 'next=SIN', label: 'Sin próxima cita' },
    { code: 'budget=PENDIENTE', label: 'Con presupuesto por hacer' },
    { code: 'debt=true', label: 'Con saldo por pagar' },
    { code: 'visit=SIN_6M', label: 'No vienen hace 6 meses' },
    { code: 'visit=NUNCA', label: 'Nunca atendidos' },
  ];
  sources: { code: string; label: string }[] = [];
  tags: { tag: string; patients: number }[] = [];
  setMine(v: boolean) { this.mine = v; try { localStorage.setItem('cc.mine', v ? '1' : '0'); } catch {} this.go(0); }

  ngOnInit() {
    try { this.mine = this.isDentist && localStorage.getItem('cc.mine') !== '0'; } catch { this.mine = this.isDentist; }
    this.q = this.route.snapshot.queryParamMap.get('q') || '';
    this.tag = this.route.snapshot.queryParamMap.get('etiqueta') || '';
    this.source = this.route.snapshot.queryParamMap.get('fuente') || '';
    this.follow = this.route.snapshot.queryParamMap.get('seguimiento') || '';
    this.api.patientProfileOptions().subscribe({ next: (o) => { this.sources = o.sources; this.tags = o.tags; }, error: () => {} });
    this.sub = this.search$.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap((q) => this.fetch(q, 0)),
    ).subscribe((p) => this.done(p));
    this.fetch(this.q, 0).subscribe((p) => this.done(p));
  }

  private fetch(q: string, page: number) {
    this.loading = true;
    this.started = performance.now();
    return this.api.searchPatientsFiltered(q.trim(), page, 20, { mine: this.mine, tag: this.tag || undefined, source: this.source || undefined, ...this.followFilter() }).pipe(catchError(() => of(null)));
  }

  private done(p: Page<Patient> | null) {
    this.loading = false;
    if (!p) return;
    this.page = p;
    this.insights = (p as any).insights || {};
    this.elapsed = Math.round(performance.now() - this.started);
    this.router.navigate([], { queryParams: { q: this.q || null, etiqueta: this.tag || null, fuente: this.source || null, seguimiento: this.follow || null }, replaceUrl: true });
  }

  private followFilter(): any {
    if (!this.follow) return {};
    const [k, v] = this.follow.split('=');
    return { [k]: k === 'debt' ? true : v };
  }
  /** Filtros actuales en el formato de los segmentos de marketing. */
  segmentParams() {
    const f: any = this.followFilter();
    return { fuente: this.source || null, etiqueta: this.tag || null, tarea: f.task || null, proxima: f.next || null,
      presupuesto: f.budget || null, deuda: f.debt ? '1' : null, visita: f.visit || null };
  }
  ins(id: number) { return this.insights[id] || {}; }
  ago(iso: string) {
    const d = Math.floor((Date.now() - parseLocal(iso).getTime()) / 86400000);
    if (d < 1) return 'hoy';
    if (d < 31) return 'hace ' + d + ' día' + (d > 1 ? 's' : '');
    const m = Math.floor(d / 30.4);
    return m < 12 ? 'hace ' + m + ' mes' + (m > 1 ? 'es' : '') : 'hace ' + Math.floor(m / 12) + ' año' + (m >= 24 ? 's' : '');
  }
  hhmm = hhmm;
  soles = soles;
  go(n: number) { this.fetch(this.q, n).subscribe((p) => this.done(p)); }
  open(p: Patient) { this.router.navigate(['/pacientes', p.id]); }
  ini = initials;
  date = shortDate;

  ngOnDestroy() { this.sub?.unsubscribe(); }
}
