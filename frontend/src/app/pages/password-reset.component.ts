import { backendPending } from '../core/deployment';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { ClinicConfigService } from '../core/clinic-config.service';

@Component({
  selector:'app-password-reset', standalone:true, imports:[FormsModule,RouterLink],
  template:`
  <div class="reset-page">
    <section class="reset-card">
      <a routerLink="/login" class="back">← Volver al inicio de sesión</a>
      <div class="brand"><img src="brand/platform-logo.svg" alt="Plataforma multiclínica"><div><b>Portal odontológico</b><small>Recuperación general de acceso</small></div></div>
      <div class="steps"><span [class.on]="step()>=1">1</span><i></i><span [class.on]="step()>=2">2</span><i></i><span [class.on]="step()>=3">3</span></div>
      @if(step()===1){
        <span class="eyebrow">Paso 1 de 3</span><h1>Recuperar contraseña</h1>
        <p>Ingresa el correo que el Administrador registró en tu cuenta. El código se enviará únicamente al medio de recuperación seguro configurado para esa cuenta.</p>
        @if(required()){<div class="required">Tu cuenta tiene una contraseña inicial temporal. Debes verificar tu identidad antes de ingresar por primera vez.</div>}
        <form (ngSubmit)="requestCode()">
          <label>Correo electrónico<input type="email" [(ngModel)]="email" name="email" required placeholder="usuario@correo.com"></label>
          @if(message()){<div class="message" [class.error]="error()">{{message()}}</div>}
          <button class="primary" [disabled]="backendPending || loading()">{{loading()?'Enviando...':'Enviar código de verificación'}}</button>
        </form>
      }
      @if(step()===2){
        <span class="eyebrow">Paso 2 de 3</span><h1>Verifica tu identidad</h1>
        <p>Si el correo está registrado y habilitado, recibirás un código por el medio de recuperación configurado (correo electrónico, SMS o WhatsApp). Escríbelo y crea una contraseña personal.</p>
        <form (ngSubmit)="confirmReset()">
          <label>Código de verificación<input inputmode="numeric" maxlength="10" [(ngModel)]="code" name="code" required placeholder="000000"></label>
          <label>Nueva contraseña<input type="password" [(ngModel)]="newPassword" name="newPassword" required minlength="10" placeholder="Mínimo 10 caracteres"></label>
          <label>Repite la contraseña<input type="password" [(ngModel)]="confirmPassword" name="confirmPassword" required minlength="10"></label>
          <div class="rules">Debe incluir mayúscula, minúscula, número y símbolo. No puede ser tu DNI.</div>
          @if(message()){<div class="message" [class.error]="error()">{{message()}}</div>}
          <button class="primary" [disabled]="backendPending || loading()">{{loading()?'Validando...':'Verificar código y cambiar contraseña'}}</button>
          <button type="button" class="link" (click)="requestCode()" [disabled]="backendPending || loading()">Reenviar código</button>
        </form>
      }
      @if(step()===3){
        <div class="success-icon">✓</div><span class="eyebrow">Paso 3 de 3</span><h1>Contraseña actualizada</h1>
        <p>Tu identidad fue verificada. Ya puedes ingresar con tu correo y la nueva contraseña.</p>
        <button class="primary" (click)="goLogin()">Ir a iniciar sesión</button>
      }
    </section>
  </div>`,
  styles:[`
    .reset-page{min-height:100vh;background:linear-gradient(135deg,#edf5f7,#f9fbfb);display:grid;place-items:center;padding:28px}.reset-card{width:min(520px,100%);background:#fff;border:1px solid #dfe9ea;border-radius:22px;padding:32px;box-shadow:0 28px 70px rgba(21,61,74,.12)}.back{font-size:10px;color:#52747c}.brand{display:flex;align-items:center;gap:11px;margin:23px 0}.brand img{width:44px;height:44px}.brand b,.brand small{display:block}.brand b{color:#214f68}.brand small{font-size:9px;color:#789092;margin-top:2px}.steps{display:flex;align-items:center;margin:18px 0 28px}.steps span{width:30px;height:30px;border-radius:50%;display:grid;place-items:center;background:#e7eeee;color:#829396;font-size:10px;font-weight:800}.steps span.on{background:#3B78A6;color:#fff}.steps i{height:2px;flex:1;background:#e2ebec}.eyebrow{text-transform:uppercase;letter-spacing:1.2px;color:#D06B4F;font-size:9px;font-weight:800}.reset-card h1{font-size:29px;margin:7px 0 10px;color:#173f43}.reset-card>p{color:#6d8487;line-height:1.65;font-size:11px}.required{background:#fff7e9;border:1px solid #f0dfb7;color:#765b25;padding:11px;border-radius:10px;font-size:10px;margin:15px 0}.reset-card form{display:grid;gap:14px;margin-top:20px}.reset-card label{font-size:10px;font-weight:700;color:#48696b}.reset-card input{display:block;width:100%;box-sizing:border-box;border:1px solid #d7e5e6;border-radius:10px;padding:12px;margin-top:6px;outline:none}.reset-card input:focus{border-color:#3B78A6;box-shadow:0 0 0 3px rgba(59,120,166,.10)}.primary{border:0;border-radius:10px;background:#3B78A6;color:#fff;padding:13px;font-weight:800;cursor:pointer}.primary:disabled{opacity:.55}.link{border:0;background:transparent;color:#3B78A6;font-weight:700;font-size:10px;cursor:pointer}.rules{font-size:9px;color:#6f8588;background:#f5f9f9;padding:9px;border-radius:8px}.message{padding:10px;border-radius:9px;background:#e8f7ee;color:#317052;font-size:10px}.message.error{background:#fff0f0;color:#a33}.success-icon{width:58px;height:58px;border-radius:50%;display:grid;place-items:center;background:#e5f7ed;color:#2d7b56;font-size:26px;font-weight:900;margin:12px 0 20px}
  `]
})
export class PasswordResetComponent implements OnInit{
  readonly backendPending = backendPending;
  private auth=inject(AuthService);private route=inject(ActivatedRoute);private router=inject(Router);private branding=inject(ClinicConfigService);
  readonly step=signal(1);readonly loading=signal(false);readonly message=signal('');readonly error=signal(false);readonly destination=signal<string|null>(null);readonly channel=signal<string|null>(null);readonly required=signal(false);
  email='';code='';newPassword='';confirmPassword='';
  ngOnInit(){this.branding.applyPlatformBranding();this.email=this.route.snapshot.queryParamMap.get('email')||'';this.required.set(this.route.snapshot.queryParamMap.get('required')==='1');}
  requestCode(){if(backendPending)return;if(!this.email.trim())return;this.loading.set(true);this.message.set('');this.error.set(false);this.auth.requestPasswordReset(this.email.trim()).subscribe({next:r=>{this.loading.set(false);this.destination.set(r.destination||null);this.channel.set(r.channel||null);this.message.set(r.message||'Código solicitado.');this.step.set(2);},error:e=>{this.loading.set(false);this.error.set(true);this.message.set(e?.error?.detail||e?.error?.message||'No se pudo enviar el código.');}});}
  confirmReset(){if(backendPending)return;if(this.newPassword!==this.confirmPassword){this.error.set(true);this.message.set('Las contraseñas no coinciden.');return;}if(!this.passwordShapeValid()){this.error.set(true);this.message.set('La contraseña debe tener 10 a 80 caracteres e incluir mayúscula, minúscula, número y símbolo.');return;}if(!this.code.trim()){this.error.set(true);this.message.set('Ingresa el código de verificación.');return;}this.loading.set(true);this.error.set(false);this.message.set('');this.auth.confirmPasswordReset(this.email.trim(),this.code.trim(),this.newPassword).subscribe({next:()=>{this.loading.set(false);this.step.set(3);},error:e=>{this.loading.set(false);this.error.set(true);this.message.set(e?.error?.detail||e?.error?.message||'No se pudo cambiar la contraseña.');}});}
  private passwordShapeValid(){const v=this.newPassword;return v.length>=10&&v.length<=80&&/[A-Z]/.test(v)&&/[a-z]/.test(v)&&/\d/.test(v)&&/[^A-Za-z0-9]/.test(v);}
  goLogin(){void this.router.navigate(['/login'],{queryParams:{email:this.email}});}
}

