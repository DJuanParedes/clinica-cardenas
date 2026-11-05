import { Component, Input, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api, InvoiceDetail } from '../core/api.service';
import { ToastService } from '../core/toast.service';
import { CUSTOMER_DOC, INVOICE_LABEL, INVOICE_TONE, apiError, dateTime, saveBlob, shortDate, soles } from '../core/format';
import { IconComponent } from '../shared/icon.component';
import { LogoComponent } from '../shared/logo.component';

/** HU-11 envío a SUNAT y HU-12 consulta / descarga del comprobante. */
@Component({
  selector: 'app-invoice-detail',
  standalone: true,
  imports: [RouterLink, IconComponent, LogoComponent],
  template: `
    <div class="page">
      <a class="back no-print" routerLink="/comprobantes"><app-icon name="chevronLeft" [size]="16" /> Comprobantes</a>
      @if (!d) {
        <div class="skeleton" style="height: 480px"></div>
      } @else {
        <div class="layout">
          <article class="doc" aria-label="Representación impresa del comprobante">
            <header class="dh">
              <div class="issuer">
                <app-logo [size]="64" />
                <div>
                  <div class="biz">{{ d.company.tradeName }}</div>
                  <div class="small">{{ d.company.businessName }}</div>
                  <div class="small muted">{{ d.company.address }}</div>
                  <div class="small muted">Instagram · clinicadentalcardenas</div>
                </div>
              </div>
              <div class="rucbox">
                <div class="strong">RUC {{ d.company.ruc }}</div>
                <div class="dtype">{{ d.invoice.docTypeLabel }}</div>
                <div class="strong tabular">{{ d.invoice.series }}-{{ pad(d.invoice.number) }}</div>
              </div>
            </header>

            <dl class="cust">
              <dt>Cliente</dt><dd>{{ d.invoice.customerName }}</dd>
              <dt>{{ docLabel(d.invoice.customerDocType) }}</dt><dd class="tabular">{{ d.invoice.customerDocNumber }}</dd>
              @if (d.invoice.customerAddress) { <dt>Dirección</dt><dd>{{ d.invoice.customerAddress }}</dd> }
              <dt>Fecha de emisión</dt><dd class="tabular">{{ date(d.invoice.issueDate) }} {{ d.invoice.issueTime.substring(0, 5) }}</dd>
              <dt>Moneda</dt><dd>Soles (PEN)</dd>
              <dt>Forma de pago</dt><dd>Contado</dd>
            </dl>

            <table class="table items">
              <thead><tr><th>Cant.</th><th>Descripción</th><th class="num">V. unit.</th><th class="num">P. unit.</th><th class="num">Importe</th></tr></thead>
              <tbody>
                @for (it of d.items; track it.id) {
                  <tr>
                    <td class="tabular">{{ it.quantity }}</td>
                    <td>{{ it.description }}</td>
                    <td class="num tabular">{{ fix(it.unitValue, 2) }}</td>
                    <td class="num tabular">{{ fix(it.unitPrice, 2) }}</td>
                    <td class="num tabular">{{ fix(it.lineTotal, 2) }}</td>
                  </tr>
                }
              </tbody>
            </table>

            <div class="foot">
              <div class="words">
                <div class="strong small">{{ d.amountInWords }}</div>
                @if (d.invoice.hash) { <div class="small muted">Resumen: <span class="tabular">{{ d.invoice.hash }}</span></div> }
                <div class="small muted">Representación impresa de la {{ d.invoice.docTypeLabel.toLowerCase() }}.</div>
              </div>
              <dl class="tot tabular">
                <dt>Op. gravada</dt><dd>{{ money(d.invoice.taxableAmount) }}</dd>
                <dt>IGV 18 %</dt><dd>{{ money(d.invoice.igvAmount) }}</dd>
                <dt class="t">Importe total</dt><dd class="t">{{ money(d.invoice.totalAmount) }}</dd>
              </dl>
            </div>
          </article>

          <aside class="side no-print">
            <section class="panel">
              <div class="panel-head"><h3>Estado ante SUNAT</h3><span class="tag" [class]="'tag ' + tone(d.invoice.status)">{{ label(d.invoice.status) }}</span></div>
              <div class="panel-body stack">
                @if (d.invoice.sunatDescription) {
                  <div class="notice" [class.teal]="d.invoice.status === 'ACEPTADO'" [class.amber]="d.invoice.status === 'OBSERVADO'" [class.gum]="d.invoice.status === 'RECHAZADO' || d.invoice.status === 'ERROR'">
                    <div><div class="strong small">Código {{ d.invoice.sunatCode ?? '—' }}</div><div class="small">{{ d.invoice.sunatDescription }}</div></div>
                  </div>
                } @else {
                  <p class="small muted">Generado, aún no enviado. El envío obtiene la constancia de recepción (CDR).</p>
                }
                @if (canSend) {
                  <button type="button" class="btn primary block" (click)="send()" [disabled]="sending">
                    @if (sending) { <span class="spin"></span> Enviando a SUNAT… } @else { <app-icon name="send" [size]="16" /> {{ d.invoice.status === 'ERROR' ? 'Reintentar envío' : 'Enviar a SUNAT' }} }
                  </button>
                }
                @if (d.invoice.status !== 'GENERADO') {
                  <button type="button" class="btn block" (click)="checkStatus()" [disabled]="checking || sending">
                    @if (checking) { <span class="spin"></span> Consultando… } @else { <app-icon name="search" [size]="16" /> Consultar estado en SUNAT }
                  </button>
                }
                @if (d.invoice.status === 'RECHAZADO') {
                  <a class="btn block" routerLink="/comprobantes/nuevo" [queryParams]="{ paciente: d.patient?.id }">Emitir comprobante corregido</a>
                }
                <div class="row wrap">
                  <button type="button" class="btn sm" (click)="print()"><app-icon name="print" [size]="15" /> Imprimir</button>
                  <button type="button" class="btn sm" (click)="xml()"><app-icon name="download" [size]="15" /> XML</button>
                  <button type="button" class="btn sm" (click)="cdr()" [disabled]="!d.invoice.hasCdr"><app-icon name="download" [size]="15" /> CDR</button>
                </div>
              </div>
            </section>

            <section class="panel">
              <div class="panel-head"><h3>Envíos</h3><span class="muted small">{{ d.submissions.length }}</span></div>
              <ul class="subs">
                @for (s of d.submissions; track s.id) {
                  <li>
                    <div class="spread"><span class="tag" [class]="'tag ' + tone(s.status)">{{ label(s.status) }}</span><span class="muted small tabular">{{ s.durationMs }} ms</span></div>
                    <div class="small">{{ s.responseDescription }}</div>
                    <div class="muted small">{{ dt(s.attemptAt) }} · {{ s.createdBy }} · entorno {{ s.environment.toLowerCase() }}</div>
                  </li>
                } @empty { <li class="muted small">Sin envíos todavía.</li> }
              </ul>
            </section>

            <section class="panel">
              <div class="panel-body small">
                <dl class="kv">
                  <dt>Emitido por</dt><dd>{{ d.invoice.createdBy }}</dd>
                  <dt>Generado</dt><dd>{{ dt(d.invoice.createdAt) }}</dd>
                  @if (d.patient) { <dt>Paciente</dt><dd><a [routerLink]="['/pacientes', d.patient.id]">{{ d.patient.fullName }}</a></dd> }
                </dl>
              </div>
            </section>
          </aside>
        </div>
      }
    </div>
  `,
  styles: [`
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 18px; align-items: start; }
    .doc { background: #fff; border: 1px solid var(--line); border-radius: var(--radius); padding: 32px 36px; }
    .dh { display: flex; justify-content: space-between; gap: 24px; padding-bottom: 20px; border-bottom: 3px solid var(--brand); }
    .issuer { display: flex; gap: 14px; align-items: center; }
    .biz { font-family: var(--font-display); font-size: var(--step-2); font-weight: 900; letter-spacing: -.01em; }
    .rucbox { border: 2px solid var(--brand); border-radius: 8px; padding: 12px 18px; text-align: center; min-width: 230px; }
    .dtype { font-size: 12px; font-weight: 700; margin: 4px 0; text-transform: uppercase; letter-spacing: .04em; }
    .cust { display: grid; grid-template-columns: 130px 1fr; gap: 6px 14px; margin: 20px 0; font-size: var(--step--1); }
    .cust dt { color: var(--muted); }
    .cust dd { margin: 0; font-weight: 600; }
    .items th { background: var(--surface-2); }
    .foot { display: flex; justify-content: space-between; gap: 24px; margin-top: 18px; padding-top: 16px; border-top: 1px solid var(--line); }
    .words { display: flex; flex-direction: column; gap: 6px; max-width: 60%; }
    .tot { display: grid; grid-template-columns: auto auto; gap: 6px 22px; margin: 0; font-size: var(--step--1); }
    .tot dt { color: var(--muted); }
    .tot dd { margin: 0; text-align: right; }
    .tot .t { font-size: var(--step-1); font-weight: 700; color: var(--ink); }
    .side { display: flex; flex-direction: column; gap: 16px; position: sticky; top: 20px; }
    .subs { list-style: none; margin: 0; padding: 0; }
    .subs li { padding: 12px 18px; border-bottom: 1px solid var(--line); display: flex; flex-direction: column; gap: 4px; }
    .subs li:last-child { border-bottom: 0; }
    .kv { grid-template-columns: 100px 1fr; }
    @media (max-width: 1000px) { .layout { grid-template-columns: 1fr; } .side { position: static; } .doc { padding: 20px; } .dh { flex-direction: column; } .words { max-width: none; } .foot { flex-direction: column; } }
    @media print { .layout { display: block; } .doc { border: 0; padding: 0; } }
  `],
})
export class InvoiceDetailComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);

  @Input() id!: string;

  d: InvoiceDetail | null = null;
  sending = false;
  checking = false;

  ngOnInit() { this.api.invoice(+this.id).subscribe((d) => (this.d = d)); }

  get canSend(): boolean { return !!this.d && ['GENERADO', 'ERROR'].includes(this.d.invoice.status); }

  send() {
    this.sending = true;
    this.api.sendInvoice(+this.id).subscribe({
      next: (d) => {
        this.d = d;
        this.sending = false;
        if (d.invoice.status === 'ACEPTADO' || d.invoice.status === 'OBSERVADO') this.toast.ok(`${d.invoice.series}-${d.invoice.number} aceptado por SUNAT.`);
        else this.toast.warn(d.invoice.sunatDescription || 'SUNAT no aceptó el comprobante.');
      },
      error: (e) => { this.sending = false; this.toast.error(apiError(e).message); },
    });
  }

  checkStatus() {
    this.checking = true;
    this.api.invoiceStatus(+this.id).subscribe({
      next: (d) => {
        this.d = d;
        this.checking = false;
        this.toast.ok(d.statusMessage || 'Estado actualizado.');
      },
      error: (e) => { this.checking = false; this.toast.error(apiError(e).message); },
    });
  }

  private file(kind: 'xml' | 'cdr') {
    const inv = this.d!.invoice;
    const name = `${kind === 'cdr' ? 'R-' : ''}${this.d!.company.ruc}-${inv.docType}-${inv.series}-${inv.number}.xml`;
    this.api.download(`/api/billing/invoices/${inv.id}/${kind}`).subscribe({
      next: (b) => saveBlob(b, name),
      error: () => this.toast.error('No se pudo descargar el archivo.'),
    });
  }
  xml() { this.file('xml'); }
  cdr() { this.file('cdr'); }
  print() { window.print(); }

  pad(n: number) { return String(n).padStart(8, '0'); }
  fix(v: number, n: number) { return Number(v).toFixed(n); }
  docLabel(c: string) { return CUSTOMER_DOC[c] || 'Documento'; }
  label(s: string) { return INVOICE_LABEL[s] || s; }
  tone(s: string) { return INVOICE_TONE[s] || 'grey'; }
  money = soles;
  date = shortDate;
  dt = dateTime;
}
