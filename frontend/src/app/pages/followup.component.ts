import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ToastService } from '../core/toast.service';
import { Dentist, Patient } from '../core/models';
import { apiError, dateTime, shortDate } from '../core/format';
import { IconComponent } from '../shared/icon.component';
import { ModalComponent } from '../shared/modal.component';
import { PatientPickerComponent } from '../shared/patient-picker.component';

type Tab = 'reprogramar' | 'espera' | 'controles' | 'satisfaccion';
const RECALL: Record<string, string> = { LIMPIEZA: 'Limpieza (6 meses)', ORTODONCIA: 'Control de ortodoncia', CONTROL: 'Control preventivo' };

/** Seguimiento: pedidos de reprogramación por WhatsApp, lista de espera, controles por vencer y satisfacción. */
@Component({
  selector: 'app-followup',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent, ModalComponent, PatientPickerComponent],
  template: `
    <div class="page">
      <header class="page-head">
        <h1>Seguimiento de pacientes</h1>
        <p class="muted">Lo que necesita una llamada o un mensaje hoy.</p>
      </header>

      <nav class="tabs" aria-label="Secciones de seguimiento">
        <a [class.on]="tab === 'reprogramar'" (click)="tab = 'reprogramar'">Quieren reprogramar @if (resched.length) { <span class="count">{{ resched.length }}</span> }</a>
        <a [class.on]="tab === 'espera'" (click)="tab = 'espera'">Lista de espera @if (wait.length) { <span class="count">{{ wait.length }}</span> }</a>
        @if (canRecall) { <a [class.on]="tab === 'controles'" (click)="tab = 'controles'">Controles por vencer @if (recalls.length) { <span class="count">{{ recalls.length }}</span> }</a> }
        <a [class.on]="tab === 'satisfaccion'" (click)="tab = 'satisfaccion'">Satisfacción</a>
      </nav>

      @switch (tab) {
        @case ('reprogramar') {
          <section class="panel">
            <div class="panel-head"><h3>Pidieron otro horario</h3><span class="muted small">Respondieron "2" al recordatorio o lo pidieron desde la app</span></div>
            <table class="table">
              <tbody>
                @for (r of resched; track r.id) {
                  <tr>
                    <td><span class="strong">{{ r.patient }}</span><div class="muted small tabular">{{ r.phone }}</div></td>
                    <td>{{ dt(r.startAt) }}<div class="muted small">{{ r.reason }} · {{ r.dentist }}</div></td>
                    <td class="muted small">pedido {{ dt(r.requestedAt) }}</td>
                    <td class="right">
                      @if (auth.can('CITAS_GESTIONAR')) { <a class="btn sm primary" routerLink="/citas/nueva" [queryParams]="{ reprogramar: r.id }">Reprogramar</a> }
                    </td>
                  </tr>
                } @empty { <tr><td class="muted empty-row">Nadie pidió reprogramar. 🎉</td></tr> }
              </tbody>
            </table>
          </section>
        }
        @case ('espera') {
          <section class="panel">
            <div class="panel-head">
              <h3>Esperan un espacio</h3>
              @if (auth.can('CITAS_GESTIONAR')) { <button class="btn sm primary" type="button" (click)="openAdd()"><app-icon name="plus" [size]="14" /> Agregar</button> }
            </div>
            <p class="muted small pad">Cuando se cancela una cita, se avisa por WhatsApp o correo a los 3 primeros de la lista (del mismo odontólogo o sin preferencia).</p>
            <table class="table">
              <tbody>
                @for (w of wait; track w.entry.id) {
                  <tr>
                    <td><a class="strong" [routerLink]="['/pacientes', w.patient?.id]">{{ w.patient?.fullName }}</a><div class="muted small tabular">{{ w.patient?.phone }}</div></td>
                    <td>{{ w.entry.reason }}<div class="muted small">{{ w.dentist || 'Cualquier odontólogo' }}@if (w.entry.preference) { · {{ w.entry.preference }} }</div></td>
                    <td>@if (w.entry.status === 'AVISADA') { <span class="tag blue">Avisado {{ dt(w.entry.notifiedAt) }}</span> } @else { <span class="tag amber">Esperando</span> }</td>
                    <td class="muted small">desde {{ date(w.entry.createdAt) }}</td>
                    <td class="right">@if (auth.can('CITAS_GESTIONAR')) { <button class="btn sm" type="button" (click)="close(w.entry.id)">Cerrar</button> }</td>
                  </tr>
                } @empty { <tr><td class="muted empty-row">La lista de espera está vacía.</td></tr> }
              </tbody>
            </table>
          </section>
        }
        @case ('controles') {
          <section class="panel">
            <div class="panel-head"><h3>Les toca volver</h3><span class="muted small">Sin cita futura · se envía solo cada lunes a las 10:00</span></div>
            <table class="table">
              <tbody>
                @for (r of recalls; track r.patientId) {
                  <tr>
                    <td><a class="strong" [routerLink]="['/pacientes', r.patientId]">{{ r.fullName }}</a><div class="muted small tabular">{{ r.phone }}</div></td>
                    <td>{{ recallLabel(r.kind) }}<div class="muted small">última visita {{ date(r.lastVisit) }}</div></td>
                    <td class="muted small">@if (r.lastRecallAt) { recordado {{ date(r.lastRecallAt) }} } @else { sin recordatorio }</td>
                    <td class="right">@if (auth.can('CITAS_GESTIONAR')) { <button class="btn sm" type="button" [disabled]="sending === r.patientId" (click)="recall(r)"><app-icon name="send" [size]="14" /> Recordar</button> }</td>
                  </tr>
                } @empty { <tr><td class="muted empty-row">Todos los pacientes están al día con sus controles.</td></tr> }
              </tbody>
            </table>
          </section>
        }
        @case ('satisfaccion') {
          <div class="grid sat">
            <section class="panel big">
              <span class="muted small">Últimos 90 días</span>
              <span class="score tabular">{{ sat?.total ? (+sat.average).toFixed(1) : '—' }}</span>
              <span class="stars" aria-hidden="true">{{ stars(sat?.average) }}</span>
              <span class="muted small">{{ sat?.total || 0 }} encuestas respondidas desde la app</span>
            </section>
            <section class="panel">
              <div class="panel-head"><h3>Distribución</h3></div>
              <div class="panel-body dist">
                @for (s of [5, 4, 3, 2, 1]; track s) {
                  <div class="drow"><span class="tabular">{{ s }} ★</span>
                    <div class="track"><div class="fill" [style.width.%]="pct(s)"></div></div>
                    <span class="tabular muted small">{{ countFor(s) }}</span></div>
                }
              </div>
            </section>
            <section class="panel">
              <div class="panel-head"><h3>Por odontólogo</h3></div>
              <table class="table"><tbody>
                @for (d of sat?.byDentist || []; track d.dentistId) {
                  <tr><td>{{ d.dentist }}</td><td class="num tabular strong">{{ (+d.average).toFixed(1) }} ★</td><td class="num muted small">{{ d.total }}</td></tr>
                } @empty { <tr><td class="muted">Sin encuestas aún.</td></tr> }
              </tbody></table>
            </section>
            <section class="panel span">
              <div class="panel-head"><h3>Comentarios</h3></div>
              <ul class="comments">
                @for (c of sat?.comments || []; track $index) {
                  <li><span class="tabular strong">{{ c.score }} ★</span><div>“{{ c.comment }}”<div class="muted small">{{ c.patient }} · {{ date(c.createdAt) }}</div></div></li>
                } @empty { <li class="muted">Todavía no hay comentarios.</li> }
              </ul>
            </section>
          </div>
        }
      }
    </div>

    <app-modal [open]="addOpen" title="Agregar a la lista de espera" (close)="addOpen = false">
      <div class="stack">
        <div class="field"><label>Paciente</label><app-patient-picker [value]="newPatient" (valueChange)="newPatient = $event" /></div>
        <div class="field"><label for="wr">Motivo</label><input id="wr" class="input" [(ngModel)]="newReason" maxlength="200" placeholder="Limpieza, evaluación, dolor…" /></div>
        <div class="grid two">
          <div class="field"><label for="wd">Odontólogo</label>
            <select id="wd" class="input" [(ngModel)]="newDentist">
              <option [ngValue]="null">Cualquiera</option>
              @for (d of dentists; track d.id) { <option [ngValue]="d.id">{{ d.fullName }}</option> }
            </select></div>
          <div class="field"><label for="wp">Preferencia</label><input id="wp" class="input" [(ngModel)]="newPref" maxlength="200" placeholder="Mañanas, sábados…" /></div>
        </div>
      </div>
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="addOpen = false">Cancelar</button>
        <button class="btn primary" type="button" (click)="add()" [disabled]="!newPatient || !newReason.trim()">Agregar</button>
      </ng-container>
    </app-modal>
  `,
  styles: [`
    .page { display: flex; flex-direction: column; gap: 16px; }
    .count { display: inline-grid; place-items: center; min-width: 20px; height: 20px; padding: 0 6px; margin-left: 4px; border-radius: 999px; background: var(--orange); color: #fff; font-size: 11.5px; font-weight: 800; }
    .pad { padding: 0 18px 8px; margin: 0; }
    .empty-row { padding: 26px 18px; }
    .sat { grid-template-columns: minmax(0, .8fr) minmax(0, 1fr) minmax(0, 1fr); align-items: start; }
    .big { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 26px 18px; text-align: center; }
    .score { font-family: var(--font-display); font-size: 56px; font-weight: 900; line-height: 1; color: var(--teal-strong); }
    .stars { color: var(--orange); font-size: 22px; letter-spacing: 2px; }
    .dist { display: flex; flex-direction: column; gap: 8px; }
    .drow { display: grid; grid-template-columns: 38px 1fr 30px; gap: 10px; align-items: center; font-size: 13px; }
    .track { height: 10px; border-radius: 6px; background: var(--surface-2); overflow: hidden; }
    .fill { height: 100%; background: var(--orange); border-radius: 6px; }
    .span { grid-column: 1 / -1; }
    .comments { list-style: none; margin: 0; padding: 4px 18px 16px; display: flex; flex-direction: column; gap: 12px; }
    .comments li { display: grid; grid-template-columns: 44px 1fr; gap: 10px; }
    @media (max-width: 1000px) { .sat { grid-template-columns: 1fr; } }
  `],
})
export class FollowupComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  auth = inject(AuthService);
  tab: Tab = 'reprogramar';
  resched: any[] = [];
  wait: any[] = [];
  recalls: any[] = [];
  sat: any = null;
  sending: number | null = null;
  dentists: Dentist[] = [];

  addOpen = false;
  newPatient: Patient | null = null;
  newReason = '';
  newDentist: number | null = null;
  newPref = '';

  get canRecall() { return this.auth.can('CITAS_GESTIONAR', 'REPORTES'); }

  ngOnInit() {
    this.api.rescheduleRequests().subscribe((r) => (this.resched = r));
    this.loadWait();
    if (this.canRecall) this.api.recalls().subscribe((r) => (this.recalls = r));
    this.api.satisfaction(90).subscribe((s) => (this.sat = s));
  }
  loadWait() { this.api.waitlist().subscribe((w) => (this.wait = w)); }

  openAdd() {
    this.addOpen = true;
    this.newPatient = null; this.newReason = ''; this.newDentist = null; this.newPref = '';
    if (!this.dentists.length) this.api.dentists().subscribe((d) => (this.dentists = d));
  }
  add() {
    if (!this.newPatient) return;
    this.api.addWaitlist({ patientId: this.newPatient.id, dentistId: this.newDentist, reason: this.newReason, preference: this.newPref }).subscribe({
      next: () => { this.addOpen = false; this.toast.ok('Agregado a la lista de espera.'); this.loadWait(); },
      error: (e) => this.toast.error(apiError(e).message),
    });
  }
  close(id: number) {
    this.api.closeWaitlist(id).subscribe({ next: () => { this.toast.ok('Solicitud cerrada.'); this.loadWait(); }, error: (e) => this.toast.error(apiError(e).message) });
  }
  recall(r: any) {
    this.sending = r.patientId;
    this.api.sendRecall(r.patientId, r.kind).subscribe({
      next: (x) => { this.sending = null; x.queued > 0 ? this.toast.ok(x.message) : this.toast.warn(x.message); r.lastRecallAt = new Date().toISOString().slice(0, 19); },
      error: (e) => { this.sending = null; this.toast.error(apiError(e).message); },
    });
  }

  recallLabel(k: string) { return RECALL[k] || k; }
  dt = dateTime;
  date = shortDate;
  stars(avg: any) { const n = Math.round(+avg || 0); return '★★★★★'.slice(0, n) + '☆☆☆☆☆'.slice(0, 5 - n); }
  countFor(s: number) { return +(this.sat?.distribution || []).find((d: any) => +d.score === s)?.count || 0; }
  pct(s: number) { const t = +this.sat?.total || 0; return t ? (this.countFor(s) * 100) / t : 0; }
}
