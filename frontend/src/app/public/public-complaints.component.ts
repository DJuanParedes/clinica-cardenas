import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { apiError, dateTime, saveBlob, shortDate } from '../core/format';
import { LogoComponent } from '../shared/logo.component';
import { PUBLIC_STYLES } from './public-styles';

/**
 * v2.9: Libro de Reclamaciones virtual (sin iniciar sesión). Campos del Anexo I del DS 011-2011-PCM.
 * No se pide aceptar la política de privacidad para registrar: el aviso es solo informativo.
 */
@Component({
  selector: 'app-public-complaints',
  standalone: true,
  imports: [FormsModule, RouterLink, LogoComponent],
  template: `
    <header class="top"><app-logo [size]="44" />
      <div><div class="name">{{ info?.tradeName || 'Clínica Dental' }}</div><div class="addr">Libro de Reclamaciones</div></div>
      <img class="lr" src="libro-reclamaciones.svg" alt="" width="58" height="58" />
    </header>
    <main>
      <nav class="mode"><button type="button" [class.on]="mode === 'nuevo'" (click)="mode = 'nuevo'">Registrar</button>
        <button type="button" [class.on]="mode === 'consultar'" (click)="mode = 'consultar'">Consultar mi hoja</button></nav>

      @if (mode === 'consultar') {
        <div class="card">
          <h1>Consulta tu reclamo o queja</h1>
          <p class="lead">Usa el código de 8 letras que te dimos al registrarla y tu número de documento.</p>
          <div class="grid2">
            <label>Código<input class="input" [(ngModel)]="lk.code" maxlength="8" autocapitalize="characters" /></label>
            <label>N.° de documento<input class="input" [(ngModel)]="lk.doc" maxlength="15" inputmode="numeric" /></label>
          </div>
          @if (lkError) { <div class="err">{{ lkError }}</div> }
          <button class="btn primary block" type="button" [disabled]="!lk.code || !lk.doc || busy" (click)="lookup()">Consultar</button>
          @if (found) {
            <div class="sum">
              <b>Hoja N.° {{ found.number }}</b> · {{ found.kind === 'RECLAMO' ? 'Reclamo' : 'Queja' }} del {{ dt(found.filedAt) }}<br />
              Estado: <b>{{ found.status === 'RESPONDIDO' ? 'Respondido' : found.status === 'EN_PROCESO' ? 'En revisión' : 'Recibido' }}</b>
              @if (found.status !== 'RESPONDIDO') { · respuesta a más tardar el {{ sd(found.dueDate) }} }
            </div>
            @if (found.response) { <div class="notice"><b>Respuesta ({{ dt(found.respondedAt) }}):</b><br /><span class="pre">{{ found.response }}</span></div> }
            <button class="btn ghost" type="button" (click)="pdf(found.code, lk.doc)">Descargar hoja en PDF</button>
          }
        </div>
      } @else if (done) {
        <div class="card done">
          <div class="big">📘</div>
          <h1>Registramos tu {{ done.kind === 'RECLAMO' ? 'reclamo' : 'queja' }}</h1>
          <p class="lead">Hoja N.° <b>{{ done.number }}</b> · Código de consulta <b class="code">{{ done.code }}</b></p>
          <p>Te responderemos a más tardar el <b>{{ sd(done.dueDate) }}</b>.
            @if (done.copySent) { Enviamos una copia a <b>{{ done.email }}</b>. } @else { Guarda el código o descarga tu copia. }</p>
          <button class="btn primary" type="button" (click)="pdf(done.code, f.docNumber)">Descargar copia en PDF</button>
          <button class="back" type="button" (click)="reset()">Registrar otra hoja</button>
        </div>
      } @else {
        <div class="card">
          <h1>Hoja de reclamación virtual</h1>
          @if (info) {
            <div class="prov"><b>{{ info.businessName }}</b> · RUC {{ info.ruc }}<br />{{ info.address }}<br />
              <span class="muted">Fecha: {{ sd(info.today) }} · Hoja N.° {{ info.nextNumber }} (se confirma al enviar)</span></div>
          }

          <h2>1. Tus datos</h2>
          <div class="grid2">
            <label class="span2">Nombres y apellidos<input class="input" [(ngModel)]="f.fullName" maxlength="150" autocomplete="name" [class.invalid]="e['fullName']" />
              @if (e['fullName']) { <span class="err">{{ e['fullName'] }}</span> }</label>
            <label>Tipo de documento<select class="input" [(ngModel)]="f.docType">
              <option value="DNI">DNI</option><option value="CE">Carné de extranjería</option><option value="PASAPORTE">Pasaporte</option><option value="RUC">RUC</option></select></label>
            <label>N.° de documento<input class="input" [(ngModel)]="f.docNumber" maxlength="15" inputmode="numeric" [class.invalid]="e['docNumber']" />
              @if (e['docNumber']) { <span class="err">{{ e['docNumber'] }}</span> }</label>
            <label class="span2">Domicilio<input class="input" [(ngModel)]="f.address" maxlength="250" autocomplete="street-address" [class.invalid]="e['address']" />
              @if (e['address']) { <span class="err">{{ e['address'] }}</span> }</label>
            <label>Teléfono<input class="input" [(ngModel)]="f.phone" maxlength="20" inputmode="tel" autocomplete="tel" />
              @if (e['phone']) { <span class="err">{{ e['phone'] }}</span> }</label>
            <label>Correo (te enviamos la copia aquí)<input class="input" type="email" [(ngModel)]="f.email" maxlength="120" autocomplete="email" />
              @if (e['email']) { <span class="err">{{ e['email'] }}</span> }</label>
            <label class="chk span2"><input type="checkbox" [(ngModel)]="f.minor" /> Soy menor de edad (lo registra mi padre, madre o apoderado)</label>
            @if (f.minor) {
              <label>Nombre del padre, madre o apoderado<input class="input" [(ngModel)]="f.guardianName" maxlength="150" />
                @if (e['guardianName']) { <span class="err">{{ e['guardianName'] }}</span> }</label>
              <label>Su documento<input class="input" [(ngModel)]="f.guardianDoc" maxlength="12" />
                @if (e['guardianDoc']) { <span class="err">{{ e['guardianDoc'] }}</span> }</label>
            }
          </div>

          <h2>2. Servicio contratado</h2>
          <div class="grid2">
            <label>Servicio o tratamiento<input class="input" [(ngModel)]="f.service" maxlength="250" placeholder="Ej.: curación, ortodoncia, consulta" />
              @if (e['service']) { <span class="err">{{ e['service'] }}</span> }</label>
            <label>Monto reclamado (S/, opcional)<input class="input" type="number" min="0" step="0.01" [(ngModel)]="f.amount" /></label>
          </div>

          <h2>3. Detalle</h2>
          <div class="opts two">
            <button type="button" class="opt" [class.on]="f.kind === 'RECLAMO'" (click)="f.kind = 'RECLAMO'">
              <div><b>Reclamo</b><small>Disconformidad con el servicio recibido.</small></div></button>
            <button type="button" class="opt" [class.on]="f.kind === 'QUEJA'" (click)="f.kind = 'QUEJA'">
              <div><b>Queja</b><small>Malestar con la atención (no con el servicio en sí).</small></div></button>
          </div>
          @if (e['kind']) { <span class="err">{{ e['kind'] }}</span> }
          <label>¿Qué pasó?<textarea class="input" rows="5" [(ngModel)]="f.detail" maxlength="4000" [class.invalid]="e['detail']"></textarea>
            @if (e['detail']) { <span class="err">{{ e['detail'] }}</span> }</label>
          <label>¿Qué solicitas?<textarea class="input" rows="3" [(ngModel)]="f.request" maxlength="2000" [class.invalid]="e['request']"></textarea>
            @if (e['request']) { <span class="err">{{ e['request'] }}</span> }</label>

          <p class="small muted">Usaremos estos datos solo para atender tu reclamo o queja, según la Ley 29733 de Protección de Datos Personales.
            La formulación del reclamo no impide acudir a otras vías de solución ni es requisito para presentar una denuncia ante el INDECOPI o SUSALUD.
            Responderemos en un plazo no mayor a {{ info?.termDays || 15 }} días hábiles.</p>
          @if (error) { <div class="err">{{ error }}</div> }
          <button class="btn primary block" type="button" [disabled]="busy" (click)="send()">{{ busy ? 'Enviando…' : 'Enviar hoja de reclamación' }}</button>
        </div>
      }
      <p class="foot"><a routerLink="/reservar">Reservar una cita</a></p>
    </main>
  `,
  styles: [PUBLIC_STYLES + `
    .top .lr { margin-left: auto; }
    .mode { display: flex; gap: 6px; margin: 6px 0 12px; }
    .mode button { flex: 1; border: 1px solid var(--line, #e2e8f0); background: #fff; border-radius: 12px; padding: 9px; font: inherit; font-weight: 700; cursor: pointer; }
    .mode button.on { background: var(--teal-soft, #e6f7fb); border-color: var(--teal, #0aa5c9); color: var(--teal-strong); }
    h2 { font-size: 15px; margin: 8px 0 0; color: var(--teal-strong, #0b5cad); }
    label { display: grid; gap: 4px; font-size: 13.5px; font-weight: 600; }
    label.chk { display: flex; align-items: center; gap: 8px; font-weight: 500; }
    .prov { background: #f3f7f9; border-radius: 12px; padding: 10px 12px; font-size: 13.5px; }
    .opts.two { grid-template-columns: 1fr 1fr; }
    @media (max-width: 560px) { .opts.two { grid-template-columns: 1fr; } }
    .invalid { border-color: var(--gum, #c0264d) !important; }
    .code { letter-spacing: 2px; font-family: monospace; font-size: 17px; }
    .pre { white-space: pre-wrap; }
    .small { font-size: 12.5px; }
    .done .btn { justify-self: center; }
    .done .back { justify-self: center; }
  `],
})
export class PublicComplaintsComponent implements OnInit {
  private api = inject(Api);
  info: any = null;
  mode: 'nuevo' | 'consultar' = 'nuevo';
  f: any = this.blank();
  e: Record<string, string> = {};
  error = '';
  busy = false;
  done: any = null;
  lk = { code: '', doc: '' };
  lkError = '';
  found: any = null;

