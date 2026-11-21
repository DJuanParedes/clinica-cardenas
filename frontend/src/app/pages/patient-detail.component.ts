import { PatientFlagsComponent } from '../shared/patient-flags.component';
import { OrthoTabComponent } from './clinical/ortho-tab.component';
import { InformedConsentsComponent } from '../shared/informed-consents.component';
import { TaskListComponent } from '../shared/task-list.component';
import { MarketingConsentComponent } from '../shared/marketing-consent.component';
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
import { PatientProfilePanelsComponent } from './patient-profile-panels.component';
import { AnamnesisTabComponent } from './clinical/anamnesis-tab.component';
import { VitalsTabComponent } from './clinical/vitals-tab.component';
import { PeriodontogramTabComponent } from './clinical/periodontogram-tab.component';
import { LabOrdersComponent } from './lab-orders.component';
import { PatientProfile } from '../core/api.service';

type Tab = 'resumen' | 'historia' | 'odontograma' | 'periodontograma' | 'plan' | 'laboratorio' | 'ortodoncia' | 'archivos' | 'cuenta';

@Component({
  selector: 'app-patient-detail',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent, HistoryTabComponent, OdontogramTabComponent, PlanTabComponent, AccountTabComponent, FilesTabComponent, PatientProfilePanelsComponent, AnamnesisTabComponent, VitalsTabComponent, PeriodontogramTabComponent, LabOrdersComponent, TaskListComponent, MarketingConsentComponent, PatientFlagsComponent, OrthoTabComponent, InformedConsentsComponent],
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
              @if (profile?.hcNumber || p.hcNumber) { <span class="tabular">HC {{ profile?.hcNumber || p.hcNumber }}</span> }
              <span>{{ p.age }} años · {{ p.sex === 'F' ? 'Femenino' : 'Masculino' }}</span>
              <span class="tabular"><app-icon name="phone" [size]="14" /> {{ p.phone }}</span>
              @if (p.email) { <span><app-icon name="mail" [size]="14" /> {{ p.email }}</span> }
            </div>
            @if (profile?.tags?.length) {
              <div class="hero-tags">@for (t of profile!.tags; track t) { <a class="tag teal plain" [routerLink]="['/pacientes']" [queryParams]="{ etiqueta: t }">{{ t }}</a> }</div>
            }
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

        <app-patient-flags [patientId]="p.id" />
        @if (clinical && anamAlerts.length) {
          <div class="notice gum anam" role="alert"><app-icon name="alert" /><div>@for (a of anamAlerts; track a) { <div class="strong">{{ a }}</div> }</div></div>
        }
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
            <a [class.on]="tab === 'periodontograma'" (click)="go('periodontograma')">Periodontograma</a>
          }
          @if (canPlan) { <a [class.on]="tab === 'plan'" (click)="go('plan')">Plan de tratamiento</a> }
          @if (clinical) { <a [class.on]="tab === 'ortodoncia'" (click)="go('ortodoncia')">Ortodoncia</a> }
          @if (auth.can('LABORATORIO')) { <a [class.on]="tab === 'laboratorio'" (click)="go('laboratorio')">Laboratorio</a> }
          <a [class.on]="tab === 'archivos'" (click)="go('archivos')">Fotos y documentos</a>
          @if (canAccount) { <a [class.on]="tab === 'cuenta'" (click)="go('cuenta')">Cuenta y pagos</a> }
        </nav>

        @switch (tab) {
          @case ('historia') {
            <div class="seg sub">
              <button type="button" [class.on]="sub === 'antecedentes'" (click)="sub = 'antecedentes'">Antecedentes y diagnósticos</button>
              <button type="button" [class.on]="sub === 'anamnesis'" (click)="sub = 'anamnesis'">Anamnesis</button>
              <button type="button" [class.on]="sub === 'signos'" (click)="sub = 'signos'">Signos vitales</button>
            </div>
            @switch (sub) {
              @case ('anamnesis') { <app-anamnesis-tab [patientId]="p.id" /> }
              @case ('signos') { <app-vitals-tab [patientId]="p.id" /> }
              @default { <app-history-tab [patientId]="p.id" /> }
            }
          }
          @case ('odontograma') { <app-odontogram-tab [patientId]="p.id" /> }
          @case ('periodontograma') { <app-periodontogram-tab [patientId]="p.id" /> }
          @case ('plan') { <app-plan-tab [patientId]="p.id" /> }
          @case ('laboratorio') { <app-lab-orders [patientId]="p.id" /> }
          @case ('cuenta') { <app-account-tab [patientId]="p.id" /> }
          @case ('ortodoncia') { <app-ortho-tab [patientId]="p.id" /> }
          @case ('archivos') {
            @if (auth.can('HISTORIA_CLINICA', 'PACIENTES_EDITAR')) { <app-informed-consents [patientId]="p.id" [patientName]="p.fullName" /> }
            <app-files-tab [patientId]="p.id" [patientName]="p.fullName" />
          }
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
                        <div class="row wrap up">
                          <button type="button" class="btn sm" (click)="printConsent()"><app-icon name="print" [size]="14" /> Imprimir documento para firmar</button>
                        </div>
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

              <app-patient-profile-panels [patientId]="p.id" (changed)="profile = $event" />
              @if (auth.can('TAREAS')) { <app-task-list [patientId]="p.id" [subtitle]="p.fullName" /> }
              <app-marketing-consent [patient]="p" />

              <section class="panel">
                <div class="panel-head"><h3>Archivos solicitados al paciente</h3>
                  @if (canRequest) { <button class="btn sm" type="button" (click)="reqOpen = !reqOpen"><app-icon name="plus" [size]="14" /> Pedir archivo</button> }</div>
                <div class="panel-body small">
                  @if (reqOpen) {
                    <form class="reqform" (ngSubmit)="createReq()">
                      <input class="input" name="rt" [(ngModel)]="rq.title" placeholder="Ej. Radiografía panorámica" maxlength="150" />
                      <select class="input" name="rk" [(ngModel)]="rq.kind"><option value="RADIOGRAFIA">Radiografía / placa</option><option value="DOCUMENTO">Documento / informe</option><option value="FOTO">Foto</option></select>
                      <input class="input" name="rd" [(ngModel)]="rq.description" placeholder="Para qué (opcional)" maxlength="500" />
                      <input class="input" name="rdue" type="date" [(ngModel)]="rq.dueDate" />
                      <button class="btn primary sm" type="submit" [disabled]="busyReq || !rq.title">Pedir</button>
                    </form>
                    <p class="muted">El paciente lo verá en "Mi historia → Documentos" de la app y podrá subirlo; también puedes subirlo tú.</p>
                  }
                  @for (r of reqs; track r.id) {
                    <div class="reqrow">
                      <div class="grow"><strong>{{ r.title }}</strong> <span class="muted">· pedido por {{ r.requestedBy }} el {{ date(r.requestedAt) }}{{ r.dueDate ? ' · para el ' + date(r.dueDate) : '' }}</span>
                        @if (r.description) { <div class="muted">{{ r.description }}</div> }</div>
                      @if (r.status === 'PENDIENTE') {
                        <span class="tag amber">Pendiente</span>
                        @if (canRequest) {
                          <label class="btn sm"><app-icon name="upload" [size]="14" /> Subir<input type="file" accept="image/jpeg,image/png,image/webp,application/pdf" hidden (change)="uploadReq(r, $event)" /></label>
                          <button class="btn sm ghost" type="button" (click)="cancelReq(r)">Anular</button>
                        }
                      } @else { <span class="tag green">Recibido · {{ r.fulfilledBy }}</span> <a class="link" (click)="go('archivos')">Ver en Archivos</a> }
                    </div>
                  } @empty { <span class="muted">No se le pidieron archivos.</span> }
                </div>
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
    .reqform { display: grid; grid-template-columns: 2fr 1.3fr 2fr 1fr auto; gap: 6px; margin-bottom: 6px; }
    .reqrow { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; padding: 8px 0; border-bottom: 1px solid var(--line); } .reqrow:last-child { border-bottom: 0; }
    @media (max-width: 900px) { .reqform { grid-template-columns: 1fr; } }
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
    .seg.sub { display: inline-flex; border: 1px solid var(--line); border-radius: 999px; padding: 3px; margin-bottom: 14px; background: var(--surface); flex-wrap: wrap; }
    .seg.sub button { border: 0; background: none; padding: 6px 14px; border-radius: 999px; font: inherit; cursor: pointer; }
    .seg.sub button.on { background: var(--teal-soft); color: var(--teal-strong); font-weight: 700; }
    .anam { margin-bottom: 14px; }
    .hero-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
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
  profile: PatientProfile | null = null;
  anamAlerts: string[] = [];
  sub: 'antecedentes' | 'anamnesis' | 'signos' = 'antecedentes';
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
    const allowed: Record<string, boolean> = { resumen: true, historia: this.clinical, odontograma: this.clinical, periodontograma: this.clinical, laboratorio: this.auth.can('LABORATORIO'), ortodoncia: this.clinical, plan: this.canPlan, archivos: true, cuenta: this.canAccount };
    if (!this.tab || !allowed[this.tab]) this.tab = 'resumen';
    const id = +this.id;
    if (this.p?.id === id) return;
    this.api.patient(id).subscribe((r: any) => {
      this.p = r.patient; this.next = r.nextAppointment || null; this.nextIsMine = !!r.nextIsMine;
      this.alerts = r.clinicalAlerts || null; this.pending = r.pendingProcedures || []; this.anamAlerts = (r as any).anamnesisAlerts || [];
      if (this.alerts && !this.alerts.allergies && !this.alerts.medication && !this.alerts.background) this.alerts = { ...this.alerts, empty: true };
    });
    this.loadReqs(id);
    this.api.patientConsents(id).subscribe({ next: (c) => (this.consent = c), error: () => {} });
    this.api.patientAppointments(id).subscribe((a) => (this.appts = a));
    if (this.auth.can('PACIENTES_EDITAR')) this.api.patientAudit(id).subscribe((a) => (this.audit = a));
    this.api.patientNotifications(id).subscribe((n) => (this.notifs = n.filter((x) => x.operation !== 'ANULAR')));
  }

  reqs: any[] = [];
  reqOpen = false;
  busyReq = false;
  rq: any = { title: '', kind: 'RADIOGRAFIA', description: '', dueDate: null };
  get canRequest() { return this.auth.can('PACIENTES_EDITAR') || this.auth.can('HISTORIA_CLINICA'); }
  loadReqs(id: number) { this.api.fileRequests(id).subscribe({ next: (r) => (this.reqs = r), error: () => {} }); }
  createReq() {
    if (!this.p) return;
    this.busyReq = true;
    this.api.createFileRequest(this.p.id, { ...this.rq, dueDate: this.rq.dueDate || null }).subscribe({
      next: (r) => { this.busyReq = false; this.reqs = r; this.reqOpen = false; this.rq = { title: '', kind: 'RADIOGRAFIA', description: '', dueDate: null }; this.toast.ok('Pedido enviado: el paciente lo verá en su app.'); },
      error: (e) => { this.busyReq = false; this.toast.error(apiError(e).message); },
    });
  }
  cancelReq(r: any) { if (this.p) this.api.cancelFileRequest(this.p.id, r.id).subscribe({ next: (x) => (this.reqs = x), error: (e) => this.toast.error(apiError(e).message) }); }
  uploadReq(r: any, ev: Event) {
    const input = ev.target as HTMLInputElement; const file = input.files?.[0]; input.value = '';
    if (!file || !this.p) return;
    this.api.uploadFileRequest(this.p.id, r.id, file).subscribe({ next: (x) => { this.reqs = x; this.toast.ok('Archivo guardado en la ficha.'); }, error: (e) => this.toast.error(apiError(e).message) });
  }

  relLabel(r?: string) { return r === 'PADRE' ? 'Padre' : r === 'MADRE' ? 'Madre' : 'Tutor legal'; }
  consentChannel(c?: string) { return c === 'APP' ? 'en la app' : c === 'PORTAL' ? 'en el portal' : 'presencial en recepción'; }

  /** Documento de consentimiento listo para imprimir y firmar: datos del paciente (o su representante), texto vigente y firmas. */
  printConsent() {
    const p: any = this.p;
    if (!p) return;
    const w = window.open('', '_blank');
    if (!w) { this.toast.error('El navegador bloqueó la ventana. Permite ventanas emergentes para imprimir.'); return; }
    w.document.write('<p style="font-family:sans-serif">Preparando el documento…</p>');
    this.api.legal('PRIVACIDAD').subscribe({
      next: (doc) => {
        const esc = (v: any) => String(v ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
        const body = esc(doc.body).split(/\n{2,}/).map((x) => '<p>' + x.replace(/\n/g, '<br>') + '</p>').join('');
        const today = new Date().toLocaleDateString('es-PE', { day: '2-digit', month: 'long', year: 'numeric' });
        const minor = !!p.minor;
        const signer = minor
          ? `${esc(p.guardianName)}, ${esc(this.relLabel(p.guardianRelationship)).toLowerCase()} y representante legal de ${esc(p.firstName)} ${esc(p.lastName)}, identificado(a) con ${esc(p.guardianDocType || 'DNI')} N.° ${esc(p.guardianDocNumber)}`
          : `${esc(p.firstName)} ${esc(p.lastName)}, identificado(a) con ${esc(p.docType)} N.° ${esc(p.docNumber)}`;
        w.document.open();
        w.document.write(`<!doctype html><html lang="es"><head><meta charset="utf-8"><title>Consentimiento · ${esc(p.firstName)} ${esc(p.lastName)}</title>
<style>body{font-family:Georgia,serif;color:#1d2b33;max-width:720px;margin:32px auto;padding:0 24px;font-size:13.5px;line-height:1.5}
h1{font-size:18px;text-align:center;margin:8px 0 4px}h2{font-size:14px;margin:18px 0 6px}.c{text-align:center;color:#555;font-size:12px}
table{width:100%;border-collapse:collapse;margin:10px 0}td{border:1px solid #bbb;padding:6px 8px;font-size:12.5px}td.k{width:34%;background:#f3f6f7}
.firmas{display:flex;gap:40px;margin-top:56px}.f{flex:1;text-align:center;border-top:1px solid #333;padding-top:6px;font-size:12px}
.huella{width:90px;height:110px;border:1px solid #333;margin-left:auto;text-align:center;font-size:11px;padding-top:90px;box-sizing:border-box}
.btn{position:fixed;top:12px;right:12px;padding:8px 14px;font-size:14px}@media print{.btn{display:none}body{margin:0 auto}}</style></head><body>
<button class="btn" onclick="window.print()">Imprimir</button>
<div class="c">CLÍNICA DENTAL CÁRDENAS</div>
<h1>Consentimiento para el tratamiento de datos personales</h1>
<div class="c">Ley N.° 29733 y su Reglamento (D.S. 016-2024-JUS) · ${esc(doc.title)} · versión ${esc(doc.version)}</div>
<h2>Datos del paciente</h2>
<table><tr><td class="k">Paciente</td><td>${esc(p.firstName)} ${esc(p.lastName)}</td></tr>
<tr><td class="k">Documento</td><td>${esc(p.docType)} ${esc(p.docNumber)}</td></tr>
<tr><td class="k">Fecha de nacimiento</td><td>${esc(p.birthDate)}${p.age != null ? ' (' + esc(p.age) + ' años)' : ''}</td></tr>
<tr><td class="k">Celular / correo</td><td>${esc(p.phone)}${p.email ? ' · ' + esc(p.email) : ''}</td></tr>
${minor ? `<tr><td class="k">Representante legal</td><td>${esc(p.guardianName)} (${esc(this.relLabel(p.guardianRelationship))}) · ${esc(p.guardianDocType || 'DNI')} ${esc(p.guardianDocNumber)} · ${esc(p.guardianPhone)}</td></tr>` : ''}
</table>
<h2>Política de privacidad</h2>${body}
<h2>Declaración</h2>
<p>Yo, ${signer}, declaro haber leído y comprendido la política de privacidad de la Clínica Dental Cárdenas y otorgo mi consentimiento libre, previo, expreso, informado e inequívoco para el tratamiento de ${minor ? 'sus' : 'mis'} datos personales y de salud con las finalidades indicadas. Sé que puedo revocarlo y ejercer mis derechos de acceso, rectificación, cancelación y oposición en cualquier momento.</p>
<p>Chaclacayo, ${today}.</p>
<div class="firmas"><div class="f">Firma del ${minor ? 'representante legal' : 'paciente'}<br>${minor ? esc(p.guardianName) : esc(p.firstName) + ' ' + esc(p.lastName)}<br>${minor ? esc(p.guardianDocType || 'DNI') + ' ' + esc(p.guardianDocNumber) : esc(p.docType) + ' ' + esc(p.docNumber)}</div>
<div class="f">Firma y sello de la clínica<br>Recibido por</div><div class="huella">Huella</div></div>
<script>setTimeout(function(){window.print()},400)</script></body></html>`);
        w.document.close();
      },
      error: () => { w.close(); this.toast.error('No se pudo cargar la política vigente.'); },
    });
  }

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
