import { Component, EventEmitter, HostListener, Input, Output } from '@angular/core';
import { IconComponent } from './icon.component';

@Component({
  selector: 'app-modal',
  standalone: true,
  imports: [IconComponent],
  template: `
    @if (open) {
      <div class="backdrop" (click)="close.emit()"></div>
      <section class="modal" role="dialog" aria-modal="true" [attr.aria-label]="title" [style.max-width.px]="width">
        <header>
          <div>
            <h2>{{ title }}</h2>
            @if (subtitle) { <p class="muted small">{{ subtitle }}</p> }
          </div>
          <button type="button" class="btn ghost sm" (click)="close.emit()" aria-label="Cerrar"><app-icon name="x" /></button>
        </header>
        <div class="body"><ng-content></ng-content></div>
        <footer><ng-content select="[modal-actions]"></ng-content></footer>
      </section>
    }
  `,
  styles: [`
    .backdrop { position: fixed; inset: 0; background: rgba(15, 43, 49, .42); z-index: 200; animation: fade .15s; }
    .modal { position: fixed; z-index: 201; left: 50%; top: 50%; transform: translate(-50%, -50%);
      width: calc(100vw - 32px); max-height: calc(100vh - 48px); display: flex; flex-direction: column;
      background: var(--surface); border-radius: var(--radius-l); box-shadow: var(--shadow-pop); animation: pop .18s ease-out; }
    header { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; padding: 20px 22px 12px; }
    header h2 { font-size: var(--step-1); }
    .body { padding: 6px 22px 18px; overflow: auto; }
    footer { display: flex; justify-content: flex-end; gap: 8px; padding: 14px 22px; border-top: 1px solid var(--line); }
    footer:empty { display: none; }
    @keyframes fade { from { opacity: 0; } }
    @keyframes pop { from { opacity: 0; transform: translate(-50%, -47%); } }
  `],
})
export class ModalComponent {
  @Input() open = false;
  @Input() title = '';
  @Input() subtitle = '';
  @Input() width = 560;
  @Output() close = new EventEmitter<void>();

  @HostListener('document:keydown.escape')
  onEsc() { if (this.open) this.close.emit(); }
}
