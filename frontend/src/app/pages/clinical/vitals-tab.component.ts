import { Component, Input, OnChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { apiError, dateTime } from '../../core/format';
import { IconComponent } from '../../shared/icon.component';

interface Vital {
  id: number; takenAt: string; systolic?: number; diastolic?: number; heartRate?: number; respRate?: number; temperature?: number;
  spo2?: number; weight?: number; height?: number; glucose?: number; notes?: string; createdBy: string; flags: string[]; bmi?: number;
}

/** v2.5: signos vitales antes de atender. Marca presión alta/baja, fiebre, saturación y glucosa fuera de rango. */
@Component({
  selector: 'app-vitals-tab',
  standalone: true,
  imports: [FormsModule, IconComponent],
  template: `
    <section class="panel">
      <div class="panel-head"><h3>Registrar signos vitales</h3><span class="muted small">Llena solo lo que mediste</span></div>
      <form class="panel-body" (ngSubmit)="save()">
        <div class="vgrid">
          <div class="field pa">
            <label>Presión arterial <span class="muted">(mmHg)</span></label>
            <div class="row"><input class="input tabular" name="sys" type="number" [(ngModel)]="f.systolic" placeholder="120" [class.invalid]="e['systolic']" />
              <span>/</span><input class="input tabular" name="dia" type="number" [(ngModel)]="f.diastolic" placeholder="80" [class.invalid]="e['diastolic']" /></div>
            @if (e['systolic'] || e['diastolic']) { <span class="err">{{ e['systolic'] || e['diastolic'] }}</span> }
          </div>
          @for (x of fields; track x.k) {
            <div class="field">
              <label [for]="'v-' + x.k">{{ x.label }} <span class="muted">({{ x.unit }})</span></label>
              <input class="input tabular" [id]="'v-' + x.k" [name]="x.k" type="number" [step]="x.step || 1" [(ngModel)]="f[x.k]" [placeholder]="x.ph" [class.invalid]="e[x.k]" />
              @if (e[x.k]) { <span class="err">{{ e[x.k] }}</span> }
            </div>
          }
          <div class="field wide">
            <label for="v-notes">Observación</label>
            <input class="input" id="v-notes" name="notes" [(ngModel)]="f.notes" maxlength="250" placeholder="Ej.: tomó su antihipertensivo hoy" />
          </div>
        </div>
        <div class="row end"><button class="btn primary" type="submit" [disabled]="saving">{{ saving ? 'Guardando…' : 'Guardar' }}</button></div>
      </form>
    </section>

    <section class="panel">
      <div class="panel-head"><h3>Historial</h3><span class="muted small">{{ rows.length }} registros</span></div>
      @if (!rows.length) { <div class="empty small"><p>Sin registros de signos vitales.</p></div> }
      @else {
        <div class="scroll">
          <table class="table">
            <thead><tr><th>Fecha</th><th>PA</th><th>FC</th><th>FR</th><th>T°</th><th>SpO₂</th><th>Peso</th><th>Talla</th><th>IMC</th><th>Glucosa</th><th></th></tr></thead>
            <tbody>
              @for (v of rows; track v.id) {
                <tr [class.warn]="v.flags.length">
                  <td class="nowrap">{{ dt(v.takenAt) }}<div class="muted small">{{ v.createdBy }}</div></td>
                  <td class="tabular">{{ v.systolic ? v.systolic + '/' + v.diastolic : '—' }}</td>
                  <td class="tabular">{{ v.heartRate ?? '—' }}</td>
                  <td class="tabular">{{ v.respRate ?? '—' }}</td>
                  <td class="tabular">{{ v.temperature ?? '—' }}</td>
                  <td class="tabular">{{ v.spo2 != null ? v.spo2 + '%' : '—' }}</td>
                  <td class="tabular">{{ v.weight ?? '—' }}</td>
                  <td class="tabular">{{ v.height ?? '—' }}</td>
                  <td class="tabular">{{ v.bmi ?? '—' }}</td>
                  <td class="tabular">{{ v.glucose ?? '—' }}</td>
                  <td>
                    @for (fl of v.flags; track fl) { <span class="tag gum">{{ fl }}</span> }
                    @if (v.notes) { <div class="muted small">{{ v.notes }}</div> }
                    @if (canDelete(v)) { <button type="button" class="btn ghost sm" (click)="del(v)" aria-label="Borrar registro"><app-icon name="x" [size]="14" /></button> }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
  styles: [`
    :host { display: grid; gap: 16px; }
    .vgrid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 12px; }
    .pa { grid-column: span 2; }
    .pa .row { gap: 6px; align-items: center; }
    .wide { grid-column: 1 / -1; }
    .end { justify-content: flex-end; margin-top: 10px; }
    .scroll { overflow-x: auto; }
    tr.warn td { background: #fff5f6; }
    .tag { margin: 0 4px 4px 0; }
  `],
})
export class VitalsTabComponent implements OnChanges {
  @Input() patientId!: number;
  private api = inject(Api);
  private toast = inject(ToastService);
  private auth = inject(AuthService);
  rows: Vital[] = [];
  f: any = {};
  e: Record<string, string> = {};
  saving = false;
  fields = [
    { k: 'heartRate', label: 'Pulso', unit: 'lpm', ph: '72' },
    { k: 'respRate', label: 'Frec. respiratoria', unit: 'rpm', ph: '16' },
    { k: 'temperature', label: 'Temperatura', unit: '°C', ph: '36.5', step: 0.1 },
    { k: 'spo2', label: 'Saturación', unit: '%', ph: '98' },
    { k: 'weight', label: 'Peso', unit: 'kg', ph: '65', step: 0.1 },
    { k: 'height', label: 'Talla', unit: 'cm', ph: '165' },
    { k: 'glucose', label: 'Glucosa', unit: 'mg/dL', ph: '95' },
  ];

  ngOnChanges() { this.api.vitals(this.patientId).subscribe((r) => (this.rows = r)); }

  save() {
    const body: any = {};
    for (const [k, v] of Object.entries(this.f)) if (v !== null && v !== undefined && v !== '') body[k] = v;
    this.saving = true; this.e = {};
    this.api.addVitals(this.patientId, body).subscribe({
      next: (r) => {
        this.saving = false; this.rows = r; this.f = {};
        const fl = r[0]?.flags || [];
        fl.length ? this.toast.warn('Guardado. Atención: ' + fl.join(', ') + '.') : this.toast.ok('Signos vitales guardados.');
      },
      error: (err) => { this.saving = false; const a = apiError(err); this.e = a.fields || {}; this.toast.error(a.message); },
    });
  }

  canDelete(v: Vital) { return this.auth.can('ELIMINAR_REGISTROS'); }
  del(v: Vital) {
    if (!confirm('¿Borrar este registro de signos vitales?')) return;
    this.api.deleteVitals(this.patientId, v.id).subscribe({ next: (r) => (this.rows = r), error: (err) => this.toast.error(apiError(err).message) });
  }
  dt = dateTime;
}
