import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../core/auth.service';
import { ClinicBrand, NavItem } from '../core/models';
import { ALL_NAV } from '../core/module-definitions';
import { ClinicConfigService } from '../core/clinic-config.service';
import { ApiService } from '../core/api.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule],
  template: `
    <div class="app-shell" [class.sidebar-open]="menuOpen()">
      <aside class="sidebar">
        <a class="brand" routerLink="/">
          <span class="brand-mark"><img [src]="clinic.logoUrl()" [alt]="'Logo ' + clinic.name()"></span>
          <span><b>{{ clinic.name() }}</b><small>{{ clinic.config().tagline }}</small></span>
        </a>
        <nav>
          @for (group of groups; track group.key) {
            @if (itemsFor(group.key).length) {
              <p class="nav-group">{{ groupLabel(group.key) }}</p>
              @for (item of itemsFor(group.key); track item.route) {
                <a [routerLink]="item.route" routerLinkActive="active" (click)="menuOpen.set(false)">
                  <span>{{ item.icon }}</span>{{ item.label }}
                </a>
              }
            }
          }
          @if (auth.user()?.role === 'RECEPCION') {
            <button class="more-tools" (click)="moreToolsOpen.set(!moreToolsOpen())">
              <span>{{ moreToolsOpen() ? '−' : '＋' }}</span>
              {{ moreToolsOpen() ? 'Ocultar herramientas adicionales' : 'Mostrar más herramientas' }}
            </button>
          }
        </nav>
        <div class="sidebar-footer">
          <div class="security-badge"><span>🔒</span><div><b>Sesión protegida</b><small>Acceso auditado por rol</small></div></div>
        </div>
      </aside>

      <section class="workspace">
        <header class="topbar">
          <button class="icon-button mobile-menu" (click)="menuOpen.set(!menuOpen())">☰</button>
          <div class="topbar-title">
            <small>{{ roleLabel() }}</small>
            <b>{{ auth.user()?.name }}</b>
          </div>
          @if(auth.isPlatformAdmin()) {
            <label class="clinic-switch"><span>Clínica activa</span><select [ngModel]="clinic.config().id" (ngModelChange)="switchClinic($event)" [disabled]="switching()">
              @for(item of clinics(); track item.id){ @if(item.active){<option [value]="item.code">{{item.shortName}}</option>} }
            </select></label>
          } @else {
            <div class="clinic-context"><small>Clínica</small><b>{{clinic.shortName()}}</b></div>
          }
          <div class="topbar-actions">
            <button class="icon-button" title="Notificaciones" (click)="notificationsOpen.set(!notificationsOpen())">🔔<span class="notification-dot"></span></button>
            <div class="avatar">{{ initials() }}</div>
            <button class="logout" (click)="passwordOpen.set(true)">Cambiar clave</button>
            <button class="logout" (click)="auth.logout()">Cerrar sesión</button>
          </div>
          @if (notificationsOpen()) {
            <section class="notification-panel">
              <div><b>Notificaciones</b><button (click)="notificationsOpen.set(false)">×</button></div>
              @for (item of notifications(); track item.route) {
                <a [routerLink]="item.route" (click)="notificationsOpen.set(false)"><span>{{ item.icon }}</span><div><b>{{ item.title }}</b><small>{{ item.text }}</small></div></a>
              }
            </section>
          }
        </header>
        <main class="content"><router-outlet /></main>
      </section>
      @if (menuOpen()) { <button class="overlay" (click)="menuOpen.set(false)" aria-label="Cerrar menú"></button> }
      @if (passwordOpen()) {
        <div class="password-backdrop" (click)="closePassword()"></div>
        <section class="password-modal">
          <div><span>Seguridad de la cuenta</span><h2>Cambiar contraseña</h2></div>
          @if(passwordMessage()){<p [class.error]="passwordError()">{{passwordMessage()}}</p>}
          <form (ngSubmit)="changePassword()">
            <label>Contraseña actual<input type="password" [(ngModel)]="currentPassword" name="currentPassword" required></label>
            <label>Nueva contraseña<input type="password" [(ngModel)]="newPassword" name="newPassword" minlength="10" required></label>
            <label>Repetir contraseña<input type="password" [(ngModel)]="confirmPassword" name="confirmPassword" minlength="10" required></label>
            <div><button type="button" class="logout" (click)="closePassword()">Cancelar</button><button class="primary-password" [disabled]="passwordSaving()">{{passwordSaving()?'Actualizando...':'Actualizar contraseña'}}</button></div>
          </form>
        </section>
      }
    </div>
  `,
  styles: [`
    .app-shell{min-height:100vh;background:#f4f7f8;display:grid;grid-template-columns:290px 1fr}.sidebar{background:linear-gradient(180deg,var(--clinic-sidebar-start) 0%,var(--clinic-sidebar-end) 100%);color:#fff;position:fixed;inset:0 auto 0 0;width:290px;display:flex;flex-direction:column;z-index:30;box-shadow:18px 0 45px rgba(6,39,43,.12)}.brand{height:90px;display:flex;align-items:center;gap:14px;padding:0 22px;color:#fff;border-bottom:1px solid rgba(255,255,255,.1)}.brand-mark{width:52px;height:52px;border-radius:15px;background:#fff;display:grid;place-items:center;box-shadow:0 8px 18px rgba(13,47,68,.2);overflow:hidden}.brand-mark img{width:47px;height:47px;display:block;object-fit:contain}.brand b{display:block;font-size:18px;line-height:1.2}.brand small{display:block;color:#d3e3ec;margin-top:4px;font-size:12px}nav{padding:16px 12px 30px;overflow:auto;flex:1}.nav-group{font-size:11px;text-transform:uppercase;letter-spacing:1.2px;color:#b8d1df;margin:21px 13px 8px;font-weight:800}nav a{display:flex;align-items:center;gap:12px;color:#edf5f8;padding:12px 13px;border-radius:11px;margin:4px 0;font-size:14px;font-weight:600;line-height:1.3;transition:.2s}nav a span{width:24px;text-align:center;font-size:16px}nav a:hover,nav a.active{background:rgba(255,255,255,.12);color:#fff}nav a.active{box-shadow:inset 4px 0 var(--brand-orange);font-weight:800}.more-tools{width:100%;min-height:46px;margin-top:17px;border:1px dashed rgba(255,255,255,.28);border-radius:11px;background:rgba(255,255,255,.05);color:#e3f0f4;font-size:12px;font-weight:800;text-align:left;padding:10px 13px}.more-tools span{margin-right:6px}.more-tools:hover{background:rgba(255,255,255,.1)}.sidebar-footer{padding:16px}.security-badge{display:flex;gap:11px;align-items:center;padding:13px;border:1px solid rgba(255,255,255,.11);border-radius:13px;background:rgba(255,255,255,.06)}.security-badge b,.security-badge small{display:block}.security-badge b{font-size:13px}.security-badge small{font-size:11px;color:#c5dbe6;margin-top:2px}.workspace{grid-column:2;min-width:0}.topbar{height:90px;background:#fff;border-bottom:1px solid #e1eaec;display:flex;align-items:center;justify-content:space-between;gap:18px;padding:0 32px;position:sticky;top:0;z-index:20}.topbar-title small,.clinic-context small{display:block;color:#607b7e;text-transform:uppercase;font-size:11px;letter-spacing:1px}.topbar-title b,.clinic-context b{font-size:17px;color:#173d41}.clinic-switch{display:flex;align-items:center;gap:10px;background:#eef5f6;border:1px solid #d7e4e5;border-radius:12px;padding:8px 10px}.clinic-switch span{font-size:10px;text-transform:uppercase;color:#678083;font-weight:800}.clinic-switch select{min-width:170px;border:0;background:#fff;border-radius:8px;padding:8px;color:#244f53;font-weight:800}.clinic-context{margin-left:auto}.topbar-actions{display:flex;align-items:center;gap:12px}.icon-button{border:0;background:#edf5f6;width:44px;height:44px;border-radius:12px;cursor:pointer;position:relative}.notification-dot{position:absolute;right:8px;top:7px;width:8px;height:8px;background:#e75858;border-radius:50%}.avatar{width:44px;height:44px;border-radius:13px;display:grid;place-items:center;background:linear-gradient(135deg,var(--brand-blue),var(--brand-orange));color:#fff;font-weight:800;font-size:13px}.logout{min-height:42px;border:1px solid #d3e2e4;background:#fff;border-radius:10px;padding:9px 13px;color:#385d61;font-size:12px;font-weight:700;cursor:pointer}.mobile-menu{display:none}.content{padding:32px;max-width:1600px;margin:auto}.notification-panel{position:absolute;right:28px;top:78px;width:370px;background:#fff;border:1px solid #dce8e9;border-radius:16px;box-shadow:0 24px 60px rgba(16,56,60,.18);padding:14px;z-index:50}.notification-panel>div{display:flex;justify-content:space-between;align-items:center;padding:5px 7px 11px}.notification-panel>div b{font-size:15px}.notification-panel>div button{border:0;background:#eef4f4;border-radius:8px;width:34px;height:34px;min-height:34px}.notification-panel a{display:flex;gap:12px;align-items:flex-start;color:#31585b;padding:13px;border-radius:11px}.notification-panel a:hover{background:#f1f9f8}.notification-panel a>span{font-size:22px}.notification-panel a b,.notification-panel a small{display:block}.notification-panel a b{font-size:13px}.notification-panel a small{font-size:11px;color:#70888a;margin-top:4px;line-height:1.5}.password-backdrop{position:fixed;inset:0;background:rgba(5,35,39,.45);z-index:80}.password-modal{position:fixed;z-index:81;left:50%;top:50%;transform:translate(-50%,-50%);width:min(470px,92vw);background:#fff;border-radius:20px;padding:30px;box-shadow:0 30px 80px rgba(4,35,39,.3)}.password-modal span{font-size:11px;text-transform:uppercase;color:var(--brand-blue);font-weight:800}.password-modal h2{font-size:25px;margin:6px 0 20px}.password-modal label{display:block;font-size:13px;font-weight:800;color:#48696b;margin:13px 0}.password-modal input{display:block;width:100%;box-sizing:border-box;border:1px solid #d3e2e4;border-radius:11px;padding:12px;margin-top:7px}.password-modal form>div{display:flex;justify-content:flex-end;gap:10px;margin-top:20px}.password-modal p{padding:12px;border-radius:9px;background:#e8f7ee;color:#2d7253;font-size:13px}.password-modal p.error{background:#fff0f0;color:#a33}.primary-password{min-height:44px;border:0;border-radius:10px;background:var(--brand-blue);color:#fff;padding:10px 15px;font-weight:800}.overlay{display:none}@media(max-width:1100px){.clinic-context{display:none}.clinic-switch span{display:none}.clinic-switch select{min-width:130px}}@media(max-width:980px){.app-shell{display:block}.sidebar{transform:translateX(-100%);transition:.25s}.sidebar-open .sidebar{transform:translateX(0)}.workspace{grid-column:auto}.mobile-menu{display:grid;place-items:center}.overlay{display:block;position:fixed;inset:0;background:rgba(0,0,0,.35);z-index:25;border:0}.content{padding:20px}.topbar{padding:0 16px;height:76px}.logout{display:none}.notification-panel{right:12px;top:68px;width:min(370px,calc(100vw - 24px))}}@media(max-width:650px){.clinic-switch{max-width:130px}.clinic-switch select{min-width:0;width:108px}.topbar-title{display:none}}@media(max-width:560px){.topbar-actions{gap:7px}.topbar-actions .icon-button{display:none}.avatar{width:40px;height:40px}.content{padding:16px}}
  `]
})
export class ShellComponent implements OnInit {
  readonly auth = inject(AuthService);
  readonly clinic = inject(ClinicConfigService);
  private readonly api = inject(ApiService);
  readonly clinics = signal<ClinicBrand[]>([]);
  readonly switching = signal(false);
  readonly menuOpen = signal(false);
  readonly notificationsOpen = signal(false);
  readonly moreToolsOpen = signal(false);
  readonly passwordOpen = signal(false);
  readonly passwordSaving = signal(false);
  readonly passwordMessage = signal('');
  readonly passwordError = signal(false);
  currentPassword = '';
  newPassword = '';
  confirmPassword = '';
  readonly groups = [
    { key: 'principal', label: 'Vista general' },
    { key: 'clinica', label: 'Atención clínica' },
    { key: 'operaciones', label: 'Operaciones' },
    { key: 'administracion', label: 'Administración' }
  ] as const;
  readonly nav = ALL_NAV;
  private readonly receptionAllowedRoutes = new Set([
    '/app/dashboard','/app/recepcion-hoy','/app/pacientes','/app/paciente-360','/app/citas','/app/agenda-diaria','/app/tratamientos-proformas','/app/mapa-sillones',
    '/app/solicitudes-web','/app/caja-pagos','/app/satisfaccion','/app/laboratorio',
    '/app/reportes','/app/reclamos','/app/inventario'
  ]);
  private readonly receptionSecondaryRoutes = new Set([
    '/app/paciente-360','/app/inventario','/app/satisfaccion','/app/laboratorio','/app/reportes','/app/reclamos'
  ]);

