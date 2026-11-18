import { Component, Input, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, switchMap, of, catchError } from 'rxjs';
import { Api, UbigeoDep, UbigeoProv } from '../core/api.service';
import { LegalModalComponent } from '../shared/legal-modal.component';
import { ToastService } from '../core/toast.service';
import { DOC_TYPES, apiError, dateTime, isoDate } from '../core/format';
import { IconComponent } from '../shared/icon.component';

interface Form {
  docType: string; docNumber: string; firstName: string; lastName: string; birthDate: string; sex: string;
  phone: string; email: string; address: string; district: string; ubigeo: string;
  emergencyContactName: string; emergencyContactPhone: string; emergencyContactRelationship: string; notes: string; dataConsent: boolean;
  guardianName: string; guardianDocType: string; guardianDocNumber: string; guardianRelationship: string; guardianPhone: string;
}

/** HU-01 registro y HU-03 actualización de datos del paciente. */
@Component({
  selector: 'app-patient-form',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent, LegalModalComponent],
  template: `
    <div class="page narrow">
      <a class="back" [routerLink]="id ? ['/pacientes', id] : '/pacientes'"><app-icon name="chevronLeft" [size]="16" /> {{ id ? 'Volver a la ficha' : 'Pacientes' }}</a>
      <div class="page-head">
        <div>
          <h1>{{ id ? 'Editar datos del paciente' : 'Registrar paciente' }}</h1>
          @if (id && meta) {
            <p class="sub">Última modificación: {{ meta }}</p>
          } @else {
            <p class="sub">Los campos con * son obligatorios. El documento identifica al paciente de forma única.</p>
          }
        </div>
      </div>

      @if (duplicate) {
        <div class="notice amber dup" role="alert">
          <app-icon name="alert" />
          <div class="grow">Ya existe un paciente con este documento: <strong>{{ duplicate.fullName }}</strong>.</div>
          <a class="btn sm" [routerLink]="['/pacientes', duplicate.id]">Abrir su ficha</a>
        </div>
      }

      <form (ngSubmit)="save()" novalidate>
        <section class="panel">
          <div class="panel-head"><h3>Identificación</h3></div>
          <div class="panel-body grid cols-3">
            <div class="field">
              <label class="req" for="dt">Tipo de documento</label>
              <select id="dt" name="docType" class="input" [(ngModel)]="f.docType" (ngModelChange)="checkDoc()" [disabled]="!!id">
                @for (d of docTypes; track d.code) { <option [value]="d.code">{{ d.label }}</option> }
              </select>
            </div>
            <div class="field span-2">
              <label class="req" for="dn">Número de documento</label>
              <input id="dn" name="docNumber" class="input tabular" [class.invalid]="e['docNumber']" [(ngModel)]="f.docNumber"
                     (ngModelChange)="checkDoc()" [disabled]="!!id" [attr.maxlength]="f.docType === 'DNI' ? 8 : 12"
                     [attr.inputmode]="f.docType === 'DNI' ? 'numeric' : 'text'" autocomplete="off" />
              @if (e['docNumber']) { <span class="err">{{ e['docNumber'] }}</span> }
              @else if (id) { <span class="hint">El documento no se modifica después del registro.</span> }
              @else if (dniEnabled && f.docType === 'DNI') {
                <button type="button" class="btn sm lookup" (click)="lookup()" [disabled]="looking || !validDni">
                  <app-icon name="search" [size]="14" /> {{ looking ? 'Consultando RENIEC…' : 'Autocompletar con RENIEC' }}</button>
              }
            </div>
            <div class="field">
              <label class="req" for="fn">Nombres</label>
              <input id="fn" name="firstName" class="input" [class.invalid]="e['firstName']" [(ngModel)]="f.firstName" autocomplete="off" />
              @if (e['firstName']) { <span class="err">{{ e['firstName'] }}</span> }
            </div>
            <div class="field span-2">
              <label class="req" for="ln">Apellidos</label>
              <input id="ln" name="lastName" class="input" [class.invalid]="e['lastName']" [(ngModel)]="f.lastName" autocomplete="off" />
              @if (e['lastName']) { <span class="err">{{ e['lastName'] }}</span> }
            </div>
            <div class="field">
              <label class="req" for="bd">Fecha de nacimiento</label>
              <input id="bd" name="birthDate" type="date" class="input" [class.invalid]="e['birthDate']" [(ngModel)]="f.birthDate" [max]="today" />
              @if (e['birthDate']) { <span class="err">{{ e['birthDate'] }}</span> }
              @else if (age !== null) { <span class="hint">{{ age }} años{{ minor ? ' · menor de edad: se registra su representante legal' : '' }}</span> }
            </div>
            <div class="field">
              <span class="label req">Sexo</span>
              <div class="seg" role="radiogroup" aria-label="Sexo">
                <button type="button" [class.on]="f.sex === 'F'" (click)="f.sex = 'F'" role="radio" [attr.aria-checked]="f.sex === 'F'">Femenino</button>
                <button type="button" [class.on]="f.sex === 'M'" (click)="f.sex = 'M'" role="radio" [attr.aria-checked]="f.sex === 'M'">Masculino</button>
              </div>
              @if (e['sex']) { <span class="err">{{ e['sex'] }}</span> }
            </div>
          </div>
        </section>

        <section class="panel">
          <div class="panel-head"><h3>Contacto</h3></div>
          <div class="panel-body grid cols-3">
            <div class="field">
              <label class="req" for="ph">Celular o teléfono</label>
              <input id="ph" name="phone" class="input tabular" [class.invalid]="e['phone']" [(ngModel)]="f.phone" inputmode="tel" placeholder="987 654 321" />
              @if (e['phone']) { <span class="err">{{ e['phone'] }}</span> }
            </div>
            <div class="field span-2">
              <label for="em">Correo electrónico</label>
              <input id="em" name="email" type="email" class="input" [class.invalid]="e['email']" [(ngModel)]="f.email" placeholder="Para recordatorios de cita" />
              @if (e['email']) { <span class="err">{{ e['email'] }}</span> }
            </div>
            <div class="field span-2">
              <label for="ad">Dirección</label>
              <input id="ad" name="address" class="input" [(ngModel)]="f.address" />
            </div>
            <div class="field">
              <label for="dep">Departamento</label>
              <select id="dep" name="dep" class="input" [(ngModel)]="dep" (ngModelChange)="onDep()">
                <option value="">Seleccionar…</option>
                @for (d of deps; track d[0]) { <option [value]="d[0]">{{ d[1] }}</option> }
              </select>
            </div>
            <div class="field">
              <label for="prv">Provincia</label>
              <select id="prv" name="prv" class="input" [(ngModel)]="prov" (ngModelChange)="onProv()" [disabled]="!dep">
                <option value="">Seleccionar…</option>
                @for (p of provinces; track p[0]) { <option [value]="p[0]">{{ p[1] }}</option> }
              </select>
            </div>
            <div class="field">
              <label for="ds">Distrito</label>
              <select id="ds" name="ubigeo" class="input" [class.invalid]="e['ubigeo']" [(ngModel)]="f.ubigeo" [disabled]="!prov">
                <option value="">Seleccionar…</option>
                @for (d of districts; track d[0]) { <option [value]="d[0]">{{ d[1] }}</option> }
              </select>
              @if (e['ubigeo']) { <span class="err">{{ e['ubigeo'] }}</span> }
              @else if (f.ubigeo) { <span class="hint">UBIGEO {{ f.ubigeo }}</span> }
            </div>
          </div>
        </section>

        @if (minor) {
          <section class="panel guardian">
            <div class="panel-head"><h3>Representante legal del menor</h3><span class="tag amber">Obligatorio · menor de 18 años</span></div>
            <div class="panel-body grid cols-3">
              <p class="span-all muted small">Por ser menor de edad, quien decide sobre su atención y firma el consentimiento es su padre, madre o tutor legal
                (Código Civil, patria potestad y tutela). No se usa "contacto de emergencia".</p>
              <div class="field">
                <label class="req" for="grel">Vínculo</label>
                <select id="grel" name="grel" class="input" [class.invalid]="e['guardianRelationship']" [(ngModel)]="f.guardianRelationship">
                  <option value="">Seleccionar…</option><option value="MADRE">Madre</option><option value="PADRE">Padre</option><option value="TUTOR_LEGAL">Tutor legal</option>
                </select>
                @if (e['guardianRelationship']) { <span class="err">{{ e['guardianRelationship'] }}</span> }
              </div>
              <div class="field span-2">
                <label class="req" for="gname">Nombres y apellidos</label>
                <input id="gname" name="gname" class="input" [class.invalid]="e['guardianName']" [(ngModel)]="f.guardianName" />
                @if (e['guardianName']) { <span class="err">{{ e['guardianName'] }}</span> }
              </div>
              <div class="field">
                <label class="req" for="gdt">Documento</label>
                <select id="gdt" name="gdt" class="input" [(ngModel)]="f.guardianDocType">
                  @for (d of docTypes; track d.code) { <option [value]="d.code">{{ d.label }}</option> }
                </select>
              </div>
              <div class="field">
                <label class="req" for="gdn">Número</label>
                <input id="gdn" name="gdn" class="input tabular" [class.invalid]="e['guardianDocNumber']" [(ngModel)]="f.guardianDocNumber" [attr.maxlength]="f.guardianDocType === 'DNI' ? 8 : 12" />
                @if (e['guardianDocNumber']) { <span class="err">{{ e['guardianDocNumber'] }}</span> }
              </div>
              <div class="field">
                <label class="req" for="gph">Celular</label>
                <input id="gph" name="gph" class="input tabular" [class.invalid]="e['guardianPhone']" [(ngModel)]="f.guardianPhone" inputmode="tel" />
                @if (e['guardianPhone']) { <span class="err">{{ e['guardianPhone'] }}</span> }
              </div>
            </div>
          </section>
        }

        <section class="panel">
          <div class="panel-head"><h3>{{ minor ? 'Notas y consentimiento' : 'Contacto de emergencia, notas y consentimiento' }}</h3></div>
          <div class="panel-body grid cols-3">
            @if (!minor) {
              <div class="field">
                <label for="ecn">Contacto de emergencia</label>
                <input id="ecn" name="ecn" class="input" [(ngModel)]="f.emergencyContactName" placeholder="Nombre completo" />
              </div>
              <div class="field">
                <label for="ecr">Parentesco o relación</label>
                <select id="ecr" name="ecr" class="input" [(ngModel)]="f.emergencyContactRelationship">
                  <option value="">Seleccionar…</option>
                  @for (r of relations; track r) { <option [value]="r">{{ r }}</option> }
                </select>
              </div>
              <div class="field">
                <label for="ecp">Teléfono del contacto</label>
                <input id="ecp" name="ecp" class="input tabular" [class.invalid]="e['emergencyContactPhone']" [(ngModel)]="f.emergencyContactPhone" inputmode="tel" />
                @if (e['emergencyContactPhone']) { <span class="err">{{ e['emergencyContactPhone'] }}</span> }
              </div>
              <p class="span-all muted small">Persona a quien avisar si el paciente sufre una complicación durante la atención. Es opcional para adultos.</p>
            }
            <div class="field span-all">
              <label for="nt">Notas administrativas</label>
              <textarea id="nt" name="notes" class="input" [(ngModel)]="f.notes" maxlength="500" placeholder="Preferencias de horario, seguro, referido por…"></textarea>
            </div>
            <div class="field span-all">
              <div class="consent" [class.ok]="f.dataConsent" [class.invalid]="e['dataConsent']">
                <app-icon [name]="f.dataConsent ? 'check' : 'shield'" />
                <div class="grow">
                  @if (f.dataConsent) {
                    <strong>Política de privacidad aceptada{{ consentVersion ? ' (v' + consentVersion + ')' : '' }}</strong>
                    <div class="muted small">{{ minor ? 'Aceptó el representante legal.' : 'Aceptó el paciente.' }} Al guardar queda el registro con fecha, versión y usuario.
                      Imprime la política para su firma y luego adjunta la copia firmada en la ficha del paciente.</div>
                  } @else {
                    <strong>Consentimiento para el tratamiento de datos personales y de salud</strong>
                    <div class="muted small">Obligatorio (Ley N.° 29733): los datos de salud son sensibles y requieren consentimiento expreso
                      {{ minor ? 'del padre, madre o tutor legal' : 'del paciente' }}. Lean juntos la política completa.</div>
                  }
                  @if (e['dataConsent']) { <span class="err">{{ e['dataConsent'] }}</span> }
                </div>
                @if (!consentLocked) {
                  <button type="button" class="btn sm" [class.primary]="!f.dataConsent" (click)="legalOpen = true">{{ f.dataConsent ? 'Ver política' : 'Leer y aceptar' }}</button>
                }
              </div>
            </div>
          </div>
        </section>

        <app-legal-modal [open]="legalOpen" code="PRIVACIDAD" [who]="minor ? (f.guardianName || 'representante legal') : (f.firstName + ' ' + f.lastName).trim()"
          (accepted)="onAccepted($event)" (closed)="legalOpen = false" />

        <div class="formbar">
          <a class="btn" [routerLink]="id ? ['/pacientes', id] : '/pacientes'">Cancelar</a>
          <button class="btn primary" type="submit" [disabled]="saving || !!duplicate">
            @if (saving) { <span class="spin"></span> } {{ id ? 'Guardar cambios' : 'Registrar paciente' }}
          </button>
        </div>
      </form>
    </div>
  `,
  styles: [`
    .consent { display: flex; align-items: center; gap: 12px; line-height: 1.45; padding: 12px 14px; border-radius: 12px; background: var(--surface-2); border: 1px solid var(--line); }
    .consent.ok { background: var(--green-soft); border-color: var(--green); }
    .consent.invalid { border-color: var(--red, #c0392b); }
    .guardian { border: 1px solid var(--amber, #d4a017); }
    .guardian .panel-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
    .lookup { margin-top: 6px; align-self: flex-start; }
    .narrow { max-width: 980px; }
    form { display: flex; flex-direction: column; gap: 16px; }
    .dup { margin-bottom: 16px; align-items: center; }
    .formbar { position: sticky; bottom: 0; display: flex; justify-content: flex-end; gap: 8px; padding: 14px 0; background: linear-gradient(transparent, var(--porcelain) 30%); }
  `],
})
export class PatientFormComponent implements OnInit {
  private api = inject(Api);
  private router = inject(Router);
  private toast = inject(ToastService);

