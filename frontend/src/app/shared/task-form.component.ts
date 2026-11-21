import { Component, EventEmitter, Input, OnChanges, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api.service';
import { apiError, isoDate } from '../core/format';
import { ToastService } from '../core/toast.service';
import { ModalComponent } from './modal.component';
import { PatientPickerComponent } from './patient-picker.component';

/** v2.7: crear o editar una tarea (llamar, cobrar, recordar…) con responsable y fecha. */
@Component({
  selector: 'app-task-form',
  standalone: true,
  imports: [FormsModule, ModalComponent, PatientPickerComponent],
  template: `
    <app-modal [open]="open" [title]="task ? 'Editar tarea' : 'Nueva tarea'" [subtitle]="subtitle" [width]="560" (close)="close.emit()">
      <form class="grid" (ngSubmit)="save()" id="taskForm">
        @if (askPatient) {
          <div class="field"><label>Paciente (opcional)</label><app-patient-picker [value]="patient" (valueChange)="patient = $event" /></div>
        }
        <div class="field"><label class="req">Tipo</label>
          <div class="kinds">@for (k of kindList; track k[0]) { <button type="button" [class.on]="f.kind === k[0]" (click)="pickKind(k[0])">{{ k[1] }}</button> }</div>
          @if (e['kind']) { <span class="err">{{ e['kind'] }}</span> }
        </div>
        <div class="field"><label class="req" for="tt">Qué hay que hacer</label>
          <input id="tt" name="tt" class="input" [(ngModel)]="f.title" maxlength="150" [class.invalid]="e['title']" placeholder="Ej.: Llamar para agendar su control" />
          @if (e['title']) { <span class="err">{{ e['title'] }}</span> }
        </div>
        <div class="cols-3">
          <div class="field"><label class="req" for="td">Fecha</label><input id="td" name="td" type="date" class="input" [(ngModel)]="f.dueDate" [min]="task ? '' : today" [class.invalid]="e['dueDate']" />
            @if (e['dueDate']) { <span class="err">{{ e['dueDate'] }}</span> }</div>
          <div class="field"><label for="th">Hora (opcional)</label><input id="th" name="th" type="time" class="input" [(ngModel)]="f.dueTime" /></div>
          <div class="field"><label for="tp">Prioridad</label><select id="tp" name="tp" class="input" [(ngModel)]="f.priority"><option value="NORMAL">Normal</option><option value="ALTA">Alta</option></select></div>
        </div>
        <div class="field"><label for="ta">Responsable</label>
          <select id="ta" name="ta" class="input" [(ngModel)]="f.assigneeId" [class.invalid]="e['assigneeId']">
            <option [ngValue]="null">Sin asignar (la toma quien pueda)</option>
            @for (u of assignees; track u.id) { <option [ngValue]="u.id">{{ u.fullName }}{{ u.id === me ? ' (yo)' : '' }} · {{ u.role }}</option> }
          </select>
          @if (e['assigneeId']) { <span class="err">{{ e['assigneeId'] }}</span> }
        </div>
        <div class="field"><label for="tn">Notas (opcional)</label><textarea id="tn" name="tn" class="input" rows="2" [(ngModel)]="f.notes" maxlength="1000"></textarea></div>
        @if (error) { <div class="err">{{ error }}</div> }
      </form>
      <div modal-actions>
        <button class="btn" type="button" (click)="close.emit()">Cancelar</button>
        <button class="btn primary" type="submit" form="taskForm" [disabled]="busy">{{ task ? 'Guardar cambios' : 'Crear tarea' }}</button>
      </div>
    </app-modal>
  `,
  styles: [`
    .kinds { display: flex; flex-wrap: wrap; gap: 6px; }
    .kinds button { border: 1px solid var(--line-strong); background: var(--surface); border-radius: 999px; padding: 6px 12px; font: inherit; font-size: var(--step--1); cursor: pointer; }
    .kinds button.on { background: var(--teal-soft); border-color: var(--teal); color: var(--teal-strong); font-weight: 700; }
    .err { color: var(--gum); font-size: 12.5px; }
  `],
})
export class TaskFormComponent implements OnChanges {
  @Input() open = false;
  @Input() task: any = null;
  @Input() patientId: number | null = null;
  @Input() leadId: number | null = null;
  @Input() subtitle = '';
  @Input() askPatient = false;
  @Input() defaultTitle = '';
  @Output() close = new EventEmitter<void>();
  @Output() saved = new EventEmitter<any>();
  private api = inject(Api);
  private toast = inject(ToastService);
  kindList: [string, string][] = [];
  assignees: any[] = [];
  me = -1;
  patient: any = null;
  f: any = {};
  e: Record<string, string> = {};
  error = '';
  busy = false;
  today = isoDate();
  private static opts: any = null;

  ngOnChanges() {
    if (!this.open) return;
    this.e = {}; this.error = ''; this.patient = null;
    const t = this.task;
    this.f = t ? { kind: t.kind, title: t.title, dueDate: t.dueDate, dueTime: t.dueTime || '', priority: t.priority, assigneeId: t.assignee?.id ?? null, notes: t.notes || '' }
      : { kind: 'LLAMAR', title: this.defaultTitle, dueDate: this.today, dueTime: '', priority: 'NORMAL', assigneeId: null, notes: '' };
    const apply = (o: any) => {
      this.kindList = Object.entries(o.kinds) as [string, string][];
      this.assignees = o.assignees; this.me = o.me;
      if (!t && this.f.assigneeId === null && this.assignees.some((u) => u.id === this.me)) this.f.assigneeId = this.me;
    };
    if (TaskFormComponent.opts) apply(TaskFormComponent.opts);
    else this.api.taskOptions().subscribe((o) => { TaskFormComponent.opts = o; apply(o); });
  }
  pickKind(k: string) {
    const auto: Record<string, string> = { LLAMAR: 'Llamar', WHATSAPP: 'Escribir por WhatsApp', COBRAR: 'Cobrar saldo pendiente', RECORDAR: 'Recordar' };
    if (!this.f.title || Object.values(auto).includes(this.f.title)) this.f.title = auto[k] || '';
    this.f.kind = k;
  }
  save() {
    this.e = {}; this.error = ''; this.busy = true;
    const body = { ...this.f, dueTime: this.f.dueTime || null, patientId: this.patient?.id ?? this.patientId, leadId: this.leadId };
    const req = this.task ? this.api.updateTask(this.task.id, body) : this.api.createTask(body);
    req.subscribe({
      next: (r) => { this.busy = false; this.toast.ok(this.task ? 'Tarea actualizada.' : 'Tarea creada.'); this.saved.emit(r); },
      error: (err) => { this.busy = false; const a = apiError(err); this.e = a.fields || {}; this.error = a.fields ? '' : a.message; },
    });
  }
}
