import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api, InventoryItem } from '../core/api.service';
import { ToastService } from '../core/toast.service';
import { apiError, dateTime, isoDate, shortDate, soles } from '../core/format';
import { IconComponent } from '../shared/icon.component';
import { PagerComponent, pageOf } from '../shared/pager.component';
import { ProductThumbComponent } from '../shared/product-thumb.component';
import { ModalComponent } from '../shared/modal.component';

const MOVE_LABEL: Record<string, string> = { ENTRADA: 'Entrada', SALIDA: 'Salida', AJUSTE: 'Ajuste', CONSUMO: 'Consumo en tratamiento' };

/** Inventario de materiales: stock, mínimos, entradas/salidas y consumo automático por procedimiento. */
@Component({
  selector: 'app-inventory',
  standalone: true,
  imports: [FormsModule, IconComponent, ModalComponent, PagerComponent, ProductThumbComponent],
  template: `
    <div class="page">
      <header class="page-head spread">
        <div>
          <h1>Inventario</h1>
          <p class="muted">Stock por lotes con vencimiento. Las compras entran con su factura y generan el egreso en caja; el consumo baja solo al realizar un procedimiento (primero el lote que vence antes).</p>
        </div>
        <div class="row wrap">
          <button class="btn" type="button" (click)="openSupplies()"><app-icon name="tooth" [size]="16" /> Consumo por procedimiento</button>
          <button class="btn" type="button" (click)="openPurchase()"><app-icon name="receipt" [size]="16" /> Registrar compra</button>
          <button class="btn primary" type="button" (click)="openNew()"><app-icon name="plus" [size]="16" /> Nuevo material</button>
        </div>
      </header>

      <div class="kpis">
        <div class="kpi"><span class="muted small">Materiales activos</span><strong>{{ activeCount }}</strong></div>
        <div class="kpi" [class.warn]="lowCount"><span class="muted small">Por agotarse</span><strong>{{ lowCount }}</strong></div>
        <div class="kpi" [class.bad]="expiredCount" [class.warn]="!expiredCount && expiring.length"><span class="muted small">Vencidos / por vencer (60 días)</span><strong>{{ expiredCount }} / {{ expiring.length - expiredCount }}</strong></div>
        <div class="kpi"><span class="muted small">Valor del inventario (al costo)</span><strong>{{ money(value) }}</strong></div>
        <div class="kpi" [class.warn]="pendingRequests"><span class="muted small">Solicitudes pendientes</span><strong>{{ pendingRequests }}</strong></div>
      </div>

      <nav class="tabs" aria-label="Secciones del inventario">
        <button type="button" [class.on]="tab === 'items'" (click)="tab = 'items'">Materiales</button>
        <button type="button" [class.on]="tab === 'exp'" (click)="tab = 'exp'">Vencimientos @if (expiring.length) { <span class="count">{{ expiring.length }}</span> }</button>
        <button type="button" [class.on]="tab === 'buy'" (click)="tab = 'buy'; loadPurchases()">Compras</button>
        <button type="button" [class.on]="tab === 'req'" (click)="tab = 'req'; loadRequests()">Solicitudes de odontólogos @if (pendingRequests) { <span class="count">{{ pendingRequests }}</span> }</button>
        <button type="button" [class.on]="tab === 'moves'" (click)="tab = 'moves'">Movimientos</button>
      </nav>

      @if (tab === 'items') {
      @if (lowCount > 0) {
        <div class="notice amber" role="status"><app-icon name="alert" />
          <div><strong>{{ lowCount }} {{ lowCount === 1 ? 'material está' : 'materiales están' }} por agotarse.</strong> Revisa la lista y registra la compra.</div>
        </div>
      }

      <div class="row tools">
        <input class="input search" type="search" [(ngModel)]="q" (ngModelChange)="pgItems = 1" placeholder="Buscar por nombre, marca o proveedor" aria-label="Buscar material" />
        <select class="input" style="max-width: 200px" [(ngModel)]="cat" (ngModelChange)="pgItems = 1" aria-label="Tipo">
          <option value="">Todos los tipos</option><option value="INSUMO">Insumos</option><option value="INSTRUMENTAL">Instrumental</option><option value="MEDICAMENTO">Medicamentos</option>
        </select>
        <label class="check"><input type="checkbox" [(ngModel)]="onlyLow" /> Solo por agotarse</label>
      </div>

      <section class="panel">
        <table class="table">
          <thead><tr><th>Material</th><th>Tipo</th><th>Marca / proveedor</th><th class="num">Stock</th><th class="num">Mínimo</th><th>Vence</th><th>Estado</th><th class="right">Acciones</th></tr></thead>
          <tbody>
            @for (i of pageOf(shown, pgItems); track i.id) {
              <tr [class.off]="!i.active">
                <td><div class="prod"><button type="button" class="ph" (click)="edit(i)" [attr.aria-label]="'Ver ' + i.name"><app-product-thumb [src]="i.imageUrl" [name]="i.name" [size]="48" /></button>
                  <div><button type="button" class="link strong" (click)="edit(i)">{{ i.name }}</button><div class="muted small">{{ i.unit }}{{ i.unitCost != null ? ' · ' + money(i.unitCost) + ' c/u' : '' }}</div></div></div></td>
                <td><span class="tag" [class.teal]="i.category === 'INSUMO'" [class.grey]="i.category === 'INSTRUMENTAL'" [class.amber]="i.category === 'MEDICAMENTO'">{{ catLabel(i.category) }}</span></td>
                <td class="small">{{ i.brand || '—' }}<div class="muted">{{ i.supplier || '' }}</div></td>
                <td class="num tabular strong">{{ n(i.stock) }}</td>
                <td class="num tabular muted">{{ n(i.minStock) }}</td>
                <td class="small nowrap">
                  @if (nextExpiry[i.id]; as d) { <span [class.minus]="daysTo(d) < 0" [class.warnt]="daysTo(d) >= 0 && daysTo(d) <= 60">{{ date(d) }}</span> }
                  @else { <span class="muted">{{ i.tracksExpiry === false ? 'No aplica' : '—' }}</span> }
                </td>
                <td>
                  @if (!i.active) { <span class="tag grey">Inactivo</span> }
                  @else if (i.low) { <span class="tag gum">Por agotarse</span> }
                  @else { <span class="tag green">Suficiente</span> }
                </td>
                <td class="right nowrap">
                  <button class="btn sm ghost" type="button" (click)="openLots(i)" title="Ver lotes"><app-icon name="eye" [size]="14" /> Ver</button>
                  <button class="btn sm" type="button" (click)="openMove(i, 'ENTRADA')"><app-icon name="arrowDown" [size]="14" /> Entrada</button>
                  <button class="btn sm" type="button" (click)="openMove(i, 'SALIDA')"><app-icon name="arrowUp" [size]="14" /> Salida</button>
                </td>
              </tr>
            } @empty {
              <tr><td colspan="8" class="muted center">No hay materiales que coincidan.</td></tr>
            }
          </tbody>
        </table>
<app-pager [total]="(shown || []).length" [page]="pgItems" (pageChange)="pgItems = $event" />
      </section>
      }

      @if (tab === 'exp') {
        <div class="notice" role="note"><app-icon name="info" /><div>Los lotes vencidos <strong>no se usan en pacientes</strong>: el consumo automático los salta. Dales de baja con una <em>Salida</em> (motivo: vencido) y, si son medicamentos o residuos biocontaminados, entrégalos a la EO-RS autorizada (NTS 144-MINSA).</div></div>
        <section class="panel">
          <table class="table">
            <thead><tr><th>Material</th><th>Lote</th><th>Vence</th><th class="num">Queda</th><th>Situación</th><th></th></tr></thead>
            <tbody>
              @for (l of pageOf(expiring, pgExp); track l.id) {
                <tr>
                  <td class="strong">{{ l.item }}<div class="muted small">{{ l.brand || '' }}</div></td>
                  <td class="tabular small">{{ l.lotCode || 's/n' }}</td>
                  <td class="tabular">{{ date(l.expiresOn) }}</td>
                  <td class="num tabular">{{ n(l.remaining) }} {{ l.unit }}</td>
                  <td>@if (l.expired) { <span class="tag gum">Vencido hace {{ -l.days }} días</span> } @else { <span class="tag amber">Vence en {{ l.days }} días</span> }</td>
                  <td class="right">@if (l.expired) { <button class="btn sm" type="button" (click)="discardLot(l)">Dar de baja</button> }</td>
                </tr>
              } @empty { <tr><td colspan="6" class="muted center">Ningún lote vence en los próximos 60 días.</td></tr> }
            </tbody>
          </table>
          <app-pager [total]="expiring.length" [page]="pgExp" (pageChange)="pgExp = $event" />
        </section>
      }

      @if (tab === 'buy') {
        <section class="panel">
          <div class="panel-head spread"><h3>Compras a proveedores</h3>
            <input class="input search" type="search" [(ngModel)]="qBuy" (ngModelChange)="pgBuy = 1" placeholder="Buscar proveedor o comprobante" /></div>
          <table class="table">
            <thead><tr><th>Fecha</th><th>Proveedor</th><th>Comprobante</th><th class="num">Total</th><th>Pago</th><th></th></tr></thead>
            <tbody>
              @for (p of pageOf(shownPurchases, pgBuy); track p.id) {
                <tr>
                  <td class="tabular">{{ date(p.purchaseDate) }}</td>
                  <td class="strong">{{ p.supplier }}<div class="muted small">{{ p.lineCount }} producto(s)</div></td>
                  <td class="small">{{ docLabel(p.docType) }} {{ p.docNumber || '' }}</td>
                  <td class="num tabular strong">{{ money(p.total) }}</td>
                  <td>@if (p.status === 'POR_PAGAR') { <span class="tag amber">Por pagar · {{ date(p.dueDate) }}</span> }
                      @else { <span class="tag green">Pagada · {{ p.method?.toLowerCase() }}</span><div class="muted small">egreso en caja</div> }</td>
                  <td class="right nowrap">
                    <button class="btn sm ghost" type="button" (click)="viewPurchase(p)"><app-icon name="eye" [size]="14" /> Ver</button>
                    @if (p.status === 'POR_PAGAR') { <button class="btn sm" type="button" (click)="payFor = p; payMethod = ''">Pagar</button> }
                  </td>
                </tr>
              } @empty { <tr><td colspan="6" class="muted center">Sin compras registradas en los últimos 3 meses.</td></tr> }
            </tbody>
          </table>
          <app-pager [total]="shownPurchases.length" [page]="pgBuy" (pageChange)="pgBuy = $event" />
        </section>
      }

      @if (tab === 'req') {
        <section class="panel">
          <div class="panel-head spread"><h3>Solicitudes de insumos</h3>
            <div class="seg">@for (k of reqStates; track k.v) { <button type="button" [class.on]="reqState === k.v" (click)="reqState = k.v; loadRequests()">{{ k.l }}</button> }</div></div>
          <table class="table">
            <thead><tr><th>Fecha</th><th>Pidió</th><th>Material</th><th class="num">Cantidad</th><th class="num">En stock</th><th>Estado</th><th></th></tr></thead>
            <tbody>
              @for (r of pageOf(requests, pgReq); track r.id) {
                <tr>
                  <td class="small tabular nowrap">{{ dt(r.requestedAt) }}</td>
                  <td>{{ r.requestedByName || r.requestedBy }}</td>
                  <td class="strong">{{ r.item }} @if (r.urgency === 'URGENTE') { <span class="tag gum">Urgente</span> }<div class="muted small">{{ r.note || '' }}</div></td>
                  <td class="num tabular">{{ n(r.quantity) }} {{ r.unit || '' }}</td>
                  <td class="num tabular" [class.minus]="r.stock != null && +r.stock < +r.quantity">{{ r.stock != null ? n(r.stock) : '—' }}</td>
                  <td><span class="tag" [class.amber]="r.status === 'PENDIENTE'" [class.green]="r.status === 'ENTREGADA'" [class.grey]="r.status === 'ANULADA' || r.status === 'RECHAZADA'">{{ reqLabel(r.status) }}</span>
                    @if (r.answer) { <div class="muted small">{{ r.answer }}</div> }</td>
                  <td class="right nowrap">@if (r.status === 'PENDIENTE') {
                    <button class="btn sm primary" type="button" (click)="answer(r, 'ENTREGAR')" [disabled]="!r.itemId">Entregar</button>
                    <button class="btn sm" type="button" (click)="rejectFor = r; rejectText = ''">Rechazar</button> }</td>
                </tr>
              } @empty { <tr><td colspan="7" class="muted center">No hay solicitudes.</td></tr> }
            </tbody>
          </table>
          <app-pager [total]="requests.length" [page]="pgReq" (pageChange)="pgReq = $event" />
        </section>
      }

      @if (tab === 'moves') {
      <section class="panel">
        <div class="panel-head spread"><h3>Últimos movimientos</h3>
          <input class="input search" type="search" [(ngModel)]="qMoves" (ngModelChange)="pgMoves = 1" placeholder="Buscar material o motivo" /></div>
        <table class="table">
          <tbody>
            @for (m of pageOf(shownMoves, pgMoves); track m.id) {
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
<app-pager [total]="shownMoves.length" [page]="pgMoves" (pageChange)="pgMoves = $event" />
      </section>
      }
    </div>

    <!-- Lotes de un material -->
    <app-modal [open]="!!lotsFor" [title]="lotsFor?.name || ''" [subtitle]="(lotsFor ? catLabel(lotsFor.category) + ' · stock ' + n(lotsFor.stock) + ' ' + lotsFor.unit : '')" [width]="760" (close)="lotsFor = null">
      @if (lotsFor) {
        <dl class="kv small">
          <dt>Marca</dt><dd>{{ lotsFor.brand || '—' }}</dd>
          <dt>Proveedor / laboratorio</dt><dd>{{ lotsFor.supplier || '—' }}</dd>
          <dt>Último costo</dt><dd>{{ lotsFor.unitCost != null ? money(lotsFor.unitCost) + ' por ' + lotsFor.unit : '—' }}</dd>
          <dt>Control de vencimiento</dt><dd>{{ lotsFor.tracksExpiry === false ? 'No (instrumental o reutilizable)' : 'Sí' }}</dd>
        </dl>
        <table class="table">
          <thead><tr><th>Lote</th><th>Ingresó</th><th>Vence</th><th class="num">Entró</th><th class="num">Queda</th><th class="num">Costo</th></tr></thead>
          <tbody>
            @for (l of lots; track l.id) {
              <tr [class.off]="+l.remaining <= 0">
                <td class="small">{{ l.lotCode || 's/n' }}@if (l.supplier) { <div class="muted">{{ l.supplier }} · {{ l.docNumber || '' }}</div> }</td>
                <td class="small tabular">{{ dt(l.createdAt) }}</td>
                <td class="tabular" [class.minus]="l.expiresOn && daysTo(l.expiresOn) < 0">{{ l.expiresOn ? date(l.expiresOn) : '—' }}</td>
                <td class="num tabular">{{ n(l.quantity) }}</td>
                <td class="num tabular strong">{{ n(l.remaining) }}</td>
                <td class="num tabular">{{ l.unitCost != null ? money(l.unitCost) : '—' }}</td>
              </tr>
            } @empty { <tr><td colspan="6" class="muted center">Sin lotes registrados.</td></tr> }
          </tbody>
        </table>
      }
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="lotsFor = null">Cerrar</button>
        @if (lotsFor) { <button class="btn primary" type="button" (click)="edit(lotsFor); lotsFor = null">Editar material</button> }
      </ng-container>
    </app-modal>

    <!-- Compra a proveedor -->
    <app-modal [open]="buyOpen" title="Registrar compra a proveedor" subtitle="Ingresa los lotes al stock y registra el egreso en caja con el comprobante del proveedor." [width]="900" (close)="buyOpen = false">
      <form class="stack" (ngSubmit)="savePurchase()">
        <div class="grid three">
          <div class="field"><label class="req" for="bs">Proveedor / laboratorio</label><input id="bs" class="input" name="bs" [(ngModel)]="buy.supplier" list="suppliers" maxlength="120" />
            <datalist id="suppliers">@for (x of suppliers; track x) { <option [value]="x"></option> }</datalist></div>
          <div class="field"><label class="req" for="bd">Comprobante</label>
            <select id="bd" class="input" name="bd" [(ngModel)]="buy.docType"><option value="">Seleccionar…</option><option value="FACTURA">Factura</option><option value="BOLETA">Boleta</option><option value="GUIA">Guía de remisión</option><option value="SIN_COMPROBANTE">Sin comprobante</option></select></div>
          <div class="field"><label for="bn">Serie y número</label><input id="bn" class="input" name="bn" [(ngModel)]="buy.docNumber" placeholder="F001-00012345" maxlength="20" /></div>
          <div class="field"><label class="req" for="bf">Fecha</label><input id="bf" class="input" type="date" name="bf" [(ngModel)]="buy.purchaseDate" /></div>
          <div class="field"><label class="req" for="bp">Pago</label>
            <select id="bp" class="input" name="bp" [(ngModel)]="buy.paid"><option [ngValue]="true">Pagada al contado</option><option [ngValue]="false">Al crédito (pagar después)</option></select></div>
          @if (buy.paid) {
            <div class="field"><label class="req" for="bm">Medio de pago</label>
              <select id="bm" class="input" name="bm" [(ngModel)]="buy.method"><option value="">Seleccionar…</option>@for (m of methods; track m.v) { <option [value]="m.v">{{ m.l }}</option> }</select></div>
          } @else {
            <div class="field"><label class="req" for="bv">Pagar hasta</label><input id="bv" class="input" type="date" name="bv" [(ngModel)]="buy.dueDate" /></div>
          }
        </div>
        <table class="table lines">
          <thead><tr><th>Producto</th><th style="width:100px">Cantidad</th><th style="width:120px">Costo unit. (S/)</th><th style="width:130px">Lote</th><th style="width:150px">Vence</th><th class="num" style="width:100px">Subtotal</th><th></th></tr></thead>
          <tbody>
            @for (l of buy.lines; track $index) {
              <tr>
                <td><select class="input" [(ngModel)]="l.itemId" [name]="'li' + $index" (ngModelChange)="prefill(l)"><option [ngValue]="null">Seleccionar…</option>
                  @for (i of activeItems; track i.id) { <option [ngValue]="i.id">{{ i.name }}{{ i.brand ? ' · ' + i.brand : '' }}</option> }</select></td>
                <td><input class="input" type="number" min="0" step="1" [(ngModel)]="l.quantity" [name]="'lq' + $index" /></td>
                <td><input class="input" type="number" min="0" step="0.01" [(ngModel)]="l.unitCost" [name]="'lc' + $index" /></td>
                <td><input class="input" [(ngModel)]="l.lotCode" [name]="'ll' + $index" maxlength="40" placeholder="Nº de lote" /></td>
                <td><input class="input" type="date" [(ngModel)]="l.expiresOn" [name]="'le' + $index" [disabled]="tracks(l.itemId) === false" /></td>
                <td class="num tabular">{{ money((+l.quantity || 0) * (+l.unitCost || 0)) }}</td>
                <td><button class="btn ghost sm" type="button" (click)="buy.lines.splice($index, 1)" aria-label="Quitar"><app-icon name="x" [size]="14" /></button></td>
              </tr>
            }
          </tbody>
        </table>
        <div class="spread">
          <button class="btn sm" type="button" (click)="addLine()"><app-icon name="plus" [size]="14" /> Agregar producto</button>
          <strong class="tabular">Total {{ money(buyTotal) }}</strong>
        </div>
        <div class="field"><label for="bo">Observaciones</label><input id="bo" class="input" name="bo" [(ngModel)]="buy.notes" maxlength="255" /></div>
      </form>
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="buyOpen = false">Cancelar</button>
        <button class="btn primary" type="button" (click)="savePurchase()" [disabled]="busy">Registrar compra</button>
      </ng-container>
    </app-modal>

    <app-modal [open]="!!purchaseView" [title]="'Compra · ' + (purchaseView?.supplier || '')" [subtitle]="purchaseView ? docLabel(purchaseView.docType) + ' ' + (purchaseView.docNumber || '') + ' · ' + date(purchaseView.purchaseDate) : ''" [width]="720" (close)="purchaseView = null">
      @if (purchaseView) {
        <table class="table">
          <thead><tr><th>Producto</th><th>Lote</th><th>Vence</th><th class="num">Cantidad</th><th class="num">Costo</th><th class="num">Subtotal</th></tr></thead>
          <tbody>@for (l of purchaseView.lines; track l.id) {
            <tr><td>{{ l.item }}</td><td class="small">{{ l.lotCode || 's/n' }}</td><td class="tabular">{{ l.expiresOn ? date(l.expiresOn) : '—' }}</td>
              <td class="num tabular">{{ n(l.quantity) }} {{ l.unit }}</td><td class="num tabular">{{ money(l.unitCost) }}</td><td class="num tabular">{{ money(+l.quantity * +l.unitCost) }}</td></tr>
          }</tbody>
        </table>
        <p class="right strong">Total {{ money(purchaseView.total) }}</p>
        <p class="muted small">Registrada por {{ purchaseView.createdBy }} · {{ dt(purchaseView.createdAt) }}{{ purchaseView.expenseId ? ' · egreso de caja #' + purchaseView.expenseId : '' }}</p>
      }
      <ng-container modal-actions><button class="btn" type="button" (click)="purchaseView = null">Cerrar</button></ng-container>
    </app-modal>

    <app-modal [open]="!!payFor" title="Pagar compra al proveedor" [subtitle]="payFor ? payFor.supplier + ' · ' + money(payFor.total) : ''" (close)="payFor = null">
      <div class="field"><label class="req" for="pm">Medio de pago</label>
        <select id="pm" class="input" [(ngModel)]="payMethod"><option value="">Seleccionar…</option>@for (m of methods; track m.v) { <option [value]="m.v">{{ m.l }}</option> }</select></div>
      <p class="muted small">Se registra el egreso en la caja de hoy.</p>
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="payFor = null">Cancelar</button>
        <button class="btn primary" type="button" (click)="pay()" [disabled]="busy || !payMethod">Registrar pago</button>
      </ng-container>
    </app-modal>

    <app-modal [open]="!!rejectFor" title="Rechazar solicitud" [subtitle]="rejectFor ? rejectFor.item + ' · ' + (rejectFor.requestedByName || rejectFor.requestedBy) : ''" (close)="rejectFor = null">
      <div class="field"><label class="req" for="rj">Motivo</label><input id="rj" class="input" [(ngModel)]="rejectText" maxlength="255" placeholder="Sin stock, se pidió al proveedor; usar alternativa…" /></div>
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="rejectFor = null">Cancelar</button>
        <button class="btn primary" type="button" (click)="answer(rejectFor, 'RECHAZAR', rejectText)" [disabled]="busy || !rejectText.trim()">Rechazar</button>
      </ng-container>
    </app-modal>

    <app-modal [open]="!!moveFor" [title]="(move.kind === 'ENTRADA' ? 'Entrada de ' : move.kind === 'SALIDA' ? 'Salida de ' : 'Ajuste de ') + (moveFor?.name || '')"
               [subtitle]="'Stock actual: ' + n(moveFor?.stock) + ' ' + (moveFor?.unit || '')" (close)="moveFor = null">
      <form class="stack" (ngSubmit)="saveMove()">
        <div class="seg">
          @for (k of ['ENTRADA', 'SALIDA', 'AJUSTE']; track k) { <button type="button" [class.on]="move.kind === k" (click)="move.kind = k">{{ moveLabel(k) }}</button> }
        </div>
        <div class="field"><label for="mq">{{ move.kind === 'AJUSTE' ? 'Stock contado (queda en)' : 'Cantidad' }}</label>
          <input id="mq" class="input" type="number" min="0" step="0.01" name="q" [(ngModel)]="move.quantity" /></div>
        @if (move.kind === 'ENTRADA') {
          <div class="grid two">
            <div class="field"><label for="ml">Lote</label><input id="ml" class="input" name="ml" [(ngModel)]="move.lotCode" maxlength="40" /></div>
            <div class="field"><label for="me">Vence</label><input id="me" class="input" type="date" name="me" [(ngModel)]="move.expiresOn" [disabled]="moveFor?.tracksExpiry === false" /></div>
          </div>
          <p class="muted small">¿Es una compra? Usa <strong>Registrar compra</strong> para que quede la factura y el egreso en caja.</p>
        }
        <div class="field"><label for="mr" [class.req]="move.kind === 'SALIDA'">Motivo</label>
          <input id="mr" class="input" name="r" [(ngModel)]="move.reason" maxlength="200" [placeholder]="move.kind === 'ENTRADA' ? 'Compra, proveedor, factura…' : 'Vencido, roto, inventario…'" /></div>
      </form>
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="moveFor = null">Cancelar</button>
        <button class="btn primary" type="button" (click)="saveMove()" [disabled]="busy">Registrar</button>
      </ng-container>
    </app-modal>

    <app-modal [open]="itemOpen" [title]="itemId ? 'Editar material' : 'Nuevo material'" (close)="itemOpen = false">
      <form class="stack" (ngSubmit)="saveItem()">
        @if (itemId) {
          <div class="photo">
            <app-product-thumb [src]="itemImage" [name]="item.name" [size]="140" />
            <div class="stack-sm">
              <div class="strong small">Foto del producto</div>
              <div class="muted small">JPG, PNG o WEBP hasta 4 MB. Si no tiene foto se muestra un dibujo según el tipo de material.</div>
              <div class="row">
                <label class="btn sm"><app-icon name="upload" [size]="14" /> {{ imgBusy ? 'Subiendo…' : (itemImage ? 'Cambiar foto' : 'Subir foto') }}
                  <input type="file" accept="image/jpeg,image/png,image/webp" hidden (change)="uploadImage($event)" [disabled]="imgBusy" /></label>
                @if (itemImage) { <button class="btn sm ghost" type="button" (click)="removeImage()">Quitar</button> }
              </div>
            </div>
          </div>
        } @else { <p class="muted small">Podrás subir la foto del producto después de guardarlo.</p> }
        <div class="field"><label class="req" for="in">Nombre</label><input id="in" class="input" name="name" [(ngModel)]="item.name" maxlength="120" /></div>
        <div class="grid two">
          <div class="field"><label class="req" for="ic">Tipo</label>
            <select id="ic" class="input" name="cat" [(ngModel)]="item.category" (ngModelChange)="item.tracksExpiry = $event !== 'INSTRUMENTAL'">
              <option value="">Seleccionar…</option><option value="INSUMO">Insumo (se consume)</option><option value="INSTRUMENTAL">Instrumental (se esteriliza y reutiliza)</option><option value="MEDICAMENTO">Medicamento</option></select></div>
          <div class="field"><label for="ib">Marca</label><input id="ib" class="input" name="brand" [(ngModel)]="item.brand" maxlength="80" placeholder="3M, Dentsply…" /></div>
          <div class="field"><label for="ip">Proveedor / laboratorio</label><input id="ip" class="input" name="sup" [(ngModel)]="item.supplier" maxlength="120" list="suppliers2" />
            <datalist id="suppliers2">@for (x of suppliers; track x) { <option [value]="x"></option> }</datalist></div>
          <div class="field"><label for="ik">Costo unitario (S/)</label><input id="ik" class="input" type="number" min="0" step="0.01" name="cost" [(ngModel)]="item.unitCost" /></div>
        </div>
        <label class="check"><input type="checkbox" name="trk" [(ngModel)]="item.tracksExpiry" /> Controlar fecha de vencimiento por lote</label>
        <div class="grid two">
          <div class="field"><label for="iu">Unidad</label><input id="iu" class="input" name="unit" [(ngModel)]="item.unit" maxlength="20" placeholder="unidad, caja, par…" /></div>
          <div class="field"><label for="im">Stock mínimo</label><input id="im" class="input" type="number" min="0" name="min" [(ngModel)]="item.minStock" /></div>
        </div>
        @if (!itemId) {
          <div class="grid three">
            <div class="field"><label for="is">Stock inicial</label><input id="is" class="input" type="number" min="0" name="init" [(ngModel)]="item.initialStock" /></div>
            <div class="field"><label for="il">Lote</label><input id="il" class="input" name="lot" [(ngModel)]="item.lotCode" maxlength="40" /></div>
            <div class="field"><label for="ie">Vence</label><input id="ie" class="input" type="date" name="exp" [(ngModel)]="item.expiresOn" [disabled]="!item.tracksExpiry" /></div>
          </div>
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
          <option [ngValue]="null">Seleccionar procedimiento…</option>
          @for (p of procedures; track p.id) { <option [ngValue]="p.id">{{ p.code }} · {{ p.name }}</option> }
        </select>
        <table class="table">
          <tbody>
            @for (r of rows; track $index) {
              <tr>
                <td><select class="input" [(ngModel)]="r.itemId" aria-label="Material"><option [ngValue]="null">Seleccionar…</option>
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
    .prod { display: flex; align-items: center; gap: 12px; }
    .ph { padding: 0; border: 0; background: none; cursor: pointer; border-radius: 10px; }
    .photo { display: flex; gap: 16px; align-items: center; padding: 12px; border: 1px solid var(--line); border-radius: 14px; }
    .stack-sm { display: flex; flex-direction: column; gap: 6px; }
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
    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 10px; }
    .kpi { display: flex; flex-direction: column; gap: 4px; padding: 12px 14px; border-radius: 14px; background: var(--surface); border: 1px solid var(--line); }
    .kpi strong { font-size: 22px; }
    .kpi.warn { border-color: #f1d38c; background: #fff9ea; }
    .kpi.bad { border-color: #f3b4ac; background: #fdeeec; }
    .tabs { display: flex; gap: 4px; border-bottom: 1px solid var(--line); overflow-x: auto; }
    .tabs button { border: 0; background: none; padding: 10px 14px; font: inherit; font-size: 14px; cursor: pointer; color: var(--ink-2); border-bottom: 2px solid transparent; white-space: nowrap; }
    .tabs button.on { color: var(--teal-strong); border-bottom-color: var(--teal); font-weight: 700; }
    .count { display: inline-block; min-width: 20px; padding: 0 6px; margin-left: 4px; border-radius: 10px; background: var(--gum, #d23c62); color: #fff; font-size: 12px; }
    .warnt { color: #8a5a00; font-weight: 700; }
    .grid.three { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
    .grid.two { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
    .lines .input { min-width: 0; }
    .right { text-align: right; }
    @media (max-width: 760px) { .grid.three, .grid.two { grid-template-columns: 1fr; } .table { font-size: 13px; } }
    .seg { display: inline-flex; border: 1px solid var(--line); border-radius: 10px; overflow: hidden; align-self: flex-start; }
    .seg button { border: 0; background: var(--surface); padding: 7px 14px; font: inherit; font-size: 13.5px; cursor: pointer; }
    .seg button.on { background: var(--brand); color: #fff; font-weight: 700; }
  `],
})
export class InventoryComponent implements OnInit {
  pageOf = pageOf;
  pgMoves = 1;
  pgItems = 1;
  private api = inject(Api);
  private toast = inject(ToastService);
  items: InventoryItem[] = [];
  recent: any[] = [];
  lowCount = 0;
  q = '';
  onlyLow = false;
  busy = false;

