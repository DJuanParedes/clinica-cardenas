import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/** Isotipo de Clínica Dental Cárdenas: diente celeste con corona naranja y anillo naranja. */
@Component({
  selector: 'app-logo',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg [attr.width]="size" [attr.height]="size" viewBox="40 40 332 332" role="img" aria-label="Clínica Dental Cárdenas">
      @if (ring) { <path d="M334 102 A154 154 0 1 0 342 300 A149.5 149.5 0 1 1 334 102 Z" [attr.fill]="mono ? 'currentColor' : '#F89818'" /> }
      <path d="M121 196 C116 236 140 272 160 292 C168 301 180 306 186 296 C193 284 199 268 214 266 C229 266 236 284 243 296 C250 307 263 304 270 292 C292 258 312 222 312 172 C312 136 290 118 262 118 C238 118 222 132 210 154 C196 178 168 196 121 196 Z" [attr.fill]="mono ? 'currentColor' : '#00B0E0'" />
      <path d="M116 168 C116 148 128 132 156 130 C182 128 206 128 224 128 C214 146 200 170 176 182 C156 192 134 194 124 190 C118 186 116 178 116 168 Z" [attr.fill]="mono ? 'currentColor' : '#F89818'" />
    </svg>
  `,
  styles: [`:host { display: inline-flex; line-height: 0; flex: none; }`],
})
export class LogoComponent {
  @Input() size = 40;
  @Input() ring = true;
  @Input() mono = false;
}
