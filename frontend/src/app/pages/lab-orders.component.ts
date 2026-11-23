import { DeleteButtonComponent } from '../shared/delete-button.component';
import { Component, Input, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ToastService } from '../core/toast.service';
import { Patient, PlanView } from '../core/models';
import { METHOD_LABEL, apiError, dateTime, shortDate, soles } from '../core/format';
import { IconComponent } from '../shared/icon.component';
import { ModalComponent } from '../shared/modal.component';
import { PatientPickerComponent } from '../shared/patient-picker.component';

export const LAB_STATUS: Record<string, { label: string; tone: string }> = {
  POR_ENVIAR: { label: 'Por enviar', tone: 'grey' }, ENVIADA: { label: 'En laboratorio', tone: 'blue' }, EN_PRUEBA: { label: 'En prueba', tone: 'amber' },
  RECIBIDA: { label: 'Recibida', tone: 'teal' }, ENTREGADA: { label: 'Entregada', tone: 'green' }, ANULADA: { label: 'Anulada', tone: 'gum' },
};
const ACTION_LABEL: Record<string, string> = {
  ENVIADA: 'Enviar al laboratorio', EN_PRUEBA: 'Llegó para prueba', RECIBIDA: 'Recibir trabajo terminado', ENTREGADA: 'Entregar / instalar al paciente', ANULADA: 'Anular',
};
const SHADES = ['A1', 'A2', 'A3', 'A3.5', 'A4', 'B1', 'B2', 'B3', 'B4', 'C1', 'C2', 'C3', 'C4', 'D2', 'D3', 'D4', 'BL1', 'BL2', 'BL3', 'BL4'];