  moveFor: InventoryItem | null = null;
  move: any = { kind: 'ENTRADA', quantity: 1 as number, reason: '', lotCode: '', expiresOn: null };
  itemOpen = false;
  itemId: number | null = null;
  itemImage: string | null = null;
  imgBusy = false;
  item: any = this.emptyItem();
  emptyItem() { return { name: '', unit: 'unidad', minStock: 0, initialStock: 0, active: true, category: '', brand: '', supplier: '', unitCost: null, tracksExpiry: true, lotCode: '', expiresOn: null }; }

  tab: 'items' | 'exp' | 'buy' | 'req' | 'moves' = 'items';
  cat = '';
  expiring: any[] = [];
  expiredCount = 0;
  nextExpiry: Record<number, string> = {};
  value = 0;
  pendingRequests = 0;
  pgExp = 1; pgBuy = 1; pgReq = 1;
  qBuy = ''; qMoves = '';
  purchases: any[] = [];
  requests: any[] = [];
  reqState = 'PENDIENTE';
  reqStates = [{ v: 'PENDIENTE', l: 'Pendientes' }, { v: 'ENTREGADA', l: 'Entregadas' }, { v: 'RECHAZADA', l: 'Rechazadas' }, { v: '', l: 'Todas' }];
  methods = [{ v: 'EFECTIVO', l: 'Efectivo' }, { v: 'TRANSFERENCIA', l: 'Transferencia' }, { v: 'TARJETA', l: 'Tarjeta' }, { v: 'YAPE', l: 'Yape' }, { v: 'PLIN', l: 'Plin' }];
  lotsFor: InventoryItem | null = null;
  lots: any[] = [];
  buyOpen = false;
  buy: any = { supplier: '', docType: '', docNumber: '', purchaseDate: '', paid: true, method: '', dueDate: null, notes: '', lines: [] };
  purchaseView: any = null;
  payFor: any = null;
  payMethod = '';
  rejectFor: any = null;
  rejectText = '';

