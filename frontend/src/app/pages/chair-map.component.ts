import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';

interface ChairState {
  chairId: number;
  chairName: string;
  branch: string;
  status: string;
  patient?: string | null;
  doctor?: string | null;
  nextAppointment?: string | null;
  availableAt?: string | null;
}
interface ChairEncounter {
  id: number;
  appointmentId: number;
  patientName: string;
  doctorName: string;
  chairName: string;
  specialty?: string;
  treatmentNames?: string;
  scheduledStart: string;
  scheduledEnd: string;
  status: string;
  paymentStatus?: string;
}

@Component({
  selector: 'app-chair-map',
  standalone: true,
  imports: [DatePipe, FormsModule],
  template: `
<section class="page-head">
  <div><span>Operación en tiempo real</span><h1>Mapa visual de sillones</h1><p>Selecciona un sillón para consultar sus citas y espacios disponibles en la fecha elegida.</p></div>
  <button class="primary-btn" (click)="load()">Actualizar</button>
</section>
<section class="day-toolbar panel-card">
  <button (click)="moveDay(-1)">‹ Día anterior</button>
  <label>Fecha<input type="date" [ngModel]="selectedDate()" (ngModelChange)="changeDate($event)"></label>
  <button (click)="today()">Hoy</button>
  <button (click)="moveDay(1)">Día siguiente ›</button>
</section>
@if(message()){<div class="message error">{{message()}}</div>}
<section class="grid">
  @for(c of chairs(); track c.chairId){
    <button type="button" class="chair-card panel-card" [class.selected]="selectedChair()===c.chairName" [attr.data-status]="c.status" (click)="selectChair(c)">
      <header><span>🪑</span><em>{{c.status}}</em></header>
      <h2>{{c.chairName}}</h2>
      <small class="branch">{{c.branch}}</small>
      @if(c.patient){<p><b>{{c.patient}}</b><small>{{c.doctor}}</small></p>}
      @else{<p><b>Sin paciente en atención</b><small>Selecciona para revisar sus citas</small></p>}
      <div class="count"><b>{{countForChair(c.chairName)}}</b><span>citas en la fecha</span></div>
      <footer><small>Próxima cita</small><b>{{c.nextAppointment?(c.nextAppointment|date:'dd/MM HH:mm'):'Sin cita próxima'}}</b></footer>
    </button>
  }
  @empty{<article class="panel-card"><h2>Sin sillones configurados</h2><p>Registra los sillones desde “Doctores y sillones”.</p></article>}
</section>
@if(selectedChair()){
<section class="panel-card schedule">
  <div class="schedule-head"><div><span>Agenda del sillón</span><h2>{{selectedChair()}} · {{selectedDate()|date:'dd/MM/yyyy':'UTC'}}</h2></div><b>{{selectedAppointments().length}} cita(s)</b></div>
  <div class="appointment-list">
    @for(a of selectedAppointments(); track a.id){
      <article>
        <time>{{a.scheduledStart|date:'HH:mm'}}–{{a.scheduledEnd|date:'HH:mm'}}</time>
        <div><b>{{a.patientName}}</b><small>{{a.doctorName}} · {{a.treatmentNames||a.specialty||'Atención odontológica'}}</small></div>
        <em [attr.data-status]="a.status">{{a.status}}</em>
      </article>
    }
    @empty{<div class="empty"><b>No tiene citas en esta fecha.</b><span>El sillón está disponible, salvo bloqueos o mantenimiento configurados.</span></div>}
  </div>
</section>}
`,
  styles: [`
.day-toolbar{display:flex;gap:10px;align-items:end;margin-bottom:14px}.day-toolbar label{font-size:9px;font-weight:700;color:#526f72}.day-toolbar input{display:block;margin-top:5px;padding:9px;border:1px solid #dce7e8;border-radius:8px}.day-toolbar button{padding:9px 12px;border:1px solid #d7e4e5;background:#fff;border-radius:8px}.message{padding:11px;border-radius:9px;margin-bottom:14px}.message.error{background:#fff0f0;color:#a94444}.grid{display:grid;grid-template-columns:repeat(4,1fr);gap:14px}.chair-card{text-align:left;width:100%;border-top:5px solid #3bbd83;cursor:pointer;transition:.15s}.chair-card:hover,.chair-card.selected{transform:translateY(-2px);box-shadow:0 12px 30px #163c3f1f}.chair-card.selected{outline:3px solid #3B78A633}.chair-card[data-status=ATENDIENDO]{border-top-color:#e3a62c}.chair-card[data-status=MANTENIMIENTO],.chair-card[data-status=FUERA_DE_SERVICIO],.chair-card[data-status=BLOQUEADO]{border-top-color:#c45858}.chair-card header{display:flex;justify-content:space-between;font-size:23px}.chair-card em{font-size:8px;padding:5px 8px;background:#edf4f4;border-radius:99px;font-style:normal}.chair-card h2{font-size:17px;margin-bottom:2px}.branch{font-size:8px;color:#789092}.chair-card p b,.chair-card p small,.chair-card footer b,.chair-card footer small{display:block}.chair-card p small,.chair-card footer small{font-size:8px;color:#789092;margin-top:4px}.count{display:flex;align-items:baseline;gap:5px;background:#f0f8f7;border-radius:8px;padding:8px;margin:12px 0}.count b{font-size:17px}.count span{font-size:8px;color:#6d8688}.chair-card footer{border-top:1px solid #e8efef;padding-top:12px;margin-top:10px}.schedule{margin-top:16px}.schedule-head{display:flex;justify-content:space-between;align-items:center}.schedule-head span{font-size:8px;color:#71898b;text-transform:uppercase}.schedule-head h2{margin:4px 0}.schedule-head>b{font-size:10px;background:#edf7f6;padding:7px 10px;border-radius:99px}.appointment-list article{display:grid;grid-template-columns:110px 1fr auto;gap:15px;align-items:center;padding:13px 0;border-bottom:1px solid #e8efef}.appointment-list time{font-weight:800;color:#173f42}.appointment-list div b,.appointment-list div small{display:block}.appointment-list div small{font-size:9px;color:#71898b;margin-top:4px}.appointment-list em{font-size:8px;font-style:normal;background:#edf4f4;border-radius:99px;padding:6px 9px}.appointment-list em[data-status=NO_ASISTIO],.appointment-list em[data-status=CANCELADA]{background:#fff0f0;color:#a94444}.empty{padding:24px;text-align:center;background:#f7fbfb;border-radius:10px}.empty b,.empty span{display:block}.empty span{font-size:9px;color:#71898b;margin-top:5px}@media(max-width:1000px){.grid{grid-template-columns:1fr 1fr}}@media(max-width:600px){.grid{grid-template-columns:1fr}.day-toolbar{flex-wrap:wrap}.appointment-list article{grid-template-columns:80px 1fr}.appointment-list em{grid-column:2}}
  `]
})
export class ChairMapComponent implements OnInit {
  private api = inject(ApiService);
  chairs = signal<ChairState[]>([]);
  encounters = signal<ChairEncounter[]>([]);
  selectedChair = signal('');
  selectedDate = signal(this.localDate(new Date()));
  message = signal('');

