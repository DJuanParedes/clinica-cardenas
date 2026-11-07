import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api, NotificationItem } from '../core/api.service';
import { ToastService } from '../core/toast.service';
import { CHANNEL_LABEL, NOTIF_STATUS, NOTIF_TONE, NOTIF_TYPE, apiError, dateTime } from '../core/format';
import { IconComponent } from '../shared/icon.component';

/** HU-23 / HU-24: bitácora de recordatorios y avisos enviados a los pacientes. */
@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <h1>Notificaciones a pacientes</h1>
          <p class="sub">Recordatorios de cita y avisos de cambio enviados por WhatsApp y correo a través del microservicio de notificaciones.</p>
        </div>
        <div class="actions">
          <button class="btn" type="button" (click)="load()"><app-icon name="refresh" [size]="16" /> Actualizar</button>
          @if (panelUrl) { <a class="btn" [href]="panelUrl" target="_blank" rel="noopener"><app-icon name="external" [size]="16" /> Abrir bandeja de WhatsApp y correo</a> }
        </div>
      </div>

      <section class="counters">
        @for (k of statusKeys; track k) {
          <button type="button" class="panel cnt" [class.on]="status === k" (click)="toggleStatus(k)">
            <span class="v tabular">{{ counters[k] || 0 }}</span>
            <span class="tag" [class]="'tag ' + tone(k)">{{ stLabel(k) }}</span>
          </button>
        }
      </section>

      <section class="panel">
        <div class="panel-head">
          <h3>Bitácora</h3>
          <select class="input sel" [(ngModel)]="type" (ngModelChange)="load()" aria-label="Tipo">
            <option value="">Todos los tipos</option>
            @for (t of typeKeys; track t) { <option [value]="t">{{ typeLabel(t) }}</option> }
          </select>
        </div>
        <table class="table">
          <thead><tr><th>Creada</th><th>Tipo</th><th>Canal y destino</th><th>Estado</th><th>Programado / enviado</th><th></th></tr></thead>
          <tbody>
            @for (n of items; track n.id) {
              <tr class="click" (click)="toggleOpen(n.id)">
                <td class="tabular nowrap muted">{{ dt(n.createdAt) }}</td>
                <td>
                  <div class="strong">{{ typeLabel(n.type) }}{{ n.operation === 'ANULAR' ? ' · anulación' : '' }}</div>
                  @if (n.patientId) { <a class="small" [routerLink]="['/pacientes', n.patientId]" (click)="$event.stopPropagation()">Ver paciente</a> }
                </td>
                <td><app-icon [name]="n.channel === 'EMAIL' ? 'mail' : 'message'" [size]="14" /> {{ channel(n.channel) }}<div class="muted small">{{ n.destination }}</div></td>
                <td><span class="tag" [class]="'tag ' + tone(n.status)">{{ stLabel(n.status) }}</span>
                  @if (n.lastError && n.status !== 'ENVIADO') { <div class="muted small err">{{ n.lastError }}</div> }
                  @if (n.attempts > 1) { <div class="muted small">{{ n.attempts }} intentos</div> }</td>
                <td class="tabular small">
                  @if (n.scheduledFor) { <div>Programado: {{ dt(n.scheduledFor) }}</div> }
                  @if (n.sentAt) { <div class="ok">Enviado: {{ dt(n.sentAt) }}</div> }
                </td>
                <td class="right">
                  @if (n.status === 'FALLIDO' && n.operation !== 'ANULAR') {
                    <button class="btn sm" type="button" (click)="retry(n); $event.stopPropagation()"><app-icon name="send" [size]="14" /> Reenviar</button>
                  }
                </td>
              </tr>
              @if (open === n.id) {
                <tr class="body"><td colspan="6">
                  @if (n.subject) { <div class="strong small">{{ n.subject }}</div> }
                  <pre>{{ n.body }}</pre>
                  <div class="muted small key">Clave de idempotencia: {{ n.idempotencyKey }}</div>
                </td></tr>
              }
            } @empty { <tr><td colspan="6"><div class="empty small"><p>No hay notificaciones con ese filtro.</p></div></td></tr> }
          </tbody>
        </table>
        <div class="panel-foot small muted">
          Cada mensaje lleva una clave única: si se intenta enviar dos veces el mismo aviso, el microservicio lo detecta y no lo duplica.
          Si el servicio no responde, el sistema reintenta hasta 5 veces con espera creciente.
        </div>
      </section>
    </div>
  `,
  styles: [`
    .counters { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 10px; margin-bottom: 18px; }
    .cnt { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; padding: 12px 14px; cursor: pointer; font: inherit; text-align: left; color: inherit; }
    .cnt.on { outline: 2px solid var(--teal); }
    .cnt .v { font-size: var(--step-2); font-weight: 800; }
    .sel { width: 200px; height: 34px; font-size: 13px; }
    .nowrap { white-space: nowrap; }
    .ok { color: var(--green); }
    .err { max-width: 260px; }
    tr.body td { background: var(--surface-2); }
    pre { white-space: pre-wrap; font-family: inherit; font-size: 13.5px; margin: 6px 0; background: #e9f7e3; border-radius: 10px; padding: 10px 12px; max-width: 560px; }
    .key { font-family: ui-monospace, monospace; font-size: 11.5px; }
    @media (max-width: 900px) { .counters { grid-template-columns: repeat(3, 1fr); } }
  `],
})
export class NotificationsComponent implements OnInit, OnDestroy {
  private api = inject(Api);
  private toast = inject(ToastService);

  items: NotificationItem[] = [];
  counters: Record<string, number> = {};
  panelUrl = '';
  status = '';
  type = '';
  open: number | null = null;
  statusKeys = ['ENVIADO', 'PROGRAMADO', 'PENDIENTE', 'FALLIDO', 'DUPLICADO', 'ANULADO'];
  typeKeys = ['RECORDATORIO', 'CAMBIO_CITA', 'CANCELACION', 'CODIGO_ACCESO'];
  private timer: any;

  ngOnInit() {
    this.load();
    this.timer = setInterval(() => this.load(), 15000);
  }

  ngOnDestroy() { clearInterval(this.timer); }

  load() {
    this.api.notifications(this.status || undefined, this.type || undefined).subscribe((r) => {
      this.items = r.items;
      this.counters = r.counters;
      this.panelUrl = r.panelUrl;
    });
  }

  toggleStatus(k: string) {
    this.status = this.status === k ? '' : k;
    this.load();
  }

  toggleOpen(id: number) { this.open = this.open === id ? null : id; }

  retry(n: NotificationItem) {
    this.api.retryNotification(n.id).subscribe({
      next: () => { this.toast.ok('Reenvío en cola.'); setTimeout(() => this.load(), 1200); },
      error: (e) => this.toast.error(apiError(e).message),
    });
  }

  dt = dateTime;
  stLabel(s: string) { return NOTIF_STATUS[s] || s; }
  tone(s: string) { return NOTIF_TONE[s] || 'grey'; }
  typeLabel(t: string) { return NOTIF_TYPE[t] || t; }
  channel(c: string) { return CHANNEL_LABEL[c] || c; }
}
