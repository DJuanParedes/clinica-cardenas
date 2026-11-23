import { Component, Input, OnChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { apiError, dateTime } from '../core/format';
import { ToastService } from '../core/toast.service';
import { ModalComponent } from './modal.component';

/** v2.8: franja fija arriba de la ficha: alergias, medicación y notas fijadas. Se ve en todas las pestañas. */
@Component({
  selector: 'app-patient-flags',
  standalone: true,
  imports: [FormsModule, ModalComponent],
  template: `
    @if (f) {
      <div class="flags" role="region" aria-label="Alertas y notas del paciente">
        @if (f.allergies) { <span class="chip red" [title]="f.allergies">⚠ Alergias: {{ f.allergies }}</span> }
        @else if (f.hasMedicalAlert && !clinical) { <span class="chip red">⚠ Tiene alertas médicas</span> }
        @if (f.medication) { <span class="chip amber" [title]="f.medication">💊 {{ f.medication }}</span> }
        @for (n of pinned(); track n.id) {
          <button type="button" class="chip" [class.yellow]="n.tone === 'AMARILLO'" [class.red]="n.tone === 'ROJO'" [class.blue]="n.tone === 'AZUL'" (click)="openNotes()" [title]="'Por ' + n.createdBy">📌 {{ n.text }}</button>
        }
        @if (canEdit) { <button type="button" class="add" (click)="openNotes()">{{ f.notes.length ? 'Notas (' + f.notes.length + ')' : '+ Nota fija' }}</button> }
      </div>
    }
    <app-modal [open]="open" title="Notas del paciente" subtitle="Las fijadas se ven arriba en toda la ficha" [width]="560" (close)="open = false">
      <form class="new" (ngSubmit)="add()">
        <input class="input" name="nt" [(ngModel)]="text" maxlength="500" placeholder="Ej.: Prefiere citas en la tarde · Viene con su hija · Pide factura" />
        <select class="input tone" name="tn" [(ngModel)]="tone" aria-label="Color"><option value="AMARILLO">Aviso</option><option value="ROJO">Importante</option><option value="AZUL">Dato útil</option></select>
        <button class="btn primary" type="submit" [disabled]="!text.trim()">Agregar</button>
      </form>
      @if (error) { <p class="err">{{ error }}</p> }
      <ul class="list">
        @for (n of f?.notes || []; track n.id) {
          <li [class]="'t-' + n.tone">
            <div class="grow"><div>{{ n.text }}</div><div class="muted small">{{ n.updatedBy || n.createdBy }} · {{ dt(n.updatedAt || n.createdAt) }}</div></div>
            <label class="check small"><input type="checkbox" [checked]="n.pinned" (change)="toggle(n)" /> Fijada</label>
            @if (auth.can('ELIMINAR_REGISTROS')) { <button class="btn sm ghost" type="button" (click)="del(n)" aria-label="Borrar nota">Borrar</button> }
          </li>
        } @empty { <li class="muted small">Sin notas.</li> }
      </ul>
    </app-modal>
  `,
  styles: [`
    .flags { position: sticky; top: 0; z-index: 5; display: flex; flex-wrap: wrap; gap: 6px; padding: 8px 0; margin-bottom: 8px; background: var(--bg, #f4f8fa); }
    .chip { display: inline-flex; align-items: center; gap: 4px; max-width: 420px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
      border: 1px solid var(--line); border-radius: 999px; padding: 4px 11px; font: inherit; font-size: 12.5px; font-weight: 600; background: var(--surface); cursor: default; }
    button.chip { cursor: pointer; }
    .chip.red { background: var(--gum-soft); color: var(--gum); border-color: #efc3cf; }
    .chip.amber { background: var(--amber-soft); color: var(--amber); border-color: #ecd3a6; }
    .chip.yellow { background: #fff6d6; color: #7a5b00; border-color: #f0dc8f; }
    .chip.blue { background: var(--restore-soft); color: var(--restore); border-color: #c5d4ee; }
    .add { border: 1px dashed var(--line-strong); background: transparent; border-radius: 999px; padding: 4px 11px; font: inherit; font-size: 12.5px; color: var(--muted); cursor: pointer; }
    .new { display: flex; gap: 8px; margin-bottom: 12px; } .new .input { flex: 1; } .new .tone { flex: none; width: auto; }
    .list { list-style: none; margin: 0; padding: 0; display: grid; gap: 6px; }
    .list li { display: flex; gap: 10px; align-items: center; padding: 8px 10px; border-radius: 10px; border: 1px solid var(--line); }
    .t-AMARILLO { background: #fffbea; } .t-ROJO { background: var(--gum-soft); } .t-AZUL { background: var(--restore-soft); }
    .grow { flex: 1; } .err { color: var(--gum); }
  `],
})
export class PatientFlagsComponent implements OnChanges {
  @Input() patientId!: number;
  private api = inject(Api);
  private toast = inject(ToastService);
  auth = inject(AuthService);
  f: any = null;
  open = false;
  text = '';
  tone = 'AMARILLO';
  error = '';
  get clinical() { return this.auth.can('HISTORIA_CLINICA'); }
  get canEdit() { return this.auth.can('PACIENTES_EDITAR', 'HISTORIA_CLINICA'); }
  ngOnChanges() { this.api.patientFlags(this.patientId).subscribe((f) => (this.f = f)); }
  pinned() { return (this.f?.notes || []).filter((n: any) => n.pinned); }
  openNotes() { this.error = ''; this.open = true; }
  add() {
    this.api.addPatientNote(this.patientId, { text: this.text, tone: this.tone, pinned: true }).subscribe({
      next: (f) => { this.f = f; this.text = ''; }, error: (e) => (this.error = apiError(e).message) });
  }
  toggle(n: any) { this.api.updatePatientNote(this.patientId, n.id, { text: n.text, tone: n.tone, pinned: !n.pinned }).subscribe((f) => (this.f = f)); }
  del(n: any) {
    if (!confirm('¿Borrar la nota?')) return;
    this.api.deletePatientNote(this.patientId, n.id).subscribe({ next: (f) => { this.f = f; this.toast.ok('Nota borrada.'); }, error: (e) => this.toast.error(apiError(e).message) });
  }
  dt = dateTime;
}
