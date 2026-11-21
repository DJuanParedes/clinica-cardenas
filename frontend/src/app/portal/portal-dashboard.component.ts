import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api, PortalAppointment } from '../core/api.service';
import { PortalAuth } from '../core/portal-auth.service';
import { hhmm, longDay } from '../core/format';
import { IconComponent } from '../shared/icon.component';

/** Resumen de acciones del paciente; no expone datos de otras historias ni pacientes. */
@Component({
  selector: 'app-portal-dashboard', standalone: true, imports: [RouterLink, IconComponent],
  template: `
    @if (!loaded) { <div class="skeleton" style="height:260px;border-radius:20px"></div> } @else {
      @if (next) {
        <section class="next"><span>PRÓXIMA CITA</span><strong>{{ longDay(next.startAt) }}</strong><b>{{ hhmm(next.startAt) }}</b>
          <p>{{ next.reason }} · {{ next.dentist.fullName }}</p>
          <div class="actions"><a class="btn accent" [routerLink]="['/app/citas']">Ver o gestionar</a><a class="btn light" [href]="mapUrl" target="_blank" rel="noopener">Cómo llegar</a></div>
        </section>
      } @else { <section class="empty"><app-icon name="calendar" [size]="34" /><h2>¿Qué necesitas hoy?</h2><p class="muted">Agenda tu próxima atención en pocos pasos.</p><a class="btn primary" routerLink="/app/agendar">Agendar cita</a></section> }

      @for (notice of notices; track notice.id) { <section class="notice"><app-icon name="alert" [size]="18" /><div><strong>{{ notice.title || 'Aviso de la clínica' }}</strong><p>{{ notice.body }}</p></div></section> }

      <h3>Accesos directos</h3><div class="quick">
        <a routerLink="/app/agendar"><app-icon name="calendar" [size]="23" /><span>Agendar cita</span></a>
        <a routerLink="/app/historia"><app-icon name="clipboard" [size]="23" /><span>Tratamientos y documentos</span></a>
        <a routerLink="/app/promociones"><app-icon name="tag" [size]="23" /><span>Promociones</span></a>
        <a routerLink="/app/cuenta"><app-icon name="wallet" [size]="23" /><span>Pagos y comprobantes</span></a>
      </div>
    }
  `,
  styles: [`
    .next{background:linear-gradient(145deg,#0679ac,#00b0e0);color:#fff;border-radius:20px;padding:20px;box-shadow:0 12px 28px -14px #0679ac}.next span{font-size:11px;font-weight:900;letter-spacing:.08em}.next strong,.next b{display:block}.next strong{font-size:20px;margin-top:5px}.next b{font-size:35px;line-height:1.1}.next p{margin:7px 0 14px}.actions{display:flex;gap:8px}.light{background:#fff;color:var(--teal-strong);border-color:#fff}.empty{text-align:center;background:#fff;border:1px solid var(--line);border-radius:20px;padding:25px}.empty h2{margin:8px 0 4px}.notice{display:flex;gap:10px;align-items:flex-start;background:#fff4df;border-left:4px solid var(--orange);border-radius:12px;padding:12px;margin-top:12px}.notice p{margin:3px 0 0;font-size:13px}.notice app-icon{color:#a85a00;flex:none}h3{font-size:15px;margin:20px 4px 9px}.quick{display:grid;grid-template-columns:1fr 1fr;gap:10px}.quick a{min-height:104px;background:#fff;border:1px solid var(--line);border-radius:16px;padding:14px;display:flex;flex-direction:column;gap:9px;color:var(--teal-strong);font-weight:800;font-size:13px}.quick a:hover{text-decoration:none;background:var(--brand-soft)}.quick span{color:var(--ink)}
  `],
})
export class PortalDashboardComponent implements OnInit {
  private api = inject(Api); private portal = inject(PortalAuth);
  loaded = false; next: PortalAppointment | null = null; notices: any[] = [];
  get mapUrl() { return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(this.portal.patient()?.clinic?.address || 'Clínica Dental Cárdenas')}`; }
  ngOnInit() { this.api.portalAppointments().subscribe({ next: r => { this.next = r.next; this.loaded = true; }, error: () => this.loaded = true }); this.api.portalNotices().subscribe({ next: r => this.notices = r.items.filter(n => !n.readAt).slice(0, 2), error: () => {} }); }
  hhmm = hhmm; longDay = longDay;
}

