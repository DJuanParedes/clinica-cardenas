import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { apiError } from '../core/format';
import { LogoComponent } from '../shared/logo.component';
import { LegalModalComponent } from '../shared/legal-modal.component';
import { PUBLIC_STYLES } from './public-styles';

import { OnlyDirective } from '../shared/only.directive';
/** v2.6: prerregistro: el paciente nuevo llena su ficha y antecedentes antes de su primera cita. */
@Component({
  selector: 'app-public-register',
  standalone: true,
  imports: [OnlyDirective, FormsModule, RouterLink, LogoComponent, LegalModalComponent],
  template: `
    <header class="top"><app-logo [size]="44" /><div><div class="name">{{ f?.clinic?.name || 'Clínica Dental' }}</div><div class="addr">{{ f?.clinic?.address }}</div></div></header>
    <main>
      @if (!f) { <div class="card"><div class="skeleton" style="height: 200px"></div></div> }
      @else if (!f.enabled) { <div class="card done"><div class="big">🦷</div><h1>Te registramos en la clínica</h1><p class="lead">Por ahora el registro se hace en recepción. ¿Dudas? {{ f.clinic.phone }}</p></div> }
      @else if (done) {
        <div class="card done">
          <div class="big">✅</div><h1>¡Gracias, {{ done.firstName }}!</h1>
          <p class="lead">Tu ficha quedó registrada{{ done.hcNumber ? ' con el N° de historia ' + done.hcNumber : '' }}. Cuando vengas, solo dinos tu DNI.</p>
          <a class="btn primary" routerLink="/reservar">Reservar mi cita</a>
        </div>
      } @else {
        <h1>Llena tu ficha antes de venir</h1>
        <p class="lead">Así tu primera atención es más rápida. Tus datos de salud solo los ven los odontólogos.</p>
        <form class="card" (ngSubmit)="send()">
          <h3>Tus datos</h3>
          <div class="grid2">
            <div class="field"><label class="req" for="dn">DNI</label><input id="dn" name="dn" class="input tabular" [(ngModel)]="p.docNumber" maxlength="8" appOnly="digits" inputmode="numeric" [class.invalid]="e['docNumber']" />@if (e['docNumber']) { <span class="err">{{ e['docNumber'] }}</span> }</div>
            <div class="field"><label class="req" for="bd">Fecha de nacimiento</label><input id="bd" name="bd" type="date" class="input" [(ngModel)]="p.birthDate" [max]="today" [class.invalid]="e['birthDate']" />@if (e['birthDate']) { <span class="err">{{ e['birthDate'] }}</span> }</div>
            <div class="field"><label class="req" for="fn">Nombres</label><input id="fn" name="fn" class="input" [(ngModel)]="p.firstName" maxlength="60" [class.invalid]="e['firstName']" appOnly="letters" />@if (e['firstName']) { <span class="err">{{ e['firstName'] }}</span> }</div>
            <div class="field"><label class="req" for="ln">Apellidos</label><input id="ln" name="ln" class="input" [(ngModel)]="p.lastName" maxlength="80" [class.invalid]="e['lastName']" appOnly="letters" />@if (e['lastName']) { <span class="err">{{ e['lastName'] }}</span> }</div>
            <div class="field"><label class="req" for="sx">Sexo</label><select id="sx" name="sx" class="input" [(ngModel)]="p.sex" [class.invalid]="e['sex']"><option value="">Elige…</option><option value="F">Femenino</option><option value="M">Masculino</option></select>@if (e['sex']) { <span class="err">{{ e['sex'] }}</span> }</div>
            <div class="field"><label class="req" for="ph">Celular (WhatsApp)</label><input id="ph" name="ph" class="input tabular" [(ngModel)]="p.phone" maxlength="9" inputmode="numeric" [class.invalid]="e['phone']" appOnly="phone" />@if (e['phone']) { <span class="err">{{ e['phone'] }}</span> }</div>
            <div class="field"><label for="em">Correo</label><input id="em" name="em" type="email" class="input" [(ngModel)]="p.email" maxlength="120" [class.invalid]="e['email']" appOnly="email" />@if (e['email']) { <span class="err">{{ e['email'] }}</span> }</div>
            <div class="field"><label for="ad">Dirección</label><input id="ad" name="ad" class="input" [(ngModel)]="p.address" maxlength="200" appOnly="text" /></div>
            <div class="field"><label for="ec">Contacto de emergencia</label><input id="ec" name="ec" class="input" [(ngModel)]="p.emergencyContactName" maxlength="120" appOnly="letters" /></div>
            <div class="field"><label for="ep">Celular de emergencia</label><input id="ep" name="ep" class="input tabular" [(ngModel)]="p.emergencyContactPhone" maxlength="9" [class.invalid]="e['emergencyContactPhone']" appOnly="phone" /></div>
            @if (minor) {
              <p class="span2 notice">Es menor de edad: completa los datos del padre, madre o tutor.</p>
              <div class="field"><label class="req" for="gn">Nombre del apoderado</label><input id="gn" name="gn" class="input" [(ngModel)]="p.guardianName" maxlength="120" [class.invalid]="e['guardianName']" appOnly="letters" />@if (e['guardianName']) { <span class="err">{{ e['guardianName'] }}</span> }</div>
              <div class="field"><label class="req" for="gd">DNI del apoderado</label><input id="gd" name="gd" class="input tabular" [(ngModel)]="p.guardianDocNumber" maxlength="8" appOnly="digits" inputmode="numeric" [class.invalid]="e['guardianDocNumber']" /></div>
              <div class="field"><label class="req" for="gr">Vínculo</label><select id="gr" name="gr" class="input" [(ngModel)]="p.guardianRelationship"><option value="">Elige…</option><option value="MADRE">Madre</option><option value="PADRE">Padre</option><option value="TUTOR">Tutor legal</option></select></div>
              <div class="field"><label class="req" for="gp">Celular del apoderado</label><input id="gp" name="gp" class="input tabular" [(ngModel)]="p.guardianPhone" maxlength="9" appOnly="phone" /></div>
            }
            <div class="field span2"><label for="src">¿Cómo nos conociste?</label><select id="src" name="src" class="input" [(ngModel)]="p.source"><option value="">Prefiero no decir</option>@for (s of f.sources; track s.code) { <option [value]="s.code">{{ s.label }}</option> }</select></div>
          </div>
          <h3>Tu salud</h3>
          <div class="grid2">
            <div class="field"><label for="al">¿Alergias? (medicamentos, látex, anestesia…)</label><input id="al" name="al" class="input" [(ngModel)]="allergies" maxlength="500" placeholder="Ninguna" appOnly="text" /></div>
            <div class="field"><label for="md">¿Tomas algún medicamento?</label><input id="md" name="md" class="input" [(ngModel)]="medication" maxlength="500" placeholder="Ninguno" appOnly="text" /></div>
          </div>
          <p class="muted small">¿Tienes o has tenido…?</p>
          <div class="opts">
            @for (q of f.questions || []; track q.id) {
              <div class="opt yn"><span class="grow">{{ q.label }}</span>
                <div class="chips"><button type="button" [class.on]="ans[q.id]?.value === 'NO'" (click)="set(q.id, 'NO')">No</button><button type="button" [class.on]="ans[q.id]?.value === 'SI'" (click)="set(q.id, 'SI')">Sí</button></div>
              </div>
              @if (ans[q.id]?.value === 'SI') { <input class="input" [name]="'n' + q.id" [(ngModel)]="ans[q.id].note" placeholder="Detalle (desde cuándo, medicamento…)" maxlength="300" appOnly="text" /> }
            }
          </div>
          <div class="consent" [class.ok]="p.dataConsent" [class.invalid]="e['dataConsent']">
            <input type="checkbox" id="cs" name="cs" [ngModel]="p.dataConsent" (click)="$event.preventDefault(); legalOpen = true" />
            <label for="cs">He leído y acepto la <a (click)="legalOpen = true">política de tratamiento de datos personales</a> (Ley N.° 29733), incluidos mis datos de salud.</label>
          </div>
          @if (e['dataConsent']) { <span class="err">{{ e['dataConsent'] }}</span> }
          <label class="consent opt"><input type="checkbox" name="mk" [(ngModel)]="p.marketingConsent" /> <span>Quiero recibir promociones y novedades de la clínica por WhatsApp o correo (opcional; puedo darme de baja respondiendo BAJA).</span></label>
          @if (error) { <div class="err">{{ error }}</div> }
          <button class="btn primary block" type="submit" [disabled]="busy">{{ busy ? 'Enviando…' : 'Enviar mi ficha' }}</button>
        </form>
      }
    </main>
    <app-legal-modal [open]="legalOpen" code="PRIVACIDAD" [who]="minor ? (p.guardianName || 'apoderado') : (p.firstName + ' ' + p.lastName).trim()" (accepted)="p.dataConsent = true; legalOpen = false" (closed)="legalOpen = false" />
  `,
  styles: [PUBLIC_STYLES + `.opt.yn { cursor: default; } .grow { flex: 1; } h3 { margin: 4px 0 0; font-size: 16px; }`],
})
export class PublicRegisterComponent implements OnInit {
  private api = inject(Api);
  f: any = null;
  p: any = { docType: 'DNI', docNumber: '', birthDate: '', firstName: '', lastName: '', sex: '', phone: '', email: '', address: '', source: '', dataConsent: false, marketingConsent: false };
  ans: Record<string, { value?: string; note?: string }> = {};
  allergies = '';
  medication = '';
  e: Record<string, string> = {};
  error = '';
  busy = false;
  legalOpen = false;
  done: any = null;
  today = new Date().toLocaleDateString('en-CA');
  get minor() {
    if (!this.p.birthDate) return false;
    const b = new Date(this.p.birthDate + 'T00:00'), n = new Date();
    let a = n.getFullYear() - b.getFullYear(); if (n < new Date(n.getFullYear(), b.getMonth(), b.getDate())) a--;
    return a < 18;
  }
  ngOnInit() { this.api.publicPreregisterForm().subscribe({ next: (f) => (this.f = f), error: () => (this.f = { enabled: false, clinic: {} }) }); }
  set(id: string, v: string) { const a = this.ans[id] || {}; a.value = a.value === v ? undefined : v; this.ans[id] = a; }
  send() {
    this.e = {}; this.error = '';
    if (!this.p.dataConsent) { this.e = { dataConsent: 'Lee y acepta la política de privacidad para continuar.' }; return; }
    this.busy = true;
    const answers: any = {};
    for (const [k, v] of Object.entries(this.ans)) if (v.value) answers[k] = v;
    this.api.publicPreregister({ person: { ...this.p, source: this.p.source || null }, answers, allergies: this.allergies, medication: this.medication }).subscribe({
      next: (r) => { this.busy = false; this.done = r; window.scrollTo({ top: 0 }); },
      error: (err) => { this.busy = false; const a = apiError(err); this.e = a.fields || {}; this.error = a.message; },
    });
  }
}
