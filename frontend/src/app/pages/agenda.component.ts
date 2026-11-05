import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ToastService } from '../core/toast.service';
import { Appointment, AuditEntry, Block, Dentist } from '../core/models';
import { STATUS_LABEL, STATUS_TONE, apiError, dateTime, hhmm, isoDate, longDay, parseLocal } from '../core/format';
import { IconComponent } from '../shared/icon.component';
import { ModalComponent } from '../shared/modal.component';

const SLOT_PX = 30;     // alto de 15 minutos
const MIN_PER_SLOT = 15;

interface Column { dentist: Dentist; blocks: Block[]; appts: Appointment[]; }

/** Agenda diaria por odontólogo (HU-07 / HU-09). */
@Component({
  selector: 'app-agenda',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent, ModalComponent],
  template: `
    <div class="page wide">
      <div class="page-head">
        <div>
          <h1>Agenda del día</h1>
          <p class="sub">{{ dayLabel }}</p>
        </div>
        <div class="actions">
          <div class="row nav">
            <button class="btn sm" type="button" (click)="shift(-1)" aria-label="Día anterior"><app-icon name="chevronLeft" [size]="16" /></button>
            <button class="btn sm" type="button" (click)="setDate(todayIso)" [disabled]="date === todayIso">Hoy</button>
            <button class="btn sm" type="button" (click)="shift(1)" aria-label="Día siguiente"><app-icon name="chevronRight" [size]="16" /></button>
            <input class="input datein" type="date" [ngModel]="date" (ngModelChange)="setDate($event)" aria-label="Elegir fecha" />
          </div>
          @if (auth.hasRole('ADMIN', 'RECEPCION')) {
            <a class="btn primary" routerLink="/citas/nueva" [queryParams]="{ fecha: date }"><app-icon name="plus" [size]="16" /> Nueva cita</a>
          }
        </div>
      </div>

      <div class="counts">
        @for (s of statusOrder; track s) {
          <span class="tag" [class]="'tag ' + tone(s)">{{ label(s) }} <strong class="tabular">{{ counts[s] || 0 }}</strong></span>
        }
        <span class="grow"></span>
        @if (dentists.length > 1 && !auth.hasRole('ODONTOLOGO')) {
          <select class="input dsel" [ngModel]="filter" (ngModelChange)="filter = $event; load()" aria-label="Filtrar por odontólogo">
            <option [ngValue]="null">Todos los odontólogos</option>
            @for (d of dentists; track d.id) { <option [ngValue]="d.id">{{ d.fullName }}</option> }
          </select>
        }
      </div>

      <section class="panel board">
        @if (loading) {
          <div class="skeleton" style="height: 420px; margin: 16px"></div>
        } @else if (columns.length === 0) {
          <div class="empty"><h3>Ningún odontólogo atiende este día</h3><p>Elige otra fecha o revisa los horarios en Usuarios y accesos.</p></div>
        } @else {
          <div class="grid-head" [style.grid-template-columns]="cols">
            <div></div>
            @for (c of columns; track c.dentist.id) {
              <div class="dh">
                <span class="dot" [style.background]="c.dentist.color"></span>
                <div><div class="strong">{{ c.dentist.fullName }}</div><div class="muted small">{{ c.dentist.specialty }} · {{ c.appts.length }} citas</div></div>
              </div>
            }
          </div>
          <div class="grid-body" [style.grid-template-columns]="cols" [style.height.px]="bodyHeight">
            <div class="times">
              @for (t of hours; track t) { <span class="tl tabular" [style.top.px]="y(t)">{{ t }}</span> }
            </div>
            @for (c of columns; track c.dentist.id) {
              <div class="col">
                @for (t of hours; track t) { <span class="hline" [style.top.px]="y(t)"></span> }
                @for (b of c.blocks; track b.start) {
                  <div class="open" [style.top.px]="y(b.start)" [style.height.px]="y(b.end) - y(b.start)"></div>
                }
                @for (a of c.appts; track a.id) {
                  <button type="button" class="appt" [class]="'appt s-' + a.status"
                          [style.top.px]="y(time(a.startAt)) + 1" [style.height.px]="y(time(a.endAt)) - y(time(a.startAt)) - 2"
                          [style.border-left-color]="c.dentist.color" (click)="open(a)">
                    <span class="t tabular">{{ hhmm(a.startAt) }}–{{ hhmm(a.endAt) }}</span>
                    <span class="n">{{ a.patient.fullName }}</span>
                    <span class="r">{{ a.reason }}</span>
                    <span class="st">{{ label(a.status) }}</span>
                  </button>
                }
              </div>
            }
            @if (nowY !== null) { <div class="now" [style.top.px]="nowY"><span>{{ nowLabel }}</span></div> }
          </div>
        }
      </section>
    </div>

    <app-modal [open]="!!sel" [title]="sel?.patient?.fullName" [subtitle]="sel ? hhmm(sel.startAt) + ' – ' + hhmm(sel.endAt) + ' · ' + sel.dentist.fullName : ''" (close)="sel = null">
      @if (sel) {
        <div class="stack">
          <div class="row wrap">
            <span class="tag" [class]="'tag ' + tone(sel.status)">{{ label(sel.status) }}</span>
            <span class="muted small">{{ sel.reason }} · {{ sel.durationMin }} min</span>
          </div>
          @if (sel.notes) { <p class="small">{{ sel.notes }}</p> }
          @if (sel.statusReason) { <p class="small muted">{{ sel.statusReason }}</p> }
          <dl class="kv">
            <dt>Documento</dt><dd>{{ sel.patient.docType }} {{ sel.patient.docNumber }}</dd>
            <dt>Teléfono</dt><dd>{{ sel.patient.phone }}</dd>
            @if (sel.arrivedAt) { <dt>Llegada</dt><dd>{{ hhmm(sel.arrivedAt) }}</dd> }
            <dt>Programada por</dt><dd>{{ sel.createdBy }} · {{ dt(sel.createdAt) }}</dd>
          </dl>

          @if (canChange) {
            <div class="row wrap actions-row">
              @for (n of nextStates(sel.status); track n) {
                <button type="button" class="btn sm" [class.primary]="n === primaryNext(sel.status)" [class.danger]="n === 'CANCELADA' || n === 'NO_ASISTIO'"
                        (click)="act(n)" [disabled]="busy">{{ actionLabel(n) }}</button>
              }
              @if (canReschedule(sel)) {
                <a class="btn sm" routerLink="/citas/nueva" [queryParams]="{ reprogramar: sel.id }">Reprogramar</a>
              }
            </div>
            @if (cancelling) {
              <div class="field">
                <label for="cr">Motivo de la cancelación</label>
                <input id="cr" class="input" [(ngModel)]="reason" placeholder="Ej. paciente avisó por teléfono" />
                <div class="row" style="justify-content: flex-end; margin-top: 6px">
                  <button type="button" class="btn sm" (click)="cancelling = false">Volver</button>
                  <button type="button" class="btn sm danger" (click)="change('CANCELADA')" [disabled]="!reason.trim() || busy">Cancelar cita</button>
                </div>
              </div>
            }
          }

          <div class="row"><a class="small" [routerLink]="['/pacientes', sel.patient.id]">Abrir ficha del paciente</a></div>

          @if (history.length) {
            <details>
              <summary class="small strong">Historial de la cita</summary>
              <ul class="hist">@for (h of history; track h.id) { <li><span class="muted">{{ dt(h.createdAt) }}</span> {{ h.username }} · {{ h.detail }}</li> }</ul>
            </details>
          }
        </div>
      }
    </app-modal>
  `,
  styles: [`
    .wide { max-width: none; }
    .nav { gap: 6px; }
    .datein { width: 160px; height: 30px; font-size: 13px; }
    .counts { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-bottom: 14px; }
    .counts strong { margin-left: 2px; }
    .dsel { width: 240px; height: 34px; font-size: 13px; }
    .board { overflow-x: auto; }
    .grid-head, .grid-body { display: grid; min-width: 640px; }
    .grid-head { border-bottom: 1px solid var(--line); position: sticky; top: 0; background: var(--surface); z-index: 3; }
    .dh { display: flex; align-items: center; gap: 10px; padding: 12px 14px; border-left: 1px solid var(--line); }
    .dot { width: 10px; height: 10px; border-radius: 50%; flex: none; }
    .grid-body { position: relative; }
    .times { position: relative; }
    .tl { position: absolute; right: 10px; transform: translateY(-50%); font-size: 11.5px; color: var(--muted); }
    .col { position: relative; border-left: 1px solid var(--line); background: repeating-linear-gradient(135deg, #f3f6f6 0 6px, #eef2f2 6px 12px); }
    .hline { position: absolute; left: 0; right: 0; border-top: 1px solid #e3eaeb; z-index: 1; }
    .open { position: absolute; left: 0; right: 0; background: var(--surface); }
    .appt { position: absolute; left: 6px; right: 6px; z-index: 2; display: flex; flex-direction: column; align-items: flex-start; gap: 0; overflow: hidden;
      padding: 5px 9px; border: 1px solid var(--line); border-left: 4px solid; border-radius: 7px; background: #fff; text-align: left; font: inherit; cursor: pointer; color: var(--ink);
      box-shadow: 0 1px 2px rgba(15, 43, 49, .06); }
    .appt:hover { box-shadow: 0 4px 14px -4px rgba(15, 43, 49, .25); z-index: 4; }
    .appt .t { font-size: 11.5px; color: var(--muted); }
    .appt .n { font-weight: 600; font-size: 13px; line-height: 1.25; }
    .appt .r { font-size: 12px; color: var(--ink-2); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 100%; }
    .appt .st { position: absolute; right: 8px; top: 5px; font-size: 11px; font-weight: 600; color: var(--muted); }
    .appt.s-CONFIRMADA .st { color: var(--teal); }
    .appt.s-EN_ESPERA { background: var(--amber-soft); } .appt.s-EN_ESPERA .st { color: var(--amber); }
    .appt.s-EN_ATENCION { background: var(--restore-soft); } .appt.s-EN_ATENCION .st { color: var(--restore); }
    .appt.s-ATENDIDA { background: var(--green-soft); } .appt.s-ATENDIDA .st { color: var(--green); }
    .appt.s-CANCELADA, .appt.s-NO_ASISTIO { background: #fafbfb; opacity: .6; text-decoration: line-through; }
    .now { position: absolute; left: 56px; right: 0; border-top: 2px solid var(--gum); z-index: 5; pointer-events: none; }
    .now span { position: absolute; left: -54px; top: -10px; font-size: 11px; font-weight: 700; color: var(--gum); background: var(--surface); padding: 0 3px; }
    .actions-row { padding-top: 6px; }
    .hist { list-style: none; padding: 0; margin: 8px 0 0; font-size: 12.5px; display: flex; flex-direction: column; gap: 4px; }
  `],
})
export class AgendaComponent implements OnInit, OnChanges, OnDestroy {
  private api = inject(Api);
  private toast = inject(ToastService);
  private router = inject(Router);
  auth = inject(AuthService);

