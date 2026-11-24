import { DeleteButtonComponent } from '../../shared/delete-button.component';
import { Component, Input, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { PlanView, Procedure } from '../../core/models';
import { ITEM_LABEL, ITEM_TONE, apiError, isoDate, shortDate, soles } from '../../core/format';
import { IconComponent } from '../../shared/icon.component';
import { ModalComponent } from '../../shared/modal.component';
import { InstallmentsComponent } from './installments.component';

interface DraftItem { procedureId: number | null; tooth: number | null; price: number | null; }

/** HU-06: plan de tratamiento y evolución por sesión. */
@Component({
  selector: 'app-plan-tab',
  standalone: true,
  imports: [DeleteButtonComponent, FormsModule, IconComponent, ModalComponent, InstallmentsComponent],
  template: `
    <div class="spread head">
      <p class="muted small">{{ plans.length }} {{ plans.length === 1 ? 'plan' : 'planes' }} · los procedimientos realizados quedan disponibles para facturar en caja.</p>
      @if (canEdit) { <button class="btn primary" type="button" (click)="openNew()"><app-icon name="plus" [size]="16" /> Nuevo plan</button> }
    </div>

    @if (loaded && plans.length === 0) {
      <div class="panel empty">
        <h3>Sin plan de tratamiento</h3>
        <p>Arma el plan con los procedimientos del diagnóstico para dar seguimiento por sesión.</p>
        @if (canEdit) { <button class="btn primary" type="button" (click)="openNew()">Crear plan de tratamiento</button> }
      </div>
    }

    @for (v of plans; track v.plan.id) {
      <section class="panel plan">
        <div class="panel-head">
          <div>
            <div class="row"><h3>{{ v.plan.title }}</h3><span class="tag" [class]="'tag ' + planTone(v.plan.status)">{{ planLabel(v.plan.status) }}</span> <app-delete-button entity="treatment_plans" [recordId]="v.plan.id" what="Plan de tratamiento" [iconOnly]="true" [ghost]="true" (deleted)="load()" /></div>
            <p class="muted small">{{ v.plan.dentist?.fullName || v.plan.createdBy }} · creado {{ date(v.plan.createdAt) }}@if (v.plan.notes) { · {{ v.plan.notes }} }</p>
          </div>
          <div class="progress" [attr.aria-label]="'Avance ' + v.doneCount + ' de ' + v.itemCount">
            <div class="spread small"><span class="strong">{{ v.doneCount }} de {{ v.itemCount }} realizados</span><span class="tabular">{{ money(v.doneAmount) }} / {{ money(v.total) }}</span></div>
            <div class="track"><div class="fill" [style.width.%]="v.itemCount ? (v.doneCount * 100 / v.itemCount) : 0"></div></div>
          </div>
        </div>

        <div class="cols">
          <table class="table">
            <thead><tr><th>Procedimiento</th><th>Pieza</th><th class="num">Precio</th><th>Estado</th></tr></thead>
            <tbody>
              @for (i of v.items; track i.id) {
                <tr>
                  <td>{{ i.procedure.name }}<div class="muted small">{{ i.procedure.code }}@if (i.invoiceId) { · facturado }</div></td>
                  <td class="tabular">{{ i.tooth || '—' }}</td>
                  <td class="num tabular">{{ money(i.price) }}</td>
                  <td>
                    @if (canEdit && v.plan.status !== 'ANULADO') {
                      <select class="input status" [value]="i.status" (change)="setStatus(v, i.id, $any($event.target).value)" [attr.aria-label]="'Estado de ' + i.procedure.name">
                        <option value="PENDIENTE">Pendiente</option>
                        <option value="EN_PROCESO">En proceso</option>
                        <option value="REALIZADO">Realizado</option>
                      </select>
                    } @else {
                      <span class="tag" [class]="'tag ' + itemTone(i.status)">{{ itemLabel(i.status) }}</span>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>

          <div class="evo">
            <h4>Evolución por sesión</h4>
            @if (canEdit && v.plan.status === 'ACTIVO') {
              <form class="evo-form" (ngSubmit)="addEvolution(v)">
                <div class="grid two">
                  <input class="input" type="date" name="d{{ v.plan.id }}" [(ngModel)]="evo[v.plan.id].sessionDate" [max]="today" aria-label="Fecha de sesión" />
                  <select class="input" name="i{{ v.plan.id }}" [(ngModel)]="evo[v.plan.id].itemId" aria-label="Procedimiento trabajado">
                    <option [ngValue]="null">Sesión general</option>
                    @for (i of v.items; track i.id) { @if (i.status !== 'REALIZADO') { <option [ngValue]="i.id">{{ i.procedure.name }}{{ i.tooth ? ' · ' + i.tooth : '' }}</option> } }
                  </select>
                </div>
                <textarea class="input" rows="2" name="n{{ v.plan.id }}" [(ngModel)]="evo[v.plan.id].notes" placeholder="Qué se realizó, materiales, indicaciones…"></textarea>
                <div class="spread">
                  <label class="check">
                    <input type="checkbox" name="m{{ v.plan.id }}" [(ngModel)]="evo[v.plan.id].markDone" [disabled]="!evo[v.plan.id].itemId" />
                    Procedimiento terminado
                  </label>
                  <button class="btn sm primary" type="submit" [disabled]="busy">Registrar sesión</button>
                </div>
              </form>
            }
            <ol class="evolist">
              @for (e of v.evolutions; track e.id) {
                <li>
                  <span class="date tabular">{{ date(e.sessionDate) }}</span>
                  <div>
                    @if (e.itemId) { <div class="strong small">{{ itemName(v, e.itemId) }}</div> }
                    <div>{{ e.notes }}</div>
                    <div class="muted small">{{ e.dentist?.fullName || e.createdBy }}</div>
                  </div>
                </li>
              } @empty {
                <li class="muted small">Aún no hay sesiones registradas.</li>
              }
            </ol>
          </div>
        </div>

        @if (v.plan.status !== 'ANULADO' && v.total > 0) { <app-installments [planId]="v.plan.id" [canEdit]="canInstall && v.plan.status === 'ACTIVO'" /> }
        @if (canEdit && v.plan.status === 'ACTIVO') {
          <div class="panel-foot row">
            <button class="btn sm" type="button" (click)="openAdd(v)"><app-icon name="plus" [size]="14" /> Agregar procedimiento</button>
            <span class="grow"></span>
            <button class="btn sm danger" type="button" (click)="cancelPlan(v)">Anular plan</button>
          </div>
        }
      </section>
    }

    <app-modal [open]="!!use" title="Materiales usados en esta atención" [subtitle]="use?.label || ''" [width]="640" (close)="use = null; load()">
      @if (use) {
        <div class="stack">
          <p class="muted small">Viene cargado el kit estándar del procedimiento. Ajusta las cantidades a lo que realmente usaste,
            quita lo que no usaste o agrega otro material. Se descuenta del inventario al confirmar.</p>
          @if (use.loading) { <div class="skeleton" style="height: 120px"></div> }
          @else {
            <table class="table use">
              <thead><tr><th>Material</th><th class="right">Cantidad</th><th class="right">Stock</th><th></th></tr></thead>
              <tbody>
                @for (r of use.rows; track $index) {
                  <tr>
                    <td><select class="input" [(ngModel)]="r.itemId" (ngModelChange)="syncRow(r)" [attr.aria-label]="'Material ' + ($index + 1)">
                      <option [ngValue]="null">Elige un material…</option>
                      @for (c of use.catalog; track c.itemId) { <option [ngValue]="c.itemId">{{ c.name }}</option> }
                    </select></td>
                    <td class="right"><input class="input qty tabular" type="number" min="0" step="0.5" [(ngModel)]="r.quantity" [attr.aria-label]="'Cantidad ' + ($index + 1)" /> <span class="muted small">{{ r.unit }}</span></td>
                    <td class="right tabular" [class.gum]="r.stock != null && r.quantity > r.stock">{{ r.stock ?? '—' }}</td>
                    <td class="right"><button class="btn sm ghost" type="button" (click)="use.rows.splice($index, 1)" aria-label="Quitar">✕</button></td>
                  </tr>
                } @empty { <tr><td colspan="4" class="muted small">Este procedimiento no tiene kit. Agrega lo que usaste o confirma sin materiales.</td></tr> }
              </tbody>
            </table>
            <button class="btn sm" type="button" (click)="use.rows.push({ itemId: null, quantity: 1, unit: '', stock: null })"><app-icon name="plus" [size]="14" /> Agregar material</button>
          }
          <div class="row end">
            <button class="btn" type="button" (click)="use = null; load()">Cancelar</button>
            <button class="btn primary" type="button" [disabled]="busy || use.loading" (click)="confirmUse()">Marcar realizado y descontar</button>
          </div>
        </div>
      }
    </app-modal>

    <app-modal [open]="modal" [title]="addTo ? 'Agregar procedimiento' : 'Nuevo plan de tratamiento'" [width]="720" (close)="modal = false">
      <form class="stack" (ngSubmit)="submitModal()">
        @if (!addTo) {
          <div class="field">
            <label class="req" for="pt">Nombre del plan</label>
            <input id="pt" name="pt" class="input" [(ngModel)]="title" placeholder="Ej. Rehabilitación sector posterior" />
          </div>
        }
        <div class="items">
          <div class="ihead"><span>Procedimiento</span><span>Pieza</span><span>Precio S/</span><span></span></div>
          @for (it of draft; track $index) {
            <div class="irow">
              <select class="input" [name]="'p' + $index" [(ngModel)]="it.procedureId" (ngModelChange)="onProc(it)" aria-label="Procedimiento">
                <option [ngValue]="null">Seleccionar…</option>
                @for (g of groups; track g.name) {
                  <optgroup [label]="g.name">
                    @for (p of g.items; track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }
                  </optgroup>
                }
              </select>
              <input class="input tabular" type="number" [name]="'t' + $index" [(ngModel)]="it.tooth" min="11" max="85" placeholder="—" aria-label="Pieza" />
              <input class="input tabular" type="number" [name]="'pr' + $index" [(ngModel)]="it.price" min="0" step="0.5" aria-label="Precio" />
              <button type="button" class="btn ghost sm" (click)="draft.splice($index, 1)" [disabled]="draft.length === 1" aria-label="Quitar fila"><app-icon name="x" [size]="15" /></button>
            </div>
          }
          <button type="button" class="btn sm" (click)="draft.push({ procedureId: null, tooth: null, price: null })"><app-icon name="plus" [size]="14" /> Otra fila</button>
        </div>
        @if (!addTo) {
          <div class="field">
            <label for="pn">Indicaciones</label>
            <textarea id="pn" name="pn" class="input" rows="2" [(ngModel)]="notes"></textarea>
          </div>
        }
        <div class="spread">
          <span class="muted">Total estimado <strong class="tabular">{{ money(draftTotal) }}</strong></span>
          @if (modalError) { <span class="err small">{{ modalError }}</span> }
        </div>
      </form>
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="modal = false">Cancelar</button>
        <button class="btn primary" type="button" (click)="submitModal()" [disabled]="busy">{{ addTo ? 'Agregar al plan' : 'Crear plan' }}</button>
      </ng-container>
    </app-modal>
  `,
  styles: [`
    .use .input { min-width: 0; }
    .use .qty { width: 84px; display: inline-block; }
    .use .gum { color: var(--gum); font-weight: 700; }
    .row.end { justify-content: flex-end; gap: 8px; }
    .head { margin-bottom: 14px; }
    .plan { margin-bottom: 18px; }
    .progress { min-width: 260px; }
    .track { height: 6px; background: var(--surface-2); border: 1px solid var(--line); border-radius: 999px; margin-top: 6px; overflow: hidden; }
    .fill { height: 100%; background: var(--teal); }
    .cols { display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr); }
    .cols > .table { border-right: 1px solid var(--line); }
    .status { height: 32px; font-size: 13px; width: 132px; }
    .evo { padding: 14px 18px; }
    .evo h4 { font-size: var(--step--1); font-weight: 600; margin-bottom: 10px; }
    .evo-form { display: flex; flex-direction: column; gap: 8px; padding-bottom: 14px; border-bottom: 1px solid var(--line); margin-bottom: 8px; }
    .two { grid-template-columns: 150px 1fr; gap: 8px; }
    .evolist { list-style: none; margin: 0; padding: 0; font-size: var(--step--1); }
    .evolist li { display: grid; grid-template-columns: 92px 1fr; gap: 10px; padding: 10px 0; border-bottom: 1px dashed var(--line); }
    .evolist li:last-child { border-bottom: 0; }
    .evolist .date { color: var(--muted); }
    .items { display: flex; flex-direction: column; gap: 8px; }
    .ihead, .irow { display: grid; grid-template-columns: minmax(0, 1fr) 80px 110px 36px; gap: 8px; align-items: center; }
    .ihead { font-size: 12px; font-weight: 600; color: var(--muted); }
    @media (max-width: 1000px) { .cols { grid-template-columns: 1fr; } .cols > .table { border-right: 0; border-bottom: 1px solid var(--line); } }
  `],
})
export class PlanTabComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  private auth = inject(AuthService);

  @Input() patientId!: number;

  plans: PlanView[] = [];
  loaded = false;
  procedures: Procedure[] = [];
  groups: { name: string; items: Procedure[] }[] = [];
  evo: Record<number, { sessionDate: string; itemId: number | null; notes: string; markDone: boolean }> = {};
  busy = false;
  today = isoDate();

  modal = false;
  addTo: PlanView | null = null;
  title = '';
  notes = '';
  draft: DraftItem[] = [];
  modalError = '';

  get canEdit() { return this.auth.can('PLAN_TRATAMIENTO'); }
  get canInstall() { return this.auth.can('PLAN_TRATAMIENTO', 'CAJA_COBRAR'); }

  ngOnInit() {
    this.load();
    this.api.procedures().subscribe((list) => {
      this.procedures = list;
      const map = new Map<string, Procedure[]>();
      for (const p of list) map.set(p.category, [...(map.get(p.category) || []), p]);
      this.groups = [...map.entries()].map(([name, items]) => ({ name, items }));
    });
  }

  load() {
    this.api.plans(this.patientId).subscribe((list) => {
      this.plans = list;
      this.loaded = true;
      for (const v of list) {
        if (!this.evo[v.plan.id]) this.evo[v.plan.id] = { sessionDate: this.today, itemId: null, notes: '', markDone: false };
      }
    });
  }

  private replace(view: PlanView) {
    this.plans = this.plans.map((p) => (p.plan.id === view.plan.id ? view : p));
  }

  /** v2.10: al marcar como realizado se confirma lo que realmente se usó (precargado con el kit del procedimiento). */
  use: { label: string; loading: boolean; rows: { itemId: number | null; quantity: number; unit: string; stock: number | null }[];
    catalog: any[]; done: (supplies: { itemId: number; quantity: number }[]) => void } | null = null;

  private askUsage(itemId: number, label: string, done: (supplies: { itemId: number; quantity: number }[]) => void) {
    this.use = { label, loading: true, rows: [], catalog: [], done };
    this.api.itemSupplies(itemId).subscribe({
      next: (r) => {
        if (!this.use) return;
        this.use.catalog = r.catalog;
        this.use.rows = r.kit.map((k: any) => ({ itemId: k.itemId, quantity: Number(k.quantity), unit: k.unit, stock: Number(k.stock) }));
        this.use.loading = false;
      },
      error: () => { this.use = null; done([]); },
    });
  }

  syncRow(r: { itemId: number | null; unit: string; stock: number | null }) {
    const c = this.use?.catalog.find((x) => x.itemId === r.itemId);
    r.unit = c?.unit || ''; r.stock = c ? Number(c.stock) : null;
  }

  confirmUse() {
    if (!this.use) return;
    const supplies = this.use.rows.filter((r) => r.itemId && Number(r.quantity) > 0).map((r) => ({ itemId: r.itemId!, quantity: Number(r.quantity) }));
    const done = this.use.done;
    this.use = null;
    done(supplies);
  }

  setStatus(v: PlanView, itemId: number, status: string) {
    const item = v.items.find((i) => i.id === itemId);
    if (status === 'REALIZADO' && item?.status !== 'REALIZADO') {
      this.askUsage(itemId, item ? item.procedure.name + (item.tooth ? ' · pieza ' + item.tooth : '') : '', (supplies) => this.saveStatus(itemId, status, supplies));
      return;
    }
    this.saveStatus(itemId, status, null);
  }

  private saveStatus(itemId: number, status: string, supplies: { itemId: number; quantity: number }[] | null) {
    this.api.setItemStatus(itemId, status, supplies).subscribe({
      next: (view) => { this.replace(view); this.toast.ok('Estado actualizado.'); },
      error: (e) => { this.toast.error(apiError(e).message); this.load(); },
    });
  }

  addEvolution(v: PlanView) {
    const f = this.evo[v.plan.id];
    if (!f.notes.trim()) { this.toast.warn('Describe lo realizado en la sesión.'); return; }
    if (f.itemId && f.markDone) {
      const item = v.items.find((i) => i.id === f.itemId);
      this.askUsage(f.itemId, item ? item.procedure.name + (item.tooth ? ' · pieza ' + item.tooth : '') : '', (supplies) => this.saveEvolution(v, supplies));
      return;
    }
    this.saveEvolution(v, null);
  }

  private saveEvolution(v: PlanView, supplies: { itemId: number; quantity: number }[] | null) {
    const f = this.evo[v.plan.id];
    this.busy = true;
    this.api.addEvolution(v.plan.id, { ...f, markDone: f.itemId ? f.markDone : false, supplies }).subscribe({
      next: (view) => {
        this.busy = false;
        this.replace(view);
        this.evo[v.plan.id] = { sessionDate: this.today, itemId: null, notes: '', markDone: false };
        this.toast.ok('Sesión registrada en la evolución.');
      },
      error: (e) => { this.busy = false; this.toast.error(apiError(e).message); },
    });
  }

  cancelPlan(v: PlanView) {
    if (!confirm(`¿Anular el plan "${v.plan.title}"? Los procedimientos pendientes ya no se podrán trabajar.`)) return;
    this.api.cancelPlan(v.plan.id).subscribe({
      next: (view) => { this.replace(view); this.toast.ok('Plan anulado.'); },
      error: (e) => this.toast.error(apiError(e).message),
    });
  }

  itemName(v: PlanView, id: number): string {
    const i = v.items.find((x) => x.id === id);
    return i ? i.procedure.name + (i.tooth ? ' · pieza ' + i.tooth : '') : '';
  }

  openNew() {
    this.addTo = null;
    this.title = '';
    this.notes = '';
    this.draft = [{ procedureId: null, tooth: null, price: null }];
    this.modalError = '';
    this.modal = true;
  }

  openAdd(v: PlanView) {
    this.addTo = v;
    this.draft = [{ procedureId: null, tooth: null, price: null }];
    this.modalError = '';
    this.modal = true;
  }

  onProc(it: DraftItem) {
    const p = this.procedures.find((x) => x.id === it.procedureId);
    if (p) it.price = Number(p.price);
  }

  get draftTotal(): number { return this.draft.reduce((s, i) => s + (Number(i.price) || 0), 0); }

  submitModal() {
    this.modalError = '';
    const items = this.draft.filter((d) => d.procedureId);
    if (!this.addTo && !this.title.trim()) { this.modalError = 'Ponle un nombre al plan.'; return; }
    if (items.length === 0) { this.modalError = 'Agrega al menos un procedimiento.'; return; }
    this.busy = true;
    const done = () => (this.busy = false);
    if (this.addTo) {
      const planId = this.addTo.plan.id;
      let pending = items.length;
      let last: PlanView | null = null;
      for (const it of items) {
        this.api.addPlanItem(planId, it).subscribe({
          next: (view) => { last = view; if (--pending === 0) { done(); this.modal = false; this.load(); this.toast.ok('Procedimientos agregados.'); } },
          error: (e) => { done(); this.modalError = apiError(e).message; },
        });
      }
      return;
    }
    this.api.createPlan(this.patientId, { title: this.title, notes: this.notes, items }).subscribe({
      next: () => { done(); this.modal = false; this.toast.ok('Plan de tratamiento creado.'); this.load(); },
      error: (e) => { done(); this.modalError = apiError(e).message; },
    });
  }

  planLabel(s: string) { return ({ ACTIVO: 'Activo', COMPLETADO: 'Completado', ANULADO: 'Anulado' } as Record<string, string>)[s] || s; }
  planTone(s: string) { return ({ ACTIVO: 'teal', COMPLETADO: 'green', ANULADO: 'grey' } as Record<string, string>)[s] || 'grey'; }
  itemLabel(s: string) { return ITEM_LABEL[s] || s; }
  itemTone(s: string) { return ITEM_TONE[s] || 'grey'; }
  money = soles;
  date = shortDate;
}
