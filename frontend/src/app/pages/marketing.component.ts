import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, switchMap, catchError, of } from 'rxjs';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { apiError, dateTime, isoDate, shortDate } from '../core/format';
import { ToastService } from '../core/toast.service';
import { ModalComponent } from '../shared/modal.component';
import { TaskFormComponent } from '../shared/task-form.component';

const MONTHS = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];

/** v2.7: segmentos de pacientes, campañas y cumpleaños. */
@Component({
  selector: 'app-marketing',
  standalone: true,
  imports: [FormsModule, RouterLink, ModalComponent, TaskFormComponent],
  template: `
    <div class="page">
      <header class="page-head">
        <div><h1>Marketing</h1><p class="muted">Arma grupos de pacientes, crea tareas para llamarlos y envía campañas a quienes aceptaron recibir promociones.</p></div>
      </header>
      <nav class="tabs">
        <a [class.on]="view === 'segmentos'" (click)="setView('segmentos')">Segmentos</a>
        <a [class.on]="view === 'campanas'" (click)="setView('campanas')">Campañas</a>
        <a [class.on]="view === 'cumpleanos'" (click)="setView('cumpleanos')">Cumpleaños</a>
      </nav>

      <!-- ===================== SEGMENTOS ===================== -->
      @if (view === 'segmentos') {
        @if (segments.length) {
          <div class="segs">
            @for (s of segments; track s.id) {
              <button type="button" class="seg-card" [class.on]="current?.id === s.id" (click)="loadSegment(s)">
                <strong>{{ s.name }}</strong><span class="muted small">{{ s.summary }}</span>
                <span class="small"><b>{{ s.count }}</b> pacientes · <b>{{ s.reachable }}</b> con permiso de publicidad</span>
              </button>
            }
          </div>
        }
        <div class="builder">
          <section class="panel">
            <div class="panel-head"><h3>{{ current ? current.name : 'Filtros' }}</h3>
              <button class="btn sm ghost" type="button" (click)="reset()">Limpiar</button></div>
            <div class="panel-body grid">
              <div class="field"><label>Cómo nos conocieron</label>
                <div class="chips">@for (o of sources; track o.code) { <button type="button" [class.on]="has(f.sources, o.code)" (click)="toggle('sources', o.code)">{{ o.label }}</button> }</div></div>
              @if (tags.length) {
                <div class="field"><label>Etiquetas</label>
                  <div class="chips">@for (t of tags; track t.tag) { <button type="button" [class.on]="has(f.tags, t.tag)" (click)="toggle('tags', t.tag)">{{ t.tag }}</button> }</div></div>
              }
              <div class="cols-3">
                <div class="field"><label for="sx">Sexo</label><select id="sx" class="input" [(ngModel)]="f.sex" (ngModelChange)="changed()"><option [ngValue]="null">Todos</option><option value="F">Mujeres</option><option value="M">Hombres</option></select></div>
                <div class="field"><label for="a1">Edad desde</label><input id="a1" class="input" type="number" min="0" max="120" [(ngModel)]="f.ageMin" (ngModelChange)="changed()" /></div>
                <div class="field"><label for="a2">hasta</label><input id="a2" class="input" type="number" min="0" max="120" [(ngModel)]="f.ageMax" (ngModelChange)="changed()" /></div>
              </div>
              <div class="cols-2">
                <div class="field"><label for="vi">Última visita</label><select id="vi" class="input" [(ngModel)]="f.visit" (ngModelChange)="changed()">
                  <option [ngValue]="null">Cualquiera</option><option value="CON_3M">Vinieron en los últimos 3 meses</option><option value="SIN_3M">No vienen hace 3 meses</option>
                  <option value="SIN_6M">No vienen hace 6 meses</option><option value="SIN_12M">No vienen hace 1 año</option><option value="NUNCA">Nunca atendidos</option></select></div>
                <div class="field"><label for="na">Próxima cita</label><select id="na" class="input" [(ngModel)]="f.nextAppointment" (ngModelChange)="changed()">
                  <option [ngValue]="null">Indistinto</option><option value="SIN">Sin próxima cita</option><option value="CON">Con próxima cita</option></select></div>
                <div class="field"><label for="tk">Tareas</label><select id="tk" class="input" [(ngModel)]="f.task" (ngModelChange)="changed()">
                  <option [ngValue]="null">Indistinto</option><option value="SIN">Sin tarea pendiente</option><option value="CON">Con tarea pendiente</option></select></div>
                <div class="field"><label for="bm">Cumpleaños en</label><select id="bm" class="input" [(ngModel)]="f.birthdayMonth" (ngModelChange)="changed()">
                  <option [ngValue]="null">Cualquier mes</option>@for (m of months; track $index) { <option [ngValue]="$index + 1">{{ m }}</option> }</select></div>
                <div class="field"><label for="mk">Publicidad</label><select id="mk" class="input" [(ngModel)]="f.marketing" (ngModelChange)="changed()">
                  <option [ngValue]="null">Indistinto</option><option value="SI">Aceptan</option><option value="SIN_RESPUESTA">Sin preguntar</option><option value="NO">No aceptan</option></select></div>
                <div class="field"><label for="rg">Registrados</label><select id="rg" class="input" [(ngModel)]="f.registered" (ngModelChange)="changed()">
                  <option [ngValue]="null">Cuando sea</option><option value="30D">Últimos 30 días</option><option value="90D">Últimos 90 días</option><option value="365D">Último año</option></select></div>
                <div class="field"><label for="dn">Atendidos por</label><select id="dn" class="input" [(ngModel)]="f.dentistId" (ngModelChange)="changed()">
                  <option [ngValue]="null">Cualquier odontólogo</option>@for (d of dentists; track d.id) { <option [ngValue]="d.id">{{ d.fullName }}</option> }</select></div>
              </div>
              <div class="row wrap">
                <label class="check"><input type="checkbox" [checked]="f.budget === 'PENDIENTE'" (change)="f.budget = f.budget ? null : 'PENDIENTE'; changed()" /> Con presupuesto por hacer</label>
                <label class="check"><input type="checkbox" [checked]="!!f.debt" (change)="f.debt = f.debt ? null : true; changed()" /> Con saldo por pagar</label>
              </div>
            </div>
          </section>

          <section class="panel">
            <div class="panel-head">
              <div><h3>{{ pv ? pv.count : '…' }} pacientes</h3><span class="muted small">{{ pv?.description }}</span></div>
              <span class="tag plain" [class.green]="pv?.reachable">{{ pv?.reachable ?? 0 }} pueden recibir campañas</span>
            </div>
            <div class="acts">
              <button class="btn sm" type="button" (click)="openSave()">{{ current ? 'Guardar cambios' : 'Guardar segmento' }}</button>
              @if (auth.can('TAREAS')) { <button class="btn sm" type="button" [disabled]="!pv?.count" (click)="bulkOpen = true; bulk = newBulk()">Crear tareas</button> }
              <button class="btn sm primary" type="button" [disabled]="!pv?.reachable" (click)="newCampaign(true)">Crear campaña</button>
              @if (current) { <button class="btn sm ghost danger" type="button" (click)="delSegment()">Eliminar segmento</button> }
            </div>
            <table class="table">
              <thead><tr><th>Paciente</th><th>Última visita</th><th>Próxima cita</th><th>Publicidad</th></tr></thead>
              <tbody>
                @for (r of pv?.sample || []; track r.id) {
                  <tr><td><a [routerLink]="['/pacientes', r.id]">{{ r.fullName }}</a><div class="muted small">{{ r.phone }}</div></td>
                    <td>{{ r.insights?.lastVisit ? sd(r.insights.lastVisit.startAt) : 'Nunca' }}</td>
                    <td>{{ r.insights?.nextAppointment ? sd(r.insights.nextAppointment.startAt) : '—' }}</td>
                    <td><span class="tag plain" [class.green]="r.marketingConsent === true" [class.gum]="r.marketingConsent === false">{{ r.marketingConsent === true ? 'Acepta' : r.marketingConsent === false ? 'No' : 'Sin preguntar' }}</span></td></tr>
                } @empty { <tr><td colspan="4" class="muted center">Ningún paciente cumple estos filtros.</td></tr> }
              </tbody>
            </table>
            @if (pv && pv.count > pv.sample.length) { <p class="muted small pad">Se muestran los primeros {{ pv.sample.length }}.</p> }
          </section>
        </div>
      }

      <!-- ===================== CAMPAÑAS ===================== -->
      @if (view === 'campanas') {
        <div class="notice small"><span>Las campañas solo llegan a pacientes que <strong>aceptaron recibir promociones</strong> (Ley 29733 y art. 58 del Código de Protección al Consumidor).
          Cada mensaje dice cómo darse de baja: responder BAJA. Se envían entre las 08:00 y las 20:00.</span></div>
        <div class="row spread head2"><h3>Campañas</h3><button class="btn primary" type="button" (click)="newCampaign(false)">+ Nueva campaña</button></div>
        <section class="panel">
          <table class="table">
            <thead><tr><th>Campaña</th><th>Para</th><th>Estado</th><th class="right">Enviados</th><th class="right">Leídos</th><th class="right">Reservaron</th><th></th></tr></thead>
            <tbody>
              @for (c of campaigns; track c.id) {
                <tr>
                  <td><strong>{{ c.name }}</strong><div class="muted small">{{ chLabel[c.channel] }} · {{ c.subject }}</div></td>
                  <td class="small">{{ c.segmentName || c.audience }}<div class="muted">{{ c.status === 'ENVIADA' ? c.recipients + ' pacientes' : '≈ ' + c.estimated + ' pacientes' }}</div></td>
                  <td><span class="tag" [class.green]="c.status === 'ENVIADA'" [class.blue]="c.status === 'PROGRAMADA'" [class.grey]="c.status === 'CANCELADA'">{{ stLabel[c.status] }}</span>
                    <div class="muted small">{{ c.sentAt ? dt(c.sentAt) : c.scheduledAt && c.status === 'PROGRAMADA' ? dt(c.scheduledAt) : '' }}</div></td>
                  <td class="right tabular">{{ c.stats ? c.stats.delivered : '—' }}</td>
                  <td class="right tabular">{{ c.stats ? c.stats.read : '—' }}</td>
                  <td class="right tabular">{{ c.stats ? c.stats.booked : '—' }}{{ c.stats?.optOuts ? ' · ' + c.stats.optOuts + ' bajas' : '' }}</td>
                  <td class="right">@if (c.status === 'BORRADOR' || c.status === 'PROGRAMADA') { <button class="btn sm ghost" type="button" (click)="editCampaign(c)">Abrir</button> }
                    @else { <button class="btn sm ghost" type="button" (click)="detail = c">Ver</button> }</td>
                </tr>
              } @empty { <tr><td colspan="7" class="empty">Aún no hay campañas. Arma un segmento y crea la primera.</td></tr> }
            </tbody>
          </table>
        </section>
      }

      <!-- ===================== CUMPLEAÑOS ===================== -->
      @if (view === 'cumpleanos') {
        @if (bd) {
          <section class="panel">
            <div class="panel-head"><h3>Saludo automático</h3>
              <label class="check"><input type="checkbox" [(ngModel)]="bs.active" (change)="saveBd()" /> {{ bs.active ? 'Activo' : 'Apagado' }}</label></div>
            <div class="panel-body bdset">
              <div class="bubble"><div class="wa">{{ bd.settings.template.body }}</div><div class="foot">Responde BAJA si no deseas recibir más promociones.</div></div>
              <div class="grid">
                <div class="field"><label for="bh">Hora de envío</label><input id="bh" class="input" type="time" [(ngModel)]="bs.time" (change)="saveBd()" /></div>
                <label class="check"><input type="checkbox" [(ngModel)]="bs.createTask" (change)="saveBd()" /> Crear también una tarea "Saludar por su cumpleaños" para recepción</label>
                <p class="muted small">Se envía a quienes no pidieron la baja de promociones. El texto se edita en
                  <a routerLink="/comunicacion" [queryParams]="{ vista: 'plantillas' }">Comunicación → Plantillas</a>.</p>
                <button class="btn sm" type="button" (click)="runBd()">Procesar los cumpleaños de hoy ahora</button>
              </div>
            </div>
          </section>
          <div class="row spread head2">
            <h3>Cumpleaños de {{ months[bdMonth - 1] }} · {{ bd.patients.length }}</h3>
            <select class="input auto" [(ngModel)]="bdMonth" (ngModelChange)="loadBd()" aria-label="Mes">@for (m of months; track $index) { <option [ngValue]="$index + 1">{{ m }}</option> }</select>
          </div>
          <section class="panel">
            <table class="table">
              <thead><tr><th>Día</th><th>Paciente</th><th>Cumple</th><th>Publicidad</th><th>Saludo</th><th></th></tr></thead>
              <tbody>
                @for (p of bd.patients; track p.id) {
                  <tr [class.today]="isToday(p)">
                    <td class="tabular strong">{{ p.day }}{{ isToday(p) ? ' · hoy' : '' }}</td>
                    <td><a [routerLink]="['/pacientes', p.id]">{{ p.fullName }}</a><div class="muted small">{{ p.phone }}</div></td>
                    <td>{{ p.turns }} años</td>
                    <td><span class="tag plain" [class.green]="p.marketingConsent === true" [class.gum]="p.marketingConsent === false">{{ p.marketingConsent === true ? 'Acepta' : p.marketingConsent === false ? 'Baja' : 'Sin preguntar' }}</span></td>
                    <td>{{ p.greeted ? '✓ Enviado' : '—' }}{{ p.tasked ? ' · tarea' : '' }}</td>
                    <td class="right">@if (auth.can('TAREAS')) { <button class="btn sm ghost" type="button" (click)="taskFor = p">+ Tarea</button> }</td>
                  </tr>
                } @empty { <tr><td colspan="6" class="empty">Nadie cumple años este mes.</td></tr> }
              </tbody>
            </table>
          </section>
        }
      }
    </div>

    <!-- guardar segmento -->
    <app-modal [open]="saveOpen" [title]="current ? 'Guardar cambios del segmento' : 'Guardar segmento'" [subtitle]="pv?.description" [width]="480" (close)="saveOpen = false">
      <div class="grid">
        <div class="field"><label class="req" for="sn">Nombre</label><input id="sn" class="input" [(ngModel)]="segName" maxlength="80" placeholder="Ej.: Ortodoncia sin control hace 6 meses" [class.invalid]="se['name']" />
          @if (se['name']) { <span class="err">{{ se['name'] }}</span> }</div>
        <div class="field"><label for="sd">Para qué sirve (opcional)</label><input id="sd" class="input" [(ngModel)]="segDesc" maxlength="300" /></div>
      </div>
      <div modal-actions>
        @if (current) { <button class="btn ghost" type="button" (click)="current = null; saveSegment()">Guardar como nuevo</button> }
        <button class="btn primary" type="button" (click)="saveSegment()">Guardar</button>
      </div>
    </app-modal>

    <!-- tareas en lote -->
    <app-modal [open]="bulkOpen" title="Crear una tarea por paciente" [subtitle]="(pv?.count || 0) + ' pacientes · ' + (pv?.description || '')" [width]="520" (close)="bulkOpen = false">
      <div class="grid">
        <div class="field"><label for="bk">Tipo</label><select id="bk" class="input" [(ngModel)]="bulk.kind">@for (k of kinds; track k[0]) { <option [value]="k[0]">{{ k[1] }}</option> }</select></div>
        <div class="field"><label class="req" for="bt">Tarea</label><input id="bt" class="input" [(ngModel)]="bulk.title" maxlength="150" [class.invalid]="be['title']" />@if (be['title']) { <span class="err">{{ be['title'] }}</span> }</div>
        <div class="cols-2">
          <div class="field"><label class="req" for="bdd">Fecha</label><input id="bdd" class="input" type="date" [(ngModel)]="bulk.dueDate" [min]="today" [class.invalid]="be['dueDate']" /></div>
          <div class="field"><label for="ba">Responsable</label><select id="ba" class="input" [(ngModel)]="bulk.assigneeId"><option [ngValue]="null">Sin asignar</option>@for (u of assignees; track u.id) { <option [ngValue]="u.id">{{ u.fullName }}</option> }</select></div>
        </div>
        <div class="field"><label for="bn">Notas (opcional)</label><textarea id="bn" class="input" rows="2" [(ngModel)]="bulk.notes" maxlength="1000" placeholder="Ej.: Ofrecer control con 20 % de descuento"></textarea></div>
        <p class="muted small">No se repite a quien ya tiene esa misma tarea pendiente. Máximo 500 a la vez.</p>
        @if (bulkErr) { <div class="err">{{ bulkErr }}</div> }
      </div>
      <div modal-actions><button class="btn" type="button" (click)="bulkOpen = false">Cancelar</button><button class="btn primary" type="button" [disabled]="busy" (click)="createBulk()">Crear {{ pv?.count }} tareas</button></div>
    </app-modal>

    <!-- editor de campaña -->
    <app-modal [open]="!!camp" [title]="camp?.id ? 'Campaña: ' + camp?.name : 'Nueva campaña'" [width]="860" (close)="camp = null">
      @if (camp) {
        <div class="ced">
          <div class="grid">
            <div class="field"><label class="req" for="cn">Nombre (interno)</label><input id="cn" class="input" [(ngModel)]="camp.name" maxlength="100" [class.invalid]="ce['name']" placeholder="Ej.: Limpieza de verano" />@if (ce['name']) { <span class="err">{{ ce['name'] }}</span> }</div>
            <div class="field"><label for="cs">Para quién</label>
              <select id="cs" class="input" [(ngModel)]="camp.segmentId" (ngModelChange)="campAudience()">
                <option [ngValue]="null">Los filtros que armé en Segmentos</option>
                @for (s of segments; track s.id) { <option [ngValue]="s.id">{{ s.name }} ({{ s.reachable }})</option> }
              </select>
              <span class="muted small">{{ campAud }}</span></div>
            <div class="field"><label for="cc">Canal</label><select id="cc" class="input" [(ngModel)]="camp.channel"><option value="PREFERIDO">El que prefiere cada paciente</option><option value="WHATSAPP">Solo WhatsApp</option><option value="EMAIL">Solo correo</option></select></div>
            <div class="field"><label class="req" for="csu">Asunto (correo)</label><input id="csu" class="input" [(ngModel)]="camp.subject" maxlength="150" (ngModelChange)="pvCamp$.next(0)" [class.invalid]="ce['subject']" />@if (ce['subject']) { <span class="err">{{ ce['subject'] }}</span> }</div>
            <div class="field"><label class="req" for="cb">Mensaje</label>
              <textarea id="cb" class="input" rows="7" [(ngModel)]="camp.body" maxlength="1000" (ngModelChange)="pvCamp$.next(0)" [class.invalid]="ce['body']"></textarea>
              <div class="vars">@for (v of vars; track v) { <button type="button" (click)="insertVar(v)">{{ '{' + v + '}' }}</button> }</div>
              @if (ce['body']) { <span class="err">{{ ce['body'] }}</span> }<span class="muted small">{{ (camp.body || '').length }}/1000</span></div>
          </div>
          <div>
            <p class="muted small">Así lo verá un paciente</p>
            <div class="bubble"><div class="wa">{{ campPv?.body || '…' }}</div><div class="foot">{{ campPv?.footer }}</div></div>
            <div class="send">
              <label class="field"><span class="small strong">Programar para (opcional)</span><input class="input" type="datetime-local" [(ngModel)]="camp.when" [min]="today + 'T08:00'" /></label>
              @if (error) { <div class="err">{{ error }}</div> }
            </div>
          </div>
        </div>
      }
      <div modal-actions>
        @if (camp?.id) { <button class="btn ghost danger" type="button" (click)="cancelCamp()">Cancelar campaña</button> }
        <button class="btn" type="button" [disabled]="busy" (click)="saveCamp(null)">Guardar borrador</button>
        @if (camp?.when) { <button class="btn primary" type="button" [disabled]="busy" (click)="saveCamp('schedule')">Programar</button> }
        @else { <button class="btn primary" type="button" [disabled]="busy" (click)="saveCamp('now')">Enviar ahora</button> }
      </div>
    </app-modal>

    <!-- detalle de campaña enviada -->
    <app-modal [open]="!!detail" [title]="detail?.name" [subtitle]="'Enviada el ' + dt(detail?.sentAt) + ' · ' + (detail?.segmentName || detail?.audience)" [width]="620" (close)="detail = null">
      @if (detail?.stats; as s) {
        <div class="stats">
          <div><b>{{ detail.recipients }}</b><span>pacientes</span></div>
          <div><b>{{ s.delivered }}</b><span>mensajes entregados</span></div>
          <div><b>{{ s.read }}</b><span>leídos</span></div>
          <div><b>{{ s.failed }}</b><span>fallidos</span></div>
          <div><b>{{ s.booked }}</b><span>reservaron cita en 30 días</span></div>
          <div><b>{{ s.optOuts }}</b><span>pidieron la baja</span></div>
        </div>
        <div class="bubble"><div class="wa">{{ detail.body }}</div></div>
      }
    </app-modal>

    <app-task-form [open]="!!taskFor" [patientId]="taskFor?.id" [subtitle]="taskFor?.fullName" defaultTitle="Saludar por su cumpleaños" (close)="taskFor = null" (saved)="taskFor = null; loadBd()" />
  `,
  styles: [`
    .segs { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 10px; margin-bottom: 14px; }
    .seg-card { display: grid; gap: 4px; text-align: left; padding: 12px 14px; border: 1px solid var(--line); border-radius: var(--radius); background: var(--surface); font: inherit; cursor: pointer; }
    .seg-card.on { border-color: var(--teal); background: var(--teal-soft); }
    .builder { display: grid; grid-template-columns: minmax(320px, 440px) 1fr; gap: 16px; align-items: start; }
    @media (max-width: 1000px) { .builder { grid-template-columns: 1fr; } }
    .chips { display: flex; flex-wrap: wrap; gap: 6px; }
    .chips button, .vars button { border: 1px solid var(--line-strong); background: var(--surface); border-radius: 999px; padding: 4px 11px; font: inherit; font-size: 12.5px; cursor: pointer; }
    .chips button.on { background: var(--teal-soft); border-color: var(--teal); color: var(--teal-strong); font-weight: 700; }
    .vars { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 6px; }
    .vars button { font-family: ui-monospace, monospace; font-size: 11.5px; padding: 2px 8px; }
    .acts { display: flex; flex-wrap: wrap; gap: 8px; padding: 12px 18px; border-bottom: 1px solid var(--line); }
    .danger { color: var(--gum); }
    .center { text-align: center; } .pad { padding: 8px 18px; }
    .head2 { margin: 18px 0 10px; } .head2 h3 { margin: 0; }
    .auto { width: auto; }
    .notice { margin-bottom: 4px; }
    .bubble { background: #e7f8e3; border-radius: 10px 10px 10px 2px; padding: 10px 12px; font-size: 13.5px; }
    .wa { white-space: pre-wrap; word-break: break-word; }
    .foot { margin-top: 8px; font-size: 12px; font-style: italic; color: #4b6b57; }
    .bdset { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
    @media (max-width: 800px) { .bdset { grid-template-columns: 1fr; } }
    tr.today td { background: var(--amber-soft); }
    .ced { display: grid; grid-template-columns: 1.2fr 1fr; gap: 20px; }
    @media (max-width: 760px) { .ced { grid-template-columns: 1fr; } }
    .send { margin-top: 14px; display: grid; gap: 8px; }
    .err { color: var(--gum); font-size: 12.5px; }
    .stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-bottom: 14px; }
    .stats div { border: 1px solid var(--line); border-radius: 12px; padding: 10px 12px; display: grid; }
    .stats b { font-size: 22px; } .stats span { font-size: 12.5px; color: var(--muted); }
  `],
})
export class MarketingComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  auth = inject(AuthService);
  view = 'segmentos';
  months = MONTHS;
  today = isoDate();
  sources: any[] = [];
  tags: any[] = [];
  dentists: any[] = [];
  kinds: [string, string][] = [];
  assignees: any[] = [];
  segments: any[] = [];
  current: any = null;
  f: any = this.emptyFilter();
  pv: any = null;
  private pv$ = new Subject<void>();
  saveOpen = false;
  segName = '';
  segDesc = '';
  se: Record<string, string> = {};
  bulkOpen = false;
  bulk: any = {};
  be: Record<string, string> = {};
  bulkErr = '';
  busy = false;
  campaigns: any[] = [];
  camp: any = null;
  campPv: any = null;
  campAud = '';
  ce: Record<string, string> = {};
  error = '';
  vars = ['paciente', 'paciente_completo', 'clinica', 'telefono_clinica', 'enlace_citas'];
  pvCamp$ = new Subject<number>();
  detail: any = null;
  bd: any = null;
  bs: any = {};
  bdMonth = new Date().getMonth() + 1;
  taskFor: any = null;
  chLabel: Record<string, string> = { PREFERIDO: 'Canal preferido', WHATSAPP: 'WhatsApp', EMAIL: 'Correo' };
  stLabel: Record<string, string> = { BORRADOR: 'Borrador', PROGRAMADA: 'Programada', ENVIANDO: 'Enviando', ENVIADA: 'Enviada', CANCELADA: 'Cancelada' };

  ngOnInit() {
    const qp = this.route.snapshot.queryParamMap;
    this.view = qp.get('vista') || 'segmentos';
    // filtros que vienen de la lista de pacientes
    if (qp.get('fuente')) this.f.sources = [qp.get('fuente')];
    if (qp.get('etiqueta')) this.f.tags = [qp.get('etiqueta')];
    if (qp.get('tarea')) this.f.task = qp.get('tarea');
    if (qp.get('proxima')) this.f.nextAppointment = qp.get('proxima');
    if (qp.get('presupuesto')) this.f.budget = qp.get('presupuesto');
    if (qp.get('deuda')) this.f.debt = true;
    if (qp.get('visita')) this.f.visit = qp.get('visita');
    this.api.patientProfileOptions().subscribe((o) => { this.sources = o.sources; this.tags = o.tags; });
    this.api.dentists().subscribe((d) => (this.dentists = d));
    if (this.auth.can('TAREAS')) this.api.taskOptions().subscribe((o) => { this.kinds = Object.entries(o.kinds) as [string, string][]; this.assignees = o.assignees; });
    this.pv$.pipe(debounceTime(250), switchMap(() => this.api.segmentPreview(this.clean()).pipe(catchError(() => of(null))))).subscribe((r) => { if (r) this.pv = r; });
    this.pvCamp$.pipe(debounceTime(300), switchMap(() => this.api.previewCampaign({ subject: this.camp?.subject || '', body: this.camp?.body || '' }).pipe(catchError(() => of(null)))))
      .subscribe((r) => (this.campPv = r));
    this.loadSegments();
    this.changed();
    this.loadCampaigns();
    if (this.view === 'cumpleanos') this.loadBd();
    if (qp.get('nueva')) setTimeout(() => this.newCampaign(false), 300);
  }
  setView(v: string) {
    this.view = v;
    this.router.navigate([], { queryParams: { vista: v }, replaceUrl: true });
    if (v === 'cumpleanos' && !this.bd) this.loadBd();
    if (v === 'campanas') this.loadCampaigns();
  }

  // ---------- segmentos
  emptyFilter() { return { sources: [], tags: [], sex: null, ageMin: null, ageMax: null, birthdayMonth: null, visit: null, nextAppointment: null, budget: null, debt: null, task: null, marketing: null, registered: null, dentistId: null }; }
  clean() {
    const c: any = {};
    for (const [k, v] of Object.entries(this.f)) if (v !== null && v !== '' && !(Array.isArray(v) && !v.length)) c[k] = v;
    return c;
  }
  has(l: string[], v: string) { return (l || []).includes(v); }
  toggle(k: 'sources' | 'tags', v: string) { const l = this.f[k] || []; this.f[k] = l.includes(v) ? l.filter((x: string) => x !== v) : [...l, v]; this.changed(); }
  changed() { this.pv$.next(); }
  reset() { this.current = null; this.f = this.emptyFilter(); this.changed(); }
  loadSegments() { this.api.segments().subscribe((s) => (this.segments = s)); }
  loadSegment(s: any) { this.current = s; this.f = { ...this.emptyFilter(), ...s.filters, sources: s.filters.sources || [], tags: s.filters.tags || [] }; this.changed(); }
  openSave() { this.se = {}; this.segName = this.current?.name || ''; this.segDesc = this.current?.description || ''; this.saveOpen = true; }
  saveSegment() {
    this.api.saveSegment(this.current?.id ?? null, { name: this.segName, description: this.segDesc, filters: this.clean() }).subscribe({
      next: (s) => { this.saveOpen = false; this.current = s; this.toast.ok('Segmento guardado.'); this.loadSegments(); },
      error: (e) => { const a = apiError(e); this.se = a.fields || { name: a.message }; },
    });
  }
  delSegment() {
    if (!this.current || !confirm('¿Eliminar el segmento "' + this.current.name + '"? Los pacientes no se borran.')) return;
    this.api.deleteSegment(this.current.id).subscribe(() => { this.toast.ok('Segmento eliminado.'); this.reset(); this.loadSegments(); });
  }
  newBulk() { return { kind: 'LLAMAR', title: 'Llamar para agendar su control', dueDate: this.today, assigneeId: null, notes: '', priority: 'NORMAL' }; }
  createBulk() {
    this.be = {}; this.bulkErr = ''; this.busy = true;
    const body = this.current ? { ...this.bulk, segmentId: this.current.id } : { ...this.bulk, filters: this.clean() };
    this.api.segmentTasks(body).subscribe({
      next: (r) => { this.busy = false; this.bulkOpen = false; this.toast.ok(r.created + ' tareas creadas' + (r.skipped ? ' · ' + r.skipped + ' ya la tenían' : '') + '.'); this.changed(); },
      error: (e) => { this.busy = false; const a = apiError(e); this.be = a.fields || {}; this.bulkErr = a.fields ? '' : a.message; },
    });
  }

  // ---------- campañas
  loadCampaigns() { this.api.campaigns().subscribe((c) => (this.campaigns = c)); }
  newCampaign(fromBuilder: boolean) {
    this.ce = {}; this.error = '';
    this.camp = { id: null, name: '', segmentId: fromBuilder && this.current ? this.current.id : null, channel: 'PREFERIDO',
      subject: 'Novedades de {clinica}', body: 'Hola {paciente} 👋\n\n', when: '' };
    if (this.view !== 'campanas' && !fromBuilder) this.setView('campanas');
    this.campAudience(); this.pvCamp$.next(0);
  }
  editCampaign(c: any) {
    this.ce = {}; this.error = '';
    this.camp = { id: c.id, name: c.name, segmentId: c.segmentId, channel: c.channel, subject: c.subject, body: c.body, filters: c.filters,
      when: c.status === 'PROGRAMADA' && c.scheduledAt ? String(c.scheduledAt).slice(0, 16) : '' };
    this.campAudience(); this.pvCamp$.next(0);
  }
  campAudience() {
    if (!this.camp) return;
    if (this.camp.segmentId) { const s = this.segments.find((x) => x.id === this.camp.segmentId); this.campAud = s ? s.summary + ' · ' + s.reachable + ' con permiso' : ''; }
    else this.campAud = (this.camp.filters ? 'Filtros guardados en la campaña' : (this.pv?.description || '')) + ' · ' + (this.camp.filters ? '' : (this.pv?.reachable ?? 0) + ' con permiso');
  }
  insertVar(v: string) { this.camp.body = (this.camp.body || '') + '{' + v + '}'; this.pvCamp$.next(0); }
  saveCamp(action: 'now' | 'schedule' | null) {
    if (action === 'now' && !confirm('¿Enviar la campaña ahora? Llegará a los pacientes que aceptaron promociones.')) return;
    this.ce = {}; this.error = ''; this.busy = true;
    const body = { name: this.camp.name, segmentId: this.camp.segmentId, filters: this.camp.segmentId ? null : (this.camp.filters || this.clean()),
      channel: this.camp.channel, subject: this.camp.subject, body: this.camp.body };
    this.api.saveCampaign(this.camp.id, body).subscribe({
      next: (c) => {
        this.camp.id = c.id;
        if (!action) { this.busy = false; this.toast.ok('Borrador guardado.'); this.camp = null; this.loadCampaigns(); return; }
        this.api.scheduleCampaign(c.id, action === 'schedule' ? this.camp.when + ':00' : null).subscribe({
          next: (r) => { this.busy = false; this.camp = null; this.toast.ok(r.status === 'ENVIADA' ? 'Campaña enviada a ' + r.recipients + ' pacientes.' : 'Campaña programada.'); this.loadCampaigns(); },
          error: (e) => { this.busy = false; const a = apiError(e); this.error = a.fields?.['scheduledAt'] || a.message; this.loadCampaigns(); },
        });
      },
      error: (e) => { this.busy = false; const a = apiError(e); this.ce = a.fields || {}; this.error = a.fields ? '' : a.message; },
    });
  }
  cancelCamp() {
    if (!confirm('¿Cancelar la campaña? No se enviará.')) return;
    this.api.cancelCampaign(this.camp.id).subscribe(() => { this.camp = null; this.toast.ok('Campaña cancelada.'); this.loadCampaigns(); });
  }

  // ---------- cumpleaños
  loadBd() {
    this.api.birthdays(this.bdMonth).subscribe((r) => { this.bd = r; this.bs = { active: r.settings.active, time: r.settings.time, createTask: r.settings.createTask }; });
  }
  saveBd() {
    this.api.updateBirthdaySettings(this.bs).subscribe({ next: () => this.toast.ok('Configuración guardada.'), error: (e) => { this.toast.error(apiError(e).message); this.loadBd(); } });
  }
  runBd() {
    this.api.runBirthdays().subscribe((r) => { this.toast.ok(r.active || r.tasks ? `${r.sent} saludos en cola · ${r.tasks} tareas creadas.` : 'El saludo y las tareas están apagados.'); this.loadBd(); });
  }
  isToday(p: any) { const d = new Date(); return this.bdMonth === d.getMonth() + 1 && p.day === d.getDate(); }
  sd = shortDate;
  dt = dateTime;
}