  @Input() fecha?: string;

  todayIso = isoDate();
  date = this.todayIso;
  filter: number | null = null;
  dentists: Dentist[] = [];
  columns: Column[] = [];
  counts: Record<string, number> = {};
  loading = true;
  hours: string[] = [];
  startMin = 8 * 60;
  endMin = 20 * 60;
  bodyHeight = 0;
  cols = '64px';
  nowY: number | null = null;
  nowLabel = '';

  sel: Appointment | null = null;
  history: AuditEntry[] = [];
  cancelling = false;
  reason = '';
  busy = false;

  statusOrder = ['PROGRAMADA', 'CONFIRMADA', 'EN_ESPERA', 'EN_ATENCION', 'ATENDIDA', 'CANCELADA', 'NO_ASISTIO'];
  private timer = setInterval(() => this.placeNow(), 60_000);

  constructor() {
    this.api.dentists().subscribe((d) => (this.dentists = d));
    const me = this.auth.user();
    if (me?.role === 'ODONTOLOGO' && me.dentistId) this.filter = me.dentistId;
  }

  ngOnInit() {
    if (this.fecha && /^\d{4}-\d{2}-\d{2}$/.test(this.fecha)) this.date = this.fecha;
    this.load();
  }

  ngOnChanges(ch: SimpleChanges) {
    const c = ch['fecha'];
    if (c && !c.firstChange && this.fecha && this.fecha !== this.date && /^\d{4}-\d{2}-\d{2}$/.test(this.fecha)) {
      this.date = this.fecha;
      this.load();
    }
  }

