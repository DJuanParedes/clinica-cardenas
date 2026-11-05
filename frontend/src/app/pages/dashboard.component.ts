import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ToastService } from '../core/toast.service';
import { Appointment } from '../core/models';
import { STATUS_LABEL, STATUS_TONE, apiError, hhmm, initials, isoDate, longDay, soles } from '../core/format';
import { IconComponent } from '../shared/icon.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, IconComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <p class="muted">{{ today }}</p>
          <h1>{{ greeting }}, {{ firstName }}</h1>
        </div>
        <div class="actions">
          @if (auth.hasRole('ADMIN', 'RECEPCION')) {
            <a class="btn" routerLink="/pacientes/nuevo"><app-icon name="patient" [size]="16" /> Registrar paciente</a>
            <a class="btn primary" routerLink="/citas/nueva"><app-icon name="plus" [size]="16" /> Nueva cita</a>
          }
          @if (auth.hasRole('CAJA')) {
            <a class="btn primary" routerLink="/comprobantes/nuevo"><app-icon name="receipt" [size]="16" /> Emitir comprobante</a>
          }
        </div>
      </div>

      @if (!data) {
        <div class="skeleton" style="height: 92px; margin-bottom: 16px"></div>
        <div class="skeleton" style="height: 320px"></div>
      } @else {
        <section class="figures panel" aria-label="Resumen del día">
          <div class="fig">
            <span class="v tabular">{{ data.appointmentsToday }}</span>
            <span class="l">{{ data.scopedToDentist ? 'tus citas hoy' : 'citas hoy' }}</span>
            <span class="meta">{{ data.statusCounts.ATENDIDA }} atendidas · {{ data.statusCounts.CANCELADA + data.statusCounts.NO_ASISTIO }} canceladas</span>
          </div>
          <div class="fig">
            <span class="v tabular">{{ data.waiting.length }}</span>
            <span class="l">en sala de espera</span>
            <span class="meta">o en atención ahora</span>
          </div>
          @if (auth.hasRole('ADMIN', 'CAJA', 'DIRECTOR')) {
            <div class="fig">
              <span class="v tabular">{{ money(data.billedToday) }}</span>
              <span class="l">facturado hoy</span>
              <span class="meta">{{ money(data.billedMonth) }} en el mes</span>
            </div>
            <div class="fig">
              <span class="v tabular" [class.warn]="data.pendingSunat > 0">{{ data.pendingSunat }}</span>
              <span class="l">comprobantes sin enviar</span>
              @if (data.pendingSunat > 0 && auth.hasRole('ADMIN', 'CAJA')) {
                <a class="meta" routerLink="/comprobantes" [queryParams]="{ status: 'GENERADO' }">Revisar envíos</a>
              } @else { <span class="meta">todo enviado a SUNAT</span> }
            </div>
          } @else {
            <div class="fig">
              <span class="v tabular">{{ data.patientsTotal }}</span>
              <span class="l">pacientes registrados</span>
              <span class="meta">{{ data.patientsThisMonth }} nuevos este mes</span>
            </div>
            <div class="fig">
              <span class="v tabular">{{ data.pendingTreatments }}</span>
              <span class="l">procedimientos por realizar</span>
              <span class="meta">en planes activos</span>
            </div>
          }
        </section>

        <div class="board">
          <section class="panel">
            <div class="panel-head">
              <h3>Próximas citas de hoy</h3>
              @if (auth.hasRole('ADMIN', 'RECEPCION', 'ODONTOLOGO', 'DIRECTOR')) { <a routerLink="/agenda" class="small">Ver agenda completa</a> }
            </div>
            @if (data.upcoming.length === 0) {
              <div class="empty">
                <h3>No quedan citas por atender hoy</h3>
                <p>Las nuevas citas aparecerán aquí en orden de llegada.</p>
              </div>
            } @else {
              <ol class="timeline">
                @for (a of data.upcoming; track a.id) {
                  <li>
                    <span class="time tabular">{{ hhmm(a.startAt) }}</span>
                    <span class="bar" [style.background]="a.dentist.color"></span>
                    <div class="grow">
                      <a [routerLink]="['/pacientes', a.patient.id]" class="strong pname">{{ a.patient.fullName }}</a>
                      <div class="muted small">{{ a.reason }} · {{ a.dentist.fullName }} · {{ a.durationMin }} min</div>
                    </div>
                    <span class="tag" [class]="'tag ' + tone(a.status)">{{ label(a.status) }}</span>
                    @if (auth.hasRole('ADMIN', 'RECEPCION')) {
                      <button type="button" class="btn sm" (click)="arrive(a)" [disabled]="busy === a.id">Registrar llegada</button>
                    }
                  </li>
                }
              </ol>
            }
          </section>

          <section class="panel">
            <div class="panel-head"><h3>Sala de espera</h3></div>
            @if (data.waiting.length === 0) {
              <div class="empty small"><p>Nadie esperando en este momento.</p></div>
            } @else {
              <ul class="waiting">
                @for (a of data.waiting; track a.id) {
                  <li>
                    <span class="avatar">{{ ini(a.patient.fullName) }}</span>
                    <div class="grow">
                      <div class="strong">{{ a.patient.fullName }}</div>
                      <div class="muted small">Cita {{ hhmm(a.startAt) }} · llegó {{ hhmm(a.arrivedAt) }}</div>
                    </div>
                    @if (a.status === 'EN_ESPERA' && auth.hasRole('ADMIN', 'RECEPCION', 'ODONTOLOGO')) {
                      <button type="button" class="btn sm" (click)="move(a, 'EN_ATENCION')" [disabled]="busy === a.id">Pasar a atención</button>
                    } @else if (a.status === 'EN_ATENCION' && auth.hasRole('ADMIN', 'RECEPCION', 'ODONTOLOGO')) {
                      <button type="button" class="btn sm" (click)="move(a, 'ATENDIDA')" [disabled]="busy === a.id">Marcar atendida</button>
                    } @else {
                      <span class="tag" [class]="'tag ' + tone(a.status)">{{ label(a.status) }}</span>
                    }
                  </li>
                }
              </ul>
            }
          </section>
        </div>
      }
    </div>
  `,
  styles: [`
    .figures { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin-bottom: 18px; }
    .fig { display: flex; flex-direction: column; padding: 18px 22px; border-right: 1px solid var(--line); }
    .fig:last-child { border-right: 0; }
    .fig .v { font-size: var(--step-3); font-weight: 600; letter-spacing: -.02em; line-height: 1.1; }
    .fig .v.warn { color: var(--amber); }
    .fig .l { font-weight: 600; color: var(--ink-2); font-size: var(--step--1); margin-top: 2px; }
    .fig .meta { font-size: 12px; color: var(--muted); margin-top: 6px; }
    .board { display: grid; grid-template-columns: minmax(0, 1.7fr) minmax(0, 1fr); gap: 18px; align-items: start; }
    .timeline { list-style: none; margin: 0; padding: 0; }
    .timeline li { display: flex; align-items: center; gap: 14px; padding: 12px 18px; border-bottom: 1px solid var(--line); }
    .timeline li:last-child { border-bottom: 0; }
    .time { font-size: var(--step-1); font-weight: 600; width: 52px; }
    .bar { width: 4px; align-self: stretch; border-radius: 2px; }
    .pname { color: var(--ink); }
    .waiting { list-style: none; margin: 0; padding: 0; }
    .waiting li { display: flex; align-items: center; gap: 12px; padding: 12px 18px; border-bottom: 1px solid var(--line); }
    .waiting li:last-child { border-bottom: 0; }
    @media (max-width: 1100px) { .board { grid-template-columns: 1fr; } }
    @media (max-width: 720px) {
      .figures { grid-template-columns: 1fr 1fr; }
      .fig:nth-child(2) { border-right: 0; }
      .fig:nth-child(-n+2) { border-bottom: 1px solid var(--line); }
      .timeline li { flex-wrap: wrap; gap: 10px; padding: 12px 14px; }
      .timeline li .grow { flex: 1 1 60%; }
    }
  `],
})
export class DashboardComponent implements OnInit {
  private api = inject(Api);
  auth = inject(AuthService);
  private toast = inject(ToastService);

  data: any = null;
  busy: number | null = null;
  today = longDay(isoDate());

  get firstName(): string {
    const n = this.auth.user()?.fullName || '';
    return n.replace(/^(Dra?\.)\s+/i, '').split(' ')[0];
  }

  get greeting(): string {
    const h = new Date().getHours();
    return h < 12 ? 'Buenos días' : h < 19 ? 'Buenas tardes' : 'Buenas noches';
  }

  ngOnInit() { this.load(); }

  load() { this.api.dashboard().subscribe((d) => (this.data = d)); }

  hhmm = hhmm;
  money = soles;
  ini = initials;
  label(s: string) { return STATUS_LABEL[s] || s; }
  tone(s: string) { return STATUS_TONE[s] || 'grey'; }

  arrive(a: Appointment) { this.move(a, 'EN_ESPERA'); }

  move(a: Appointment, status: string) {
    this.busy = a.id;
    this.api.setStatus(a.id, status).subscribe({
      next: () => { this.busy = null; this.load(); },
      error: (e) => { this.busy = null; this.toast.error(apiError(e).message); this.load(); },
    });
  }
}