  @Input() id?: string;
  @Input() doc?: string;

  docTypes = DOC_TYPES;
  today = isoDate();
  f: Form = {
    docType: 'DNI', docNumber: '', firstName: '', lastName: '', birthDate: '', sex: '',
    phone: '', email: '', address: '', district: '', ubigeo: '', emergencyContactName: '', emergencyContactPhone: '', emergencyContactRelationship: '',
    notes: '', dataConsent: false, guardianName: '', guardianDocType: 'DNI', guardianDocNumber: '', guardianRelationship: '', guardianPhone: '',
  };
  relations = ['Cónyuge o pareja', 'Madre', 'Padre', 'Hijo(a)', 'Hermano(a)', 'Otro familiar', 'Amigo(a)'];
  deps: UbigeoDep[] = [];
  dep = '';
  prov = '';
  dniEnabled = false;
  looking = false;
  legalOpen = false;
  consentVersion = '';
  e: Record<string, string> = {};
  duplicate: { id: number; fullName: string } | null = null;
  saving = false;
  consentLocked = false;
  meta = '';
  private check$ = new Subject<void>();

  constructor() {
    this.check$.pipe(
      debounceTime(300),
      switchMap(() => {
        const n = this.f.docNumber.trim();
        const ok = this.f.docType === 'DNI' ? /^\d{8}$/.test(n) : n.length >= 6;
        if (!ok || this.id) return of(null);
        return this.api.checkDocument(this.f.docType, n).pipe(catchError(() => of(null)));
      }),
    ).subscribe((r) => (this.duplicate = r && r.exists ? r.patient! : null));
  }