  get dayLabel() { return longDay(this.date) + (this.date === this.todayIso ? ' · hoy' : ''); }

  setDate(d: string) {
    if (!d) return;
    this.date = d;
    this.router.navigate([], { queryParams: { fecha: d === this.todayIso ? null : d }, replaceUrl: true });
    this.load();
  }

  shift(n: number) {
    const d = parseLocal(this.date);
    d.setDate(d.getDate() + n);
    this.setDate(isoDate(d));
  }

  load() {
    this.loading = true;
    this.api.day(this.date, this.filter ?? undefined).subscribe({
      next: (r) => {
        this.counts = r.counts;
        this.columns = r.columns
          .map((c) => ({ ...c, appts: r.appointments.filter((a) => a.dentist.id === c.dentist.id) }))
          .filter((c) => c.blocks.length > 0 || c.appts.length > 0);
        this.computeRange();
        this.loading = false;
        this.placeNow();
        if (this.sel) this.sel = r.appointments.find((a) => a.id === this.sel!.id) || null;
      },
      error: () => (this.loading = false),
    });
  }

  private toMin(t: string): number {
    const [h, m] = t.substring(0, 5).split(':').map(Number);
    return h * 60 + m;
  }

  private computeRange() {
    let lo = 9 * 60, hi = 19 * 60;
    for (const c of this.columns) {
      for (const b of c.blocks) { lo = Math.min(lo, this.toMin(b.start)); hi = Math.max(hi, this.toMin(b.end)); }
      for (const a of c.appts) { lo = Math.min(lo, this.toMin(this.time(a.startAt))); hi = Math.max(hi, this.toMin(this.time(a.endAt))); }
    }
    this.startMin = Math.floor(lo / 60) * 60;
    this.endMin = Math.ceil(hi / 60) * 60;
    this.hours = [];
    for (let m = this.startMin; m <= this.endMin; m += 60) this.hours.push(String(Math.floor(m / 60)).padStart(2, '0') + ':00');
    this.bodyHeight = ((this.endMin - this.startMin) / MIN_PER_SLOT) * SLOT_PX + 12;
    this.cols = `64px repeat(${this.columns.length}, minmax(220px, 1fr))`;
  }

