import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { apiError, dateTime, saveBlob, shortDate, soles } from '../core/format';
import { ToastService } from '../core/toast.service';

import { OnlyDirective } from '../shared/only.directive';
/** v2.9: Libro de Reclamaciones: hojas recibidas, plazo en días hábiles, respuesta y reporte anual. */
@Component({
  selector: 'app-complaints',
  standalone: true,
  imports: [OnlyDirective, FormsModule, RouterLink],
  template: `
    <div class="page">
      <header class="page-head">
        <div><h1>Libro de Reclamaciones</h1>
          <p class="muted">Reclamos y quejas del formulario público. Plazo de respuesta: {{ sum?.termDays || 15 }} días hábiles.
            Enlace para pacientes: <a [href]="publicUrl" target="_blank" rel="noopener">{{ publicUrl }}</a></p></div>
        <div class="actions">
          <select class="input sm" [(ngModel)]="year" (change)="load()" aria-label="Año">@for (y of years; track y) { <option [ngValue]="y">{{ y }}</option> }</select>
          <button class="btn ghost" type="button" (click)="exportCsv()">Exportar {{ year }}</button>
          <button class="btn ghost" type="button" (click)="showCfg = !showCfg">Ajustes</button>
        </div>
      </header>

      @if (showCfg) {
        <section class="panel cfg">
          <label>Plazo (días hábiles)<input class="input" type="number" min="1" max="30" [(ngModel)]="cfg.termDays" appOnly="int" /></label>
          <label>Correo que recibe el aviso de cada reclamo y el recordatorio diario<input class="input" type="email" [(ngModel)]="cfg.noticeEmail" placeholder="direccion@clinica.pe" appOnly="email" maxlength="120" /></label>
          <button class="btn primary" type="button" (click)="saveCfg()">Guardar</button>
          <p class="muted small">INDECOPI exige responder en 15 días hábiles como máximo; no conviene subirlo.</p>
        </section>
      }

      <div class="kpis">
        <button type="button" class="kpi" [class.on]="status === 'ABIERTOS'" (click)="setStatus('ABIERTOS')"><b>{{ sum?.open ?? '—' }}</b><span>Por responder</span></button>
        <button type="button" class="kpi warn" [class.on]="status === 'VENCIDOS'" (click)="setStatus('VENCIDOS')"><b>{{ sum?.overdue ?? '—' }}</b><span>Vencidos</span></button>
        <div class="kpi"><b>{{ sum?.dueSoon ?? '—' }}</b><span>Vencen en 3 días hábiles</span></div>
        <button type="button" class="kpi" [class.on]="status === ''" (click)="setStatus('')"><b>{{ sum?.year ?? '—' }}</b><span>Registrados en {{ thisYear }}</span></button>
      </div>

      <div class="bar">
        <div class="seg">@for (s of statuses; track s[0]) { <button type="button" [class.on]="status === s[0]" (click)="setStatus(s[0])">{{ s[1] }}</button> }</div>
        <input class="input" [(ngModel)]="q" (keyup.enter)="load()" placeholder="Buscar por nombre, documento, N.° o código" aria-label="Buscar" appOnly="search" maxlength="100" />
      </div>

      <div class="split" [class.open]="cur">
        <section class="panel">
          <table class="table">
            <thead><tr><th>N.°</th><th>Consumidor</th><th>Tipo</th><th>Plazo</th><th>Estado</th></tr></thead>
            <tbody>
              @for (c of list; track c.id) {
                <tr class="click" [class.sel]="cur?.id === c.id" (click)="open(c)">
                  <td class="mono">{{ c.number }}<div class="muted small">{{ dt(c.filedAt) }}</div></td>
                  <td>{{ c.fullName }}<div class="muted small">{{ c.docType }} {{ c.docNumber }}{{ c.minor ? ' · menor' : '' }}</div></td>
                  <td>{{ c.kind === 'RECLAMO' ? 'Reclamo' : 'Queja' }}</td>
                  <td>{{ sd(c.dueDate) }}
                    @if (c.status !== 'RESPONDIDO') { <div class="small" [class.gum]="c.overdue" [class.amber]="!c.overdue && c.daysLeft <= 3">
                      {{ c.overdue ? 'Vencido' : c.daysLeft === 0 ? 'Vence hoy' : 'Faltan ' + c.daysLeft + ' días háb.' }}</div> }</td>
                  <td><span class="tag" [class.green]="c.status === 'RESPONDIDO'" [class.amber]="c.status === 'EN_PROCESO'" [class.gum]="c.status === 'PENDIENTE'">{{ label[c.status] }}</span></td>
                </tr>
              } @empty { <tr><td colspan="5" class="empty">No hay hojas con este filtro.</td></tr> }
            </tbody>
          </table>
        </section>

        @if (cur) {
          <section class="panel det">
            <div class="spread"><h2>Hoja N.° {{ cur.number }}</h2><button class="btn sm ghost" type="button" (click)="cur = null" aria-label="Cerrar">✕</button></div>
            <p class="muted small">Código {{ cur.code }} · {{ dt(cur.filedAt) }} · copia al consumidor: {{ cur.copySent ? 'enviada' : (cur.email ? 'no se pudo enviar' : 'sin correo') }}</p>
            <dl>
              <dt>Consumidor</dt><dd>{{ cur.fullName }} · {{ cur.docType }} {{ cur.docNumber }}
                @if (cur.patientId) { · <a [routerLink]="['/pacientes', cur.patientId]">ficha {{ cur.hcNumber || '' }}</a> }</dd>
              @if (cur.minor) { <dt>Apoderado</dt><dd>{{ cur.guardianName }} · {{ cur.guardianDoc }}</dd> }
              <dt>Contacto</dt><dd>{{ cur.phone || '—' }} · {{ cur.email || 'sin correo' }}<br />{{ cur.address }}</dd>
              <dt>Servicio</dt><dd>{{ cur.service }}{{ cur.amount != null ? ' · ' + money(cur.amount) : '' }}</dd>
              <dt>{{ cur.kind === 'RECLAMO' ? 'Reclamo' : 'Queja' }}</dt><dd class="pre">{{ cur.detail }}</dd>
              <dt>Pedido</dt><dd class="pre">{{ cur.request }}</dd>
            </dl>

            @if (cur.status === 'RESPONDIDO') {
              <div class="resp"><b>Respuesta</b> · {{ dt(cur.respondedAt) }} · {{ chLabel[cur.responseChannel] }} · {{ cur.respondedBy }}
                {{ cur.responseChannel === 'EMAIL' ? (cur.responseSent ? '· enviada' : '· no se pudo enviar el correo') : '' }}
                <p class="pre">{{ cur.response }}</p>
                @if (cur.providerNotes) { <p class="muted small pre">Observaciones: {{ cur.providerNotes }}</p> }</div>
            } @else {
              <label>Observaciones y acciones adoptadas (internas, salen en la hoja)<textarea class="input" rows="3" [(ngModel)]="form.providerNotes" appOnly="text" maxlength="5000"></textarea></label>
              <button class="btn ghost sm" type="button" (click)="markInProcess()">{{ cur.status === 'PENDIENTE' ? 'Marcar en revisión' : 'Guardar observaciones' }}</button>
              <label>Respuesta al consumidor<textarea class="input" rows="6" [(ngModel)]="form.response" placeholder="Explica qué revisaste y qué solución ofrece la clínica." appOnly="text" maxlength="5000"></textarea></label>
              <div class="seg">@for (c of channels; track c[0]) { <button type="button" [class.on]="form.channel === c[0]" [disabled]="c[0] === 'EMAIL' && !cur.email" (click)="form.channel = c[0]">{{ c[1] }}</button> }</div>
              @if (err) { <div class="err">{{ err }}</div> }
              <button class="btn primary" type="button" [disabled]="busy" (click)="respond()">{{ form.channel === 'EMAIL' ? 'Responder y enviar por correo' : 'Registrar respuesta' }}</button>
            }
            <button class="btn ghost sm" type="button" (click)="pdf()">Descargar hoja (PDF)</button>
          </section>
        }
      </div>
    </div>
  `,
  styles: [`
    .actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
    .cfg { display: grid; grid-template-columns: 160px 1fr auto; gap: 12px; align-items: end; margin-bottom: 14px; }
    .cfg label { display: grid; gap: 4px; font-size: 13px; }
    .cfg p { grid-column: 1 / -1; margin: 0; }
    .kpis { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-bottom: 12px; }
    .kpi { background: #fff; border: 1px solid var(--line); border-radius: 14px; padding: 12px; text-align: left; font: inherit; display: grid; gap: 2px; }
    button.kpi { cursor: pointer; }
    .kpi.on { border-color: var(--teal); background: var(--teal-soft); }
    .kpi b { font-size: 24px; }
    .kpi span { color: var(--muted); font-size: 12.5px; }
    .kpi.warn b { color: var(--gum); }
    .bar { display: flex; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
    .bar .input { flex: 1; min-width: 220px; }
    .split { display: grid; grid-template-columns: minmax(0, 1fr); gap: 14px; }
    .split.open { grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr); }
    @media (max-width: 980px) { .split.open { grid-template-columns: minmax(0, 1fr); } .kpis { grid-template-columns: repeat(2, minmax(0, 1fr)); } .cfg { grid-template-columns: 1fr; } }
    tr.click { cursor: pointer; } tr.sel { background: var(--teal-soft); }
    .mono { font-family: monospace; white-space: nowrap; }
    .det { display: grid; gap: 10px; align-content: start; }
    .det h2 { margin: 0; font-size: 18px; }
    dl { display: grid; grid-template-columns: 110px 1fr; gap: 6px 10px; margin: 0; font-size: 14px; }
    dt { color: var(--muted); }
    dd { margin: 0; }
    .pre { white-space: pre-wrap; }
    .det label { display: grid; gap: 4px; font-size: 13px; }
    .resp { background: var(--green-soft, #ecf8f0); border-radius: 12px; padding: 10px 12px; font-size: 14px; }
    .gum { color: var(--gum); font-weight: 700; } .amber { color: #b26a00; font-weight: 700; }
    .err { color: var(--gum); font-size: 13px; }
    .small { font-size: 12.5px; }
  `],
})
export class ComplaintsComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  thisYear = new Date().getFullYear();
  year = this.thisYear;
  years = [this.thisYear, this.thisYear - 1, this.thisYear - 2];
  statuses = [['ABIERTOS', 'Por responder'], ['VENCIDOS', 'Vencidos'], ['RESPONDIDO', 'Respondidos'], ['', 'Todos']];
  channels = [['EMAIL', 'Por correo'], ['DOMICILIO', 'Carta a su domicilio'], ['PRESENCIAL', 'En la clínica']];
  label: Record<string, string> = { PENDIENTE: 'Pendiente', EN_PROCESO: 'En revisión', RESPONDIDO: 'Respondido' };
  chLabel: Record<string, string> = { EMAIL: 'por correo', DOMICILIO: 'carta a domicilio', PRESENCIAL: 'en la clínica' };
  status = 'ABIERTOS';
  q = '';
  list: any[] = [];
  sum: any = null;
  cur: any = null;
  form: any = {};
  err = '';
  busy = false;
  showCfg = false;
  cfg: any = { termDays: 15, noticeEmail: '' };
  publicUrl = location.origin + '/libro-reclamaciones';

  ngOnInit() { this.load(); }

  load() {
    this.api.complaints(this.status || undefined, this.year, this.q || undefined).subscribe((l) => (this.list = l));
    this.api.complaintSummary().subscribe((s) => { this.sum = s; this.cfg = { termDays: s.termDays, noticeEmail: s.noticeEmail }; });
  }

  setStatus(s: string) { this.status = s; this.load(); }

  open(c: any) {
    this.cur = c; this.err = '';
    this.form = { providerNotes: c.providerNotes || '', response: '', channel: c.email ? 'EMAIL' : 'DOMICILIO' };
  }

  markInProcess() {
    this.api.updateComplaint(this.cur.id, { status: 'EN_PROCESO', providerNotes: this.form.providerNotes }).subscribe({
      next: (c) => { this.toast.ok('Guardado'); this.refresh(c); }, error: (e) => this.toast.error(apiError(e).message),
    });
  }

  respond() {
    this.busy = true; this.err = '';
    this.api.respondComplaint(this.cur.id, { ...this.form, sendEmail: true }).subscribe({
      next: (c) => { this.busy = false; this.toast.ok('Respuesta registrada'); this.refresh(c); },
      error: (e) => { this.busy = false; const a = apiError(e); this.err = a.fields ? Object.values(a.fields).join(' ') : a.message; },
    });
  }

  refresh(c: any) { this.cur = c; this.open(c); this.load(); }

  saveCfg() {
    this.api.complaintSettings({ termDays: String(this.cfg.termDays), noticeEmail: this.cfg.noticeEmail }).subscribe({
      next: (s) => { this.sum = s; this.showCfg = false; this.toast.ok('Ajustes guardados'); }, error: (e) => this.toast.error(apiError(e).message),
    });
  }

  pdf() { this.api.complaintPdf(this.cur.id).subscribe((b) => saveBlob(b, `hoja-reclamacion-${this.cur.number}.pdf`)); }
  exportCsv() { this.api.complaintsCsv(this.year).subscribe((b) => saveBlob(b, `libro-reclamaciones-${this.year}.csv`)); }

  dt = dateTime;
  sd = shortDate;
  money = soles;
}
