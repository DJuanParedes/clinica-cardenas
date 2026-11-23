import { HostListener, Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { Api } from '../core/api.service';
import { IconComponent } from '../shared/icon.component';
import { LogoComponent } from '../shared/logo.component';
import { initials } from '../core/format';

interface NavItem { path: string; label: string; icon: string; perms: string[]; roles?: string[]; }
interface NavGroup { title: string; items: NavItem[]; }

/** Menú según los permisos del rol (HU-26). */
const NAV: NavGroup[] = [
  { title: '', items: [
    { path: '/inicio', label: 'Inicio', icon: 'home', perms: [] },
    { path: '/agenda', label: 'Agenda del día', icon: 'calendar', perms: ['AGENDA_VER'] },
    { path: '/pacientes', label: 'Pacientes', icon: 'patient', perms: ['PACIENTES_VER'] },
    { path: '/mensajes', label: 'Mensajes WhatsApp', icon: 'message', perms: ['MENSAJES'] },
    { path: '/tareas', label: 'Tareas', icon: 'checks', perms: ['TAREAS'] },
    { path: '/seguimiento', label: 'Seguimiento', icon: 'activity', perms: ['AGENDA_VER'] },
    { path: '/embudo', label: 'Embudo de ventas', icon: 'funnel', perms: ['LEADS'] },
    { path: '/mis-ganancias', label: 'Mis ganancias', icon: 'coins', perms: [], roles: ['ODONTOLOGO'] },
    { path: '/ortodoncia', label: 'Ortodoncia', icon: 'tooth', perms: ['HISTORIA_CLINICA'] },
    { path: '/consentimientos', label: 'Consentimientos', icon: 'clipboard', perms: ['HISTORIA_CLINICA', 'PACIENTES_EDITAR'] },
    { path: '/laboratorio', label: 'Laboratorio', icon: 'tooth', perms: ['LABORATORIO', 'CAJA_EGRESOS'] },
    { path: '/inventario', label: 'Inventario', icon: 'clipboard', perms: ['INVENTARIO'] },
    { path: '/insumos', label: 'Solicitar insumos', icon: 'box', perms: ['SOLICITAR_INSUMOS'] },
    { path: '/catalogo', label: 'Procedimientos y promociones', icon: 'tag', perms: ['PROMOCIONES', 'PLAN_TRATAMIENTO'] },
  ] },
  { title: 'Caja', items: [
    { path: '/caja', label: 'Caja del día', icon: 'wallet', perms: ['CAJA_COBRAR', 'CAJA_EGRESOS'] },
    { path: '/comprobantes', label: 'Comprobantes', icon: 'receipt', perms: ['COMPROBANTES'] },
    { path: '/caja/cierre', label: 'Cuadre de caja', icon: 'check', perms: ['CAJA_CIERRE'] },
    { path: '/caja/reporte', label: 'Flujo de caja', icon: 'coins', perms: ['CAJA_REPORTE'] },
  ] },
  { title: 'Gestión', items: [
    { path: '/reportes', label: 'Indicadores', icon: 'chart', perms: ['REPORTES'] },
    { path: '/comisiones', label: 'Comisiones', icon: 'percent', perms: ['COMISIONES'] },
    { path: '/marketing', label: 'Marketing y campañas', icon: 'megaphone', perms: ['MARKETING'] },
    { path: '/comunicacion', label: 'Comunicación con pacientes', icon: 'send', perms: ['AUTOMATIZACIONES'] },
    { path: '/notificaciones', label: 'Notificaciones', icon: 'bell', perms: ['NOTIFICACIONES'] },
    { path: '/reclamos', label: 'Libro de reclamaciones', icon: 'clipboard', perms: ['RECLAMOS'] },
  ] },
  { title: 'Seguridad', items: [
    { path: '/usuarios', label: 'Personal', icon: 'shield', perms: ['USUARIOS'] },
    { path: '/roles', label: 'Roles y permisos', icon: 'key', perms: ['ROLES'] },
    { path: '/perfil', label: 'Mi perfil', icon: 'user', perms: [] },
    { path: '/seguridad', label: 'Mi seguridad (2 pasos)', icon: 'lock', perms: [] },
  ] },
];

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent, LogoComponent],
  template: `
    <div class="shell" [class.nav-open]="navOpen">
      <aside class="side">
        <a class="brand" routerLink="/inicio" aria-label="Clínica Dental Cárdenas, ir al inicio">
          <app-logo [size]="44" />
          <span class="wm">
            <span class="w1">Clínica Dental</span>
            <span class="w2">Cárdenas</span>
          </span>
        </a>

        @if (createItems().length) {
          <div class="create" (click)="$event.stopPropagation()">
            <button type="button" class="btn accent block quick" [attr.aria-expanded]="createOpen" aria-haspopup="menu" (click)="createOpen = !createOpen">
              <app-icon name="plus" [size]="16" [stroke]="2.2" /> Crear</button>
            @if (createOpen) {
              <div class="create-menu" role="menu">
                @for (c of createItems(); track c.path + (c.q || '')) {
                  <a role="menuitem" [routerLink]="c.path" [queryParams]="c.params" (click)="createOpen = false"><app-icon [name]="c.icon" [size]="16" /> {{ c.label }}</a>
                }
              </div>
            }
          </div>
        }

        <nav aria-label="Navegación principal">
          @for (g of nav(); track g.title) {
            @if (g.title) { <div class="gt">{{ g.title }}</div> }
            @for (item of g.items; track item.path) {
              <a [routerLink]="item.path" routerLinkActive="on" [routerLinkActiveOptions]="{ exact: item.path === '/caja' }" class="nav">
                <app-icon [name]="item.icon" />
                <span>{{ item.label }}</span>
                @if (item.path === '/tareas' && dueTasks() > 0) { <span class="badge tasks" [attr.aria-label]="dueTasks() + ' tareas vencidas o para hoy'">{{ dueTasks() > 99 ? '99+' : dueTasks() }}</span> }
                @if (item.path === '/mensajes' && unread() > 0) { <span class="badge" [attr.aria-label]="unread() + ' mensajes sin leer'">{{ unread() > 99 ? '99+' : unread() }}</span> }
              </a>
            }
          }
        </nav>

        <p class="place">
          <app-icon name="home" [size]="14" /> Sede La Floresta · Chaclacayo
        </p>

        <div class="me">
          <span class="avatar">{{ ini() }}</span>
          <div class="who">
            <div class="n">{{ auth.user()?.fullName }}</div>
            <div class="r">{{ auth.user()?.roleLabel }}</div>
          </div>
          <button type="button" class="out" (click)="auth.logout()" title="Cerrar sesión" aria-label="Cerrar sesión">
            <app-icon name="logout" />
          </button>
        </div>
      </aside>

      <div class="scrim" (click)="navOpen = false"></div>

      <main class="main">
        <div class="topbar">
          <button type="button" class="btn ghost sm" (click)="navOpen = !navOpen" aria-label="Abrir menú"><app-icon name="menu" /></button>
          <app-logo [size]="30" />
          <span class="tb">Clínica Dental Cárdenas</span>
        </div>
        <router-outlet />
      </main>
    </div>
  `,
  styles: [`
    .shell { display: grid; grid-template-columns: 256px minmax(0, 1fr); min-height: 100vh; }
    .side { position: sticky; top: 0; height: 100vh; display: flex; flex-direction: column; gap: 4px;
      background: var(--surface); border-right: 1px solid var(--line); padding: 18px 14px 14px; overflow-y: auto; overflow-x: hidden; }
    .brand { display: flex; align-items: center; gap: 10px; padding: 2px 6px 18px; color: var(--ink); }
    .brand:hover { text-decoration: none; }
    .wm { display: flex; flex-direction: column; line-height: 1.05; font-family: var(--font-display); }
    .w1 { font-size: 13px; font-weight: 700; color: var(--teal); letter-spacing: .01em; }
    .w2 { font-size: 22px; font-weight: 900; color: var(--ink); letter-spacing: -.01em; }
    .quick { height: 42px; margin: 0 0 16px; font-size: 14px; width: 100%; }
    nav { display: flex; flex-direction: column; gap: 2px; }
    .gt { font-size: 11px; font-weight: 800; color: var(--muted); letter-spacing: .04em; padding: 14px 12px 4px; }
    .nav { position: relative; display: flex; align-items: center; gap: 12px; padding: 9px 12px; border-radius: 10px;
      color: var(--ink-2); font-size: 14px; font-weight: 600; }
    .nav:hover { background: var(--surface-2); color: var(--ink); text-decoration: none; }
    .create { position: relative; }
    .create-menu { position: absolute; left: 0; right: 0; top: calc(100% + 6px); z-index: 50; background: var(--surface); border: 1px solid var(--line);
      border-radius: 14px; box-shadow: 0 16px 40px -18px rgba(11, 40, 60, .45); padding: 6px; display: grid; }
    .create-menu a { display: flex; align-items: center; gap: 10px; padding: 9px 10px; border-radius: 10px; color: var(--ink); font-weight: 600; font-size: 14px; text-decoration: none; }
    .create-menu a:hover { background: var(--teal-soft); color: var(--teal-strong); }
    .badge.tasks { background: var(--gum); }
    .badge { margin-left: auto; min-width: 20px; height: 20px; padding: 0 6px; border-radius: 10px; background: #25d366; color: #fff;
      font-size: 11.5px; font-weight: 800; display: grid; place-items: center; }
    .nav.on { background: var(--brand-soft); color: var(--teal-strong); font-weight: 700; }
    .nav.on::before { content: ""; position: absolute; left: -14px; top: 8px; bottom: 8px; width: 4px; border-radius: 0 4px 4px 0; background: var(--orange); }
    .place { display: flex; align-items: center; gap: 6px; margin: auto 8px 10px; font-size: 12px; color: var(--muted); }
    .me { display: flex; align-items: center; gap: 10px; padding: 12px 6px 2px; border-top: 1px solid var(--line); }
    .who { min-width: 0; flex: 1; }
    .who .n { font-size: 13px; font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .who .r { font-size: 12px; color: var(--muted); }
    .out { background: none; border: 0; color: var(--muted); cursor: pointer; padding: 6px; border-radius: 8px; }
    .out:hover { color: var(--gum); background: var(--gum-soft); }
    .main { min-width: 0; }
    .topbar { display: none; }
    .scrim { display: none; }
    @media (max-width: 960px) {
      .shell { grid-template-columns: minmax(0, 1fr); }
      .side { position: fixed; z-index: 100; left: 0; top: 0; width: 272px; transform: translateX(-100%); transition: transform .2s ease; box-shadow: var(--shadow-pop); }
      .nav-open .side { transform: none; }
      .nav-open .scrim { display: block; position: fixed; inset: 0; z-index: 99; background: rgba(14, 42, 59, .35); }
      .topbar { display: flex; align-items: center; gap: 10px; padding: 8px 12px; background: var(--surface); border-bottom: 1px solid var(--line); position: sticky; top: 0; z-index: 50; }
      .tb { font-family: var(--font-display); font-weight: 800; }
    }
    @media print { .side, .topbar { display: none !important; } .shell { display: block; } }
  `],
})
export class ShellComponent implements OnDestroy {
  auth = inject(AuthService);
  private router = inject(Router);
  private api = inject(Api);
  navOpen = false;
  /** Mensajes de WhatsApp sin leer (insignia del menú, se actualiza cada 15 s). */
  unread = signal(0);
  dueTasks = signal(0);
  private polls = 0;
  private timer: ReturnType<typeof setInterval> | undefined;

