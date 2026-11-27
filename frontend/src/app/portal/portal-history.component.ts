import { Component, OnInit, inject } from '@angular/core';
import { Api } from '../core/api.service';
import { ToastService } from '../core/toast.service';
import { apiError, dateTime, saveBlob, shortDate, soles } from '../core/format';
import { IconComponent } from '../shared/icon.component';

/** Mi historia: tratamientos (qué se hizo y cuándo), pagos, ficha, documentos firmados y archivos que pide la clínica. */
@Component({
  selector: 'app-portal-history',
  standalone: true,
  imports: [IconComponent],
  template: `
    <nav class="seg">
      <button type="button" [class.on]="tab === 't'" (click)="tab = 't'">Tratamientos</button>
      <button type="button" [class.on]="tab === 'p'" (click)="tab = 'p'">Pagos</button>
      <button type="button" [class.on]="tab === 'f'" (click)="tab = 'f'">Mi ficha</button>
      <button type="button" [class.on]="tab === 'd'" (click)="tab = 'd'">Documentos @if (pendingReq) { <b class="dot">{{ pendingReq }}</b> }</button>
    </nav>

    @if (!t) { <div class="skeleton" style="height: 200px; border-radius: 18px"></div> }
    @else {
      @switch (tab) {
        @case ('t') {
          @for (pl of t.plans; track pl.id) {
            <section class="card">
              <div class="spread"><strong>{{ pl.title }}</strong><span class="tag" [class.green]="pl.status === 'COMPLETADO'">{{ pl.doneCount }}/{{ pl.itemCount }} realizados</span></div>
              <div class="muted small">{{ pl.dentist || '' }} · desde {{ d(pl.createdAt) }}</div>
              <div class="bar"><span [style.width.%]="pl.itemCount ? pl.doneCount * 100 / pl.itemCount : 0"></span></div>
              <ul class="items">
                @for (it of pl.items; track it.id) {
                  <li [class.done]="it.status === 'REALIZADO'">
                    <app-icon [name]="it.status === 'REALIZADO' ? 'check' : 'clock'" [size]="16" />
                    <div class="grow"><div>{{ it.procedure }}{{ it.tooth ? ' · pieza ' + it.tooth : '' }}</div>
                      <div class="muted small">@if (it.status === 'REALIZADO') { Realizado el {{ dt(it.doneAt) }}{{ it.doneBy ? ' por ' + it.doneBy : '' }} } @else { {{ it.status === 'EN_PROCESO' ? 'En proceso' : 'Pendiente' }} }</div></div>
                    <div class="right small"><div>{{ m(it.price) }}</div>@if (+it.balance > 0) { <div class="owe">debe {{ m(it.balance) }}</div> } @else { <div class="ok">pagado</div> }</div>
                  </li>
                }
              </ul>
              @if (pl.sessions.length) {
                <details><summary>Sesiones de atención ({{ pl.sessions.length }})</summary>
                  @for (s of pl.sessions; track $index) { <div class="ses"><strong>{{ d(s.date) }}</strong> · {{ s.dentist }}{{ s.procedure ? ' · ' + s.procedure : '' }}<div class="small">{{ s.notes }}</div></div> }
                </details>
              }
              <div class="tot small">Total {{ m(pl.total) }} · pagado {{ m(pl.paid) }} · <strong>saldo {{ m(pl.balance) }}</strong></div>
            </section>
          } @empty { <section class="card muted">Aún no tienes planes de tratamiento.</section> }
          @if (t.visits.length) {
            <h3>Atenciones</h3>
            <section class="card">@for (v of t.visits; track $index) { <div class="row small"><span class="grow">{{ dt(v.date) }} · {{ v.reason }}</span><span class="muted">{{ v.status === 'ATENDIDA' ? v.dentist : 'No asistió' }}</span></div> }</section>
          }
        }
        @case ('p') {
          <section class="card sum"><div><span class="muted small">Total de tratamientos</span><strong>{{ m(t.total) }}</strong></div>
            <div><span class="muted small">Pagado</span><strong class="ok">{{ m(t.paid) }}</strong></div><div><span class="muted small">Saldo</span><strong [class.owe]="+t.balance > 0">{{ m(t.balance) }}</strong></div></section>
          <h3>Pagos realizados</h3>
          <section class="card">
            @for (p of t.payments; track p.id) { <div class="row small" [class.void]="p.status !== 'VALIDO'"><span class="grow">{{ dt(p.paidAt) }} · {{ p.procedure || 'Pago a cuenta' }}<span class="muted"> · {{ p.method.toLowerCase() }}</span></span><strong>{{ m(p.amount) }}</strong></div> }
            @empty { <span class="muted">Aún no registras pagos.</span> }
          </section>
          @if (docs?.invoices?.length) {
            <h3>Comprobantes</h3>
            <section class="card">@for (i of docs.invoices; track i.id) { <button class="lnk" type="button" (click)="openInvoice(i)"><app-icon name="receipt" [size]="16" /> <span class="grow">{{ i.type }} {{ i.number }} · {{ d(i.date) }}</span><strong>{{ m(i.total) }}</strong></button> }</section>
          }
        }
        @case ('f') {
          <section class="card">
            @if (t.clinical) {
              <div class="kv"><span>Alergias</span><strong [class.owe]="t.clinical.allergies">{{ t.clinical.allergies || 'No refiere' }}</strong></div>
              <div class="kv"><span>Medicación actual</span><span>{{ t.clinical.medication || '—' }}</span></div>
              <div class="kv"><span>Antecedentes</span><span>{{ t.clinical.background || '—' }}</span></div>
              <div class="kv"><span>Grupo sanguíneo</span><span>{{ t.clinical.bloodType || '—' }}</span></div>
              <p class="muted small">Actualizado el {{ dt(t.clinical.updatedAt) }}. Si algo cambió (una alergia nueva, un medicamento), avísale a tu odontólogo.</p>
            } @else { <span class="muted">Tu ficha clínica se completa en tu primera consulta.</span> }
          </section>
          <p class="muted small">Tienes derecho a acceder a tu historia clínica completa (Ley 29414). Puedes pedir una copia en recepción.</p>
        }
        @case ('d') {
          <h3>Archivos que te pide la clínica</h3>
          <section class="card">
            @for (r of reqs; track r.id) {
              <div class="req">
                <div class="grow"><strong>{{ r.title }}</strong>@if (r.planTitle) { <span class="muted small"> · para {{ r.planTitle }}</span> }
                  <div class="muted small">{{ r.description || '' }}{{ r.dueDate ? ' · antes del ' + d(r.dueDate) : '' }}</div></div>
                @if (r.status === 'PENDIENTE') {
                  <label class="btn sm primary"><app-icon name="upload" [size]="14" /> {{ busyId === r.id ? 'Subiendo…' : 'Subir' }}
                    <input type="file" accept="image/jpeg,image/png,image/webp,application/pdf" hidden (change)="upload(r, $event)" [disabled]="busyId" /></label>
                } @else { <button class="btn sm" type="button" (click)="openFile(r.fileId)"><app-icon name="check" [size]="14" /> Recibido · ver</button> }
              </div>
            } @empty { <span class="muted">La clínica no te ha pedido archivos.</span> }
          </section>
          <h3>Consentimientos firmados</h3>
          <section class="card">
            @for (c of consents?.records || []; track c.id) {
              <div class="req"><div class="grow"><strong>{{ c.title || c.code }}</strong><div class="muted small">{{ dt(c.acceptedAt) }} · {{ channel(c.channel) }} · {{ c.acceptedBy }}</div></div>
                <button class="btn sm" type="button" (click)="consentPdf(c)"><app-icon name="download" [size]="14" /> Constancia</button></div>
            } @empty { <span class="muted">Sin registros.</span> }
            @if (consents?.signedFileId) { <button class="btn sm" type="button" (click)="openFile(consents.signedFileId)"><app-icon name="file" [size]="14" /> Ver mi copia firmada</button> }
          </section>
          @if (docs?.files?.length) {
            <h3>Documentos de tu atención</h3>
            <section class="card">@for (f of docs.files; track f.id) { <button class="lnk" type="button" (click)="openFile(f.id)"><app-icon name="file" [size]="16" /> <span class="grow">{{ f.title }}</span><span class="muted small">{{ d(f.date) }}</span></button> }</section>
          }
        }
      }
    }
  `,
  styles: [`
    .seg { display: flex; gap: 4px; background: #fff; border: 1px solid var(--line); border-radius: 14px; padding: 4px; margin-bottom: 14px; overflow-x: auto; }
    .seg button { flex: 1; border: 0; background: none; padding: 9px 10px; border-radius: 10px; font: inherit; font-size: 14px; cursor: pointer; white-space: nowrap; color: var(--ink-2); }
    .seg button.on { background: var(--brand-soft); color: var(--teal-strong); font-weight: 700; }
    .dot { background: var(--gum); color: #fff; border-radius: 10px; padding: 0 6px; font-size: 11px; }
    .card { background: #fff; border: 1px solid var(--line); border-radius: 18px; padding: 14px 16px; display: flex; flex-direction: column; gap: 8px; margin-bottom: 12px; }
    h3 { font-size: 15px; margin: 16px 4px 8px; }
    .bar { height: 8px; border-radius: 6px; background: #eef2f4; overflow: hidden; } .bar span { display: block; height: 100%; background: var(--teal); }
    .items { list-style: none; margin: 4px 0 0; padding: 0; display: grid; gap: 10px; }
    .items li { display: flex; gap: 10px; align-items: flex-start; } .items li.done app-icon { color: var(--teal-strong); }
    .right { text-align: right; white-space: nowrap; } .owe { color: var(--gum); font-weight: 700; } .ok { color: #1f7a4d; font-weight: 700; }
    .tot { border-top: 1px solid var(--line); padding-top: 8px; }
    details summary { cursor: pointer; color: var(--teal-strong); font-size: 14px; } .ses { padding: 6px 0; border-bottom: 1px dashed var(--line); font-size: 13.5px; }
    .sum { flex-direction: row; justify-content: space-between; } .sum div { display: grid; gap: 2px; } .sum strong { font-size: 18px; }
    .void { opacity: .5; text-decoration: line-through; }
    .lnk { display: flex; gap: 10px; align-items: center; border: 0; background: none; font: inherit; text-align: left; cursor: pointer; padding: 6px 0; color: var(--ink); }
    .kv { display: grid; grid-template-columns: 130px 1fr; gap: 8px; font-size: 14px; } .kv span:first-child { color: var(--muted); }
    .req { display: flex; gap: 10px; align-items: center; padding: 6px 0; border-bottom: 1px solid var(--line); } .req:last-child { border-bottom: 0; }
    .grow { flex: 1; min-width: 0; } .row { display: flex; gap: 8px; align-items: center; } .spread { display: flex; justify-content: space-between; gap: 8px; align-items: center; }
  `],
})
export class PortalHistoryComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  tab: 't' | 'p' | 'f' | 'd' = 't';
  t: any = null;
  docs: any = null;
  consents: any = null;
  reqs: any[] = [];
  busyId: number | null = null;
  get pendingReq() { return this.reqs.filter((r) => r.status === 'PENDIENTE').length; }

  ngOnInit() {
    this.api.portalTreatments().subscribe((t) => (this.t = t));
    this.api.portalDocuments().subscribe({ next: (d) => (this.docs = d), error: () => {} });
    this.api.portalConsents().subscribe({ next: (c) => (this.consents = c), error: () => {} });
    this.api.portalFileRequests().subscribe({ next: (r) => { this.reqs = r; if (this.pendingReq) this.tab = 'd'; }, error: () => {} });
  }
  m = soles;
  d = shortDate;
  dt = dateTime;
  channel(c: string) { return c === 'APP' ? 'en la app' : c === 'PORTAL' ? 'en el portal' : 'presencial'; }

  upload(r: any, ev: Event) {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.busyId = r.id;
    this.api.portalUploadRequest(r.id, file).subscribe({
      next: (list) => { this.busyId = null; this.reqs = list; this.toast.ok('Archivo enviado a la clínica. ¡Gracias!'); },
      error: (e) => { this.busyId = null; this.toast.error(apiError(e).message); },
    });
  }
  openFile(id: number) { this.api.portalFileLink(id).subscribe({ next: (r) => window.open(r.url, '_blank'), error: (e) => this.toast.error(apiError(e).message) }); }
  openInvoice(i: any) { this.api.portalInvoiceLink(i.id).subscribe({ next: (r) => window.open(r.url, '_blank'), error: (e) => this.toast.error(apiError(e).message) }); }
  consentPdf(c: any) { this.api.portalConsentPdf(c.id).subscribe({ next: (b) => saveBlob(b, `consentimiento-${c.code.toLowerCase()}.pdf`), error: () => this.toast.error('No se pudo descargar.') }); }
}
