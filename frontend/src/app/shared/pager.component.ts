import { Component, EventEmitter, Input, Output } from '@angular/core';

/** Paginación de listas en pantalla: muestra de 10 en 10 (o el tamaño indicado) con anterior / siguiente. */
@Component({
  selector: 'app-pager',
  standalone: true,
  template: `
    @if (total > size) {
      <nav class="pager" aria-label="Paginación">
        <span class="muted small">{{ from }}–{{ to }} de {{ total }}</span>
        <div class="btns">
          <button type="button" class="pg" [disabled]="cur <= 1" (click)="go(1)" aria-label="Primera página">«</button>
          <button type="button" class="pg" [disabled]="cur <= 1" (click)="go(cur - 1)">Anterior</button>
          <span class="small strong">Página {{ cur }} de {{ pages }}</span>
          <button type="button" class="pg" [disabled]="cur >= pages" (click)="go(cur + 1)">Siguiente</button>
          <button type="button" class="pg" [disabled]="cur >= pages" (click)="go(pages)" aria-label="Última página">»</button>
        </div>
      </nav>
    }
  `,
  styles: [`
    .pager { display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; padding: 10px 16px; border-top: 1px solid var(--line); }
    .btns { display: flex; align-items: center; gap: 6px; }
    .pg { height: 32px; min-width: 32px; padding: 0 10px; border: 1px solid var(--line-strong); border-radius: 8px; background: var(--surface); font: inherit; font-size: 13px; font-weight: 700; color: var(--ink-2); cursor: pointer; }
    .pg:disabled { opacity: .45; cursor: not-allowed; }
  `],
})
export class PagerComponent {
  @Input() total = 0;
  @Input() page = 1;
  @Input() size = 10;
  @Output() pageChange = new EventEmitter<number>();

  get pages() { return Math.max(1, Math.ceil(this.total / this.size)); }
  get cur() { return Math.min(Math.max(1, this.page), this.pages); }
  get from() { return this.total === 0 ? 0 : (this.cur - 1) * this.size + 1; }
  get to() { return Math.min(this.total, this.cur * this.size); }
  go(p: number) { this.pageChange.emit(Math.min(Math.max(1, p), this.pages)); }
}

/** Corta una lista para la página indicada. */
export function pageOf<T>(list: T[] | null | undefined, page: number, size = 10): T[] {
  const all = list || [];
  const last = Math.max(1, Math.ceil(all.length / size));
  const p = Math.min(Math.max(1, page), last);
  return all.slice((p - 1) * size, p * size);
}
