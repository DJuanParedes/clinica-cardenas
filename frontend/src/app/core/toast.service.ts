import { Injectable, signal } from '@angular/core';

export interface Toast { id: number; text: string; kind: 'ok' | 'error' | 'warn'; }

@Injectable({ providedIn: 'root' })
export class ToastService {
  private seq = 0;
  readonly items = signal<Toast[]>([]);

  show(text: string, kind: Toast['kind'] = 'ok', ms = 4200) {
    const id = ++this.seq;
    this.items.update((l) => [...l, { id, text, kind }]);
    setTimeout(() => this.dismiss(id), ms);
  }
  ok(text: string) { this.show(text, 'ok'); }
  error(text: string) { this.show(text, 'error', 6500); }
  warn(text: string) { this.show(text, 'warn', 5500); }
  dismiss(id: number) { this.items.update((l) => l.filter((t) => t.id !== id)); }
}
