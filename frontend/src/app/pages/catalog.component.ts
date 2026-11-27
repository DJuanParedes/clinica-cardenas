import { DeleteButtonComponent } from '../shared/delete-button.component';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ToastService } from '../core/toast.service';
import { apiError, isoDate, shortDate, soles } from '../core/format';
import { IconComponent } from '../shared/icon.component';
import { ModalComponent } from '../shared/modal.component';
import { PagerComponent, pageOf } from '../shared/pager.component';

import { OnlyDirective } from '../shared/only.directive';
/** Catálogo de procedimientos con variantes (familias) y promociones con vigencia. */
@Component({
  selector: 'app-catalog',
  standalone: true,
  imports: [OnlyDirective, DeleteButtonComponent, FormsModule, IconComponent, ModalComponent, PagerComponent],
  template: `
    <div class="page">
      <div class="page-head spread">
        <div><h1>Procedimientos y promociones</h1>
          <p class="sub">Las variantes (p. ej. limpieza simple o profunda) se agrupan por familia. Una promoción vigente se aplica sola al armar el plan de tratamiento y el paciente ve el precio de lista y el descuento.</p></div>
        @if (canEdit) {
          <div class="row wrap">
            <button class="btn" type="button" (click)="newProc()"><app-icon name="plus" [size]="16" /> Procedimiento</button>
            <button class="btn primary" type="button" (click)="newPromo()"><app-icon name="tag" [size]="16" /> Nueva promoción</button>
          </div>
        }
      </div>

      <nav class="tabs"><button type="button" [class.on]="tab === 'proc'" (click)="tab = 'proc'">Procedimientos</button>
        <button type="button" [class.on]="tab === 'promo'" (click)="tab = 'promo'">Promociones @if (activePromos) { <span class="count">{{ activePromos }}</span> }</button></nav>

      @if (tab === 'proc') {
        <section class="panel">
          <div class="panel-head spread"><input class="input search" type="search" [(ngModel)]="q" (ngModelChange)="pg = 1" placeholder="Buscar procedimiento, código o familia" appOnly="search" maxlength="100" /></div>
          <table class="table">
            <thead><tr><th>Código</th><th>Procedimiento</th><th>Familia / categoría</th><th class="num">Precio de lista</th><th>Hoy</th><th></th></tr></thead>
            <tbody>
              @for (p of pageOf(shownProcs, pg); track p.id) {
                <tr [class.off]="!p.active">
                  <td class="tabular small">{{ p.code }}</td>
                  <td class="strong">{{ p.name }}<div class="muted small">{{ p.description || '' }}{{ p.durationMin ? ' · ' + p.durationMin + ' min' : '' }}</div></td>
                  <td class="small">{{ p.family || '—' }}<div class="muted">{{ p.category }}</div></td>
                  <td class="num tabular">{{ money(p.price) }}</td>
                  <td>@if (p.promotion) { <span class="tag gum">{{ money(p.promotion.price) }}</span><div class="muted small">{{ p.promotion.name }}</div> } @else if (!p.active) { <span class="tag grey">Inactivo</span> } @else { <span class="muted small">—</span> }</td>
                  <td class="right nowrap">
                    <button class="btn sm ghost" type="button" (click)="view = p"><app-icon name="eye" [size]="14" /> Ver</button>
                    @if (canEdit) { <button class="btn sm" type="button" (click)="editProc(p)"><app-icon name="edit" [size]="14" /> Editar</button> }
                    <app-delete-button entity="procedures" [recordId]="p.id" what="Procedimiento" [iconOnly]="true" [ghost]="true" (deleted)="load()" />
                  </td>
                </tr>
              } @empty { <tr><td colspan="6" class="muted center">Sin resultados.</td></tr> }
            </tbody>
          </table>
          <app-pager [total]="shownProcs.length" [page]="pg" (pageChange)="pg = $event" />
        </section>
      }

      @if (tab === 'promo') {
        <section class="panel">
          <table class="table">
            <thead><tr><th>Promoción</th><th>Aplica a</th><th>Descuento</th><th>Vigencia</th><th>Estado</th><th class="num">Usos</th><th></th></tr></thead>
            <tbody>
              @for (p of pageOf(promos, pgPromo); track p.id) {
                <tr>
                  <td class="strong">{{ p.name }}<div class="muted small">{{ p.conditions || '' }}</div></td>
                  <td class="small">{{ p.procedureName || ('Familia: ' + p.family) }}</td>
                  <td class="small">{{ promoText(p) }}@if (p.finalPrice != null) { <div class="muted">{{ money(p.listPrice) }} → <strong>{{ money(p.finalPrice) }}</strong></div> }</td>
                  <td class="small tabular">{{ date(p.validFrom) }} – {{ date(p.validTo) }}</td>
                  <td><span class="tag" [class.green]="p.state === 'VIGENTE'" [class.amber]="p.state === 'PROGRAMADA'" [class.grey]="p.state === 'VENCIDA' || p.state === 'INACTIVA'">{{ stateLabel(p.state) }}</span></td>
                  <td class="num tabular">{{ p.uses }}</td>
                  <td class="right">@if (canEdit) { <button class="btn sm" type="button" (click)="editPromo(p)"><app-icon name="edit" [size]="14" /> Editar</button> } <app-delete-button entity="promotions" [recordId]="p.id" what="Promoción" [iconOnly]="true" [ghost]="true" (deleted)="load()" /></td>
                </tr>
              } @empty { <tr><td colspan="7" class="muted center">No hay promociones.</td></tr> }
            </tbody>
          </table>
          <app-pager [total]="promos.length" [page]="pgPromo" (pageChange)="pgPromo = $event" />
        </section>
      }
    </div>

    <app-modal [open]="!!view" [title]="view?.name || ''" [subtitle]="view ? view.code + ' · ' + view.category : ''" (close)="view = null">
      @if (view) {
        <dl class="kv small">
          <dt>Familia</dt><dd>{{ view.family || '—' }}</dd>
          <dt>Descripción</dt><dd>{{ view.description || '—' }}</dd>
          <dt>Duración</dt><dd>{{ view.durationMin ? view.durationMin + ' minutos' : '—' }}</dd>
          <dt>Precio de lista</dt><dd>{{ money(view.price) }}</dd>
          <dt>Precio hoy</dt><dd>@if (view.promotion) { <strong>{{ money(view.promotion.price) }}</strong> · {{ view.promotion.name }} } @else { {{ money(view.price) }} }</dd>
          @if (variants(view).length > 1) { <dt>Variantes</dt><dd>@for (v of variants(view); track v.id) { <div>{{ v.name }} · {{ money(v.price) }}</div> }</dd> }
        </dl>
      }
      <ng-container modal-actions><button class="btn" type="button" (click)="view = null">Cerrar</button></ng-container>
    </app-modal>

    <app-modal [open]="procOpen" [title]="pf.id ? 'Editar procedimiento' : 'Nuevo procedimiento'" (close)="procOpen = false">
      <form class="stack" (ngSubmit)="saveProc()">
        <div class="grid two">
          <div class="field"><label class="req" for="pc">Código</label><input id="pc" class="input" name="pc" [(ngModel)]="pf.code" [disabled]="!!pf.id" placeholder="PRE-006" maxlength="7" appOnly="text" /></div>
          <div class="field"><label class="req" for="pg">Categoría</label>
            <select id="pg" class="input" name="pg" [(ngModel)]="pf.category"><option value="">Seleccionar…</option>@for (c of categories; track c) { <option [value]="c">{{ c }}</option> }</select></div>
        </div>
        <div class="field"><label class="req" for="pn">Nombre</label><input id="pn" class="input" name="pn" [(ngModel)]="pf.name" maxlength="120" appOnly="text" /></div>
        <div class="grid two">
          <div class="field"><label for="pfm">Familia (variantes)</label><input id="pfm" class="input" name="pfm" [(ngModel)]="pf.family" list="fams" maxlength="60" placeholder="Limpieza dental" appOnly="text" />
            <datalist id="fams">@for (f of families; track f) { <option [value]="f"></option> }</datalist></div>
          <div class="field"><label class="req" for="pp">Precio de lista (S/)</label><input id="pp" class="input" type="number" min="0" step="0.5" name="pp" [(ngModel)]="pf.price" appOnly="decimal" /></div>
          <div class="field"><label for="pd">Duración (min)</label><input id="pd" class="input" type="number" min="5" step="5" name="pd" [(ngModel)]="pf.durationMin" appOnly="int" /></div>
        </div>
        <div class="field"><label for="pds">Descripción para el paciente</label><input id="pds" class="input" name="pds" [(ngModel)]="pf.description" maxlength="255" appOnly="text" /></div>
        @if (pf.id) { <label class="check"><input type="checkbox" name="pa" [(ngModel)]="pf.active" /> Activo (se puede agregar a planes)</label> }
      </form>
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="procOpen = false">Cancelar</button>
        <button class="btn primary" type="button" (click)="saveProc()" [disabled]="busy">Guardar</button>
      </ng-container>
    </app-modal>

    <app-modal [open]="promoOpen" [title]="mf.id ? 'Editar promoción' : 'Nueva promoción'" (close)="promoOpen = false">
      <form class="stack" (ngSubmit)="savePromo()">
        <div class="field"><label class="req" for="mn">Nombre</label><input id="mn" class="input" name="mn" [(ngModel)]="mf.name" maxlength="100" placeholder="Campaña de limpieza" appOnly="text" /></div>
        <div class="seg"><button type="button" [class.on]="mf.scope === 'proc'" (click)="mf.scope = 'proc'">Un procedimiento</button><button type="button" [class.on]="mf.scope === 'fam'" (click)="mf.scope = 'fam'">Toda una familia</button></div>
        @if (mf.scope === 'proc') {
          <div class="field"><label class="req" for="mp">Procedimiento</label>
            <select id="mp" class="input" name="mp" [(ngModel)]="mf.procedureId"><option [ngValue]="null">Seleccionar…</option>
              @for (p of procs; track p.id) { @if (p.active) { <option [ngValue]="p.id">{{ p.name }} · {{ money(p.price) }}</option> } }</select></div>
        } @else {
          <div class="field"><label class="req" for="mfa">Familia</label>
            <select id="mfa" class="input" name="mfa" [(ngModel)]="mf.family"><option value="">Seleccionar…</option>@for (f of families; track f) { <option [value]="f">{{ f }}</option> }</select></div>
        }
        <div class="grid two">
          <div class="field"><label class="req" for="mt">Tipo</label>
            <select id="mt" class="input" name="mt" [(ngModel)]="mf.discountType"><option value="">Seleccionar…</option><option value="PORCENTAJE">% de descuento</option><option value="MONTO">S/ de descuento</option><option value="PRECIO">Precio promocional</option></select></div>
          <div class="field"><label class="req" for="mv">{{ mf.discountType === 'PORCENTAJE' ? 'Porcentaje' : 'Soles' }}</label><input id="mv" class="input" type="number" min="0" step="0.5" name="mv" [(ngModel)]="mf.value" appOnly="decimal" /></div>
          <div class="field"><label class="req" for="mfr">Desde</label><input id="mfr" class="input" type="date" name="mfr" [(ngModel)]="mf.validFrom" /></div>
          <div class="field"><label class="req" for="mto">Hasta</label><input id="mto" class="input" type="date" name="mto" [(ngModel)]="mf.validTo" /></div>
        </div>
        @if (preview) { <p class="notice small">{{ preview }}</p> }
        <div class="field"><label for="mc">Condiciones (se muestran al paciente)</label><input id="mc" class="input" name="mc" [(ngModel)]="mf.conditions" maxlength="255" placeholder="No acumulable. Previa evaluación." appOnly="text" /></div>
        <label class="check"><input type="checkbox" name="ma" [(ngModel)]="mf.active" /> Activa</label>
      </form>
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="promoOpen = false">Cancelar</button>
        <button class="btn primary" type="button" (click)="savePromo()" [disabled]="busy">Guardar</button>
      </ng-container>
    </app-modal>
  `,
  styles: [`
    .tabs { display: flex; gap: 4px; border-bottom: 1px solid var(--line); margin-bottom: 14px; }
    .tabs button { border: 0; background: none; padding: 10px 14px; font: inherit; cursor: pointer; color: var(--ink-2); border-bottom: 2px solid transparent; }
    .tabs button.on { color: var(--teal-strong); border-bottom-color: var(--teal); font-weight: 700; }
    .count { display: inline-block; min-width: 20px; padding: 0 6px; margin-left: 4px; border-radius: 10px; background: var(--teal); color: #fff; font-size: 12px; }
    .grid.two { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
    .off { opacity: .55; } .center { text-align: center; padding: 24px; } .right { text-align: right; } .search { max-width: 340px; }
    @media (max-width: 640px) { .grid.two { grid-template-columns: 1fr; } }
  `],
})
export class CatalogComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  private auth = inject(AuthService);
  pageOf = pageOf;
  tab: 'proc' | 'promo' = 'proc';
  procs: any[] = [];
  promos: any[] = [];
  q = '';
  pg = 1; pgPromo = 1;
  view: any = null;
  busy = false;
  procOpen = false; pf: any = {};
  promoOpen = false; mf: any = {};
  categories = ['Diagnóstico', 'Prevención', 'Operatoria', 'Endodoncia', 'Cirugía', 'Prótesis', 'Implantología', 'Ortodoncia', 'Estética', 'Periodoncia', 'Odontopediatría'];

  get canEdit() { return this.auth.can('PROMOCIONES'); }
  get families() { return [...new Set(this.procs.map((p) => p.family).filter((f) => !!f))].sort(); }
  get activePromos() { return this.promos.filter((p) => p.state === 'VIGENTE').length; }
  get shownProcs() { const q = this.q.trim().toLowerCase(); return this.procs.filter((p) => !q || [p.name, p.code, p.family, p.category].some((x) => (x || '').toLowerCase().includes(q))); }
  get preview() {
    const p = this.procs.find((x) => x.id === this.mf.procedureId);
    const v = +this.mf.value;
    if (!p || !v || !this.mf.discountType) return '';
    const price = +p.price;
    const fin = this.mf.discountType === 'PORCENTAJE' ? price - price * v / 100 : this.mf.discountType === 'MONTO' ? price - v : v;
    return `${p.name}: ${soles(price)} → ${soles(Math.max(0, fin))} (ahorro ${soles(Math.max(0, price - fin))}).`;
  }

  ngOnInit() { this.load(); }
  load() {
    this.api.catalogProcedures(this.canEdit).subscribe((p) => (this.procs = p));
    this.api.promotions().subscribe((p) => (this.promos = p));
  }
  variants(p: any) { return p.family ? this.procs.filter((x) => x.family === p.family && x.active) : [p]; }
  money = soles;
  date = shortDate;
  promoText(p: any) { return p.discountType === 'PORCENTAJE' ? `${+p.value} % de descuento` : p.discountType === 'MONTO' ? `${soles(p.value)} de descuento` : `Precio especial ${soles(p.value)}`; }
  stateLabel(s: string) { return ({ VIGENTE: 'Vigente', PROGRAMADA: 'Programada', VENCIDA: 'Vencida', INACTIVA: 'Inactiva' } as any)[s] || s; }

  newProc() { this.pf = { id: null, code: '', name: '', category: '', family: '', price: null, description: '', durationMin: 30, active: true }; this.procOpen = true; }
  editProc(p: any) { this.pf = { ...p, price: +p.price }; this.procOpen = true; }
  saveProc() {
    this.busy = true;
    this.api.saveProcedure(this.pf.id, { ...this.pf, price: this.pf.price == null ? null : +this.pf.price }).subscribe({
      next: () => { this.busy = false; this.procOpen = false; this.toast.ok('Procedimiento guardado.'); this.load(); },
      error: (e) => { this.busy = false; const a = apiError(e); this.toast.error(a.fields ? Object.values(a.fields)[0] : a.message); },
    });
  }
  newPromo() {
    const t = new Date(); t.setDate(t.getDate() + 30);
    this.mf = { id: null, name: '', scope: 'proc', procedureId: null, family: '', discountType: '', value: null, validFrom: isoDate(), validTo: isoDate(t), conditions: '', active: true };
    this.promoOpen = true;
  }
  editPromo(p: any) { this.mf = { ...p, scope: p.procedureId ? 'proc' : 'fam', value: +p.value, family: p.family || '' }; this.promoOpen = true; }
  savePromo() {
    this.busy = true;
    const b = { ...this.mf, procedureId: this.mf.scope === 'proc' ? this.mf.procedureId : null, family: this.mf.scope === 'fam' ? this.mf.family : null, value: +this.mf.value };
    this.api.savePromotion(this.mf.id, b).subscribe({
      next: (list) => { this.busy = false; this.promoOpen = false; this.promos = list; this.toast.ok('Promoción guardada.'); this.load(); this.tab = 'promo'; },
      error: (e) => { this.busy = false; const a = apiError(e); this.toast.error(a.fields ? Object.values(a.fields)[0] : a.message); },
    });
  }
}
