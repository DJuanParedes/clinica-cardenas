import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api, InventoryItem } from '../core/api.service';
import { ToastService } from '../core/toast.service';
import { apiError, dateTime } from '../core/format';
import { IconComponent } from '../shared/icon.component';
import { ModalComponent } from '../shared/modal.component';

const MOVE_LABEL: Record<string, string> = { ENTRADA: 'Entrada', SALIDA: 'Salida', AJUSTE: 'Ajuste', CONSUMO: 'Consumo en tratamiento' };

/** Inventario de materiales: stock, mínimos, entradas/salidas y consumo automático por procedimiento. */
@Component({
  selector: 'app-inventory',
  standalone: true,
  imports: [FormsModule, IconComponent, ModalComponent],
  template: `
    <div class="page">
      <header class="page-head spread">
        <div>
          <h1>Inventario</h1>
          <p class="muted">El stock baja solo cuando un procedimiento se marca como realizado. Registra aquí compras y salidas.</p>
        </div>
        <div class="row">
          <button class="btn" type="button" (click)="openSupplies()"><app-icon name="tooth" [size]="16" /> Consumo por procedimiento</button>
          <button class="btn primary" type="button" (click)="openNew()"><app-icon name="plus" [size]="16" /> Nuevo material</button>
        </div>
      </header>

      @if (lowCount > 0) {
        <div class="notice amber" role="status"><app-icon name="alert" />
          <div><strong>{{ lowCount }} {{ lowCount === 1 ? 'material está' : 'materiales están' }} por agotarse.</strong> Revisa la lista y registra la compra.</div>
        </div>
      }

      <div class="row tools">
        <input class="input search" type="search" [(ngModel)]="q" placeholder="Buscar material" aria-label="Buscar material" />
        <label class="check"><input type="checkbox" [(ngModel)]="onlyLow" /> Solo por agotarse</label>
      </div>

      <section class="panel">
        <table class="table">
          <thead><tr><th>Material</th><th class="num">Stock</th><th class="num">Mínimo</th><th>Estado</th><th class="right">Movimiento</th></tr></thead>
          <tbody>
            @for (i of shown; track i.id) {
              <tr [class.off]="!i.active">
                <td><button type="button" class="link strong" (click)="edit(i)">{{ i.name }}</button><div class="muted small">{{ i.unit }}</div></td>
                <td class="num tabular strong">{{ n(i.stock) }}</td>
                <td class="num tabular muted">{{ n(i.minStock) }}</td>
                <td>
                  @if (!i.active) { <span class="tag grey">Inactivo</span> }
                  @else if (i.low) { <span class="tag gum">Por agotarse</span> }
                  @else { <span class="tag green">Suficiente</span> }
                </td>
                <td class="right nowrap">
                  <button class="btn sm" type="button" (click)="openMove(i, 'ENTRADA')"><app-icon name="arrowDown" [size]="14" /> Entrada</button>
                  <button class="btn sm" type="button" (click)="openMove(i, 'SALIDA')"><app-icon name="arrowUp" [size]="14" /> Salida</button>
                </td>
              </tr>
            } @empty {
              <tr><td colspan="5" class="muted center">No hay materiales que coincidan.</td></tr>
            }
          </tbody>
        </table>
      </section>

      <section class="panel">
        <div class="panel-head"><h3>Últimos movimientos</h3></div>
        <table class="table">
          <tbody>
            @for (m of recent; track m.id) {
              <tr>
                <td class="muted small nowrap tabular">{{ dt(m.createdAt) }}</td>
                <td>{{ m.item }}<div class="muted small">{{ moveLabel(m.kind) }}@if (m.reason) { · {{ m.reason }} }</div></td>
                <td class="num tabular" [class.plus]="+m.quantity > 0" [class.minus]="+m.quantity < 0">{{ +m.quantity > 0 ? '+' : '' }}{{ n(m.quantity) }}</td>
                <td class="num tabular muted">queda {{ n(m.stockAfter) }}</td>
                <td class="muted small">{{ m.createdBy }}</td>
              </tr>
            } @empty { <tr><td class="muted">Aún no hay movimientos.</td></tr> }
          </tbody>
        </table>
      </section>
    </div>

    <app-modal [open]="!!moveFor" [title]="(move.kind === 'ENTRADA' ? 'Entrada de ' : move.kind === 'SALIDA' ? 'Salida de ' : 'Ajuste de ') + (moveFor?.name || '')"
               [subtitle]="'Stock actual: ' + n(moveFor?.stock) + ' ' + (moveFor?.unit || '')" (close)="moveFor = null">
      <form class="stack" (ngSubmit)="saveMove()">
        <div class="seg">
          @for (k of ['ENTRADA', 'SALIDA', 'AJUSTE']; track k) { <button type="button" [class.on]="move.kind === k" (click)="move.kind = k">{{ moveLabel(k) }}</button> }
        </div>
        <div class="field"><label for="mq">{{ move.kind === 'AJUSTE' ? 'Stock contado (queda en)' : 'Cantidad' }}</label>
          <input id="mq" class="input" type="number" min="0" step="0.01" name="q" [(ngModel)]="move.quantity" /></div>
        <div class="field"><label for="mr">Motivo</label>
          <input id="mr" class="input" name="r" [(ngModel)]="move.reason" maxlength="200" [placeholder]="move.kind === 'ENTRADA' ? 'Compra, proveedor, factura…' : 'Vencido, roto, inventario…'" /></div>
      </form>
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="moveFor = null">Cancelar</button>
        <button class="btn primary" type="button" (click)="saveMove()" [disabled]="busy">Registrar</button>
      </ng-container>
    </app-modal>

    <app-modal [open]="itemOpen" [title]="itemId ? 'Editar material' : 'Nuevo material'" (close)="itemOpen = false">
      <form class="stack" (ngSubmit)="saveItem()">
        <div class="field"><label for="in">Nombre</label><input id="in" class="input" name="name" [(ngModel)]="item.name" maxlength="120" /></div>
        <div class="grid two">
          <div class="field"><label for="iu">Unidad</label><input id="iu" class="input" name="unit" [(ngModel)]="item.unit" maxlength="20" placeholder="unidad, caja, par…" /></div>
          <div class="field"><label for="im">Stock mínimo</label><input id="im" class="input" type="number" min="0" name="min" [(ngModel)]="item.minStock" /></div>
        </div>
        @if (!itemId) {
          <div class="field"><label for="is">Stock inicial</label><input id="is" class="input" type="number" min="0" name="init" [(ngModel)]="item.initialStock" /></div>
        } @else {
          <label class="check"><input type="checkbox" name="act" [(ngModel)]="item.active" /> Material activo</label>
        }
      </form>
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="itemOpen = false">Cancelar</button>
        <button class="btn primary" type="button" (click)="saveItem()" [disabled]="busy">Guardar</button>
      </ng-container>
    </app-modal>

    <app-modal [open]="suppliesOpen" title="Consumo por procedimiento" subtitle="Lo que se descuenta del stock cada vez que se realiza el procedimiento." [width]="760" (close)="suppliesOpen = false">
      <div class="stack">
        <select class="input" [(ngModel)]="procId" (ngModelChange)="pickProc()" aria-label="Procedimiento">
          @for (p of procedures; track p.id) { <option [ngValue]="p.id">{{ p.code }} · {{ p.name }}</option> }
        </select>
        <table class="table">
          <tbody>
            @for (r of rows; track $index) {
              <tr>
                <td><select class="input" [(ngModel)]="r.itemId" aria-label="Material">
                  @for (i of items; track i.id) { <option [ngValue]="i.id">{{ i.name }}</option> }
                </select></td>
                <td style="width:120px"><input class="input" type="number" min="0" step="0.05" [(ngModel)]="r.quantity" aria-label="Cantidad" /></td>
                <td style="width:40px"><button class="btn ghost sm" type="button" (click)="rows.splice($index, 1)" aria-label="Quitar"><app-icon name="x" [size]="14" /></button></td>
              </tr>
            } @empty { <tr><td class="muted">Este procedimiento no descuenta materiales.</td></tr> }
          </tbody>
        </table>
        <button class="btn sm" type="button" (click)="rows.push({ itemId: items[0]?.id, quantity: 1 })"><app-icon name="plus" [size]="14" /> Agregar material</button>
      </div>
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="suppliesOpen = false">Cerrar</button>
        <button class="btn primary" type="button" (click)="saveSupplies()" [disabled]="busy || !procId">Guardar consumo</button>
      </ng-container>
    </app-modal>
  `,
  styles: [`
    .page { display: flex; flex-direction: column; gap: 16px; }
    .tools { gap: 14px; flex-wrap: wrap; }
    .search { max-width: 320px; }
    .link { border: 0; background: none; padding: 0; font: inherit; color: var(--ink); cursor: pointer; text-align: left; }
    .link:hover { color: var(--teal-strong); text-decoration: underline; }
    .off { opacity: .55; }
    .nowrap { white-space: nowrap; }
    .plus { color: var(--green-ink, #1f7a4d); font-weight: 700; }
    .minus { color: var(--gum-ink, #b4234a); font-weight: 700; }
    .center { text-align: center; padding: 24px; }
    .seg { display: inline-flex; border: 1px solid var(--line); border-radius: 10px; overflow: hidden; align-self: flex-start; }
    .seg button { border: 0; background: var(--surface); padding: 7px 14px; font: inherit; font-size: 13.5px; cursor: pointer; }
    .seg button.on { background: var(--brand); color: #fff; font-weight: 700; }
  `],
})
export class InventoryComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  items: InventoryItem[] = [];
  recent: any[] = [];
  lowCount = 0;
  q = '';
  onlyLow = false;
  busy = false;

  moveFor: InventoryItem | null = null;
  move = { kind: 'ENTRADA', quantity: 1 as number, reason: '' };
  itemOpen = false;
  itemId: number | null = null;
  item = { name: '', unit: 'unidad', minStock: 0, initialStock: 0, active: true };

  suppliesOpen = false;
  procedures: { id: number; code: string; name: string }[] = [];
  allSupplies: any[] = [];
  procId: number | null = null;
  rows: { itemId: number; quantity: number }[] = [];

  get shown() {
    const q = this.q.trim().toLowerCase();
    return this.items.filter((i) => (!q || i.name.toLowerCase().includes(q)) && (!this.onlyLow || i.low));
  }

  ngOnInit() { this.load(); }
  load() {
    this.api.inventory().subscribe((r) => { this.items = r.items; this.recent = r.recent; this.lowCount = r.lowCount; });
  }

  n(v: any) { const x = +v; return Number.isFinite(x) ? (Number.isInteger(x) ? String(x) : x.toFixed(2)) : '—'; }
  dt = dateTime;
  moveLabel(k: string) { return MOVE_LABEL[k] || k; }

  openMove(i: InventoryItem, kind: string) { this.moveFor = i; this.move = { kind, quantity: kind === 'AJUSTE' ? +i.stock : 1, reason: '' }; }
  saveMove() {
    if (!this.moveFor || this.busy) return;
    this.busy = true;
    this.api.inventoryMove(this.moveFor.id, { kind: this.move.kind, quantity: +this.move.quantity, reason: this.move.reason }).subscribe({
      next: () => { this.busy = false; this.moveFor = null; this.toast.ok('Movimiento registrado.'); this.load(); },
      error: (e) => { this.busy = false; this.toast.error(apiError(e).message); },
    });
  }

  openNew() { this.itemId = null; this.item = { name: '', unit: 'unidad', minStock: 0, initialStock: 0, active: true }; this.itemOpen = true; }
  edit(i: InventoryItem) { this.itemId = i.id; this.item = { name: i.name, unit: i.unit, minStock: +i.minStock, initialStock: 0, active: i.active }; this.itemOpen = true; }
  saveItem() {
    if (this.busy) return;
    this.busy = true;
    const req = this.itemId ? this.api.updateInventoryItem(this.itemId, this.item) : this.api.createInventoryItem(this.item);
    req.subscribe({
      next: () => { this.busy = false; this.itemOpen = false; this.toast.ok('Material guardado.'); this.load(); },
      error: (e) => { this.busy = false; this.toast.error(apiError(e).message); },
    });
  }

  openSupplies() {
    this.suppliesOpen = true;
    this.api.procedures().subscribe((p) => {
      this.procedures = p as any;
      if (!this.procId && p.length) this.procId = p[0].id;
      this.api.supplies().subscribe((s) => { this.allSupplies = s; this.pickProc(); });
    });
  }
  pickProc() {
    this.rows = this.allSupplies.filter((s) => s.procedureId === this.procId).map((s) => ({ itemId: s.itemId, quantity: +s.quantity }));
  }
  saveSupplies() {
    if (!this.procId) return;
    this.busy = true;
    this.api.saveSupplies(this.procId, this.rows.filter((r) => r.itemId && +r.quantity > 0)).subscribe({
      next: () => {
        this.busy = false; this.toast.ok('Consumo guardado.');
        this.api.supplies().subscribe((s) => (this.allSupplies = s));
      },
      error: (e) => { this.busy = false; this.toast.error(apiError(e).message); },
    });
  }
}