  selectedAppointments = computed(() => this.encounters()
    .filter(item => item.chairName === this.selectedChair() && String(item.scheduledStart).slice(0, 10) === this.selectedDate())
    .sort((a, b) => String(a.scheduledStart).localeCompare(String(b.scheduledStart))));

  ngOnInit() { this.load(); }

  load() {
    this.message.set('');
    this.api.get<ChairState[]>('/encounters/chairs/map').subscribe({
      next: values => {
        this.chairs.set(values);
        if (!this.selectedChair() && values.length) this.selectedChair.set(values[0].chairName);
      },
      error: e => this.message.set(e?.error?.message || 'No se pudo cargar el estado de los sillones.')
    });
    this.loadSchedule();
  }

  private loadSchedule() {
    this.api.get<ChairEncounter[]>(`/encounters/chairs/schedule?date=${encodeURIComponent(this.selectedDate())}`).subscribe({
      next: values => this.encounters.set(values),
      error: e => { this.encounters.set([]); this.message.set(e?.error?.message || 'No se pudieron cargar las citas vinculadas.'); }
    });
  }
  selectChair(chair: ChairState) { this.selectedChair.set(chair.chairName); }
  countForChair(name: string) { return this.encounters().filter(item => item.chairName === name).length; }
  changeDate(value: string) { this.selectedDate.set(value); this.loadSchedule(); }
  today() { this.selectedDate.set(this.localDate(new Date())); this.loadSchedule(); }
  moveDay(days: number) { const d = new Date(this.selectedDate() + 'T12:00:00'); d.setDate(d.getDate() + days); this.selectedDate.set(this.localDate(d)); this.loadSchedule(); }
  private localDate(d: Date) { return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; }
}
