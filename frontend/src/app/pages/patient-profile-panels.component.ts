import { Component, EventEmitter, Input, OnChanges, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api, PatientProfile, PatientRelation } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ToastService } from '../core/toast.service';
import { Patient } from '../core/models';
import { apiError } from '../core/format';
import { IconComponent } from '../shared/icon.component';
import { ModalComponent } from '../shared/modal.component';
import { PatientPickerComponent } from '../shared/patient-picker.component';

import { OnlyDirective } from '../shared/only.directive';
export const REL_LABEL: Record<string, string> = {
  PADRE: 'Padre', MADRE: 'Madre', HIJO: 'Hijo', HIJA: 'Hija', CONYUGE: 'Cónyuge', HERMANO: 'Hermano(a)', ABUELO: 'Abuelo(a)',
  NIETO: 'Nieto(a)', TIO: 'Tío(a)', TUTOR: 'Tutor(a)', OTRO: 'Otro',
};

/** v2.5: datos para la clínica (N° HC, captación, etiquetas, datos fiscales) y familiares vinculados. */
@Component({
  selector: 'app-patient-profile-panels',
  standalone: true,
  imports: [OnlyDirective, FormsModule, RouterLink, IconComponent, ModalComponent, PatientPickerComponent],
  template: `
    <section class="panel">
      <div class="panel-head">
        <h3>Datos para la clínica</h3>
        @if (canEdit && !editing && prof) { <button class="btn sm" type="button" (click)="startEdit()"><app-icon name="edit" [size]="15" /> Editar</button> }
      </div>
      @if (!prof) { <div class="panel-body"><div class="skeleton" style="height: 90px"></div></div> }
      @else if (!editing) {
        <div class="panel-body">
          <dl class="kv">
            <dt>N° de historia clínica</dt><dd class="tabular strong">{{ prof.hcNumber || '—' }}</dd>
            <dt>¿Cómo nos conoció?</dt><dd>{{ prof.sourceLabel || 'Sin registrar' }}{{ prof.sourceDetail ? ' · ' + prof.sourceDetail : '' }}</dd>
            <dt>Etiquetas</dt>
            <dd>@for (t of prof.tags; track t) { <span class="tag teal plain chip">{{ t }}</span> } @empty { <span class="muted">Sin etiquetas</span> }</dd>
            <dt>Datos para factura</dt>
            <dd>
              @if (prof.fiscalRuc) { <strong>{{ prof.fiscalName }}</strong><div class="muted small tabular">RUC {{ prof.fiscalRuc }}{{ prof.fiscalAddress ? ' · ' + prof.fiscalAddress : '' }}</div> }
              @else { <span class="muted">Sin RUC: se emite boleta a su nombre</span> }
            </dd>
          </dl>
        </div>
      } @else {
        <form class="panel-body form" (ngSubmit)="save()">
          <div class="grid cols-2">
            <div class="field">
              <label for="hc">N° de historia clínica</label>
              <input id="hc" name="hc" class="input tabular" [class.invalid]="e['hcNumber']" [(ngModel)]="d.hcNumber" maxlength="20" appOnly="text" />
              @if (e['hcNumber']) { <span class="err">{{ e['hcNumber'] }}</span> } @else { <span class="hint">Cámbialo si el paciente ya tenía una ficha en papel.</span> }
            </div>
            <div class="field">
              <label for="src">¿Cómo nos conoció?</label>
              <select id="src" name="src" class="input" [class.invalid]="e['source']" [(ngModel)]="d.source">
                <option value="">Sin registrar</option>
                @for (o of options?.sources || []; track o.code) { <option [value]="o.code">{{ o.label }}</option> }
              </select>
              @if (e['source']) { <span class="err">{{ e['source'] }}</span> }
            </div>
            @if (d.source) {
              <div class="field span2">
                <label for="srcd">{{ d.source === 'RECOMENDACION' ? '¿Quién lo recomendó?' : 'Detalle (campaña, publicación, empresa…)' }}</label>
                <input id="srcd" name="srcd" class="input" [(ngModel)]="d.sourceDetail" maxlength="120" appOnly="text" />
              </div>
            }
            <div class="field span2">
              <label for="tg">Etiquetas</label>
              <div class="chips">
                @for (t of d.tags; track t) { <span class="tag teal plain chip">{{ t }} <button type="button" (click)="removeTag(t)" [attr.aria-label]="'Quitar ' + t">×</button></span> }
                <input id="tg" name="tg" class="input tag-input" [(ngModel)]="newTag" list="tag-list" placeholder="Escribe y presiona Enter"
                       (keydown.enter)="$event.preventDefault(); addTag()" (blur)="addTag()" maxlength="40" appOnly="text" />
                <datalist id="tag-list">@for (t of options?.tags || []; track t.tag) { <option [value]="t.tag">{{ t.patients }} pacientes</option> }</datalist>
              </div>
              @if (e['tags']) { <span class="err">{{ e['tags'] }}</span> } @else { <span class="hint">Ej.: VIP, Ortodoncia, Convenio ABC, Paga en cuotas. Sirven para filtrar y para campañas.</span> }
            </div>
          </div>
          <fieldset>
            <legend>Datos para factura (opcional)</legend>
            <div class="grid cols-2">
              <div class="field">
                <label for="ruc">RUC</label>
                <div class="row">
                  <input id="ruc" name="ruc" class="input tabular" [class.invalid]="e['fiscalRuc']" [(ngModel)]="d.fiscalRuc" maxlength="11" inputmode="numeric" appOnly="digits" />
                  <button type="button" class="btn sm" (click)="lookupRuc()" [disabled]="(d.fiscalRuc || '').length !== 11 || looking">{{ looking ? '…' : 'Buscar' }}</button>
                </div>
                @if (e['fiscalRuc']) { <span class="err">{{ e['fiscalRuc'] }}</span> } @else if (rucMsg) { <span class="hint">{{ rucMsg }}</span> }
              </div>
              <div class="field">
                <label for="rs">Razón social</label>
                <input id="rs" name="rs" class="input" [class.invalid]="e['fiscalName']" [(ngModel)]="d.fiscalName" maxlength="200" appOnly="text" />
                @if (e['fiscalName']) { <span class="err">{{ e['fiscalName'] }}</span> }
              </div>
              <div class="field span2">
                <label for="fa">Dirección fiscal</label>
                <input id="fa" name="fa" class="input" [(ngModel)]="d.fiscalAddress" maxlength="250" appOnly="text" />
              </div>
            </div>
          </fieldset>
          <div class="row end">
            <button type="button" class="btn" (click)="editing = false">Cancelar</button>
            <button type="submit" class="btn primary" [disabled]="saving">{{ saving ? 'Guardando…' : 'Guardar' }}</button>
          </div>
        </form>
      }
    </section>

    <section class="panel">
      <div class="panel-head">
        <h3>Familiares</h3>
        @if (canEdit) { <button class="btn sm" type="button" (click)="openRel(null)"><app-icon name="plus" [size]="15" /> Agregar familiar</button> }
      </div>
      <div class="panel-body">
        @for (r of prof?.relations || []; track r.id + '-' + r.inverse) {
          <div class="rel">
            <span class="avatar">{{ ini(r.fullName) }}</span>
            <div class="grow">
              <div class="strong">
                @if (r.relatedPatientId) { <a [routerLink]="['/pacientes', r.relatedPatientId]">{{ r.fullName }}</a> } @else { {{ r.fullName }} }
                <span class="muted small"> · {{ r.inverse ? 'lo registró como ' + rel(r.relationship).toLowerCase() : rel(r.relationship) }}</span>
              </div>
              <div class="muted small">
                {{ r.docType ? r.docType + ' ' + r.docNumber : '' }}{{ r.phone ? ' · ' + r.phone : '' }}{{ r.email ? ' · ' + r.email : '' }}
                @if (r.relatedHc) { · HC {{ r.relatedHc }} }
              </div>
              <div class="row wrap">
                @if (r.relatedPatientId) { <span class="tag teal">Paciente de la clínica</span> }
                @if (r.isGuardian) { <span class="tag blue">Apoderado</span> }
                @if (r.pays) { <span class="tag amber">Responsable de pago</span> }
              </div>
            </div>
            @if (canEdit && !r.inverse) {
              <button type="button" class="btn ghost sm" (click)="openRel(r)" aria-label="Editar familiar"><app-icon name="edit" [size]="15" /></button>
              <button type="button" class="btn ghost sm" (click)="removeRel(r)" aria-label="Quitar familiar"><app-icon name="x" [size]="15" /></button>
            }
          </div>
        } @empty {
          <p class="muted small">Sin familiares registrados. Vincula a padres, hijos o cónyuge para ver a la familia junta y saber quién paga.</p>
        }
      </div>
    </section>

    <app-modal [open]="relOpen" [title]="relEditing ? 'Editar familiar' : 'Agregar familiar'" (close)="relOpen = false" [width]="560">
      <form class="form" (ngSubmit)="saveRel()" id="relForm">
        @if (!relEditing) {
          <div class="seg">
            <button type="button" [class.on]="relMode === 'paciente'" (click)="relMode = 'paciente'">Ya es paciente</button>
            <button type="button" [class.on]="relMode === 'contacto'" (click)="relMode = 'contacto'; picked = null">No es paciente</button>
          </div>
        }
        @if (relMode === 'paciente' && !relEditing) {
          <div class="field">
            <label>Buscar paciente</label>
            <app-patient-picker [value]="picked" (valueChange)="picked = $event" />
            @if (re['relatedPatientId']) { <span class="err">{{ re['relatedPatientId'] }}</span> }
          </div>
        } @else if (relMode === 'contacto' || (relEditing && !r.relatedPatientId)) {
          <div class="grid cols-2">
            <div class="field span2">
              <label for="rn" class="req">Nombre completo</label>
              <input id="rn" name="rn" class="input" [class.invalid]="re['fullName']" [(ngModel)]="r.fullName" maxlength="150" appOnly="letters" />
              @if (re['fullName']) { <span class="err">{{ re['fullName'] }}</span> }
            </div>
            <div class="field">
              <label for="rdt">Documento</label>
              <div class="row">
                <select id="rdt" name="rdt" class="input narrow" [(ngModel)]="r.docType"><option value="DNI">DNI</option><option value="CE">CE</option><option value="PAS">Pasaporte</option></select>
                <input name="rdn" class="input tabular" [class.invalid]="re['docNumber']" [(ngModel)]="r.docNumber" maxlength="12" [appOnly]="r.docType === 'PAS' ? 'alnum' : 'digits'" aria-label="Número de documento" />
              </div>
              @if (re['docNumber']) { <span class="err">{{ re['docNumber'] }}</span> }
            </div>
            <div class="field">
              <label for="rp">Celular</label>
              <input id="rp" name="rp" class="input tabular" [class.invalid]="re['phone']" [(ngModel)]="r.phone" maxlength="9" inputmode="numeric" appOnly="phone" />
              @if (re['phone']) { <span class="err">{{ re['phone'] }}</span> }
            </div>
            <div class="field span2">
              <label for="re">Correo</label>
              <input id="re" name="re" class="input" [class.invalid]="re['email']" [(ngModel)]="r.email" maxlength="120" appOnly="email" />
              @if (re['email']) { <span class="err">{{ re['email'] }}</span> }
            </div>
          </div>
        }
        <div class="grid cols-2">
          <div class="field">
            <label for="rr" class="req">Parentesco</label>
            <select id="rr" name="rr" class="input" [class.invalid]="re['relationship']" [(ngModel)]="r.relationship">
              <option value="">Elige…</option>
              @for (k of options?.relationships || []; track k) { <option [value]="k">{{ rel(k) }}</option> }
            </select>
            @if (re['relationship']) { <span class="err">{{ re['relationship'] }}</span> }
          </div>
          <div class="field checks">
            <label class="check"><input type="checkbox" name="rg" [(ngModel)]="r.isGuardian" /> Es su apoderado</label>
            <label class="check"><input type="checkbox" name="rpy" [(ngModel)]="r.pays" /> Responsable de pago</label>
          </div>
          <div class="field span2">
            <label for="rno">Nota</label>
            <input id="rno" name="rno" class="input" [(ngModel)]="r.notes" maxlength="200" appOnly="text" />
          </div>
        </div>
      </form>
      <div modal-actions class="row end">
        <button type="button" class="btn" (click)="relOpen = false">Cancelar</button>
        <button type="submit" form="relForm" class="btn primary" [disabled]="saving">{{ saving ? 'Guardando…' : 'Guardar' }}</button>
      </div>
    </app-modal>
  `,
  styles: [`
    :host { display: contents; }
    .chip { margin: 0 6px 6px 0; }
    .chip button { background: none; border: 0; padding: 0 0 0 4px; cursor: pointer; color: inherit; font: inherit; }
    .chips { display: flex; flex-wrap: wrap; align-items: center; gap: 0; border: 1px solid var(--line); border-radius: var(--radius); padding: 6px 6px 0; background: var(--surface); }
    .tag-input { border: 0; flex: 1; min-width: 160px; margin-bottom: 6px; padding: 4px; }
    .tag-input:focus { box-shadow: none; }
    fieldset { border: 1px solid var(--line); border-radius: var(--radius); padding: 10px 14px 4px; margin: 12px 0; }
    legend { font-weight: 700; font-size: 13px; padding: 0 6px; }
    .span2 { grid-column: 1 / -1; }
    .rel { display: flex; gap: 12px; align-items: flex-start; padding: 10px 0; border-bottom: 1px solid var(--line); }
    .rel:last-child { border-bottom: 0; }
    .rel .row { margin-top: 4px; gap: 6px; }
    .seg { display: inline-flex; border: 1px solid var(--line); border-radius: 999px; padding: 3px; margin-bottom: 12px; }
    .seg button { border: 0; background: none; padding: 6px 14px; border-radius: 999px; font: inherit; cursor: pointer; }
    .seg button.on { background: var(--teal-soft); color: var(--teal-strong); font-weight: 700; }
    .checks { justify-content: flex-end; gap: 6px; }
    .check { display: flex; gap: 8px; align-items: center; font-weight: 500; }
    .narrow { max-width: 110px; }
    .end { justify-content: flex-end; gap: 8px; }
  `],
})
export class PatientProfilePanelsComponent implements OnChanges {
  @Input() patientId!: number;
  @Output() changed = new EventEmitter<PatientProfile>();
  private api = inject(Api);
  private toast = inject(ToastService);
  auth = inject(AuthService);