/** v2.5: órdenes de laboratorio. Página completa (/laboratorio) o pestaña de la ficha del paciente (con patientId). */
@Component({
  selector: 'app-lab-orders',
  standalone: true,
  imports: [DeleteButtonComponent, FormsModule, RouterLink, IconComponent, ModalComponent, PatientPickerComponent],
  template: `
    <div [class.page]="!patientId">
      @if (!patientId) {
        <div class="page-head">
          <div><h1>Laboratorio</h1><p class="sub">Trabajos enviados al laboratorio dental: fechas de entrega, pruebas en boca y lo que se les debe.</p></div>
          <div class="actions">
            @if (canEdit) { <button class="btn primary" type="button" (click)="openNew()"><app-icon name="plus" [size]="16" /> Nueva orden</button> }
          </div>
        </div>
        <div class="seg top">
          <button type="button" [class.on]="view === 'ordenes'" (click)="view = 'ordenes'">Órdenes</button>
          <button type="button" [class.on]="view === 'labs'" (click)="view = 'labs'; loadLabs()">Laboratorios</button>
        </div>
      }

      @if (view === 'ordenes') {
        @if (summary) {
          <div class="kpis">
            <button type="button" class="kpi" [class.on]="filter.status === 'POR_ENVIAR'" (click)="quick('POR_ENVIAR')"><span>Por enviar</span><strong>{{ summary.porEnviar }}</strong></button>
            <button type="button" class="kpi" [class.on]="filter.status === 'EN_LAB'" (click)="quick('EN_LAB')"><span>En laboratorio</span><strong>{{ summary.enLaboratorio }}</strong></button>
            <button type="button" class="kpi" [class.bad]="summary.vencidas > 0" [class.on]="filter.overdue" (click)="quick('VENCIDAS')"><span>Atrasadas</span><strong>{{ summary.vencidas }}</strong></button>
            <button type="button" class="kpi" [class.on]="filter.status === 'RECIBIDA'" (click)="quick('RECIBIDA')"><span>Listas para entregar</span><strong>{{ summary.porEntregar }}</strong></button>
            <button type="button" class="kpi" [class.on]="filter.unpaid" (click)="quick('PAGAR')"><span>Por pagar al laboratorio</span><strong>{{ money(summary.porPagar) }}</strong></button>
          </div>
        }
        <section class="panel">
          <div class="panel-head">
            <div class="row wrap filters">
              @if (!patientId) { <input class="input" [(ngModel)]="filter.q" (keyup.enter)="load()" placeholder="Paciente, DNI, código o trabajo" aria-label="Buscar" /> }
              <select class="input" [(ngModel)]="filter.status" (ngModelChange)="filter.overdue = false; filter.unpaid = false; load()" aria-label="Estado">
                <option value="">Todos los estados</option><option value="ACTIVAS">Activas (sin entregar)</option><option value="EN_LAB">En laboratorio o en prueba</option>
                @for (s of statusKeys; track s) { <option [value]="s">{{ st(s).label }}</option> }
              </select>
              @if (!patientId) {
                <select class="input" [(ngModel)]="filter.laboratoryId" (ngModelChange)="load()" aria-label="Laboratorio">
                  <option [ngValue]="null">Todos los laboratorios</option>
                  @for (l of options?.laboratories || []; track l.id) { <option [ngValue]="l.id">{{ l.name }}</option> }
                </select>
              }
            </div>
            @if (patientId && canEdit) { <button class="btn primary sm" type="button" (click)="openNew()"><app-icon name="plus" [size]="15" /> Nueva orden</button> }
          </div>
          @if (!items) { <div class="panel-body"><div class="skeleton" style="height: 120px"></div></div> }
          @else if (!items.length) { <div class="empty small"><p>{{ patientId ? 'Este paciente no tiene trabajos de laboratorio.' : 'No hay órdenes con estos filtros.' }}</p></div> }
          @else {
            <div class="scroll">
              <table class="table">
                <thead><tr><th>Orden</th>@if (!patientId) { <th>Paciente</th> }<th>Trabajo</th><th>Laboratorio</th><th>Entrega</th><th>Estado</th><th class="right">Costo</th><th class="right">Por pagar</th></tr></thead>
                <tbody>
                  @for (o of items; track o.id) {
                    <tr class="click" (click)="openDetail(o.id)" tabindex="0" (keydown.enter)="openDetail(o.id)">
                      <td class="tabular nowrap strong">{{ o.code }}<div class="muted small">{{ date(o.createdAt) }}</div></td>
                      @if (!patientId) { <td>{{ o.patientName }}<div class="muted small">HC {{ o.hcNumber }}</div></td> }
                      <td>{{ o.workTypeLabel }}{{ o.teeth ? ' · ' + o.teeth : '' }}<div class="muted small">{{ o.description }}</div></td>
                      <td>{{ o.laboratoryName }}</td>
                      <td class="nowrap">
                        @if (o.dueDate) { {{ date(o.dueDate) }}
                          @if (o.overdue) { <div class="small red">Atrasada {{ -o.daysLeft }} d</div> }
                          @else if (o.daysLeft !== null && o.daysLeft <= 2) { <div class="small amberc">{{ o.daysLeft === 0 ? 'Vence hoy' : 'En ' + o.daysLeft + ' d' }}</div> }
                        } @else { <span class="muted">—</span> }
                      </td>
                      <td><span [class]="'tag ' + st(o.status).tone">{{ st(o.status).label }}</span></td>
                      <td class="right tabular">{{ money(o.cost) }}</td>
                      <td class="right tabular" [class.red]="o.balance > 0">{{ o.balance > 0 ? money(o.balance) : '—' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </section>
      } @else {
        <section class="panel">
          <div class="panel-head"><h3>Laboratorios</h3>@if (canEdit) { <button class="btn primary sm" type="button" (click)="openLab(null)"><app-icon name="plus" [size]="15" /> Agregar laboratorio</button> }</div>
          <table class="table">
            <thead><tr><th>Laboratorio</th><th>Contacto</th><th class="right">En proceso</th><th class="right">Saldo por pagar</th><th></th></tr></thead>
            <tbody>
              @for (l of labs; track l.id) {
                <tr [class.muted]="!l.active">
                  <td class="strong">{{ l.name }}@if (l.ruc) { <div class="muted small tabular">RUC {{ l.ruc }}</div> }@if (!l.active) { <span class="tag grey">Inactivo</span> }</td>
                  <td>{{ l.contact || '—' }}<div class="muted small">{{ l.phone }}{{ l.email ? ' · ' + l.email : '' }}</div></td>
                  <td class="right tabular">{{ l.inProgress }}</td>
                  <td class="right tabular" [class.red]="l.balance > 0">{{ money(l.balance) }}</td>
                  <td class="right">@if (canEdit) { <button class="btn ghost sm" type="button" (click)="openLab(l)"><app-icon name="edit" [size]="14" /></button> } <app-delete-button entity="laboratories" [recordId]="l.id" what="Laboratorio" [iconOnly]="true" [ghost]="true" (deleted)="loadLabs()" /></td>
                </tr>
              } @empty { <tr><td colspan="5" class="muted center">Agrega los laboratorios con los que trabaja la clínica.</td></tr> }
            </tbody>
          </table>
        </section>
      }
    </div>

    <!-- Nueva / editar orden -->
    <app-modal [open]="formOpen" [title]="f.id ? 'Editar orden ' + f.code : 'Nueva orden de laboratorio'" [width]="680" (close)="formOpen = false">
      <form id="labForm" class="form" (ngSubmit)="saveOrder()">
        @if (!patientId && !f.id) {
          <div class="field"><label class="req">Paciente</label><app-patient-picker [value]="picked" (valueChange)="pickPatient($event)" />
            @if (e['patientId']) { <span class="err">{{ e['patientId'] }}</span> }</div>
        }
        <div class="grid cols-2">
          <div class="field">
            <label class="req" for="lab">Laboratorio</label>
            <select id="lab" name="lab" class="input" [(ngModel)]="f.laboratoryId" [class.invalid]="e['laboratoryId']">
              <option [ngValue]="null">Elige…</option>
              @for (l of options?.laboratories || []; track l.id) { <option [ngValue]="l.id">{{ l.name }}</option> }
            </select>
            @if (e['laboratoryId']) { <span class="err">{{ e['laboratoryId'] }}</span> }
            @else if (!(options?.laboratories || []).length) { <span class="hint">Primero agrega un laboratorio en la pestaña "Laboratorios".</span> }
          </div>
          <div class="field">
            <label class="req" for="wt">Tipo de trabajo</label>
            <select id="wt" name="wt" class="input" [(ngModel)]="f.workType" [class.invalid]="e['workType']">
              <option value="">Elige…</option>
              @for (w of options?.workTypes || []; track w.code) { <option [value]="w.code">{{ w.label }}</option> }
            </select>
            @if (e['workType']) { <span class="err">{{ e['workType'] }}</span> }
          </div>
          @if (planItems.length) {
            <div class="field span2">
              <label for="ti">Procedimiento del plan (opcional)</label>
              <select id="ti" name="ti" class="input" [(ngModel)]="f.treatmentItemId" (ngModelChange)="onItem($event)">
                <option [ngValue]="null">Sin vincular</option>
                @for (it of planItems; track it.id) { <option [ngValue]="it.id">{{ it.label }}</option> }
              </select>
            </div>
          }
          <div class="field span2">
            <label class="req" for="ds">Descripción del trabajo</label>
            <input id="ds" name="ds" class="input" [(ngModel)]="f.description" maxlength="300" placeholder="Ej.: corona de zirconio monolítica" [class.invalid]="e['description']" />
            @if (e['description']) { <span class="err">{{ e['description'] }}</span> }
          </div>
          <div class="field"><label for="tt">Piezas (FDI)</label><input id="tt" name="tt" class="input" [(ngModel)]="f.teeth" maxlength="60" placeholder="16, 17" [class.invalid]="e['teeth']" />
            @if (e['teeth']) { <span class="err">{{ e['teeth'] }}</span> }</div>
          <div class="field"><label for="sh">Color (VITA)</label>
            <input id="sh" name="sh" class="input" [(ngModel)]="f.shade" list="shades" maxlength="20" placeholder="A2" />
            <datalist id="shades">@for (s of shades; track s) { <option [value]="s"></option> }</datalist></div>
          <div class="field"><label for="mt">Material</label><input id="mt" name="mt" class="input" [(ngModel)]="f.material" maxlength="60" placeholder="Zirconio, disilicato, metal-cerámica…" /></div>
          <div class="field"><label for="dd">Fecha de entrega del laboratorio</label><input id="dd" name="dd" type="date" class="input" [(ngModel)]="f.dueDate" [min]="f.id ? null : today" [class.invalid]="e['dueDate']" />
            @if (e['dueDate']) { <span class="err">{{ e['dueDate'] }}</span> }</div>
          <div class="field"><label for="cs">Costo del laboratorio (S/)</label><input id="cs" name="cs" type="number" step="0.01" min="0" class="input tabular" [(ngModel)]="f.cost" [class.invalid]="e['cost']" />
            @if (e['cost']) { <span class="err">{{ e['cost'] }}</span> }</div>
          <div class="field span2"><label for="nt">Indicaciones para el laboratorio</label><textarea id="nt" name="nt" class="input" rows="2" [(ngModel)]="f.notes" maxlength="500" placeholder="Tipo de terminación, antagonista, fotos de color…"></textarea></div>
        </div>
        @if (!f.id) { <label class="check"><input type="checkbox" name="send" [(ngModel)]="f.send" /> Ya se envió hoy (registrar como "En laboratorio")</label> }
      </form>
      <div modal-actions class="row end">
        <button type="button" class="btn" (click)="formOpen = false">Cancelar</button>
        <button type="submit" form="labForm" class="btn primary" [disabled]="saving">{{ saving ? 'Guardando…' : 'Guardar orden' }}</button>
      </div>
    </app-modal>

    <!-- Detalle -->
    <app-modal [open]="!!d" [title]="d ? d.code + ' · ' + d.workTypeLabel : ''" [subtitle]="d ? d.patientName + ' · ' + d.laboratoryName : ''" [width]="720" (close)="d = null">
      @if (d) {
        <div class="det">
          <div class="row wrap gap"><span [class]="'tag ' + st(d.status).tone">{{ st(d.status).label }}</span>
            @if (d.overdue) { <span class="tag gum">Atrasada</span> }
            @if (!patientId) { <a class="link small" [routerLink]="['/pacientes', d.patientId]" [queryParams]="{ tab: 'laboratorio' }" (click)="d = null">Ver ficha del paciente</a> } <app-delete-button entity="lab_orders" [recordId]="d.id" what="Orden de laboratorio" (deleted)="d = null; load()" /></div>
          <dl class="kv">
            <dt>Trabajo</dt><dd>{{ d.description }}{{ d.teeth ? ' · piezas ' + d.teeth : '' }}</dd>
            @if (d.shade || d.material) { <dt>Color / material</dt><dd>{{ d.shade || '—' }} · {{ d.material || '—' }}</dd> }
            @if (d.treatmentItem) { <dt>Plan</dt><dd>{{ d.treatmentItem.procedure }}{{ d.treatmentItem.tooth ? ' · pieza ' + d.treatmentItem.tooth : '' }}</dd> }
            <dt>Odontólogo</dt><dd>{{ d.dentistName || '—' }}</dd>
            <dt>Fechas</dt><dd>Envío {{ d.sentAt ? date(d.sentAt) : '—' }} · Entrega pactada {{ d.dueDate ? date(d.dueDate) : '—' }}{{ d.receivedAt ? ' · Recibida ' + date(d.receivedAt) : '' }}{{ d.deliveredAt ? ' · Entregada ' + date(d.deliveredAt) : '' }}</dd>
            @if (d.notes) { <dt>Indicaciones</dt><dd>{{ d.notes }}</dd> }
            @if (d.voidReason) { <dt>Motivo de anulación</dt><dd class="red">{{ d.voidReason }}</dd> }
            <dt>Costo</dt><dd class="tabular">{{ money(d.cost) }} · pagado {{ money(d.paid) }} · <strong [class.red]="d.balance > 0">por pagar {{ money(d.balance) }}</strong></dd>
          </dl>

          @if (canEdit && d.next.length) {
            <div class="actions-box">
              <div class="row wrap gap">
                @for (n of d.next; track n) {
                  <button type="button" class="btn sm" [class.primary]="n !== 'ANULADA'" [class.danger]="n === 'ANULADA'" (click)="pendingStatus = n">{{ act(n) }}</button>
                }
                @if (d.status !== 'ENTREGADA' && d.status !== 'ANULADA') { <button type="button" class="btn sm ghost" (click)="editOrder()"><app-icon name="edit" [size]="14" /> Editar</button> }
              </div>
              @if (pendingStatus) {
                <div class="row wrap gap confirm">
                  <input class="input" type="date" [(ngModel)]="statusDate" [max]="today" aria-label="Fecha" />
                  <input class="input grow" [(ngModel)]="statusNote" [placeholder]="pendingStatus === 'ANULADA' ? 'Motivo de la anulación (obligatorio)' : pendingStatus === 'EN_PRUEBA' ? 'Qué se prueba (estructura, bizcocho…)' : 'Nota (opcional)'" maxlength="300" aria-label="Nota" />
                  <button type="button" class="btn primary sm" (click)="doStatus()" [disabled]="saving">Confirmar: {{ act(pendingStatus) }}</button>
                  <button type="button" class="btn sm" (click)="pendingStatus = null">Cancelar</button>
                </div>
              }
            </div>
          }

          @if (canPay && d.status !== 'ANULADA' && d.balance > 0) {
            <form class="pay row wrap gap" (ngSubmit)="doPay()">
              <strong class="grow">Pagar al laboratorio</strong>
              <input class="input amt tabular" name="pa" type="number" step="0.01" min="0" [(ngModel)]="pay.amount" aria-label="Importe" [class.invalid]="pe['amount']" />
              <select class="input" name="pm" [(ngModel)]="pay.method" aria-label="Medio de pago">@for (m of methods; track m) { <option [value]="m">{{ methodLabel[m] }}</option> }</select>
              <input class="input" name="pr" [(ngModel)]="pay.reference" placeholder="N° operación" maxlength="40" aria-label="Referencia" />
              <button class="btn primary sm" type="submit" [disabled]="saving">Registrar pago</button>
              @if (pe['amount'] || pe['method']) { <span class="err full">{{ pe['amount'] || pe['method'] }}</span> }
              <span class="muted small full">Se registra como egreso de caja (categoría Laboratorio).</span>
            </form>
          }

          @if (d.payments.length) {
            <h4>Pagos</h4>
            <ul class="tl">@for (p of d.payments; track p.id) { <li [class.voided]="p.status !== 'VALIDO'"><span class="tabular strong">{{ money(p.amount) }}</span> · {{ methodLabel[p.method] || p.method }}{{ p.reference ? ' · ' + p.reference : '' }} · {{ date(p.date) }} · {{ p.createdBy }}{{ p.status !== 'VALIDO' ? ' (egreso anulado)' : '' }}</li> }</ul>
          }
          <h4>Historial</h4>
          <ul class="tl">@for (ev of d.events; track ev.id) { <li><span class="tag plain" [class]="'tag plain ' + st(ev.status).tone">{{ st(ev.status).label }}</span> {{ ev.note || '' }} <span class="muted small">· {{ dt(ev.createdAt) }} · {{ ev.createdBy }}</span></li> }</ul>
        </div>
      }
    </app-modal>

    <!-- Laboratorio -->
    <app-modal [open]="labOpen" [title]="lf.id ? 'Editar laboratorio' : 'Agregar laboratorio'" [width]="560" (close)="labOpen = false">
      <form id="labF" class="form grid cols-2" (ngSubmit)="saveLab()">
        <div class="field span2"><label class="req" for="ln">Nombre</label><input id="ln" name="ln" class="input" [(ngModel)]="lf.name" maxlength="120" [class.invalid]="le['name']" />@if (le['name']) { <span class="err">{{ le['name'] }}</span> }</div>
        <div class="field"><label for="lr">RUC</label><input id="lr" name="lr" class="input tabular" [(ngModel)]="lf.ruc" maxlength="11" [class.invalid]="le['ruc']" />@if (le['ruc']) { <span class="err">{{ le['ruc'] }}</span> }</div>
        <div class="field"><label for="lc">Contacto</label><input id="lc" name="lc" class="input" [(ngModel)]="lf.contact" maxlength="120" /></div>
        <div class="field"><label for="lp">Teléfono</label><input id="lp" name="lp" class="input tabular" [(ngModel)]="lf.phone" maxlength="15" [class.invalid]="le['phone']" />@if (le['phone']) { <span class="err">{{ le['phone'] }}</span> }</div>
        <div class="field"><label for="le">Correo</label><input id="le" name="le" class="input" [(ngModel)]="lf.email" maxlength="120" [class.invalid]="le['email']" />@if (le['email']) { <span class="err">{{ le['email'] }}</span> }</div>
        <div class="field span2"><label for="la">Dirección</label><input id="la" name="la" class="input" [(ngModel)]="lf.address" maxlength="200" /></div>
        <div class="field span2"><label for="lno">Notas</label><input id="lno" name="lno" class="input" [(ngModel)]="lf.notes" maxlength="300" placeholder="Tiempos de entrega, horario de recojo…" /></div>
        @if (lf.id) { <label class="check span2"><input type="checkbox" name="lact" [(ngModel)]="lf.active" /> Activo</label> }
      </form>
      <div modal-actions class="row end">
        <button type="button" class="btn" (click)="labOpen = false">Cancelar</button>
        <button type="submit" form="labF" class="btn primary" [disabled]="saving">Guardar</button>
      </div>
    </app-modal>
  `,
  styles: [`
    .seg { display: inline-flex; border: 1px solid var(--line); border-radius: 999px; padding: 3px; background: var(--surface); margin-bottom: 14px; }
    .seg button { border: 0; background: none; padding: 6px 16px; border-radius: 999px; font: inherit; cursor: pointer; }
    .seg button.on { background: var(--teal-soft); color: var(--teal-strong); font-weight: 700; }
    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px; margin-bottom: 14px; }
    .kpi { text-align: left; border: 1px solid var(--line); background: var(--surface); border-radius: 14px; padding: 10px 14px; display: grid; gap: 2px; font: inherit; cursor: pointer; }
    .kpi span { font-size: 12px; color: var(--muted); } .kpi strong { font-size: 22px; }
    .kpi.on { border-color: var(--teal); background: var(--teal-soft); }
    .kpi.bad strong { color: var(--gum); }
    .filters { gap: 8px; } .filters .input { width: auto; min-width: 180px; }
    .scroll { overflow-x: auto; }
    .red { color: var(--gum); } .amberc { color: var(--amber); }
    .span2 { grid-column: 1 / -1; }
    .check { display: flex; gap: 8px; align-items: center; margin-top: 8px; }
    .end { justify-content: flex-end; gap: 8px; }
    .gap { gap: 8px; }
    .det h4 { margin: 16px 0 6px; font-size: 13px; text-transform: uppercase; letter-spacing: .04em; color: var(--muted); }
    .actions-box { border: 1px solid var(--line); border-radius: 12px; padding: 10px; margin-top: 12px; display: grid; gap: 8px; }
    .confirm .input { width: auto; }
    .pay { border: 1px solid #ecd3a6; background: var(--amber-soft); border-radius: 12px; padding: 10px; margin-top: 12px; align-items: center; }
    .pay .input { width: auto; } .amt { max-width: 120px; }
    .full { flex-basis: 100%; }
    .tl { list-style: none; padding: 0; margin: 0; display: grid; gap: 6px; font-size: 14px; }
    .tl .voided { text-decoration: line-through; color: var(--muted); }
    .btn.danger { color: var(--gum); border-color: #efc3cf; }
  `],
})
export class LabOrdersComponent implements OnInit {
  @Input() patientId?: number;
  private api = inject(Api);
  private toast = inject(ToastService);
  private route = inject(ActivatedRoute);
  auth = inject(AuthService);

