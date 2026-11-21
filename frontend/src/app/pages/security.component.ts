import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
// @ts-ignore: la librería se instala al compilar; su tipado no es necesario aquí
import qrcode from 'qrcode-generator';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ToastService } from '../core/toast.service';
import { apiError } from '../core/format';
import { IconComponent } from '../shared/icon.component';

/** Verificación en 2 pasos (TOTP) para la cuenta del personal. */
@Component({
  selector: 'app-security',
  standalone: true,
  imports: [FormsModule, IconComponent],
  template: `
    <div class="page">
      <header class="page-head">
        <h1>Mi seguridad</h1>
        <p class="muted">Además de tu contraseña, al ingresar te pediremos un código de 6 dígitos. Elige cómo recibirlo: <strong>por correo</strong> (no necesitas instalar nada) o con una <strong>app de autenticación</strong> en el celular (funciona sin internet).</p>
      </header>

      <section class="panel box">
        <div class="state">
          <app-icon name="shield" [size]="28" />
          <div>
            <h3>Verificación en 2 pasos</h3>
            @if (enabled) { <p><span class="tag green">Activada · {{ method === 'CORREO' ? 'código por correo' : 'app de autenticación' }}</span> Al iniciar sesión te pediremos el código {{ method === 'CORREO' ? 'que llega a tu correo' : 'de tu app' }}.</p> }
            @else { <p><span class="tag amber">Desactivada</span> Cualquiera con tu contraseña podría entrar a las historias clínicas.</p> }
          </div>
        </div>

        @if (!enabled && !setup && !emailSetup) {
          <div class="choices">
            <button class="choice" type="button" (click)="startEmail()" [disabled]="busy">
              <app-icon name="mail" [size]="22" /><strong>Código por correo</strong>
              <span class="muted small">Recomendado si no quieres instalar nada. Llega a {{ auth.user()?.email }}.</span></button>
            <button class="choice" type="button" (click)="start()" [disabled]="busy">
              <app-icon name="smartphone" [size]="22" /><strong>App de autenticación</strong>
              <span class="muted small">Google o Microsoft Authenticator. Más segura: no depende del correo.</span></button>
          </div>
        }

        @if (emailSetup) {
          <p>Enviamos un código a <strong>{{ emailSetup.email }}</strong>. Escríbelo para confirmar que el correo es tuyo:</p>
          @if (emailSetup.demoCode) { <p class="muted small">Modo demostración: el código es {{ emailSetup.demoCode }}</p> }
          <div class="row confirm">
            <input class="input code" [(ngModel)]="code" inputmode="numeric" maxlength="6" placeholder="000000" aria-label="Código del correo" />
            <button class="btn primary" type="button" (click)="enableEmail()" [disabled]="busy || code.length !== 6">Confirmar y activar</button>
            <button class="btn ghost" type="button" (click)="emailSetup = null; code = ''">Cancelar</button>
          </div>
        }

        @if (setup) {
          <ol class="steps">
            <li>Instala <strong>Google Authenticator</strong> o <strong>Microsoft Authenticator</strong> en tu celular.</li>
            <li>En la app, toca <em>Agregar</em> y escanea este código:
              <div class="qr" [innerHTML]="qr"></div>
              <span class="muted small">¿No puedes escanear? Escribe esta clave: <code class="key">{{ pretty(setup.secret) }}</code></span>
            </li>
            <li>Escribe el código de 6 dígitos que muestra la app:
              <div class="row confirm">
                <input class="input code" [(ngModel)]="code" inputmode="numeric" maxlength="6" placeholder="000000" aria-label="Código de 6 dígitos" />
                <button class="btn primary" type="button" (click)="enable()" [disabled]="busy || code.length !== 6">Confirmar y activar</button>
              </div>
            </li>
          </ol>
        }

        @if (enabled) {
          <div class="row confirm">
            <input class="input code" [(ngModel)]="code" inputmode="numeric" maxlength="6" placeholder="000000" aria-label="Código actual para desactivar" />
            <button class="btn danger" type="button" (click)="disable()" [disabled]="busy || code.length !== 6">Desactivar</button>
          </div>
          @if (method === 'CORREO') { <button class="btn sm ghost" type="button" (click)="sendDisableCode()" [disabled]="busy">Enviarme un código para desactivar</button> }
          <p class="muted small">Para desactivarla escribe el código {{ method === 'CORREO' ? 'que te enviamos al correo' : 'actual de tu app' }}. Si perdiste el acceso, pide al administrador que restablezca tu cuenta.</p>
        }
      </section>
    </div>
  `,
  styles: [`
    .page { display: flex; flex-direction: column; gap: 16px; max-width: 720px; }
    .box { padding: 22px; display: flex; flex-direction: column; gap: 18px; align-items: flex-start; }
    .state { display: flex; gap: 14px; align-items: flex-start; color: var(--teal-strong); }
    .state h3 { color: var(--ink); margin-bottom: 4px; }
    .state p { color: var(--ink-2); display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin: 0; }
    .steps { margin: 0; padding-left: 20px; display: flex; flex-direction: column; gap: 16px; }
    .qr { margin: 10px 0 6px; width: 200px; height: 200px; padding: 8px; background: #fff; border: 1px solid var(--line); border-radius: 12px; }
    .qr :is(svg, img) { width: 100%; height: 100%; display: block; }
    .key { font-size: 13px; letter-spacing: .08em; background: var(--surface-2); padding: 2px 6px; border-radius: 6px; word-break: break-all; }
    .confirm { gap: 10px; margin-top: 8px; flex-wrap: wrap; }
    .choices { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; width: 100%; }
    .choice { display: flex; flex-direction: column; gap: 6px; align-items: flex-start; text-align: left; padding: 16px; border: 1px solid var(--line); border-radius: 14px; background: var(--surface); cursor: pointer; font: inherit; color: var(--ink); }
    .choice:hover { border-color: var(--teal); }
    @media (max-width: 640px) { .choices { grid-template-columns: 1fr; } }
    .code { width: 150px; font-size: 20px; letter-spacing: .35em; text-align: center; font-variant-numeric: tabular-nums; }
  `],
})
export class SecurityComponent {
  private api = inject(Api);
  private toast = inject(ToastService);
  private sanitizer = inject(DomSanitizer);
  auth = inject(AuthService);
  setup: { secret: string; otpauthUrl: string } | null = null;
  qr: SafeHtml = '';
  code = '';
  busy = false;

