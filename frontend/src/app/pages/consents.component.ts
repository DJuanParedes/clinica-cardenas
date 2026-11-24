import { DeleteButtonComponent } from '../shared/delete-button.component';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { apiError, dateTime } from '../core/format';
import { ToastService } from '../core/toast.service';

/** v2.8: plantillas de consentimiento informado (editables) y seguimiento de los enviados para firmar. */
@Component({
  selector: 'app-consents',
  standalone: true,
  imports: [DeleteButtonComponent, FormsModule, RouterLink],
  template: `
    <div class="page">
      <header class="page-head"><div><h1>Consentimientos informados</h1>
        <p class="muted">Textos que firman los pacientes. Se envían desde la ficha (pestaña "Fotos y documentos") por WhatsApp, correo o en la tablet.</p></div></header>
      <nav class="tabs"><a [class.on]="view === 'enviados'" (click)="view = 'enviados'; loadSent()">Enviados</a>
        @if (auth.can('CONSENTIMIENTOS')) { <a [class.on]="view === 'plantillas'" (click)="view = 'plantillas'">Plantillas</a> }</nav>

      @if (view === 'enviados') {
        <div class="seg st">@for (s of statuses; track s[0]) { <button type="button" [class.on]="status === s[0]" (click)="status = s[0]; loadSent()">{{ s[1] }}</button> }</div>
        <section class="panel">
          <table class="table">
            <thead><tr><th>Paciente</th><th>Documento</th><th>Enviado</th><th>Estado</th><th></th></tr></thead>
            <tbody>
              @for (r of sent; track r.id) {
                <tr>
                  <td><a [routerLink]="['/pacientes', r.patientId]" [queryParams]="{ tab: 'archivos' }">{{ r.patientName }}</a></td>
                  <td class="small">{{ r.title.replace('Consentimiento informado · ', '') }}</td>
                  <td class="small">{{ dt(r.createdAt) }}<div class="muted">{{ r.channel === 'PRESENCIAL' ? 'En la clínica' : r.channel === 'WHATSAPP' ? 'WhatsApp' : 'Correo' }} · {{ r.createdBy }}</div></td>
                  <td>@if (r.status === 'FIRMADO') { <span class="tag green">Firmado</span><div class="muted small">{{ dt(r.signedAt) }}</div> }
                    @else if (r.status === 'ANULADO') { <span class="tag grey">Anulado</span> }
                    @else if (r.expired) { <span class="tag gum">Venció</span> }
                    @else { <span class="tag amber">{{ r.openedAt ? 'Abierto' : 'Por firmar' }}</span> }</td>
                  <td class="right">@if (r.fileId) { <button class="btn sm ghost" type="button" (click)="openFile(r.fileId)">Ver PDF</button> }</td>
                </tr>
              } @empty { <tr><td colspan="5" class="empty">No hay consentimientos con este filtro.</td></tr> }
            </tbody>
          </table>
        </section>
      }

      @if (view === 'plantillas') {
        <div class="ed">
          <aside class="panel">
            @for (t of templates; track t.id) {
              <button type="button" class="ti" [class.on]="cur?.id === t.id" (click)="pick(t)"><strong>{{ t.name }}</strong>
                <span class="muted small">v{{ t.version }} · {{ t.signed }} firmados{{ t.active ? '' : ' · desactivada' }}</span></button>
            }
            <button type="button" class="ti new" (click)="pick(null)">+ Nueva plantilla</button>
          </aside>
          <section class="panel">
            @if (cur) {
              <div class="panel-body grid">
                <div class="field"><label class="req" for="tn">Nombre</label><input id="tn" class="input" [(ngModel)]="cur.name" maxlength="120" [class.invalid]="e['name']" />@if (e['name']) { <span class="err">{{ e['name'] }}</span> }</div>
                <div class="field"><label class="req" for="tb">Texto</label>
                  <textarea id="tb" class="input" rows="18" [(ngModel)]="cur.body" maxlength="8000" [class.invalid]="e['body']"></textarea>
                  <div class="vars">@for (v of vars; track v) { <button type="button" (click)="cur.body = (cur.body || '') + '{' + v + '}'">{{ '{' + v + '}' }}</button> }</div>
                  @if (e['body']) { <span class="err">{{ e['body'] }}</span> }
                  <span class="muted small">Los consentimientos ya enviados conservan el texto con el que se enviaron. Guardar un cambio crea una nueva versión.</span>
                </div>
                @if (cur.id) { <label class="check"><input type="checkbox" [(ngModel)]="cur.active" /> Disponible para enviar</label> }
                <div class="row"><button class="btn primary" type="button" (click)="save()">Guardar</button>@if (cur.id) { <app-delete-button entity="consent_templates" [recordId]="cur.id" what="Plantilla de consentimiento" [small]="false" (deleted)="cur = null; loadTemplates()" /> }</div>
              </div>
            } @else { <p class="empty">Elige una plantilla para editarla.</p> }
          </section>
        </div>
      }
    </div>
  `,
  styles: [`
    .st { margin-bottom: 12px; }
    .ed { display: grid; grid-template-columns: 280px 1fr; gap: 16px; align-items: start; }
    @media (max-width: 900px) { .ed { grid-template-columns: 1fr; } }
    .ti { display: grid; gap: 2px; width: 100%; text-align: left; padding: 10px 14px; border: 0; border-bottom: 1px solid var(--line); background: none; font: inherit; cursor: pointer; }
    .ti.on { background: var(--teal-soft); } .ti.new { color: var(--teal-strong); font-weight: 700; }
    .vars { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 6px; }
    .vars button { border: 1px solid var(--line-strong); background: var(--surface); border-radius: 999px; padding: 2px 8px; font-family: ui-monospace, monospace; font-size: 11.5px; cursor: pointer; }
    textarea { font-size: 14px; line-height: 1.5; } .err { color: var(--gum); font-size: 12.5px; }
  `],
})
export class ConsentsComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  auth = inject(AuthService);
  view = 'enviados';
  status = '';
  statuses: [string, string][] = [['', 'Últimos 90 días'], ['PENDIENTE', 'Por firmar'], ['FIRMADO', 'Firmados'], ['ANULADO', 'Anulados']];
  sent: any[] = [];
  templates: any[] = [];
  cur: any = null;
  e: Record<string, string> = {};
  vars = ['firmante', 'documento_firmante', 'condicion', 'paciente', 'documento', 'odontologo', 'procedimiento', 'pieza', 'clinica', 'fecha'];
  ngOnInit() { this.loadSent(); if (this.auth.can('CONSENTIMIENTOS')) this.loadTemplates(); }
  loadSent() { this.api.informedConsents(this.status || undefined).subscribe((l) => (this.sent = l)); }
  loadTemplates(keep?: number) { this.api.consentTemplates(true).subscribe((t) => { this.templates = t; if (keep) this.pick(t.find((x) => x.id === keep)); }); }
  pick(t: any) { this.e = {}; this.cur = t ? { ...t, active: !!t.active } : { id: null, name: '', body: '', active: true }; }
  save() {
    this.e = {};
    this.api.saveConsentTemplate(this.cur.id, { name: this.cur.name, body: this.cur.body, active: this.cur.active }).subscribe({
      next: (t) => { this.toast.ok('Plantilla guardada.'); this.loadTemplates(t.id); },
      error: (err) => { const a = apiError(err); this.e = a.fields || { name: a.message }; },
    });
  }
  openFile(id: number) { this.api.fileContent(id).subscribe((b) => window.open(URL.createObjectURL(b), '_blank')); }
  dt = dateTime;
}
