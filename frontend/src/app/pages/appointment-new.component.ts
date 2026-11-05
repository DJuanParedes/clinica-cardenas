import { Component, Input, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { ToastService } from '../core/toast.service';
import { Appointment, Dentist, Patient, Slot } from '../core/models';
import { apiError, hhmm, isoDate, longDay, parseLocal } from '../core/format';
import { IconComponent } from '../shared/icon.component';
import { PatientPickerComponent } from '../shared/patient-picker.component';

const REASONS = ['Evaluación inicial', 'Control', 'Profilaxis', 'Dolor dental', 'Endodoncia', 'Restauración', 'Control de ortodoncia', 'Exodoncia'];
const WEEK = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];

/** HU-07 programar cita según disponibilidad · HU-08 reprogramar. */
@Component({
  selector: 'app-appointment-new',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent, PatientPickerComponent],
  template: `
    <div class="page">
      <a class="back" routerLink="/agenda"><app-icon name="chevronLeft" [size]="16" /> Agenda</a>
      <div class="page-head">
        <div>
          <h1>{{ editing ? 'Reprogramar cita' : 'Nueva cita' }}</h1>
          <p class="sub">Solo se muestran horarios libres dentro del horario de atención del odontólogo.</p>
        </div>
      </div>

      <div class="layout">
        <div class="steps">
          <section class="panel">
            <div class="panel-head"><h3><span class="n">1</span> Paciente</h3></div>
            <div class="panel-body">
              <app-patient-picker [value]="patient" (valueChange)="patient = $event" [locked]="!!editing" />
            </div>
          </section>

          <section class="panel">
            <div class="panel-head"><h3><span class="n">2</span> Odontólogo, día y duración</h3></div>
            <div class="panel-body stack">
              <div class="dentists" role="radiogroup" aria-label="Odontólogo">
                @for (d of dentists; track d.id) {
                  <button type="button" class="dcard" [class.on]="dentist?.id === d.id" (click)="pickDentist(d)" role="radio" [attr.aria-checked]="dentist?.id === d.id">
                    <span class="dot" [style.background]="d.color"></span>
                    <span><span class="strong block">{{ d.fullName }}</span><span class="muted small">{{ d.specialty }}</span></span>
                  </button>
                }
              </div>

              <div class="days" role="radiogroup" aria-label="Día">
                @for (d of days; track d.iso) {
                  <button type="button" class="day" [class.on]="date === d.iso" (click)="pickDate(d.iso)" role="radio" [attr.aria-checked]="date === d.iso">
                    <span class="wd">{{ d.wd }}</span><span class="dn tabular">{{ d.n }}</span>
                  </button>
                }
                <input class="input datein" type="date" [min]="todayIso" [ngModel]="date" (ngModelChange)="pickDate($event)" aria-label="Otra fecha" />
              </div>

              <div class="row wrap">
                <span class="label">Duración</span>
                <div class="seg">
                  @for (m of durations; track m) {
                    <button type="button" [class.on]="duration === m" (click)="pickDuration(m)">{{ m }} min</button>
                  }
                </div>
              </div>
            </div>
          </section>

          <section class="panel">
            <div class="panel-head">
              <h3><span class="n">3</span> Horario libre</h3>
              @if (slots.length) { <span class="muted small">{{ freeCount }} disponibles · {{ longDate }}</span> }
            </div>
            <div class="panel-body">
              @if (!dentist) {
                <p class="muted">Elige un odontólogo para ver sus horarios libres.</p>
              } @else if (loadingSlots) {
                <div class="skeleton" style="height: 120px"></div>
              } @else if (!works) {
                <div class="notice"><app-icon name="calendar" /> {{ dentist.fullName }} no atiende el {{ longDate }}. Prueba otro día.</div>
              } @else if (freeCount === 0) {
                <div class="notice amber"><app-icon name="alert" /> No quedan horarios libres de {{ duration }} min ese día. Prueba con otra duración u otro día.</div>
              }
              @if (dentist && !loadingSlots && works) {
                @for (g of groups; track g.label) {
                  @if (g.slots.length) {
                    <div class="sgroup">
                      <span class="glabel">{{ g.label }}</span>
                      <div class="slots">
                        @for (s of g.slots; track s.start) {
                          <button type="button" class="slot tabular" [class.on]="slot?.start === s.start" [class.busy]="s.reason === 'OCUPADO'"
                                  [class.past]="s.reason === 'PASADO'" [disabled]="!s.available" (click)="slot = s"
                                  [attr.title]="s.reason === 'OCUPADO' ? 'Ocupado' : s.reason === 'PASADO' ? 'Hora pasada' : 'Libre'">
                            {{ hhmm(s.start) }}
                          </button>
                        }
                      </div>
                    </div>
                  }
                }
              }
            </div>
          </section>

          <section class="panel">
            <div class="panel-head"><h3><span class="n">4</span> Motivo</h3></div>
            <div class="panel-body stack">
              <div class="row wrap">
                @for (r of reasons; track r) {
                  <button type="button" class="chip" [class.on]="reason === r" (click)="reason = r">{{ r }}</button>
                }
              </div>
              <div class="grid cols-2">
                <div class="field">
                  <label class="req" for="rs">Motivo de la cita</label>
                  <input id="rs" class="input" [(ngModel)]="reason" maxlength="200" />
                </div>
                <div class="field">
                  <label for="nt">Notas para el odontólogo</label>
                  <input id="nt" class="input" [(ngModel)]="notes" maxlength="500" placeholder="Opcional" />
                </div>
                @if (editing) {
                  <div class="field span-all">
                    <label class="req" for="cr">Motivo de la reprogramación</label>
                    <input id="cr" class="input" [(ngModel)]="changeReason" placeholder="Ej. paciente pidió cambiar por trabajo" />
                  </div>
                }
              </div>
            </div>
          </section>
        </div>

        <aside class="summary panel">
          <div class="panel-head"><h3>Resumen</h3></div>
          <div class="panel-body">
            <dl class="sum">
              <dt>Paciente</dt><dd>{{ patient?.fullName || '—' }}</dd>
              <dt>Odontólogo</dt><dd>{{ dentist?.fullName || '—' }}</dd>
              <dt>Fecha</dt><dd>{{ longDate }}</dd>
              <dt>Hora</dt><dd class="big tabular">{{ slot ? hhmm(slot.start) + ' – ' + hhmm(slot.end) : '—' }}</dd>
              <dt>Motivo</dt><dd>{{ reason || '—' }}</dd>
            </dl>
            @if (editing) {
              <div class="notice small was">Antes: {{ wasLabel }}</div>
            }
            @if (missing) { <p class="muted small">Falta: {{ missing }}.</p> }
            <button type="button" class="btn primary lg block" [disabled]="!!missing || saving" (click)="save()">
              @if (saving) { <span class="spin"></span> } {{ editing ? 'Guardar nueva fecha' : 'Programar cita' }}
            </button>
          </div>
        </aside>
      </div>
    </div>
  `,
  styles: [`
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 18px; align-items: start; }
    .steps { display: flex; flex-direction: column; gap: 16px; }
    h3 .n { display: inline-grid; place-items: center; width: 22px; height: 22px; border-radius: 50%; background: var(--night); color: #fff; font-size: 12px; margin-right: 8px; }
    .block { display: block; }
    .dentists { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 8px; }
    .dcard { display: flex; align-items: center; gap: 12px; padding: 12px 14px; border: 1px solid var(--line-strong); border-radius: var(--radius); background: var(--surface); font: inherit; text-align: left; cursor: pointer; color: inherit; }
    .dcard:hover { border-color: var(--ink-2); }
    .dcard.on { border-color: var(--teal); box-shadow: inset 0 0 0 1px var(--teal); background: #f4faf9; }
    .dot { width: 12px; height: 12px; border-radius: 50%; flex: none; }
    .days { display: flex; gap: 6px; flex-wrap: wrap; align-items: stretch; }
    .day { display: flex; flex-direction: column; align-items: center; justify-content: center; width: 56px; height: 58px; border: 1px solid var(--line-strong); border-radius: var(--radius-s); background: var(--surface); font: inherit; cursor: pointer; color: inherit; }
    .day .wd { font-size: 11.5px; color: var(--muted); }
    .day .dn { font-size: 19px; font-weight: 600; }
    .day.on { background: var(--teal); color: #fff; border-color: var(--teal); }
    .day.on .wd { color: #cdeefa; }
    .datein { width: 168px; height: 58px; }
    .sgroup { display: flex; gap: 14px; align-items: flex-start; padding: 8px 0; }
    .sgroup + .sgroup { border-top: 1px solid var(--line); }
    .glabel { width: 64px; font-size: 12.5px; font-weight: 600; color: var(--muted); padding-top: 8px; }
    .slots { display: flex; flex-wrap: wrap; gap: 6px; flex: 1; }
    .slot { width: 74px; height: 36px; border: 1px solid #bfdcd8; border-radius: var(--radius-s); background: #f4faf9; color: var(--teal-strong); font: inherit; font-weight: 600; font-size: 14px; cursor: pointer; }
    .slot:hover:not(:disabled) { background: var(--teal-soft); border-color: var(--teal); }
    .slot.on { background: var(--teal); color: #fff; border-color: var(--teal); }
    .slot.busy { background: var(--surface-2); color: #a5b5b8; border-color: var(--line); text-decoration: line-through; cursor: not-allowed; }
    .slot.past { background: transparent; color: #b8c6c8; border-style: dashed; border-color: var(--line); cursor: not-allowed; }
    .chip { height: 30px; padding: 0 12px; border: 1px solid var(--line-strong); border-radius: 999px; background: var(--surface); font: inherit; font-size: 13px; cursor: pointer; }
    .chip.on { background: var(--teal-soft); border-color: var(--teal); color: var(--teal-strong); }
    .summary { position: sticky; top: 20px; }
    .sum { display: grid; grid-template-columns: 1fr; gap: 2px; margin: 0 0 16px; }
    .sum dt { font-size: 12px; color: var(--muted); margin-top: 8px; }
    .sum dd { margin: 0; font-weight: 600; }
    .sum .big { font-size: var(--step-2); font-weight: 600; }
    .was { margin-bottom: 12px; }
    @media (max-width: 1000px) { .layout { grid-template-columns: 1fr; } .summary { position: static; } }
  `],
})
export class AppointmentNewComponent implements OnInit {
  private api = inject(Api);
  private router = inject(Router);
  private toast = inject(ToastService);