  view: 'ordenes' | 'labs' = 'ordenes';
  options: any = null;
  items: any[] | null = null;
  summary: any = null;
  labs: any[] = [];
  filter: { status: string; laboratoryId: number | null; overdue: boolean; unpaid: boolean; q: string } = { status: '', laboratoryId: null, overdue: false, unpaid: false, q: '' };
  statusKeys = Object.keys(LAB_STATUS);
  shades = SHADES;
  methods = Object.keys(METHOD_LABEL);
  methodLabel = METHOD_LABEL;
  today = new Date().toLocaleDateString('en-CA');

  formOpen = false;
  f: any = {};
  e: Record<string, string> = {};
  picked: Patient | null = null;
  planItems: { id: number; label: string; tooth?: number }[] = [];
  saving = false;

  d: any = null;
  pendingStatus: string | null = null;
  statusNote = '';
  statusDate = this.today;
  pay: any = { method: 'TRANSFERENCIA' };
  pe: Record<string, string> = {};

  labOpen = false;
  lf: any = {};
  le: Record<string, string> = {};

  get canEdit() { return this.auth.can('LABORATORIO'); }
  get canPay() { return this.auth.can('CAJA_EGRESOS'); }

  ngOnInit() {
    if (!this.patientId) this.filter.status = this.route.snapshot.queryParamMap.get('estado') || 'ACTIVAS';
    this.api.labOptions().subscribe((o) => (this.options = o));
    this.load();
  }

