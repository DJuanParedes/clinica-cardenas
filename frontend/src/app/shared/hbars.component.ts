import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { soles } from '../core/format';

export interface HBarItem { name: string; value: number; note?: string; }

/** Ranking con barras horizontales (participación de cada elemento sobre el mayor). */
@Component({
  selector: 'app-hbars',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!items.length) { <p class="muted small">Sin datos en el período.</p> }
    <ul>
      @for (it of items; track it.name) {
        <li>
          <div class="top"><span class="nm">{{ it.name }}</span><span class="v tabular">{{ money ? soles(it.value) : it.value }}@if (it.note) { <span class="muted"> · {{ it.note }}</span> }</span></div>
          <div class="track"><div class="fill" [style.width.%]="width(it.value)" [style.background]="color"></div></div>
        </li>
      }
    </ul>
  `,
  styles: [`
    ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 12px; }
    .top { display: flex; justify-content: space-between; gap: 10px; font-size: 13px; margin-bottom: 5px; }
    .nm { font-weight: 600; color: var(--ink); }
    .v { font-weight: 700; white-space: nowrap; }
    .track { height: 8px; border-radius: 99px; background: var(--surface-2); overflow: hidden; }
    .fill { height: 100%; border-radius: 99px; }
  `],
})
export class HBarsComponent {
  @Input() items: HBarItem[] = [];
  @Input() money = false;
  @Input() color = 'var(--brand)';
  soles = soles;
  width(v: number) {
    const max = Math.max(...this.items.map((i) => i.value), 0);
    return max ? Math.max(2, (v / max) * 100) : 0;
  }
}
