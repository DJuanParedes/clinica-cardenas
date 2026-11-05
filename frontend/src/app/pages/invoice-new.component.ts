import { Component, Input, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api, RucInfo } from '../core/api.service';
import { ToastService } from '../core/toast.service';
import { Patient, Procedure } from '../core/models';
import { apiError, patientDocToSunat, soles, validRuc } from '../core/format';
import { IconComponent } from '../shared/icon.component';
import { PatientPickerComponent } from '../shared/patient-picker.component';

interface Line { description: string; quantity: number; unitPrice: number; treatmentItemId?: number | null; }

const IGV = 0.18;
const BOLETA_LIMIT = 700;

/** HU-10: generar comprobante por los servicios prestados. */
@Component({
  selector: 'app-invoice-new',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent, PatientPickerComponent],
  template: `
    <div class="page">
      <a class="back" routerLink="/comprobantes"><app-icon name="chevronLeft" [size]="16" /> Comprobantes</a>
      <div class="page-head">
        <div>
          <h1>Emitir comprobante</h1>
          <p class="sub">Los precios incluyen IGV (18 %). La numeración la asigna el sistema al generar.</p>
        </div>
        <div class="seg big" role="radiogroup" aria-label="Tipo de comprobante">
          <button type="button" [class.on]="docType === '03'" (click)="setType('03')">Boleta B001</button>
          <button type="button" [class.on]="docType === '01'" (click)="setType('01')">Factura F001</button>
        </div>
      </div>

      <div class="layout">
        <div class="stack-l">
          <section class="panel">
            <div class="panel-head"><h3>Paciente atendido</h3><span class="muted small">Opcional · permite facturar procedimientos realizados</span></div>
            <div class="panel-body">
              <app-patient-picker [value]="patient" (valueChange)="onPatient($event)" />
              @if (billable.length) {
                <div class="billable">
                  <p class="small strong">Procedimientos realizados sin facturar</p>
                  @for (b of billable; track b.treatmentItemId) {
                    <label class="bitem">
                      <input type="checkbox" [checked]="hasItem(b.treatmentItemId)" (change)="toggleItem(b)" />
                      <span class="grow">{{ b.description }}<span class="muted small"> · {{ b.planTitle }}</span></span>
                      <span class="tabular strong">{{ money(b.price) }}</span>
                    </label>
                  }
                </div>
              } @else if (patient && loadedBillable) {
                <p class="muted small" style="margin-top: 10px">No tiene procedimientos realizados pendientes de facturar.</p>
              }
            </div>
          </section>

          <section class="panel">
            <div class="panel-head"><h3>Datos del cliente</h3></div>
            <div class="panel-body grid cols-3">
              <div class="field">
                <label class="req" for="cdt">Documento</label>
                <select id="cdt" class="input" [(ngModel)]="customer.docType" [disabled]="docType === '01'">
                  @if (docType === '01') { <option value="6">RUC</option> }
                  @else {
                    <option value="1">DNI</option><option value="4">Carné de extranjería</option>
                    <option value="7">Pasaporte</option><option value="6">RUC</option><option value="0">Sin documento</option>
                  }
                </select>
                @if (e['customerDocType']) { <span class="err">{{ e['customerDocType'] }}</span> }
              </div>
              @if (customer.docType !== '0') {
                <div class="field">
                  <label class="req" for="cdn">Número</label>
                  <input id="cdn" class="input tabular" [class.invalid]="e['customerDocNumber']" [(ngModel)]="customer.docNumber"
                         (ngModelChange)="onDocNumber($event)"
                         [attr.maxlength]="customer.docType === '6' ? 11 : customer.docType === '1' ? 8 : 12" inputmode="numeric" />
                  @if (e['customerDocNumber']) { <span class="err">{{ e['customerDocNumber'] }}</span> }
                  @else if (customer.docType === '6' && customer.docNumber.length === 11) {
                    @if (!rucOk) { <span class="hint">Dígito verificador incorrecto</span> }
                    @else if (rucLoading) { <span class="hint">Consultando el padrón de SUNAT…</span> }
                    @else if (rucInfo) {
                      <span class="hint" [class.ok]="rucHabil" [class.bad]="!rucHabil">
                        SUNAT: {{ rucInfo.estado }} · {{ rucInfo.condicion }}
                      </span>
                    } @else if (rucMsg) { <span class="hint">{{ rucMsg }}</span> }
                    @else { <span class="hint ok">RUC válido</span> }
                  }
                </div>
                <div class="field">
                  <label class="req" for="cn">{{ customer.docType === '6' ? 'Razón social' : 'Nombre completo' }}</label>
                  <input id="cn" class="input" [class.invalid]="e['customerName']" [(ngModel)]="customer.name" />
                  @if (e['customerName']) { <span class="err">{{ e['customerName'] }}</span> }
                </div>
              } @else {
                <div class="field span-2"><span class="label">Cliente</span><p class="muted" style="padding-top: 8px">Clientes varios · permitido en boletas de hasta S/ 700.00</p></div>
              }
              @if (rucInfo && !rucHabil && customer.docType === '6') {
                <div class="notice amber span-all" role="alert">
                  <app-icon name="alert" />
                  <div>Según SUNAT este RUC está <strong>{{ rucInfo.estado }}</strong> y <strong>{{ rucInfo.condicion }}</strong>.
                    Una factura a este cliente sería rechazada; confirma los datos o emite boleta.</div>
                </div>
              }
              @if (docType === '01' || customer.docType === '6') {
                <div class="field span-all">
                  <label [class.req]="docType === '01'" for="ca">Dirección fiscal</label>
                  <input id="ca" class="input" [class.invalid]="e['customerAddress']" [(ngModel)]="customer.address" />
                  @if (e['customerAddress']) { <span class="err">{{ e['customerAddress'] }}</span> }
                </div>
              }
            </div>
          </section>

          <section class="panel">
            <div class="panel-head">
              <h3>Conceptos</h3>
              <select class="input addproc" [(ngModel)]="catalogPick" (ngModelChange)="addProcedure($event)" aria-label="Agregar desde catálogo">
                <option [ngValue]="null">Agregar desde el catálogo…</option>
                @for (p of procedures; track p.id) { <option [ngValue]="p">{{ p.name }} · {{ money(p.price) }}</option> }
              </select>
            </div>
            <table class="table lines">
              <thead><tr><th>Descripción</th><th class="num">Cant.</th><th class="num">P. unit. (inc. IGV)</th><th class="num">Importe</th><th></th></tr></thead>
              <tbody>
                @for (l of lines; track $index) {
                  <tr>
                    <td><input class="input" [(ngModel)]="l.description" [disabled]="!!l.treatmentItemId" aria-label="Descripción" /></td>
                    <td class="num"><input class="input tabular qty" type="number" min="1" step="1" [(ngModel)]="l.quantity" aria-label="Cantidad" /></td>
                    <td class="num"><input class="input tabular price" type="number" min="0" step="0.5" [(ngModel)]="l.unitPrice" [disabled]="!!l.treatmentItemId" aria-label="Precio" /></td>
                    <td class="num tabular strong">{{ money((l.quantity || 0) * (l.unitPrice || 0)) }}</td>
                    <td><button type="button" class="btn ghost sm" (click)="lines.splice($index, 1)" aria-label="Quitar"><app-icon name="x" [size]="15" /></button></td>
                  </tr>
                } @empty {
                  <tr><td colspan="5" class="muted center">Agrega conceptos desde el catálogo, desde los procedimientos del paciente o como línea libre.</td></tr>
                }
              </tbody>
            </table>
            <div class="panel-foot">
              <button type="button" class="btn sm" (click)="lines.push({ description: '', quantity: 1, unitPrice: 0 })"><app-icon name="plus" [size]="14" /> Línea libre</button>
              @if (e['items']) { <span class="err small" style="margin-left: 12px">{{ e['items'] }}</span> }
            </div>
          </section>
        </div>

        <aside class="panel totals">
          <div class="panel-head"><h3>{{ docType === '01' ? 'Factura electrónica' : 'Boleta de venta electrónica' }}</h3></div>
          <div class="panel-body">
            <dl class="tot tabular">
              <dt>Op. gravada</dt><dd>{{ money(taxable) }}</dd>
              <dt>IGV 18 %</dt><dd>{{ money(igv) }}</dd>
              <dt class="t">Total</dt><dd class="t">{{ money(total) }}</dd>
            </dl>
            @if (docType === '03' && customer.docType === '0' && total > limit) {
              <div class="notice amber small">Las boletas mayores a S/ 700.00 deben identificar al cliente.</div>
            }
            <label class="check send">
              <input type="checkbox" [(ngModel)]="sendNow" /> Enviar a SUNAT al generar
            </label>
            <button type="button" class="btn primary lg block" (click)="generate()" [disabled]="saving || lines.length === 0">
              @if (saving) { <span class="spin"></span> {{ sendNow ? 'Generando y enviando…' : 'Generando…' }} } @else { Generar comprobante }
            </button>
          </div>
        </aside>
      </div>
    </div>
  `,
  styles: [`
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 18px; align-items: start; }
    .stack-l { display: flex; flex-direction: column; gap: 16px; }
    .seg.big button { height: 40px; padding: 0 18px; font-size: 14px; }
    .billable { margin-top: 14px; display: flex; flex-direction: column; gap: 6px; }
    .bitem { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid var(--line); border-radius: var(--radius-s); cursor: pointer; font-size: var(--step--1); }
    .bitem:hover { background: var(--surface-2); }
    .hint.ok { color: var(--green); }
    .hint.bad { color: var(--amber); font-weight: 700; }
    .addproc { width: 300px; height: 34px; font-size: 13px; }
    .lines td { padding: 8px 10px; }
    .lines .input { height: 34px; font-size: 14px; }
    .qty { width: 70px; text-align: right; }
    .price { width: 120px; text-align: right; }
    .center { text-align: center; padding: 22px; }
    .totals { position: sticky; top: 20px; }
    .tot { display: grid; grid-template-columns: 1fr auto; gap: 8px; margin: 0 0 16px; }
    .tot dt { color: var(--muted); }
    .tot dd { margin: 0; text-align: right; }
    .tot .t { font-size: var(--step-2); font-weight: 700; color: var(--ink); padding-top: 8px; border-top: 1px solid var(--line); }
    .send { margin: 6px 0 14px; }
    @media (max-width: 1000px) { .layout { grid-template-columns: 1fr; } .totals { position: static; } .addproc { width: 100%; } }
  `],
})
export class InvoiceNewComponent implements OnInit {
  private api = inject(Api);
  private router = inject(Router);
  private toast = inject(ToastService);

