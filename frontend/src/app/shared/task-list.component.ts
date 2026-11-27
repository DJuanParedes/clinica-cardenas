import { Component, Input, OnChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api.service';
import { apiError, shortDate } from '../core/format';
import { ToastService } from '../core/toast.service';
import { ModalComponent } from './modal.component';
import { TaskFormComponent } from './task-form.component';

import { OnlyDirective } from './only.directive';
/** v2.7: tareas de un paciente o de un lead, para su ficha. */
@Component({
  selector: 'app-task-list',
  standalone: true,
  imports: [OnlyDirective, FormsModule, ModalComponent, TaskFormComponent],
  template: `
    <section class="panel">
      <div class="panel-head"><h3>Tareas{{ pending.length ? ' · ' + pending.length + ' pendiente' + (pending.length > 1 ? 's' : '') : '' }}</h3>
        <button class="btn sm" type="button" (click)="edit = null; formOpen = true">+ Tarea</button></div>
      <div class="list">
        @for (t of pending; track t.id) {
          <div class="t" [class.over]="t.overdue">
            <button class="done" type="button" title="Marcar como hecha" (click)="closing = t; closeMode = 'HECHA'; result = ''" [attr.aria-label]="'Marcar como hecha: ' + t.title"></button>
            <div class="grow">
              <div class="title">{{ t.title }} @if (t.priority === 'ALTA') { <span class="tag gum plain">Alta</span> }</div>
              <div class="meta">{{ t.kindLabel }} · {{ t.overdue ? 'venció el ' : '' }}{{ shortDate(t.dueDate) }}{{ t.dueTime ? ' ' + t.dueTime : '' }} · {{ t.assignee?.fullName || 'Sin asignar' }}</div>
              @if (t.notes) { <div class="notes">{{ t.notes }}</div> }
            </div>
            <button class="link" type="button" (click)="edit = t; formOpen = true">Editar</button>
          </div>
        } @empty { <p class="muted small pad">Sin tareas pendientes.</p> }
        @if (closed.length) {
          <button class="link pad" type="button" (click)="showClosed = !showClosed">{{ showClosed ? 'Ocultar' : 'Ver' }} {{ closed.length }} cerrada{{ closed.length > 1 ? 's' : '' }}</button>
          @if (showClosed) {
            @for (t of closed; track t.id) {
              <div class="t closed"><span class="tag plain" [class.green]="t.status === 'HECHA'">{{ t.status === 'HECHA' ? 'Hecha' : 'Cancelada' }}</span>
                <div class="grow"><div class="title">{{ t.title }}</div><div class="meta">{{ t.completedBy }} · {{ shortDate(t.completedAt) }}{{ t.result ? ' · ' + t.result : '' }}</div></div>
                <button class="link" type="button" (click)="reopen(t)">Reabrir</button></div>
            }
          }
        }
      </div>
    </section>
    <app-task-form [open]="formOpen" [task]="edit" [patientId]="patientId" [leadId]="leadId" [subtitle]="subtitle" [defaultTitle]="defaultTitle"
      (close)="formOpen = false" (saved)="formOpen = false; load()" />
    <app-modal [open]="!!closing" [title]="closeMode === 'HECHA' ? 'Marcar como hecha' : 'Cancelar tarea'" [subtitle]="closing?.title" [width]="460" (close)="closing = null">
      <div class="field"><label for="rs">{{ closeMode === 'HECHA' ? '¿Qué pasó? (opcional)' : '¿Por qué se cancela?' }}</label>
        <textarea id="rs" class="input" rows="2" [(ngModel)]="result" maxlength="500" placeholder="Ej.: Contestó, agenda el lunes" appOnly="text"></textarea></div>
      <div modal-actions>
        @if (closeMode === 'HECHA') { <button class="btn ghost" type="button" (click)="closeMode = 'CANCELADA'">Cancelar la tarea</button> }
        <button class="btn primary" type="button" (click)="confirmClose()">{{ closeMode === 'HECHA' ? 'Marcar hecha' : 'Cancelar tarea' }}</button>
      </div>
    </app-modal>
  `,
  styles: [`
    .list { display: grid; }
    .t { display: flex; gap: 10px; align-items: flex-start; padding: 10px 18px; border-bottom: 1px solid var(--line); }
    .t:last-child { border-bottom: 0; }
    .t.over .meta { color: var(--gum); font-weight: 600; }
    .t.closed { opacity: .75; }
    .done { flex: none; width: 20px; height: 20px; margin-top: 2px; border-radius: 6px; border: 2px solid var(--line-strong); background: var(--surface); cursor: pointer; }
    .done:hover { border-color: var(--green); background: var(--green-soft); }
    .title { font-weight: 600; font-size: var(--step--1); display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
    .meta, .notes { font-size: 12.5px; color: var(--muted); }
    .notes { color: var(--ink-2); margin-top: 2px; }
    .link { background: none; border: 0; color: var(--teal-strong); font: inherit; font-size: 12.5px; cursor: pointer; padding: 0; }
    .pad { padding: 10px 18px; }
  `],
})
export class TaskListComponent implements OnChanges {
  @Input() patientId: number | null = null;
  @Input() leadId: number | null = null;
  @Input() subtitle = '';
  @Input() defaultTitle = '';
  private api = inject(Api);
  private toast = inject(ToastService);
  pending: any[] = [];
  closed: any[] = [];
  showClosed = false;
  formOpen = false;
  edit: any = null;
  closing: any = null;
  closeMode: 'HECHA' | 'CANCELADA' = 'HECHA';
  result = '';
  ngOnChanges() { this.load(); }
  load() {
    if (!this.patientId && !this.leadId) return;
    this.api.tasks({ patientId: this.patientId ?? undefined, leadId: this.leadId ?? undefined, status: 'TODAS', scope: 'all' }).subscribe((r) => {
      this.pending = r.items.filter((t) => t.status === 'PENDIENTE').sort((a, b) => (a.dueDate + (a.dueTime || '')).localeCompare(b.dueDate + (b.dueTime || '')));
      this.closed = r.items.filter((t) => t.status !== 'PENDIENTE');
    });
  }
  confirmClose() {
    const t = this.closing;
    const req = this.closeMode === 'HECHA' ? this.api.completeTask(t.id, this.result) : this.api.cancelTask(t.id, this.result);
    req.subscribe({ next: () => { this.closing = null; this.toast.ok(this.closeMode === 'HECHA' ? 'Tarea hecha.' : 'Tarea cancelada.'); this.load(); },
      error: (e) => this.toast.error(apiError(e).message) });
  }
  reopen(t: any) { this.api.reopenTask(t.id).subscribe(() => this.load()); }
  shortDate = shortDate;
}
