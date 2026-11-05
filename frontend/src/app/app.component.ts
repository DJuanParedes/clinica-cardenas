import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastService } from './core/toast.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `
    <router-outlet />
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
export class AppComponent {
  toast = inject(ToastService);
}
