import { Component, Input, OnChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api, NotificationItem } from '../core/api.service';
import { ToastService } from '../core/toast.service';
import { AuthService } from '../core/auth.service';
import { Appointment, AuditEntry, Patient } from '../core/models';
import { CHANNEL_LABEL, NOTIF_STATUS, NOTIF_TONE, NOTIF_TYPE, STATUS_LABEL, STATUS_TONE, apiError, dateTime, hhmm, initials, longDay, shortDate } from '../core/format';
import { AccountTabComponent } from './account-tab.component';
import { IconComponent } from '../shared/icon.component';
import { HistoryTabComponent } from './clinical/history-tab.component';
import { OdontogramTabComponent } from './clinical/odontogram-tab.component';
import { PlanTabComponent } from './clinical/plan-tab.component';
import { FilesTabComponent } from './clinical/files-tab.component';

type Tab = 'resumen' | 'historia' | 'odontograma' | 'plan' | 'archivos' | 'cuenta';

@Component({
  selector: 'app-patient-detail',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent, HistoryTabComponent, OdontogramTabComponent, PlanTabComponent, AccountTabComponent, FilesTabComponent],
  template: `
    <div class="page">
      <a class="back" routerLink="/pacientes"><app-icon name="chevronLeft" [size]="16" /> Pacientes</a>

      @if (!p) {
        <div class="skeleton" style="height: 110px"></div>
      } @else {
        <header class="hero panel">
          <span class="avatar lg">{{ ini(p.fullName) }}</span>
          <div class="grow">
            <h1>{{ p.fullName }}</h1>
            <div class="facts">
              <span class="tabular">{{ p.docType }} {{ p.docNumber }}</span>
              <span>{{ p.age }} años · {{ p.sex === 'F' ? 'Femenino' : 'Masculino' }}</span>
              <span class="tabular"><app-icon name="phone" [size]="14" /> {{ p.phone }}</span>
              @if (p.email) { <span><app-icon name="mail" [size]="14" /> {{ p.email }}</span> }
            </div>
          </div>
          <div class="next">
            @if (next) {
              <span class="muted small">Próxima cita</span>
              <span class="strong">{{ day(next.startAt) }}, {{ hhmm(next.startAt) }}</span>
              <span class="muted small">{{ next.dentist.fullName }} · {{ next.reason }}</span>
              @if (nextIsMine) { <span class="tag teal">Es contigo</span> }
            } @else {
              <span class="muted small">Sin citas programadas</span>
            }
          </div>
          <div class="actions">
            @if (auth.can('PACIENTES_EDITAR')) {
              <a class="btn" [routerLink]="['/pacientes', p.id, 'editar']"><app-icon name="edit" [size]="16" /> Editar datos</a>
            }
            @if (auth.can('CITAS_GESTIONAR')) {
              <a class="btn primary" routerLink="/citas/nueva" [queryParams]="{ paciente: p.id }"><app-icon name="calendar" [size]="16" /> Programar cita</a>
            }
            @if (auth.can('CAJA_COBRAR')) {
              <a class="btn" routerLink="/caja/cobro" [queryParams]="{ paciente: p.id }"><app-icon name="wallet" [size]="16" /> Registrar cobro</a>
            }
            @if (auth.can('COMPROBANTES')) {
              <a class="btn" routerLink="/comprobantes/nuevo" [queryParams]="{ paciente: p.id }"><app-icon name="receipt" [size]="16" /> Emitir comprobante</a>
            }
          </div>
        </header>

        @if (clinical && (alerts || pending.length)) {
          <section class="clin panel">
            <div class="alerts">
              @if (alerts?.allergies) { <div class="al red"><app-icon name="alert" [size]="16" /><div><strong>Alergias</strong><div>{{ alerts.allergies }}</div></div></div> }
              @else { <div class="al grey"><app-icon name="check" [size]="16" /><div><strong>Alergias</strong><div>{{ alerts ? 'No refiere' : 'Historia clínica sin llenar' }}</div></div></div> }
              @if (alerts?.medication) { <div class="al amber"><app-icon name="clipboard" [size]="16" /><div><strong>Medicación actual</strong><div>{{ alerts.medication }}</div></div></div> }
              @if (alerts?.background) { <div class="al amber"><app-icon name="activity" [size]="16" /><div><strong>Antecedentes</strong><div>{{ alerts.background }}</div></div></div> }
            </div>
            <div class="todo">
              <strong>Procedimientos por realizar</strong>
              @if (pending.length) {
                <ul>@for (x of pending; track x.id) { <li>{{ x.procedure }}{{ x.tooth ? ' · pieza ' + x.tooth : '' }} <span class="muted small">{{ x.plan }}{{ x.dentist ? ' · ' + x.dentist : '' }}</span></li> }</ul>
              } @else { <p class="muted small">No tiene procedimientos pendientes en su plan.</p> }
            </div>
          </section>
        }

        <nav class="tabs" aria-label="Secciones de la ficha">
          <a [class.on]="tab === 'resumen'" (click)="go('resumen')">Datos y citas</a>
          @if (clinical) {
            <a [class.on]="tab === 'historia'" (click)="go('historia')">Historia clínica</a>
            <a [class.on]="tab === 'odontograma'" (click)="go('odontograma')">Odontograma</a>
          }
          @if (canPlan) { <a [class.on]="tab === 'plan'" (click)="go('plan')">Plan de tratamiento</a> }
          <a [class.on]="tab === 'archivos'" (click)="go('archivos')">Fotos y documentos</a>
          @if (canAccount) { <a [class.on]="tab === 'cuenta'" (click)="go('cuenta')">Cuenta y pagos</a> }
        </nav>

        @switch (tab) {
          @case ('historia') { <app-history-tab [patientId]="p.id" /> }
          @case ('odontograma') { <app-odontogram-tab [patientId]="p.id" /> }
          @case ('plan') { <app-plan-tab [patientId]="p.id" /> }
          @case ('cuenta') { <app-account-tab [patientId]="p.id" /> }
          @case ('archivos') { <app-files-tab [patientId]="p.id" [patientName]="p.fullName" /> }
          @default {
            <div class="grid summary">
              <section class="panel">
                <div class="panel-head"><h3>Datos personales y de contacto</h3></div>
                <div class="panel-body">
                  <dl class="kv">
                    <dt>Fecha de nacimiento</dt><dd>{{ date(p.birthDate) }}</dd>
                    <dt>Dirección</dt><dd>{{ addressLine(p) }}</dd>
                    @if (p.minor) {
                      <dt>Representante legal</dt><dd><strong>{{ p.guardianName }}</strong> · {{ relLabel(p.guardianRelationship) }}<div class="muted small">{{ p.guardianDocType }} {{ p.guardianDocNumber }} · {{ p.guardianPhone }}</div></dd>
                    } @else {
                      <dt>Contacto de emergencia</dt><dd>{{ p.emergencyContactName || '—' }}{{ p.emergencyContactRelationship ? ' (' + p.emergencyContactRelationship + ')' : '' }} {{ p.emergencyContactPhone ? '· ' + p.emergencyContactPhone : '' }}</dd>
                    }
                    <dt>Notas</dt><dd>{{ p.notes || '—' }}</dd>
                    <dt>Datos personales</dt>
                    <dd>
                      @if (p.dataConsent) {
                        <span class="tag green">Consentimiento Ley 29733{{ p.consentVersion ? ' · v' + p.consentVersion : '' }}</span>
                        @if (consent?.acceptedBy) { <div class="muted small">Aceptó: {{ consent.acceptedBy }} · {{ consentChannel(p.consentChannel) }}</div> }
                        @if (p.consentFileId) { <div class="small"><a (click)="go('archivos')" class="link">Copia firmada adjunta</a></div> }
                        @else if (auth.can('PACIENTES_EDITAR')) {
                          <label class="btn sm up"><app-icon name="upload" [size]="14" /> {{ uploading ? 'Subiendo…' : 'Adjuntar copia firmada' }}
                            <input type="file" accept="application/pdf,image/jpeg,image/png" hidden (change)="uploadSigned($event)" [disabled]="uploading" /></label>
                          <div class="muted small">Imprime la política, que la firme {{ p.minor ? 'el representante legal' : 'el paciente' }} y adjunta la foto o el PDF.</div>
                        }
                      } @else { <span class="tag amber">Sin consentimiento registrado</span> <div class="muted small">Edita los datos del paciente para registrar su aceptación.</div> }
                    </dd>
                    <dt>Registro</dt><dd class="muted">{{ dt(p.createdAt) }} por {{ p.createdBy }}</dd>
                    @if (p.updatedAt) { <dt>Última modificación</dt><dd class="muted">{{ dt(p.updatedAt) }} por {{ p.updatedBy }}</dd> }
                  </dl>
                </div>
                @if (audit.length) {
                  <div class="panel-foot">
                    <details>
                      <summary class="small strong">Registro de cambios ({{ audit.length }})</summary>
                      <ul class="audit">
                        @for (a of audit; track a.id) { <li><span class="muted">{{ dt(a.createdAt) }}</span> {{ a.username }} · {{ a.detail }}</li> }
                      </ul>
                    </details>
                  </div>
                }
              </section>

              <section class="panel">
                <div class="panel-head"><h3>Citas</h3><span class="muted small">{{ appts.length }} en total</span></div>
                @if (appts.length === 0) {
                  <div class="empty small"><p>Este paciente aún no tiene citas.</p></div>
                } @else {
                  <table class="table">
                    <tbody>
                      @for (a of appts; track a.id) {
                        <tr>
                          <td class="tabular nowrap">{{ date(a.startAt) }}<div class="muted small">{{ hhmm(a.startAt) }} – {{ hhmm(a.endAt) }}</div></td>
                          <td>{{ a.reason }}<div class="muted small">{{ a.dentist.fullName }}</div></td>
                          <td class="right"><span class="tag" [class]="'tag ' + tone(a.status)">{{ label(a.status) }}</span></td>
                        </tr>
                      }
                    </tbody>
                  </table>
                }
              </section>

              <section class="panel span-all">
                <div class="panel-head">
                  <h3>Recordatorios y avisos</h3>
                  @if (auth.can('PACIENTES_EDITAR')) {
                    <div class="row">
                      <label for="nc" class="muted small">Recibe avisos por</label>
                      <select id="nc" class="input chan" [ngModel]="p.notifyChannel || 'WHATSAPP'" (ngModelChange)="setChannel($event)">
                        @for (c of channels; track c) { <option [value]="c">{{ channelLabel(c) }}</option> }
                      </select>
                    </div>
                  } @else { <span class="muted small">Avisos por {{ channelLabel(p.notifyChannel || 'WHATSAPP') }}</span> }
                </div>
                @if (notifs.length === 0) {
                  <div class="empty small"><p>Aún no se enviaron recordatorios a este paciente.</p></div>
                } @else {
                  <table class="table">
                    <tbody>
                      @for (n of notifs.slice(0, 6); track n.id) {
                        <tr>
                          <td class="tabular nowrap muted small">{{ dt(n.sentAt || n.scheduledFor || n.createdAt) }}</td>
                          <td><span class="strong">{{ nType(n.type) }}</span> <span class="muted small">por {{ channelLabel(n.channel) }}</span></td>
                          <td class="right"><span class="tag" [class]="'tag ' + nTone(n.status)">{{ nStatus(n.status) }}</span></td>
                        </tr>
                      }
                    </tbody>
                  </table>
                }
              </section>
            </div>
          }
        }
      }
    </div>
  `,
  styles: [`
    .clin { display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr); gap: 14px; padding: 14px 16px; margin-bottom: 14px; }
    .alerts { display: flex; flex-wrap: wrap; gap: 8px; }
    .al { display: flex; gap: 8px; align-items: flex-start; padding: 8px 12px; border-radius: 12px; min-width: 180px; flex: 1; font-size: 13px; line-height: 1.35; }
    .al.red { background: #fde8e6; color: #8a1c12; border: 1px solid #f3b4ac; }
    .al.amber { background: #fff4dc; color: #6b4a00; border: 1px solid #f1d38c; }
    .al.grey { background: var(--surface-2, #f4f6f8); color: var(--ink-2); border: 1px solid var(--line); }
    .todo ul { margin: 6px 0 0; padding-left: 18px; font-size: 13.5px; }
    .up { margin-top: 6px; }
    @media (max-width: 900px) { .clin { grid-template-columns: 1fr; } }
    .hero { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 14px 18px; padding: 20px 22px; margin-bottom: 18px;
      background: linear-gradient(100deg, #fff 55%, var(--brand-soft)); }
    .hero .actions:empty { display: none; }
    .hero .actions { grid-column: 1 / -1; justify-content: flex-end; padding-top: 14px; border-top: 1px solid var(--line); }
    .hero h1 { font-size: var(--step-2); }
    .facts { display: flex; flex-wrap: wrap; gap: 6px 18px; margin-top: 6px; color: var(--ink-2); font-size: var(--step--1); }
    .facts span { display: inline-flex; align-items: center; gap: 5px; }
    .next { display: flex; flex-direction: column; padding: 0 20px; border-left: 1px solid var(--line); min-width: 180px; }
    .actions { display: flex; gap: 8px; flex-wrap: wrap; }
    .summary { grid-template-columns: minmax(0, 1fr) minmax(0, 1.1fr); align-items: start; }
    .audit { list-style: none; padding: 0; margin: 10px 0 0; font-size: 12.5px; display: flex; flex-direction: column; gap: 6px; }
    .nowrap { white-space: nowrap; }
    .chan { width: 190px; height: 32px; font-size: 13px; }
    .span-all { grid-column: 1 / -1; }
    @media (max-width: 1000px) { .summary { grid-template-columns: 1fr; } .hero { grid-template-columns: auto minmax(0, 1fr); } .next { grid-column: 1 / -1; border-left: 0; padding: 0; } .hero .actions { justify-content: flex-start; } }
  `],
})
export class PatientDetailComponent implements OnChanges {
  private api = inject(Api);
  private router = inject(Router);
  auth = inject(AuthService);

