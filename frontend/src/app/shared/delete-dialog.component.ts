import { Component, effect, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api, DeletePreview } from '../core/api.service';
import { DeleteService } from '../core/delete.service';
import { ToastService } from '../core/toast.service';
import { apiError } from '../core/format';
import { IconComponent } from './icon.component';

import { OnlyDirective } from './only.directive';
/** v2.11: confirmación de eliminación con todo lo que se borra junto, lo que lo impide y la papelera. */
@Component({
  selector: 'app-delete-dialog',
  standalone: true,
  imports: [OnlyDirective, FormsModule, IconComponent],
  template: `
    @if (svc.current(); as c) {
      <div class="backdrop" (click)="cancel()"></div>
      <section class="dlg" role="dialog" aria-modal="true" aria-labelledby="del-title">
        <header>
          <h2 id="del-title"><app-icon name="trash" [size]="18" /> Eliminar {{ (p?.title || c.what || 'registro').toLowerCase() }}</h2>
          <button type="button" class="btn ghost sm" (click)="cancel()" aria-label="Cerrar"><app-icon name="x" /></button>
        </header>
        <div class="body">
          @if (loading) {
            <div class="skeleton" style="height: 120px"></div>
          } @else if (error) {
            <p class="err">{{ error }}</p>
          } @else if (p) {
            <p class="who">{{ p.label }}</p>
            @if (p.blockers.length) {
              <div class="box block">
                <strong>No se puede eliminar:</strong>
                <ul>@for (b of p.blockers; track b) { <li>{{ b }}</li> }</ul>
              </div>
            } @else {
              @if (keys(p.alsoDeleted).length) {
                <div class="box">
                  <strong>También se eliminará:</strong>
                  <ul>@for (k of keys(p.alsoDeleted); track k) { <li>{{ p.alsoDeleted[k] }} {{ k }}</li> }</ul>
                </div>
              } @else {
                <p class="muted">No tiene otros registros que dependan de él.</p>
              }
              @if (keys(p.unlinked).length) {
                <div class="box soft">
                  <strong>Se conservan, pero sin este vínculo:</strong>
                  <ul>@for (k of keys(p.unlinked); track k) { <li>{{ p.unlinked[k] }} {{ k }}</li> }</ul>
                </div>
              }
            }
            @for (w of p.warnings; track w) { <p class="warn"><app-icon name="alert" [size]="14" /> {{ w }}</p> }
            @if (p.canDelete) {
              <div class="field"><label for="del-reason">Motivo (opcional)</label>
                <input id="del-reason" class="input" [(ngModel)]="reason" maxlength="300" placeholder="Registro de prueba, duplicado…" appOnly="text" /></div>
              <div class="field"><label for="del-confirm">Escribe <b>ELIMINAR</b> para confirmar</label>
                <input id="del-confirm" class="input" [(ngModel)]="confirm" autocomplete="off" (keyup.enter)="go()" appOnly="nospace" maxlength="72" /></div>
              <p class="muted small">No se puede deshacer desde la pantalla, pero queda una copia completa en <b>Papelera</b>.</p>
            }
          }
        </div>
        <footer>
          <button class="btn" type="button" (click)="cancel()">{{ p && !p.canDelete ? 'Entendido' : 'Cancelar' }}</button>
          @if (p?.canDelete) {
            <button class="btn danger" type="button" (click)="go()" [disabled]="busy || confirm.trim().toUpperCase() !== 'ELIMINAR'">
              @if (busy) { <span class="spin"></span> } <app-icon name="trash" [size]="15" /> Eliminar definitivamente
            </button>
          }
        </footer>
      </section>
    }
  `,
  styles: [`
    .backdrop { position: fixed; inset: 0; background: rgba(15, 43, 49, .5); z-index: 400; }
    .dlg { position: fixed; z-index: 401; left: 50%; top: 50%; transform: translate(-50%, -50%); width: min(520px, calc(100vw - 24px));
      max-height: calc(100vh - 40px); display: flex; flex-direction: column; background: var(--surface); border-radius: var(--radius-l); box-shadow: var(--shadow-pop); }
    header { display: flex; justify-content: space-between; align-items: center; padding: 18px 20px 8px; }
    header h2 { font-size: var(--step-1); display: flex; gap: 8px; align-items: center; color: var(--gum); }
    .body { padding: 4px 20px 14px; overflow: auto; display: flex; flex-direction: column; gap: 10px; }
    footer { display: flex; justify-content: flex-end; gap: 8px; padding: 12px 20px; border-top: 1px solid var(--line); flex-wrap: wrap; }
    .who { font-weight: 800; color: var(--ink); }
    .box { border: 1px solid var(--line); border-radius: 10px; padding: 10px 12px; font-size: 14px; }
    .box ul { margin: 6px 0 0 18px; padding: 0; }
    .box.block { border-color: #e7b9c5; background: var(--gum-soft); }
    .box.soft { background: var(--surface-2); }
    .warn { display: flex; gap: 6px; align-items: flex-start; font-size: 13px; color: #8a5a00; background: #fff6e0; border-radius: 8px; padding: 8px 10px; margin: 0; }
    .err { color: var(--gum); }
  `],
})
export class DeleteDialogComponent {
  svc = inject(DeleteService);
  private api = inject(Api);
  private toast = inject(ToastService);
  p: DeletePreview | null = null;
  loading = false; busy = false; error = '';
  confirm = ''; reason = '';

  constructor() {
    effect(() => {
      const c = this.svc.current();
      if (!c) return;
      this.p = null; this.error = ''; this.confirm = ''; this.reason = ''; this.loading = true;
      this.api.deletePreview(c.entity, c.id).subscribe({
        next: (p) => { this.p = p; this.loading = false; },
        error: (e) => { this.error = apiError(e).message; this.loading = false; },
      });
    });
  }

  keys(o: Record<string, number>) { return Object.keys(o || {}); }

  cancel() { if (!this.busy) this.svc.finish(false); }

  go() {
    const c = this.svc.current();
    if (!c || !this.p?.canDelete || this.confirm.trim().toUpperCase() !== 'ELIMINAR') return;
    this.busy = true;
    this.api.deleteRecord(c.entity, c.id, this.confirm, this.reason).subscribe({
      next: (r) => { this.busy = false; this.toast.ok(`Eliminado: ${r.label}`); this.svc.finish(true); },
      error: (e) => { this.busy = false; this.toast.error(apiError(e).message); },
    });
  }
}