  get enabled() { return !!(this.auth.user() as any)?.twoFactor; }
  get method() { return (this.auth.user() as any)?.twoFactorMethod || 'APP'; }
  emailSetup: any = null;

  startEmail() {
    this.busy = true;
    this.api.start2faEmail().subscribe({
      next: (r) => { this.busy = false; this.emailSetup = r; this.code = ''; },
      error: (e) => { this.busy = false; this.toast.error(apiError(e).message); },
    });
  }
  sendDisableCode() {
    this.busy = true;
    this.api.start2faEmail().subscribe({
      next: (r) => { this.busy = false; this.toast.ok(r.message + (r.demoCode ? ' (demo: ' + r.demoCode + ')' : '')); },
      error: (e) => { this.busy = false; this.toast.error(apiError(e).message); },
    });
  }
  enableEmail() {
    this.busy = true;
    this.api.enable2faEmail(this.code).subscribe({
      next: (r) => { this.busy = false; this.emailSetup = null; this.code = ''; this.toast.ok(r.message); this.auth.refresh(); },
      error: (e) => { this.busy = false; this.toast.error(apiError(e).message); },
    });
  }

  start() {
    this.busy = true;
    this.api.totpSetup().subscribe({
      next: (s) => {
        this.busy = false;
        this.setup = s;
        const q = (qrcode as any)(0, 'M');
        q.addData(s.otpauthUrl);
        q.make();
        this.qr = this.sanitizer.bypassSecurityTrustHtml(q.createSvgTag({ cellSize: 4, margin: 2, scalable: true }));
      },
      error: (e) => { this.busy = false; this.toast.error(apiError(e).message); },
    });
  }

  enable() {
    this.busy = true;
    this.api.totpEnable(this.code).subscribe({
      next: () => { this.busy = false; this.setup = null; this.code = ''; this.toast.ok('Verificación en 2 pasos activada.'); this.auth.refresh(); },
      error: (e) => { this.busy = false; this.toast.error(apiError(e).message); },
    });
  }

  disable() {
    this.busy = true;
    this.api.totpDisable(this.code).subscribe({
      next: () => { this.busy = false; this.code = ''; this.toast.ok('Verificación en 2 pasos desactivada.'); this.auth.refresh(); },
      error: (e) => { this.busy = false; this.toast.error(apiError(e).message); },
    });
  }

  pretty(s: string) { return s.replace(/(.{4})/g, '$1 ').trim(); }
}
