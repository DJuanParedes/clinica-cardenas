import { Component, ElementRef, EventEmitter, Input, OnChanges, Output, ViewChild, inject } from '@angular/core';
import { Api } from '../core/api.service';
import { ModalComponent } from './modal.component';

/**
 * Política de privacidad / términos en un modal: el botón "Acepto" se habilita solo al llegar al final del texto
 * (lectura completa antes de consentir, Ley N.° 29733). Emite la versión aceptada.
 */
@Component({
  selector: 'app-legal-modal',
  standalone: true,
  imports: [ModalComponent],
  template: `
    <app-modal [open]="open" [title]="doc?.title || 'Política de privacidad'" [subtitle]="doc ? 'Versión ' + doc.version + (who ? ' · acepta: ' + who : '') : ''" [width]="760" (close)="closed.emit()">
      @if (doc) {
        <div #box class="legal" (scroll)="onScroll($event)" tabindex="0" aria-label="Texto completo del documento">
          @for (p of paragraphs; track $index) { <p>{{ p }}</p> }
          <p class="end">— Fin del documento —</p>
        </div>
        @if (!read) { <p class="muted small hint">Desplázate hasta el final para poder aceptar.</p> }
      } @else { <p class="muted">Cargando…</p> }
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="closed.emit()">Cancelar</button>
        <button class="btn primary" type="button" [disabled]="!read" (click)="accept()">He leído y acepto</button>
      </ng-container>
    </app-modal>
  `,
  styles: [`
    .legal { max-height: 52vh; overflow-y: auto; padding: 14px 16px; border: 1px solid var(--line); border-radius: 12px; background: var(--surface-2, #f6f8fa); line-height: 1.55; font-size: 14px; }
    .legal p { margin: 0 0 12px; }
    .end { text-align: center; color: var(--muted, #6b7a80); font-size: 12px; }
    .hint { margin-top: 8px; }
  `],
})
export class LegalModalComponent implements OnChanges {
  private api = inject(Api);
  @Input() open = false;
  @Input() code = 'PRIVACIDAD';
  @Input() who = '';
  @Output() accepted = new EventEmitter<string>();
  @Output() closed = new EventEmitter<void>();
  @ViewChild('box') box?: ElementRef<HTMLElement>;
  doc: { version: string; title: string; body: string } | null = null;
  read = false;

  get paragraphs() { return (this.doc?.body || '').split(/\n\s*\n/); }

  ngOnChanges() {
    if (this.open) {
      this.read = false;
      if (!this.doc) this.api.legal(this.code).subscribe((d) => { this.doc = d; this.checkShort(); });
      else this.checkShort();
    }
  }

  /** Si el texto entra completo sin desplazarse, ya está leído. */
  private checkShort() {
    setTimeout(() => {
      const el = this.box?.nativeElement;
      if (el && el.scrollHeight <= el.clientHeight + 12) this.read = true;
    }, 60);
  }

  onScroll(ev: Event) {
    const el = ev.target as HTMLElement;
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 12) this.read = true;
  }

  accept() {
    if (!this.read || !this.doc) return;
    this.accepted.emit(this.doc.version);
  }
}