  prof: PatientProfile | null = null;
  options: any = null;
  editing = false;
  saving = false;
  looking = false;
  rucMsg = '';
  newTag = '';
  d: any = {};
  e: Record<string, string> = {};

  relOpen = false;
  relEditing: PatientRelation | null = null;
  relMode: 'paciente' | 'contacto' = 'paciente';
  picked: Patient | null = null;
  r: any = {};
  re: Record<string, string> = {};

  get canEdit() { return this.auth.can('PACIENTES_EDITAR'); }

  ngOnChanges() {
    this.load();
    if (!this.options) this.api.patientProfileOptions().subscribe((o) => (this.options = o));
  }

  load() { this.api.patientProfile(this.patientId).subscribe((p) => this.set(p)); }
  private set(p: PatientProfile) { this.prof = p; this.changed.emit(p); }

  startEdit() {
    const p = this.prof!;
    this.d = { hcNumber: p.hcNumber || '', source: p.source || '', sourceDetail: p.sourceDetail || '', tags: [...p.tags],
      fiscalRuc: p.fiscalRuc || '', fiscalName: p.fiscalName || '', fiscalAddress: p.fiscalAddress || '' };
    this.e = {}; this.rucMsg = ''; this.newTag = ''; this.editing = true;
  }

  addTag() {
    const t = this.newTag.trim().replace(/\s+/g, ' ');
    if (t && !this.d.tags.some((x: string) => x.toLowerCase() === t.toLowerCase())) this.d.tags.push(t);
    this.newTag = '';
  }
  removeTag(t: string) { this.d.tags = this.d.tags.filter((x: string) => x !== t); }