  readonly roleLabel = computed(() => {
    switch (this.auth.user()?.role) {
      case 'DOCTOR': return 'Panel del doctor';
      case 'RECEPCION': return 'Panel de recepción';
      case 'FINANZAS': return 'Panel de finanzas';
      default: return this.auth.isPlatformAdmin() ? 'Superadministrador de plataforma' : 'Administrador de clínica';
    }
  });

  readonly initials = computed(() => this.auth.user()?.name.split(' ').slice(0,2).map(p => p[0]).join('').toUpperCase() ?? 'CC');

  readonly notifications = computed(() => {
    const role = this.auth.user()?.role;
    if (role === 'DOCTOR') return [
      {icon:'🚨',title:'Emergencia en atención',text:'Revisa la cola prioritaria y actualiza el estado.',route:'/app/emergencias'},
      {icon:'⚗',title:'Trabajo de laboratorio próximo',text:'Hay una orden con entrega cercana.',route:'/app/laboratorio'},
      {icon:'☎',title:'Seguimiento pendiente',text:'Un paciente requiere contacto posterior.',route:'/app/seguimientos'}
    ];
    if (role === 'RECEPCION') return [
      {icon:'☑',title:'Jornada de recepción',text:'Revisa pacientes por recibir, inasistencias y solicitudes.',route:'/app/recepcion-hoy'},
      {icon:'🌐',title:'Solicitud web pendiente',text:'Revisa solicitudes nuevas y coordina una cita.',route:'/app/solicitudes-web'},
      {icon:'💬',title:'Solicitud del paciente',text:'Revisa reclamos y sugerencias abiertas.',route:'/app/reclamos'}
    ];
    if (role === 'FINANZAS') return [
      {icon:'💳',title:'Comisiones en revisión',text:'Hay liquidaciones pendientes de aprobación.',route:'/app/comisiones'},
      {icon:'🎫',title:'Membresía con saldo',text:'Revisa pagos y vigencias de planes.',route:'/app/membresias'},
      {icon:'🧾',title:'Presupuestos aceptados',text:'Consulta propuestas listas para cobranza.',route:'/app/presupuestos'}
    ];
    return [
      {icon:'🖥',title:'Servicios disponibles',text:'El entorno se encuentra operativo.',route:'/app/monitoreo'},
      {icon:'🛠',title:'Mantenimiento próximo',text:'Hay equipos con revisión programada.',route:'/app/equipos'},
      {icon:'🔐',title:'Auditoría pendiente',text:'Revisa eventos administrativos y accesos.',route:'/app/seguridad'}
    ];
  });