  @Input() paciente?: string;

  docType: '01' | '03' = '03';
  patient: Patient | null = null;
  billable: any[] = [];
  loadedBillable = false;
  procedures: Procedure[] = [];
  customer = { docType: '1', docNumber: '', name: '', address: '' };
  lines: Line[] = [];
  e: Record<string, string> = {};
  saving = false;
  sendNow = true;
  limit = BOLETA_LIMIT;
  catalogPick: Procedure | null = null;

  ngOnInit() {
    this.api.procedures().subscribe((p) => (this.procedures = p));
    if (this.paciente) this.api.patient(+this.paciente).subscribe((r) => this.onPatient(r.patient));
  }

  setType(t: '01' | '03') {
    this.docType = t;
    this.e = {};
    this.rucInfo = null;
    this.rucMsg = '';
    if (t === '01') {
      this.customer = { docType: '6', docNumber: '', name: '', address: '' };
    } else if (this.patient) {
      this.fillFromPatient(this.patient);
    } else {
      this.customer = { docType: '1', docNumber: '', name: '', address: '' };
    }
  }

  private fillFromPatient(p: Patient) {
    this.customer = { docType: patientDocToSunat(p.docType), docNumber: p.docNumber, name: p.fullName.toUpperCase(), address: p.address || '' };
  }

