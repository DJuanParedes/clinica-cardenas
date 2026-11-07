import { Component, OnInit, inject } from '@angular/core';
import { Api, PortalAppointment } from '../core/api.service';
import { PortalAuth } from '../core/portal-auth.service';
import { ToastService } from '../core/toast.service';
import { STATUS_LABEL, STATUS_TONE, apiError, hhmm, longDay, parseLocal, saveBlob, shortDate } from '../core/format';
import { IconComponent } from '../shared/icon.component';

/** HU-22: el paciente consulta sus próximas citas (fecha, hora y odontólogo) y confirma su asistencia. */
@Component({
  selector: 'app-portal-home',
  standalone: true,
  imports: [IconComponent],
  template: `
    @if (!loaded) {
      <div class="skeleton" style="height: 220px; border-radius: 20px"></div>
    } @else {
      @if (next) {
        <section class="next">
          <div class="lbl">Tu próxima cita</div>
          <div class="day">{{ longDay(next.startAt) }}</div>
          <div class="time tabular">{{ hhmm(next.startAt) }} <span>· {{ next.durationMin }} min</span></div>
          <div class="who"><span class="dot" [style.background]="next.dentist.color"></span>{{ next.dentist.fullName }} · {{ next.dentist.specialty }}</div>
          <div class="why">{{ next.reason }}</div>
          <div class="where"><app-icon name="home" [size]="14" /> {{ clinic?.address }}</div>
          <div class="st"><span class="tag" [class]="'tag ' + tone(next.status)">{{ label(next.status) }}</span></div>
          <div class="acts">
            @if (next.canConfirm) {
              <button class="btn accent lg" type="button" (click)="confirm(next)" [disabled]="busy"><app-icon name="check" [size]="18" /> Confirmar asistencia</button>
            }
            <button class="btn lg" type="button" (click)="ics(next)"><app-icon name="calendar" [size]="18" /> Agregar a mi calendario</button>
          </div>
          <a class="call" [href]="'https://wa.me/51' + phoneDigits" target="_blank" rel="noopener"><app-icon name="message" [size]="16" /> ¿No puedes asistir? Escríbenos por WhatsApp</a>
        </section>
      } @else {
        <section class="none">
          <app-icon name="calendar" [size]="34" />
          <h2>No tienes citas programadas</h2>
          <p class="muted">Para separar una cita escríbenos o llámanos.</p>
          <a class="btn primary lg" [href]="'https://wa.me/51' + phoneDigits" target="_blank" rel="noopener"><app-icon name="message" [size]="18" /> Pedir cita por WhatsApp</a>
        </section>
      }

      <div class="seg full">
        <button type="button" [class.on]="view === 'up'" (click)="view = 'up'">Próximas ({{ upcoming.length }})</button>
        <button type="button" [class.on]="view === 'past'" (click)="view = 'past'">Historial ({{ past.length }})</button>
      </div>

      <ul class="list">
        @for (a of (view === 'up' ? upcoming : past); track a.id) {
          <li>
            <div class="date"><span class="d tabular">{{ dayNum(a.startAt) }}</span><span class="m">{{ month(a.startAt) }}</span></div>
            <div class="grow">
              <div class="strong">{{ a.reason }}</div>
              <div class="muted small">{{ hhmm(a.startAt) }} · {{ a.dentist.fullName }}</div>
            </div>
            <span class="tag" [class]="'tag ' + tone(a.status)">{{ label(a.status) }}</span>
          </li>
        } @empty { <li class="muted small empty">{{ view === 'up' ? 'Sin otras citas programadas.' : 'Aún no tienes atenciones registradas.' }}</li> }
      </ul>
      <p class="muted small foot">Te enviaremos un recordatorio un día antes de cada cita y un aviso si tu cita cambia. Puedes elegir el canal en Perfil.</p>
    }
  `,
  styles: [`
    .next { background: linear-gradient(160deg, #00b0e0, #0679ac); color: #fff; border-radius: 22px; padding: 20px 20px 16px; box-shadow: 0 14px 30px -14px rgba(6, 121, 172, .7); position: relative; overflow: hidden; }
    .next::after { content: ""; position: absolute; right: -56px; top: -56px; width: 140px; height: 140px; border-radius: 50%; border: 14px solid rgba(248, 152, 24, .55); }
    .next > * { position: relative; z-index: 1; }
    .lbl { font-size: 12px; font-weight: 800; letter-spacing: .04em; opacity: .9; }
    .day { font-family: var(--font-display); font-weight: 900; font-size: 22px; margin-top: 4px; }
    .time { font-size: 40px; font-weight: 900; line-height: 1.05; }
    .time span { font-size: 15px; font-weight: 700; opacity: .85; }
    .who { display: flex; align-items: center; gap: 8px; font-weight: 700; margin-top: 8px; }
    .dot { width: 10px; height: 10px; border-radius: 50%; border: 2px solid #fff; }
    .why { opacity: .92; font-size: 14px; }
    .where { display: flex; gap: 6px; align-items: center; font-size: 13px; opacity: .9; margin-top: 6px; }
    .st { margin-top: 10px; }
    .st .tag { background: rgba(255, 255, 255, .92); }
    .acts { display: flex; flex-direction: column; gap: 8px; margin-top: 14px; position: relative; z-index: 1; }
    .acts .btn { width: 100%; justify-content: center; }
    .acts .btn:not(.accent) { background: rgba(255, 255, 255, .15); color: #fff; border-color: rgba(255, 255, 255, .5); }
    .call { display: flex; align-items: center; justify-content: center; gap: 6px; color: #fff; font-size: 13px; font-weight: 700; margin-top: 12px; }
    .none { background: #fff; border: 1px solid var(--line); border-radius: 20px; padding: 26px 20px; text-align: center; color: var(--teal); }
    .none h2 { font-size: 18px; color: var(--ink); margin: 8px 0 4px; }
    .seg.full { display: grid; grid-template-columns: 1fr 1fr; margin: 18px 0 10px; }
    .seg.full button { height: 40px; }
    .list { list-style: none; margin: 0; padding: 0; background: #fff; border: 1px solid var(--line); border-radius: 18px; overflow: hidden; }
    .list li { display: flex; align-items: center; gap: 12px; padding: 12px 14px; border-bottom: 1px solid var(--line); }
    .list li:last-child { border-bottom: 0; }
    .list .empty { justify-content: center; padding: 22px; }
    .date { width: 46px; flex: none; display: flex; flex-direction: column; align-items: center; background: var(--brand-soft); border-radius: 12px; padding: 5px 0; color: var(--teal-strong); }
    .date .d { font-size: 18px; font-weight: 900; line-height: 1; }
    .date .m { font-size: 11px; font-weight: 800; text-transform: uppercase; }
    .foot { margin-top: 14px; text-align: center; }
  `],
})
export class PortalHomeComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  portal = inject(PortalAuth);

  loaded = false;
  next: PortalAppointment | null = null;
  upcoming: PortalAppointment[] = [];
  past: PortalAppointment[] = [];
  view: 'up' | 'past' = 'up';
  busy = false;

  get clinic() { return this.portal.patient()?.clinic; }
  get phoneDigits() { return (this.clinic?.phone || '').replace(/\D/g, ''); }

  ngOnInit() { this.load(); }

  load() {
    this.api.portalAppointments().subscribe((r) => {
      this.loaded = true;
      this.next = r.next;
      this.upcoming = r.upcoming.filter((a) => a.id !== r.next?.id);
      this.past = r.past;
    });
  }

  confirm(a: PortalAppointment) {
    this.busy = true;
    this.api.portalConfirm(a.id).subscribe({
      next: () => { this.busy = false; this.toast.ok('¡Gracias! Confirmaste tu asistencia.'); this.load(); },
      error: (e) => { this.busy = false; this.toast.error(apiError(e).message); },
    });
  }

  /** Archivo .ics para agregar la cita al calendario del celular. */
  ics(a: PortalAppointment) {
    const f = (iso: string) => {
      const d = parseLocal(iso);
      const p = (n: number) => String(n).padStart(2, '0');
      return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}T${p(d.getHours())}${p(d.getMinutes())}00`;
    };
    const text = ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Clinica Dental Cardenas//Citas//ES', 'BEGIN:VEVENT',
      `UID:cita-${a.id}@clinicacardenas.pe`, `DTSTART;TZID=America/Lima:${f(a.startAt)}`, `DTEND;TZID=America/Lima:${f(a.endAt)}`,
      `SUMMARY:Cita dental · ${a.reason}`, `DESCRIPTION:${a.dentist.fullName} (${a.dentist.specialty})`,
      `LOCATION:${this.clinic?.address || 'Clínica Dental Cárdenas'}`,
      'BEGIN:VALARM', 'TRIGGER:-PT2H', 'ACTION:DISPLAY', 'DESCRIPTION:Cita en Clínica Dental Cárdenas', 'END:VALARM',
      'END:VEVENT', 'END:VCALENDAR'].join('\r\n');
    saveBlob(new Blob([text], { type: 'text/calendar;charset=utf-8' }), `cita-${shortDate(a.startAt).replace(/\s/g, '-')}.ics`);
  }

  hhmm = hhmm;
  longDay = longDay;
  dayNum(iso: string) { return parseLocal(iso).getDate(); }
  month(iso: string) { return parseLocal(iso).toLocaleDateString('es-PE', { month: 'short' }).replace('.', ''); }
  label(s: string) { return STATUS_LABEL[s] || s; }
  tone(s: string) { return STATUS_TONE[s] || 'grey'; }
}
