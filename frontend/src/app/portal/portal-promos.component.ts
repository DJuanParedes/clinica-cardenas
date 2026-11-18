import { Component, OnInit, inject } from '@angular/core';
import { Api } from '../core/api.service';
import { PortalAuth } from '../core/portal-auth.service';
import { IconComponent } from '../shared/icon.component';

/** Módulo Promociones del portal: campañas vigentes con precio antes/ahora, vigencia y condiciones (Ley 29571). */
@Component({
  selector: 'app-portal-promos',
  standalone: true,
  imports: [IconComponent],
  template: `
    <header class="head">
      <h1>Promociones</h1>
      <p class="muted">Campañas vigentes de la clínica</p>
    </header>
    @if (!loaded) {
      <div class="skeleton" style="height: 180px; border-radius: 20px"></div>
    } @else if (!promos.length) {
      <section class="empty"><app-icon name="tag" [size]="34" /><p>Por ahora no hay promociones vigentes.</p><p class="small muted">Te avisaremos cuando haya una nueva.</p></section>
    } @else {
      <div class="list">
        @for (p of promos; track p.id) {
          <article class="card">
            <div class="top">
              <h3>{{ p.name }}</h3>
              @if (badge(p); as b) { <span class="badge">{{ b }}</span> }
            </div>
            @if (p.procedure) { <div class="proc">{{ p.procedure }}</div> }
            @if (p.finalPrice != null) {
              <div class="price"><strong>S/ {{ money(p.finalPrice) }}</strong>
                @if (p.listPrice != null && p.listPrice > p.finalPrice) { <s>S/ {{ money(p.listPrice) }}</s> }</div>
            }
            @if (p.validTo) { <div class="small">Válida hasta el {{ p.validTo.slice(8, 10) }}/{{ p.validTo.slice(5, 7) }}/{{ p.validTo.slice(0, 4) }}</div> }
            @if (p.conditions) { <div class="small muted">Condiciones: {{ p.conditions }}</div> }
            @if (phoneDigits) {
              <a class="btn accent" target="_blank" rel="noopener" [href]="'https://wa.me/51' + phoneDigits.slice(-9) + '?text=' + enc('Hola, quiero reservar una cita con la promoción: ' + p.name)">
                <app-icon name="message" [size]="16" /> Reservar por WhatsApp</a>
            }
          </article>
        }
      </div>
      <p class="small muted legal">Precios en soles, incluyen IGV. Válidos hasta la fecha indicada o hasta agotar cupos, según las condiciones de cada campaña.</p>
    }
  `,
  styles: [`
    .head h1 { margin: 0; font-size: 24px; }
    .head p { margin: 2px 0 16px; }
    .list { display: grid; gap: 14px; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); }
    .card { background: #fff; border-radius: 20px; padding: 18px; display: grid; gap: 8px; box-shadow: 0 1px 3px rgba(15, 40, 80, .08); }
    .top { display: flex; gap: 10px; align-items: flex-start; }
    .top h3 { margin: 0; font-size: 17px; flex: 1; }
    .badge { background: #fff1dd; color: #7a4600; font-weight: 800; font-size: 13px; padding: 4px 10px; border-radius: 20px; white-space: nowrap; }
    .proc { color: var(--tinta-2, #475569); }
    .price strong { font-size: 24px; color: var(--azul, #0b5cad); margin-right: 10px; }
    .price s { color: #94a3b8; }
    .card .btn { justify-self: start; margin-top: 4px; }
    .empty { background: #fff; border-radius: 20px; padding: 32px; text-align: center; color: #64748b; }
    .legal { margin-top: 16px; }
  `],
})
export class PortalPromosComponent implements OnInit {
  private api = inject(Api);
  private portal = inject(PortalAuth);
  promos: any[] = [];
  loaded = false;
  get phoneDigits() { return (this.portal.patient()?.clinic?.phone || '').replace(/\D/g, ''); }
  ngOnInit() { this.api.publicPromotions().subscribe({ next: (p) => { this.promos = p; this.loaded = true; }, error: () => (this.loaded = true) }); }
  enc(s: string) { return encodeURIComponent(s); }
  money(v: number) { return Number(v).toFixed(Number(v) % 1 ? 2 : 0); }
  badge(p: any): string | null {
    if (p.discountType === 'PORCENTAJE' && p.value != null) return `-${p.value} %`;
    if (p.listPrice != null && p.finalPrice != null && p.listPrice > p.finalPrice) return `Ahorra S/ ${this.money(p.listPrice - p.finalPrice)}`;
    return null;
  }
}
