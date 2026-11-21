import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { apiError, hhmm, longDay } from '../core/format';
import { LogoComponent } from '../shared/logo.component';
import { LegalModalComponent } from '../shared/legal-modal.component';
import { PUBLIC_STYLES } from './public-styles';

/** v2.6: enlace público para reservar cita sin cuenta (Instagram, Google, WhatsApp). */
@Component({
  selector: 'app-public-booking',
  standalone: true,
  imports: [FormsModule, RouterLink, LogoComponent, LegalModalComponent],
  template: `
    <header class="top"><app-logo [size]="44" /><div><div class="name">{{ o?.clinic?.name || 'Clínica Dental' }}</div><div class="addr">{{ o?.clinic?.address }}</div></div></header>
    <main>
      @if (!o) { <div class="card"><div class="skeleton" style="height: 200px"></div></div> }
      @else if (!o.enabled) {
        <div class="card done"><div class="big">📞</div><h1>Reserva por teléfono</h1><p class="lead">Por ahora no estamos recibiendo reservas por la web. Llámanos o escríbenos al <strong>{{ o.clinic.phone }}</strong>.</p></div>
      } @else if (done) {
        <div class="card done">
          <div class="big">✅</div>
          <h1>¡Listo, {{ done.firstName }}!</h1>
          <p class="lead">Tu cita quedó reservada para el <strong>{{ longDay(done.startAt) }} a las {{ hhmm(done.startAt) }}</strong> con {{ done.dentist }}.</p>
          <p>Te enviamos la confirmación por WhatsApp{{ done.emailSent ? ' y correo' : '' }}. Confírmala con el botón <strong>Confirmar</strong> del mensaje.</p>
          <p class="muted small">📍 {{ o.clinic.address }} · ¿Dudas? {{ o.clinic.phone }}</p>
        </div>
      } @else {
        <h1>Reserva tu cita</h1>
        <p class="lead">Elige el motivo, el horario y deja tus datos. Te enviamos la confirmación por WhatsApp.</p>
        <div class="steps"><span [class.on]="step >= 1"></span><span [class.on]="step >= 2"></span><span [class.on]="step >= 3"></span></div>

        @if (step === 1) {
          <section class="card">
            <label class="strong">¿Qué necesitas?</label>
            <div class="chips">@for (r of o.reasons; track r.code) { <button type="button" [class.on]="reason === r.code" (click)="reason = r.code; resetSlots()">{{ r.label }}</button> }</div>
            <label class="strong">¿Con quién?</label>
            <div class="opts">
              @for (d of o.dentists; track d.id) {
                <button type="button" class="opt" [class.on]="dentist?.id === d.id" (click)="dentist = d; resetSlots()">
                  @if (d.photoUrl) { <img [src]="d.photoUrl" [alt]="d.fullName" /> } @else { <span class="av">{{ d.fullName.split(' ')[1]?.[0] || 'D' }}</span> }
                  <span><strong>{{ d.fullName }}</strong><small>{{ d.specialty }}</small></span>
                </button>
              }
            </div>
            <button class="btn primary block" type="button" [disabled]="!reason || !dentist" (click)="step = 2">Ver horarios</button>
          </section>
        }

        @if (step === 2) {
          <section class="card">
            <button class="back" type="button" (click)="step = 1">← Cambiar motivo u odontólogo</button>
            <div><strong>{{ reasonLabel() }}</strong> · {{ dentist.fullName }}</div>
            <div class="field"><label for="dt">Fecha</label><input id="dt" class="input" type="date" [(ngModel)]="date" [min]="minDate" [max]="maxDate" (ngModelChange)="loadSlots()" /></div>
            @if (loading) { <p class="muted">Buscando horarios libres…</p> }
            @else if (date) {
              <div class="slots">@for (s of slots; track s) { <button type="button" [class.on]="startAt === s" (click)="startAt = s">{{ hhmm(s) }}</button> }
                @empty { <p class="muted span2">No hay horarios libres ese día. Prueba otra fecha.</p> }</div>
            }
            <button class="btn primary block" type="button" [disabled]="!startAt" (click)="step = 3">Continuar</button>
          </section>
        }

        @if (step === 3) {
          <form class="card" (ngSubmit)="book()">
            <button class="back" type="button" (click)="step = 2">← Cambiar horario</button>
            <div class="sum">📅 <strong>{{ longDay(startAt) }} a las {{ hhmm(startAt) }}</strong> · {{ dentist.fullName }} · {{ reasonLabel() }}</div>
            <p class="muted small">¿Ya eres paciente? Basta con tu documento y fecha de nacimiento; no cambiamos tu ficha.</p>
            <div class="grid2">
              <div class="field"><label class="req" for="dn">DNI</label><input id="dn" name="dn" class="input tabular" [(ngModel)]="p.docNumber" maxlength="8" inputmode="numeric" [class.invalid]="e['docNumber']" />
                @if (e['docNumber']) { <span class="err">{{ e['docNumber'] }}</span> }</div>
              <div class="field"><label class="req" for="bd">Fecha de nacimiento</label><input id="bd" name="bd" class="input" type="date" [(ngModel)]="p.birthDate" [max]="today" [class.invalid]="e['birthDate']" />
                @if (e['birthDate']) { <span class="err">{{ e['birthDate'] }}</span> }</div>
              <div class="field"><label for="fn">Nombres</label><input id="fn" name="fn" class="input" [(ngModel)]="p.firstName" maxlength="60" autocomplete="given-name" [class.invalid]="e['firstName']" />
                @if (e['firstName']) { <span class="err">{{ e['firstName'] }}</span> }</div>
              <div class="field"><label for="ln">Apellidos</label><input id="ln" name="ln" class="input" [(ngModel)]="p.lastName" maxlength="80" autocomplete="family-name" [class.invalid]="e['lastName']" />
                @if (e['lastName']) { <span class="err">{{ e['lastName'] }}</span> }</div>
              <div class="field"><label for="sx">Sexo</label><select id="sx" name="sx" class="input" [(ngModel)]="p.sex" [class.invalid]="e['sex']"><option value="">Elige…</option><option value="F">Femenino</option><option value="M">Masculino</option></select>
                @if (e['sex']) { <span class="err">{{ e['sex'] }}</span> }</div>
              <div class="field"><label class="req" for="ph">Celular (WhatsApp)</label><input id="ph" name="ph" class="input tabular" [(ngModel)]="p.phone" maxlength="9" inputmode="numeric" autocomplete="tel" [class.invalid]="e['phone']" />
                @if (e['phone']) { <span class="err">{{ e['phone'] }}</span> }</div>
              <div class="field span2"><label for="em">Correo (opcional)</label><input id="em" name="em" class="input" type="email" [(ngModel)]="p.email" maxlength="120" autocomplete="email" [class.invalid]="e['email']" />
                @if (e['email']) { <span class="err">{{ e['email'] }}</span> }</div>
              @if (minor) {
                <p class="span2 notice">Es menor de edad: completa los datos del padre, madre o tutor que lo acompañará.</p>
                <div class="field"><label class="req" for="gn">Nombre del apoderado</label><input id="gn" name="gn" class="input" [(ngModel)]="p.guardianName" maxlength="120" [class.invalid]="e['guardianName']" />@if (e['guardianName']) { <span class="err">{{ e['guardianName'] }}</span> }</div>
                <div class="field"><label class="req" for="gd">DNI del apoderado</label><input id="gd" name="gd" class="input tabular" [(ngModel)]="p.guardianDocNumber" maxlength="8" [class.invalid]="e['guardianDocNumber']" />@if (e['guardianDocNumber']) { <span class="err">{{ e['guardianDocNumber'] }}</span> }</div>
                <div class="field"><label class="req" for="gr">Vínculo</label><select id="gr" name="gr" class="input" [(ngModel)]="p.guardianRelationship" [class.invalid]="e['guardianRelationship']"><option value="">Elige…</option><option value="MADRE">Madre</option><option value="PADRE">Padre</option><option value="TUTOR">Tutor legal</option></select></div>
                <div class="field"><label class="req" for="gp">Celular del apoderado</label><input id="gp" name="gp" class="input tabular" [(ngModel)]="p.guardianPhone" maxlength="9" [class.invalid]="e['guardianPhone']" /></div>
              }
              <div class="field span2"><label for="nt">¿Algo que debamos saber? (opcional)</label><input id="nt" name="nt" class="input" [(ngModel)]="notes" maxlength="300" placeholder="Ej.: me duele una muela desde ayer" /></div>
            </div>
            <div class="consent" [class.ok]="p.dataConsent" [class.invalid]="e['dataConsent']">
              <input type="checkbox" id="cs" name="cs" [ngModel]="p.dataConsent" (click)="$event.preventDefault(); legalOpen = true" />
              <label for="cs">He leído y acepto la <a (click)="legalOpen = true">política de tratamiento de datos personales</a> (Ley N.° 29733).</label>
            </div>
            @if (e['dataConsent']) { <span class="err">{{ e['dataConsent'] }}</span> }
            <label class="consent opt"><input type="checkbox" name="mk" [(ngModel)]="p.marketingConsent" /> <span>Quiero recibir promociones y novedades de la clínica por WhatsApp o correo (opcional; puedo darme de baja respondiendo BAJA).</span></label>
            @if (error) { <div class="err">{{ error }}</div> }
            <button class="btn primary block" type="submit" [disabled]="busy">{{ busy ? 'Reservando…' : 'Reservar cita' }}</button>
          </form>
        }
        <p class="foot">¿Es tu primera vez? También puedes <a routerLink="/registro">llenar tu ficha antes de venir</a>.</p>
        <p class="foot lrb"><a routerLink="/libro-reclamaciones"><img src="libro-reclamaciones.svg" alt="" width="34" height="34" /> Libro de Reclamaciones</a></p>
      }
    </main>
    <app-legal-modal [open]="legalOpen" code="PRIVACIDAD" [who]="(p.firstName + ' ' + p.lastName).trim()" (accepted)="p.dataConsent = true; legalOpen = false" (closed)="legalOpen = false" />
  `,
  styles: [PUBLIC_STYLES + `.lrb a { display: inline-flex; align-items: center; gap: 8px; font-weight: 700; color: var(--teal-strong); }`],
})
export class PublicBookingComponent implements OnInit {
  private api = inject(Api);
  o: any = null;
  step = 1;
  reason = '';
  dentist: any = null;
  date = '';
  slots: string[] = [];
  startAt = '';
  loading = false;
  notes = '';
  p: any = { docType: 'DNI', docNumber: '', birthDate: '', firstName: '', lastName: '', sex: '', phone: '', email: '', dataConsent: false, marketingConsent: false, source: 'WEB' };
  e: Record<string, string> = {};
  error = '';
  busy = false;
  legalOpen = false;
  done: any = null;
  today = new Date().toLocaleDateString('en-CA');
  get minDate() { return this.today; }
  get maxDate() { const d = new Date(); d.setDate(d.getDate() + 60); return d.toLocaleDateString('en-CA'); }
  get minor() {
    if (!this.p.birthDate) return false;
    const b = new Date(this.p.birthDate + 'T00:00'), n = new Date();
    let a = n.getFullYear() - b.getFullYear(); if (n < new Date(n.getFullYear(), b.getMonth(), b.getDate())) a--;
    return a < 18;
  }

