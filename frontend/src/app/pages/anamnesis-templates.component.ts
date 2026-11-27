import { DeleteButtonComponent } from '../shared/delete-button.component';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api.service';
import { ToastService } from '../core/toast.service';
import { apiError, dateTime } from '../core/format';
import { IconComponent } from '../shared/icon.component';
import { AnamQuestion, AnamTemplate } from './clinical/anamnesis-tab.component';

import { OnlyDirective } from '../shared/only.directive';
/** v2.5: la clínica arma sus propias plantillas de anamnesis (preguntas, secciones, opciones y alertas). */
@Component({
  selector: 'app-anamnesis-templates',
  standalone: true,
  imports: [OnlyDirective, DeleteButtonComponent, FormsModule, IconComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Plantillas de anamnesis</h1><p class="sub">Preguntas que el odontólogo llena en la historia clínica. Las marcadas como alerta, si se responden "Sí", aparecen en rojo en la ficha del paciente.</p></div>
        <div class="actions"><button class="btn primary" type="button" (click)="newTemplate()"><app-icon name="plus" [size]="16" /> Nueva plantilla</button></div>
      </div>
      <div class="grid layout">
        <section class="panel">
          <div class="panel-head"><h3>Plantillas</h3></div>
          <div class="panel-body list">
            @for (t of templates; track t.id) {
              <button type="button" class="it" [class.on]="d?.id === t.id" (click)="open(t)">
                <span class="strong">{{ t.name }}</span>
                <span class="muted small">{{ t.specialty || 'General' }} · {{ t.questions.length }} preguntas</span>
                @if (!t.active) { <span class="tag grey">Inactiva</span> }
              </button>
            }
          </div>
        </section>
        <section class="panel">
          @if (!d) { <div class="empty"><p>Elige una plantilla para editarla o crea una nueva.</p></div> }
          @else {
            <form class="panel-body" (ngSubmit)="save()">
              <div class="grid cols-2">
                <div class="field"><label class="req" for="tn">Nombre</label><input id="tn" name="tn" class="input" [(ngModel)]="d.name" maxlength="80" [class.invalid]="e['name']" appOnly="text" />
                  @if (e['name']) { <span class="err">{{ e['name'] }}</span> }</div>
                <div class="field"><label for="ts">Especialidad</label><input id="ts" name="ts" class="input" [(ngModel)]="d.specialty" maxlength="60" appOnly="letters" /></div>
              </div>
              <label class="check"><input type="checkbox" name="ta" [(ngModel)]="d.active" /> Activa (aparece para llenar)</label>
              @if (e['questions']) { <div class="notice gum"><app-icon name="alert" /> {{ e['questions'] }}</div> }
              <h3 class="qh">Preguntas</h3>
              @for (q of d.questions; track $index; let i = $index) {
                <div class="q">
                  <div class="qtop">
                    <input class="input sec" [name]="'s' + i" [(ngModel)]="q.section" placeholder="Sección" maxlength="60" aria-label="Sección" appOnly="text" />
                    <select class="input type" [name]="'t' + i" [(ngModel)]="q.type" aria-label="Tipo de respuesta">
                      <option value="YES_NO">Sí / No</option><option value="CHOICE">Opciones</option><option value="TEXT">Texto</option>
                    </select>
                    <div class="mv">
                      <button type="button" class="btn ghost sm" (click)="move(i, -1)" [disabled]="i === 0" aria-label="Subir"><app-icon name="arrowUp" [size]="14" /></button>
                      <button type="button" class="btn ghost sm" (click)="move(i, 1)" [disabled]="i === d.questions.length - 1" aria-label="Bajar"><app-icon name="arrowDown" [size]="14" /></button>
                      <button type="button" class="btn ghost sm" (click)="d.questions.splice(i, 1)" aria-label="Quitar pregunta"><app-icon name="x" [size]="14" /></button>
                    </div>
                  </div>
                  <input class="input" [name]="'l' + i" [(ngModel)]="q.label" placeholder="Pregunta" maxlength="150" aria-label="Pregunta" appOnly="text" />
                  @if (q.type === 'CHOICE') {
                    <input class="input" [name]="'o' + i" [ngModel]="(q.options || []).join(', ')" (ngModelChange)="q.options = splitOpts($event)" placeholder="Opciones separadas por coma: Leve, Moderado, Severo" aria-label="Opciones" appOnly="text" maxlength="200" />
                  }
                  @if (q.type === 'YES_NO') { <label class="check small"><input type="checkbox" [name]="'a' + i" [(ngModel)]="q.alert" /> Si responde "Sí", mostrar como alerta clínica</label> }
                </div>
              }
              <button type="button" class="btn sm" (click)="addQ()"><app-icon name="plus" [size]="14" /> Agregar pregunta</button>
              <div class="row end">
                @if (d.updatedAt) { <span class="muted small grow">Editada {{ dt(d.updatedAt) }} por {{ d.updatedBy }}</span> }

                @if (d.id) { <app-delete-button entity="anamnesis_templates" [recordId]="d.id" what="Formulario de anamnesis" [small]="false" (deleted)="d = null; ngOnInit()" /> }                <button type="button" class="btn" (click)="d = null">Cancelar</button>
                <button type="submit" class="btn primary" [disabled]="saving">{{ saving ? 'Guardando…' : 'Guardar plantilla' }}</button>
              </div>
              <p class="muted small">Las fichas ya llenadas conservan las preguntas con las que se respondieron.</p>
            </form>
          }
        </section>
      </div>
    </div>
  `,
  styles: [`
    .layout { grid-template-columns: minmax(220px, 1fr) minmax(0, 2.4fr); align-items: start; }
    @media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }
    .list { display: grid; gap: 6px; }
    .it { display: grid; gap: 2px; text-align: left; border: 1px solid var(--line); background: var(--surface); border-radius: 10px; padding: 8px 10px; font: inherit; cursor: pointer; }
    .it.on { border-color: var(--teal); background: var(--teal-soft); }
    .qh { margin: 16px 0 8px; }
    .q { border: 1px solid var(--line); border-radius: 12px; padding: 10px; display: grid; gap: 6px; margin-bottom: 8px; }
    .qtop { display: flex; gap: 6px; align-items: center; }
    .sec { flex: 1; } .type { width: 140px; }
    .mv { display: flex; }
    .check { display: flex; gap: 8px; align-items: center; margin: 8px 0; }
    .end { justify-content: flex-end; gap: 8px; margin-top: 12px; align-items: center; }
  `],
})
export class AnamnesisTemplatesComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  templates: AnamTemplate[] = [];
  d: any = null;
  e: Record<string, string> = {};
  saving = false;

  ngOnInit() { this.api.anamnesisTemplates(true).subscribe((t) => (this.templates = t)); }
  open(t: AnamTemplate) { this.e = {}; this.d = JSON.parse(JSON.stringify({ ...t, active: !!t.active })); }
  newTemplate() { this.e = {}; this.d = { name: '', specialty: '', active: true, questions: [{ id: '', section: 'General', label: '', type: 'YES_NO', alert: false }] }; }
  addQ() { const last = this.d.questions[this.d.questions.length - 1]; this.d.questions.push({ id: '', section: last?.section || 'General', label: '', type: 'YES_NO', alert: false } as AnamQuestion); }
  move(i: number, dir: number) { const q = this.d.questions; [q[i], q[i + dir]] = [q[i + dir], q[i]]; }
  splitOpts(s: string) { return s.split(',').map((x) => x.trim()).filter(Boolean); }

  save() {
    this.saving = true; this.e = {};
    const body = { name: this.d.name, specialty: this.d.specialty, active: this.d.active, questions: this.d.questions };
    const req = this.d.id ? this.api.updateAnamnesisTemplate(this.d.id, body) : this.api.createAnamnesisTemplate(body);
    req.subscribe({
      next: (t) => {
        this.saving = false; this.toast.ok('Plantilla guardada.');
        this.api.anamnesisTemplates(true).subscribe((all) => { this.templates = all; this.open(all.find((x) => x.id === t.id) || t); });
      },
      error: (err) => { this.saving = false; const a = apiError(err); this.e = a.fields || {}; this.toast.error(a.message); },
    });
  }
  dt = dateTime;
}
