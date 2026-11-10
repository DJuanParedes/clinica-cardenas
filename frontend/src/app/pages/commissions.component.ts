import { Component, Input, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api, CommissionRule } from '../core/api.service';
import { ToastService } from '../core/toast.service';
import { Dentist, Procedure } from '../core/models';
import {
  METHOD_LABEL, SETTLEMENT_LABEL, SETTLEMENT_TONE, apiError, dateTime, isoDate, periodPreset, shortDate, soles,
} from '../core/format';
import { IconComponent } from '../shared/icon.component';
import { ModalComponent } from '../shared/modal.component';

type Tab = 'reglas' | 'calcular' | 'liquidaciones';

/** HU-16 reglas · HU-17 cálculo · HU-18 liquidación de comisiones. */
@Component({
  selector: 'app-commissions',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent, ModalComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <h1>Comisiones de odontólogos</h1>
          <p class="sub">Reglas acordadas, cálculo por período y liquidación de lo pagado y pendiente.</p>
        </div>
      </div>

      <section class="cards">
        @for (s of summary; track s.dentistId) {
          <article class="panel dcard">
            <span class="bar" [style.background]="s.color"></span>
            <div class="grow">
              <div class="strong">{{ s.fullName }}</div>
              <div class="muted small">{{ s.specialty }} · {{ s.activeRules }} {{ s.activeRules === 1 ? 'regla vigente' : 'reglas vigentes' }}</div>
            </div>
            <div class="right">
              <div class="muted small">Por pagar</div>
              <div class="tabular strong" [class.owe]="s.pending > 0">{{ money(s.pending) }}</div>
              <div class="muted small">pagado {{ money(s.paid) }}</div>
            </div>
          </article>
        }
      </section>

      <nav class="tabs" aria-label="Secciones de comisiones">
        <a [class.on]="tab === 'reglas'" (click)="go('reglas')">Reglas de comisión</a>
        <a [class.on]="tab === 'calcular'" (click)="go('calcular')">Calcular por período</a>
        <a [class.on]="tab === 'liquidaciones'" (click)="go('liquidaciones')">Liquidaciones</a>
      </nav>

      @switch (tab) {
        @case ('reglas') {
          <section class="panel">
            <div class="panel-head">
              <h3>Reglas por odontólogo</h3>
              <button class="btn primary sm" type="button" (click)="newRule()"><app-icon name="plus" [size]="15" /> Nueva regla</button>
            </div>
            <table class="table">
              <thead><tr><th>Odontólogo</th><th>Regla</th><th>Base</th><th>Vigencia</th><th>Estado</th><th></th></tr></thead>
              <tbody>
                @for (r of rules; track r.id) {
                  <tr [class.off]="!r.active">
                    <td><div class="row"><span class="dot" [style.background]="r.dentist.color"></span><span class="strong">{{ r.dentist.fullName }}</span></div></td>
                    <td><div class="strong">{{ r.calcType === 'PORCENTAJE' ? r.value + ' %' : money(r.value) + ' fijo' }}</div>
                      <div class="muted small">{{ r.procedure ? r.procedure.name : 'Todos los procedimientos' }}</div></td>
                    <td>{{ r.base === 'COBRADO' ? 'Lo cobrado' : 'Precio del procedimiento' }}</td>
                    <td class="tabular">{{ date(r.validFrom) }} – {{ r.validTo ? date(r.validTo) : 'sin fin' }}</td>
                    <td>
                      @if (!r.active) { <span class="tag grey">Inactiva</span> }
                      @else if (isCurrent(r)) { <span class="tag green">Vigente</span> }
                      @else { <span class="tag amber">Fuera de vigencia</span> }
                    </td>
                    <td class="right"><button class="btn sm" type="button" (click)="editRule(r)">Editar</button></td>
                  </tr>
                } @empty { <tr><td colspan="6"><div class="empty small"><p>Aún no hay reglas. Crea la primera para poder calcular comisiones.</p></div></td></tr> }
              </tbody>
            </table>
            <div class="panel-foot small muted">
              La regla de un procedimiento tiene prioridad sobre la regla general. Con base "lo cobrado", la comisión se calcula cuando el procedimiento está pagado por completo.
            </div>
          </section>
        }

        @case ('calcular') {
          <section class="panel filters">
            <div class="field">
              <label for="cd">Odontólogo</label>
              <select id="cd" class="input" [(ngModel)]="calcDentist">
                @for (d of dentists; track d.id) { <option [ngValue]="d.id">{{ d.fullName }}</option> }
              </select>
            </div>
            <div class="field"><label for="cf">Desde</label><input id="cf" type="date" class="input" [(ngModel)]="calcFrom" /></div>
            <div class="field"><label for="ct">Hasta</label><input id="ct" type="date" class="input" [(ngModel)]="calcTo" /></div>
            <div class="row quick">
              <button class="btn sm ghost" type="button" (click)="month('mes')">Este mes</button>
              <button class="btn sm ghost" type="button" (click)="month('mes-anterior')">Mes anterior</button>
            </div>
            <button class="btn primary" type="button" (click)="calculate()" [disabled]="!calcDentist || calculating">
              @if (calculating) { <span class="spin"></span> } @else { <app-icon name="percent" [size]="16" /> } Calcular
            </button>
          </section>

          @if (calc) {
            <section class="figures panel">
              <div class="fig"><span class="l">Comisión por liquidar</span><span class="v tabular">{{ money(selectedTotal) }}</span><span class="meta">{{ selected.size }} de {{ calc.lines.length }} procedimientos seleccionados</span></div>
              <div class="fig"><span class="l">Base de cálculo</span><span class="v tabular">{{ money(calc.baseTotal) }}</span><span class="meta">procedimientos realizados en el período</span></div>
              <div class="fig"><span class="l">Ya liquidado</span><span class="v tabular muted">{{ money(calc.alreadySettledTotal) }}</span><span class="meta">{{ calc.alreadySettled.length }} procedimientos</span></div>
            </section>

            <section class="panel">
              <div class="panel-head">
                <h3>Detalle del cálculo · {{ calc.dentist.fullName }}</h3>
                <button class="btn primary sm" type="button" (click)="settle()" [disabled]="!selected.size || settling">
                  <app-icon name="check" [size]="15" /> Generar liquidación {{ money(selectedTotal) }}
                </button>
              </div>
              <table class="table">
                <thead><tr><th><input type="checkbox" [checked]="selected.size === calc.lines.length && calc.lines.length > 0" (change)="toggleAll()" aria-label="Seleccionar todo" /></th>
                  <th>Fecha</th><th>Paciente</th><th>Procedimiento</th><th>Regla aplicada</th><th class="right">Base</th><th class="right">Comisión</th></tr></thead>
                <tbody>
                  @for (l of calc.lines; track l.treatmentItemId) {
                    <tr class="click" (click)="toggle(l.treatmentItemId)">
                      <td><input type="checkbox" [checked]="selected.has(l.treatmentItemId)" (click)="$event.stopPropagation()" (change)="toggle(l.treatmentItemId)" [attr.aria-label]="l.procedure" /></td>
                      <td class="tabular nowrap">{{ date(l.doneAt) }}</td>
                      <td>{{ l.patientName }}</td>
                      <td>{{ l.procedure }}</td>
                      <td class="muted small">{{ l.rule }}</td>
                      <td class="right tabular">{{ money(l.base) }}</td>
                      <td class="right tabular strong">{{ money(l.commission) }}</td>
                    </tr>
                  } @empty { <tr><td colspan="7"><div class="empty small"><p>No hay procedimientos por liquidar en el período.</p></div></td></tr> }
                </tbody>
              </table>
            </section>

            @if (calc.pendingCollection.length || calc.withoutRule.length) {
              <div class="two">
                @if (calc.pendingCollection.length) {
                  <section class="panel">
                    <div class="panel-head"><h3>Esperan cobro</h3><span class="muted small">{{ calc.pendingCollection.length }}</span></div>
                    <ul class="mini">@for (l of calc.pendingCollection; track l.treatmentItemId) { <li><span>{{ date(l.doneAt) }} · {{ l.patientName }} · {{ l.procedure }}</span><span class="muted">{{ l.reason }}</span></li> }</ul>
                    <div class="panel-foot small muted">La regla de este odontólogo comisiona sobre lo cobrado: se incluirán cuando estén pagados.</div>
                  </section>
                }
                @if (calc.withoutRule.length) {
                  <section class="panel">
                    <div class="panel-head"><h3>Sin regla vigente</h3><span class="muted small">{{ calc.withoutRule.length }}</span></div>
                    <ul class="mini">@for (l of calc.withoutRule; track l.treatmentItemId) { <li><span>{{ date(l.doneAt) }} · {{ l.procedure }}</span><span class="tabular">{{ money(l.price) }}</span></li> }</ul>
                    <div class="panel-foot small muted">Crea una regla que cubra estas fechas para comisionarlos.</div>
                  </section>
                }
              </div>
            }
          }
        }

        @case ('liquidaciones') {
          <section class="panel">
            <div class="panel-head">
              <h3>Liquidaciones</h3>
              <select class="input sel" [(ngModel)]="setStatus" (ngModelChange)="loadSettlements()" aria-label="Filtrar por estado">
                <option value="">Todos los estados</option>
                @for (k of settlementStates; track k) { <option [value]="k">{{ sLabel(k) }}</option> }
              </select>
            </div>
            <table class="table">
              <thead><tr><th>N.°</th><th>Odontólogo</th><th>Período</th><th>Estado</th><th class="right">Total</th><th class="right">Pagado</th><th class="right">Pendiente</th><th></th></tr></thead>
              <tbody>
                @for (s of settlementsList; track s.id) {
                  <tr class="click" (click)="openSettlement(s.id)">
                    <td class="tabular">{{ s.id }}</td>
                    <td>{{ s.dentist.fullName }}</td>
                    <td class="tabular nowrap">{{ date(s.periodFrom) }} – {{ date(s.periodTo) }}</td>
                    <td><span class="tag" [class]="'tag ' + sTone(s.status)">{{ sLabel(s.status) }}</span></td>
                    <td class="right tabular">{{ money(s.totalAmount) }}</td>
                    <td class="right tabular">{{ money(s.paidAmount) }}</td>
                    <td class="right tabular strong" [class.owe]="s.pendingAmount > 0">{{ money(s.pendingAmount) }}</td>
                    <td class="right"><app-icon name="chevronRight" [size]="16" /></td>
                  </tr>
                } @empty { <tr><td colspan="8"><div class="empty small"><p>No hay liquidaciones con ese filtro.</p></div></td></tr> }
              </tbody>
            </table>
          </section>
        }
      }
    </div>

    <app-modal [open]="ruleOpen" [title]="ruleEditing ? 'Editar regla' : 'Nueva regla de comisión'" [width]="640" (close)="ruleOpen = false">
      <form class="grid cols-2" (ngSubmit)="saveRule()">
        <div class="field span-2">
          <label class="req" for="rd">Odontólogo</label>
          <select id="rd" name="rd" class="input" [class.invalid]="e['dentistId']" [(ngModel)]="rf.dentistId" [disabled]="ruleLocked">
            @for (d of dentists; track d.id) { <option [ngValue]="d.id">{{ d.fullName }} · {{ d.specialty }}</option> }
          </select>
          @if (e['dentistId']) { <span class="err">{{ e['dentistId'] }}</span> }
        </div>
        <div class="field span-2">
          <label for="rp">Aplica a</label>
          <select id="rp" name="rp" class="input" [class.invalid]="e['procedureId']" [(ngModel)]="rf.procedureId" [disabled]="ruleLocked">
            <option [ngValue]="null">Todos los procedimientos (regla general)</option>
            @for (p of procedures; track p.id) { <option [ngValue]="p.id">{{ p.name }} · {{ money(p.price) }}</option> }
          </select>
        </div>
        <div class="field">
          <span class="label req">Tipo</span>
          <div class="seg">
            <button type="button" [class.on]="rf.calcType === 'PORCENTAJE'" (click)="rf.calcType = 'PORCENTAJE'" [disabled]="ruleLocked">Porcentaje</button>
            <button type="button" [class.on]="rf.calcType === 'MONTO_FIJO'" (click)="rf.calcType = 'MONTO_FIJO'" [disabled]="ruleLocked">Monto fijo</button>
          </div>
        </div>
        <div class="field">
          <label class="req" for="rv">{{ rf.calcType === 'PORCENTAJE' ? 'Porcentaje (%)' : 'Monto por procedimiento (S/)' }}</label>
          <input id="rv" name="rv" type="number" step="0.01" min="0" class="input tabular" [class.invalid]="e['value']" [(ngModel)]="rf.value" [disabled]="ruleLocked" />
          @if (e['value']) { <span class="err">{{ e['value'] }}</span> }
        </div>
        <div class="field span-2">
          <span class="label req">Base de cálculo</span>
          <div class="seg">
            <button type="button" [class.on]="rf.base === 'REALIZADO'" (click)="rf.base = 'REALIZADO'" [disabled]="ruleLocked">Precio del procedimiento realizado</button>
            <button type="button" [class.on]="rf.base === 'COBRADO'" (click)="rf.base = 'COBRADO'" [disabled]="ruleLocked">Solo cuando está cobrado</button>
          </div>
        </div>
        <div class="field">
          <label class="req" for="vf">Vigente desde</label>
          <input id="vf" name="vf" type="date" class="input" [class.invalid]="e['validFrom']" [(ngModel)]="rf.validFrom" [disabled]="ruleLocked" />
          @if (e['validFrom']) { <span class="err">{{ e['validFrom'] }}</span> }
        </div>
        <div class="field">
          <label for="vt">Hasta (opcional)</label>
          <input id="vt" name="vt" type="date" class="input" [class.invalid]="e['validTo']" [(ngModel)]="rf.validTo" />
          @if (e['validTo']) { <span class="err">{{ e['validTo'] }}</span> }
        </div>
        <div class="field span-2">
          <label for="rn">Nota del acuerdo</label>
          <input id="rn" name="rn" class="input" [(ngModel)]="rf.notes" placeholder="Ej. acuerdo firmado el 02/01/2026" />
        </div>
        @if (ruleEditing) {
          <label class="check span-2"><input type="checkbox" name="ra" [(ngModel)]="rf.active" /> Regla activa</label>
        }
        @if (ruleLocked) { <p class="notice amber span-2 small">Esta regla ya se usó en una liquidación: solo puedes cerrar su vigencia o desactivarla. Para otro acuerdo, crea una regla nueva.</p> }
        @if (preview) { <p class="notice teal span-2 small">Ejemplo: {{ preview }}</p> }
      </form>
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="ruleOpen = false">Cancelar</button>
        <button class="btn primary" type="button" (click)="saveRule()" [disabled]="busy">Guardar regla</button>
      </ng-container>
    </app-modal>

    <app-modal [open]="!!st" [title]="st ? 'Liquidación N.° ' + st.settlement.id : ''"
               [subtitle]="st ? st.settlement.dentist.fullName + ' · ' + date(st.settlement.periodFrom) + ' – ' + date(st.settlement.periodTo) : ''" [width]="780" (close)="st = null">
      @if (st) {
        <div class="stack">
          <div class="row wrap">
            <span class="tag" [class]="'tag ' + sTone(st.settlement.status)">{{ sLabel(st.settlement.status) }}</span>
            <span class="muted small">Generada {{ dt(st.settlement.createdAt) }} por {{ st.settlement.createdBy }}</span>
          </div>
          <div class="sumrow">
            <div><span class="muted small">Total</span><strong class="tabular">{{ money(st.settlement.totalAmount) }}</strong></div>
            <div><span class="muted small">Pagado</span><strong class="tabular paid">{{ money(st.settlement.paidAmount) }}</strong></div>
            <div><span class="muted small">Pendiente</span><strong class="tabular" [class.owe]="st.settlement.pendingAmount > 0">{{ money(st.settlement.pendingAmount) }}</strong></div>
          </div>
          <div class="scroll">
            <table class="table">
              <thead><tr><th>Fecha</th><th>Paciente</th><th>Procedimiento</th><th>Regla</th><th class="right">Base</th><th class="right">Comisión</th></tr></thead>
              <tbody>
                @for (l of st.lines; track l.id) {
                  <tr><td class="tabular nowrap">{{ date(l.doneAt) }}</td><td>{{ l.patientName }}</td><td>{{ l.procedureName }}</td><td class="muted small">{{ l.ruleLabel }}</td>
                    <td class="right tabular">{{ money(l.baseAmount) }}</td><td class="right tabular strong">{{ money(l.commissionAmount) }}</td></tr>
                } @empty { <tr><td colspan="6" class="muted">Liquidación anulada: los procedimientos quedaron libres para liquidarse de nuevo.</td></tr> }
              </tbody>
            </table>
          </div>
          @if (st.payouts.length) {
            <div><div class="strong small">Pagos registrados</div>
              <ul class="mini">@for (p of st.payouts; track p.id) { <li><span>{{ date(p.paidOn) }} · {{ method(p.method) }}{{ p.reference ? ' · op. ' + p.reference : '' }} · {{ p.createdBy }}</span><span class="tabular strong">{{ money(p.amount) }}</span></li> }</ul>
            </div>
          }
          @if (st.settlement.notes) { <p class="muted small">{{ st.settlement.notes }}</p> }

          @if (st.settlement.status === 'PENDIENTE' || st.settlement.status === 'PARCIAL') {
            <form class="payform panel" (ngSubmit)="pay()">
              <div class="field"><label class="req" for="pa">Importe pagado (S/)</label>
                <input id="pa" name="pa" type="number" step="0.01" min="0" class="input tabular" [class.invalid]="pe['amount']" [(ngModel)]="pf.amount" />
                @if (pe['amount']) { <span class="err">{{ pe['amount'] }}</span> }</div>
              <div class="field"><label class="req" for="pd">Fecha de pago</label>
                <input id="pd" name="pd" type="date" class="input" [class.invalid]="pe['paidOn']" [(ngModel)]="pf.paidOn" [max]="today" />
                @if (pe['paidOn']) { <span class="err">{{ pe['paidOn'] }}</span> }</div>
              <div class="field"><label class="req" for="pm">Medio</label>
                <select id="pm" name="pm" class="input" [(ngModel)]="pf.method">@for (m of methods; track m) { <option [value]="m">{{ method(m) }}</option> }</select></div>
              <div class="field"><label for="pr">N.° de operación</label>
                <input id="pr" name="pr" class="input" [class.invalid]="pe['reference']" [(ngModel)]="pf.reference" />
                @if (pe['reference']) { <span class="err">{{ pe['reference'] }}</span> }</div>
              <button class="btn primary" type="submit" [disabled]="busy"><app-icon name="check" [size]="15" /> Registrar pago</button>
            </form>
          }
          @if (st.settlement.status === 'PENDIENTE') {
            @if (annulling) {
              <div class="field">
                <label class="req" for="ar">Motivo de la anulación</label>
                <input id="ar" class="input" [(ngModel)]="annulReason" />
                <div class="row" style="justify-content: flex-end; margin-top: 6px">
                  <button class="btn sm" type="button" (click)="annulling = false">Volver</button>
                  <button class="btn sm danger" type="button" (click)="annul()" [disabled]="annulReason.trim().length < 5 || busy">Anular liquidación</button>
                </div>
              </div>
            } @else {
              <button class="btn ghost sm danger-text" type="button" (click)="annulling = true">Anular esta liquidación</button>
            }
          }
        </div>
      }
      <ng-container modal-actions>
        @if (st) { <button class="btn" type="button" (click)="print()"><app-icon name="print" [size]="15" /> Imprimir</button> }
        <button class="btn" type="button" (click)="st = null">Cerrar</button>
      </ng-container>
    </app-modal>
  `,
  styles: [`
    .cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 14px; margin-bottom: 18px; }
    .dcard { display: flex; align-items: center; gap: 14px; padding: 16px 18px; position: relative; overflow: hidden; }
    .dcard .bar { position: absolute; left: 0; top: 0; bottom: 0; width: 5px; }
    .dot { width: 10px; height: 10px; border-radius: 50%; flex: none; }
    .owe { color: var(--gum); }
    .paid { color: var(--green); }
    tr.off td { opacity: .55; }
    .filters { display: flex; flex-wrap: wrap; gap: 12px; align-items: flex-end; padding: 14px 16px; margin-bottom: 16px; }
    .filters .field { margin: 0; min-width: 170px; }
    .filters .quick { margin-bottom: 4px; }
    .figures { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); margin-bottom: 18px; }
    .fig { display: flex; flex-direction: column; padding: 16px 20px; border-right: 1px solid var(--line); }
    .fig:last-child { border-right: 0; }
    .fig .v { font-size: var(--step-2); font-weight: 800; }
    .fig .l { font-weight: 700; color: var(--ink-2); font-size: var(--step--1); }
    .fig .meta { font-size: 12px; color: var(--muted); margin-top: 4px; }
    .two { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; margin-top: 18px; }
    .mini { list-style: none; margin: 0; padding: 6px 18px; font-size: 13px; }
    .mini li { display: flex; justify-content: space-between; gap: 10px; padding: 7px 0; border-bottom: 1px solid var(--line); }
    .mini li:last-child { border-bottom: 0; }
    .sel { width: 200px; height: 34px; font-size: 13px; }
    .nowrap { white-space: nowrap; }
    .sumrow { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
    .sumrow > div { display: flex; flex-direction: column; padding: 10px 14px; background: var(--surface-2); border-radius: 10px; }
    .sumrow strong { font-size: 20px; }
    .scroll { max-height: 280px; overflow: auto; border: 1px solid var(--line); border-radius: 10px; }
    .payform { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)) auto; gap: 10px; align-items: end; padding: 14px; background: var(--surface-2); }
    .payform .field { margin: 0; }
    .danger-text { color: var(--gum); align-self: flex-start; }
    @media (max-width: 900px) { .two, .figures { grid-template-columns: 1fr; } .payform { grid-template-columns: 1fr 1fr; } }
  `],
})
export class CommissionsComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  private router = inject(Router);

  @Input() tab: Tab = 'reglas';

  summary: any[] = [];
  rules: CommissionRule[] = [];
  dentists: Dentist[] = [];
  procedures: Procedure[] = [];

  ruleOpen = false;
  ruleEditing: CommissionRule | null = null;
  ruleLocked = false;
  rf: any = {};
  e: Record<string, string> = {};
  busy = false;

  calcDentist: number | null = null;
  calcFrom = periodPreset('mes-anterior').from;
  calcTo = periodPreset('mes-anterior').to;
  calc: any = null;
  calculating = false;
  settling = false;
  selected = new Set<number>();

  settlementsList: any[] = [];
  setStatus = '';
  settlementStates = ['PENDIENTE', 'PARCIAL', 'PAGADA', 'ANULADA'];
  st: any = null;
  pf: any = {};
  pe: Record<string, string> = {};
  annulling = false;
  annulReason = '';
  today = isoDate();
  methods = ['TRANSFERENCIA', 'EFECTIVO', 'YAPE', 'PLIN', 'TARJETA'];

  ngOnInit() {
    if (!['reglas', 'calcular', 'liquidaciones'].includes(this.tab)) this.tab = 'reglas';
    this.api.dentists().subscribe((d) => { this.dentists = d; if (!this.calcDentist && d.length) this.calcDentist = d[0].id; });
    this.api.procedures().subscribe((p) => (this.procedures = p));
    this.refresh();
  }

  refresh() {
    this.api.commissionSummary().subscribe((s) => (this.summary = s));
    this.api.commissionRules().subscribe((r) => (this.rules = r));
    this.loadSettlements();
  }

  loadSettlements() { this.api.settlements(undefined, this.setStatus || undefined).subscribe((s) => (this.settlementsList = s)); }

  go(t: Tab) {
    this.tab = t;
    this.router.navigate([], { queryParams: { tab: t === 'reglas' ? null : t }, replaceUrl: true });
  }

  isCurrent(r: CommissionRule) {
    const t = isoDate();
    return r.validFrom <= t && (!r.validTo || r.validTo >= t);
  }

  // ---------- reglas
  newRule() {
    this.ruleEditing = null;
    this.ruleLocked = false;
    this.rf = { dentistId: this.dentists[0]?.id ?? null, procedureId: null, calcType: 'PORCENTAJE', value: null, base: 'REALIZADO',
      validFrom: isoDate(), validTo: null, active: true, notes: '' };
    this.e = {};
    this.ruleOpen = true;
  }

  editRule(r: CommissionRule) {
    this.ruleEditing = r;
    this.ruleLocked = false;
    this.rf = { dentistId: r.dentist.id, procedureId: r.procedure?.id ?? null, calcType: r.calcType, value: r.value, base: r.base,
      validFrom: r.validFrom, validTo: r.validTo || null, active: r.active, notes: r.notes || '' };
    this.e = {};
    this.ruleOpen = true;
    // Si ya se usó en una liquidación, solo se puede cerrar su vigencia (lo valida el servidor).
    this.api.settlements(r.dentist.id).subscribe((list) => {
      const ids = list.filter((s: any) => s.status !== 'ANULADA').map((s: any) => s.id);
      if (!ids.length) return;
      ids.forEach((id: number) => this.api.settlement(id).subscribe((d) => {
        if (d.lines.some((l: any) => l.ruleId === r.id)) this.ruleLocked = true;
      }));
    });
  }

  get preview(): string {
    const v = Number(this.rf.value);
    if (!v) return '';
    const p = this.procedures.find((x) => x.id === this.rf.procedureId);
    const price = p ? Number(p.price) : 200;
    const c = this.rf.calcType === 'PORCENTAJE' ? (price * v) / 100 : v;
    return `${p ? p.name : 'un procedimiento de ' + soles(price)} → comisión ${soles(c)}${this.rf.base === 'COBRADO' ? ', cuando el paciente lo haya pagado por completo' : ''}.`;
  }

  saveRule() {
    this.busy = true;
    this.api.saveCommissionRule(this.ruleEditing?.id ?? null, this.rf).subscribe({
      next: () => { this.busy = false; this.ruleOpen = false; this.toast.ok('Regla guardada.'); this.refresh(); },
      error: (err) => { this.busy = false; const a = apiError(err); this.e = a.fields || {}; this.toast.error(a.message); },
    });
  }

  // ---------- cálculo
  month(k: string) {
    const p = periodPreset(k);
    this.calcFrom = p.from;
    this.calcTo = p.to;
  }

  calculate() {
    if (!this.calcDentist) return;
    this.calculating = true;
    this.api.calculateCommissions(this.calcDentist, this.calcFrom, this.calcTo).subscribe({
      next: (c) => {
        this.calculating = false;
        this.calc = c;
        this.selected = new Set(c.lines.map((l: any) => l.treatmentItemId));
      },
      error: (err) => { this.calculating = false; this.toast.error(apiError(err).message); },
    });
  }

  toggle(id: number) { if (this.selected.has(id)) this.selected.delete(id); else this.selected.add(id); }
  toggleAll() {
    if (!this.calc) return;
    this.selected = this.selected.size === this.calc.lines.length ? new Set() : new Set(this.calc.lines.map((l: any) => l.treatmentItemId));
  }
  get selectedTotal(): number {
    return (this.calc?.lines || []).filter((l: any) => this.selected.has(l.treatmentItemId)).reduce((s: number, l: any) => s + Number(l.commission), 0);
  }

  settle() {
    if (!this.calc || !this.selected.size) return;
    this.settling = true;
    this.api.settle({ dentistId: this.calc.dentist.id, from: this.calcFrom, to: this.calcTo, itemIds: [...this.selected] }).subscribe({
      next: (d) => {
        this.settling = false;
        this.toast.ok(`Liquidación N.° ${d.settlement.id} generada por ${soles(d.settlement.totalAmount)}.`);
        this.refresh();
        this.calculate();
        this.showSettlement(d);
      },
      error: (err) => { this.settling = false; this.toast.error(apiError(err).message); },
    });
  }

  // ---------- liquidaciones
  openSettlement(id: number) { this.api.settlement(id).subscribe((d) => this.showSettlement(d)); }

  private showSettlement(d: any) {
    this.st = d;
    this.pf = { amount: d.settlement.pendingAmount, paidOn: isoDate(), method: 'TRANSFERENCIA', reference: '' };
    this.pe = {};
    this.annulling = false;
    this.annulReason = '';
  }

  pay() {
    this.busy = true;
    this.api.paySettlement(this.st.settlement.id, this.pf).subscribe({
      next: (d) => { this.busy = false; this.toast.ok('Pago registrado.'); this.showSettlement(d); this.refresh(); },
      error: (err) => { this.busy = false; const a = apiError(err); this.pe = a.fields || {}; this.toast.error(a.message); },
    });
  }

  annul() {
    this.busy = true;
    this.api.annulSettlement(this.st.settlement.id, this.annulReason).subscribe({
      next: (d) => { this.busy = false; this.toast.ok('Liquidación anulada.'); this.showSettlement(d); this.refresh(); },
      error: (err) => { this.busy = false; this.toast.error(apiError(err).message); },
    });
  }

  print() { window.print(); }
  money = soles;
  date = shortDate;
  dt = dateTime;
  method(m: string) { return METHOD_LABEL[m] || m; }
  sLabel(s: string) { return SETTLEMENT_LABEL[s] || s; }
  sTone(s: string) { return SETTLEMENT_TONE[s] || 'grey'; }
}
