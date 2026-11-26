import { Component, Input, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, switchMap, of, catchError } from 'rxjs';
import { Api } from '../core/api.service';
import { ToastService } from '../core/toast.service';
import { DOC_TYPES, apiError, dateTime, isoDate } from '../core/format';
import { IconComponent } from '../shared/icon.component';
import { OnlyDirective } from '../shared/only.directive';
import { MAX, RE, docError, phoneDigits } from '../core/rules';

interface Form {
  docType: string; docNumber: string; firstName: string; lastName: string; birthDate: string; sex: string;
  phone: string; email: string; address: string; district: string;
  emergencyContactName: string; emergencyContactPhone: string; notes: string;
}

/** HU-01 registro y HU-03 actualización de datos del paciente. */
@Component({
  selector: 'app-patient-form',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent, OnlyDirective],
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
              <select id="dt" name="docType" class="input" [(ngModel)]="f.docType" (ngModelChange)="onDocType()" [disabled]="!!id">
                @for (d of docTypes; track d.code) { <option [value]="d.code">{{ d.label }}</option> }
              </select>
            </div>
            <div class="field span-2">
              <label class="req" for="dn">Número de documento</label>
              <input id="dn" name="docNumber" class="input tabular" [class.invalid]="e['docNumber']" [(ngModel)]="f.docNumber"
                     (ngModelChange)="checkDoc()" [disabled]="!!id" [maxlength]="f.docType === 'DNI' ? 8 : 12"
                     [appOnly]="f.docType === 'DNI' ? 'digits' : 'alnum'"
                     [attr.inputmode]="f.docType === 'DNI' ? 'numeric' : 'text'"
                     [placeholder]="f.docType === 'DNI' ? '8 dígitos' : f.docType === 'CE' ? '9 a 12 caracteres' : '6 a 12 caracteres'" autocomplete="off" />
              @if (e['docNumber']) { <span class="err">{{ e['docNumber'] }}</span> }
              @else if (id) { <span class="hint">El documento no se modifica después del registro.</span> }
            </div>
            <div class="field">
              <label class="req" for="fn">Nombres</label>
              <input id="fn" name="firstName" class="input" [class.invalid]="e['firstName']" [(ngModel)]="f.firstName" appOnly="letters" [maxlength]="max.firstName" autocomplete="off" />
              @if (e['firstName']) { <span class="err">{{ e['firstName'] }}</span> }
            </div>
            <div class="field span-2">
              <label class="req" for="ln">Apellidos</label>
              <input id="ln" name="lastName" class="input" [class.invalid]="e['lastName']" [(ngModel)]="f.lastName" appOnly="letters" [maxlength]="max.lastName" autocomplete="off" />
              @if (e['lastName']) { <span class="err">{{ e['lastName'] }}</span> }
            </div>
            <div class="field">
              <label class="req" for="bd">Fecha de nacimiento</label>
              <input id="bd" name="birthDate" type="date" class="input" [class.invalid]="e['birthDate']" [(ngModel)]="f.birthDate" [min]="minBirth" [max]="today" />
              @if (e['birthDate']) { <span class="err">{{ e['birthDate'] }}</span> }
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
              <input id="ph" name="phone" class="input tabular" [class.invalid]="e['phone']" [(ngModel)]="f.phone" appOnly="phone" [maxlength]="max.phone" inputmode="tel" placeholder="987654321" />
              @if (e['phone']) { <span class="err">{{ e['phone'] }}</span> }
            </div>
            <div class="field span-2">
              <label for="em">Correo electrónico</label>
              <input id="em" name="email" type="email" class="input" [class.invalid]="e['email']" [(ngModel)]="f.email" appOnly="email" [maxlength]="max.email" placeholder="Para recordatorios de cita" />
              @if (e['email']) { <span class="err">{{ e['email'] }}</span> }
            </div>
            <div class="field span-2">
              <label for="ad">Dirección</label>
              <input id="ad" name="address" class="input" [(ngModel)]="f.address" appOnly="text" [maxlength]="max.address" />
            </div>
            <div class="field">
              <label for="ds">Distrito</label>
              <input id="ds" name="district" class="input" [(ngModel)]="f.district" appOnly="letters" [maxlength]="max.district" list="districts" />
              <datalist id="districts">
                <option value="Chaclacayo"></option><option value="Lurigancho-Chosica"></option><option value="Ate"></option>
                <option value="Santa Eulalia"></option><option value="La Molina"></option><option value="Cieneguilla"></option>
              </datalist>
            </div>
          </div>
        </section>

        <section class="panel">
          <div class="panel-head"><h3>Contacto de emergencia y notas</h3></div>
          <div class="panel-body grid cols-3">
            <div class="field span-2">
              <label for="ecn">Nombre del contacto</label>
              <input id="ecn" name="ecn" class="input" [class.invalid]="e['emergencyContactName']" [(ngModel)]="f.emergencyContactName" appOnly="letters" [maxlength]="max.contactName" />
              @if (e['emergencyContactName']) { <span class="err">{{ e['emergencyContactName'] }}</span> }
            </div>
            <div class="field">
              <label for="ecp">Teléfono del contacto</label>
              <input id="ecp" name="ecp" class="input tabular" [class.invalid]="e['emergencyContactPhone']" [(ngModel)]="f.emergencyContactPhone" appOnly="phone" [maxlength]="max.phone" inputmode="tel" placeholder="987654321" />
              @if (e['emergencyContactPhone']) { <span class="err">{{ e['emergencyContactPhone'] }}</span> }
            </div>
            <div class="field span-all">
              <label for="nt">Notas administrativas</label>
              <textarea id="nt" name="notes" class="input" [(ngModel)]="f.notes" [maxlength]="max.notes" placeholder="Preferencias de horario, seguro, referido por…"></textarea>
            </div>
          </div>
        </section>

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
  minBirth = isoDate(new Date(new Date().getFullYear() - 120, new Date().getMonth(), new Date().getDate()));
  max = MAX;
  f: Form = {
    docType: 'DNI', docNumber: '', firstName: '', lastName: '', birthDate: '', sex: '',
    phone: '', email: '', address: '', district: '', emergencyContactName: '', emergencyContactPhone: '', notes: '',
  };
  e: Record<string, string> = {};
  duplicate: { id: number; fullName: string } | null = null;
  saving = false;
  meta = '';
  private check$ = new Subject<void>();

  constructor() {
    this.check$.pipe(
      debounceTime(300),
      switchMap(() => {
        const n = this.f.docNumber.trim();
        const ok = !docError(this.f.docType, n);
        if (!ok || this.id) return of(null);
        return this.api.checkDocument(this.f.docType, n).pipe(catchError(() => of(null)));
      }),
    ).subscribe((r) => (this.duplicate = r && r.exists ? r.patient! : null));
  }

  ngOnInit() {
    if (this.id) {
      this.api.patient(+this.id).subscribe(({ patient: p }) => {
        this.f = {
          docType: p.docType, docNumber: p.docNumber, firstName: p.firstName, lastName: p.lastName, birthDate: p.birthDate,
          sex: p.sex, phone: p.phone, email: p.email || '', address: p.address || '', district: p.district || '',
          emergencyContactName: p.emergencyContactName || '', emergencyContactPhone: p.emergencyContactPhone || '', notes: p.notes || '',
        };
        this.meta = p.updatedAt ? `${dateTime(p.updatedAt)} por ${p.updatedBy}` : `registrado el ${dateTime(p.createdAt)} por ${p.createdBy}`;
      });
    } else if (this.doc) {
      const d = this.doc.trim();
      if (/^\d{8}$/.test(d)) this.f.docNumber = d;
      else if (/^[A-Za-zÁÉÍÓÚáéíóúÑñ ]+$/.test(d)) this.f.lastName = d;
      this.checkDoc();
    }
  }

  /** Al cambiar el tipo se limpia el número para no dejar letras en un DNI. */
  onDocType() {
    const n = this.f.docNumber.toUpperCase().replace(/[^A-Z0-9]/g, '');
    this.f.docNumber = this.f.docType === 'DNI' ? n.replace(/\D/g, '').slice(0, 8) : n.slice(0, 12);
    this.checkDoc();
  }

  checkDoc() {
    this.e['docNumber'] = '';
    this.duplicate = null;
    this.check$.next();
  }

  private validate(): boolean {
    const e: Record<string, string> = {};
    const f = this.f;
    const docErr = docError(f.docType, f.docNumber);
    if (docErr) e['docNumber'] = docErr;
    if (!RE.name.test(f.firstName.trim())) e['firstName'] = 'Ingresa los nombres (solo letras, mínimo 2).';
    if (!RE.name.test(f.lastName.trim())) e['lastName'] = 'Ingresa los apellidos (solo letras, mínimo 2).';
    if (!f.birthDate) e['birthDate'] = 'Ingresa la fecha de nacimiento.';
    else if (f.birthDate > this.today) e['birthDate'] = 'La fecha de nacimiento no puede ser futura.';
    else if (f.birthDate < this.minBirth) e['birthDate'] = 'Revisa la fecha de nacimiento.';
    if (!f.sex) e['sex'] = 'Selecciona el sexo.';
    if (!RE.phone.test(phoneDigits(f.phone))) e['phone'] = 'Ingresa un teléfono válido (6 a 12 dígitos, solo números).';
    if (f.email.trim() && !RE.email.test(f.email.trim())) e['email'] = 'Ingresa un correo válido (ej. nombre@correo.com).';
    if (f.emergencyContactName.trim() && !RE.name.test(f.emergencyContactName.trim())) e['emergencyContactName'] = 'El nombre del contacto solo admite letras.';
    if (f.emergencyContactPhone.trim() && !RE.phone.test(phoneDigits(f.emergencyContactPhone))) e['emergencyContactPhone'] = 'Ingresa un teléfono válido (6 a 12 dígitos).';
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