  onPatient(p: Patient | null) {
    this.patient = p;
    this.lines = this.lines.filter((l) => !l.treatmentItemId);
    this.billable = [];
    this.loadedBillable = false;
    if (!p) return;
    if (this.docType === '03') this.fillFromPatient(p);
    this.api.billableItems(p.id).subscribe((b) => {
      this.billable = b;
      this.loadedBillable = true;
      for (const it of b) this.toggleItem(it);
    });
  }

  hasItem(id: number) { return this.lines.some((l) => l.treatmentItemId === id); }

  toggleItem(b: any) {
    if (this.hasItem(b.treatmentItemId)) this.lines = this.lines.filter((l) => l.treatmentItemId !== b.treatmentItemId);
    else this.lines.push({ description: b.description, quantity: 1, unitPrice: Number(b.price), treatmentItemId: b.treatmentItemId });
  }

  addProcedure(p: Procedure | null) {
    if (!p) return;
    this.lines.push({ description: p.name, quantity: 1, unitPrice: Number(p.price) });
    setTimeout(() => (this.catalogPick = null));
  }

  rucInfo: RucInfo | null = null;
  rucLoading = false;
  rucMsg = '';

  get total(): number {
    return Math.round(this.lines.reduce((s, l) => s + Math.round((l.quantity || 0) * (l.unitPrice || 0) * 100) / 100, 0) * 100) / 100;
  }
  get taxable(): number {
    return this.lines.reduce((s, l) => s + Math.round(((l.quantity || 0) * (l.unitPrice || 0) / (1 + IGV)) * 100) / 100, 0);
  }
  get igv(): number { return Math.round((this.total - this.taxable) * 100) / 100; }
  get rucOk(): boolean { return validRuc(this.customer.docNumber); }
  get rucHabil(): boolean { return !!this.rucInfo && this.rucInfo.estado === 'ACTIVO' && this.rucInfo.condicion === 'HABIDO'; }

