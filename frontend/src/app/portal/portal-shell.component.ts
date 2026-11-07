import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { PortalAuth } from '../core/portal-auth.service';
import { IconComponent } from '../shared/icon.component';
import { LogoComponent } from '../shared/logo.component';

/** HU-22: marco de la aplicación móvil del paciente (instalable desde el navegador del celular). */
@Component({
  selector: 'app-portal-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent, LogoComponent],
  template: `
    <div class="app">
      <header>
        <app-logo [size]="34" />
        <div class="grow">
          <div class="hi">Hola, {{ firstName() }}</div>
          <div class="cl">Clínica Dental Cárdenas</div>
        </div>
      </header>
      <main><router-outlet /></main>
      <nav aria-label="Navegación de la app">
        <a routerLink="/app" [routerLinkActiveOptions]="{ exact: true }" routerLinkActive="on"><app-icon name="calendar" [size]="22" /><span>Mis citas</span></a>
        <a routerLink="/app/cuenta" routerLinkActive="on"><app-icon name="wallet" [size]="22" /><span>Mi cuenta</span></a>
        <a routerLink="/app/perfil" routerLinkActive="on"><app-icon name="patient" [size]="22" /><span>Perfil</span></a>
      </nav>
    </div>
  `,
  styles: [`
    :host { display: block; min-height: 100vh; background: #e9f3f8; }
    .app { max-width: 480px; margin: 0 auto; min-height: 100vh; background: var(--porcelain); display: flex; flex-direction: column; box-shadow: 0 0 40px rgba(14, 42, 59, .08); }
    header { display: flex; align-items: center; gap: 12px; padding: 16px 18px 14px; background: #fff; border-bottom: 3px solid var(--brand); position: sticky; top: 0; z-index: 5; }
    .hi { font-family: var(--font-display); font-weight: 900; font-size: 19px; color: var(--ink); line-height: 1.1; }
    .cl { font-size: 12px; color: var(--teal); font-weight: 700; }
    main { flex: 1; padding: 16px 16px 96px; }
    nav { position: fixed; bottom: 0; left: 50%; transform: translateX(-50%); width: 100%; max-width: 480px; display: grid; grid-template-columns: repeat(3, 1fr);
      background: #fff; border-top: 1px solid var(--line); padding: 6px 6px calc(6px + env(safe-area-inset-bottom)); z-index: 5; }
    nav a { display: flex; flex-direction: column; align-items: center; gap: 2px; padding: 6px 0; border-radius: 12px; color: var(--muted); font-size: 11.5px; font-weight: 700; }
    nav a:hover { text-decoration: none; background: var(--surface-2); }
    nav a.on { color: var(--teal-strong); background: var(--brand-soft); }
  `],
})
export class PortalShellComponent {
  portal = inject(PortalAuth);
  firstName() { return (this.portal.patient()?.firstName || '').split(' ')[0]; }
}
