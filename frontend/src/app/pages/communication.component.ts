import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import qrcode from 'qrcode-generator';
import { Api } from '../core/api.service';
import { ToastService } from '../core/toast.service';
import { apiError, dateTime } from '../core/format';
import { IconComponent } from '../shared/icon.component';

type View = 'automatizaciones' | 'plantillas' | 'historial' | 'enlaces';

const STATUS: Record<string, { label: string; tone: string }> = {
  PENDIENTE: { label: 'Pendiente', tone: 'grey' }, PROGRAMADO: { label: 'Programado', tone: 'blue' }, ENVIADO: { label: 'Enviado', tone: 'teal' },
  ENTREGADO: { label: 'Entregado', tone: 'green' }, LEIDO: { label: 'Leído', tone: 'green' }, FALLIDO: { label: 'Fallido', tone: 'gum' },
  ANULADO: { label: 'Anulado', tone: 'grey' }, DUPLICADO: { label: 'Duplicado', tone: 'grey' },
};
const VAR_HELP: Record<string, string> = {
  paciente: 'Nombre', paciente_completo: 'Nombre completo', fecha: 'Día de la cita', hora: 'Hora', fecha_hora: 'Día y hora',
  fecha_anterior: 'Horario anterior (reprogramación)', odontologo: 'Odontólogo y especialidad', especialidad: 'Especialidad', motivo: 'Motivo',
  motivo_cancelacion: 'Motivo de cancelación', clinica: 'Nombre de la clínica', direccion: 'Dirección', telefono_clinica: 'Teléfono',
  enlace_citas: 'Enlace para reservar', enlace_encuesta: 'Enlace de la encuesta',
};

