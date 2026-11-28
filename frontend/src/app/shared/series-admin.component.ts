import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api.service';
import { ToastService } from '../core/toast.service';
import { apiError } from '../core/format';
import { OnlyDirective } from './only.directive';
import { DeleteButtonComponent } from './delete-button.component';

/** v2.16: administración de series de comprobantes (B001, F001…). Solo administrador. */
@Component({
  selector: 'app-series-admin',
  standalone: true,
  imports: [FormsModule, OnlyDirective, DeleteButtonComponent],
  template: `
    <p class="muted small">SUNAT: las boletas usan series que empiezan con <b>B</b> y las facturas con <b>F</b> (4 caracteres, ej. B001). Una serie que ya emitió comprobantes no cambia de código: se desactiva y se crea otra.</p>
    <table class="table">
      <thead><tr><th>Tipo</th><th>Serie</th><th class="num">Último N.°</th><th>Estado</th><th></th></tr></thead>
      <tbody>
        @for (s of list; track s.id) {
          <tr>
            <td>{{ s.docType === '07' ? 'Nota de crédito' : s.docType === '01' ? 'Factura' : 'Boleta' }}</td>
            <td class="tabular strong">
              @if (edit?.id === s.id && !s.lastNumber) { <input class="input sm" [(ngModel)]="edit.series" maxlength="4" appOnly="alnum" /> } @else { {{ s.series }} }
            </td>
            <td class="num tabular">{{ s.lastNumber }}</td>
            <td><span class="tag" [class.green]="s.active">{{ s.active ? 'Activa' : 'Inactiva' }}</span></td>
            <td class="right">
              @if (edit?.id === s.id) {
                <button class="btn sm primary" type="button" (click)="save(edit)">Guardar</button>
                <button class="btn sm ghost" type="button" (click)="edit = null">Cancelar</button>
              } @else {
                @if (!s.lastNumber) { <button class="btn sm ghost" type="button" (click)="edit = { id: s.id, docType: s.docType, series: s.series }">Editar</button> }
                <button class="btn sm ghost" type="button" (click)="save({ id: s.id, docType: s.docType, series: s.series, active: !s.active })">{{ s.active ? 'Desactivar' : 'Activar' }}</button>
                @if (!s.lastNumber) { <app-delete-button entity="invoice_series" [recordId]="s.id" [what]="'Serie ' + s.series" [iconOnly]="true" [ghost]="true" (deleted)="load()" /> }
              }
            </td>
          </tr>
        }
      </tbody>
    </table>
    <div class="row add">
      <select class="input sm" [(ngModel)]="nw.docType" (ngModelChange)="nw.series = ($event === '01' ? 'F' : $event === '07' ? 'BC' : 'B')">
        <option value="03">Boleta</option><option value="01">Factura</option><option value="07">Nota de crédito</option>
      </select>
      <input class="input sm" [(ngModel)]="nw.series" maxlength="4" appOnly="alnum" placeholder="B002" />
      <button class="btn sm primary" type="button" (click)="save(nw, true)" [disabled]="nw.series.length !== 4">Crear serie</button>
    </div>`,
  styles: [`.row.add { display: flex; gap: 8px; margin-top: 12px; align-items: center; } .input.sm { width: 110px; }`],
})
export class SeriesAdminComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  list: any[] = [];
  edit: any = null;
  nw = { docType: '03', series: 'B' };

  ngOnInit() { this.load(); }
  load() { this.api.allSeries().subscribe((l) => (this.list = l)); }

  save(s: any, isNew = false) {
    const body = { docType: s.docType, series: (s.series || '').toUpperCase(), active: s.active };
    (isNew ? this.api.createSeries(body) : this.api.updateSeries(s.id, body)).subscribe({
      next: () => { this.toast.ok(isNew ? 'Serie creada.' : 'Serie actualizada.'); this.edit = null; if (isNew) this.nw = { docType: '03', series: 'B' }; this.load(); },
      error: (e) => this.toast.error(apiError(e).message),
    });
  }
}