  load() {
    this.api.labOrders({ ...this.filter, patientId: this.patientId }).subscribe((r) => { this.items = r.items; this.summary = r.summary; });
  }
  loadLabs() { this.api.laboratories(true).subscribe((l) => (this.labs = l)); }

  quick(k: string) {
    this.filter = { ...this.filter, status: '', overdue: false, unpaid: false };
    if (k === 'VENCIDAS') { this.filter.status = 'EN_LAB'; this.filter.overdue = true; }
    else if (k === 'PAGAR') this.filter.unpaid = true;
    else this.filter.status = k;
    this.load();
  }

  openNew() {
    this.e = {}; this.picked = null; this.planItems = [];
    this.f = { laboratoryId: this.options?.laboratories?.length === 1 ? this.options.laboratories[0].id : null, workType: '', treatmentItemId: null, cost: null, send: false };
    if (this.patientId) this.loadPlan(this.patientId);
    this.formOpen = true;
  }
  pickPatient(p: Patient | null) { this.picked = p; this.planItems = []; this.f.treatmentItemId = null; if (p) this.loadPlan(p.id); }

  private loadPlan(pid: number) {
    this.api.plans(pid).subscribe({
      next: (plans: PlanView[]) => {
        this.planItems = plans.filter((p) => p.plan.status !== 'ANULADO').flatMap((p) => p.items.filter((i) => i.status !== 'ANULADO')
          .map((i) => ({ id: i.id, tooth: i.tooth, label: `${i.procedure.name}${i.tooth ? ' · pieza ' + i.tooth : ''} — ${p.plan.title}` })));
      },
      error: () => (this.planItems = []),
    });
  }
  onItem(id: number | null) {
    const it = this.planItems.find((x) => x.id === id);
    if (it?.tooth && !this.f.teeth) this.f.teeth = String(it.tooth);
    if (it && !this.f.description) this.f.description = it.label.split(' — ')[0];
  }

