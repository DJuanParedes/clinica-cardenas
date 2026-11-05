import { Component, Input, OnChanges, OnInit, SimpleChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { Invoice, Page } from '../core/models';
import { INVOICE_LABEL, INVOICE_TONE, shortDate, soles } from '../core/format';
import { IconComponent } from '../shared/icon.component';

/** HU-12: consultar comprobantes por número, cliente, estado y fecha. */
@Component({
  selector: 'app-invoices',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <h1>Comprobantes</h1>
          <p class="sub">Boletas y facturas electrónicas con su estado ante SUNAT.</p>
        </div>
        <div class="actions"><a class="btn primary" routerLink="/comprobantes/nuevo"><app-icon name="plus" [size]="16" /> Emitir comprobante</a></div>
      </div>

      <form class="filters panel" (ngSubmit)="search(0)">
        <div class="field grow">
          <label for="q">Número, cliente o documento</label>
          <input id="q" name="q" class="input" [(ngModel)]="q" placeholder="B001-12, RUC, DNI o nombre" />
        </div>
        <div class="field">
          <label for="st">Estado</label>
          <select id="st" name="st" class="input" [(ngModel)]="status" (ngModelChange)="search(0)">
            <option value="">Todos</option>
            @for (s of statuses; track s) { <option [value]="s">{{ statusLabel(s) }}</option> }
          </select>
        </div>
        <div class="field">
          <label for="dt">Tipo</label>
          <select id="dt" name="dt" class="input" [(ngModel)]="docType" (ngModelChange)="search(0)">
            <option value="">Todos</option><option value="03">Boletas</option><option value="01">Facturas</option>
          </select>
        </div>
        <div class="field"><label for="f">Desde</label><input id="f" name="f" type="date" class="input" [(ngModel)]="from" /></div>
        <div class="field"><label for="t">Hasta</label><input id="t" name="t" type="date" class="input" [(ngModel)]="to" /></div>
        <button class="btn" type="submit"><app-icon name="search" [size]="16" /> Buscar</button>
      </form>

      <section class="panel">
        @if (page && page.content.length === 0) {
          <div class="empty">
            <h3>No hay comprobantes con esos filtros</h3>
            <p>Quita algún filtro o emite un comprobante nuevo.</p>
          </div>
        } @else {
          <table class="table">
            <thead><tr><th>Número</th><th>Emisión</th><th>Cliente</th><th class="num">Total</th><th>SUNAT</th></tr></thead>
            <tbody>
              @if (!page) { @for (i of [1,2,3,4]; track i) { <tr><td colspan="5"><div class="skeleton" style="height: 20px"></div></td></tr> } }
              @for (inv of page?.content || []; track inv.id) {
                <tr class="click" (click)="open(inv)" tabindex="0" (keydown.enter)="open(inv)">
                  <td><span class="strong tabular">{{ inv.series }}-{{ inv.number }}</span><div class="muted small">{{ inv.docType === '01' ? 'Factura' : 'Boleta' }}</div></td>
                  <td class="tabular">{{ date(inv.issueDate) }}<div class="muted small">{{ inv.issueTime.substring(0, 5) }}</div></td>
                  <td>{{ inv.customerName }}<div class="muted small tabular">{{ inv.customerDocNumber }}</div></td>
                  <td class="num tabular strong">{{ money(inv.totalAmount) }}</td>
                  <td><span class="tag" [class]="'tag ' + statusTone(inv.status)">{{ statusLabel(inv.status) }}</span></td>
                </tr>
              }
            </tbody>
          </table>
          @if (page && page.pages > 1) {
            <div class="panel-foot spread">
              <span class="muted small">{{ page.total }} comprobantes · página {{ page.page + 1 }} de {{ page.pages }}</span>
              <div class="row">
                <button class="btn sm" [disabled]="page.page === 0" (click)="search(page.page - 1)">Anterior</button>
                <button class="btn sm" [disabled]="page.page + 1 >= page.pages" (click)="search(page.page + 1)">Siguiente</button>
              </div>
            </div>
          }
        }
      </section>
    </div>
  `,
  styles: [`
    .filters { display: flex; flex-wrap: wrap; gap: 12px; align-items: flex-end; padding: 14px 16px; margin-bottom: 16px; }
    .filters .grow { min-width: 220px; }
    .filters select, .filters input[type=date] { min-width: 140px; }
  `],
})
export class InvoicesComponent implements OnInit, OnChanges {
  private api = inject(Api);
  private router = inject(Router);

  @Input('status') statusParam?: string;

  q = '';
  status = '';
  docType = '';
  from = '';
  to = '';
  page: Page<Invoice> | null = null;
  statuses = ['GENERADO', 'ACEPTADO', 'OBSERVADO', 'RECHAZADO', 'ERROR'];

  ngOnInit() {
    this.status = this.statusParam || '';
    this.search(0);
  }

  ngOnChanges(ch: SimpleChanges) {
    if (ch['statusParam'] && !ch['statusParam'].firstChange) {
      this.status = this.statusParam || '';
      this.search(0);
    }
  }

  search(n: number) {
    this.api.invoices({ q: this.q, status: this.status, docType: this.docType, from: this.from, to: this.to, page: n })
      .subscribe((p) => (this.page = p));
  }

  open(i: Invoice) { this.router.navigate(['/comprobantes', i.id]); }
  statusLabel(s: string) { return INVOICE_LABEL[s] || s; }
  statusTone(s: string) { return INVOICE_TONE[s] || 'grey'; }
  money = soles;
  date = shortDate;
}