  @Input() paciente?: string;
  @Input() fecha?: string;
  @Input() reprogramar?: string;

  todayIso = isoDate();
  dentists: Dentist[] = [];
  patient: Patient | null = null;
  dentist: Dentist | null = null;
  date = this.todayIso;
  duration = 30;
  durations = [30, 45, 60, 90];
  slots: Slot[] = [];
  works = true;
  slot: Slot | null = null;
  loadingSlots = false;
  reason = '';
  notes = '';
  changeReason = '';
  reasons = REASONS;
  saving = false;
  editing: Appointment | null = null;
  days: { iso: string; wd: string; n: number }[] = [];

  ngOnInit() {
    const base = new Date();
    for (let i = 0; i < 10; i++) {
      const d = new Date(base.getFullYear(), base.getMonth(), base.getDate() + i);
      if (d.getDay() === 0) continue;
      this.days.push({ iso: isoDate(d), wd: i === 0 ? 'Hoy' : WEEK[d.getDay()], n: d.getDate() });
    }
    if (this.fecha && this.fecha >= this.todayIso) this.date = this.fecha;

    this.api.dentists().subscribe((list) => {
      this.dentists = list;
      if (!this.reprogramar && list.length === 1) this.pickDentist(list[0]);
    });

    if (this.paciente) this.api.patient(+this.paciente).subscribe((r) => (this.patient = r.patient));

    if (this.reprogramar) {
      this.loadAppointment(+this.reprogramar);
    }
  }

