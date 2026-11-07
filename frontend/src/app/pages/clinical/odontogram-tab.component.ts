import { Component, Input, OnInit, inject } from '@angular/core';
import { Api } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { OdontogramEntry } from '../../core/models';
import { apiError, dateTime } from '../../core/format';
import { OdontogramComponent } from '../../shared/odontogram.component';
import { IconComponent } from '../../shared/icon.component';

/** HU-05: diagnóstico gráfico por pieza y cara. */
@Component({
  selector: 'app-odontogram-tab',
  standalone: true,
  imports: [OdontogramComponent, IconComponent],
  template: `
    <section class="panel">
      <div class="panel-head">
        <div>
          <h3>Odontograma</h3>
          <p class="muted small">
            @if (updatedAt) { Última actualización {{ dt(updatedAt) }} por {{ updatedBy }} } @else { Aún no se registró el odontograma de este paciente. }
          </p>
        </div>
        <div class="row">
          @if (!editing) {
            <button class="btn" type="button" (click)="print()"><app-icon name="print" [size]="16" /> Imprimir</button>
            @if (canEdit) { <button class="btn primary" type="button" (click)="startEdit()"><app-icon name="edit" [size]="16" /> Editar odontograma</button> }
          } @else {
            @if (dirty) { <span class="muted small">Cambios sin guardar</span> }
            <button class="btn" type="button" (click)="cancel()">Descartar</button>
            <button class="btn primary" type="button" (click)="save()" [disabled]="saving || !dirty">
              @if (saving) { <span class="spin"></span> } Guardar odontograma
            </button>
          }
        </div>
      </div>
      <div class="panel-body">
        @if (loaded) {
          <app-odontogram [entries]="editing ? draft : entries" [editable]="editing" (entriesChange)="onChange($event)" />
        } @else {
          <div class="skeleton" style="height: 260px"></div>
        }
      </div>
    </section>
  `,
})
export class OdontogramTabComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  private auth = inject(AuthService);

  @Input() patientId!: number;

  entries: OdontogramEntry[] = [];
  draft: OdontogramEntry[] = [];
  editing = false;
  dirty = false;
  saving = false;
  loaded = false;
  updatedAt?: string;
  updatedBy?: string;

  get canEdit() { return this.auth.can('HISTORIA_CLINICA'); }

  ngOnInit() {
    this.api.odontogram(this.patientId).subscribe((r) => this.apply(r));
  }

  private apply(r: { entries: OdontogramEntry[]; updatedAt?: string; updatedBy?: string }) {
    this.entries = r.entries;
    this.updatedAt = r.updatedAt;
    this.updatedBy = r.updatedBy;
    this.loaded = true;
  }

  startEdit() {
    this.draft = this.entries.map((e) => ({ ...e }));
    this.dirty = false;
    this.editing = true;
  }

  onChange(list: OdontogramEntry[]) {
    this.draft = list;
    this.dirty = true;
  }

  cancel() {
    this.editing = false;
    this.dirty = false;
  }

  save() {
    this.saving = true;
    const payload = this.draft.map((e) => ({ tooth: e.tooth, face: e.face, condition: e.condition, note: e.note || null }));
    this.api.saveOdontogram(this.patientId, payload as OdontogramEntry[]).subscribe({
      next: (r) => {
        this.apply(r);
        this.saving = false;
        this.editing = false;
        this.dirty = false;
        this.toast.ok(`Odontograma guardado (${r.entries.length} marcas).`);
      },
      error: (e) => { this.saving = false; this.toast.error(apiError(e).message); },
    });
  }

  print() { window.print(); }
  dt = dateTime;
}