  suppliesOpen = false;
  procedures: { id: number; code: string; name: string }[] = [];
  allSupplies: any[] = [];
  procId: number | null = null;
  rows: { itemId: number | null; quantity: number }[] = [];

  get shown() {
    const q = this.q.trim().toLowerCase();
    return this.items.filter((i) => (!q || [i.name, i.brand, i.supplier].some((x) => (x || '').toLowerCase().includes(q)))
      && (!this.onlyLow || i.low) && (!this.cat || i.category === this.cat));
  }
  get shownMoves() {
    const q = this.qMoves.trim().toLowerCase();
    return this.recent.filter((m) => !q || (m.item + ' ' + (m.reason || '') + ' ' + (m.createdBy || '')).toLowerCase().includes(q));
  }
  get shownPurchases() {
    const q = this.qBuy.trim().toLowerCase();
    return this.purchases.filter((p) => !q || (p.supplier + ' ' + (p.docNumber || '')).toLowerCase().includes(q));
  }
  get activeItems() { return this.items.filter((i) => i.active); }
  get activeCount() { return this.activeItems.length; }
  get suppliers() { return [...new Set(this.items.map((i) => i.supplier).filter((x): x is string => !!x))].sort(); }
  get buyTotal() { return (this.buy?.lines || []).reduce((a: number, l: any) => a + (+l.quantity || 0) * (+l.unitCost || 0), 0); }

