import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api.service';
import { dateTime } from '../core/format';
import { IconComponent } from '../shared/icon.component';

import { OnlyDirective } from '../shared/only.directive';
const TITLES: Record<string, string> = {
  patients: 'Paciente', appointments: 'Cita', users: 'Usuario', dentists: 'Odontólogo', procedures: 'Procedimiento',
  treatment_plans: 'Plan de tratamiento', treatment_items: 'Tratamiento', tasks: 'Tarea', leads: 'Oportunidad CRM',
  lab_orders: 'Orden de laboratorio', laboratories: 'Laboratorio', commission_rules: 'Regla de comisión',
  consent_templates: 'Plantilla de consentimiento', message_templates: 'Plantilla de mensaje', automation_rules: 'Recordatorio automático',
  anamnesis_templates: 'Formulario de anamnesis', campaigns: 'Campaña', segments: 'Segmento', promotions: 'Promoción',
  inventory_items: 'Material', inventory_purchases: 'Compra', supply_requests: 'Pedido de material', expenses: 'Egreso',
  waitlist: 'Lista de espera', patient_notes: 'Nota', assistant_faq: 'Pregunta del asistente', invoice_series: 'Serie',
};

/** v2.11: todo lo que el administrador eliminó, con quién, cuándo, por qué y una copia descargable. */
@Component({
  selector: 'app-trash',
  standalone: true,
  imports: [OnlyDirective, FormsModule, IconComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <h1>Papelera</h1>
          <p class="sub">Registros eliminados por el administrador. Cada uno guarda una copia completa (con lo que se borró junto) que puedes descargar.</p>
        </div>
        <input class="input search" placeholder="Buscar…" [(ngModel)]="q" aria-label="Buscar en la papelera" appOnly="search" maxlength="100" />
      </div>
      @if (!rows) {
        <div class="skeleton" style="height: 240px"></div>
      } @else if (!filtered.length) {
        <section class="panel empty"><app-icon name="trash" [size]="28" /><p>{{ rows.length ? 'Nada coincide con la búsqueda.' : 'Todavía no se ha eliminado nada.' }}</p></section>
      } @else {
        <section class="panel">
          <table class="table">
            <thead><tr><th>Fecha</th><th>Tipo</th><th>Registro</th><th>Qué se borró</th><th>Motivo</th><th>Quién</th><th></th></tr></thead>
            <tbody>
              @for (r of filtered; track r.id) {
                <tr>
                  <td>{{ dt(r.deletedAt) }}</td>
                  <td><span class="tag plain grey">{{ title(r.entity) }}</span></td>
                  <td class="strong">{{ r.label }}</td>
                  <td class="muted small">{{ r.summary }}</td>
                  <td class="small">{{ r.reason || '—' }}</td>
                  <td>{{ r.deletedBy }}</td>
                  <td class="right"><button class="btn sm" type="button" (click)="download(r)"><app-icon name="download" [size]="14" /> Copia</button></td>
                </tr>
              }
            </tbody>
          </table>
        </section>
      }
    </div>
  `,
  styles: [`.search { max-width: 260px; } .empty { text-align: center; padding: 40px; color: var(--muted); }`],
})
export class TrashComponent implements OnInit {
  private api = inject(Api);
  rows: any[] | null = null;
  q = '';
  dt = dateTime;

  ngOnInit() { this.api.trash().subscribe((r) => (this.rows = r)); }

  title(e: string) { return TITLES[e] || e; }

  get filtered() {
    const q = this.q.trim().toLowerCase();
    return (this.rows || []).filter((r) => !q || [r.label, r.summary, r.reason, r.deletedBy, this.title(r.entity)].join(' ').toLowerCase().includes(q));
  }

  download(r: any) {
    this.api.trashData(r.id).subscribe((b) => {
      const a = document.createElement('a');
      a.href = URL.createObjectURL(b);
      a.download = `eliminado-${r.entity}-${r.entityId}.json`;
      a.click();
      setTimeout(() => URL.revokeObjectURL(a.href), 2000);
    });
  }
}
