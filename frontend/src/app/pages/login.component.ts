import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { ClinicConfigService } from '../core/clinic-config.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="login-page">
      <section class="login-visual">
        <a class="back" routerLink="/">← Volver al portal</a>
        <div class="visual-copy"><div class="logo"><img src="/brand/platform-logo.svg" alt="Plataforma multiclínica"></div><span>Portal odontológico</span><h1>Un acceso seguro para cada clínica.</h1><p>Ingresa con tu cuenta. La plataforma identificará automáticamente tu clínica, su marca, tus funciones y la información que puedes consultar.</p><div class="visual-points"><b>✓ Identidad propia después de ingresar</b><b>✓ Información separada por clínica</b><b>✓ Acceso según responsabilidades</b></div></div>
      </section>
      <section class="login-panel">
        <div class="login-card">
          <div class="mobile-brand"><img src="/brand/platform-logo.svg" alt="Plataforma multiclínica"> Portal odontológico</div>
          <span class="eyebrow">Acceso general seguro</span><h2>Bienvenido</h2><p>Al validar tus datos ingresarás directamente al espacio privado de tu clínica.</p>
          <form [formGroup]="form" (ngSubmit)="submit()">
            <label>Correo electrónico<input type="email" formControlName="email" placeholder="usuario@correo.com" autocomplete="username"></label>
            <label>Contraseña<div class="password"><input [type]="showPassword() ? 'text' : 'password'" formControlName="password" placeholder="••••••••" autocomplete="current-password"><button type="button" (click)="showPassword.set(!showPassword())">{{ showPassword() ? 'Ocultar' : 'Ver' }}</button></div></label>
            <div class="forgot"><a [routerLink]="['/recuperar-contrasena']" [queryParams]="{email:form.controls.email.value}">¿Olvidaste tu contraseña?</a></div>
            @if (error()) { <div class="error">{{ error() }}</div> }
            <button class="submit" [disabled]="form.invalid || loading()">{{ loading() ? 'Validando...' : 'Ingresar al sistema' }}</button>
          </form>
          <div class="access-note"><b>Primer ingreso</b><span>Si el Administrador acaba de crear tu cuenta, tu DNI queda registrado como clave temporal de seguridad, pero no abre una sesión. Presiona “¿Olvidaste tu contraseña?”, recibe el código por SMS o WhatsApp y crea tu contraseña personal antes de ingresar.</span></div>
        </div>
      </section>
    </div>
  `,
  styles: [`
    .login-page{min-height:100vh;display:grid;grid-template-columns:.92fr 1.08fr;background:linear-gradient(135deg,#f2f7f8,#fbfcfc)}.login-visual{position:relative;overflow:hidden;padding:42px 6vw;background:radial-gradient(circle at 86% 18%,rgba(77,205,194,.3),transparent 28%),linear-gradient(145deg,#173a59,#102e47);color:#fff;display:flex;align-items:center}.login-visual::after{content:'';position:absolute;width:520px;height:520px;border:1px solid rgba(255,255,255,.09);border-radius:50%;right:-220px;bottom:-170px;box-shadow:0 0 0 90px rgba(255,255,255,.025),0 0 0 180px rgba(255,255,255,.018)}.back{position:absolute;top:35px;left:45px;color:#d1e5e8;font-size:14px;font-weight:700}.visual-copy{position:relative;z-index:2;max-width:560px}.logo{width:78px;height:78px;border-radius:22px;background:#fff;display:grid;place-items:center;box-shadow:0 20px 45px rgba(0,0,0,.18);margin-bottom:22px}.logo img{width:70px;height:70px;object-fit:contain}.visual-copy>span{font-size:12px;letter-spacing:2px;color:#d5e8ee;font-weight:700;text-transform:uppercase}.visual-copy h1{font-size:clamp(40px,4.3vw,62px);letter-spacing:-2.4px;line-height:1.05;margin:18px 0}.visual-copy p{color:#e3eef1;line-height:1.75;max-width:520px;font-size:16px}.visual-points{display:flex;flex-direction:column;gap:13px;margin-top:34px;color:#f2f8f9;font-size:15px}.login-panel{display:grid;place-items:center;padding:48px}.login-card{width:min(520px,100%);background:#fff;border:1px solid #dce8e9;border-radius:26px;padding:42px;box-shadow:0 28px 70px rgba(28,72,77,.12)}.mobile-brand{display:none;align-items:center;gap:8px}.mobile-brand img{width:38px;height:38px;object-fit:contain}.eyebrow{color:var(--brand-blue);text-transform:uppercase;font-size:12px;letter-spacing:1.5px;font-weight:800}.login-card h2{font-size:42px;line-height:1.1;letter-spacing:-1.4px;margin:10px 0}.login-card>p{color:#607b7e;font-size:15px;line-height:1.6;margin:0 0 30px}.login-card form{display:grid;gap:20px}.login-card label{font-size:14px;font-weight:800;color:#385b5e}.login-card input{width:100%;height:54px;box-sizing:border-box;margin-top:8px;border:1px solid #cfdfe1;border-radius:12px;padding:14px 15px;background:#fff;outline:none;font-size:15px}.login-card input:focus{border-color:var(--brand-blue);box-shadow:0 0 0 4px color-mix(in srgb,var(--brand-blue) 16%,transparent)}.password{position:relative}.password input{padding-right:70px}.password button{position:absolute;right:7px;top:15px;min-height:40px;border:0;background:#edf5f6;border-radius:9px;padding:7px 11px;font-size:12px;font-weight:700;color:#486a6c;cursor:pointer}.submit{min-height:54px;border:0;border-radius:12px;background:var(--brand-orange);color:#fff;padding:15px;font-size:15px;font-weight:800;cursor:pointer;box-shadow:0 14px 30px color-mix(in srgb,var(--brand-orange) 24%,transparent)}.submit:disabled{opacity:.6;cursor:not-allowed}.error{padding:12px 14px;border-radius:10px;background:#fff0f0;color:#a33f3f;font-size:13px;border:1px solid #f4cccc}.forgot{text-align:right;margin-top:-7px}.forgot a{font-size:13px;color:var(--brand-blue);font-weight:800}.access-note{margin-top:25px;padding:16px 17px;border-radius:13px;background:#eef6f8;border:1px solid #d9e8ec}.access-note b,.access-note span{display:block}.access-note b{font-size:13px;color:#2d5c72}.access-note span{font-size:12px;color:#607b80;line-height:1.65;margin-top:5px}@media(max-width:1050px){.login-page{grid-template-columns:.82fr 1.18fr}.login-panel{padding:30px}.login-card{padding:34px}}@media(max-width:900px){.login-page{grid-template-columns:1fr}.login-visual{display:none}.login-panel{padding:22px;min-height:100vh}.login-card{padding:28px}.mobile-brand{display:flex;margin-bottom:28px;color:var(--brand-blue-dark);font-size:16px;font-weight:800}.login-card h2{font-size:36px}}
  `]
})
export class LoginComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly branding = inject(ClinicConfigService);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly showPassword = signal(false);
  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(10)]]
  });

  ngOnInit(): void {
    this.branding.applyPlatformBranding();
    const email = this.route.snapshot.queryParamMap.get('email');
    if (email) this.form.controls.email.setValue(email);
  }

  submit(): void {
    if (this.form.invalid) return;
    this.loading.set(true); this.error.set('');
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => void this.router.navigateByUrl(this.auth.homeForRole()),
      error: () => {
        this.loading.set(false);
        this.error.set('No se pudo iniciar sesión. Verifica tus datos. Si es tu primer acceso o no recuerdas la contraseña, usa “¿Olvidaste tu contraseña?”.');
      }
    });
  }
}
