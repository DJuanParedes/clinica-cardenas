import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api, CashReport } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ToastService } from '../core/toast.service';
import { CATEGORY_LABEL, METHOD_LABEL, apiError, hhmm, isoDate, longDay, soles } from '../core/format';
import { IconComponent } from '../shared/icon.component';
import { ModalComponent } from '../shared/modal.component';

/** Caja del día: cobros (HU-13) y egresos (HU-15) del turno. */
@Component({
  selector: 'app-cash',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent, ModalComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <p class="muted">{{ today }}</p>
          <h1>Caja del día</h1>
        </div>
        <div class="actions">
          @if (auth.can('CAJA_REPORTE')) { <a class="btn" routerLink="/caja/reporte"><app-icon name="chart" [size]="16" /> Flujo por período</a> }
          @if (auth.can('CAJA_EGRESOS')) { <button class="btn" type="button" (click)="openExpense()"><app-icon name="arrowDown" [size]="16" /> Registrar egreso</button> }
          @if (auth.can('CAJA_COBRAR')) { <a class="btn primary" routerLink="/caja/cobro"><app-icon name="wallet" [size]="16" /> Registrar cobro</a> }
        </div>
      </div>

      @if (!r) {
        <div class="skeleton" style="height: 96px; margin-bottom: 16px"></div>
        <div class="skeleton" style="height: 320px"></div>
      } @else {
        <section class="figures panel" aria-label="Resumen de caja de hoy">
          <div class="fig"><span class="v tabular in">{{ money(r.income) }}</span><span class="l">cobrado hoy</span><span class="meta">{{ r.incomeCount }} cobros</span></div>
          <div class="fig"><span class="v tabular out">{{ money(r.outgo) }}</span><span class="l">egresos de hoy</span><span class="meta">{{ r.outgoCount }} registros</span></div>
          <div class="fig"><span class="v tabular">{{ money(r.net) }}</span><span class="l">saldo del día</span><span class="meta">ingresos menos egresos</span></div>
          <div class="fig">
            <span class="v tabular">{{ money(cashInHand) }}</span><span class="l">efectivo en caja</span>
            <span class="meta">cobros en efectivo menos egresos en efectivo</span>
          </div>
        </section>

        <div class="board">
          <section class="panel">
            <div class="panel-head"><h3>Movimientos de hoy</h3><span class="muted small">{{ r.detail.length }}</span></div>
            @if (r.detail.length === 0) {
              <div class="empty"><h3>Aún no hay movimientos hoy</h3><p>Los cobros y egresos aparecerán aquí al registrarse.</p>
                @if (auth.can('CAJA_COBRAR')) { <a class="btn primary" routerLink="/caja/cobro">Registrar el primer cobro</a> }</div>
            } @else {
              <table class="table">
                <thead><tr><th>Hora</th><th>Concepto</th><th>Medio</th><th class="right">Importe</th><th></th></tr></thead>
                <tbody>
                  @for (d of r.detail; track d.type + d.id) {
                    <tr>
                      <td class="tabular muted">{{ d.type === 'INGRESO' ? hhmm(d.date) : '—' }}</td>
                      <td>
                        <div class="strong">{{ d.concept }}</div>
                        <div class="muted small">
                          @if (d.type === 'INGRESO') { <a [routerLink]="['/pacientes', d.patientId]" [queryParams]="{ tab: 'cuenta' }">{{ d.party }}</a> }
                          @else { {{ cat(d.category) }}{{ d.party ? ' · ' + d.party : '' }} }
                          · {{ d.user }}
                        </div>
                      </td>
                      <td>{{ method(d.method) }}@if (d.reference) { <div class="muted small">op. {{ d.reference }}</div> }</td>
                      <td class="right tabular strong" [class.in]="d.type === 'INGRESO'" [class.out]="d.type === 'EGRESO'">
                        {{ d.type === 'EGRESO' ? '−' : '+' }} {{ money(d.amount) }}
                      </td>
                      <td class="right">
                        @if ((d.type === 'INGRESO' && auth.can('CAJA_COBRAR')) || (d.type === 'EGRESO' && auth.can('CAJA_EGRESOS'))) {
                          <button class="btn sm ghost" type="button" (click)="askVoid(d)" title="Anular"><app-icon name="x" [size]="14" /></button>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          </section>

          <section class="panel">
            <div class="panel-head"><h3>Por medio de pago</h3></div>
            <div class="panel-body">
              @if (r.byMethod.length === 0) { <p class="muted small">Sin movimientos.</p> }
              <ul class="methods">
                @for (mm of r.byMethod; track mm.method) {
                  <li>
                    <span class="strong">{{ mm.label }}</span>
                    <span class="tabular in">+ {{ money(mm.income) }}</span>
                    @if (mm.outgo > 0) { <span class="tabular out">− {{ money(mm.outgo) }}</span> }
                  </li>
                }
              </ul>
              @if (r.voided) { <p class="muted small">{{ r.voided }} movimiento(s) anulado(s) no se suman.</p> }
            </div>
          </section>
        </div>
      }
    </div>

    <app-modal [open]="expenseOpen" title="Registrar egreso" subtitle="Pagos a proveedores, servicios, insumos u otros gastos" [width]="600" (close)="expenseOpen = false">
      <form class="grid cols-2" (ngSubmit)="saveExpense()">
        <div class="field">
          <label class="req" for="ed">Fecha</label>
          <input id="ed" name="ed" type="date" class="input" [class.invalid]="e['expenseDate']" [(ngModel)]="x.expenseDate" [max]="todayIso" />
          @if (e['expenseDate']) { <span class="err">{{ e['expenseDate'] }}</span> }
        </div>
        <div class="field">
          <label class="req" for="ec">Categoría</label>
          <select id="ec" name="ec" class="input" [class.invalid]="e['category']" [(ngModel)]="x.category">
            <option value="">Selecciona…</option>
            @for (c of categories; track c) { <option [value]="c">{{ cat(c) }}</option> }
          </select>
          @if (e['category']) { <span class="err">{{ e['category'] }}</span> }
        </div>
        <div class="field span-2">
          <label class="req" for="eco">Concepto</label>
          <input id="eco" name="eco" class="input" [class.invalid]="e['concept']" [(ngModel)]="x.concept" placeholder="Ej. compra de anestésicos y guantes" />
          @if (e['concept']) { <span class="err">{{ e['concept'] }}</span> }
        </div>
        <div class="field">
          <label class="req" for="ea">Importe (S/)</label>
          <input id="ea" name="ea" type="number" min="0" step="0.01" class="input tabular" [class.invalid]="e['amount']" [(ngModel)]="x.amount" />
          @if (e['amount']) { <span class="err">{{ e['amount'] }}</span> }
        </div>
        <div class="field">
          <label class="req" for="em">Pagado con</label>
          <select id="em" name="em" class="input" [class.invalid]="e['method']" [(ngModel)]="x.method">
            @for (mt of methods; track mt) { <option [value]="mt">{{ method(mt) }}</option> }
          </select>
        </div>
        <div class="field">
          <label for="es">Proveedor</label>
          <input id="es" name="es" class="input" [(ngModel)]="x.supplier" />
        </div>
        <div class="field">
          <label for="er">N.° de comprobante u operación</label>
          <input id="er" name="er" class="input" [(ngModel)]="x.reference" />
        </div>
      </form>
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="expenseOpen = false">Cancelar</button>
        <button class="btn primary" type="button" (click)="saveExpense()" [disabled]="busy">Registrar egreso</button>
      </ng-container>
    </app-modal>

    <app-modal [open]="!!voiding" title="Anular movimiento" [subtitle]="voiding ? voiding.concept + ' · ' + money(voiding.amount) : ''" (close)="voiding = null">
      <div class="field">
        <label class="req" for="vr">Motivo de la anulación</label>
        <input id="vr" class="input" [(ngModel)]="voidReason" placeholder="Ej. se registró dos veces" />
        <span class="hint">El movimiento queda en el historial como anulado y no se suma en caja.</span>
      </div>
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="voiding = null">Volver</button>
        <button class="btn danger" type="button" (click)="confirmVoid()" [disabled]="voidReason.trim().length < 5 || busy">Anular</button>
      </ng-container>
    </app-modal>
  `,
  styles: [`
    .figures { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin-bottom: 18px; }
    .fig { display: flex; flex-direction: column; padding: 18px 22px; border-right: 1px solid var(--line); }
    .fig:last-child { border-right: 0; }
    .fig .v { font-size: var(--step-2); font-weight: 800; letter-spacing: -.02em; line-height: 1.15; }
    .fig .l { font-weight: 700; color: var(--ink-2); font-size: var(--step--1); margin-top: 2px; }
    .fig .meta { font-size: 12px; color: var(--muted); margin-top: 6px; }
    .in { color: var(--green); }
    .out { color: var(--gum); }
    .board { display: grid; grid-template-columns: minmax(0, 1.8fr) minmax(0, 1fr); gap: 18px; align-items: start; }
    .methods { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 10px; }
    .methods li { display: flex; gap: 10px; justify-content: space-between; padding-bottom: 10px; border-bottom: 1px solid var(--line); }
    .methods li:last-child { border-bottom: 0; }
    @media (max-width: 1100px) { .board { grid-template-columns: 1fr; } }
    @media (max-width: 720px) { .figures { grid-template-columns: 1fr 1fr; } .fig:nth-child(2) { border-right: 0; } .fig:nth-child(-n+2) { border-bottom: 1px solid var(--line); } }
  `],
})
export class CashComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  auth = inject(AuthService);

  r: CashReport | null = null;
  today = longDay(isoDate());
  todayIso = isoDate();
  methods = ['EFECTIVO', 'YAPE', 'PLIN', 'TARJETA', 'TRANSFERENCIA'];
  categories = ['INSUMOS', 'LABORATORIO', 'ALQUILER', 'SERVICIOS', 'PLANILLA', 'MANTENIMIENTO', 'MARKETING', 'IMPUESTOS', 'OTROS'];

  expenseOpen = false;
  x: any = {};
  e: Record<string, string> = {};
  busy = false;
  voiding: any = null;
  voidReason = '';

  ngOnInit() { this.load(); }

  load() { this.api.cashToday().subscribe((r) => (this.r = r)); }

  get cashInHand(): number {
    const c = this.r?.byMethod.find((m) => m.method === 'EFECTIVO');
    return c ? c.net : 0;
  }

  openExpense() {
    this.x = { expenseDate: isoDate(), category: '', concept: '', amount: null, method: 'EFECTIVO', supplier: '', reference: '' };
    this.e = {};
    this.expenseOpen = true;
  }

  saveExpense() {
    this.busy = true;
    this.api.addExpense(this.x).subscribe({
      next: () => { this.busy = false; this.expenseOpen = false; this.toast.ok('Egreso registrado.'); this.load(); },
      error: (err) => { this.busy = false; const a = apiError(err); this.e = a.fields || {}; this.toast.error(a.message); },
    });
  }

  askVoid(d: any) { this.voiding = d; this.voidReason = ''; }

  confirmVoid() {
    const d = this.voiding;
    this.busy = true;
    const call = d.type === 'INGRESO' ? this.api.voidPayment(d.id, this.voidReason) : this.api.voidExpense(d.id, this.voidReason);
    call.subscribe({
      next: () => { this.busy = false; this.voiding = null; this.toast.ok('Movimiento anulado.'); this.load(); },
      error: (err) => { this.busy = false; this.toast.error(apiError(err).message); },
    });
  }

  money = soles;
  hhmm = hhmm;
  method(m: string) { return METHOD_LABEL[m] || m; }
  cat(c?: string) { return c ? CATEGORY_LABEL[c] || c : ''; }
}
