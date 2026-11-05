import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/** Íconos de trazo propios (24x24, stroke 1.6) para no cargar librerías externas. */
const PATHS: Record<string, string> = {
  home: 'M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-5v-6h-4v6H5a1 1 0 0 1-1-1z',
  users: 'M16 20v-1.5a3.5 3.5 0 0 0-3.5-3.5h-5A3.5 3.5 0 0 0 4 18.5V20M10 11.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM20 20v-1.5a3.5 3.5 0 0 0-2.5-3.35M15.5 4.6a3.5 3.5 0 0 1 0 6.8',
  patient: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4.5 20.5a7.5 7.5 0 0 1 15 0',
  calendar: 'M7 3v3M17 3v3M4 8.5h16M5 5.5h14a1 1 0 0 1 1 1V20a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6.5a1 1 0 0 1 1-1z',
  plus: 'M12 5v14M5 12h14',
  search: 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM20 20l-4-4',
  receipt: 'M6 3h12v18l-3-2-3 2-3-2-3 2zM9 8h6M9 12h6M9 16h3',
  tooth: 'M8 3.5c-2.5 0-4 1.9-4 4.4 0 2.9 1.5 4.7 2.3 7.2.6 1.9 1 5.4 2.5 5.4 1.4 0 1.7-3 2.2-4.6.3-1 .6-1.6 1-1.6s.7.6 1 1.6c.5 1.6.8 4.6 2.2 4.6 1.5 0 1.9-3.5 2.5-5.4.8-2.5 2.3-4.3 2.3-7.2 0-2.5-1.5-4.4-4-4.4-2.2 0-3 1.2-4 1.2s-1.8-1.2-4-1.2z',
  logout: 'M15 4h3a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-3M10 16l-4-4 4-4M6 12h10',
  chevronLeft: 'M15 5l-7 7 7 7',
  chevronRight: 'M9 5l7 7-7 7',
  chevronDown: 'M5 9l7 7 7-7',
  check: 'M5 12.5l4.5 4.5L19 7.5',
  x: 'M6 6l12 12M18 6 6 18',
  clock: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2',
  alert: 'M12 9v4M12 17h.01M10.3 4.2 2.6 17.5A2 2 0 0 0 4.3 20.5h15.4a2 2 0 0 0 1.7-3L13.7 4.2a2 2 0 0 0-3.4 0z',
  edit: 'M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16zM13.5 6.5l4 4',
  send: 'M21 3 10 14M21 3l-7 18-4-7-7-4z',
  download: 'M12 4v11M7 10l5 5 5-5M5 20h14',
  print: 'M7 9V3h10v6M7 17H5a1 1 0 0 1-1-1v-5a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v5a1 1 0 0 1-1 1h-2M7 14h10v7H7z',
  lock: 'M6 11h12v9H6zM8.5 11V8a3.5 3.5 0 1 1 7 0v3',
  unlock: 'M6 11h12v9H6zM8.5 11V8a3.5 3.5 0 0 1 6.8-1.2',
  shield: 'M12 3 5 6v5.5c0 4.3 3 8 7 9.5 4-1.5 7-5.2 7-9.5V6z',
  clipboard: 'M9 4h6v3H9zM9 5.5H6a1 1 0 0 0-1 1V20a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V6.5a1 1 0 0 0-1-1h-3M9 12h6M9 16h4',
  activity: 'M3 12h4l3-7 4 14 3-7h4',
  menu: 'M4 7h16M4 12h16M4 17h16',
  arrowRight: 'M5 12h14M13 6l6 6-6 6',
  door: 'M5 21V4a1 1 0 0 1 1-1h9l4 2v16M14 12h.01M3 21h18',
  phone: 'M5 4h3l2 5-2.5 1.5a11 11 0 0 0 6 6L15 14l5 2v3a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z',
  mail: 'M4 6h16v12H4zM4 7l8 6 8-6',
  file: 'M7 3h7l5 5v13H7zM14 3v5h5',
  refresh: 'M20 11a8 8 0 0 0-14.6-4.5M4 4v4h4M4 13a8 8 0 0 0 14.6 4.5M20 20v-4h-4',
  eye: 'M2.5 12S6 5 12 5s9.5 7 9.5 7-3.5 7-9.5 7S2.5 12 2.5 12zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z',
  wallet: 'M4 7a2 2 0 0 1 2-2h11v4M4 7v11a2 2 0 0 0 2 2h14V9H6a2 2 0 0 1-2-2zM16 14.5h.01',
  chart: 'M4 20V4M4 20h16M8 16v-5M12 16V8M16 16v-3M20 16V6',
  percent: 'M19 5 5 19M7.5 9a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zM16.5 18a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z',
  bell: 'M6 16V11a6 6 0 1 1 12 0v5l1.5 2h-15zM10 20a2 2 0 0 0 4 0',
  smartphone: 'M7 3h10a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM11 18h2',
  key: 'M14.5 9.5a4 4 0 1 0-3.7 4L4 20v-3h3v-3h3l.8-.8a4 4 0 0 0 3.7-3.7zM15.5 8.5h.01',
  arrowUp: 'M12 19V5M6 11l6-6 6 6',
  arrowDown: 'M12 5v14M6 13l6 6 6-6',
  message: 'M4 5h16v11H9l-5 4z',
  external: 'M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5',
  coins: 'M9 8c3.3 0 6-1.1 6-2.5S12.3 3 9 3 3 4.1 3 5.5 5.7 8 9 8zM3 5.5v4C3 10.9 5.7 12 9 12s6-1.1 6-2.5v-4M9 16c-3.3 0-6-1.1-6-2.5M3 9.5v8C3 18.9 5.7 20 9 20c1.4 0 2.6-.2 3.6-.5M15 13c3.3 0 6-1.1 6-2.5S18.3 8 15 8M21 10.5v8c0 1.4-2.7 2.5-6 2.5s-6-1.1-6-2.5v-4',
  eyeOff: 'M3 3l18 18M10.6 5.1A9.7 9.7 0 0 1 12 5c6 0 9.5 7 9.5 7a17 17 0 0 1-3.1 3.9M6.3 6.3C3.8 8 2.5 12 2.5 12S6 19 12 19a9.4 9.4 0 0 0 5-1.4M9.9 9.9a3 3 0 0 0 4.2 4.2',
};

@Component({
  selector: 'app-icon',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg [attr.width]="size" [attr.height]="size" viewBox="0 0 24 24" fill="none" stroke="currentColor"
         [attr.stroke-width]="stroke" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path [attr.d]="d"></path>
    </svg>
  `,
  styles: [`:host { display: inline-flex; flex: none; line-height: 0; }`],
})
export class IconComponent {
  @Input() name = 'home';
  @Input() size = 18;
  @Input() stroke = 1.6;
  get d(): string { return PATHS[this.name] || ''; }
}