  @Input() id!: string;
  @Input() tab: Tab = 'resumen';

  p: Patient | null = null;
  next: Appointment | null = null;
  nextIsMine = false;
  alerts: any = null;
  pending: any[] = [];
  consent: any = null;
  uploading = false;
  appts: Appointment[] = [];
  audit: AuditEntry[] = [];
  notifs: NotificationItem[] = [];
  channels = ['WHATSAPP', 'EMAIL', 'AMBOS', 'NINGUNO'];
  private toast = inject(ToastService);

  setChannel(c: string) {
    if (!this.p) return;
    const prev = this.p.notifyChannel;
    this.p.notifyChannel = c;
    this.api.setNotificationChannel(this.p.id, c).subscribe({
      next: () => this.toast.ok('Preferencia de avisos actualizada.'),
      error: (e) => { if (this.p) this.p.notifyChannel = prev; this.toast.error(apiError(e).message); },
    });
  }

  channelLabel(c: string) { return CHANNEL_LABEL[c] || c; }
  nType(t: string) { return NOTIF_TYPE[t] || t; }
  nStatus(s: string) { return NOTIF_STATUS[s] || s; }
  nTone(s: string) { return NOTIF_TONE[s] || 'grey'; }

  get clinical(): boolean { return this.auth.can('HISTORIA_CLINICA'); }
  get canPlan(): boolean { return this.auth.can('PLAN_TRATAMIENTO', 'HISTORIA_CLINICA'); }
  get canAccount(): boolean { return this.auth.can('CAJA_COBRAR'); }

