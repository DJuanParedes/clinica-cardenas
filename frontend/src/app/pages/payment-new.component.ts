import { Component, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Account, Api, Obligation, OnlineCharge, OnlineConfig, Payment, PaymentInvoice } from '../core/api.service';
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
          @if (invoice) {
            <div class="inv-ok"><app-icon name="receipt" [size]="18" /> <span>Boleta electrónica <strong>{{ invoice.invoice }}</strong> emitida
              automáticamente{{ invoice.status === 'ACEPTADO' ? ' y aceptada por SUNAT' : (invoice.status === 'GENERADO' ? ' (pendiente de envío a SUNAT)' : ' · ' + invoice.status) }}.</span></div>
          } @else if (invoicing) {
            <p class="muted small"><span class="spin"></span> Emitiendo la boleta electrónica…</p>
          } @else {
            @if (invoiceError) { <p class="err small">{{ invoiceError }}</p> }
            <p class="muted small">Este recibo es un control interno de caja. El comprobante electrónico (boleta o factura) se emite en Comprobantes.</p>
          }
          <div class="row wrap no-print">
            <button class="btn" type="button" (click)="print()"><app-icon name="print" [size]="16" /> Imprimir recibo</button>
            @if (auth.can('COMPROBANTES')) {
              @if (invoice) {
                <a class="btn" [routerLink]="['/comprobantes', invoice.invoiceId]"><app-icon name="receipt" [size]="16" /> Ver boleta {{ invoice.invoice }}</a>
              } @else if (done.id && !invoicing) {
                <button class="btn" type="button" (click)="issueInvoice()"><app-icon name="receipt" [size]="16" /> Emitir boleta ahora</button>
              }
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
              @if (online && (f.method === 'TARJETA' || f.method === 'YAPE')) {
                <div class="field">
                  <span class="label">¿Cómo cobras?</span>
                  <div class="modes">
                    <button type="button" class="mt" [class.on]="mode === 'ONLINE'" (click)="mode = 'ONLINE'">Link de pago Mercado Pago</button>
                    <button type="button" class="mt" [class.on]="mode === 'MANUAL'" (click)="mode = 'MANUAL'">Ya cobré (POS o Yape)</button>
                  </div>
                  @if (mode === 'ONLINE') {
                    <span class="hint">El paciente paga con {{ f.method === 'YAPE' ? 'Yape' : 'su tarjeta' }} en la página segura de Mercado Pago (también puede elegir tarjeta, Yape o su cuenta). Cuando se apruebe, el cobro se registra solo.
                      @if (online.testMode) { <strong> Modo de pruebas: no se cobra dinero real.</strong> }</span>
                  }
                </div>
              }
              @if (charge) {
                <div class="charge" [class.ok]="charge.status === 'APROBADO'" [class.bad]="charge.status === 'CANCELADO' || charge.status === 'VENCIDO'">
                  <div class="row between"><strong>{{ money(charge.amount) }} · {{ charge.statusLabel }}</strong>
                    @if (charge.status === 'PENDIENTE') { <span class="spin dark"></span> }</div>
                  <div class="muted small">{{ charge.reference }} · vence {{ dt(charge.expiresAt) }}</div>
                  @if (charge.status === 'PENDIENTE' && charge.checkoutUrl) {
                    <input class="input small" readonly [value]="charge.checkoutUrl" (focus)="$any($event.target).select()" aria-label="Link de pago" />
                    <div class="row wrap">
                      <a class="btn sm primary" [href]="charge.checkoutUrl" target="_blank" rel="noopener"><app-icon name="external" [size]="14" /> Abrir página de pago</a>
                      <button class="btn sm" type="button" (click)="copyLink()">Copiar link</button>
                      <button class="btn sm" type="button" (click)="sendLink()" [disabled]="sending">{{ sending ? 'Enviando…' : 'Enviar por WhatsApp' }}</button>
                      <button class="btn sm ghost" type="button" (click)="cancelCharge()">Anular</button>
                    </div>
                    @if (charge.mpStatus === 'rejected') { <span class="err">Mercado Pago rechazó el último intento ({{ charge.mpStatusDetail }}). El paciente puede reintentar con el mismo link.</span> }
                  }
                </div>
              }
              @if (f.method !== 'EFECTIVO' && !(online && (f.method === 'TARJETA' || f.method === 'YAPE') && mode === 'ONLINE')) {
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
              @if (auth.can('COMPROBANTES')) {
                <label class="auto-inv"><input type="checkbox" name="ai" [(ngModel)]="autoInvoice" />
                  Emitir la boleta electrónica automáticamente{{ isOnline ? ' cuando se apruebe el pago' : '' }}</label>
              }
              @if (isOnline) {
                <button class="btn primary lg block" type="submit" [disabled]="!patient || saving || sel === null || charge?.status === 'PENDIENTE'">
                  @if (saving) { <span class="spin"></span> Generando… } @else { <app-icon name="link" [size]="16" /> Generar link de pago {{ f.amount > 0 ? money(f.amount) : '' }} }
                </button>
              } @else {
                <button class="btn primary lg block" type="submit" [disabled]="!patient || saving || sel === null">
                  @if (saving) { <span class="spin"></span> Registrando… } @else { <app-icon name="check" [size]="16" /> Registrar cobro {{ f.amount > 0 ? money(f.amount) : '' }} }
                </button>
              }
              @if (f.method === 'PLIN' || f.method === 'TRANSFERENCIA') { <p class="muted small">{{ f.method === 'PLIN' ? 'Plin' : 'La transferencia' }} no tiene cobro en línea: verifica el abono en la app del banco y anota el número de operación.</p> }
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
    .modes { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
    .modes .mt { height: auto; min-height: 40px; padding: 6px 8px; line-height: 1.2; }
    .charge { display: flex; flex-direction: column; gap: 8px; padding: 12px 14px; border-radius: 12px; border: 1px solid var(--line-strong); background: var(--brand-soft); }
    .charge.ok { background: var(--green-soft); border-color: var(--green); }
    .charge.bad { background: var(--surface-2, #f4f6f8); }
    .charge .between { justify-content: space-between; }
    .spin.dark { border-color: rgba(0,0,0,.15); border-top-color: var(--teal); }
    .mt.on { background: var(--teal); border-color: var(--teal); color: #fff; }
    .mt:disabled { opacity: .5; cursor: not-allowed; }
    .hint.ok { color: var(--green); font-weight: 700; }
    .receipt { max-width: 640px; padding: 28px 30px; display: flex; flex-direction: column; gap: 16px; border-top: 4px solid var(--brand); }
    .rh { display: flex; justify-content: space-between; gap: 16px; padding-bottom: 14px; border-bottom: 1px solid var(--line); }
    .rh .big { font-size: 18px; font-family: var(--font-display); }
    .auto-inv { display: flex; gap: 8px; align-items: center; font-size: 14px; margin: 4px 0 12px; cursor: pointer; }
    .inv-ok { display: flex; gap: 8px; align-items: center; padding: 10px 14px; border-radius: 10px; background: var(--green-soft); margin: 10px 0; font-size: 14px; }
    .err { color: var(--red, #b42318); }
    .amount { display: flex; justify-content: space-between; align-items: baseline; padding: 14px 18px; background: var(--green-soft); border-radius: 12px; }
    .amount strong { font-size: 28px; color: var(--green); }
    .kv { grid-template-columns: 130px 1fr; }
    @media (max-width: 1000px) { .layout { grid-template-columns: 1fr; } .side { position: static; } }
    @media print { .receipt { border: 0; box-shadow: none; } }
  `],
})
export class PaymentNewComponent implements OnInit, OnDestroy {
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
  autoInvoice = true;
  invoice: PaymentInvoice | null = null;
  invoicing = false;
  invoiceError = '';
  private invoiceTries = 0;
  methods = ['EFECTIVO', 'YAPE', 'PLIN', 'TARJETA', 'TRANSFERENCIA'];
  online: OnlineConfig | null = null;
  mode: 'ONLINE' | 'MANUAL' = 'ONLINE';
  charge: OnlineCharge | null = null;
  sending = false;
  private poll: ReturnType<typeof setInterval> | null = null;

  ngOnInit() {
    if (this.paciente) this.api.patient(+this.paciente).subscribe((r) => this.onPatient(r.patient));
    this.api.onlineConfig().subscribe({ next: (c) => (this.online = c.enabled ? c : null), error: () => (this.online = null) });
  }

  ngOnDestroy() { this.stopPoll(); }

  get isOnline() { return !!this.online && (this.f.method === 'TARJETA' || this.f.method === 'YAPE') && this.mode === 'ONLINE'; }

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
    if (this.isOnline) { this.createCharge(); return; }
    this.saving = true;
    this.e = {};
    const body = { patientId: this.patient.id, treatmentItemId: this.sel || null, amount: this.f.amount, method: this.f.method,
      reference: this.f.method === 'EFECTIVO' ? null : this.f.reference, notes: this.f.notes };
    this.api.pay(body, this.autoInvoice && this.auth.can('COMPROBANTES')).subscribe({
      next: (r) => {
        this.saving = false;
        this.done = r.payment;
        this.doneConcept = r.concept;
        this.acc = r.account;
        this.invoice = r.comprobante || null;
        this.invoiceError = r.comprobanteError || '';
        this.toast.ok(`Cobro de ${soles(r.payment.amount)} registrado.` + (this.invoice ? ` Boleta ${this.invoice.invoice} emitida.` : ''));
      },
      error: (err) => { this.saving = false; const a = apiError(err); this.e = a.fields || {}; this.toast.error(a.message); },
    });
  }

  createCharge() {
    if (!this.patient) return;
    this.saving = true;
    this.e = {};
    this.api.onlineCreate({ patientId: this.patient.id, treatmentItemId: this.sel || null, amount: this.f.amount, description: this.f.notes || undefined }).subscribe({
      next: (c) => { this.saving = false; this.charge = c; this.startPoll(); this.toast.ok('Link de pago listo. Ábrelo o envíalo al paciente.'); },
      error: (err) => { this.saving = false; const a = apiError(err); this.e = a.fields || {}; this.toast.error(a.message); },
    });
  }

  private startPoll() {
    this.stopPoll();
    this.poll = setInterval(() => {
      if (!this.charge) return;
      this.api.onlineStatus(this.charge.id).subscribe({ next: (c) => this.onCharge(c), error: () => {} });
    }, 4000);
  }

  private stopPoll() { if (this.poll) { clearInterval(this.poll); this.poll = null; } }

  private onCharge(c: OnlineCharge) {
    this.charge = c;
    if (c.status === 'PENDIENTE') return;
    this.stopPoll();
    if (c.status === 'APROBADO' && this.patient) {
      const methodCode = c.method === 'yape' ? 'YAPE' : 'TARJETA';
      this.done = { id: c.paymentId || 0, patientId: this.patient.id, amount: c.amount, method: methodCode, reference: 'MP-' + c.mpPaymentId,
        paidAt: new Date().toISOString(), cashier: 'Mercado Pago', status: 'VALIDO' } as any;
      this.doneConcept = c.description;
      this.api.account(this.patient.id).subscribe((a) => (this.acc = a));
      this.toast.ok(`Mercado Pago aprobó el pago de ${soles(c.amount)}. Quedó registrado en caja.`);
      this.followInvoice(c);
    }
  }

  /** La boleta del pago en línea la emite el servidor al aprobarse; se consulta unos segundos hasta que aparece. */
  private followInvoice(c: OnlineCharge) {
    if (c.invoiceId && c.invoice) {
      this.invoicing = false;
      this.invoice = { invoiceId: c.invoiceId, invoice: c.invoice, status: c.invoiceStatus || 'GENERADO', message: '' };
      return;
    }
    if (!this.autoInvoice || !this.auth.can('COMPROBANTES')) return;
    if (this.invoiceTries >= 5) {
      // El servidor no la emitió (o tarda): se pide directamente; es idempotente.
      if (c.paymentId) this.issueInvoice(); else this.invoicing = false;
      return;
    }
    this.invoiceTries++;
    this.invoicing = true;
    setTimeout(() => this.api.onlineStatus(c.id).subscribe({ next: (x) => this.followInvoice(x), error: () => (this.invoicing = false) }), 3000);
  }

  issueInvoice() {
    if (!this.done?.id) return;
    this.invoicing = true;
    this.invoiceError = '';
    this.api.paymentInvoice(this.done.id).subscribe({
      next: (r) => { this.invoicing = false; this.invoice = r; this.toast.ok(r.message); },
      error: (err) => { this.invoicing = false; this.invoiceError = apiError(err).message; },
    });
  }

  copyLink() {
    if (!this.charge?.checkoutUrl) return;
    navigator.clipboard?.writeText(this.charge.checkoutUrl).then(() => this.toast.ok('Link copiado.'), () => this.toast.error('No se pudo copiar; selecciónalo y cópialo.'));
  }

  sendLink() {
    if (!this.charge) return;
    this.sending = true;
    this.api.onlineWhatsapp(this.charge.id).subscribe({
      next: (r) => { this.sending = false; this.toast.ok(`Link enviado por WhatsApp al ${r.phone}.`); },
      error: (err) => { this.sending = false; this.toast.error(apiError(err).message); },
    });
  }

  cancelCharge() {
    if (!this.charge) return;
    this.api.onlineCancel(this.charge.id).subscribe({
      next: (c) => { this.stopPoll(); this.charge = c; this.toast.ok('Link de pago anulado.'); },
      error: (err) => this.toast.error(apiError(err).message),
    });
  }

  again() {
    this.stopPoll();
    this.charge = null;
    this.invoice = null;
    this.invoiceError = '';
    this.invoicing = false;
    this.invoiceTries = 0;
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
