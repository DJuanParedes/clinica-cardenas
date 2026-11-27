import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api.service';
import { ToastService } from '../core/toast.service';
import { apiError, dateTime } from '../core/format';
import { IconComponent } from '../shared/icon.component';
import { ModalComponent } from '../shared/modal.component';
import { PagerComponent, pageOf } from '../shared/pager.component';

import { OnlyDirective } from '../shared/only.directive';
import { DeleteButtonComponent } from '../shared/delete-button.component';
/** El odontólogo pide materiales; recepción los entrega desde Inventario. No ve costos ni proveedores. */
@Component({
  selector: 'app-supply-requests',
  standalone: true,
  imports: [DeleteButtonComponent, OnlyDirective, FormsModule, IconComponent, ModalComponent, PagerComponent],
  template: `
    <div class="page">
      <div class="page-head spread">
        <div><h1>Solicitar insumos</h1><p class="sub">Pide lo que necesitas para tus atenciones. Recepción lo entrega y descuenta del inventario.</p></div>
        <button class="btn primary" type="button" (click)="openNew()"><app-icon name="plus" [size]="16" /> Nueva solicitud</button>
      </div>
      <section class="panel">
        <div class="panel-head spread"><h3>Mis solicitudes</h3>
          <input class="input search" type="search" [(ngModel)]="q" (ngModelChange)="pg = 1" placeholder="Buscar material" appOnly="search" maxlength="100" /></div>
        <table class="table">
          <thead><tr><th>Fecha</th><th>Material</th><th class="num">Cantidad</th><th>Estado</th><th>Respuesta</th><th></th></tr></thead>
          <tbody>
            @for (r of pageOf(shown, pg); track r.id) {
              <tr>
                <td class="small tabular nowrap">{{ dt(r.requestedAt) }}</td>
                <td class="strong">{{ r.item }} @if (r.urgency === 'URGENTE') { <span class="tag gum">Urgente</span> }<div class="muted small">{{ r.note || '' }}</div></td>
                <td class="num tabular">{{ n(r.quantity) }} {{ r.unit || '' }}</td>
                <td><span class="tag" [class.amber]="r.status === 'PENDIENTE'" [class.green]="r.status === 'ENTREGADA'" [class.grey]="r.status !== 'PENDIENTE' && r.status !== 'ENTREGADA'">{{ label(r.status) }}</span></td>
                <td class="small">{{ r.answer || (r.answeredBy ? 'Atendió ' + r.answeredBy : '—') }}</td>
                <td class="right">@if (r.status === 'PENDIENTE') { <button class="btn sm ghost" type="button" (click)="cancel(r)">Anular</button> }
                  <app-delete-button entity="supply_requests" [recordId]="r.id" what="Pedido de material" [iconOnly]="true" [ghost]="true" (deleted)="load()" /></td>
              </tr>
            } @empty { <tr><td colspan="6" class="muted center">Aún no has pedido insumos.</td></tr> }
          </tbody>
        </table>
        <app-pager [total]="shown.length" [page]="pg" (pageChange)="pg = $event" />
      </section>
    </div>

    <app-modal [open]="open" title="Nueva solicitud de insumos" (close)="open = false">
      <form class="stack" (ngSubmit)="save()">
        <div class="field"><label class="req" for="si">Material</label>
          <select id="si" class="input" name="si" [(ngModel)]="f.itemId">
            <option [ngValue]="null">Seleccionar…</option>
            @for (i of catalog; track i.id) { <option [ngValue]="i.id">{{ i.name }}{{ i.available ? '' : ' (sin stock)' }}</option> }
            <option [ngValue]="0">Otro (no está en la lista)</option>
          </select></div>
        @if (f.itemId === 0) { <div class="field"><label class="req" for="st">¿Qué necesitas?</label><input id="st" class="input" name="st" [(ngModel)]="f.itemText" maxlength="160" appOnly="text" /></div> }
        <div class="grid two">
          <div class="field"><label class="req" for="sq">Cantidad</label><input id="sq" class="input" type="number" min="1" name="sq" [(ngModel)]="f.quantity" appOnly="int" /></div>
          <div class="field"><label for="su">Urgencia</label><select id="su" class="input" name="su" [(ngModel)]="f.urgency"><option value="NORMAL">Normal</option><option value="URGENTE">Urgente (paciente en sillón)</option></select></div>
        </div>
        <div class="field"><label for="sn">Nota</label><input id="sn" class="input" name="sn" [(ngModel)]="f.note" maxlength="255" placeholder="Para qué paciente o procedimiento" appOnly="text" /></div>
      </form>
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="open = false">Cancelar</button>
        <button class="btn primary" type="button" (click)="save()" [disabled]="busy">Enviar solicitud</button>
      </ng-container>
    </app-modal>
  `,
  styles: [`.grid.two { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; } .center { text-align: center; padding: 24px; } .search { max-width: 260px; }
    @media (max-width: 640px) { .grid.two { grid-template-columns: 1fr; } }`],
})
export class SupplyRequestsComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  list: any[] = [];
  catalog: any[] = [];
  q = '';
  pg = 1;
  pageOf = pageOf;
  open = false;
  busy = false;
  f: any = {};

  get shown() { const q = this.q.trim().toLowerCase(); return this.list.filter((r) => !q || (r.item || '').toLowerCase().includes(q)); }
  ngOnInit() { this.load(); this.api.supplyCatalog().subscribe((c) => (this.catalog = c)); }
  load() { this.api.supplyRequests('', true).subscribe((l) => (this.list = l)); }
  openNew() { this.f = { itemId: null, itemText: '', quantity: 1, urgency: 'NORMAL', note: '' }; this.open = true; }
  save() {
    this.busy = true;
    const body = { ...this.f, itemId: this.f.itemId || null, quantity: +this.f.quantity };
    this.api.createSupplyRequest(body).subscribe({
      next: () => { this.busy = false; this.open = false; this.toast.ok('Solicitud enviada a recepción.'); this.load(); },
      error: (e) => { this.busy = false; const a = apiError(e); this.toast.error(a.fields ? Object.values(a.fields)[0] : a.message); },
    });
  }
  cancel(r: any) { this.api.cancelSupplyRequest(r.id).subscribe({ next: () => { this.toast.ok('Solicitud anulada.'); this.load(); }, error: (e) => this.toast.error(apiError(e).message) }); }
  label(s: string) { return ({ PENDIENTE: 'Pendiente', ENTREGADA: 'Entregada', RECHAZADA: 'Rechazada', ANULADA: 'Anulada' } as any)[s] || s; }
  n(v: any) { const x = +v; return Number.isInteger(x) ? String(x) : x.toFixed(2); }
  dt = dateTime;
}
