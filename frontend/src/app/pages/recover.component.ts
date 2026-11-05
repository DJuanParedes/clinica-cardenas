import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { ToastService } from '../core/toast.service';
import { apiError } from '../core/format';
import { IconComponent } from '../shared/icon.component';
import { LogoComponent } from '../shared/logo.component';

@Component({
  selector: 'app-recover',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent, LogoComponent],
  template: `
    <div class="wrap">
      <form class="card panel" (ngSubmit)="step === 1 ? request() : reset()" novalidate>
        <a routerLink="/login" class="back"><app-icon name="chevronLeft" [size]="16" /> Volver a iniciar sesión</a>
        <div class="rb"><app-logo [size]="44" /><span>Clínica Dental Cárdenas</span></div>
        <h2>Recuperar contraseña</h2>

        <ol class="steps" aria-label="Pasos">
          <li [class.on]="step === 1" [class.done]="step > 1">Solicitar código</li>
          <li [class.on]="step === 2">Crear nueva contraseña</li>
        </ol>

        @if (step === 1) {
          <p class="muted">Te enviaremos un código de 6 dígitos al correo registrado en tu cuenta.</p>
          <div class="field">
            <label for="l">Usuario o correo</label>
            <input id="l" name="login" class="input" [(ngModel)]="login" autocomplete="username" autofocus />
            @if (errors['login']) { <span class="err">{{ errors['login'] }}</span> }
          </div>
          <button class="btn primary lg block" type="submit" [disabled]="loading || !login.trim()">
            @if (loading) { <span class="spin"></span> } Enviar código
          </button>
        } @else {
          <div class="notice teal">
            <app-icon name="mail" />
            <div>
              {{ info }}
              @if (demoCode) { <div class="small">Modo demostración — código: <strong class="tabular">{{ demoCode }}</strong></div> }
            </div>
          </div>
          <div class="field">
            <label for="c">Código de 6 dígitos</label>
            <input id="c" name="code" class="input code" [class.invalid]="errors['code']" [(ngModel)]="code"
                   inputmode="numeric" maxlength="6" autocomplete="one-time-code" />
            @if (errors['code']) { <span class="err">{{ errors['code'] }}</span> }
          </div>
          <div class="field">
            <label for="n">Nueva contraseña</label>
            <input id="n" name="newPassword" type="password" class="input" [class.invalid]="errors['newPassword']" [(ngModel)]="newPassword" autocomplete="new-password" />
            <span class="hint">Mínimo 8 caracteres, con al menos una letra y un número.</span>
            @if (errors['newPassword']) { <span class="err">{{ errors['newPassword'] }}</span> }
          </div>
          <div class="field">
            <label for="r">Repite la contraseña</label>
            <input id="r" name="repeat" type="password" class="input" [class.invalid]="errors['repeat']" [(ngModel)]="repeat" autocomplete="new-password" />
            @if (errors['repeat']) { <span class="err">{{ errors['repeat'] }}</span> }
          </div>
          <button class="btn primary lg block" type="submit" [disabled]="loading">
            @if (loading) { <span class="spin"></span> } Guardar contraseña
          </button>
          <button type="button" class="btn ghost block" (click)="step = 1">Pedir otro código</button>
        }
      </form>
    </div>
  `,
  styles: [`
    .wrap { min-height: 100vh; display: grid; place-items: center; padding: 24px 16px; background: linear-gradient(180deg, #f4fbfe, var(--porcelain)); }
    .rb { display: flex; align-items: center; gap: 10px; font-family: var(--font-display); font-weight: 900; font-size: 17px; }
    .card { width: min(440px, 100%); padding: 28px; display: flex; flex-direction: column; gap: 16px; }
    .steps { display: flex; gap: 8px; list-style: none; padding: 0; margin: 0; counter-reset: s; }
    .steps li { flex: 1; font-size: 12.5px; font-weight: 600; color: var(--muted); padding-top: 8px; border-top: 3px solid var(--line); counter-increment: s; }
    .steps li::before { content: counter(s) ". "; }
    .steps li.on { color: var(--teal-strong); border-color: var(--teal); }
    .steps li.done { border-color: #8cc9c0; }
    .code { font-size: 22px; letter-spacing: .3em; height: 48px; font-variant-numeric: tabular-nums; }
  `],
})
export class RecoverComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
  private toast = inject(ToastService);

  step = 1;
  login = '';
  code = '';
  newPassword = '';
  repeat = '';
  info = '';
  demoCode = '';
  loading = false;
  errors: Record<string, string> = {};

  request() {
    this.errors = {};
    this.loading = true;
    this.auth.recover(this.login.trim()).subscribe({
      next: (r) => {
        this.loading = false;
        this.info = r.maskedEmail ? `Enviamos el código a ${r.maskedEmail}. Vence en 15 minutos.` : r.message;
        this.demoCode = r.demoCode || '';
        this.step = 2;
      },
      error: (e) => { this.loading = false; this.errors = apiError(e).fields || { login: apiError(e).message }; },
    });
  }

  reset() {
    this.errors = {};
    if (!/^\d{6}$/.test(this.code.trim())) this.errors['code'] = 'El código tiene 6 dígitos.';
    if (this.newPassword.length < 8 || !/[A-Za-zÁÉÍÓÚáéíóúñÑ]/.test(this.newPassword) || !/\d/.test(this.newPassword)) {
      this.errors['newPassword'] = 'Mínimo 8 caracteres, con al menos una letra y un número.';
    }
    if (this.repeat !== this.newPassword) this.errors['repeat'] = 'Las contraseñas no coinciden.';
    if (Object.keys(this.errors).length) return;
    this.loading = true;
    this.auth.reset(this.login.trim(), this.code.trim(), this.newPassword).subscribe({
      next: (r) => { this.toast.ok(r.message); this.router.navigate(['/login']); },
      error: (e) => {
        this.loading = false;
        const err = apiError(e);
        this.errors = err.fields || { code: err.message };
      },
    });
  }
}