  ngOnChanges() {
    const allowed: Record<string, boolean> = { resumen: true, historia: this.clinical, odontograma: this.clinical, plan: this.canPlan, archivos: true, cuenta: this.canAccount };
    if (!this.tab || !allowed[this.tab]) this.tab = 'resumen';
    const id = +this.id;
    if (this.p?.id === id) return;
    this.api.patient(id).subscribe((r: any) => {
      this.p = r.patient; this.next = r.nextAppointment || null; this.nextIsMine = !!r.nextIsMine;
      this.alerts = r.clinicalAlerts || null; this.pending = r.pendingProcedures || [];
      if (this.alerts && !this.alerts.allergies && !this.alerts.medication && !this.alerts.background) this.alerts = { ...this.alerts, empty: true };
    });
    this.api.patientConsents(id).subscribe({ next: (c) => (this.consent = c), error: () => {} });
    this.api.patientAppointments(id).subscribe((a) => (this.appts = a));
    if (this.auth.can('PACIENTES_EDITAR')) this.api.patientAudit(id).subscribe((a) => (this.audit = a));
    this.api.patientNotifications(id).subscribe((n) => (this.notifs = n.filter((x) => x.operation !== 'ANULAR')));
  }

  relLabel(r?: string) { return r === 'PADRE' ? 'Padre' : r === 'MADRE' ? 'Madre' : 'Tutor legal'; }
  consentChannel(c?: string) { return c === 'APP' ? 'en la app' : c === 'PORTAL' ? 'en el portal' : 'presencial en recepción'; }

  uploadSigned(ev: Event) {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || !this.p) return;
    this.uploading = true;
    this.api.uploadSignedConsent(this.p.id, file).subscribe({
      next: (c) => { this.uploading = false; this.consent = c; if (this.p) this.p.consentFileId = c.signedFileId; this.toast.ok('Copia firmada adjuntada a la ficha.'); },
      error: (e) => { this.uploading = false; this.toast.error(apiError(e).message); },
    });
  }

  go(t: Tab) {
    this.tab = t;
    this.router.navigate([], { queryParams: { tab: t === 'resumen' ? null : t }, replaceUrl: true });
  }

  addressLine(p: Patient): string {
    return [p.address, p.district, p.province !== p.department ? p.province : null, p.department].filter((x) => !!x).join(', ') || '—';
  }

  ini = initials;
  hhmm = hhmm;
  date = shortDate;
  dt = dateTime;
  day = longDay;
  label(s: string) { return STATUS_LABEL[s] || s; }
  tone(s: string) { return STATUS_TONE[s] || 'grey'; }
}
