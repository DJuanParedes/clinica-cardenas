import { Component, Input, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { ClinicalHistory, Diagnosis } from '../../core/models';
import { apiError, dateTime } from '../../core/format';
import { IconComponent } from '../../shared/icon.component';

const CIE10 = [
  { c: 'K02.0', d: 'Caries limitada al esmalte' },
  { c: 'K02.1', d: 'Caries de la dentina' },
  { c: 'K02.2', d: 'Caries del cemento' },
  { c: 'K03.6', d: 'Depósitos en los dientes (cálculo)' },
  { c: 'K04.0', d: 'Pulpitis' },
  { c: 'K04.1', d: 'Necrosis de la pulpa' },
  { c: 'K04.7', d: 'Absceso periapical sin fístula' },
  { c: 'K05.1', d: 'Gingivitis crónica' },
  { c: 'K05.3', d: 'Periodontitis crónica' },
  { c: 'K07.3', d: 'Anomalías de la posición del diente' },
  { c: 'K08.1', d: 'Pérdida de dientes por accidente, extracción o enfermedad periodontal' },
  { c: 'K01.1', d: 'Dientes impactados' },
  { c: 'S02.5', d: 'Fractura de los dientes' },
];

const FIELDS: { key: keyof ClinicalHistory; label: string; hint?: string }[] = [
  { key: 'allergies', label: 'Alergias', hint: 'Medicamentos, látex, anestésicos…' },
  { key: 'currentMedication', label: 'Medicación actual' },
  { key: 'medicalBackground', label: 'Antecedentes médicos', hint: 'Enfermedades sistémicas, embarazo, tratamientos en curso' },
  { key: 'surgeries', label: 'Cirugías previas' },
  { key: 'habits', label: 'Hábitos', hint: 'Bruxismo, tabaco, onicofagia, respiración bucal…' },
  { key: 'familyBackground', label: 'Antecedentes familiares' },
  { key: 'observations', label: 'Observaciones clínicas' },
];

/** HU-04: historia clínica con antecedentes, alergias y diagnósticos. */
@Component({
  selector: 'app-history-tab',
  standalone: true,
  imports: [FormsModule, IconComponent],
  template: `
    @if (alerts.length) {
      <div class="notice gum alerts" role="alert">
        <app-icon name="alert" />
        <div>@for (a of alerts; track a) { <div class="strong">{{ a }}</div> }</div>
      </div>
    }

    <div class="grid layout">
      <section class="panel">
        <div class="panel-head">
          <h3>Antecedentes</h3>
          @if (!editing) {
            <button class="btn sm" type="button" (click)="startEdit()"><app-icon name="edit" [size]="15" /> {{ h?.id ? 'Editar' : 'Completar antecedentes' }}</button>
          }
        </div>
        @if (editing) {
          <form class="panel-body grid cols-2" (ngSubmit)="save()">
            <div class="field">
              <label for="bt">Grupo sanguíneo</label>
              <select id="bt" name="bloodType" class="input" [(ngModel)]="draft.bloodType">
                <option [ngValue]="null">Sin dato</option>
                @for (b of blood; track b) { <option [ngValue]="b">{{ b }}</option> }
              </select>
            </div>
            <div></div>
            @for (f of fields; track f.key) {
              <div class="field">
                <label [attr.for]="f.key">{{ f.label }}</label>
                <textarea class="input" [id]="f.key" [name]="f.key" rows="2" [(ngModel)]="draft[f.key]" [placeholder]="f.hint || ''"></textarea>
              </div>
            }
            <div class="span-all row" style="justify-content: flex-end">
              <button type="button" class="btn" (click)="editing = false">Cancelar</button>
              <button type="submit" class="btn primary" [disabled]="saving">@if (saving) { <span class="spin"></span> } Guardar antecedentes</button>
            </div>
          </form>
        } @else if (!h?.id) {
          <div class="empty"><h3>Historia clínica sin antecedentes</h3><p>Regístralos antes de la primera atención.</p></div>
        } @else {
          <div class="panel-body">
            <dl class="kv">
              <dt>Grupo sanguíneo</dt><dd>{{ h.bloodType || '—' }}</dd>
              @for (f of fields; track f.key) {
                <dt>{{ f.label }}</dt><dd [class.alert]="f.key === 'allergies' && h.allergies">{{ h[f.key] || '—' }}</dd>
              }
            </dl>
          </div>
          <div class="panel-foot muted small">Actualizado {{ dt(h.updatedAt) }} por {{ h.updatedBy }}</div>
        }
      </section>

      <section class="panel">
        <div class="panel-head"><h3>Diagnósticos</h3><span class="muted small">{{ diagnoses.length }}</span></div>
        <form class="panel-body add" (ngSubmit)="addDiagnosis()">
          <div class="grid dx">
            <div class="field">
              <label for="cie">CIE-10</label>
              <input id="cie" name="cie" class="input" list="cie10" [(ngModel)]="dx.cie10Code" (ngModelChange)="fillFromCie()" placeholder="K02.1" />
              <datalist id="cie10">@for (c of cie10; track c.c) { <option [value]="c.c">{{ c.d }}</option> }</datalist>
            </div>
            <div class="field">
              <label for="tooth">Pieza</label>
              <input id="tooth" name="tooth" class="input" type="number" min="11" max="85" [(ngModel)]="dx.tooth" placeholder="36" />
            </div>
          </div>
          <div class="field">
            <label for="dxd">Descripción</label>
            <textarea id="dxd" name="dxd" class="input" rows="2" [class.invalid]="dxError" [(ngModel)]="dx.description"></textarea>
            @if (dxError) { <span class="err">{{ dxError }}</span> }
          </div>
          <div class="row" style="justify-content: flex-end"><button type="submit" class="btn primary sm" [disabled]="savingDx">Registrar diagnóstico</button></div>
        </form>
        <ul class="dxlist">
          @for (d of diagnoses; track d.id) {
            <li>
              @if (d.cie10Code) { <span class="code tabular">{{ d.cie10Code }}</span> }
              <div class="grow">
                <div>{{ d.description }}@if (d.tooth) { <span class="muted"> · pieza {{ d.tooth }}</span> }</div>
                <div class="muted small">{{ dt(d.createdAt) }} · {{ d.dentist?.fullName || d.createdBy }}</div>
              </div>
            </li>
          } @empty {
            <li class="muted small">Sin diagnósticos registrados.</li>
          }
        </ul>
      </section>
    </div>
  `,
  styles: [`
    .alerts { margin-bottom: 16px; align-items: center; }
    .layout { grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr); align-items: start; }
    .kv { grid-template-columns: 170px 1fr; }
    dd.alert { color: var(--gum); font-weight: 700; }
    .add { display: flex; flex-direction: column; gap: 10px; border-bottom: 1px solid var(--line); }
    .dx { grid-template-columns: 1fr 110px; gap: 10px; }
    .dxlist { list-style: none; margin: 0; padding: 6px 18px 14px; }
    .dxlist li { display: flex; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--line); font-size: var(--step--1); }
    .dxlist li:last-child { border-bottom: 0; }
    .code { flex: none; height: 22px; padding: 0 7px; border-radius: 5px; background: var(--restore-soft); color: var(--restore); font-weight: 700; font-size: 12px; display: inline-flex; align-items: center; }
    @media (max-width: 1000px) { .layout { grid-template-columns: 1fr; } }
  `],
})
export class HistoryTabComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);

  @Input() patientId!: number;

  h: ClinicalHistory | null = null;
  draft: any = {};
  diagnoses: Diagnosis[] = [];
  alerts: string[] = [];
  editing = false;
  saving = false;
  savingDx = false;
  dx: { cie10Code: string; description: string; tooth: number | null } = { cie10Code: '', description: '', tooth: null };
  dxError = '';
  fields = FIELDS;
  cie10 = CIE10;
  blood = ['O+', 'O-', 'A+', 'A-', 'B+', 'B-', 'AB+', 'AB-'];

  ngOnInit() { this.load(); }

  load() {
    this.api.history(this.patientId).subscribe((r) => {
      this.h = r.history;
      this.diagnoses = r.diagnoses;
      this.alerts = r.alerts;
      if (!this.h) this.editing = false;
    });
  }

  startEdit() {
    this.draft = { ...(this.h || {}) };
    this.editing = true;
  }

  save() {
    this.saving = true;
    this.api.saveHistory(this.patientId, this.draft).subscribe({
      next: () => { this.saving = false; this.editing = false; this.toast.ok('Antecedentes guardados.'); this.load(); },
      error: (e) => { this.saving = false; this.toast.error(apiError(e).message); },
    });
  }

  fillFromCie() {
    const m = CIE10.find((c) => c.c.toUpperCase() === (this.dx.cie10Code || '').toUpperCase());
    if (m && !this.dx.description) this.dx.description = m.d;
  }

  addDiagnosis() {
    this.dxError = '';
    if (!this.dx.description?.trim()) { this.dxError = 'Describe el diagnóstico.'; return; }
    this.savingDx = true;
    this.api.addDiagnosis(this.patientId, { ...this.dx, tooth: this.dx.tooth || null }).subscribe({
      next: () => {
        this.savingDx = false;
        this.dx = { cie10Code: '', description: '', tooth: null };
        this.toast.ok('Diagnóstico registrado.');
        this.load();
      },
      error: (e) => { this.savingDx = false; this.dxError = apiError(e).message; },
    });
  }

  dt = dateTime;
}
