import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { dateTime, shortDate } from '../core/format';

/** v2.8: pacientes en tratamiento de ortodoncia, con su último control y si están atrasados. */
@Component({
  selector: 'app-ortho-cases',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="page">
      <header class="page-head"><div><h1>Ortodoncia</h1><p class="muted">Pacientes en tratamiento o en retención. Los atrasados no tienen cita y ya pasó la fecha de su próximo control.</p></div>
        <select class="input auto" [(ngModel)]="dentistId" (ngModelChange)="load()" aria-label="Odontólogo"><option [ngValue]="null">Todos los odontólogos</option>@for (d of dentists; track d.id) { <option [ngValue]="d.id">{{ d.fullName }}</option> }</select></header>
      <div class="kpis"><div><b>{{ list.length }}</b><span>casos activos</span></div><div><b class="bad">{{ late() }}</b><span>con control atrasado</span></div><div><b>{{ retention() }}</b><span>en retención</span></div></div>
      <section class="panel">
        <table class="table">
          <thead><tr><th>Paciente</th><th>Aparato</th><th>Avance</th><th>Etapa</th><th>Último control</th><th>Próxima cita</th><th class="num">Fotos</th></tr></thead>
          <tbody>
            @for (c of list; track c.id) {
              <tr [class.late]="c.controlOverdue">
                <td><a [routerLink]="['/pacientes', c.patientId]" [queryParams]="{ tab: 'ortodoncia' }">{{ c.patientName }}</a><div class="muted small">{{ c.dentistName }}</div></td>
                <td class="small">{{ c.applianceLabel }}</td>
                <td class="small">{{ c.monthsElapsed }}{{ c.estimatedMonths ? '/' + c.estimatedMonths : '' }} meses
                  @if (c.progress != null) { <div class="bar"><span [style.width.%]="c.progress"></span></div> }</td>
                <td class="small">{{ c.currentStageLabel }}@if (c.status === 'RETENCION') { <span class="tag blue plain">Retención</span> }</td>
                <td class="small">{{ c.lastControl ? sd(c.lastControl) : '—' }}@if (c.controlOverdue) { <div class="bad">Atrasado desde {{ sd(c.dueControl) }}</div> }</td>
                <td class="small">{{ c.nextAppointment ? dt(c.nextAppointment) : '—' }}</td>
                <td class="num">{{ c.photos }}</td>
              </tr>
            } @empty { <tr><td colspan="7" class="empty">No hay pacientes en tratamiento de ortodoncia.</td></tr> }
          </tbody>
        </table>
      </section>
    </div>
  `,
  styles: [`
    .page-head { display: flex; justify-content: space-between; gap: 12px; align-items: flex-start; } .auto { width: auto; }
    .kpis { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-bottom: 14px; }
    .kpis div { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius); padding: 12px 14px; display: grid; }
    .kpis b { font-size: 24px; } .kpis span { color: var(--muted); font-size: 12.5px; }
    .bad { color: var(--gum); font-weight: 600; } tr.late td { background: #fff7f8; }
    .bar { height: 5px; width: 90px; background: var(--line); border-radius: 99px; overflow: hidden; margin-top: 3px; }
    .bar span { display: block; height: 100%; background: var(--teal); }
    .num { text-align: right; }
  `],
})
export class OrthoCasesComponent implements OnInit {
  private api = inject(Api);
  list: any[] = [];
  dentists: any[] = [];
  dentistId: number | null = null;
  ngOnInit() { this.api.dentists().subscribe((d) => (this.dentists = d)); this.load(); }
  load() { this.api.orthoCases(this.dentistId ?? undefined).subscribe((l) => (this.list = l)); }
  late() { return this.list.filter((c) => c.controlOverdue).length; }
  retention() { return this.list.filter((c) => c.status === 'RETENCION').length; }
  sd = shortDate; dt = dateTime;
}