  ngOnInit(): void {
    if (this.auth.isPlatformAdmin()) {
      this.api.get<ClinicBrand[]>('/clinics').subscribe({ next: value => this.clinics.set(value), error: () => this.clinics.set([]) });
    }
  }

  switchClinic(code: string): void {
    if (!code || code === this.clinic.config().id || this.switching()) return;
    this.switching.set(true);
    this.auth.switchClinic(code).subscribe({
      next: () => window.location.assign('/app/dashboard'),
      error: () => this.switching.set(false)
    });
  }

  changePassword(): void {
    this.passwordMessage.set('');
    if (this.newPassword.length < 10) { this.passwordError.set(true); this.passwordMessage.set('La nueva contraseña debe tener al menos 10 caracteres.'); return; }
    if (this.newPassword !== this.confirmPassword) { this.passwordError.set(true); this.passwordMessage.set('Las contraseñas nuevas no coinciden.'); return; }
    this.passwordSaving.set(true);
    this.auth.changePassword(this.currentPassword, this.newPassword).subscribe({
      next: () => { this.passwordSaving.set(false); this.passwordError.set(false); this.passwordMessage.set('Contraseña actualizada. Inicia sesión nuevamente.'); setTimeout(() => this.auth.logout(), 900); },
      error: (error) => { this.passwordSaving.set(false); this.passwordError.set(true); this.passwordMessage.set(error?.error?.message || 'No se pudo cambiar la contraseña.'); }
    });
  }

  closePassword(): void {
    this.passwordOpen.set(false); this.passwordMessage.set(''); this.currentPassword=''; this.newPassword=''; this.confirmPassword='';
  }

  itemsFor(group: NavItem['group']): NavItem[] {
    const role = this.auth.user()?.role;
    return this.nav.filter(item => item.group === group && !!role && item.roles.includes(role) &&
      (item.route !== '/app/clinicas' || this.auth.isPlatformAdmin()) &&
      (role !== 'RECEPCION' || (this.receptionAllowedRoutes.has(item.route) && (this.moreToolsOpen() || !this.receptionSecondaryRoutes.has(item.route)))));
  }

  groupLabel(group: NavItem['group']): string {
    if (this.auth.user()?.role === 'RECEPCION') {
      return ({principal:'Trabajo de hoy',clinica:'Información clínica',operaciones:'Apoyo y cobros',administracion:'Control'} as Record<NavItem['group'],string>)[group];
    }
    return this.groups.find(item => item.key === group)?.label || group;
  }
}
