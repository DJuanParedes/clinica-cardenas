import { Component, Input } from '@angular/core';

type Art = { color: string; paths: string[] };

/** Imagen de un material del inventario: la foto subida o, si no tiene, un dibujo según el tipo de producto. */
@Component({
  selector: 'app-product-thumb',
  standalone: true,
  template: `
    @if (src) {
      <img class="thumb" [style.width.px]="size" [style.height.px]="size" [src]="src" [alt]="name" loading="lazy" />
    } @else {
      <span class="thumb art" [style.width.px]="size" [style.height.px]="size" [style.background]="art.color + '1a'" [title]="name">
        <svg viewBox="0 0 24 24" [attr.width]="size * 0.62" [attr.height]="size * 0.62" fill="none" [attr.stroke]="art.color" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          @for (d of art.paths; track $index) { <path [attr.d]="d" /> }
        </svg>
      </span>
    }
  `,
  styles: [`
    .thumb { display: inline-flex; align-items: center; justify-content: center; border-radius: 10px; object-fit: cover; flex: none; border: 1px solid var(--line); background: var(--surface); }
    .art { border-color: transparent; }
  `],
})
export class ProductThumbComponent {
  @Input() src: string | null | undefined;
  @Input() name = '';
  @Input() size = 44;

  get art(): Art {
    const n = (this.name || '').toLowerCase();
    if (/guante/.test(n)) return { color: '#2f7dd1', paths: ['M7 21v-7l-2-4a1.5 1.5 0 0 1 2.6-1.4L9 11V4.5a1.5 1.5 0 0 1 3 0V10V3.5a1.5 1.5 0 0 1 3 0V10V5a1.5 1.5 0 0 1 3 0v9a7 7 0 0 1-3 6v1'] };
    if (/anestesi|cartucho|jeringa|aguja/.test(n)) return { color: '#c2410c', paths: ['M18 3l3 3M16 5l3 3M17.5 6.5l-9 9M6 14l4 4M4 20l3-3M11 8l5 5'] };
    if (/lima|fresa|instrument|explorador/.test(n)) return { color: '#6d28d9', paths: ['M6 21l3-3M9 18L19 4M12 15l2 2M14 13l2 2M16 11l2 2'] };
    if (/caja|gutapercha|algod|gasa|mascarilla|babero|eyector/.test(n)) return { color: '#0e7c7b', paths: ['M3 8l9-5 9 5v8l-9 5-9-5z', 'M3 8l9 5 9-5M12 13v8'] };
    if (/resina|composite|ion[oó]mero|amalgama/.test(n)) return { color: '#b45309', paths: ['M9 3h6v4H9zM8 7h8l-1 14H9z', 'M10 12h4'] };
    if (/gel|fl[uú]or|[aá]cido|adhesivo|clorhex|hipoclorito|enjuague|alcohol/.test(n)) return { color: '#0f766e', paths: ['M10 2h4v4h-4zM9 6h6l1 3v11a2 2 0 0 1-2 2h-4a2 2 0 0 1-2-2V9z', 'M8 13h8'] };
    return { color: '#475569', paths: ['M4 7l8-4 8 4-8 4z', 'M4 7v10l8 4 8-4V7M12 11v10'] };
  }
}
