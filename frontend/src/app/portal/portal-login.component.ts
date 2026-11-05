import { Component, inject } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { PortalAuth } from '../core/portal-auth.service';
import { apiError } from '../core/format';
import { IconComponent } from '../shared/icon.component';
import { LogoComponent } from '../shared/logo.component';

type Mode = 'login' | 'activar' | 'codigo';

/** HU-22: ingreso del paciente y activación de su cuenta con un código enviado por WhatsApp. */
@Component({
  selector: 'app-portal-login',
  standalone: true,
  imports: [FormsModule, NgTemplateOutlet, RouterLink, IconComponent, LogoComponent],
  template: `
    <div class="wrap">
      <div class="hero">
        <svg class="wave" viewBox="0 0 400 140" preserveAspectRatio="none" aria-hidden="true">
          <path d="M0 0H400V70C300 120 120 40 0 110Z" fill="#00B0E0"/><path d="M0 128C120 60 300 140 400 84" fill="none" stroke="#F89818" stroke-width="10"/>
        </svg>
        <div class="badge"><app-logo [size]="72" /></div>
        <h1>Clínica Dental Cárdenas</h1>
        <p class="slogan">Que tu <span>Sonrisa</span> sea para toda la vida</p>
      </div>

      <section class="card">
        @if (mode === 'login') {
          <h2>Ingresa a tu app</h2>
          <p class="muted small">Consulta tus citas, confírmalas y revisa tu cuenta desde el celular.</p>
          <form class="stack" (ngSubmit)="login()" novalidate>
            <ng-container *ngTemplateOutlet="doc"></ng-container>
            <div class="field">
              <label for="pp">Contraseña</label>
              <input id="pp" name="pp" class="input lg" type="password" [(ngModel)]="password" autocomplete="current-password" />
            </div>
            @if (error) { <div class="notice gum small" role="alert"><app-icon name="alert" [size]="16" /><div>{{ error }}</div></div> }
            <button class="btn primary lg block" type="submit" [disabled]="busy || !docNumber || !password">@if (busy) { <span class="spin"></span> } Ingresar</button>
          </form>
          <button type="button" class="link" (click)="go('activar')">¿Primera vez u olvidaste tu contraseña? <strong>Activa tu cuenta</strong></button>
        }

        @if (mode === 'activar') {
          <h2>Activa tu cuenta</h2>
          <p class="muted small">Te enviaremos un código de 6 dígitos al WhatsApp que registraste en la clínica.</p>
          <form class="stack" (ngSubmit)="requestCode()" novalidate>
            <ng-container *ngTemplateOutlet="doc"></ng-container>
            <div class="field">
              <label for="bd">Fecha de nacimiento</label>
              <input id="bd" name="bd" class="input lg" type="date" [(ngModel)]="birthDate" />
            </div>
            @if (error) { <div class="notice gum small" role="alert"><app-icon name="alert" [size]="16" /><div>{{ error }}</div></div> }
            <button class="btn primary lg block" type="submit" [disabled]="busy || !docNumber || !birthDate">@if (busy) { <span class="spin"></span> } Enviarme el código</button>
          </form>
          <button type="button" class="link" (click)="go('login')">Ya tengo contraseña · <strong>Ingresar</strong></button>
        }

        @if (mode === 'codigo') {
          <h2>Crea tu contraseña</h2>
          <div class="notice teal small"><app-icon name="message" [size]="16" /><div>{{ info }}@if (demoCode) { <div class="demo">Modo demostración · código: <strong>{{ demoCode }}</strong></div> }</div></div>
          <form class="stack" (ngSubmit)="activate()" novalidate>
            <div class="field">
              <label for="cd">Código recibido</label>
              <input id="cd" name="cd" class="input lg code" inputmode="numeric" maxlength="6" [(ngModel)]="code" autocomplete="one-time-code" />
              @if (fe['code']) { <span class="err">{{ fe['code'] }}</span> }
            </div>
            <div class="field">
              <label for="np">Nueva contraseña</label>
              <input id="np" name="np" class="input lg" type="password" [(ngModel)]="password" autocomplete="new-password" />
              @if (fe['password']) { <span class="err">{{ fe['password'] }}</span> } @else { <span class="hint">Mínimo 8 caracteres, con letras y números.</span> }
            </div>
            @if (error) { <div class="notice gum small" role="alert"><app-icon name="alert" [size]="16" /><div>{{ error }}</div></div> }
            <button class="btn primary lg block" type="submit" [disabled]="busy || code.length < 6 || !password">@if (busy) { <span class="spin"></span> } Activar y entrar</button>
          </form>
          <button type="button" class="link" (click)="go('activar')">¿No te llegó? <strong>Pedir otro código</strong></button>
        }
      </section>

      <p class="staff"><a routerLink="/login">Soy personal de la clínica</a></p>
    </div>

    <ng-template #doc>
      <div class="docrow">
        <div class="field">
          <label for="dt">Documento</label>
          <select id="dt" name="dt" class="input lg" [(ngModel)]="docType">
            <option value="DNI">DNI</option><option value="CE">C. E.</option><option value="PAS">Pasaporte</option>
          </select>
        </div>
        <div class="field">
          <label for="dn">Número</label>
          <input id="dn" name="dn" class="input lg tabular" [(ngModel)]="docNumber" inputmode="numeric" autocomplete="username" />
        </div>
      </div>
    </ng-template>
  `,
  styles: [`
    :host { display: block; min-height: 100vh; background: #e9f3f8; }
    .wrap { max-width: 480px; margin: 0 auto; min-height: 100vh; background: var(--porcelain); padding-bottom: 30px; }
    .hero { position: relative; text-align: center; padding: 46px 20px 18px; }
    .wave { position: absolute; left: 0; top: 0; width: 100%; height: 140px; }
    .badge { position: relative; margin: 0 auto 10px; width: 104px; height: 104px; border-radius: 50%; background: #fff; display: grid; place-items: center; box-shadow: 0 10px 30px -10px rgba(0, 120, 170, .4); }
    h1 { position: relative; font-family: var(--font-display); font-size: 22px; font-weight: 900; margin: 0; }
    .slogan { margin: 4px 0 0; font-family: var(--font-display); font-weight: 800; color: #00a3d4; }
    .slogan span { font-family: var(--font-script); color: var(--orange); font-size: 24px; }
    .card { margin: 6px 16px 0; background: #fff; border: 1px solid var(--line); border-radius: 20px; padding: 22px 20px; display: flex; flex-direction: column; gap: 12px; }
    .card h2 { font-size: 20px; margin: 0; }
    .docrow { display: grid; grid-template-columns: 110px 1fr; gap: 10px; }
    .input.lg { height: 48px; font-size: 16px; }
    .code { letter-spacing: .4em; text-align: center; font-size: 22px; font-weight: 800; }
    .link { background: none; border: 0; color: var(--teal); font: inherit; font-size: 14px; cursor: pointer; padding: 6px 0; }
    .demo { margin-top: 4px; }
    .staff { text-align: center; font-size: 13px; margin-top: 18px; }
  `],
})
export class PortalLoginComponent {
  private portal = inject(PortalAuth);
  private router = inject(Router);