  ngOnInit() { this.api.complaintInfo().subscribe((i) => (this.info = i)); }

  blank() { return { kind: '', fullName: '', docType: 'DNI', docNumber: '', address: '', phone: '', email: '', minor: false, guardianName: '', guardianDoc: '', service: '', amount: null, detail: '', request: '' }; }

  send() {
    this.busy = true; this.error = ''; this.e = {};
    this.api.fileComplaint({ ...this.f, amount: this.f.amount === '' ? null : this.f.amount }).subscribe({
      next: (r) => { this.busy = false; this.done = r; window.scrollTo({ top: 0 }); },
      error: (err) => { this.busy = false; const a = apiError(err); this.error = a.message; this.e = (a as any).fields || {}; },
    });
  }

  reset() { this.done = null; this.f = this.blank(); this.api.complaintInfo().subscribe((i) => (this.info = i)); }

  lookup() {
    this.busy = true; this.lkError = ''; this.found = null;
    this.api.lookupComplaint(this.lk.code.trim().toUpperCase(), this.lk.doc.trim()).subscribe({
      next: (r) => { this.busy = false; this.found = r; },
      error: (err) => { this.busy = false; this.lkError = apiError(err).message; },
    });
  }

  pdf(code: string, doc: string) {
    this.api.publicComplaintPdf(code, doc).subscribe((b) => saveBlob(b, `hoja-reclamacion-${code}.pdf`));
  }

  dt = dateTime;
  sd = shortDate;
}
