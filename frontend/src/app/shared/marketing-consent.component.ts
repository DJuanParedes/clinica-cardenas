import { Component, Input, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { apiError, dateTime } from '../core/format';
import { ToastService } from '../core/toast.service';
import { LegalModalComponent } from './legal-modal.component';

/** v2.7: consentimiento aparte para recibir promociones (campañas y cumpleaños). */
@Component({
  selector: 'app-marketing-consent',
  standalone: true,
  imports: [FormsModule, LegalModalComponent],
  template: `
    <section class="panel">
      <div class="panel-head"><h3>Promociones y novedades</h3>
        <span class="tag plain" [class.green]="state === true" [class.gum]="state === false">{{ state === true ? 'Acepta' : state === false ? 'No acepta' : 'Sin preguntar' }}</span></div>
      <div class="panel-body small">
        <p class="muted">
          @if (state === true) { Recibe campañas y saludos de cumpleaños{{ at ? ' (desde el ' + dt(at) + ')' : '' }}. Puede darse de baja respondiendo BAJA. }
          @else if (state === false) { No se le envían campañas ni saludos{{ at ? ' (desde el ' + dt(at) + ')' : '' }}. Sus avisos de citas siguen llegando. }
          @else { Aún no se le preguntó. Recibe saludos de cumpleaños, pero no campañas. }
        </p>
        @if (auth.can('PACIENTES_EDITAR')) {
          <div class="row wrap">
            <select class="input sm" [(ngModel)]="channel" aria-label="Cómo lo indicó"><option value="PRESENCIAL">En la clínica</option><option value="TELEFONO">Por teléfono</option><option value="WHATSAPP">Por WhatsApp</option></select>
            @if (state !== true) { <button class="btn sm" type="button" (click)="legal = true">Registrar que acepta</button> }
            @if (state !== false) { <button class="btn sm ghost" type="button" (click)="set(false)">Registrar que no acepta</button> }
          </div>
        }
      </div>
    </section>
    <app-legal-modal [open]="legal" code="PUBLICIDAD" [who]="patient?.fullName || ''" (accepted)="legal = false; set(true)" (closed)="legal = false" />
  `,
  styles: [`.row { gap: 8px; align-items: center; } .input.sm { width: auto; height: 30px; padding: 0 8px; }`],
})
export class MarketingConsentComponent {
  @Input() set patient(p: any) { this._p = p; this.state = p?.marketingConsent ?? null; this.at = p?.marketingConsentAt ?? null; }
  get patient() { return this._p; }
  private _p: any;
  private api = inject(Api);
  private toast = inject(ToastService);
  auth = inject(AuthService);
  state: boolean | null = null;
  at: string | null = null;
  channel = 'PRESENCIAL';
  legal = false;
  set(accepted: boolean) {
    this.api.setMarketingConsent(this._p.id, accepted, this.channel).subscribe({
      next: (p) => { this.state = p.marketingConsent; this.at = p.marketingConsentAt; this.toast.ok(accepted ? 'Registrado: acepta recibir promociones.' : 'Registrado: no recibirá promociones.'); },
      error: (e) => this.toast.error(apiError(e).message),
    });
  }
  dt = dateTime;
}