  get age(): number | null {
    if (!this.f.birthDate) return null;
    const b = new Date(this.f.birthDate + 'T00:00:00'); const t = new Date();
    let a = t.getFullYear() - b.getFullYear();
    if (t.getMonth() < b.getMonth() || (t.getMonth() === b.getMonth() && t.getDate() < b.getDate())) a--;
    return a >= 0 ? a : null;
  }
  get minor() { const a = this.age; return a !== null && a < 18; }
  get provinces(): UbigeoProv[] { return this.deps.find((d) => d[0] === this.dep)?.[2] || []; }
  get districts() { return this.provinces.find((p) => p[0] === this.prov)?.[2] || []; }
  onDep() { this.prov = ''; this.f.ubigeo = ''; }
  onProv() { this.f.ubigeo = ''; }
  private setUbigeo(code?: string) {
    if (!code || code.length !== 6) return;
    this.dep = code.slice(0, 2); this.prov = code.slice(0, 4); this.f.ubigeo = code;
  }

  get validDni() { return /^\d{8}$/.test(this.f.docNumber.trim()); }

  lookup() {
    this.looking = true;
    this.api.lookupDni(this.f.docNumber.trim()).subscribe({
      next: (r) => { this.looking = false; this.f.firstName = r.firstName; this.f.lastName = r.lastName; this.toast.ok('Nombres completados con RENIEC. Revísalos antes de guardar.'); },
      error: (err) => { this.looking = false; this.toast.error(apiError(err).message); },
    });
  }

