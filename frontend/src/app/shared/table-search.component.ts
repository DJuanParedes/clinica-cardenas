import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IconComponent } from './icon.component';

import { OnlyDirective } from './only.directive';
/** Caja de búsqueda estándar sobre una tabla (filtra en el navegador, sin recargar). */
@Component({
  selector: 'app-table-search',
  standalone: true,
  imports: [OnlyDirective, FormsModule, IconComponent],
  template: `
    <div class="ts">
      <app-icon name="search" [size]="16" />
      <input class="input" type="search" [ngModel]="q" (ngModelChange)="q = $event; qChange.emit($event)" [placeholder]="placeholder" [attr.aria-label]="placeholder" appOnly="search" maxlength="100" />
      @if (total != null) { <span class="muted small">{{ shown }} de {{ total }}</span> }
    </div>
  `,
  styles: [`
    .ts { position: relative; display: flex; align-items: center; gap: 10px; padding: 10px 14px; border-bottom: 1px solid var(--line); }
    .ts app-icon { position: absolute; left: 26px; color: var(--ink-3); pointer-events: none; }
    .ts .input { max-width: 360px; padding-left: 34px; }
  `],
})
export class TableSearchComponent {
  @Input() q = '';
  @Output() qChange = new EventEmitter<string>();
  @Input() placeholder = 'Buscar en la tabla';
  @Input() total: number | null = null;
  @Input() shown: number | null = null;
}