  lookupRuc() {
    this.looking = true; this.rucMsg = '';
    this.api.patientRuc(this.d.fiscalRuc).subscribe({
      next: (r) => {
        this.looking = false;
        this.d.fiscalName = r.razonSocial;
        if (r.direccion) this.d.fiscalAddress = [r.direccion, r.distrito, r.provincia].filter(Boolean).join(", ");
        this.rucMsg = `${r.estado} · ${r.condicion}`;
      },
      error: (err) => { this.looking = false; this.rucMsg = apiError(err).message; },
    });
  }

  save() {
    this.addTag();
    this.saving = true; this.e = {};
    this.api.updatePatientProfile(this.patientId, this.d).subscribe({
      next: (p) => { this.saving = false; this.editing = false; this.set(p); this.toast.ok('Datos guardados.'); },
      error: (err) => { this.saving = false; const a = apiError(err); this.e = a.fields || {}; this.toast.error(a.message); },
    });
  }

  openRel(x: PatientRelation | null) {
    this.relEditing = x; this.re = {}; this.picked = null;
    this.relMode = x && !x.relatedPatientId ? 'contacto' : 'paciente';
    this.r = x ? { relatedPatientId: x.relatedPatientId, fullName: x.fullName, docType: x.docType || 'DNI', docNumber: x.docNumber || '',
      relationship: x.relationship, phone: x.phone || '', email: x.email || '', isGuardian: !!x.isGuardian, pays: !!x.pays, notes: x.notes || '' }
      : { docType: 'DNI', relationship: '', isGuardian: false, pays: false };
    this.relOpen = true;
  }