  mode: Mode = 'login';
  docType = 'DNI';
  docNumber = '';
  password = '';
  birthDate = '';
  code = '';
  info = '';
  demoCode = '';
  error = '';
  fe: Record<string, string> = {};
  busy = false;

  go(m: Mode) { this.mode = m; this.error = ''; this.fe = {}; this.password = ''; }

  login() {
    this.busy = true;
    this.error = '';
    this.portal.login(this.docType, this.docNumber.trim(), this.password).subscribe({
      next: () => this.router.navigateByUrl('/app'),
      error: (e) => {
        this.busy = false;
        const a = apiError(e);
        this.error = a.status === 0 ? 'Sin conexión con la clínica.' : a.message;
        if (a.code === 'CUENTA_NO_ACTIVADA') setTimeout(() => this.go('activar'), 1800);
      },
    });
  }

  requestCode() {
    this.busy = true;
    this.error = '';
    this.portal.requestCode(this.docType, this.docNumber.trim(), this.birthDate).subscribe({
      next: (r: any) => {
        this.busy = false;
        this.info = r.sentTo ? `Enviamos el código por WhatsApp al ${r.sentTo}. Vence en 15 minutos.` : r.message;
        this.demoCode = r.demoCode || '';
        this.code = '';
        this.password = '';
        this.mode = 'codigo';
      },
      error: (e) => { this.busy = false; this.error = apiError(e).message; },
    });
  }

  activate() {
    this.busy = true;
    this.error = '';
    this.fe = {};
    this.portal.activate(this.docType, this.docNumber.trim(), this.code.trim(), this.password).subscribe({
      next: () => this.router.navigateByUrl('/app'),
      error: (e) => { this.busy = false; const a = apiError(e); this.fe = a.fields || {}; if (!a.fields) this.error = a.message; },
    });
  }
}
