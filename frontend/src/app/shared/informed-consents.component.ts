import { Component, Input, OnChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import qrcode from 'qrcode-generator';
import { Api } from '../core/api.service';
import { apiError, dateTime } from '../core/format';
import { ToastService } from '../core/toast.service';
import { ModalComponent } from './modal.component';

/** v2.8: consentimientos informados del paciente: enviar para firmar a distancia o en la tablet, y su estado. */
@Component({
  selector: 'app-informed-consents',
  standalone: true,
  imports: [FormsModule, ModalComponent],
  template: `
    <section class="panel">
      <div class="panel-head"><h3>Consentimientos informados</h3><button class="btn sm" type="button" (click)="openSend()">+ Enviar para firmar</button></div>
      <table class="table">
        <tbody>
          @for (r of list; track r.id) {
            <tr>
              <td><strong class="small">{{ r.title.replace('Consentimiento informado · ', '') }}</strong>
                <div class="muted small">{{ dt(r.createdAt) }} · {{ r.channel === 'PRESENCIAL' ? 'en la clínica' : r.channel === 'WHATSAPP' ? 'por WhatsApp' : 'por correo' }} · {{ r.createdBy }}</div></td>
              <td class="small">
                @if (r.status === 'FIRMADO') { <span class="tag green">Firmado</span><div class="muted">{{ dt(r.signedAt) }} · {{ r.signerName }}{{ r.signerRelationship && r.signerRelationship !== 'PACIENTE' ? ' (' + rel[r.signerRelationship] + ')' : '' }}</div> }
                @else if (r.status === 'ANULADO') { <span class="tag grey">Anulado</span><div class="muted">{{ r.cancelReason }}</div> }
                @else if (r.expired) { <span class="tag gum">Venció sin firmar</span> }
                @else { <span class="tag amber">{{ r.openedAt ? 'Abierto, sin firmar' : 'Por firmar' }}</span><div class="muted">vence {{ dt(r.expiresAt) }}</div> }
              </td>
              <td class="right nowrap">
                @if (r.status === 'FIRMADO' && r.fileId) { <button class="btn sm ghost" type="button" (click)="openFile(r.fileId)">Ver PDF</button> }
                @if (r.url) {
                  <button class="btn sm ghost" type="button" (click)="shown = r">Enlace / QR</button>
                  <button class="btn sm ghost" type="button" (click)="cancel(r)">Anular</button>
                }
              </td>
            </tr>
          } @empty { <tr><td class="muted small">Aún no se le envió ningún consentimiento para firmar.</td></tr> }
        </tbody>
      </table>
    </section>

    <app-modal [open]="sendOpen" title="Enviar consentimiento para firmar" [subtitle]="patientName" [width]="620" (close)="sendOpen = false">
      <form class="grid" id="icf" (ngSubmit)="send()">
        <div class="field"><label class="req" for="it">Consentimiento</label>
          <select id="it" name="it" class="input" [(ngModel)]="f.templateId" [class.invalid]="e['templateId']"><option [ngValue]="null">Elige…</option>@for (t of templates; track t.id) { <option [ngValue]="t.id">{{ t.name }}</option> }</select>
          @if (e['templateId']) { <span class="err">{{ e['templateId'] }}</span> }</div>
        <div class="field"><label class="req" for="ip">Procedimiento (aparece en el texto)</label>
          <input id="ip" name="ip" class="input" [(ngModel)]="f.procedure" maxlength="300" placeholder="Ej.: endodoncia multirradicular" [class.invalid]="e['procedure']" />
          @if (e['procedure']) { <span class="err">{{ e['procedure'] }}</span> }</div>
        <div class="cols-2">
          <div class="field"><label for="ipz">Pieza (opcional)</label><input id="ipz" name="ipz" class="input" type="number" [(ngModel)]="f.tooth" placeholder="36" [class.invalid]="e['tooth']" /></div>
          <div class="field"><label for="id">Odontólogo</label><select id="id" name="id" class="input" [(ngModel)]="f.dentistId"><option [ngValue]="null">—</option>@for (d of dentists; track d.id) { <option [ngValue]="d.id">{{ d.fullName }}</option> }</select></div>
        </div>
        <div class="field"><label>¿Cómo lo firmará?</label>
          <div class="seg">
            <button type="button" [class.on]="f.channel === 'WHATSAPP'" (click)="f.channel = 'WHATSAPP'">Enlace por WhatsApp</button>
            <button type="button" [class.on]="f.channel === 'EMAIL'" (click)="f.channel = 'EMAIL'">Por correo</button>
            <button type="button" [class.on]="f.channel === 'PRESENCIAL'" (click)="f.channel = 'PRESENCIAL'">Aquí, en la tablet</button>
          </div>
          @if (e['channel']) { <span class="err">{{ e['channel'] }}</span> }
          <span class="muted small">El enlace vence en 7 días. Si es menor de edad, firma su apoderado.</span>
        </div>
        @if (error) { <div class="err">{{ error }}</div> }
      </form>
      <div modal-actions><button class="btn" type="button" (click)="sendOpen = false">Cancelar</button><button class="btn primary" type="submit" form="icf" [disabled]="busy">{{ f.channel === 'PRESENCIAL' ? 'Preparar para firmar' : 'Enviar' }}</button></div>
    </app-modal>

    <app-modal [open]="!!shown" title="Firmar el consentimiento" [subtitle]="shown?.title" [width]="460" (close)="shown = null">
      @if (shown) {
        <div class="qr" [innerHTML]="qr(shown.url)"></div>
        <p class="small center">Escanéalo con la tablet o el celular del paciente, o ábrelo aquí:</p>
        <div class="row center-row"><a class="btn primary" [href]="shown.url" target="_blank" rel="noopener">Abrir para firmar</a><button class="btn" type="button" (click)="copy(shown.url)">Copiar enlace</button></div>
      }
    </app-modal>
  `,
  styles: [`
    .qr { width: 220px; margin: 0 auto; } .center { text-align: center; } .center-row { justify-content: center; gap: 8px; }
    .nowrap { white-space: nowrap; } .err { color: var(--gum); font-size: 12.5px; }
    .seg { flex-wrap: wrap; }
  `],
})
export class InformedConsentsComponent implements OnChanges {
  @Input() patientId!: number;
  @Input() patientName = '';
  private api = inject(Api);
  private toast = inject(ToastService);
  private sanitizer = inject(DomSanitizer);
  list: any[] = [];
  templates: any[] = [];
  dentists: any[] = [];
  sendOpen = false;
  f: any = {};
  e: Record<string, string> = {};
  error = '';
  busy = false;
  shown: any = null;
  rel: Record<string, string> = { MADRE: 'madre', PADRE: 'padre', TUTOR: 'tutor' };
  private qrs = new Map<string, SafeHtml>();
  ngOnChanges() { this.load(); }
  load() { this.api.patientInformedConsents(this.patientId).subscribe((l) => (this.list = l)); }
  openSend() {
    this.e = {}; this.error = '';
    this.f = { templateId: null, procedure: '', tooth: null, dentistId: null, channel: 'WHATSAPP' };
    this.sendOpen = true;
    if (!this.templates.length) this.api.consentTemplates().subscribe((t) => (this.templates = t));
    if (!this.dentists.length) this.api.dentists().subscribe((d) => (this.dentists = d));
  }
  send() {
    this.busy = true; this.e = {}; this.error = '';
    this.api.sendInformedConsent(this.patientId, { ...this.f, tooth: this.f.tooth || null }).subscribe({
      next: (r) => {
        this.busy = false; this.sendOpen = false; this.load();
        if (r.channel === 'PRESENCIAL') this.shown = r;
        else this.toast.ok('Enlace enviado ' + (r.channel === 'WHATSAPP' ? 'por WhatsApp' : 'por correo') + '. Lo verás como firmado cuando lo firme.');
      },
      error: (err) => { this.busy = false; const a = apiError(err); this.e = a.fields || {}; this.error = a.fields ? '' : a.message; },
    });
  }
  cancel(r: any) {
    const reason = prompt('¿Por qué se anula? (el enlace dejará de funcionar)');
    if (!reason) return;
    this.api.cancelInformedConsent(r.id, reason).subscribe({ next: () => { this.toast.ok('Consentimiento anulado.'); this.load(); }, error: (e) => this.toast.error(apiError(e).message) });
  }
  openFile(id: number) { this.api.fileContent(id).subscribe((b) => window.open(URL.createObjectURL(b), '_blank')); }
  copy(u: string) { navigator.clipboard?.writeText(u).then(() => this.toast.ok('Enlace copiado.')); }
  qr(u: string): SafeHtml {
    if (!this.qrs.has(u)) {
      const q = (qrcode as any)(0, 'M'); q.addData(u); q.make();
      this.qrs.set(u, this.sanitizer.bypassSecurityTrustHtml(q.createSvgTag({ cellSize: 4, margin: 1, scalable: true })));
    }
    return this.qrs.get(u)!;
  }
  dt = dateTime;
}
