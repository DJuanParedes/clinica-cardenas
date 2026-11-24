import { Component, NgZone, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastService } from './core/toast.service';
import { DeleteDialogComponent } from './shared/delete-dialog.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, DeleteDialogComponent],
  template: `
    <router-outlet />
    <app-delete-dialog />
    <div class="toasts" aria-live="polite">
      @for (t of toast.items(); track t.id) {
        <div class="toast" [class]="'toast ' + t.kind" role="status">
          <span class="dot"></span>
          <span class="msg">{{ t.text }}</span>
          <button type="button" class="x" (click)="toast.dismiss(t.id)" aria-label="Cerrar aviso">×</button>
        </div>
      }
    </div>
  `,
  styles: [`
    .toasts { position: fixed; right: 20px; bottom: 20px; display: flex; flex-direction: column; gap: 8px; z-index: 1000; max-width: min(420px, calc(100vw - 40px)); }
    .toast { display: flex; align-items: flex-start; gap: 10px; padding: 12px 14px; border-radius: 10px;
      background: var(--night); color: #eef5f5; box-shadow: var(--shadow-pop); font-size: 14px; animation: rise .18s ease-out; }
    .toast .dot { width: 8px; height: 8px; border-radius: 50%; background: #5fc4b5; margin-top: 7px; flex: none; }
    .toast.error .dot { background: #f08aa3; }
    .toast.warn .dot { background: #f0c46a; }
    .toast .msg { flex: 1; }
    .toast .x { background: none; border: 0; color: #9fb7bb; font-size: 18px; line-height: 1; cursor: pointer; padding: 0 2px; }
    @keyframes rise { from { transform: translateY(8px); opacity: 0; } }
  `],
})
export class AppComponent implements OnInit, OnDestroy {
  toast = inject(ToastService);
  private zone = inject(NgZone);
  private obs?: MutationObserver;
  private timer: any;

  /**
   * v2.10: en celular y tablet cada fila de tabla se muestra como tarjeta; para que se entienda, cada celda lleva
   * el nombre de su columna (data-label), que el CSS muestra delante del valor.
   */
  ngOnInit() {
    this.zone.runOutsideAngular(() => {
      const label = () => {
        document.querySelectorAll<HTMLTableElement>('table.table:not(.keep)').forEach((t) => {
          const heads = Array.from(t.querySelectorAll('thead th')).map((th) => (th.textContent || '').trim());
          if (!heads.length) return;
          t.querySelectorAll('tbody tr').forEach((tr) => {
            let col = 0;
            Array.from(tr.children).forEach((td, i) => {
              const span = Number(td.getAttribute('colspan') || 1);
              const h = span > 1 || i === 0 || td.querySelector('.btn, input, select') ? '' : heads[col] || '';
              if (td.getAttribute('data-label') !== h) td.setAttribute('data-label', h);
              col += span;
            });
          });
        });
      };
      this.obs = new MutationObserver(() => { clearTimeout(this.timer); this.timer = setTimeout(label, 120); });
      this.obs.observe(document.body, { childList: true, subtree: true });
    });
  }

  ngOnDestroy() { this.obs?.disconnect(); }
}