/** v2.6: comunicación con el paciente: automatizaciones, plantillas, historial de envíos y enlaces públicos. */
@Component({
  selector: 'app-communication',
  standalone: true,
  imports: [FormsModule, IconComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Comunicación con pacientes</h1><p class="sub">Qué mensajes se envían, cuándo y con qué texto. Los cambios se aplican también a las citas ya programadas.</p></div>
      </div>
      <div class="seg top">
        <button type="button" [class.on]="view === 'automatizaciones'" (click)="go('automatizaciones')">Automatizaciones</button>
        <button type="button" [class.on]="view === 'plantillas'" (click)="go('plantillas')">Plantillas</button>
        <button type="button" [class.on]="view === 'historial'" (click)="go('historial')">Historial de envíos</button>
        <button type="button" [class.on]="view === 'enlaces'" (click)="go('enlaces')">Enlaces para pacientes</button>
      </div>

      @switch (view) {
        @case ('automatizaciones') {
          <div class="timeline">
            @for (r of rules; track r.code) {
              <section class="panel rule" [class.off]="!r.active">
                <div class="when">
                  <span class="badge" [class]="'badge ' + r.kind.toLowerCase()">{{ kindLabel(r.kind) }}</span>
                  <strong>{{ r.name }}</strong>
                  <span class="muted small">{{ r.active ? r.summary : 'Desactivada' }}</span>
                </div>
                <div class="cfg">
                  @if (r.kind !== 'DIA') {
                    <input class="input num tabular" type="number" [(ngModel)]="r.amount" [attr.aria-label]="'Cantidad ' + r.name" />
                    <select class="input unit" [(ngModel)]="r.unit" [attr.aria-label]="'Unidad ' + r.name"><option value="HORAS">horas</option><option value="DIAS">días</option></select>
                    <span class="muted small">{{ r.kind === 'ANTES' ? 'antes' : 'después' }}</span>
                  }
                  @if (r.unit === 'DIAS' || r.kind === 'DIA') { <span class="muted small">a las</span><input class="input time" type="time" [(ngModel)]="r.sendTime" min="07:00" max="21:00" [attr.aria-label]="'Hora ' + r.name" /> }
                  <label class="switch"><input type="checkbox" [(ngModel)]="r.active" /> Activa</label>
                  <button class="btn sm primary" type="button" (click)="saveRule(r)" [disabled]="saving">Guardar</button>
                </div>
                @if (tplOf(r.templateCode); as t) {
                  <div class="bubble">
                    <div class="wa">{{ sample(t.body) }}</div>
                    @if (t.buttons?.length) { <div class="btns">@for (b of t.buttons; track b) { <span>↩ {{ b }}</span> }</div> }
                  </div>
                  <div class="row foot">
                    <span class="muted small">{{ r.sent30d }} enviados en 30 días</span>
                    <button class="btn sm ghost" type="button" (click)="openTpl(t)"><app-icon name="edit" [size]="14" /> Editar texto</button>
                  </div>
                }
                @if (re[r.code]) { <span class="err">{{ re[r.code] }}</span> }
              </section>
            }
          </div>
          <p class="muted small">Los avisos "después" se envían solo si la cita queda como <strong>Atendida</strong>. El paciente confirma hasta 2 horas antes; si no, la cita aparece en "Citas sin confirmar" del inicio para que recepción llame.</p>
        }

        @case ('plantillas') {
          <div class="grid layout">
            <section class="panel">
              <div class="panel-head"><h3>Plantillas</h3></div>
              <div class="panel-body list">
                @for (t of templates; track t.code) {
                  <button type="button" class="it" [class.on]="d?.code === t.code" (click)="openTpl(t)">
                    <span class="strong">{{ t.name }}</span>
                    <span class="muted small">{{ catLabel(t.category) }} · {{ t.sent }} envíos</span>
                    @if (t.customized) { <span class="tag teal plain">Editada</span> }
                  </button>
                }
              </div>
            </section>
            <section class="panel">
              @if (!d) { <div class="empty"><p>Elige una plantilla para ver y editar su texto.</p></div> }
              @else {
                <div class="panel-head"><div><h3>{{ d.name }}</h3><p class="muted small">{{ d.description }}</p></div></div>
                <form class="panel-body" (ngSubmit)="saveTpl()">
                  <div class="field"><label for="ts">Asunto (correo)</label><input id="ts" name="ts" class="input" [(ngModel)]="d.subject" (ngModelChange)="preview()" maxlength="150" [class.invalid]="e['subject']" />
                    @if (e['subject']) { <span class="err">{{ e['subject'] }}</span> }</div>
                  <div class="field">
                    <label for="tb">Mensaje</label>
                    <textarea id="tb" #tb name="tb" class="input" rows="8" [(ngModel)]="d.body" (ngModelChange)="preview()" maxlength="1000" [class.invalid]="e['body']"></textarea>
                    <span class="hint">{{ (d.body || '').length }}/1000 · *texto* sale en negrita en WhatsApp</span>
                    @if (e['body']) { <span class="err">{{ e['body'] }}</span> }
                  </div>
                  <div class="vars">
                    <span class="muted small">Insertar:</span>
                    @for (v of variables; track v) { <button type="button" class="chip" (click)="insertVar(tb, v)" [title]="varHelp(v)">{{ '{' + v + '}' }}</button> }
                  </div>
                  <div class="field">
                    <label>Botones de respuesta (WhatsApp, máx. 3)</label>
                    <div class="row wrap gap">
                      @for (b of d.buttons; track $index; let i = $index) {
                        <input class="input btn-in" [name]="'b' + i" [(ngModel)]="d.buttons[i]" maxlength="20" [attr.aria-label]="'Botón ' + (i + 1)" />
                        <button type="button" class="btn ghost sm" (click)="d.buttons.splice(i, 1)" aria-label="Quitar botón"><app-icon name="x" [size]="14" /></button>
                      }
                      @if (d.buttons.length < 3) { <button type="button" class="btn sm" (click)="d.buttons.push('')"><app-icon name="plus" [size]="14" /> Botón</button> }
                    </div>
                    @if (e['buttons']) { <span class="err">{{ e['buttons'] }}</span> }
                    @else { <span class="hint">"Confirmar" confirma la cita y "No podré asistir" avisa a recepción para reprogramar.</span> }
                  </div>
                  <h4>Vista previa</h4>
                  <div class="bubble big"><div class="wa">{{ pv?.body }}</div>@if (cleanButtons().length) { <div class="btns">@for (b of cleanButtons(); track b) { <span>↩ {{ b }}</span> }</div> }</div>
                  @if (pv?.unknown?.length) { <div class="err">Variables que no existen: {{ pv.unknown.join(', ') }}</div> }
                  <div class="row end">
                    @if (d.customized) { <button type="button" class="btn ghost" (click)="restore()">Restaurar texto original</button> }
                    <span class="muted small grow">Editada {{ dt(d.updatedAt) }} por {{ d.updatedBy }}</span>
                    <button type="submit" class="btn primary" [disabled]="saving">{{ saving ? 'Guardando…' : 'Guardar plantilla' }}</button>
                  </div>
                </form>
              }
            </section>
          </div>
        }

        @case ('historial') {
          <section class="panel">
            <div class="panel-head">
              <div class="row wrap gap">
                <input class="input" [(ngModel)]="h.q" (keyup.enter)="loadHistory()" placeholder="Paciente o número" aria-label="Buscar" />
                <select class="input" [(ngModel)]="h.template" (ngModelChange)="loadHistory()" aria-label="Plantilla">
                  <option value="">Todas las plantillas</option>@for (t of templates; track t.code) { <option [value]="t.code">{{ t.name }}</option> }
                </select>
                <select class="input" [(ngModel)]="h.status" (ngModelChange)="loadHistory()" aria-label="Estado">
                  <option value="">Todos los estados</option>@for (s of statusKeys; track s) { <option [value]="s">{{ st(s).label }}</option> }
                </select>
              </div>
            </div>
            <div class="scroll">
              <table class="table">
                <thead><tr><th>Fecha</th><th>Paciente</th><th>Mensaje</th><th>Canal</th><th>Estado</th></tr></thead>
                <tbody>
                  @for (n of history; track n.id) {
                    <tr class="click" (click)="openMsg = openMsg === n.id ? null : n.id">
                      <td class="nowrap small">{{ dt(n.createdAt) }}@if (n.scheduledFor && n.status === 'PROGRAMADO') { <div class="muted">para {{ dt(n.scheduledFor) }}</div> }</td>
                      <td>{{ n.patientName || '—' }}<div class="muted small tabular">{{ n.destination }}</div></td>
                      <td>{{ n.templateName || n.type }}
                        @if (openMsg === n.id) { <div class="bubble"><div class="wa">{{ n.body }}</div></div> }
                        @if (n.lastError) { <div class="small red">{{ n.lastError }}</div> }</td>
                      <td>{{ n.channel === 'WHATSAPP' ? 'WhatsApp' : 'Correo' }}</td>
                      <td><span [class]="'tag ' + st(n.status).tone">{{ st(n.status).label }}</span></td>
                    </tr>
                  } @empty { <tr><td colspan="5" class="muted center">Sin envíos con estos filtros.</td></tr> }
                </tbody>
              </table>
            </div>
          </section>
        }

        @case ('enlaces') {
          @if (links) {
            <div class="grid cols-2">
              <section class="panel link">
                <div class="panel-head"><h3>Reservar cita</h3><label class="switch"><input type="checkbox" [(ngModel)]="links.bookingEnabled" (ngModelChange)="saveLinks()" /> Activo</label></div>
                <div class="panel-body">
                  <p class="muted small">El paciente elige motivo, odontólogo y horario libre; si es nuevo, llena sus datos y acepta la política de privacidad. Recibe la confirmación con botones.</p>
                  <div class="url"><code>{{ links.bookingUrl }}</code><button class="btn sm" type="button" (click)="copy(links.bookingUrl)">Copiar</button><a class="btn sm" [href]="links.bookingUrl" target="_blank" rel="noopener">Abrir</a></div>
                  <div class="qr" [innerHTML]="qr(links.bookingUrl)"></div>
                  <p class="small"><strong>{{ links.webBookings30d }}</strong> reservas por la web en los últimos 30 días.</p>
                </div>
              </section>
              <section class="panel link">
                <div class="panel-head"><h3>Prerregistro (ficha antes de la cita)</h3><label class="switch"><input type="checkbox" [(ngModel)]="links.registerEnabled" (ngModelChange)="saveLinks()" /> Activo</label></div>
                <div class="panel-body">
                  <p class="muted small">El paciente nuevo llena sus datos, alergias, medicación y antecedentes antes de llegar. Aparece en Pacientes con su N° de historia.</p>
                  <div class="url"><code>{{ links.registerUrl }}</code><button class="btn sm" type="button" (click)="copy(links.registerUrl)">Copiar</button><a class="btn sm" [href]="links.registerUrl" target="_blank" rel="noopener">Abrir</a></div>
                  <div class="qr" [innerHTML]="qr(links.registerUrl)"></div>
                  <p class="small"><strong>{{ links.webPatients30d }}</strong> pacientes registrados desde la web en 30 días.</p>
                </div>
              </section>
            </div>
            <p class="muted small">Compártelos en la biografía de Instagram, el perfil de Google, el estado de WhatsApp o imprime el QR para la recepción.</p>
          }
        }
      }
    </div>
  `,
  styles: [`
    .seg { display: inline-flex; flex-wrap: wrap; border: 1px solid var(--line); border-radius: 999px; padding: 3px; background: var(--surface); margin-bottom: 16px; }
    .seg button { border: 0; background: none; padding: 7px 16px; border-radius: 999px; font: inherit; cursor: pointer; }
    .seg button.on { background: var(--teal-soft); color: var(--teal-strong); font-weight: 700; }
    .timeline { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 14px; margin-bottom: 12px; }
    .rule { padding: 14px; display: grid; gap: 10px; align-content: start; }
    .rule.off { opacity: .6; }
    .when { display: grid; gap: 2px; }
    .badge { justify-self: start; font-size: 11px; font-weight: 800; text-transform: uppercase; letter-spacing: .05em; padding: 2px 8px; border-radius: 999px; }
    .badge.antes { background: var(--restore-soft); color: var(--restore); } .badge.dia { background: var(--amber-soft); color: var(--amber); } .badge.despues { background: var(--green-soft); color: var(--green); }
    .cfg { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
    .num { width: 64px; } .unit { width: 90px; } .time { width: 132px; }
    .switch { display: inline-flex; gap: 6px; align-items: center; font-size: 14px; margin-left: auto; }
    .bubble { background: #e7f8e3; border-radius: 10px 10px 10px 2px; padding: 10px 12px; font-size: 13.5px; max-width: 100%; }
    .bubble.big { font-size: 14.5px; margin: 6px 0 12px; }
    .wa { white-space: pre-wrap; word-break: break-word; }
    .btns { display: grid; gap: 1px; margin: 8px -12px -10px; border-top: 1px solid rgba(0,0,0,.08); }
    .btns span { background: #f4fcf2; color: #027eb5; font-weight: 600; text-align: center; padding: 7px; }
    .foot { justify-content: space-between; }
    .layout { grid-template-columns: minmax(240px, 1fr) minmax(0, 2.2fr); align-items: start; }
    @media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }
    .list { display: grid; gap: 6px; }
    .it { display: grid; gap: 2px; text-align: left; border: 1px solid var(--line); background: var(--surface); border-radius: 10px; padding: 8px 10px; font: inherit; cursor: pointer; }
    .it.on { border-color: var(--teal); background: var(--teal-soft); }
    .vars { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; margin: 4px 0 12px; }
    .chip { border: 1px solid var(--line); background: var(--surface); border-radius: 999px; padding: 3px 9px; font: inherit; font-size: 12px; cursor: pointer; font-family: ui-monospace, monospace; }
    .chip:hover { border-color: var(--teal); }
    .btn-in { width: 170px; }
    .gap { gap: 6px; }
    .end { justify-content: flex-end; gap: 8px; align-items: center; }
    .scroll { overflow-x: auto; }
    .red { color: var(--gum); }
    .url { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; margin: 10px 0; }
    .url code { background: var(--surface-2, #f3f6f7); padding: 6px 10px; border-radius: 8px; font-size: 13px; word-break: break-all; }
    .qr { width: 160px; height: 160px; margin: 6px 0; }
    :host ::ng-deep .qr svg { width: 100%; height: 100%; }
  `],
})
export class CommunicationComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private sanitizer = inject(DomSanitizer);

  view: View = 'automatizaciones';
  rules: any[] = [];
  templates: any[] = [];
  variables: string[] = [];
  d: any = null;
  pv: any = null;
  e: Record<string, string> = {};
  re: Record<string, string> = {};
  saving = false;
  history: any[] = [];
  h = { q: '', template: '', status: '' };
  openMsg: number | null = null;
  statusKeys = Object.keys(STATUS);
  links: any = null;
  private qrs = new Map<string, SafeHtml>();
  private pvTimer: any;

  ngOnInit() {
    const v = this.route.snapshot.queryParamMap.get('vista') as View;
    if (v) this.view = v;
    this.loadTemplates();
    this.api.automations().subscribe((r) => (this.rules = r.map((x) => ({ ...x, sendTime: x.sendTime ? x.sendTime.slice(0, 5) : null }))));
    this.loadView();
  }

  go(v: View) { this.view = v; this.router.navigate([], { queryParams: { vista: v === 'automatizaciones' ? null : v }, replaceUrl: true }); this.loadView(); }
  private loadView() {
    if (this.view === 'historial') this.loadHistory();
    if (this.view === 'enlaces' && !this.links) this.api.publicLinks().subscribe((l) => (this.links = l));
  }
  loadTemplates() { this.api.messageTemplates().subscribe((r) => { this.templates = r.templates; this.variables = r.variables; }); }
  loadHistory() { this.api.messagingHistory(this.h).subscribe((r) => (this.history = r)); }

  tplOf(code: string) { return this.templates.find((t) => t.code === code); }
  sample(body: string) {
    const ex: Record<string, string> = { paciente: 'María', paciente_completo: 'María Quispe', fecha: 'Martes 24 de noviembre', hora: '10:30', fecha_hora: 'Martes 24 de noviembre a las 10:30',
      fecha_anterior: 'Lunes 23 a las 16:00', odontologo: 'Dra. Rosa Cárdenas (Ortodoncia)', especialidad: 'Ortodoncia', motivo: 'Control', motivo_cancelacion: '',
      clinica: 'Clínica Dental Cárdenas', direccion: 'Av. Los Guindos, Chaclacayo', telefono_clinica: '987 654 000', enlace_citas: '…/reservar', enlace_encuesta: '…/encuesta/…' };
    return (body || '').replace(/\{([a-z_]+)\}/g, (m, k) => ex[k] ?? m);
  }

  saveRule(r: any) {
    this.saving = true; this.re = {};
    this.api.updateAutomation(r.code, { active: r.active, amount: r.amount, unit: r.unit, sendTime: r.sendTime }).subscribe({
      next: (x) => {
        this.saving = false;
        Object.assign(r, { ...x, sendTime: x.sendTime ? x.sendTime.slice(0, 5) : null });
        this.toast.ok(x.rescheduled ? `Guardado. Se reprogramaron los avisos de ${x.rescheduled} citas.` : 'Automatización guardada.');
      },
      error: (err) => { this.saving = false; const a = apiError(err); this.re = { [r.code]: Object.values(a.fields || {})[0] || a.message }; },
    });
  }

  openTpl(t: any) {
    this.view = 'plantillas';
    this.e = {};
    this.d = { ...t, buttons: [...(t.buttons || [])] };
    this.preview();
  }
  preview() {
    clearTimeout(this.pvTimer);
    this.pvTimer = setTimeout(() => this.api.previewTemplate(this.d.code, { body: this.d.body, subject: this.d.subject }).subscribe((p) => (this.pv = p)), 250);
  }
  cleanButtons() { return (this.d?.buttons || []).map((b: string) => (b || '').trim()).filter(Boolean); }
  insertVar(ta: HTMLTextAreaElement, v: string) {
    const token = '{' + v + '}';
    const s = ta.selectionStart ?? this.d.body.length, e = ta.selectionEnd ?? s;
    this.d.body = this.d.body.slice(0, s) + token + this.d.body.slice(e);
    setTimeout(() => { ta.focus(); ta.selectionStart = ta.selectionEnd = s + token.length; });
    this.preview();
  }
  saveTpl() {
    this.saving = true; this.e = {};
    this.api.updateTemplate(this.d.code, { subject: this.d.subject, body: this.d.body, buttons: this.cleanButtons() }).subscribe({
      next: (t) => { this.saving = false; this.toast.ok(t.rescheduled ? `Plantilla guardada. Se actualizaron los avisos de ${t.rescheduled} citas.` : 'Plantilla guardada.'); this.loadTemplates(); this.openTpl(t); },
      error: (err) => { this.saving = false; const a = apiError(err); this.e = a.fields || {}; this.toast.error(a.message); },
    });
  }
  restore() {
    if (!confirm('¿Volver al texto original de esta plantilla?')) return;
    this.api.restoreTemplate(this.d.code).subscribe({ next: (t) => { this.toast.ok('Texto original restaurado.'); this.loadTemplates(); this.openTpl(t); }, error: (err) => this.toast.error(apiError(err).message) });
  }

  saveLinks() {
    this.api.updatePublicLinks({ bookingEnabled: this.links.bookingEnabled, registerEnabled: this.links.registerEnabled }).subscribe({
      next: (l) => { this.links = l; this.toast.ok('Enlaces actualizados.'); }, error: (err) => this.toast.error(apiError(err).message),
    });
  }
  copy(u: string) { navigator.clipboard?.writeText(u).then(() => this.toast.ok('Enlace copiado.')); }
  qr(u: string): SafeHtml {
    if (!this.qrs.has(u)) {
      const q = (qrcode as any)(0, 'M'); q.addData(u); q.make();
      this.qrs.set(u, this.sanitizer.bypassSecurityTrustHtml(q.createSvgTag({ cellSize: 4, margin: 1, scalable: true })));
    }
    return this.qrs.get(u)!;
  }

  kindLabel(k: string) { return k === 'ANTES' ? 'Antes' : k === 'DIA' ? 'El día' : 'Después'; }
  catLabel(c: string) { return c === 'CITA' ? 'Avisos de cita' : c === 'RESERVA' ? 'Reserva web' : c === 'MARKETING' ? 'Marketing' : 'Automatización'; }
  varHelp(v: string) { return VAR_HELP[v] || v; }
  st(s: string) { return STATUS[s] || { label: s, tone: 'grey' }; }
  dt = dateTime;
}