  onAccepted(version: string) {
    this.f.dataConsent = true;
    this.consentVersion = version;
    this.legalOpen = false;
    this.e['dataConsent'] = '';
  }

  ngOnInit() {
    this.api.ubigeo().subscribe((d) => {
      this.deps = d;
      if (!this.id && !this.f.ubigeo) this.setUbigeo('150107'); // Chaclacayo (sede de la clínica) como sugerencia
    });
    this.api.dniConfig().subscribe({ next: (c) => (this.dniEnabled = c.enabled), error: () => {} });
    if (this.id) {
      this.api.patient(+this.id).subscribe(({ patient: p }) => {
        this.f = {
          docType: p.docType, docNumber: p.docNumber, firstName: p.firstName, lastName: p.lastName, birthDate: p.birthDate,
          sex: p.sex, phone: p.phone, email: p.email || '', address: p.address || '', district: p.district || '', ubigeo: p.ubigeo || '',
          emergencyContactName: p.emergencyContactName || '', emergencyContactPhone: p.emergencyContactPhone || '',
          emergencyContactRelationship: p.emergencyContactRelationship || '', notes: p.notes || '',
          dataConsent: !!p.dataConsent, guardianName: p.guardianName || '', guardianDocType: p.guardianDocType || 'DNI',
          guardianDocNumber: p.guardianDocNumber || '', guardianRelationship: p.guardianRelationship || '', guardianPhone: p.guardianPhone || '',
        };
        this.consentVersion = p.consentVersion || '';
        this.setUbigeo(p.ubigeo);
        this.consentLocked = !!(p as any).dataConsent;
        this.meta = p.updatedAt ? `${dateTime(p.updatedAt)} por ${p.updatedBy}` : `registrado el ${dateTime(p.createdAt)} por ${p.createdBy}`;
      });
    } else if (this.doc) {
      const d = this.doc.trim();
      if (/^\d{8}$/.test(d)) this.f.docNumber = d;
      else if (/^[A-Za-zÁÉÍÓÚáéíóúÑñ ]+$/.test(d)) this.f.lastName = d;
      this.checkDoc();
    }
  }

