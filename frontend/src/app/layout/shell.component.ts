import { Component, computed, inject } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { IconComponent } from '../shared/icon.component';
import { LogoComponent } from '../shared/logo.component';
import { initials } from '../core/format';

interface NavItem { path: string; label: string; icon: string; perms: string[]; }
interface NavGroup { title: string; items: NavItem[]; }

/** Menú según los permisos del rol (HU-26). */
const NAV: NavGroup[] = [
  { title: '', items: [
    { path: '/inicio', label: 'Inicio', icon: 'home', perms: [] },
    { path: '/agenda', label: 'Agenda del día', icon: 'calendar', perms: ['AGENDA_VER'] },
    { path: '/pacientes', label: 'Pacientes', icon: 'patient', perms: ['PACIENTES_VER'] },
  ] },
  { title: 'Caja', items: [
    { path: '/caja', label: 'Caja del día', icon: 'wallet', perms: ['CAJA_COBRAR', 'CAJA_EGRESOS'] },
    { path: '/comprobantes', label: 'Comprobantes', icon: 'receipt', perms: ['COMPROBANTES'] },
    { path: '/caja/reporte', label: 'Flujo de caja', icon: 'coins', perms: ['CAJA_REPORTE'] },
  ] },
  { title: 'Gestión', items: [
    { path: '/reportes', label: 'Indicadores', icon: 'chart', perms: ['REPORTES'] },
    { path: '/comisiones', label: 'Comisiones', icon: 'percent', perms: ['COMISIONES'] },
    { path: '/notificaciones', label: 'Notificaciones', icon: 'bell', perms: ['NOTIFICACIONES'] },
  ] },
  { title: 'Seguridad', items: [
    { path: '/usuarios', label: 'Usuarios y accesos', icon: 'shield', perms: ['USUARIOS'] },
    { path: '/roles', label: 'Roles y permisos', icon: 'key', perms: ['ROLES'] },
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

        @if (canCreateAppointment()) {
          <a class="btn accent block quick" routerLink="/citas/nueva"><app-icon name="plus" [size]="16" [stroke]="2.2" /> Nueva cita</a>
        }

        <nav aria-label="Navegación principal">
          @for (g of nav(); track g.title) {
            @if (g.title) { <div class="gt">{{ g.title }}</div> }
            @for (item of g.items; track item.path) {
              <a [routerLink]="item.path" routerLinkActive="on" [routerLinkActiveOptions]="{ exact: item.path === '/caja' }" class="nav">
                <app-icon [name]="item.icon" />
                <span>{{ item.label }}</span>
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
export class ShellComponent {
  auth = inject(AuthService);
  private router = inject(Router);
  navOpen = false;

  nav = computed(() => {
    const u = this.auth.user();
    if (!u) return [];
    const ok = (n: NavItem) => !n.perms.length || n.perms.some((p) => (u.permissions || []).includes(p));
    return NAV.map((g) => ({ title: g.title, items: g.items.filter(ok) })).filter((g) => g.items.length);
  });
  canCreateAppointment = computed(() => !!this.auth.user() && this.auth.can('CITAS_GESTIONAR'));
  ini = computed(() => initials(this.auth.user()?.fullName));

  constructor() {
    this.auth.refresh(); // permisos al día (HU-26)
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => (this.navOpen = false));
  }
}
