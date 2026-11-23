import { DeleteButtonComponent } from '../shared/delete-button.component';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { apiError, isoDate, shortDate } from '../core/format';
import { ToastService } from '../core/toast.service';
import { ModalComponent } from '../shared/modal.component';
import { TaskFormComponent } from '../shared/task-form.component';

/** v2.7: bandeja de tareas (mías, sin asignar, todas) agrupadas en vencidas, hoy y próximas. */
@Component({
  selector: 'app-tasks',
  standalone: true,
  imports: [DeleteButtonComponent, FormsModule, RouterLink, ModalComponent, TaskFormComponent],
  template: `
    <div class="page">
      <header class="page-head">
        <div><h1>Tareas</h1><p class="muted">Llamadas, cobros y recordatorios de pacientes y leads, con responsable y fecha.</p></div>
        <button class="btn primary" type="button" (click)="edit = null; formOpen = true">+ Nueva tarea</button>
      </header>
      <div class="kpis">
        <button type="button" class="kpi" [class.on]="scope === 'mine'" (click)="setScope('mine')"><span class="n" [class.bad]="counts?.mineOverdue">{{ counts?.mineOverdue ?? '–' }}</span><span>mías vencidas</span></button>
        <button type="button" class="kpi" [class.on]="scope === 'mine'" (click)="setScope('mine')"><span class="n">{{ counts?.mineToday ?? '–' }}</span><span>mías para hoy</span></button>
        <button type="button" class="kpi" [class.on]="scope === 'unassigned'" (click)="setScope('unassigned')"><span class="n">{{ counts?.unassigned ?? '–' }}</span><span>sin asignar</span></button>
        <button type="button" class="kpi" [class.on]="scope === 'all'" (click)="setScope('all')"><span class="n" [class.bad]="counts?.allOverdue">{{ counts?.allOverdue ?? '–' }}</span><span>vencidas en total</span></button>
      </div>
      <div class="filters">
        <div class="seg" role="group" aria-label="De quién">
          <button type="button" [class.on]="scope === 'mine'" (click)="setScope('mine')">Mías</button>
          <button type="button" [class.on]="scope === 'unassigned'" (click)="setScope('unassigned')">Sin asignar</button>
          <button type="button" [class.on]="scope === 'all'" (click)="setScope('all')">Todas</button>
        </div>
        <select class="input" [(ngModel)]="kind" (ngModelChange)="load()" aria-label="Tipo"><option value="">Todos los tipos</option>@for (k of kinds; track k[0]) { <option [value]="k[0]">{{ k[1] }}</option> }</select>
        @if (scope === 'all') {
          <select class="input" [(ngModel)]="assigneeId" (ngModelChange)="load()" aria-label="Responsable"><option [ngValue]="null">Cualquier responsable</option>@for (u of assignees; track u.id) { <option [ngValue]="u.id">{{ u.fullName }}</option> }</select>
        }
        <select class="input" [(ngModel)]="status" (ngModelChange)="load()" aria-label="Estado"><option value="PENDIENTE">Pendientes</option><option value="HECHA">Hechas</option><option value="CANCELADA">Canceladas</option></select>
        <input class="input grow" [(ngModel)]="q" (keyup.enter)="load()" (blur)="load()" placeholder="Buscar por paciente, lead o tarea" aria-label="Buscar" />
      </div>

      @if (loading && !items.length) { <div class="panel"><div class="skeleton" style="height: 220px"></div></div> }
      @else if (!items.length) {
        <div class="panel empty"><h3>{{ status === 'PENDIENTE' ? 'Nada pendiente por aquí' : 'Sin tareas' }}</h3>
          <p>{{ scope === 'mine' ? 'No tienes tareas pendientes asignadas.' : 'No hay tareas con estos filtros.' }}</p></div>
      } @else {
        @for (g of groups; track g.key) {
          @if (g.items.length) {
            <section class="panel grp">
              <div class="panel-head"><h3 [class.bad]="g.key === 'over'">{{ g.label }} · {{ g.items.length }}</h3></div>
              @for (t of g.items; track t.id) {
                <div class="t" [class.over]="t.overdue">
                  @if (t.status === 'PENDIENTE') { <button class="done" type="button" (click)="startClose(t)" [attr.aria-label]="'Marcar como hecha: ' + t.title" title="Marcar como hecha"></button> }
                  @else { <span class="tag plain" [class.green]="t.status === 'HECHA'">{{ t.status === 'HECHA' ? 'Hecha' : 'Cancelada' }}</span> }
                  <div class="grow">
                    <div class="title">{{ t.title }} <span class="tag plain">{{ t.kindLabel }}</span> @if (t.priority === 'ALTA') { <span class="tag gum plain">Alta</span> }
                      @if (t.origin !== 'MANUAL') { <span class="tag plain blue" [title]="t.originRef || ''">{{ originLabel[t.origin] || t.origin }}</span> }</div>
                    <div class="meta">
                      @if (t.patient) { <a [routerLink]="['/pacientes', t.patient.id]">{{ t.patient.fullName }}</a>{{ t.patient.phone ? ' · ' + t.patient.phone : '' }} · }
                      @if (t.lead) { <a routerLink="/embudo" [queryParams]="{ lead: t.lead.id }">Lead: {{ t.lead.fullName }}</a>{{ t.lead.phone ? ' · ' + t.lead.phone : '' }} · }
                      {{ shortDate(t.dueDate) }}{{ t.dueTime ? ' ' + t.dueTime : '' }} · {{ t.assignee?.fullName || 'Sin asignar' }}
                    </div>
                    @if (t.notes) { <div class="notes">{{ t.notes }}</div> }
                    @if (t.result) { <div class="notes">Resultado: {{ t.result }} — {{ t.completedBy }}</div> }
                  </div>
                  <div class="acts">
                    @if (t.status === 'PENDIENTE') {
                      @if (!t.assignee || t.assignee.id !== me) { <button class="btn sm ghost" type="button" (click)="take(t)">Tomarla</button> }
                      <button class="btn sm ghost" type="button" (click)="edit = t; formOpen = true">Editar</button>
                    } @else { <button class="btn sm ghost" type="button" (click)="reopen(t)">Reabrir</button> }
                    <app-delete-button entity="tasks" [recordId]="t.id" what="Tarea" [iconOnly]="true" [ghost]="true" (deleted)="load()" />
                  </div>
                </div>
              }
            </section>
          }
        }
      }
    </div>
    <app-task-form [open]="formOpen" [task]="edit" [askPatient]="!edit" (close)="formOpen = false" (saved)="formOpen = false; load()" />
    <app-modal [open]="!!closing" [title]="closeMode === 'HECHA' ? 'Marcar como hecha' : 'Cancelar tarea'" [subtitle]="closing?.title" [width]="460" (close)="closing = null">
      <div class="field"><label for="rs">{{ closeMode === 'HECHA' ? '¿Qué pasó? (opcional)' : '¿Por qué se cancela?' }}</label>
        <textarea id="rs" class="input" rows="2" [(ngModel)]="result" maxlength="500" placeholder="Ej.: Contestó y agendó para el lunes"></textarea></div>
      @if (closing?.patient && closeMode === 'HECHA') { <p class="muted small">¿Hay que volver a llamar? Crea otra tarea después de cerrarla.</p> }
      <div modal-actions>
        @if (closeMode === 'HECHA') { <button class="btn ghost" type="button" (click)="closeMode = 'CANCELADA'">Cancelar la tarea</button> }
        <button class="btn primary" type="button" (click)="confirmClose()">{{ closeMode === 'HECHA' ? 'Marcar hecha' : 'Cancelar tarea' }}</button>
      </div>
    </app-modal>
  `,
  styles: [`
    .kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 16px; }
    @media (max-width: 800px) { .kpis { grid-template-columns: repeat(2, 1fr); } }
    .kpi { display: grid; gap: 2px; text-align: left; padding: 14px 16px; border: 1px solid var(--line); border-radius: var(--radius); background: var(--surface); font: inherit; cursor: pointer; color: var(--muted); font-size: var(--step--1); }
    .kpi .n { font-size: 26px; font-weight: 800; color: var(--ink); }
    .kpi .n.bad, h3.bad { color: var(--gum); }
    .filters { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 16px; align-items: center; }
    .filters select { width: auto; }
    .filters .grow { min-width: 220px; flex: 1; }
    .grp { margin-bottom: 14px; }
    .t { display: flex; gap: 12px; align-items: flex-start; padding: 12px 18px; border-bottom: 1px solid var(--line); }
    .t:last-child { border-bottom: 0; }
    .t.over .meta { color: var(--gum); }
    .done { flex: none; width: 22px; height: 22px; margin-top: 1px; border-radius: 7px; border: 2px solid var(--line-strong); background: var(--surface); cursor: pointer; }
    .done:hover { border-color: var(--green); background: var(--green-soft); }
    .title { font-weight: 700; display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
    .meta, .notes { font-size: var(--step--1); color: var(--muted); margin-top: 2px; }
    .notes { color: var(--ink-2); }
    .acts { display: flex; gap: 4px; flex: none; }
  `],
})
export class TasksComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  scope = 'mine';
  status = 'PENDIENTE';
  kind = '';
  assigneeId: number | null = null;
  q = '';
  items: any[] = [];
  counts: any = null;
  loading = false;
  kinds: [string, string][] = [];
  assignees: any[] = [];
  me = -1;
  groups: { key: string; label: string; items: any[] }[] = [];
  formOpen = false;
  edit: any = null;
  closing: any = null;
  closeMode: 'HECHA' | 'CANCELADA' = 'HECHA';
  result = '';
  originLabel: Record<string, string> = { SEGMENTO: 'Segmento', CUMPLEANOS: 'Cumpleaños', LEAD: 'Embudo', CAMPANA: 'Campaña' };

  private route = inject(ActivatedRoute);
  ngOnInit() {
    if (this.route.snapshot.queryParamMap.get('nueva')) { this.edit = null; this.formOpen = true; }
    this.api.taskOptions().subscribe((o) => { this.kinds = Object.entries(o.kinds) as [string, string][]; this.assignees = o.assignees; this.me = o.me; });
    this.load();
  }
  setScope(s: string) { this.scope = s; this.assigneeId = null; this.load(); }
  load() {
    this.loading = true;
    this.api.tasks({ scope: this.scope, status: this.status, kind: this.kind || undefined, assigneeId: this.assigneeId ?? undefined, q: this.q.trim() || undefined }).subscribe({
      next: (r) => { this.items = r.items; this.counts = r.counts; this.loading = false; this.group(); },
      error: () => (this.loading = false),
    });
  }
  private group() {
    const today = isoDate();
    if (this.status !== 'PENDIENTE') { this.groups = [{ key: 'closed', label: this.status === 'HECHA' ? 'Hechas' : 'Canceladas', items: this.items }]; return; }
    const tomorrow = isoDate(new Date(Date.now() + 86400000));
    this.groups = [
      { key: 'over', label: 'Vencidas', items: this.items.filter((t) => t.dueDate < today) },
      { key: 'today', label: 'Hoy', items: this.items.filter((t) => t.dueDate === today) },
      { key: 'tomorrow', label: 'Mañana', items: this.items.filter((t) => t.dueDate === tomorrow) },
      { key: 'later', label: 'Próximas', items: this.items.filter((t) => t.dueDate > tomorrow) },
    ];
  }
  startClose(t: any) { this.closing = t; this.closeMode = 'HECHA'; this.result = ''; }
  confirmClose() {
    const t = this.closing;
    const req = this.closeMode === 'HECHA' ? this.api.completeTask(t.id, this.result) : this.api.cancelTask(t.id, this.result);
    req.subscribe({ next: () => { this.closing = null; this.toast.ok(this.closeMode === 'HECHA' ? 'Tarea hecha.' : 'Tarea cancelada.'); this.load(); },
      error: (e) => this.toast.error(apiError(e).message) });
  }
  take(t: any) {
    this.api.updateTask(t.id, { kind: t.kind, title: t.title, notes: t.notes, assigneeId: this.me, dueDate: t.dueDate, dueTime: t.dueTime, priority: t.priority })
      .subscribe({ next: () => { this.toast.ok('Ahora la tarea es tuya.'); this.load(); }, error: (e) => this.toast.error(apiError(e).message) });
  }
  reopen(t: any) { this.api.reopenTask(t.id).subscribe(() => this.load()); }
  shortDate = shortDate;
}
