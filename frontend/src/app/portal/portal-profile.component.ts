import { Component, inject } from '@angular/core';
import { Api } from '../core/api.service';
import { PortalAuth } from '../core/portal-auth.service';
import { ToastService } from '../core/toast.service';
import { apiError } from '../core/format';
import { IconComponent } from '../shared/icon.component';

/** HU-22 / HU-23: datos del paciente y canal preferido para recordatorios. */
@Component({
  selector: 'app-portal-profile',
  standalone: true,
  imports: [IconComponent],
  template: `
    @if (p(); as me) {
      <section class="card">
        <div class="strong big">{{ me.fullName }}</div>
        <div class="muted small">{{ me.docType }} {{ me.docNumber }}</div>
        <div class="kv2"><app-icon name="phone" [size]="15" /> {{ me.phone }}</div>
        <div class="kv2"><app-icon name="mail" [size]="15" /> {{ me.email || 'Sin correo registrado' }}</div>
      </section>

      <h3>¿Cómo quieres recibir tus recordatorios?</h3>
      <section class="card opts">
        @for (o of options; track o.key) {
          <button type="button" class="opt" [class.on]="me.notifyChannel === o.key" (click)="set(o.key)" [disabled]="busy || (o.needsEmail && !me.email)">
            <app-icon [name]="o.icon" [size]="20" />
            <span class="grow"><span class="strong">{{ o.label }}</span><span class="muted small block">{{ o.needsEmail && !me.email ? 'Pide a recepción registrar tu correo' : o.hint }}</span></span>
            @if (me.notifyChannel === o.key) { <app-icon name="check" [size]="18" /> }
          </button>
        }
      </section>

      <h3>Clínica</h3>
      <section class="card">
        <div class="strong">{{ me.clinic.name }}</div>
        <div class="muted small">{{ me.clinic.address }}</div>
        <div class="row wrap acts">
          <a class="btn" [href]="'tel:' + digits(me.clinic.phone)"><app-icon name="phone" [size]="16" /> Llamar</a>
          <a class="btn" [href]="'https://wa.me/51' + digits(me.clinic.phone)" target="_blank" rel="noopener"><app-icon name="message" [size]="16" /> WhatsApp</a>
        </div>
      </section>

      <button class="btn block out" type="button" (click)="portal.logout()"><app-icon name="logout" [size]="16" /> Cerrar sesión</button>
      <p class="muted small foot">Para instalar la app, abre el menú del navegador y elige "Agregar a la pantalla de inicio".</p>
    }
  `,
  styles: [`
    .card { background: #fff; border: 1px solid var(--line); border-radius: 18px; padding: 16px 18px; display: flex; flex-direction: column; gap: 6px; }
    .big { font-size: 18px; font-family: var(--font-display); }
    .kv2 { display: flex; align-items: center; gap: 8px; font-size: 14px; color: var(--ink-2); }
    h3 { font-size: 15px; margin: 18px 4px 8px; }
    .opts { padding: 6px; gap: 2px; }
    .opt { display: flex; align-items: center; gap: 12px; padding: 12px; border: 0; border-radius: 12px; background: none; font: inherit; text-align: left; cursor: pointer; color: var(--ink-2); }
    .opt.on { background: var(--brand-soft); color: var(--teal-strong); }
    .opt:disabled { opacity: .55; cursor: not-allowed; }
    .block { display: block; }
    .acts { margin-top: 8px; }
    .out { margin-top: 20px; height: 46px; color: var(--gum); }
    .foot { text-align: center; margin-top: 12px; }
  `],
})
export class PortalProfileComponent {
  private api = inject(Api);
  private toast = inject(ToastService);
  portal = inject(PortalAuth);
  p = this.portal.patient;
  busy = false;
  options = [
    { key: 'WHATSAPP', label: 'WhatsApp', hint: 'Recomendado', icon: 'message', needsEmail: false },
    { key: 'EMAIL', label: 'Correo electrónico', hint: 'A tu correo registrado', icon: 'mail', needsEmail: true },
    { key: 'AMBOS', label: 'WhatsApp y correo', hint: 'Por los dos canales', icon: 'bell', needsEmail: true },
    { key: 'NINGUNO', label: 'No recibir recordatorios', hint: 'Solo verás tus citas en la app', icon: 'x', needsEmail: false },
  ];

  set(ch: string) {
    this.busy = true;
    this.api.portalChannel(ch).subscribe({
      next: (p) => { this.busy = false; this.portal.setPatient(p); this.toast.ok('Preferencia guardada.'); },
      error: (e) => { this.busy = false; this.toast.error(apiError(e).message); },
    });
  }

  digits(s: string) { return (s || '').replace(/\D/g, ''); }
}
