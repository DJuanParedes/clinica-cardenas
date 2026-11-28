import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ModalComponent } from './modal.component';
import { OnlyDirective } from './only.directive';

/** Botón "Anular" que pide el motivo. Para registros clínicos que no se borran (NTS 139-MINSA). */
@Component({
  selector: 'app-void-button',
  standalone: true,
  imports: [FormsModule, ModalComponent, OnlyDirective],
  template: `
    <button class="btn sm ghost" type="button" (click)="open = true; reason = ''" [title]="'Anular ' + what">Anular</button>
    <app-modal [open]="open" [title]="'Anular ' + what" subtitle="No se borra: quedará tachado, con el motivo, quién y cuándo." (close)="open = false">
      <div class="field"><label for="vr">Motivo de la anulación</label>
        <textarea id="vr" class="input" rows="3" [(ngModel)]="reason" maxlength="300" appOnly="text" placeholder="Ej.: registrado en el paciente equivocado"></textarea></div>
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="open = false">Cancelar</button>
        <button class="btn danger" type="button" [disabled]="busy || reason.trim().length < 5" (click)="confirm.emit(reason.trim()); open = false">Anular</button>
      </ng-container>
    </app-modal>`,
})
export class VoidButtonComponent {
  @Input() what = 'registro';
  @Input() busy = false;
  @Output() confirm = new EventEmitter<string>();
  open = false;
  reason = '';
}
