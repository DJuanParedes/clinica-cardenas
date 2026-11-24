import { Component, Input, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api, Installments } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { apiError, isoDate, shortDate, soles } from '../../core/format';

const LABEL: Record<string, string> = { PAGADA: 'Pagada', PENDIENTE: 'Pendiente', VENCIDA: 'Vencida' };
const TONE: Record<string, string> = { PAGADA: 'green', PENDIENTE: 'grey', VENCIDA: 'gum' };

/** Pagos en cuotas de un plan de tratamiento (con recordatorio 3 días antes del vencimiento). */
@Component({
  selector: 'app-installments',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="inst">
      <div class="spread">
        <h4>Pago en cuotas</h4>
        @if (data && data.items.length) { <span class="muted small tabular">Pagado {{ money(data.paid) }} de {{ money(data.total) }}</span> }
      </div>
      @if (data && data.items.length) {
        <ol class="list">
          @for (i of data.items; track i.id) {
            <li>
              <span class="n">Cuota {{ i.number }}</span>
              <span class="tabular muted">{{ date(i.dueDate) }}</span>
              <span class="tabular strong">{{ money(i.amount) }}</span>
              <span class="tag" [class]="'tag ' + tone(i.status)">{{ label(i.status) }}</span>
            </li>
          }
        </ol>
      } @else if (data) {
        <p class="muted small">Sin cuotas. Divide el total del plan para que el paciente pague por partes; se le recuerda cada vencimiento.</p>
      }
      @if (canEdit) {
        <form class="row form" (ngSubmit)="create()">
          <label class="small" for="c{{ planId }}">Cuotas</label>
          <input id="c{{ planId }}" class="input sm" type="number" min="2" max="24" name="count" [(ngModel)]="count" />
          <label class="small" for="d{{ planId }}">Primera</label>
          <input id="d{{ planId }}" class="input sm" type="date" name="first" [(ngModel)]="first" [min]="today" />
          <button class="btn sm" type="submit" [disabled]="busy">{{ data?.items?.length ? 'Rehacer cuotas' : 'Crear cuotas' }}</button>
        </form>
      }
    </div>
  `,
  styles: [`
    .inst { padding: 14px 18px; border-top: 1px solid var(--line); display: flex; flex-direction: column; gap: 10px; }
    h4 { font-size: 14px; }
    .list { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 6px; }
    .list li { display: grid; grid-template-columns: auto 1fr auto auto; gap: 8px; align-items: center; padding: 6px 10px; border-radius: 10px; background: var(--surface-2); font-size: 13px; }
    .n { font-weight: 700; }
    .form { gap: 8px; flex-wrap: wrap; align-items: center; }
    .input.sm { height: 32px; width: auto; font-size: 13px; }
    input[type=number].sm { width: 70px; }
  `],
})
export class InstallmentsComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  @Input() planId!: number;
  @Input() canEdit = false;
  data: Installments | null = null;
  count = 3;
  today = isoDate();
  first = isoDate(new Date(Date.now() + 7 * 864e5));
  busy = false;

  ngOnInit() { this.api.installments(this.planId).subscribe((d) => (this.data = d)); }

  create() {
    if (this.data?.items.length && !confirm('Se reemplazarán las cuotas actuales. ¿Continuar?')) return;
    this.busy = true;
    this.api.createInstallments(this.planId, +this.count, this.first).subscribe({
      next: (d) => { this.busy = false; this.data = d; this.toast.ok(`${d.items.length} cuotas creadas.`); },
      error: (e) => { this.busy = false; this.toast.error(apiError(e).message); },
    });
  }

  money = soles;
  date = shortDate;
  label(s: string) { return LABEL[s] || s; }
  tone(s: string) { return TONE[s] || 'grey'; }
}