  ngOnInit() { this.load(); }
  load() {
    this.api.inventory().subscribe((r: any) => {
      this.items = r.items; this.recent = r.recent; this.lowCount = r.lowCount;
      this.expiring = r.expiring || []; this.expiredCount = r.expiredCount || 0; this.nextExpiry = r.nextExpiry || {};
      this.value = +r.value || 0; this.pendingRequests = r.pendingRequests || 0;
    });
  }
  money = soles;
  date = shortDate;
  daysTo(d: string) { return Math.round((new Date(d + 'T00:00:00').getTime() - new Date(new Date().toDateString()).getTime()) / 86400000); }
  catLabel(c?: string) { return c === 'INSTRUMENTAL' ? 'Instrumental' : c === 'MEDICAMENTO' ? 'Medicamento' : 'Insumo'; }
  docLabel(d: string) { return ({ FACTURA: 'Factura', BOLETA: 'Boleta', GUIA: 'Guía', SIN_COMPROBANTE: 'Sin comprobante' } as any)[d] || d; }
  reqLabel(s: string) { return ({ PENDIENTE: 'Pendiente', ENTREGADA: 'Entregada', RECHAZADA: 'Rechazada', ANULADA: 'Anulada' } as any)[s] || s; }
  tracks(id: number | null) { return this.items.find((i) => i.id === id)?.tracksExpiry; }