  private loadAppointment(id: number) {
    // Precarga paciente, odontólogo y motivo de la cita a reprogramar.
    this.api.appointment(id).subscribe((a) => {
      this.editing = a;
      this.patient = a.patient;
      this.reason = a.reason;
      this.notes = a.notes || '';
      this.duration = this.durations.includes(a.durationMin) ? a.durationMin : 30;
      this.dentist = a.dentist;
      this.date = a.startAt.substring(0, 10) >= this.todayIso ? a.startAt.substring(0, 10) : this.todayIso;
      this.loadSlots();
    });
  }

  get wasLabel(): string {
    const a = this.editing;
    return a ? `${longDay(a.startAt.substring(0, 10))}, ${hhmm(a.startAt)} con ${a.dentist.fullName}` : '';
  }

  get longDate(): string { return longDay(this.date); }

  get freeCount(): number { return this.slots.filter((s) => s.available).length; }

  get groups() {
    return [
      { label: 'Mañana', slots: this.slots.filter((s) => parseLocal(s.start).getHours() < 13) },
      { label: 'Tarde', slots: this.slots.filter((s) => parseLocal(s.start).getHours() >= 13) },
    ];
  }

  get missing(): string {
    const m: string[] = [];
    if (!this.patient) m.push('paciente');
    if (!this.dentist) m.push('odontólogo');
    if (!this.slot) m.push('horario');
    if (!this.reason.trim()) m.push('motivo');
    if (this.editing && !this.changeReason.trim()) m.push('motivo de la reprogramación');
    return m.join(', ');
  }

