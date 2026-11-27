import { Component, Input, OnChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { apiError, dateTime } from '../../core/format';
import { IconComponent } from '../../shared/icon.component';

import { OnlyDirective } from '../../shared/only.directive';
export interface AnamQuestion { id: string; section: string; label: string; type: 'TEXT' | 'YES_NO' | 'CHOICE'; options?: string[] | null; alert?: boolean | null; }
export interface AnamTemplate { id: number; code: string; name: string; specialty?: string; questions: AnamQuestion[]; active: boolean | number; }
interface AnamRecord { id: number; templateId: number; templateName: string; questions: AnamQuestion[]; answers: Record<string, { value?: string; note?: string }>; alerts?: string; createdAt: string; createdBy: string; updatedAt?: string; }

/** v2.5: anamnesis por plantillas. Las respuestas "Sí" marcadas como alerta suben a la ficha del paciente. */
@Component({
  selector: 'app-anamnesis-tab',
  standalone: true,
  imports: [OnlyDirective, FormsModule, RouterLink, IconComponent],
  template: `
    @if (alerts.length) {
      <div class="notice gum alerts" role="alert"><app-icon name="alert" /><div>@for (a of alerts; track a) { <div class="strong">{{ a }}</div> }</div></div>
    }
    <div class="grid layout">
      <section class="panel">
        <div class="panel-head">
          <h3>{{ draft ? (draft.recordId ? 'Corregir ' : 'Nueva ') + draft.name.toLowerCase() : 'Anamnesis' }}</h3>
          <div class="row">
            @if (!draft) {
              <select class="input" [(ngModel)]="templateId" aria-label="Plantilla">
                @for (t of templates; track t.id) { <option [ngValue]="t.id">{{ t.name }}</option> }
              </select>
              <button class="btn primary sm" type="button" (click)="start()" [disabled]="!templateId"><app-icon name="plus" [size]="15" /> Llenar</button>
              <a class="btn ghost sm" routerLink="/configuracion/anamnesis" title="Editar plantillas"><app-icon name="edit" [size]="15" /> Plantillas</a>
            }
          </div>
        </div>
        @if (draft) {
          <form class="panel-body" (ngSubmit)="save()">
            @for (sec of sections(draft.questions); track sec.name) {
              <h4 class="sec">{{ sec.name }}</h4>
              @for (q of sec.items; track q.id) {
                <div class="q" [class.al]="q.alert && draft.answers[q.id].value === 'SI'">
                  <label [for]="'q-' + q.id">{{ q.label }} @if (q.alert) { <span class="tag gum plain mini" title="Si responde Sí aparece como alerta">alerta</span> }</label>
                  @switch (q.type) {
                    @case ('YES_NO') {
                      <div class="yn">
                        <button type="button" [class.on]="draft.answers[q.id].value === 'NO'" (click)="set(q, 'NO')">No</button>
                        <button type="button" [class.on]="draft.answers[q.id].value === 'SI'" [class.yes]="draft.answers[q.id].value === 'SI'" (click)="set(q, 'SI')">Sí</button>
                        @if (draft.answers[q.id].value === 'SI') { <input class="input note" [name]="'n-' + q.id" [(ngModel)]="draft.answers[q.id].note" placeholder="Detalle (medicamento, desde cuándo…)" maxlength="300" appOnly="text" /> }
                      </div>
                    }
                    @case ('CHOICE') {
                      <div class="opts">
                        @for (o of q.options || []; track o) { <button type="button" [class.on]="draft.answers[q.id].value === o" (click)="set(q, o)">{{ o }}</button> }
                      </div>
                    }
                    @default { <textarea class="input" [id]="'q-' + q.id" [name]="'q-' + q.id" rows="2" [(ngModel)]="draft.answers[q.id].value" maxlength="2000" appOnly="decimal" inputmode="decimal"></textarea> }
                  }
                </div>
              }
            }
            <div class="row end sticky">
              <button type="button" class="btn" (click)="draft = null">Cancelar</button>
              <button type="submit" class="btn primary" [disabled]="saving">{{ saving ? 'Guardando…' : 'Guardar ficha' }}</button>
            </div>
          </form>
        } @else if (selected) {
          <div class="panel-body">
            <div class="spread"><div><strong>{{ selected.templateName }}</strong><div class="muted small">{{ dt(selected.createdAt) }} · {{ selected.createdBy }}{{ selected.updatedAt ? ' · corregida ' + dt(selected.updatedAt) : '' }}</div></div>
              @if (isToday(selected.createdAt)) { <button class="btn sm" type="button" (click)="edit(selected)"><app-icon name="edit" [size]="14" /> Corregir</button> }</div>
            @for (sec of sections(selected.questions); track sec.name) {
              @if (hasAnswers(sec.items, selected)) {
                <h4 class="sec">{{ sec.name }}</h4>
                <dl class="kv">
                  @for (q of sec.items; track q.id) {
                    @if (selected.answers[q.id]; as a) {
                      <dt>{{ q.label }}</dt>
                      <dd [class.red]="q.alert && a.value === 'SI'">{{ a.value === 'SI' ? 'Sí' : a.value === 'NO' ? 'No' : a.value }}{{ a.note ? ' · ' + a.note : '' }}</dd>
                    }
                  }
                </dl>
              }
            }
          </div>
        } @else {
          <div class="empty small"><p>Aún no se llenó ninguna anamnesis. Elige la plantilla y presiona "Llenar".</p></div>
        }
      </section>

      <section class="panel">
        <div class="panel-head"><h3>Fichas registradas</h3><span class="muted small">{{ records.length }}</span></div>
        <div class="panel-body list">
          @for (r of records; track r.id) {
            <button type="button" class="rec" [class.on]="selected?.id === r.id && !draft" (click)="selected = r; draft = null">
              <span class="strong">{{ r.templateName }}</span>
              <span class="muted small">{{ dt(r.createdAt) }} · {{ r.createdBy }}</span>
              @if (r.alerts) { <span class="small red">⚠ {{ r.alerts }}</span> }
            </button>
          } @empty { <p class="muted small">Sin fichas.</p> }
        </div>
      </section>
    </div>
  `,
  styles: [`
    .layout { grid-template-columns: minmax(0, 2fr) minmax(240px, 1fr); align-items: start; }
    @media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }
    .alerts { margin-bottom: 14px; }
    .sec { margin: 16px 0 8px; font-size: 13px; text-transform: uppercase; letter-spacing: .04em; color: var(--teal-strong); }
    .sec:first-child { margin-top: 0; }
    .q { padding: 8px 10px; border-radius: 10px; margin-bottom: 4px; display: grid; gap: 6px; }
    .q.al { background: var(--gum-soft); }
    .q label { font-weight: 600; font-size: 14px; }
    .mini { font-size: 10px; padding: 1px 6px; margin-left: 4px; }
    .yn, .opts { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
    .yn button, .opts button { border: 1px solid var(--line); background: var(--surface); border-radius: 999px; padding: 5px 14px; font: inherit; cursor: pointer; }
    .yn button.on, .opts button.on { background: var(--teal-soft); border-color: var(--teal); color: var(--teal-strong); font-weight: 700; }
    .yn button.yes { background: var(--gum-soft); border-color: var(--gum); color: var(--gum); }
    .note { flex: 1; min-width: 200px; }
    .end { justify-content: flex-end; gap: 8px; }
    .sticky { position: sticky; bottom: 0; background: var(--surface); padding: 10px 0 0; border-top: 1px solid var(--line); margin-top: 10px; }
    .list { display: grid; gap: 6px; }
    .rec { display: grid; gap: 2px; text-align: left; border: 1px solid var(--line); background: var(--surface); border-radius: 10px; padding: 8px 10px; font: inherit; cursor: pointer; }
    .rec.on { border-color: var(--teal); background: var(--teal-soft); }
    .red { color: var(--gum); font-weight: 600; }
    .spread { display: flex; justify-content: space-between; gap: 10px; align-items: flex-start; margin-bottom: 8px; }
  `],
})
export class AnamnesisTabComponent implements OnChanges {
  @Input() patientId!: number;
  private api = inject(Api);
  private toast = inject(ToastService);

  templates: AnamTemplate[] = [];
  templateId: number | null = null;
  records: AnamRecord[] = [];
  alerts: string[] = [];
  selected: AnamRecord | null = null;
  draft: { recordId?: number; templateId: number; name: string; questions: AnamQuestion[]; answers: Record<string, { value?: string | null; note?: string }> } | null = null;
  saving = false;

  ngOnChanges() {
    this.api.anamnesisTemplates().subscribe((t) => { this.templates = t; this.templateId = this.templateId ?? t[0]?.id ?? null; });
    this.load();
  }

  load() {
    this.api.patientAnamnesis(this.patientId).subscribe((r) => { this.records = r.records; this.alerts = r.alerts; this.selected = r.records[0] || null; });
  }

  start() {
    const t = this.templates.find((x) => x.id === this.templateId);
    if (!t) return;
    // Se precarga con la última ficha de la misma plantilla: el paciente solo confirma o corrige.
    const prev = this.records.find((r) => r.templateId === t.id);
    const answers: Record<string, { value?: string | null; note?: string }> = {};
    for (const q of t.questions) answers[q.id] = { value: prev?.answers[q.id]?.value ?? null, note: prev?.answers[q.id]?.note ?? '' };
    this.draft = { templateId: t.id, name: t.name, questions: t.questions, answers };
  }

  edit(r: AnamRecord) {
    const answers: Record<string, { value?: string | null; note?: string }> = {};
    for (const q of r.questions) answers[q.id] = { value: r.answers[q.id]?.value ?? null, note: r.answers[q.id]?.note ?? '' };
    this.draft = { recordId: r.id, templateId: r.templateId, name: r.templateName, questions: r.questions, answers };
  }

  set(q: AnamQuestion, v: string) {
    const a = this.draft!.answers[q.id];
    a.value = a.value === v ? null : v;
  }

  save() {
    const d = this.draft!;
    this.saving = true;
    const req = d.recordId ? this.api.updateAnamnesis(this.patientId, d.recordId, { answers: d.answers }) : this.api.addAnamnesis(this.patientId, { templateId: d.templateId, answers: d.answers });
    req.subscribe({
      next: (r) => { this.saving = false; this.draft = null; this.records = r.records; this.alerts = r.alerts; this.selected = r.records[0] || null; this.toast.ok('Anamnesis guardada.'); },
      error: (err) => { this.saving = false; this.toast.error(apiError(err).message); },
    });
  }

  sections(qs: AnamQuestion[]) {
    const out: { name: string; items: AnamQuestion[] }[] = [];
    for (const q of qs) {
      const s = out.find((x) => x.name === q.section);
      if (s) s.items.push(q); else out.push({ name: q.section, items: [q] });
    }
    return out;
  }
  hasAnswers(items: AnamQuestion[], r: AnamRecord) { return items.some((q) => r.answers[q.id]); }
  isToday(iso: string) { return iso?.slice(0, 10) === new Date().toLocaleDateString('en-CA'); }
  dt = dateTime;
}