  openLots(i: InventoryItem) { this.lotsFor = i; this.lots = []; this.api.inventoryLots(i.id).subscribe((l) => (this.lots = l)); }
  discardLot(l: any) {
    const i = this.items.find((x) => x.id === l.itemId);
    if (!i) return;
    this.moveFor = i;
    this.move = { kind: 'SALIDA', quantity: +l.remaining, reason: 'Baja por vencimiento · lote ' + (l.lotCode || 's/n'), lotCode: '', expiresOn: null };
  }

  loadPurchases() { this.api.purchases().subscribe((p) => (this.purchases = p)); }
  viewPurchase(p: any) { this.api.purchase(p.id).subscribe((x) => (this.purchaseView = x)); }
  openPurchase() {
    this.buy = { supplier: '', docType: '', docNumber: '', purchaseDate: isoDate(), paid: true, method: '', dueDate: null, notes: '', lines: [] };
    this.addLine();
    this.buyOpen = true;
  }
  addLine() { this.buy.lines.push({ itemId: null, quantity: 1, unitCost: null, lotCode: '', expiresOn: null }); }
  prefill(l: any) { const i = this.items.find((x) => x.id === l.itemId); if (i && l.unitCost == null && i.unitCost != null) l.unitCost = +i.unitCost; if (i?.supplier && !this.buy.supplier) this.buy.supplier = i.supplier; }
  savePurchase() {
    if (this.busy) return;
    const lines = this.buy.lines.filter((l: any) => l.itemId).map((l: any) => ({ ...l, quantity: +l.quantity, unitCost: l.unitCost == null ? null : +l.unitCost, expiresOn: l.expiresOn || null }));
    this.busy = true;
    this.api.createPurchase({ ...this.buy, lines, dueDate: this.buy.paid ? null : this.buy.dueDate, method: this.buy.paid ? this.buy.method : null }).subscribe({
      next: () => { this.busy = false; this.buyOpen = false; this.toast.ok(this.buy.paid ? 'Compra registrada: stock actualizado y egreso en caja.' : 'Compra al crédito registrada: stock actualizado.'); this.load(); this.tab = 'buy'; this.loadPurchases(); },
      error: (e) => { this.busy = false; const a = apiError(e); this.toast.error(a.fields ? Object.values(a.fields)[0] as string : a.message); },
    });
  }
  pay() {
    if (!this.payFor) return;
    this.busy = true;
    this.api.payPurchase(this.payFor.id, { method: this.payMethod }).subscribe({
      next: () => { this.busy = false; this.payFor = null; this.toast.ok('Pago registrado como egreso en caja.'); this.loadPurchases(); },
      error: (e) => { this.busy = false; this.toast.error(apiError(e).message); },
    });
  }