  saveOrder() {
    const body = { ...this.f, patientId: this.patientId ?? this.picked?.id ?? this.f.patientId };
    this.saving = true; this.e = {};
    const req = this.f.id ? this.api.updateLabOrder(this.f.id, body) : this.api.createLabOrder(body);
    req.subscribe({
      next: (o) => { this.saving = false; this.formOpen = false; this.toast.ok(`Orden ${o.code} guardada.`); this.load(); if (this.f.id) this.d = o; },
      error: (err) => { this.saving = false; const a = apiError(err); this.e = a.fields || {}; this.toast.error(a.message); },
    });
  }

  openDetail(id: number) {
    this.pendingStatus = null; this.statusNote = ''; this.statusDate = this.today; this.pe = {};
    this.api.labOrder(id).subscribe((o) => { this.d = o; this.pay = { method: 'TRANSFERENCIA', amount: o.balance }; });
  }

  editOrder() {
    const o = this.d;
    this.e = {}; this.planItems = [];
    this.f = { id: o.id, code: o.code, patientId: o.patientId, laboratoryId: o.laboratoryId, workType: o.workType, treatmentItemId: o.treatmentItemId,
      description: o.description, teeth: o.teeth, shade: o.shade, material: o.material, dueDate: o.dueDate, cost: o.cost, notes: o.notes };
    this.loadPlan(o.patientId);
    this.d = null; this.formOpen = true;
  }