  y(t: string): number {
    return ((this.toMin(t) - this.startMin) / MIN_PER_SLOT) * SLOT_PX + 6;
  }

  time(iso: string): string { return hhmm(iso); }

  placeNow() {
    if (this.date !== isoDate()) { this.nowY = null; return; }
    const d = new Date();
    const m = d.getHours() * 60 + d.getMinutes();
    if (m < this.startMin || m > this.endMin) { this.nowY = null; return; }
    this.nowLabel = String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
    this.nowY = ((m - this.startMin) / MIN_PER_SLOT) * SLOT_PX + 6;
  }

  get canChange() { return this.auth.hasRole('ADMIN', 'RECEPCION', 'ODONTOLOGO'); }

  canReschedule(a: Appointment) {
    return this.auth.hasRole('ADMIN', 'RECEPCION') && ['PROGRAMADA', 'CONFIRMADA', 'EN_ESPERA'].includes(a.status);
  }

  nextStates(s: string): string[] {
    const map: Record<string, string[]> = {
      PROGRAMADA: ['CONFIRMADA', 'EN_ESPERA', 'NO_ASISTIO', 'CANCELADA'],
      CONFIRMADA: ['EN_ESPERA', 'NO_ASISTIO', 'CANCELADA'],
      EN_ESPERA: ['EN_ATENCION', 'CANCELADA'],
      EN_ATENCION: ['ATENDIDA'],
    };
    return map[s] || [];
  }

  primaryNext(s: string): string {
    return ({ PROGRAMADA: 'EN_ESPERA', CONFIRMADA: 'EN_ESPERA', EN_ESPERA: 'EN_ATENCION', EN_ATENCION: 'ATENDIDA' } as Record<string, string>)[s] || '';
  }

  actionLabel(n: string): string {
    return ({
      CONFIRMADA: 'Confirmar asistencia', EN_ESPERA: 'Registrar llegada', EN_ATENCION: 'Pasar a atención',
      ATENDIDA: 'Marcar atendida', NO_ASISTIO: 'No asistió', CANCELADA: 'Cancelar cita',
    } as Record<string, string>)[n] || n;
  }

  act(n: string) {
    if (n === 'CANCELADA') this.cancelling = true;
    else this.change(n);
  }

  open(a: Appointment) {
    this.sel = a;
    this.cancelling = false;
    this.reason = '';
    this.history = [];
    this.api.appointmentAudit(a.id).subscribe((h) => (this.history = h));
  }

  change(status: string) {
    if (!this.sel) return;
    this.busy = true;
    this.api.setStatus(this.sel.id, status, status === 'CANCELADA' ? this.reason : undefined).subscribe({
      next: (a) => {
        this.busy = false;
        this.cancelling = false;
        this.toast.ok(`${a.patient.fullName}: ${this.label(a.status).toLowerCase()}.`);
        this.sel = a;
        this.api.appointmentAudit(a.id).subscribe((h) => (this.history = h));
        this.load();
      },
      error: (e) => { this.busy = false; this.toast.error(apiError(e).message); },
    });
  }

  label(s: string) { return STATUS_LABEL[s] || s; }
  tone(s: string) { return STATUS_TONE[s] || 'grey'; }
  hhmm = hhmm;
  dt = dateTime;

  ngOnDestroy() { clearInterval(this.timer); }
}
