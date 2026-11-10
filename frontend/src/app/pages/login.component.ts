import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { apiError } from '../core/format';
import { IconComponent } from '../shared/icon.component';
import { LogoComponent } from '../shared/logo.component';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent, LogoComponent],
  template: `
    <div class="wrap">
      <section class="art" aria-label="Clínica Dental Cárdenas">
        <!-- ondas de la marca: celeste y naranja -->
        <svg class="waves" viewBox="0 0 800 900" preserveAspectRatio="none" aria-hidden="true">
          <path d="M0 0 H800 V40 C 600 150, 300 60, 0 170 Z" fill="#00B0E0"/>
          <path d="M0 214 C 300 100, 600 196, 800 82" fill="none" stroke="#F89818" stroke-width="20"/>
          <path d="M0 900 V820 C 260 720, 560 860, 800 700 V900 Z" fill="#00B0E0"/>
          <path d="M0 780 C 260 676, 560 812, 800 652" fill="none" stroke="#F89818" stroke-width="14"/>
        </svg>
        <span class="dot d1" aria-hidden="true"></span>
        <span class="dot d2" aria-hidden="true"></span>

        <div class="badge"><app-logo [size]="104" /></div>

        <div class="slogan">
          <span class="l1">Que tu</span>
          <span class="l2">Sonrisa</span>
          <span class="l3">sea para</span>
          <span class="l4">toda la vida</span>
        </div>

        <p class="who">
          <strong>Clínica Dental Cárdenas</strong><br>
          La Floresta, Chaclacayo · Odontología integral
        </p>
      </section>

      <section class="formside">
        <form class="card" (ngSubmit)="submit()" novalidate>
          <div class="mobile-brand"><app-logo [size]="52" /><span>Clínica Dental Cárdenas</span></div>
          <h2>Iniciar sesión</h2>
          <p class="muted">Ingresa con la cuenta que te asignó la administración de la clínica.</p>

          @if (error) {
            <div class="notice" [class.gum]="!locked" [class.amber]="locked" role="alert">
              <app-icon [name]="locked ? 'lock' : 'alert'" />
              <div>
                <div>{{ error }}</div>
                @if (locked) { <a routerLink="/recuperar" class="small">Recuperar contraseña</a> }
              </div>
            </div>
          }

          <div class="field">
            <label for="u">Usuario</label>
            <input id="u" name="username" class="input" [class.invalid]="touched && !username" [(ngModel)]="username"
                   autocomplete="username" autocapitalize="none" spellcheck="false" autofocus />
            @if (touched && !username) { <span class="err">Ingresa tu usuario.</span> }
          </div>

          <div class="field">
            <div class="spread"><label for="p">Contraseña</label><a routerLink="/recuperar" class="small">¿Olvidaste tu contraseña?</a></div>
            <div class="pw">
              <input id="p" name="password" class="input" [class.invalid]="touched && !password" [(ngModel)]="password"
                     [type]="show ? 'text' : 'password'" autocomplete="current-password" />
              <button type="button" class="eye" (click)="show = !show" [attr.aria-label]="show ? 'Ocultar contraseña' : 'Mostrar contraseña'">
                <app-icon [name]="show ? 'eyeOff' : 'eye'" />
              </button>
            </div>
            @if (touched && !password) { <span class="err">Ingresa tu contraseña.</span> }
          </div>

          @if (needCode) {
            <div class="field">
              <label for="c">Código de verificación</label>
              <input id="c" name="code" class="input code" [(ngModel)]="code" inputmode="numeric" maxlength="6"
                     autocomplete="one-time-code" placeholder="000000" />
              <span class="muted small">Abre tu app de autenticación (Google Authenticator, Microsoft Authenticator…) y escribe el código de 6 dígitos.</span>
            </div>
          }
          <button class="btn primary lg block" type="submit" [disabled]="loading">
            @if (loading) { <span class="spin"></span> Verificando… } @else { Ingresar }
          </button>

          <details class="demo">
            <summary>Cuentas de prueba</summary>
            <p class="small muted">Contraseña para todas: <strong>Clinica2026</strong></p>
            <div class="accounts">
              @for (a of demo; track a.u) {
                <button type="button" class="acc" (click)="fill(a.u)"><span class="strong">{{ a.u }}</span><span class="muted">{{ a.r }}</span></button>
              }
            </div>
          </details>
          <a class="patient-app" routerLink="/app/ingresar"><app-icon name="smartphone" [size]="18" /> <span><strong>¿Eres paciente?</strong> Revisa tus citas en la app</span></a>
        </form>
      </section>
    </div>
  `,
  styles: [`
    .wrap { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr); min-height: 100vh; background: var(--surface); }
    .art { position: relative; overflow: hidden; background: #f4fbfe; display: flex; flex-direction: column; justify-content: center; padding: 150px 64px 140px; }
    .dot { position: absolute; border-radius: 50%; }
    .d1 { width: 46px; height: 46px; right: 9%; top: 34%; background: #F89818; opacity: .9; }
    .d2 { width: 70px; height: 70px; left: -24px; top: 58%; background: #00B0E0; opacity: .2; }
    .waves { position: absolute; inset: 0; width: 100%; height: 100%; }
    .badge { position: relative; width: 132px; height: 132px; border-radius: 50%; background: #fff; display: grid; place-items: center;
      box-shadow: 0 10px 40px -10px rgba(0, 120, 170, .35); margin-bottom: 22px; }
    .slogan { position: relative; display: flex; flex-direction: column; align-items: flex-start; font-family: var(--font-display); line-height: 1; }
    .l1 { font-size: clamp(2rem, 3.4vw, 2.9rem); font-weight: 900; color: #00a3d4; }
    .l2 { font-family: var(--font-script); font-size: clamp(3rem, 5.2vw, 4.4rem); font-weight: 700; color: var(--orange); margin: -4px 0 -2px 6px; }
    .l3 { font-size: clamp(2rem, 3.4vw, 2.9rem); font-weight: 900; color: var(--orange); }
    .l4 { margin-top: 12px; font-size: clamp(1.8rem, 3.1vw, 2.6rem); font-weight: 900; color: #fff; background: #00b0e0; padding: 6px 22px 10px; border-radius: 999px; }
    .who { position: relative; margin-top: 26px; color: var(--ink-2); font-size: 15px; }
    .who strong { font-family: var(--font-display); font-size: 18px; color: var(--ink); }
    .formside { display: grid; place-items: center; padding: 32px 20px; }
    .card { width: min(400px, 100%); display: flex; flex-direction: column; gap: 18px; }
    .card h2 { font-size: var(--step-3); }
    .mobile-brand { display: none; align-items: center; gap: 12px; font-family: var(--font-display); font-weight: 900; font-size: 20px; }
    .pw { position: relative; }
    .code { font-size: 22px; letter-spacing: .4em; text-align: center; font-variant-numeric: tabular-nums; }
    .pw .input { padding-right: 44px; }
    .eye { position: absolute; right: 4px; top: 4px; height: 32px; width: 36px; border: 0; background: none; color: var(--muted); cursor: pointer; border-radius: 6px; }
    .eye:hover { color: var(--ink); background: var(--surface-2); }
    .demo { border-top: 1px solid var(--line); padding-top: 14px; font-size: var(--step--1); }
    .demo summary { cursor: pointer; color: var(--ink-2); font-weight: 700; }
    .demo p { margin: 8px 0; }
    .accounts { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
    .acc { display: flex; flex-direction: column; align-items: flex-start; padding: 8px 12px; border: 1px solid var(--line); border-radius: 10px; background: var(--surface); font: inherit; font-size: 12.5px; cursor: pointer; text-align: left; }
    .patient-app { display: flex; align-items: center; gap: 10px; padding: 12px 14px; border-radius: 12px; background: var(--orange-soft); color: var(--orange-ink); font-size: 13.5px; }
    .patient-app:hover { text-decoration: none; filter: brightness(.98); }
    .acc:hover { border-color: var(--brand); background: var(--brand-soft); }
    @media (max-width: 900px) {
      .wrap { grid-template-columns: 1fr; }
      .art { display: none; }
      .mobile-brand { display: flex; }
    }
  `],
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);

  username = '';
  password = '';
  show = false;
  loading = false;
  touched = false;
  error = '';
  locked = false;
  needCode = false;
  code = '';

  demo = [
    { u: 'admin', r: 'Administrador' }, { u: 'recepcion', r: 'Recepcionista' },
    { u: 'dra.cardenas', r: 'Odontóloga' }, { u: 'dr.ramos', r: 'Odontólogo' },
    { u: 'caja', r: 'Caja' }, { u: 'director', r: 'Director' },
  ];

  fill(u: string) {
    this.username = u;
    this.password = 'Clinica2026';
  }

  submit() {
    this.touched = true;
    this.error = '';
    if (!this.username || !this.password) return;
    this.loading = true;
    this.auth.login(this.username.trim(), this.password, this.needCode ? this.code.trim() : undefined).subscribe({
      next: () => this.router.navigateByUrl(this.auth.home()),
      error: (e) => {
        const err = apiError(e);
        this.loading = false;
        this.locked = err.code === 'CUENTA_BLOQUEADA';
        if (err.code === 'REQUIERE_2FA') { this.needCode = true; this.error = ''; return; }
        if (err.code === 'CODIGO_2FA_INVALIDO') { this.code = ''; this.error = err.message; return; }
        this.error = err.status === 0 ? 'No hay conexión con el servidor.' : err.message;
        this.password = '';
        this.needCode = false;
        this.code = '';
      },
    });
  }
}