  ngOnInit() {
    const src = new URLSearchParams(location.search).get('origen');
    if (src) this.p.source = src.toUpperCase();
    this.api.publicBookingOptions().subscribe({ next: (o) => (this.o = o), error: () => (this.o = { enabled: false, clinic: {} }) });
  }
  reasonLabel() { return this.o?.reasons?.find((r: any) => r.code === this.reason)?.label; }
  resetSlots() { this.slots = []; this.startAt = ''; if (this.date) this.loadSlots(); }
  loadSlots() {
    this.slots = []; this.startAt = '';
    if (!this.date || !this.dentist || !this.reason) return;
    this.loading = true;
    this.api.publicSlots(this.dentist.id, this.date, this.reason).subscribe({ next: (r) => { this.slots = r.slots; this.loading = false; }, error: () => (this.loading = false) });
  }
  book() {
    this.e = {}; this.error = '';
    if (!this.p.dataConsent) { this.e = { dataConsent: 'Lee y acepta la política de privacidad para continuar.' }; return; }
    this.busy = true;
    this.api.publicBook({ person: this.p, dentistId: this.dentist.id, startAt: this.startAt, reasonCode: this.reason, notes: this.notes }).subscribe({
      next: (r) => { this.busy = false; this.done = r; window.scrollTo({ top: 0 }); },
      error: (err) => { this.busy = false; const a = apiError(err); this.e = a.fields || {}; this.error = a.message; if (this.e['startAt']) { this.step = 2; this.loadSlots(); } },
    });
  }
  hhmm = hhmm; longDay = longDay;
}
