import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { DeleteService } from '../core/delete.service';
import { IconComponent } from './icon.component';

/** v2.11: botón Eliminar. Solo aparece para quien tiene ELIMINAR_REGISTROS (el administrador). */
@Component({
  selector: 'app-delete-button',
  standalone: true,
  imports: [IconComponent],
  template: `
    @if (svc.allowed) {
      <button type="button" class="btn danger" [class.sm]="small" [class.ghost]="ghost" (click)="ask($event)"
              [attr.title]="'Eliminar ' + (what || 'registro').toLowerCase()" [attr.aria-label]="'Eliminar ' + (what || 'registro').toLowerCase()">
        <app-icon name="trash" [size]="small ? 14 : 16" />@if (!iconOnly) { <span>{{ text }}</span> }
      </button>
    }
  `,
  styles: [`:host { display: contents; } button { gap: 6px; }`],
})
export class DeleteButtonComponent {
  svc = inject(DeleteService);
  @Input({ required: true }) entity!: string;
  @Input({ required: true }) recordId!: string | number;
  @Input() what = '';
  @Input() text = 'Eliminar';
  @Input() small = true;
  @Input() ghost = false;
  @Input() iconOnly = false;
  @Output() deleted = new EventEmitter<void>();

  ask(ev: Event) {
    ev.stopPropagation();
    this.svc.ask(this.entity, this.recordId, this.what).subscribe((ok) => { if (ok) this.deleted.emit(); });
  }
}