  loadRequests() { this.api.supplyRequests(this.reqState).subscribe((r) => { this.requests = r; this.pgReq = 1; }); }
  answer(r: any, action: string, text = '') {
    this.busy = true;
    this.api.answerSupplyRequest(r.id, { action, answer: text || null }).subscribe({
      next: () => { this.busy = false; this.rejectFor = null; this.toast.ok(action === 'ENTREGAR' ? 'Entregado: se descontó del stock.' : 'Solicitud rechazada.'); this.loadRequests(); this.load(); },
      error: (e) => { this.busy = false; this.toast.error(apiError(e).message); },
    });
  }

  n(v: any) { const x = +v; return Number.isFinite(x) ? (Number.isInteger(x) ? String(x) : x.toFixed(2)) : '—'; }
  dt = dateTime;
  moveLabel(k: string) { return MOVE_LABEL[k] || k; }

  openMove(i: InventoryItem, kind: string) { this.moveFor = i; this.move = { kind, quantity: kind === 'AJUSTE' ? +i.stock : 1, reason: '', lotCode: '', expiresOn: null }; }
  saveMove() {
    if (!this.moveFor || this.busy) return;
    this.busy = true;
    this.api.inventoryMove(this.moveFor.id, { kind: this.move.kind, quantity: +this.move.quantity, reason: this.move.reason, lotCode: this.move.lotCode || null, expiresOn: this.move.expiresOn || null }).subscribe({
      next: () => { this.busy = false; this.moveFor = null; this.toast.ok('Movimiento registrado.'); this.load(); },
      error: (e) => { this.busy = false; this.toast.error(apiError(e).message); },
    });
  }

