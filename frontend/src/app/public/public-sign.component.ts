import { AfterViewChecked, Component, ElementRef, Input, OnInit, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api.service';
import { apiError, dateTime } from '../core/format';
import { LogoComponent } from '../shared/logo.component';
import { PUBLIC_STYLES } from './public-styles';

import { OnlyDirective } from '../shared/only.directive';
/** v2.8: firma a distancia de un consentimiento informado (enlace por WhatsApp o correo, o tablet de la clínica). */
@Component({
  selector: 'app-public-sign',
  standalone: true,
  imports: [OnlyDirective, FormsModule, LogoComponent],
  template: `
    <header class="top"><app-logo [size]="44" /><div><div class="name">{{ d?.clinic || 'Clínica Dental' }}</div><div class="addr">Consentimiento informado</div></div></header>
    <main>
      @if (error && !d) { <div class="card done"><div class="big">🔗</div><h1>Enlace no válido</h1><p class="lead">{{ error }}</p></div> }
      @else if (!d) { <div class="card"><div class="skeleton" style="height: 240px"></div></div> }
      @else if (done || d.status === 'FIRMADO') { <div class="card done"><div class="big">✅</div><h1>Consentimiento firmado</h1><p class="lead">{{ done?.message || 'Este documento ya fue firmado' + (d.signedAt ? ' el ' + dt(d.signedAt) : '') + '.' }}</p></div> }
      @else if (d.status === 'ANULADO') { <div class="card done"><div class="big">🚫</div><h1>Documento anulado</h1><p class="lead">La clínica anuló este envío. Si tienes dudas, comunícate con ellos.</p></div> }
      @else if (d.expired) { <div class="card done"><div class="big">⏰</div><h1>El enlace venció</h1><p class="lead">Pide a la clínica que te lo envíe de nuevo.</p></div> }
      @else {
        <h1>{{ d.title }}</h1>
        <p class="lead">Paciente: <strong>{{ d.patientName }}</strong>{{ d.minor ? ' (menor de edad: firma su madre, padre o tutor)' : '' }}</p>
        <div class="card doc">{{ d.body }}</div>
        <form class="card" (ngSubmit)="sign()">
          <div class="grid2">
            <div class="field"><label class="req" for="sn">Nombre completo de quien firma</label><input id="sn" name="sn" class="input" [(ngModel)]="f.signerName" maxlength="120" autocomplete="name" [class.invalid]="e['signerName']" appOnly="letters" />
              @if (e['signerName']) { <span class="err">{{ e['signerName'] }}</span> }</div>
            <div class="field"><label class="req" for="sd">DNI o carné de extranjería</label><input id="sd" name="sd" class="input tabular" [(ngModel)]="f.signerDoc" maxlength="12" appOnly="digits" inputmode="numeric" inputmode="numeric" [class.invalid]="e['signerDoc']" />
              @if (e['signerDoc']) { <span class="err">{{ e['signerDoc'] }}</span> }</div>
            @if (d.minor) {
              <div class="field span2"><label class="req" for="sr">Firmo como</label><select id="sr" name="sr" class="input" [(ngModel)]="f.relationship" [class.invalid]="e['relationship']">
                <option value="">Elige…</option><option value="MADRE">Madre</option><option value="PADRE">Padre</option><option value="TUTOR">Tutor(a) legal</option></select>
                @if (e['relationship']) { <span class="err">{{ e['relationship'] }}</span> }</div>
            }
          </div>
          <label class="consent" [class.ok]="f.accepted" [class.invalid]="e['accepted']"><input type="checkbox" name="ac" [(ngModel)]="f.accepted" />
            <span>Leí todo el documento, me explicaron el tratamiento y doy mi consentimiento.</span></label>
          @if (e['accepted']) { <span class="err">{{ e['accepted'] }}</span> }
          <div class="field"><label class="req">Firma aquí con tu dedo</label>
            <canvas #pad class="pad" [class.invalid]="e['signatureJpeg']" width="640" height="220" aria-label="Recuadro para firmar"
              (pointerdown)="down($event)" (pointermove)="move($event)" (pointerup)="drawing = false" (pointerleave)="drawing = false"></canvas>
            <div class="row"><button class="back" type="button" (click)="clear()">Borrar firma</button>@if (e['signatureJpeg']) { <span class="err">{{ e['signatureJpeg'] }}</span> }</div>
          </div>
          @if (error) { <div class="err">{{ error }}</div> }
          <button class="btn primary block" type="submit" [disabled]="busy">{{ busy ? 'Firmando…' : 'Firmar consentimiento' }}</button>
          <p class="muted small">Al firmar guardamos la fecha y hora, desde dónde firmaste y un código del texto ({{ d.hash }}…) para que nadie pueda cambiarlo después.</p>
        </form>
      }
    </main>
  `,
  styles: [PUBLIC_STYLES + `
    .doc { white-space: pre-wrap; font-size: 15px; line-height: 1.55; max-height: 55vh; overflow: auto; }
    .pad { width: 100%; height: auto; aspect-ratio: 640 / 220; border: 2px dashed var(--line, #cbd5e1); border-radius: 14px; background: #fff; touch-action: none; }
    .pad.invalid { border-color: var(--gum, #c0264d); }
    .row { display: flex; gap: 10px; align-items: center; }
    .consent input { margin-top: 3px; }
    form.card { margin-top: 14px; }
  `],
})
export class PublicSignComponent implements OnInit, AfterViewChecked {
  @Input() token = '';
  @ViewChild('pad') pad?: ElementRef<HTMLCanvasElement>;
  private api = inject(Api);
  d: any = null;
  f: any = { signerName: '', signerDoc: '', relationship: '', accepted: false };
  e: Record<string, string> = {};
  error = '';
  busy = false;
  done: any = null;
  drawing = false;
  signed = false;
  private ready = false;

  ngOnInit() {
    this.api.publicConsent(this.token).subscribe({
      next: (d) => { this.d = d; this.f.signerName = d.signerName || ''; this.f.signerDoc = d.signerDoc || ''; this.f.relationship = d.minor ? (d.relationship || '') : 'PACIENTE'; },
      error: (err) => (this.error = apiError(err).message),
    });
  }
  ngAfterViewChecked() {
    if (this.pad && !this.ready) {
      const g = this.pad.nativeElement.getContext('2d')!;
      g.fillStyle = '#fff'; g.fillRect(0, 0, 640, 220); g.lineWidth = 3; g.lineCap = 'round'; g.lineJoin = 'round'; g.strokeStyle = '#0b2540';
      this.ready = true;
    }
  }
  private point(e: PointerEvent) {
    const c = this.pad!.nativeElement, r = c.getBoundingClientRect();
    return { x: (e.clientX - r.left) * (c.width / r.width), y: (e.clientY - r.top) * (c.height / r.height) };
  }
  down(e: PointerEvent) {
    (e.target as HTMLElement).setPointerCapture?.(e.pointerId);
    const g = this.pad!.nativeElement.getContext('2d')!; const p = this.point(e);
    this.drawing = true; g.beginPath(); g.moveTo(p.x, p.y);
  }
  move(e: PointerEvent) {
    if (!this.drawing) return;
    const g = this.pad!.nativeElement.getContext('2d')!; const p = this.point(e);
    g.lineTo(p.x, p.y); g.stroke(); this.signed = true;
  }
  clear() { const g = this.pad!.nativeElement.getContext('2d')!; g.fillStyle = '#fff'; g.fillRect(0, 0, 640, 220); this.signed = false; }
  sign() {
    this.e = {}; this.error = '';
    if (!this.signed) { this.e = { signatureJpeg: 'Firma en el recuadro.' }; return; }
    this.busy = true;
    const signatureJpeg = this.pad!.nativeElement.toDataURL('image/jpeg', 0.85);
    this.api.signPublicConsent(this.token, { ...this.f, signatureJpeg }).subscribe({
      next: (r) => { this.busy = false; this.done = r; window.scrollTo({ top: 0 }); },
      error: (err) => { this.busy = false; const a = apiError(err); this.e = a.fields || {}; this.error = a.fields ? '' : a.message; },
    });
  }
  dt = dateTime;
}
