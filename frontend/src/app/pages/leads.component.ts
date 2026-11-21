import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { apiError, dateTime, isoDate, shortDate, soles } from '../core/format';
import { ToastService } from '../core/toast.service';
import { ModalComponent } from '../shared/modal.component';
import { PatientPickerComponent } from '../shared/patient-picker.component';
import { TaskListComponent } from '../shared/task-list.component';

/** v2.7: embudo de ventas. Tablero por etapas; se mueve arrastrando o desde la ficha del lead. */
@Component({
  selector: 'app-leads',
  standalone: true,
  imports: [FormsModule, RouterLink, ModalComponent, PatientPickerComponent, TaskListComponent],
  template: `
    <div class="page wide">
      <header class="page-head">
        <div><h1>Embudo de ventas</h1><p class="muted">Personas interesadas desde el primer contacto hasta que aceptan su tratamiento.</p></div>
        <div class="row">
          <div class="seg"><button type="button" [class.on]="mode === 'board'" (click)="mode = 'board'">Tablero</button><button type="button" [class.on]="mode === 'metrics'" (click)="mode = 'metrics'; loadMetrics()">Resultados</button></div>
          <button class="btn primary" type="button" (click)="newLead()">+ Nuevo lead</button>
        </div>
      </header>

      @if (mode === 'board') {
        <div class="filters">
          <input class="input grow" [(ngModel)]="q" (keyup.enter)="load()" (blur)="load()" placeholder="Buscar por nombre, celular o interés" aria-label="Buscar" />
          <select class="input" [(ngModel)]="source" (ngModelChange)="load()" aria-label="Fuente"><option value="">Todas las fuentes</option>@for (s of opts?.sources || []; track s.code) { <option [value]="s.code">{{ s.label }}</option> }</select>
          <select class="input" [(ngModel)]="assigneeId" (ngModelChange)="load()" aria-label="Responsable"><option [ngValue]="null">Cualquier responsable</option>@for (u of opts?.assignees || []; track u.id) { <option [ngValue]="u.id">{{ u.fullName }}</option> }</select>
          @if (auth.can('MARKETING') && opts) {
            <label class="check small" title="Quien escribe por WhatsApp sin ser paciente entra como lead nuevo"><input type="checkbox" [checked]="opts.whatsappAuto" (change)="toggleWa()" /> Crear leads desde WhatsApp</label>
          }
        </div>
        <div class="board">
          @for (st of stages; track st.code) {
            <section class="col" [class.drop]="dragOver === st.code" (dragover)="$event.preventDefault(); dragOver = st.code" (dragleave)="dragOver = ''" (drop)="drop(st.code)">
              <header><strong>{{ st.label }}</strong><span class="muted small">{{ st.count }}{{ st.value > 0 ? ' · ' + soles(st.value) : '' }}</span></header>
              <div class="cards">
                @for (l of st.items; track l.id) {
                  <article class="card" draggable="true" (dragstart)="drag = l" (dragend)="drag = null; dragOver = ''" (click)="open(l.id)" tabindex="0" (keydown.enter)="open(l.id)">
                    <div class="nm">{{ l.fullName }}</div>
                    @if (l.interest || l.procedure) { <div class="small">{{ l.procedure?.name || l.interest }}</div> }
                    <div class="meta"><span class="tag plain" [title]="l.sourceLabel">{{ l.sourceLabel }}</span>@if (l.estimatedValue) { <span class="small strong">{{ soles(l.estimatedValue) }}</span> }</div>
                    @if (l.nextTask) { <div class="small" [class.bad]="l.nextTask.overdue">☐ {{ l.nextTask.title }} · {{ sd(l.nextTask.dueDate) }}</div> }
                    @else if (st.code !== 'GANADO' && st.code !== 'PERDIDO') { <div class="small warn">Sin próxima acción</div> }
                    @if (l.nextAppointment) { <div class="small">📅 {{ dt(l.nextAppointment) }}</div> }
                    @if (l.lostReason) { <div class="small muted">{{ l.lostReason }}</div> }
                    <div class="foot small muted">{{ l.assignee?.fullName || 'Sin responsable' }} · {{ l.daysInStage === 0 ? 'hoy' : l.daysInStage + ' d' }}{{ l.patient ? ' · paciente' : '' }}</div>
                  </article>
                } @empty { <p class="muted small empty-col">Sin leads</p> }
              </div>
            </section>
          }
        </div>
      }

      @if (mode === 'metrics') {
        <div class="row spread mhead">
          <h3>Leads de los últimos {{ days }} días</h3>
          <select class="input auto" [(ngModel)]="days" (ngModelChange)="loadMetrics()" aria-label="Período"><option [ngValue]="30">30 días</option><option [ngValue]="90">90 días</option><option [ngValue]="180">6 meses</option><option [ngValue]="365">1 año</option></select>
        </div>
        @if (m) {
          <div class="kpis">
            <div><b>{{ m.created }}</b><span>leads nuevos</span></div>
            <div><b>{{ m.won }}</b><span>aceptaron tratamiento</span></div>
            <div><b>{{ m.conversion }} %</b><span>conversión</span></div>
            <div><b>{{ soles(m.pipelineValue) }}</b><span>en negociación ({{ m.open }})</span></div>
            <div><b>{{ soles(m.wonValue) }}</b><span>ganado</span></div>
            <div><b>{{ m.avgDaysToWin ?? '—' }}</b><span>días promedio hasta ganar</span></div>
          </div>
          <div class="cols-2 mgrid">
            <section class="panel"><div class="panel-head"><h3>Embudo</h3></div><div class="panel-body">
              @for (f of m.funnel; track f.stage) {
                <div class="fun"><span class="lbl">{{ f.label }}</span><div class="fb"><span [style.width.%]="f.percent"></span></div><span class="tabular small">{{ f.reached }} · {{ f.percent }} %</span></div>
              }
            </div></section>
            <section class="panel"><div class="panel-head"><h3>Por fuente</h3></div>
              <table class="table"><thead><tr><th>Fuente</th><th class="right">Leads</th><th class="right">Ganados</th><th class="right">Conversión</th></tr></thead>
                <tbody>@for (s of m.bySource; track s.source) { <tr><td>{{ s.label }}</td><td class="right tabular">{{ s.total }}</td><td class="right tabular">{{ s.won }}</td><td class="right tabular">{{ s.conversion }} %</td></tr> }
                  @empty { <tr><td colspan="4" class="muted">Sin datos</td></tr> }</tbody></table>
              @if (m.lostReasons.length) {
                <div class="panel-body small"><strong>Por qué se perdieron:</strong> @for (r of m.lostReasons; track r.reason) { <span class="tag plain">{{ r.reason }} · {{ r.count }}</span> }</div>
              }
            </section>
          </div>
        }
      }
    </div>

    <!-- nuevo / editar lead -->
    <app-modal [open]="formOpen" [title]="form.id ? 'Editar lead' : 'Nuevo lead'" [width]="600" (close)="formOpen = false">
      <form class="grid" id="leadForm" (ngSubmit)="saveLead()">
        <div class="cols-2">
          <div class="field span-2"><label class="req" for="ln">Nombre</label><input id="ln" name="ln" class="input" [(ngModel)]="form.fullName" maxlength="120" [class.invalid]="fe['fullName']" />@if (fe['fullName']) { <span class="err">{{ fe['fullName'] }}</span> }</div>
          <div class="field"><label for="lp">Celular</label><input id="lp" name="lp" class="input tabular" [(ngModel)]="form.phone" maxlength="12" inputmode="tel" [class.invalid]="fe['phone']" />@if (fe['phone']) { <span class="err">{{ fe['phone'] }}</span> }</div>
          <div class="field"><label for="le">Correo</label><input id="le" name="le" class="input" type="email" [(ngModel)]="form.email" maxlength="120" [class.invalid]="fe['email']" />@if (fe['email']) { <span class="err">{{ fe['email'] }}</span> }</div>
          <div class="field"><label class="req" for="ls">¿De dónde llegó?</label><select id="ls" name="ls" class="input" [(ngModel)]="form.source" [class.invalid]="fe['source']"><option value="">Elige…</option>@for (s of opts?.sources || []; track s.code) { <option [value]="s.code">{{ s.label }}</option> }</select>@if (fe['source']) { <span class="err">{{ fe['source'] }}</span> }</div>
          <div class="field"><label for="la">Responsable</label><select id="la" name="la" class="input" [(ngModel)]="form.assigneeId"><option [ngValue]="null">Sin asignar</option>@for (u of opts?.assignees || []; track u.id) { <option [ngValue]="u.id">{{ u.fullName }}</option> }</select></div>
          <div class="field"><label for="lpr">Tratamiento de interés</label><select id="lpr" name="lpr" class="input" [(ngModel)]="form.procedureId" (ngModelChange)="procPicked()"><option [ngValue]="null">Otro / no sabe</option>@for (p of opts?.procedures || []; track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }</select></div>
          <div class="field"><label for="lv">Valor estimado (S/)</label><input id="lv" name="lv" class="input tabular" type="number" min="0" step="10" [(ngModel)]="form.estimatedValue" [class.invalid]="fe['estimatedValue']" /></div>
          <div class="field span-2"><label for="li">Qué preguntó o le interesa</label><input id="li" name="li" class="input" [(ngModel)]="form.interest" maxlength="150" placeholder="Ej.: Precio de brackets estéticos" /></div>
          @if (!form.id) { <div class="field"><label for="lf">Contactar el (crea una tarea)</label><input id="lf" name="lf" class="input" type="date" [(ngModel)]="form.followUpDate" [min]="today" /></div> }
          <div class="field span-2"><label for="lno">Notas</label><textarea id="lno" name="lno" class="input" rows="2" [(ngModel)]="form.notes" maxlength="1000"></textarea></div>
        </div>
        @if (formErr) { <div class="err">{{ formErr }} @if (dupId) { <a (click)="formOpen = false; open(dupId)">Abrir ese lead</a> }</div> }
      </form>
      <div modal-actions><button class="btn" type="button" (click)="formOpen = false">Cancelar</button><button class="btn primary" type="submit" form="leadForm" [disabled]="busy">Guardar</button></div>
    </app-modal>

    <!-- ficha del lead -->
    <app-modal [open]="!!lead" [title]="lead?.fullName" [subtitle]="(lead?.stageLabel || '') + ' · ' + (lead?.sourceLabel || '') + (lead?.phone ? ' · ' + lead?.phone : '')" [width]="820" (close)="closeLead()">
      @if (lead) {
        <div class="stages">
          @for (s of opts?.stages || []; track s.code) {
            <button type="button" [class.on]="lead.stage === s.code" [class.lost]="s.code === 'PERDIDO'" (click)="move(lead, s.code)">{{ s.label }}</button>
          }
        </div>
        @if (lostFor) {
          <div class="lost-box">
            <strong>¿Por qué se perdió?</strong>
            <div class="chips">@for (r of opts?.lostReasons || []; track r) { <button type="button" [class.on]="lostReason === r" (click)="lostReason = r">{{ r }}</button> }</div>
            <input class="input" [(ngModel)]="lostDetail" maxlength="150" placeholder="Detalle (opcional)" />
            <div class="row"><button class="btn sm" type="button" (click)="lostFor = null">Cancelar</button><button class="btn sm primary" type="button" [disabled]="!lostReason" (click)="confirmLost()">Marcar como perdido</button></div>
          </div>
        }
        <div class="ldet">
          <div>
            <dl class="kv">
              <dt>Interés</dt><dd>{{ lead.procedure?.name || lead.interest || '—' }}{{ lead.procedure && lead.interest ? ' · ' + lead.interest : '' }}</dd>
              <dt>Valor estimado</dt><dd>{{ lead.estimatedValue ? soles(lead.estimatedValue) : '—' }}</dd>
              <dt>Responsable</dt><dd>{{ lead.assignee?.fullName || 'Sin asignar' }}</dd>
              <dt>Contacto</dt><dd>{{ lead.phone || '' }}{{ lead.email ? ' · ' + lead.email : '' }}
                @if (lead.phone) { · <a [href]="'https://wa.me/51' + lead.phone" target="_blank" rel="noopener">WhatsApp</a> }</dd>
              <dt>Paciente</dt>
              <dd>
                @if (lead.patient) { <a [routerLink]="['/pacientes', lead.patient.id]">{{ lead.patient.fullName }}</a>
                  @if (auth.can('CITAS_GESTIONAR') && !lead.nextAppointment && lead.stage !== 'GANADO' && lead.stage !== 'PERDIDO') { · <a routerLink="/citas/nueva" [queryParams]="{ paciente: lead.patient.id }">Agendar cita</a> }
                  @if (lead.nextAppointment) { · cita el {{ dt(lead.nextAppointment) }} }
                } @else {
                  <div class="row wrap">
                    @if (auth.can('PACIENTES_EDITAR')) { <a class="btn sm" routerLink="/pacientes/nuevo" [queryParams]="{ lead: lead.id }">Registrar como paciente</a> }
                    <button class="btn sm ghost" type="button" (click)="linking = !linking">Ya es paciente</button>
                  </div>
                  @if (lead.phoneMatches?.length) {
                    <div class="match small">Con este celular hay {{ lead.phoneMatches.length === 1 ? 'un paciente' : lead.phoneMatches.length + ' pacientes' }}:
                      @for (pm of lead.phoneMatches; track pm.id) { <button class="btn sm ghost" type="button" (click)="link(pm)">Vincular a {{ pm.fullName }}</button> }</div>
                  }
                  @if (linking) { <div class="link-pick"><app-patient-picker (valueChange)="link($event)" /></div> }
                }
              </dd>
              @if (lead.notes) { <dt>Notas</dt><dd>{{ lead.notes }}</dd> }
            </dl>
            <div class="row"><button class="btn sm ghost" type="button" (click)="editLead()">Editar datos</button></div>
            <form class="note" (ngSubmit)="addNote()"><input class="input" name="nt" [(ngModel)]="note" maxlength="500" placeholder="Anotar algo (llamada, respuesta, precio enviado…)" /><button class="btn sm" type="submit" [disabled]="!note.trim()">Anotar</button></form>
            <ul class="tl">
              @for (e of lead.events; track $index) { <li><span class="muted small">{{ dt(e.createdAt) }} · {{ e.createdBy }}</span><div>{{ e.detail }}</div></li> }
            </ul>
          </div>
          <app-task-list [leadId]="lead.id" [subtitle]="lead.fullName" [defaultTitle]="'Llamar a ' + lead.fullName" />
        </div>
      }
    </app-modal>
  `,
  styles: [`
    .page.wide { max-width: none; }
    .filters { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 14px; align-items: center; }
    .filters select { width: auto; } .filters .grow { flex: 1; min-width: 220px; }
    .board { display: grid; grid-template-columns: repeat(6, minmax(200px, 1fr)); gap: 12px; overflow-x: auto; padding-bottom: 8px; align-items: start; }
    .col { min-width: 0; background: var(--surface-2); border: 1px solid var(--line); border-radius: var(--radius); min-height: 300px; }
    .col.drop { outline: 2px dashed var(--teal); }
    .col header { display: grid; padding: 10px 12px; border-bottom: 1px solid var(--line); }
    .cards { display: grid; grid-template-columns: minmax(0, 1fr); gap: 8px; padding: 10px; }
    .card .tag { max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; display: inline-block; line-height: 22px; }
    .match { margin-top: 8px; padding: 8px 10px; background: var(--amber-soft); border-radius: 10px; display: grid; gap: 4px; justify-items: start; }
    .card { min-width: 0; overflow: hidden; background: var(--surface); border: 1px solid var(--line); border-radius: 12px; padding: 10px 11px; display: grid; gap: 4px; cursor: grab; box-shadow: 0 2px 6px -4px rgba(0,0,0,.2); }
    .card:hover { border-color: var(--teal); }
    .nm { font-weight: 700; }
    .meta { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 6px; min-width: 0; }
    .foot { border-top: 1px solid var(--line); padding-top: 4px; margin-top: 2px; }
    .bad { color: var(--gum); font-weight: 600; } .warn { color: var(--amber); }
    .empty-col { text-align: center; padding: 20px 0; }
    .mhead { margin-bottom: 10px; } .mhead h3 { margin: 0; } .auto { width: auto; }
    .kpis { display: grid; grid-template-columns: repeat(6, 1fr); gap: 10px; margin-bottom: 16px; }
    @media (max-width: 1100px) { .kpis { grid-template-columns: repeat(3, 1fr); } }
    .kpis div { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius); padding: 12px 14px; display: grid; }
    .kpis b { font-size: 22px; } .kpis span { color: var(--muted); font-size: 12.5px; }
    .mgrid { align-items: start; }
    .fun { display: grid; grid-template-columns: 150px 1fr 90px; gap: 10px; align-items: center; margin-bottom: 10px; }
    .fb { height: 14px; background: var(--line); border-radius: 99px; overflow: hidden; }
    .fb span { display: block; height: 100%; background: var(--teal); }
    .stages { display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 14px; }
    .stages button { flex: 1; min-width: 110px; border: 1px solid var(--line-strong); background: var(--surface); padding: 8px 6px; font: inherit; font-size: 12.5px; cursor: pointer; border-radius: 8px; }
    .stages button.on { background: var(--teal); color: #fff; border-color: var(--teal); font-weight: 700; }
    .stages button.lost.on { background: var(--gum); border-color: var(--gum); }
    .lost-box { display: grid; gap: 8px; padding: 12px; border: 1px solid #efc3cf; background: var(--gum-soft); border-radius: 12px; margin-bottom: 14px; }
    .chips { display: flex; flex-wrap: wrap; gap: 6px; }
    .chips button { border: 1px solid var(--line-strong); background: var(--surface); border-radius: 999px; padding: 4px 11px; font: inherit; font-size: 12.5px; cursor: pointer; }
    .chips button.on { background: var(--gum); color: #fff; border-color: var(--gum); }
    .ldet { display: grid; grid-template-columns: 1.1fr 1fr; gap: 18px; align-items: start; }
    @media (max-width: 760px) { .ldet { grid-template-columns: 1fr; } }
    .note { display: flex; gap: 8px; margin: 14px 0 8px; }
    .tl { list-style: none; padding: 0; margin: 0; display: grid; gap: 8px; max-height: 260px; overflow: auto; font-size: var(--step--1); }
    .tl li { border-left: 2px solid var(--line-strong); padding-left: 10px; }
    .link-pick { margin-top: 8px; }
    .err { color: var(--gum); font-size: 12.5px; } .err a { cursor: pointer; }
  `],
})
export class LeadsComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  auth = inject(AuthService);
  mode = 'board';
  q = '';
  source = '';
  assigneeId: number | null = null;
  stages: any[] = [];
  opts: any = null;
  drag: any = null;
  dragOver = '';
  lead: any = null;
  lostFor: any = null;
  lostReason = '';
  lostDetail = '';
  linking = false;
  note = '';
  formOpen = false;
  form: any = {};
  fe: Record<string, string> = {};
  formErr = '';
  dupId: number | null = null;
  busy = false;
  days = 90;
  m: any = null;
  today = isoDate();

  ngOnInit() {
    this.load();
    const id = this.route.snapshot.queryParamMap.get('lead');
    if (id) this.open(+id);
    if (this.route.snapshot.queryParamMap.get('nuevo')) setTimeout(() => this.newLead());
  }
  load() {
    this.api.leads({ q: this.q.trim() || undefined, source: this.source || undefined, assigneeId: this.assigneeId ?? undefined }).subscribe((r) => { this.stages = r.stages; this.opts = r.options; });
  }
  loadMetrics() { this.api.leadMetrics(this.days).subscribe((m) => (this.m = m)); }
  open(id: number) {
    this.linking = false; this.lostFor = null; this.note = '';
    this.api.lead(id).subscribe({ next: (l) => (this.lead = l), error: () => this.toast.error('No se encontró el lead.') });
    this.router.navigate([], { queryParams: { lead: id }, replaceUrl: true });
  }
  closeLead() { this.lead = null; this.router.navigate([], { queryParams: {}, replaceUrl: true }); this.load(); }
  drop(stage: string) {
    const l = this.drag; this.dragOver = ''; this.drag = null;
    if (!l || l.stage === stage) return;
    this.move(l, stage);
  }
  move(l: any, stage: string) {
    if (l.stage === stage) return;
    if (stage === 'PERDIDO') { this.lostFor = l; this.lostReason = ''; this.lostDetail = ''; if (!this.lead || this.lead.id !== l.id) this.open(l.id); return; }
    this.api.moveLead(l.id, stage).subscribe({
      next: (r) => { if (this.lead?.id === r.id) this.lead = r; this.toast.ok(r.fullName + ': ' + r.stageLabel + '.'); this.load(); },
      error: (e) => this.toast.error(apiError(e).message),
    });
  }
  confirmLost() {
    const reason = this.lostReason + (this.lostDetail.trim() ? ': ' + this.lostDetail.trim() : '');
    this.api.moveLead(this.lostFor.id, 'PERDIDO', reason).subscribe({
      next: (r) => { this.lostFor = null; this.lead = r; this.toast.ok('Lead marcado como perdido.'); this.load(); },
      error: (e) => this.toast.error(apiError(e).message),
    });
  }
  link(p: any) {
    if (!p) return;
    this.api.linkLeadPatient(this.lead.id, p.id).subscribe({ next: (r) => { this.lead = r; this.linking = false; this.toast.ok('Vinculado a ' + p.fullName + '.'); this.load(); },
      error: (e) => this.toast.error(apiError(e).message) });
  }
  addNote() {
    this.api.leadNote(this.lead.id, this.note).subscribe({ next: (r) => { this.lead = r; this.note = ''; }, error: (e) => this.toast.error(apiError(e).message) });
  }
  newLead() {
    this.fe = {}; this.formErr = ''; this.dupId = null;
    this.form = { id: null, fullName: '', phone: '', email: '', source: '', assigneeId: null, procedureId: null, estimatedValue: null, interest: '', notes: '', followUpDate: this.today };
    this.formOpen = true;
  }
  editLead() {
    const l = this.lead;
    this.fe = {}; this.formErr = ''; this.dupId = null;
    this.form = { id: l.id, fullName: l.fullName, phone: l.phone || '', email: l.email || '', source: l.source, assigneeId: l.assignee?.id ?? null,
      procedureId: l.procedure?.id ?? null, estimatedValue: l.estimatedValue, interest: l.interest || '', notes: l.notes || '' };
    this.formOpen = true;
  }
  procPicked() {
    const p = (this.opts?.procedures || []).find((x: any) => x.id === this.form.procedureId);
    if (p && !this.form.estimatedValue) this.form.estimatedValue = p.price;
  }
  saveLead() {
    this.fe = {}; this.formErr = ''; this.dupId = null; this.busy = true;
    const body = { ...this.form, estimatedValue: this.form.estimatedValue === '' ? null : this.form.estimatedValue, followUpDate: this.form.followUpDate || null };
    this.api.saveLead(this.form.id, body).subscribe({
      next: (l) => { this.busy = false; this.formOpen = false; this.toast.ok(this.form.id ? 'Lead actualizado.' : 'Lead registrado.'); this.load(); if (this.form.id) this.lead = l; },
      error: (e) => {
        this.busy = false; const a = apiError(e);
        this.fe = a.fields || {}; this.formErr = a.fields && !a.fields['leadId'] ? '' : a.message;
        if (a.fields?.['leadId']) this.dupId = +a.fields['leadId'];
      },
    });
  }
  toggleWa() { this.api.setLeadWhatsappAuto(!this.opts.whatsappAuto).subscribe((o) => { this.opts = { ...this.opts, whatsappAuto: o.whatsappAuto }; this.toast.ok(o.whatsappAuto ? 'Los contactos nuevos de WhatsApp entrarán al embudo.' : 'Ya no se crearán leads desde WhatsApp.'); }); }
  sd = shortDate;
  dt = dateTime;
  soles = soles;
}