  openNew() { this.itemId = null; this.itemImage = null; this.item = this.emptyItem(); this.itemOpen = true; }
  edit(i: InventoryItem) {
    this.itemId = i.id; this.itemImage = i.imageUrl || null;
    this.item = { name: i.name, unit: i.unit, minStock: +i.minStock, initialStock: 0, active: i.active, category: i.category || 'INSUMO', brand: i.brand || '',
      supplier: i.supplier || '', unitCost: i.unitCost != null ? +i.unitCost : null, tracksExpiry: i.tracksExpiry !== false, lotCode: '', expiresOn: null };
    this.itemOpen = true;
  }
  saveItem() {
    if (this.busy) return;
    this.busy = true;
    const body = { ...this.item, unitCost: this.item.unitCost === '' || this.item.unitCost == null ? null : +this.item.unitCost, expiresOn: this.item.expiresOn || null, category: this.item.category || null };
    const req = this.itemId ? this.api.updateInventoryItem(this.itemId, body) : this.api.createInventoryItem(body);
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

  uploadImage(ev: Event) {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || !this.itemId) return;
    this.imgBusy = true;
    this.api.inventoryImage(this.itemId, file).subscribe({
      next: (it) => { this.imgBusy = false; this.itemImage = it.imageUrl || null; this.toast.ok('Foto guardada.'); this.load(); },
      error: (e) => { this.imgBusy = false; this.toast.error(apiError(e).message); },
    });
  }

  removeImage() {
    if (!this.itemId) return;
    this.api.removeInventoryImage(this.itemId).subscribe({
      next: () => { this.itemImage = null; this.toast.ok('Foto quitada.'); this.load(); },
      error: (e) => this.toast.error(apiError(e).message),
    });
  }
}