  nav = computed(() => {
    const u = this.auth.user();
    if (!u) return [];
    const ok = (n: NavItem) => (!n.roles || n.roles.includes(u.role)) && (!n.perms.length || n.perms.some((p) => (u.permissions || []).includes(p)));
    return NAV.map((g) => ({ title: g.title, items: g.items.filter(ok) })).filter((g) => g.items.length);
  });
  canCreateAppointment = computed(() => !!this.auth.user() && this.auth.can('CITAS_GESTIONAR'));
  /** v2.8: botón global "Crear" con lo que cada rol puede registrar. */
  createOpen = false;
  createItems = computed(() => {
    if (!this.auth.user()) return [];
    const all: { label: string; icon: string; path: string; params?: any; q?: string; perm: string }[] = [
      { label: 'Nueva cita', icon: 'calendar', path: '/citas/nueva', perm: 'CITAS_GESTIONAR' },
      { label: 'Registrar paciente', icon: 'patient', path: '/pacientes/nuevo', perm: 'PACIENTES_EDITAR' },
      { label: 'Nueva tarea', icon: 'checks', path: '/tareas', params: { nueva: 1 }, q: 't', perm: 'TAREAS' },
      { label: 'Registrar cobro', icon: 'wallet', path: '/caja/cobro', perm: 'CAJA_COBRAR' },
      { label: 'Emitir comprobante', icon: 'receipt', path: '/comprobantes/nuevo', perm: 'COMPROBANTES' },
      { label: 'Nuevo lead', icon: 'funnel', path: '/embudo', params: { nuevo: 1 }, q: 'l', perm: 'LEADS' },
      { label: 'Nueva campaña', icon: 'megaphone', path: '/marketing', params: { vista: 'campanas', nueva: 1 }, q: 'c', perm: 'MARKETING' },
    ];
    return all.filter((c) => this.auth.can(c.perm));
  });
  ini = computed(() => initials(this.auth.user()?.fullName));

  @HostListener('document:click') closeCreate() { this.createOpen = false; }
  @HostListener('document:keydown.escape') escCreate() { this.createOpen = false; }

  constructor() {
    this.auth.refresh(); // permisos al día (HU-26)
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => (this.navOpen = false));
    this.pollUnread();
    this.timer = setInterval(() => this.pollUnread(), 15000);
  }

  ngOnDestroy() { clearInterval(this.timer); }

  private pollUnread() {
    if (this.auth.can('TAREAS') && this.polls++ % 4 === 0)
      this.api.taskSummary().subscribe({ next: (s) => this.dueTasks.set((s.mineOverdue || 0) + (s.mineToday || 0)), error: () => {} });
    if (!this.auth.can('MENSAJES')) return;
    this.api.conversationSummary().subscribe({ next: (s) => this.unread.set(s.unread), error: () => {} });
  }
}
