import { Component, Input, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Account, Api, Obligation, Payment } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ToastService } from '../core/toast.service';
import { Patient } from '../core/models';
import { ITEM_LABEL, ITEM_TONE, METHOD_LABEL, apiError, dateTime, shortDate, soles } from '../core/format';
import { IconComponent } from '../shared/icon.component';
import { LogoComponent } from '../shared/logo.component';
import { PatientPickerComponent } from '../shared/patient-picker.component';

/** HU-13: registrar un pago y su medio de pago, eligiendo la obligación que se paga. */
@Component({
  selector: 'app-payment-new',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent, LogoComponent, PatientPickerComponent],
  template: `
    <div class="page">
      <a class="back no-print" routerLink="/caja"><app-icon name="chevronLeft" [size]="16" /> Caja del día</a>

      @if (done) {
        <section class="panel receipt">
          <header class="rh">
            <div class="row"><app-logo [size]="46" /><div><div class="strong big">Clínica Dental Cárdenas</div><div class="muted small">Urb. La Floresta, Chaclacayo · Recibo interno de caja</div></div></div>
            <div class="right"><div class="muted small">Recibo</div><div class="strong tabular">N.° {{ pad(done.id) }}</div></div>
          </header>
          <dl class="kv">
            <dt>Paciente</dt><dd>{{ patient?.fullName }} · {{ patient?.docType }} {{ patient?.docNumber }}</dd>
            <dt>Concepto</dt><dd>{{ doneConcept }}</dd>
            <dt>Medio de pago</dt><dd>{{ method(done.method) }}{{ done.reference ? ' · operación ' + done.reference : '' }}</dd>
            <dt>Fecha</dt><dd>{{ dt(done.paidAt) }} · cobrado por {{ done.cashier }}</dd>
          </dl>
          <div class="amount"><span>Importe recibido</span><strong class="tabular">{{ money(done.amount) }}</strong></div>
          <div class="after muted">Saldo pendiente del paciente después del pago: <strong class="tabular">{{ money(acc?.balance) }}</strong>
            @if (acc && acc.inFavor > 0) { · saldo a favor {{ money(acc.inFavor) }} }</div>
          <p class="muted small">Este recibo es un control interno de caja. El comprobante electrónico (boleta o factura) se emite en Comprobantes.</p>
          <div class="row wrap no-print">
            <button class="btn" type="button" (click)="print()"><app-icon name="print" [size]="16" /> Imprimir recibo</button>
            @if (auth.can('COMPROBANTES')) {
              <a class="btn" routerLink="/comprobantes/nuevo" [queryParams]="{ paciente: patient?.id }"><app-icon name="receipt" [size]="16" /> Emitir comprobante</a>
            }
            <button class="btn primary" type="button" (click)="again()"><app-icon name="plus" [size]="16" /> Registrar otro cobro</button>
          </div>
        </section>
      } @else {
        <div class="page-head"><div><h1>Registrar cobro</h1><p class="sub">Elige al paciente, lo que paga y cómo lo paga.</p></div></div>

        <div class="layout">
          <div class="stack">
            <section class="panel">
              <div class="panel-head"><h3>1. Paciente</h3></div>
              <div class="panel-body"><app-patient-picker [value]="patient" (valueChange)="onPatient($event)" /></div>
            </section>

            @if (patient) {
              <section class="panel">
                <div class="panel-head">
                  <h3>2. ¿Qué paga?</h3>
                  @if (acc) { <span class="muted small">Saldo pendiente {{ money(acc.balance) }}</span> }
                </div>
                @if (!acc) {
                  <div class="skeleton" style="height: 160px; margin: 16px"></div>
                } @else {
                  <table class="table">
                    <thead><tr><th></th><th>Procedimiento</th><th>Estado</th><th class="right">Precio</th><th class="right">Pagado</th><th class="right">Pendiente</th></tr></thead>
                    <tbody>
                      @for (o of payable; track o.treatmentItemId) {
                        <tr class="click" [class.sel]="sel === o.treatmentItemId" (click)="choose(o)">
                          <td><input type="radio" name="ob" [checked]="sel === o.treatmentItemId" (change)="choose(o)" [attr.aria-label]="o.procedure" /></td>
                          <td><div class="strong">{{ o.procedure }}{{ o.tooth ? ' · pieza ' + o.tooth : '' }}</div><div class="muted small">{{ o.planTitle }}{{ o.doneAt ? ' · ' + date(o.doneAt) : '' }}</div></td>
                          <td><span class="tag" [class]="'tag ' + itemTone(o.status)">{{ itemLabel(o.status) }}</span></td>
                          <td class="right tabular">{{ money(o.price) }}</td>
                          <td class="right tabular muted">{{ money(o.paid) }}</td>
                          <td class="right tabular strong">{{ money(o.pending) }}</td>
                        </tr>
                      }
                      <tr class="click" [class.sel]="sel === 0" (click)="choose(null)">
                        <td><input type="radio" name="ob" [checked]="sel === 0" (change)="choose(null)" aria-label="Pago a cuenta" /></td>
                        <td colspan="5"><div class="strong">Pago a cuenta</div><div class="muted small">Adelanto o abono general que se descuenta del saldo del paciente</div></td>
                      </tr>
                    </tbody>
                  </table>
                  @if (payable.length === 0) { <div class="panel-foot small muted">El paciente no tiene procedimientos con saldo pendiente.</div> }
                }
              </section>
            }
          </div>

          <aside class="panel side">
            <div class="panel-head"><h3>3. Pago</h3></div>
            <form class="panel-body stack" (ngSubmit)="submit()">
              <div class="field">
                <label class="req" for="am">Importe (S/)</label>
                <input id="am" name="am" type="number" min="0" step="0.01" class="input tabular big" [class.invalid]="e['amount']" [(ngModel)]="f.amount" [disabled]="!patient" />
                @if (e['amount']) { <span class="err">{{ e['amount'] }}</span> }
                @else if (selected) { <span class="hint">Pendiente de este procedimiento: {{ money(selected.pending) }}</span> }
              </div>
              <div class="field">
                <span class="label req">Medio de pago</span>
                <div class="methods">
                  @for (mt of methods; track mt) {
                    <button type="button" class="mt" [class.on]="f.method === mt" (click)="f.method = mt" [disabled]="!patient">{{ method(mt) }}</button>
                  }
                </div>
                @if (e['method']) { <span class="err">{{ e['method'] }}</span> }
              </div>
              @if (f.method !== 'EFECTIVO') {
                <div class="field">
                  <label class="req" for="rf">{{ f.method === 'TARJETA' ? 'N.° de voucher' : 'N.° de operación' }}</label>
                  <input id="rf" name="rf" class="input tabular" [class.invalid]="e['reference']" [(ngModel)]="f.reference" [disabled]="!patient" />
                  @if (e['reference']) { <span class="err">{{ e['reference'] }}</span> }
                </div>
              } @else if (f.amount > 0) {
                <div class="field">
                  <label for="rc">Recibe (para calcular el vuelto)</label>
                  <input id="rc" name="rc" type="number" min="0" step="0.10" class="input tabular" [(ngModel)]="received" />
                  @if (received && received >= f.amount) { <span class="hint ok">Vuelto: {{ money(received - f.amount) }}</span> }
                </div>
              }
              <div class="field">
                <label for="nt">Nota (opcional)</label>
                <input id="nt" name="nt" class="input" [(ngModel)]="f.notes" [disabled]="!patient" />
              </div>
              <button class="btn primary lg block" type="submit" [disabled]="!patient || saving || sel === null">
                @if (saving) { <span class="spin"></span> Registrando… } @else { <app-icon name="check" [size]="16" /> Registrar cobro {{ f.amount > 0 ? money(f.amount) : '' }} }
              </button>
              @if (patient && sel === null) { <p class="muted small">Elige qué paga el paciente.</p> }
            </form>
          </aside>
        </div>
      }
    </div>
  `,
  styles: [`
    .layout { display: grid; grid-template-columns: minmax(0, 1.6fr) minmax(300px, 1fr); gap: 18px; align-items: start; }
    .side { position: sticky; top: 18px; }
    tr.sel td { background: var(--brand-soft); }
    .big { font-size: 22px; font-weight: 800; height: 50px; }
    .methods { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; }
    .mt { height: 40px; border: 1px solid var(--line-strong); border-radius: 10px; background: var(--surface); font: inherit; font-weight: 700; font-size: 13px; cursor: pointer; color: var(--ink-2); }
    .mt.on { background: var(--teal); border-color: var(--teal); color: #fff; }
    .mt:disabled { opacity: .5; cursor: not-allowed; }
    .hint.ok { color: var(--green); font-weight: 700; }
    .receipt { max-width: 640px; padding: 28px 30px; display: flex; flex-direction: column; gap: 16px; border-top: 4px solid var(--brand); }
    .rh { display: flex; justify-content: space-between; gap: 16px; padding-bottom: 14px; border-bottom: 1px solid var(--line); }
    .rh .big { font-size: 18px; font-family: var(--font-display); }
    .amount { display: flex; justify-content: space-between; align-items: baseline; padding: 14px 18px; background: var(--green-soft); border-radius: 12px; }
    .amount strong { font-size: 28px; color: var(--green); }
    .kv { grid-template-columns: 130px 1fr; }
    @media (max-width: 1000px) { .layout { grid-template-columns: 1fr; } .side { position: static; } }
    @media print { .receipt { border: 0; box-shadow: none; } }
  `],
})
export class PaymentNewComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  auth = inject(AuthService);

  @Input() paciente?: string;
  @Input() item?: string;

  patient: Patient | null = null;
  acc: Account | null = null;
  sel: number | null = null; // 0 = a cuenta
  f: any = { amount: null, method: 'EFECTIVO', reference: '', notes: '' };
  received: number | null = null;
  e: Record<string, string> = {};
  saving = false;
  done: Payment | null = null;
  doneConcept = '';
  methods = ['EFECTIVO', 'YAPE', 'PLIN', 'TARJETA', 'TRANSFERENCIA'];

  ngOnInit() {
    if (this.paciente) this.api.patient(+this.paciente).subscribe((r) => this.onPatient(r.patient));
  }

  get payable(): Obligation[] { return (this.acc?.obligations || []).filter((o) => o.pending > 0); }
  get selected(): Obligation | undefined { return this.payable.find((o) => o.treatmentItemId === this.sel); }

  onPatient(p: Patient | null) {
    this.patient = p;
    this.acc = null;
    this.sel = null;
    this.e = {};
    if (!p) return;
    this.api.account(p.id).subscribe((a) => {
      this.acc = a;
      const pre = this.item ? this.payable.find((o) => o.treatmentItemId === +this.item!) : this.payable.find((o) => o.status === 'REALIZADO');
      if (pre) this.choose(pre);
    });
  }

  choose(o: Obligation | null) {
    this.sel = o ? o.treatmentItemId : 0;
    this.f.amount = o ? o.pending : (this.acc && this.acc.balance > 0 ? this.acc.balance : null);
    this.e = {};
  }

  submit() {
    if (!this.patient || this.sel === null) return;
    this.saving = true;
    this.e = {};
    const body = { patientId: this.patient.id, treatmentItemId: this.sel || null, amount: this.f.amount, method: this.f.method,
      reference: this.f.method === 'EFECTIVO' ? null : this.f.reference, notes: this.f.notes };
    this.api.pay(body).subscribe({
      next: (r) => {
        this.saving = false;
        this.done = r.payment;
        this.doneConcept = r.concept;
        this.acc = r.account;
        this.toast.ok(`Cobro de ${soles(r.payment.amount)} registrado.`);
      },
      error: (err) => { this.saving = false; const a = apiError(err); this.e = a.fields || {}; this.toast.error(a.message); },
    });
  }

  again() {
    this.done = null;
    this.f = { amount: null, method: 'EFECTIVO', reference: '', notes: '' };
    this.received = null;
    if (this.patient) this.onPatient(this.patient);
  }

  print() { window.print(); }
  pad(n: number) { return String(n).padStart(6, '0'); }
  money = soles;
  dt = dateTime;
  date = shortDate;
  method(m: string) { return METHOD_LABEL[m] || m; }
  itemLabel(s: string) { return ITEM_LABEL[s] || s; }
  itemTone(s: string) { return ITEM_TONE[s] || 'grey'; }
}
