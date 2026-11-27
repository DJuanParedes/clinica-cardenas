import { Component, Input, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { apiError, dateTime } from '../core/format';
import { ToastService } from '../core/toast.service';
import { LegalModalComponent } from './legal-modal.component';
import { CLINIC, esc, openPrintWindow, paragraphs, writePrintDoc } from '../core/print-doc';

/** v2.7: consentimiento aparte para recibir promociones (campañas y cumpleaños). */
@Component({
  selector: 'app-marketing-consent',
  standalone: true,
  imports: [FormsModule, LegalModalComponent],
  template: `
    <section class="panel">
      <div class="panel-head"><h3>Promociones y novedades</h3>
        <span class="tag plain" [class.green]="state === true" [class.gum]="state === false">{{ state === true ? 'Acepta' : state === false ? 'No acepta' : 'Sin preguntar' }}</span></div>
      <div class="panel-body small">
        <p class="muted">
          @if (state === true) { Recibe campañas y saludos de cumpleaños{{ at ? ' (desde el ' + dt(at) + ')' : '' }}. Puede darse de baja respondiendo BAJA. }
          @else if (state === false) { No se le envían campañas ni saludos{{ at ? ' (desde el ' + dt(at) + ')' : '' }}. Sus avisos de citas siguen llegando. }
          @else { Aún no se le preguntó. Recibe saludos de cumpleaños, pero no campañas. }
        </p>
        @if (auth.can('PACIENTES_EDITAR')) {
          <div class="row wrap">
            <select class="input sm" [(ngModel)]="channel" aria-label="Cómo lo indicó" title="Por dónde dio el paciente su respuesta; queda registrado como prueba del consentimiento"><option value="PRESENCIAL">En la clínica</option><option value="TELEFONO">Por teléfono</option><option value="WHATSAPP">Por WhatsApp</option></select>
            @if (state !== true) { <button class="btn sm" type="button" (click)="legal = true">Registrar que acepta</button> }
            @if (state !== false) { <button class="btn sm ghost" type="button" (click)="set(false)">Registrar que no acepta</button> }
            <button class="btn sm ghost" type="button" (click)="print()" title="Constancia para firmar y archivar">Imprimir constancia</button>
          </div>
        }
      </div>
    </section>
    <app-legal-modal [open]="legal" code="PUBLICIDAD" [who]="patient?.fullName || ''" (accepted)="legal = false; set(true)" (closed)="legal = false" />
  `,
  styles: [`.row { gap: 8px; align-items: center; } .input.sm { width: auto; height: 30px; padding: 0 8px; }`],
})
export class MarketingConsentComponent {
  @Input() set patient(p: any) { this._p = p; this.state = p?.marketingConsent ?? null; this.at = p?.marketingConsentAt ?? null; }
  get patient() { return this._p; }
  private _p: any;
  private api = inject(Api);
  private toast = inject(ToastService);
  auth = inject(AuthService);
  state: boolean | null = null;
  at: string | null = null;
  channel = 'PRESENCIAL';
  legal = false;
  set(accepted: boolean) {
    this.api.setMarketingConsent(this._p.id, accepted, this.channel).subscribe({
      next: (p) => { this.state = p.marketingConsent; this.at = p.marketingConsentAt; this.toast.ok(accepted ? 'Registrado: acepta recibir promociones.' : 'Registrado: no recibirá promociones.'); },
      error: (e) => this.toast.error(apiError(e).message),
    });
  }
  dt = dateTime;

  /** Constancia del consentimiento de promociones (Ley 29733: finalidad adicional, aceptación aparte). */
  print() {
    const p = this._p;
    if (!p) return;
    const w = openPrintWindow();
    if (!w) { this.toast.error('El navegador bloqueó la ventana. Permite ventanas emergentes para imprimir.'); return; }
    const via: Record<string, string> = { PRESENCIAL: 'En la clínica', TELEFONO: 'Por teléfono', WHATSAPP: 'Por WhatsApp' };
    const today = new Date().toLocaleDateString('es-PE', { day: '2-digit', month: 'long', year: 'numeric' });
    const name = esc(p.fullName || `${p.firstName ?? ''} ${p.lastName ?? ''}`);
    this.api.legal('PUBLICIDAD').subscribe({
      next: (doc: any) => writePrintDoc(w, {
        title: 'Consentimiento para envío de promociones y novedades',
        subtitle: 'Finalidad adicional según la Ley N.° 29733',
        code: 'CDC-PUBL',
        body: `
<h2>1. Datos del paciente</h2>
<table><tr><td class="k">Paciente</td><td>${name}</td></tr>
<tr><td class="k">Documento de identidad</td><td>${esc(p.docType)} ${esc(p.docNumber)}</td></tr>
<tr><td class="k">Celular / correo</td><td>${esc(p.phone)}${p.email ? ' · ' + esc(p.email) : ''}</td></tr>
<tr><td class="k">Estado registrado</td><td>${this.state === true ? 'Acepta' : this.state === false ? 'No acepta' : 'Sin respuesta'}${this.at ? ' · desde el ' + esc(this.dt(this.at)) : ''}</td></tr>
</table>
<h2>2. Texto informado al paciente</h2>${paragraphs(doc.body)}
<h2>3. Decisión del paciente</h2>
<table><tr><td class="k">Marque una opción</td><td>☐ Sí acepto recibir promociones, campañas y saludos &nbsp;&nbsp; ☐ No acepto</td></tr>
<tr><td class="k">Medio por el que respondió</td><td>${esc(via[this.channel] || this.channel)}</td></tr></table>
<p>Esta autorización es voluntaria: no aceptarla no afecta la atención ni los avisos de citas. Puede retirarla en cualquier momento respondiendo BAJA o en recepción.</p>
<p>${CLINIC.city}, ${today}.</p>
<div class="firmas"><div class="f">Firma del paciente o representante<br>${name}<br>${esc(p.docType)} ${esc(p.docNumber)}</div><div class="f">Firma y sello de la clínica<br>Recibido por</div></div>`,
      }),
      error: () => { w.close(); this.toast.error('No se pudo cargar el texto vigente.'); },
    });
  }
}