  doStatus() {
    this.saving = true;
    this.api.labOrderStatus(this.d.id, { status: this.pendingStatus, note: this.statusNote, date: this.statusDate }).subscribe({
      next: (o) => { this.saving = false; this.d = o; this.pendingStatus = null; this.statusNote = ''; this.toast.ok('Estado actualizado: ' + this.st(o.status).label + '.'); this.load(); },
      error: (err) => { this.saving = false; this.toast.error(apiError(err).message); },
    });
  }

  doPay() {
    this.saving = true; this.pe = {};
    this.api.payLabOrder(this.d.id, this.pay).subscribe({
      next: (o) => { this.saving = false; this.d = o; this.pay = { method: this.pay.method, amount: o.balance }; this.toast.ok('Pago registrado en caja.'); this.load(); },
      error: (err) => { this.saving = false; const a = apiError(err); this.pe = a.fields || {}; this.toast.error(a.message); },
    });
  }

  openLab(l: any) { this.le = {}; this.lf = l ? { ...l, active: !!l.active } : { active: true }; this.labOpen = true; }
  saveLab() {
    this.saving = true; this.le = {};
    const req = this.lf.id ? this.api.updateLaboratory(this.lf.id, this.lf) : this.api.createLaboratory(this.lf);
    req.subscribe({
      next: () => { this.saving = false; this.labOpen = false; this.toast.ok('Laboratorio guardado.'); this.loadLabs(); this.api.labOptions().subscribe((o) => (this.options = o)); },
      error: (err) => { this.saving = false; const a = apiError(err); this.le = a.fields || {}; this.toast.error(a.message); },
    });
  }

  st(s: string) { return LAB_STATUS[s] || { label: s, tone: 'grey' }; }
  act(s: string) { return ACTION_LABEL[s] || s; }
  money = soles;
  date = shortDate;
  dt = dateTime;
}
