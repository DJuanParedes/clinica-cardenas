import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { apiError, hhmm, longDay } from '../core/format';
import { ToastService } from '../core/toast.service';
import { IconComponent } from '../shared/icon.component';

/** Reserva autocontenida: el servidor vuelve a validar disponibilidad y reglas al confirmar. */
@Component({
  selector: 'app-portal-booking', standalone: true, imports: [FormsModule, RouterLink, IconComponent],
  template: `
    <div class="head"><a routerLink="/app">← Inicio</a><h2>{{ rescheduleId ? 'Reprogramar cita' : 'Agendar cita' }}</h2><p class="muted">Paso {{ step }} de 3</p></div>
    @if (!options) { <div class="skeleton" style="height:280px;border-radius:18px"></div> } @else {
      @if (step === 1) { <section class="card"><label>¿Qué necesitas?</label><select class="input" [(ngModel)]="reason" (ngModelChange)="resetSlots()"><option value="">Selecciona un motivo</option>@for (r of options.reasons;track r.code){<option [value]="r.code">{{r.label}} · {{r.durationMin}} min</option>}</select><label>Busca por odontólogo o motivo</label><input class="input" [(ngModel)]="search" placeholder="Ej.: ortodoncia o nombre" /><div class="doctors">@for(d of filtered;track d.id){<button type="button" [class.selected]="dentist?.id===d.id" (click)="selectDentist(d)">@if(d.photoUrl){<img [src]="d.photoUrl" [alt]="d.fullName"/>}<span><strong>{{d.fullName}}</strong><small>{{d.specialty}}</small></span><app-icon name="check" [size]="18" /></button>}@empty{<p class="muted">No encontramos un profesional con esa búsqueda.</p>}</div><button class="btn primary block" [disabled]="!dentist || !reason" (click)="step=2">Elegir fecha y hora</button></section> }
      @if (step === 2) { <section class="card"><button class="link" (click)="step=1">← Cambiar profesional o motivo</button><p><strong>{{ dentist.fullName }}</strong> · {{ selectedReason?.label }}</p><label>Fecha</label><input class="input" type="date" [(ngModel)]="date" [min]="minDate" [max]="maxDate" (ngModelChange)="loadSlots()"/>@if(loadingSlots){<div class="muted">Buscando horarios disponibles…</div>}@else if(date){<div class="slots">@for(s of slots;track s){<button type="button" [class.on]="startAt===s" (click)="startAt=s">{{ hhmm(s) }}</button>}@empty{<p class="muted">No hay horarios libres ese día. Elige otra fecha.</p>}</div>}<button class="btn primary block" [disabled]="!startAt" (click)="step=3">Revisar reserva</button></section> }
      @if (step === 3) { <section class="card"><button class="link" (click)="step=2">← Cambiar horario</button><h3>{{ rescheduleId ? 'Confirma el nuevo horario' : 'Confirma tu cita' }}</h3><p><strong>{{ selectedReason?.label }}</strong><br>{{ dentist.fullName }} · {{ dentist.specialty }}<br>{{ longDay(startAt) }} a las {{ hhmm(startAt) }}</p><label>Cuéntanos brevemente el motivo (opcional)</label><textarea class="input" maxlength="450" [(ngModel)]="notes"></textarea>@if(error){<p class="err">{{error}}</p>}<button class="btn primary block" [disabled]="busy" (click)="book()">{{ busy ? 'Guardando…' : (rescheduleId ? 'Cambiar a este horario' : 'Confirmar cita') }}</button></section> }
    }
  `,
  styles: [`
    .head a,.link{color:var(--teal-strong);font-size:13px;background:none;border:0;padding:0;cursor:pointer}.head h2{margin:8px 0 2px}.head p{margin:0 0 12px}.card{background:#fff;border:1px solid var(--line);border-radius:18px;padding:16px;display:flex;flex-direction:column;gap:10px}.doctors{display:grid;gap:8px}.doctors button{display:flex;align-items:center;gap:10px;border:1px solid var(--line);border-radius:13px;background:#fff;padding:10px;text-align:left;font:inherit}.doctors button.selected{border-color:var(--teal);background:var(--brand-soft)}.doctors img{width:40px;height:40px;border-radius:50%;object-fit:cover}.doctors span{display:grid;gap:2px;flex:1}.doctors small{color:var(--muted)}.doctors app-icon{opacity:0}.doctors .selected app-icon{opacity:1;color:var(--teal-strong)}.slots{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.slots button{padding:10px;border:1px solid var(--line);border-radius:10px;background:#fff;font:inherit;font-weight:700}.slots button.on{background:var(--teal);color:#fff;border-color:var(--teal)}textarea{min-height:78px;resize:vertical}.err{color:var(--gum);font-size:13px;margin:0}
  `],
})
export class PortalBookingComponent implements OnInit {
  private api = inject(Api); private router = inject(Router); private toast = inject(ToastService); private route = inject(ActivatedRoute);
  /** Si llega ?reprogramar=ID se mueve esa cita en vez de crear otra. */
  rescheduleId: number | null = null;
  options: any; step = 1; search = ''; reason = ''; dentist: any; date = ''; slots: string[] = []; startAt = ''; loadingSlots = false; notes = ''; busy = false; error = '';
  get minDate() { const d = new Date(); d.setDate(d.getDate() + 1); return d.toISOString().slice(0, 10); }
  get maxDate() { const d = new Date(); d.setDate(d.getDate() + 60); return d.toISOString().slice(0, 10); }
  get selectedReason() { return this.options?.reasons.find((r: any) => r.code === this.reason); }
  get filtered() { const q = this.search.trim().toLowerCase(); return this.options?.dentists.filter((d: any) => !q || `${d.fullName} ${d.specialty} ${this.selectedReason?.label || ''}`.toLowerCase().includes(q)) || []; }
  ngOnInit() {
    const q = this.route.snapshot.queryParamMap;
    this.rescheduleId = q.get('reprogramar') ? Number(q.get('reprogramar')) : null;
    const dentistId = Number(q.get('odontologo')) || null;
    this.api.portalBookingOptions().subscribe({
      next: r => { this.options = r; if (dentistId) this.dentist = r.dentists.find((d: any) => d.id === dentistId) || null; },
      error: e => this.error = apiError(e).message,
    });
  }
  selectDentist(d: any) { this.dentist = d; this.resetSlots(); }
  resetSlots() { this.slots = []; this.startAt = ''; }
  loadSlots() { this.resetSlots(); if (!this.date || !this.dentist || !this.reason) return; this.loadingSlots = true; this.api.portalBookingSlots(this.dentist.id, this.date, this.reason, this.rescheduleId).subscribe({ next: r => { this.slots = r.slots; this.loadingSlots = false; }, error: e => { this.error = apiError(e).message; this.loadingSlots = false; } }); }
  book() {
    this.busy = true; this.error = '';
    if (this.rescheduleId) {
      this.api.portalReschedule(this.rescheduleId, { dentistId: this.dentist.id, startAt: this.startAt }).subscribe({ next: r => { this.toast.ok(r.message); this.router.navigateByUrl('/app/citas'); }, error: e => { this.error = apiError(e).message; this.busy = false; } });
      return;
    }
    this.api.portalBook({ dentistId: this.dentist.id, startAt: this.startAt, reasonCode: this.reason, notes: this.notes }).subscribe({ next: r => { this.toast.ok(r.message); this.router.navigateByUrl('/app/citas'); }, error: e => { this.error = apiError(e).message; this.busy = false; } });
  }
  hhmm = hhmm; longDay = longDay;
}