  checkDoc() {
    this.e['docNumber'] = '';
    this.duplicate = null;
    this.check$.next();
  }

  private validate(): boolean {
    const e: Record<string, string> = {};
    const doc = this.f.docNumber.replace(/\s/g, '').toUpperCase();
    if (this.f.docType === 'DNI' && !/^\d{8}$/.test(doc)) e['docNumber'] = 'El DNI debe tener 8 dígitos.';
    if (this.f.docType === 'CE' && !/^[A-Z0-9]{9,12}$/.test(doc)) e['docNumber'] = 'El carné de extranjería tiene de 9 a 12 caracteres.';
    if (this.f.docType === 'PAS' && !/^[A-Z0-9]{6,12}$/.test(doc)) e['docNumber'] = 'El pasaporte tiene de 6 a 12 caracteres.';
    if (this.f.firstName.trim().length < 2) e['firstName'] = 'Ingresa los nombres.';
    if (this.f.lastName.trim().length < 2) e['lastName'] = 'Ingresa los apellidos.';
    if (!this.f.birthDate) e['birthDate'] = 'Ingresa la fecha de nacimiento.';
    else if (this.f.birthDate > this.today) e['birthDate'] = 'La fecha de nacimiento no puede ser futura.';
    if (!this.f.sex) e['sex'] = 'Selecciona el sexo.';
    if (!/^\+?\d{6,12}$/.test(this.f.phone.replace(/[\s()-]/g, ''))) e['phone'] = 'Ingresa un teléfono válido (6 a 12 dígitos).';
    if (this.f.email.trim() && !/^[^\s]+[@][^\s]+\.[^\s]+$/.test(this.f.email.trim())) e['email'] = 'Ingresa un correo válido.';
    if (this.minor) {
      if (this.f.guardianName.trim().length < 5) e['guardianName'] = 'Ingresa el nombre completo del padre, madre o tutor legal.';
      if (!this.f.guardianRelationship) e['guardianRelationship'] = 'Selecciona el vínculo.';
      const g = this.f.guardianDocNumber.replace(/\s/g, '');
      if (this.f.guardianDocType === 'DNI' ? !/^\d{8}$/.test(g) : g.length < 6) e['guardianDocNumber'] = 'Ingresa un documento válido.';
      if (!/^\+?\d{6,12}$/.test(this.f.guardianPhone.replace(/[\s()-]/g, ''))) e['guardianPhone'] = 'Ingresa un celular válido.';
    }
    if (!this.f.dataConsent) e['dataConsent'] = 'Falta aceptar la política de privacidad.';
    this.e = e;
    return Object.keys(e).length === 0;
  }

  save() {
    if (!this.validate()) {
      this.toast.warn('Revisa los campos marcados.');
      return;
    }
    this.saving = true;
    const body = { ...this.f };
    const req = this.id ? this.api.updatePatient(+this.id, body) : this.api.createPatient(body);
    req.subscribe({
      next: (p) => {
        this.toast.ok(this.id ? 'Datos del paciente actualizados.' : `Paciente registrado: ${p.fullName}.`);
        this.router.navigate(['/pacientes', p.id]);
      },
      error: (err) => {
        this.saving = false;
        const a = apiError(err);
        if (a.code === 'PACIENTE_DUPLICADO' && a.fields?.['existingId']) {
          this.duplicate = { id: +a.fields['existingId'], fullName: a.message.split(': ').pop()?.replace(/\.$/, '') || '' };
        }
        this.e = { ...(a.fields || {}) };
        if (a.status && a.status < 500) this.toast.error(a.message);
      },
    });
  }
}
