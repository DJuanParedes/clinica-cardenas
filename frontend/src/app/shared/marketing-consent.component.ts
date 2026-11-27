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
            @if (state !== true) { <button class="btn sm" type="button" (click)="legal = true">Registrar que acepta</button> }
            @if (state !== false) { <button class="btn sm ghost" type="button" (click)="set(false)">Registrar que no acepta</button> }
            <button class="btn sm ghost" type="button" (click)="print()" title="Constancia para firmar y archivar">Imprimir constancia</button>
            <label class="btn sm ghost" [class.disabled]="uploading" title="PDF o foto de la constancia firmada por el paciente">{{ uploading ? 'Subiendo…' : signedAt ? 'Reemplazar constancia firmada' : 'Subir constancia firmada' }}
              <input type="file" accept="application/pdf,image/jpeg,image/png" hidden (change)="upload($event)" [disabled]="uploading" /></label>
          </div>
        }
        @if (signedAt) { <p class="small ok-line">✔ Constancia firmada adjunta el {{ dt(signedAt) }} (en Archivos del paciente).</p> }
      </div>
    </section>
    <app-legal-modal [open]="legal" code="PUBLICIDAD" [who]="patient?.fullName || ''" (accepted)="legal = false; set(true)" (closed)="legal = false" />
  `,
  styles: [`.row { gap: 8px; align-items: center; } .ok-line { color: var(--green, #1a7f4b); margin-top: 8px; } .disabled { opacity: .6; pointer-events: none; }`],
})
export class MarketingConsentComponent {
  @Input() set patient(p: any) { this._p = p; this.state = p?.marketingConsent ?? null; this.at = p?.marketingConsentAt ?? null; this.loadSigned(); }
  get patient() { return this._p; }
  private _p: any;
  private api = inject(Api);
  private toast = inject(ToastService);
  auth = inject(AuthService);
  state: boolean | null = null;
  at: string | null = null;
  channel = 'PRESENCIAL';
  uploading = false;
  signedAt: string | null = null;
  private static TITLE = 'Constancia de promociones firmada';
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
</table>
<p>Esta autorización es voluntaria: no aceptarla no afecta la atención ni los avisos de citas. Puede retirarla en cualquier momento respondiendo BAJA o en recepción.</p>
<p>${CLINIC.city}, ${today}.</p>
<div class="firmas"><div class="f">Firma del paciente o representante<br>${name}<br>${esc(p.docType)} ${esc(p.docNumber)}</div><div class="f">Firma y sello de la clínica<br>Recibido por</div></div>`,
      }),
      error: () => { w.close(); this.toast.error('No se pudo cargar el texto vigente.'); },
    });
  }

  private loadSigned() {
    this.signedAt = null;
    if (!this._p?.id) return;
    this.api.patientFiles(this._p.id).subscribe({
      next: (fs: any[]) => { const f = fs.find((x) => x.title === MarketingConsentComponent.TITLE); this.signedAt = f ? (f.createdAt || f.uploadedAt || null) : null; },
      error: () => {},
    });
  }

  /** Adjunta a la ficha la constancia firmada (queda en Archivos como consentimiento). */
  upload(ev: Event) {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || !this._p) return;
    if (file.size > 10 * 1024 * 1024) { this.toast.error('El archivo supera 10 MB.'); return; }
    this.uploading = true;
    this.api.uploadFile(this._p.id, file, file.name, { kind: 'CONSENTIMIENTO', title: MarketingConsentComponent.TITLE }).subscribe({
      next: (f: any) => { this.uploading = false; this.signedAt = f?.createdAt || new Date().toISOString(); this.toast.ok('Constancia firmada adjuntada a la ficha.'); },
      error: (e) => { this.uploading = false; this.toast.error(apiError(e).message); },
    });
  }
}