  saveRel() {
    const body = { ...this.r };
    if (!this.relEditing) {
      if (this.relMode === 'paciente') {
        if (!this.picked) { this.re = { relatedPatientId: 'Busca y elige al familiar.' }; return; }
        body.relatedPatientId = this.picked.id;
      } else body.relatedPatientId = null;
    }
    this.saving = true; this.re = {};
    const req = this.relEditing ? this.api.updateRelation(this.patientId, this.relEditing.id, body) : this.api.addRelation(this.patientId, body);
    req.subscribe({
      next: (p) => { this.saving = false; this.relOpen = false; this.set(p); this.toast.ok('Familiar guardado.'); },
      error: (err) => { this.saving = false; const a = apiError(err); this.re = a.fields || {}; this.toast.error(a.message); },
    });
  }

  removeRel(x: PatientRelation) {
    if (!confirm(`¿Quitar el vínculo con ${x.fullName}?`)) return;
    this.api.deleteRelation(this.patientId, x.id).subscribe({ next: (p) => { this.set(p); this.toast.ok('Vínculo quitado.'); }, error: (err) => this.toast.error(apiError(err).message) });
  }

  rel(k: string) { return REL_LABEL[k] || k; }
  ini(n: string) { return n.split(' ').filter(Boolean).slice(0, 2).map((w) => w[0]).join('').toUpperCase(); }
}
