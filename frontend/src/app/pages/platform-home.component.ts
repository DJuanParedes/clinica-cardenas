import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { ClinicConfigService } from '../core/clinic-config.service';
import { ClinicBrand } from '../core/models';

@Component({
  selector: 'app-platform-home',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="platform-page">
      <header>
        <a class="platform-brand" routerLink="/">
          <img src="brand/platform-logo.svg" alt="Plataforma multiclínica">
          <div><b>Portal odontológico</b><small>Plataforma multiclínica</small></div>
        </a>
        <nav><a routerLink="." fragment="clinicas">Clínicas</a><a routerLink="." fragment="como-funciona">Cómo funciona</a></nav>
        <a class="login" routerLink="/login">Iniciar sesión</a>
      </header>

      <main>
        <section class="hero">
          <div class="hero-copy">
            <span class="eyebrow">Una plataforma · múltiples clínicas</span>
            <h1>Encuentra tu clínica y accede a una atención organizada.</h1>
            <p>Este es el portal general de la red. Cada clínica mantiene su propia identidad, equipo, pacientes e información completamente separada.</p>
            <div class="actions"><a class="primary" routerLink="." fragment="clinicas">Ver clínicas</a><a class="secondary" routerLink="/login">Acceso del personal</a></div>
            <div class="trust"><span><b>{{clinics().length}}</b> clínicas disponibles</span><span><b>24/7</b> acceso seguro</span><span><b>100%</b> información separada</span></div>
          </div>
          <div class="network" aria-hidden="true">
            <div class="network-ring ring-one"></div><div class="network-ring ring-two"></div>
            <div class="hub"><img src="brand/platform-logo.svg" alt=""><b>Portal general</b><small>Conecta sin mezclar</small></div>
            <div class="node node-a">Clínica A</div><div class="node node-b">Clínica B</div><div class="node node-c">Clínica C</div>
          </div>
        </section>

        <section class="clinics" id="clinicas">
          <div class="section-heading"><span>Directorio</span><h2>Clínicas de la plataforma</h2><p>Selecciona una clínica para visitar su página pública. El personal puede entrar directamente con su correo institucional.</p></div>
          @if (loading()) {
            <div class="status">Cargando clínicas disponibles…</div>
          } @else if (error()) {
            <div class="status error">{{error()}}</div>
          } @else {
            <div class="clinic-grid">
              @for (brand of clinics(); track brand.code) {
                <article [style.--clinic-color]="brand.primaryColor" [style.--clinic-accent]="brand.accentColor">
                  <div class="clinic-cover" [style.background-image]="cover(brand)"><span>Clínica verificada</span></div>
                  <div class="clinic-content">
                    <div class="clinic-title"><img [src]="brand.logoUrl" [alt]="'Logo ' + brand.name"><div><h3>{{brand.name}}</h3><small>{{brand.tagline}}</small></div></div>
                    <p>{{brand.description}}</p>
                    <div class="clinic-data"><span>📍 {{brand.address}}</span>@if(brand.phone){<span>☎ {{brand.phone}}</span>}@if(brand.email){<span>✉ {{brand.email}}</span>}<span>🕘 {{brand.workingHours}}</span></div>
                    <a [routerLink]="['/marca', brand.slug]">Visitar esta clínica <span>→</span></a>
                  </div>
                </article>
              } @empty {
                <div class="status">Todavía no hay clínicas públicas disponibles.</div>
              }
            </div>
          }
        </section>

        <section class="how" id="como-funciona">
          <div><span>1</span><h3>Portal general</h3><p>Muestra el directorio completo sin adoptar la marca de ninguna clínica.</p></div>
          <div><span>2</span><h3>Acceso identificado</h3><p>El correo y la contraseña determinan la clínica y las responsabilidades del usuario.</p></div>
          <div><span>3</span><h3>Espacio propio</h3><p>Después del ingreso aparecen únicamente la identidad y los datos de la clínica asignada.</p></div>
        </section>
      </main>

      <footer><div class="platform-brand"><img src="brand/platform-logo.svg" alt=""><div><b>Portal odontológico</b><small>Gestión multiclínica segura</small></div></div><p>Una sola plataforma, espacios independientes para cada clínica.</p><a routerLink="/login">Acceso al sistema</a></footer>
    </div>
  `,
  styles: [`
    .platform-page{min-height:100vh;background:#f6fafb;color:#15394a}header{height:78px;display:flex;align-items:center;justify-content:space-between;gap:26px;padding:0 max(5vw,24px);background:rgba(255,255,255,.95);border-bottom:1px solid #dce9ec;position:sticky;top:0;z-index:20;backdrop-filter:blur(14px)}.platform-brand{display:flex;align-items:center;gap:11px;color:#173a59}.platform-brand img{width:44px;height:44px}.platform-brand b,.platform-brand small{display:block}.platform-brand b{font-size:15px}.platform-brand small{font-size:10px;color:#65808c;text-transform:uppercase;letter-spacing:1px;margin-top:2px}nav{display:flex;gap:28px}nav a{color:#496976;font-size:13px;font-weight:700}.login,.primary,.secondary{display:inline-flex;align-items:center;justify-content:center;min-height:44px;padding:0 19px;border-radius:11px;font-weight:800;font-size:13px}.login,.primary{background:#1d6473;color:#fff;box-shadow:0 12px 26px rgba(29,100,115,.18)}.secondary{border:1px solid #bdd3d8;background:#fff;color:#285563}.hero{min-height:630px;padding:70px max(7vw,30px);display:grid;grid-template-columns:1.06fr .94fr;gap:60px;align-items:center;background:radial-gradient(circle at 82% 22%,#d9f2ef 0,transparent 27%),linear-gradient(140deg,#fff,#edf5f7)}.eyebrow,.section-heading>span{display:inline-block;color:#1d7981;text-transform:uppercase;letter-spacing:1.7px;font-size:11px;font-weight:900}.hero h1{font-size:clamp(43px,5.4vw,75px);line-height:1.02;letter-spacing:-3px;margin:20px 0;color:#173a59}.hero-copy>p{font-size:17px;line-height:1.75;color:#5b7680;max-width:690px}.actions{display:flex;gap:12px;margin:31px 0}.trust{display:flex;flex-wrap:wrap;gap:29px;border-top:1px solid #cfdee2;padding-top:23px;color:#718790;font-size:11px}.trust b{display:block;color:#173a59;font-size:20px;margin-bottom:3px}.network{min-height:480px;position:relative;display:grid;place-items:center}.network-ring{position:absolute;border:1px solid rgba(29,121,129,.2);border-radius:50%}.ring-one{width:370px;height:370px}.ring-two{width:500px;height:500px}.hub{width:215px;height:215px;border-radius:58px;background:linear-gradient(145deg,#fff,#e9f4f5);box-shadow:0 32px 70px rgba(24,73,87,.2);display:flex;flex-direction:column;align-items:center;justify-content:center;z-index:3;border:1px solid #d6e8ea}.hub img{width:90px;height:90px}.hub b,.hub small{display:block}.hub b{margin-top:10px}.hub small{color:#6b858e;margin-top:4px}.node{position:absolute;background:#fff;border:1px solid #cfe1e4;border-radius:14px;padding:13px 18px;font-size:12px;font-weight:800;box-shadow:0 16px 35px rgba(29,78,91,.12);z-index:4}.node::before{content:'●';color:#52bcb5;margin-right:7px}.node-a{top:52px;left:18px}.node-b{right:0;top:150px}.node-c{left:25px;bottom:52px}.clinics{padding:95px max(6vw,28px);background:#fff}.section-heading{text-align:center;max-width:760px;margin:0 auto 43px}.section-heading h2{font-size:clamp(31px,4vw,52px);letter-spacing:-1.8px;margin:12px 0}.section-heading p{color:#69838d;line-height:1.65}.clinic-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:24px;max-width:1240px;margin:auto}.clinic-grid article{border:1px solid #dbe7e9;border-radius:22px;overflow:hidden;background:#fff;box-shadow:0 18px 46px rgba(26,74,86,.08);transition:.25s}.clinic-grid article:hover{transform:translateY(-6px);box-shadow:0 28px 60px rgba(26,74,86,.14)}.clinic-cover{height:150px;background-position:center;background-size:cover;position:relative;background-color:var(--clinic-color)}.clinic-cover::after{content:'';position:absolute;inset:0;background:linear-gradient(180deg,transparent,rgba(11,35,45,.5))}.clinic-cover span{position:absolute;left:16px;bottom:14px;z-index:2;background:#fff;color:var(--clinic-color);padding:7px 10px;border-radius:8px;font-size:9px;font-weight:900;text-transform:uppercase}.clinic-content{padding:23px}.clinic-title{display:flex;gap:13px;align-items:center}.clinic-title img{width:52px;height:52px;object-fit:contain;border-radius:12px;border:1px solid #e1ebed;padding:4px;background:#fff}.clinic-title h3{margin:0;font-size:18px}.clinic-title small{display:block;color:#789098;margin-top:4px}.clinic-content>p{color:#617b85;font-size:12px;line-height:1.65;min-height:58px}.clinic-data{display:grid;gap:7px;color:#5c7680;font-size:10px;padding:14px 0;border-top:1px solid #e5edef;border-bottom:1px solid #e5edef}.clinic-content>a{display:flex;justify-content:space-between;margin-top:17px;color:var(--clinic-color);font-weight:900;font-size:12px}.status{max-width:760px;margin:auto;padding:25px;border-radius:15px;background:#eef5f6;color:#56747e;text-align:center}.status.error{background:#fff1f1;color:#9b4848}.how{padding:80px max(7vw,30px);display:grid;grid-template-columns:repeat(3,1fr);gap:20px;background:#173a59;color:#fff}.how div{padding:28px;border:1px solid rgba(255,255,255,.12);border-radius:18px;background:rgba(255,255,255,.04)}.how span{width:40px;height:40px;display:grid;place-items:center;border-radius:12px;background:#55c3bc;color:#103a46;font-weight:900}.how h3{margin:20px 0 9px}.how p{color:#c5d9df;font-size:12px;line-height:1.7;margin:0}footer{min-height:105px;padding:24px max(5vw,24px);display:flex;align-items:center;justify-content:space-between;gap:25px;background:#102e47;color:#c5d8df}footer .platform-brand b{color:#fff}footer p{font-size:12px}footer>a{color:#71d2cb;font-weight:800;font-size:12px}@media(max-width:980px){.hero{grid-template-columns:1fr;text-align:center}.hero-copy>p{margin-inline:auto}.actions,.trust{justify-content:center}.network{min-height:390px}.clinic-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:700px){header nav{display:none}.hero{padding:55px 23px}.hero h1{letter-spacing:-1.8px}.network{display:none}.clinic-grid,.how{grid-template-columns:1fr}.clinics{padding:70px 20px}.how{padding:55px 20px}footer{align-items:flex-start;flex-direction:column}}
  `]
})
export class PlatformHomeComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly branding = inject(ClinicConfigService);
  readonly clinics = signal<ClinicBrand[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');

  ngOnInit(): void {
    this.branding.applyPlatformBranding();
    this.api.get<ClinicBrand[]>('/public/clinics').subscribe({
      next: clinics => { this.clinics.set(clinics.filter(clinic => clinic.active)); this.loading.set(false); },
      error: () => { this.error.set('No se pudo cargar el directorio de clínicas. Inténtalo nuevamente.'); this.loading.set(false); }
    });
  }

  cover(brand: ClinicBrand): string {
    return `linear-gradient(180deg, transparent, color-mix(in srgb, ${brand.primaryDarkColor} 72%, transparent)), url("${brand.heroImageUrl}")`;
  }
}