  /** Consulta RUC en SUNAT y completa razón social y dirección fiscal. */
  onDocNumber(v: string) {
    this.rucInfo = null;
    this.rucMsg = '';
    const n = (v || '').trim();
    if (this.customer.docType !== '6' || n.length !== 11 || !validRuc(n)) return;
    this.rucLoading = true;
    this.api.lookupRuc(n).subscribe({
      next: (r) => {
        if (this.customer.docNumber.trim() !== n) return;
        this.rucLoading = false;
        this.rucInfo = r;
        this.customer.name = r.razonSocial;
        const addr = [r.direccion, r.distrito, r.provincia, r.departamento].filter((x) => x && x !== '-').join(', ');
        if (addr) this.customer.address = addr;
      },
      error: (err) => {
        this.rucLoading = false;
        const a = apiError(err);
        this.rucMsg = a.status === 404 ? 'RUC válido · no figura en el padrón: escribe los datos' : 'Consulta RUC no disponible: escribe los datos';
      },
    });
  }

  private validate(): boolean {
    const e: Record<string, string> = {};
    const c = this.customer;
    const n = c.docNumber.trim();
    if (this.docType === '01') {
      if (!validRuc(n)) e['customerDocNumber'] = 'Ingresa un RUC válido de 11 dígitos.';
      if (!c.address.trim()) e['customerAddress'] = 'La factura requiere dirección fiscal.';
    } else if (c.docType === '1' && !/^\d{8}$/.test(n)) e['customerDocNumber'] = 'El DNI debe tener 8 dígitos.';
    else if (c.docType === '6' && !validRuc(n)) e['customerDocNumber'] = 'El RUC no es válido.';
    else if (c.docType === '0' && this.total > BOLETA_LIMIT) e['customerDocType'] = 'Identifica al cliente: el total supera S/ 700.00.';
    if (c.docType !== '0' && c.name.trim().length < 3) e['customerName'] = 'Ingresa el nombre o razón social.';
    if (this.lines.length === 0) e['items'] = 'Agrega al menos un concepto.';
    else if (this.lines.some((l) => !l.description.trim() || !(l.quantity > 0) || !(l.unitPrice > 0))) e['items'] = 'Cada concepto necesita descripción, cantidad y precio mayor a cero.';
    this.e = e;
    return Object.keys(e).length === 0;
  }

  generate() {
    if (!this.validate()) { this.toast.warn('Revisa los datos del comprobante.'); return; }
    this.saving = true;
    const body = {
      docType: this.docType, patientId: this.patient?.id ?? null, customerDocType: this.customer.docType,
      customerDocNumber: this.customer.docNumber.trim(), customerName: this.customer.name.trim(), customerAddress: this.customer.address.trim() || null,
      items: this.lines.map((l) => ({ description: l.description.trim(), quantity: l.quantity, unitPrice: l.unitPrice, treatmentItemId: l.treatmentItemId ?? null })),
    };
    this.api.createInvoice(body).subscribe({
      next: (d) => {
        const id = d.invoice.id;
        if (!this.sendNow) {
          this.toast.ok(`Comprobante ${d.invoice.fullNumber} generado.`);
          this.router.navigate(['/comprobantes', id]);
          return;
        }
        this.api.sendInvoice(id).subscribe({
          next: (s) => {
            const st = s.invoice.status;
            if (st === 'ACEPTADO' || st === 'OBSERVADO') this.toast.ok(`${s.invoice.fullNumber} aceptado por SUNAT.`);
            else this.toast.warn(`${s.invoice.fullNumber}: ${s.invoice.sunatDescription}`);
            this.router.navigate(['/comprobantes', id]);
          },
          error: () => this.router.navigate(['/comprobantes', id]),
        });
      },
      error: (err) => {
        this.saving = false;
        const a = apiError(err);
        this.e = a.fields || {};
        this.toast.error(a.message);
      },
    });
  }

  money = soles;
}