  pickDentist(d: Dentist) { this.dentist = d; this.loadSlots(); }
  pickDate(iso: string) { if (!iso || iso < this.todayIso) return; this.date = iso; this.loadSlots(); }
  pickDuration(m: number) { this.duration = m; this.loadSlots(); }

  loadSlots() {
    if (!this.dentist) return;
    this.slot = null;
    this.loadingSlots = true;
    this.api.slots(this.dentist.id, this.date, this.duration, this.editing?.id).subscribe({
      next: (r) => { this.slots = r.slots; this.works = r.works; this.loadingSlots = false; },
      error: (e) => { this.loadingSlots = false; this.toast.error(apiError(e).message); },
    });
  }

  save() {
    if (this.missing || !this.slot || !this.patient || !this.dentist) return;
    this.saving = true;
    const body = {
      patientId: this.patient.id, dentistId: this.dentist.id, startAt: this.slot.start, durationMin: this.duration,
      reason: this.reason.trim(), notes: this.notes.trim() || null, changeReason: this.changeReason.trim() || null,
    };
    const req = this.editing ? this.api.reschedule(this.editing.id, body) : this.api.createAppointment(body);
    req.subscribe({
      next: (a) => {
        this.toast.ok(`${this.editing ? 'Cita reprogramada' : 'Cita programada'}: ${a.patient.fullName}, ${longDay(a.startAt.substring(0, 10)).toLowerCase()} a las ${hhmm(a.startAt)}.`);
        this.router.navigate(['/agenda'], { queryParams: { fecha: a.startAt.substring(0, 10) } });
      },
      error: (e) => {
        this.saving = false;
        const err = apiError(e);
        this.toast.error(err.message);
        if (err.code === 'CRUCE_HORARIO' || err.code === 'FUERA_DE_HORARIO') this.loadSlots();
      },
    });
  }

  hhmm = hhmm;
}
