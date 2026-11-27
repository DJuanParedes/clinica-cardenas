import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ToastService } from '../core/toast.service';
import { apiError, dateTime, initials } from '../core/format';
import { IconComponent } from '../shared/icon.component';

import { OnlyDirective } from '../shared/only.directive';
/** Mi perfil: cada usuario actualiza sus datos y su contraseña (exige la actual). */
@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [OnlyDirective, FormsModule, RouterLink, IconComponent],
  template: `
    <div class="page narrow">
      <div class="page-head"><div><h1>Mi perfil</h1><p class="sub">Tus datos de contacto y tu contraseña. El puesto y los permisos los asigna administración.</p></div></div>
      @if (p) {
        <section class="panel hero">
          <span class="avatar xl">{{ ini(p.fullName) }}</span>
          <div><h2>{{ p.fullName }}</h2>
            <div class="muted">{{ p.username }} · {{ p.roleLabel }}@if (p.accessRoleLabel && p.accessRoleLabel !== p.roleLabel) { · rol {{ p.accessRoleLabel }} }</div>
            <div class="row wrap small" style="margin-top: 6px">
              @if (p.twoFactor) { <span class="tag green">Verificación en 2 pasos: {{ p.twoFactorMethod === 'CORREO' ? 'por correo' : 'con app' }}</span> }
              @else { <a class="tag amber" routerLink="/seguridad">Activa la verificación en 2 pasos</a> }
            </div>
          </div>
        </section>
        <div class="grid two">
          <section class="panel">
            <div class="panel-head"><h3>Datos personales</h3></div>
            <form class="panel-body stack" (ngSubmit)="save()">
              <div class="field"><label class="req" for="pn">Nombres y apellidos</label><input id="pn" class="input" name="pn" [(ngModel)]="f.fullName" maxlength="120" appOnly="letters" />
                @if (err['fullName']) { <span class="err">{{ err['fullName'] }}</span> }</div>
              <div class="field"><label class="req" for="pe">Correo</label><input id="pe" class="input" type="email" name="pe" [(ngModel)]="f.email" maxlength="120" appOnly="email" />
                <span class="hint">Aquí llegan los códigos para recuperar la contraseña o ingresar.</span>
                @if (err['email']) { <span class="err">{{ err['email'] }}</span> }</div>
              <div class="field"><label for="pt">Celular</label><input id="pt" class="input" name="pt" [(ngModel)]="f.phone" maxlength="9" inputmode="numeric" placeholder="9XXXXXXXX" appOnly="phone" />
                @if (err['phone']) { <span class="err">{{ err['phone'] }}</span> }</div>
              <button class="btn primary" type="submit" [disabled]="busy">Guardar cambios</button>
            </form>
          </section>
          <section class="panel">
            <div class="panel-head"><h3>Cambiar contraseña</h3></div>
            <form class="panel-body stack" (ngSubmit)="changePassword()">
              <div class="field"><label class="req" for="pc">Contraseña actual</label><input id="pc" class="input" type="password" name="pc" [(ngModel)]="pw.currentPassword" autocomplete="current-password" appOnly="text" maxlength="72" />
                @if (err['currentPassword']) { <span class="err">{{ err['currentPassword'] }}</span> }</div>
              <div class="field"><label class="req" for="pw1">Nueva contraseña</label><input id="pw1" class="input" type="password" name="pw1" [(ngModel)]="pw.newPassword" autocomplete="new-password" appOnly="nospace" maxlength="72" />
                <span class="hint">Mínimo 8 caracteres, con letras y números.</span>
                @if (err['newPassword']) { <span class="err">{{ err['newPassword'] }}</span> }</div>
              <div class="field"><label class="req" for="pw2">Repite la nueva contraseña</label><input id="pw2" class="input" type="password" name="pw2" [(ngModel)]="pw.repeat" autocomplete="new-password" appOnly="nospace" maxlength="72" />
                @if (pw.repeat && pw.repeat !== pw.newPassword) { <span class="err">No coincide.</span> }</div>
              <button class="btn primary" type="submit" [disabled]="busy || !pw.currentPassword || !pw.newPassword || pw.repeat !== pw.newPassword">Cambiar contraseña</button>
            </form>
          </section>
        </div>
      }
    </div>
  `,
  styles: [`
    .narrow { max-width: 980px; }
    .hero { display: flex; gap: 16px; align-items: center; padding: 18px; margin-bottom: 14px; }
    .hero h2 { margin: 0; }
    .avatar.xl { width: 64px; height: 64px; font-size: 22px; }
    .grid.two { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
    .err { color: var(--gum-ink, #b4234a); font-size: 12.5px; }
    .hint { color: var(--ink-3); font-size: 12.5px; }
    @media (max-width: 800px) { .grid.two { grid-template-columns: 1fr; } }
  `],
})
export class ProfileComponent implements OnInit {
  private api = inject(Api);
  private auth = inject(AuthService);
  private toast = inject(ToastService);
  p: any = null;
  f: any = {};
  pw: any = { currentPassword: '', newPassword: '', repeat: '' };
  err: Record<string, string> = {};
  busy = false;
  ini = initials;
  dt = dateTime;

  ngOnInit() { this.api.myProfile().subscribe((p) => this.set(p)); }
  private set(p: any) { this.p = p; this.f = { fullName: p.fullName, email: p.email, phone: p.phone || '' }; }
  save() {
    this.busy = true; this.err = {};
    this.api.updateMyProfile(this.f).subscribe({
      next: (p) => { this.busy = false; this.set(p); this.auth.refresh(); this.toast.ok('Datos actualizados.'); },
      error: (e) => { this.busy = false; const a = apiError(e); this.err = a.fields || {}; this.toast.error(a.message); },
    });
  }
  changePassword() {
    this.busy = true; this.err = {};
    this.api.changeMyPassword({ currentPassword: this.pw.currentPassword, newPassword: this.pw.newPassword }).subscribe({
      next: () => { this.busy = false; this.pw = { currentPassword: '', newPassword: '', repeat: '' }; this.toast.ok('Contraseña cambiada.'); },
      error: (e) => { this.busy = false; const a = apiError(e); this.err = a.fields || {}; this.toast.error(a.message); },
    });
  }
}
