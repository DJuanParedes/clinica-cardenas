import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { ClinicConfigService } from '../core/clinic-config.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink, FormsModule],
  template: `
    <div class="public-site">
      <header class="public-nav">
        <a class="public-brand" href="#inicio"><img [src]="clinic.logoUrl()" [alt]="'Logo ' + clinic.name()"><div><b>{{ clinic.name() }}</b><small>{{ clinic.config().tagline }}</small></div></a>
        <nav>
          <a href="#nosotros">Nosotros</a><a href="#servicios">Especialidades</a><a href="#doctores">Doctores</a><a href="#tecnologia">Tecnología</a><a [routerLink]="['/marca',clinic.config().slug,'encuesta']">Encuesta</a><a href="#contacto">Contacto</a>
        </nav>
        <div class="public-actions"><a class="login-link" [routerLink]="['/marca',clinic.config().slug,'encuesta']">Calificar atención</a><a class="appointment" href="#contacto">Reservar cita</a><a class="login-link" routerLink="/login">Iniciar sesión</a></div>
      </header>

      <main>
        <section class="hero" id="inicio">
          <div class="hero-copy">
            <div class="eyebrow">● Atención odontológica integral</div>
            <h1>Tu sonrisa merece una atención <span>extraordinaria.</span></h1>
            <p>{{ clinic.config().description }}</p>
            <div class="hero-buttons"><a class="primary" href="#contacto">Agenda una evaluación</a><a class="secondary" href="#servicios">Ver especialidades</a></div>
            <div class="hero-trust"><div><b>La Floresta</b><small>sede de atención</small></div><div><b>Integral</b><small>evaluación odontológica</small></div><div><b>Coordinada</b><small>solicitud de citas</small></div></div>
          </div>
          <div class="hero-visual">
            <div class="visual-orbit orbit-one"></div><div class="visual-orbit orbit-two"></div>
            <div class="tooth-card"><img [src]="clinic.logoUrl()" [alt]="clinic.name()"><b>{{ clinic.name() }}</b><small>{{ clinic.config().tagline }}</small></div>
            <div class="floating-card card-a"><span>✓</span><div><b>Agenda inteligente</b><small>Disponibilidad en tiempo real</small></div></div>
            <div class="floating-card card-b"><span>★</span><div><b>Equipo clínico</b><small>Atención según evaluación</small></div></div>
          </div>
        </section>

        <section class="quick-care">
          <div><span>🚨</span><b>¿Tienes una emergencia dental?</b><p>Dolor intenso, inflamación, sangrado o traumatismo.</p></div>
          <a href="#contacto">Solicitar atención prioritaria →</a>
        </section>

        <section class="section" id="servicios">
          <div class="section-heading"><span>Especialidades</span><h2>Todo lo que tu salud bucal necesita</h2><p>Atención integral para niños, jóvenes, adultos y adultos mayores.</p></div>
          <div class="service-grid">
            @for (service of services; track service.title) {
              <article><div class="service-icon">{{ service.icon }}</div><h3>{{ service.title }}</h3><p>{{ service.text }}</p><a href="#contacto">Conocer más →</a></article>
            }
          </div>
        </section>

        <section class="section about" id="nosotros">
          <div class="about-visual"><div class="clinic-room">🪥<span>Atención odontológica<br>en La Floresta</span></div><div class="experience"><b>La Floresta</b><small>Lima, Perú</small></div></div>
          <div class="about-copy"><span class="section-label">Nuestra clínica</span><h2>Atención odontológica cercana y organizada</h2><p>La evaluación, el plan propuesto, las citas y el seguimiento se registran de forma ordenada para que el paciente conozca cada etapa de su atención.</p><ul><li>Evaluación a cargo del equipo clínico asignado.</li><li>Registro seguro de la historia y evolución clínica.</li><li>Presupuestos y tratamientos claramente documentados.</li><li>Seguimiento coordinado desde recepción.</li></ul><a class="primary" href="#doctores">Conoce cómo atendemos</a></div>
        </section>

        <section class="section doctors" id="doctores">
          <div class="section-heading"><span>Equipo profesional</span><h2>Especialistas comprometidos contigo</h2></div>
          <div class="doctor-grid">
            @for (doctor of doctors; track doctor.name) {
              <article><div class="doctor-photo">{{ doctor.initials }}</div><div><small>{{ doctor.specialty }}</small><h3>{{ doctor.name }}</h3><p>{{ doctor.description }}</p><span class="availability">● Horarios disponibles</span></div></article>
            }
          </div>
        </section>

        <section class="technology" id="tecnologia"><div><span>Gestión de la atención</span><h2>Información clara durante todo el proceso</h2><p>La plataforma ayuda a organizar evaluaciones, planes de tratamiento, citas, odontogramas, documentos y seguimiento del paciente.</p></div><div class="tech-pills"><span>Historia clínica organizada</span><span>Odontograma digital</span><span>Plan de tratamiento</span><span>Agenda coordinada</span><span>Seguimiento seguro</span></div></section>

        <section class="section testimonials"><div class="section-heading"><span>Proceso de atención</span><h2>Un recorrido sencillo para cada paciente</h2></div><div class="testimonial-grid"><article><b>1. Solicita una evaluación</b><p>Envía tus datos y recepción se comunicará para coordinar la disponibilidad.</p></article><article><b>2. Recibe un plan claro</b><p>El equipo clínico registra la evaluación y explica las alternativas correspondientes.</p></article><article><b>3. Continúa tu seguimiento</b><p>Las citas, avances y pagos quedan organizados dentro de la historia del paciente.</p></article></div></section>

        <section class="contact" id="contacto"><div><span>Reserva tu evaluación</span><h2>Comienza hoy a cuidar tu sonrisa</h2><p>La recepción de {{ clinic.shortName() }} confirmará tu solicitud y te indicará los horarios disponibles.</p><div class="contact-data"><b>📍 {{ clinic.config().address }}</b>@if(clinic.config().phone){<b>☎ {{ clinic.config().phone }}</b>}@if(clinic.config().email){<b>✉ {{ clinic.config().email }}</b>}<b>🕘 {{ clinic.config().workingHours }}</b></div></div><form (ngSubmit)="sendInquiry()"><label>Nombre completo<input [(ngModel)]="inquiry.fullName" name="fullName" placeholder="Escribe tu nombre" required></label><label>Teléfono<input [(ngModel)]="inquiry.phone" name="phone" inputmode="numeric" maxlength="9" pattern="[0-9]{9}" placeholder="999999999" required></label><label>Motivo de consulta<select [(ngModel)]="inquiry.specialty" name="specialty" required><option>Evaluación general</option><option>Dolor o molestia dental</option><option>Consulta sobre tratamiento</option><option>Control odontológico</option><option>Atención prioritaria</option></select></label><label>Mensaje<textarea [(ngModel)]="inquiry.message" name="message" placeholder="Cuéntanos brevemente qué necesitas"></textarea></label><button [disabled]="sending()">{{sending() ? 'Enviando...' : 'Solicitar cita'}}</button>@if(inquiryMessage()){<small [class.error-text]="inquiryError()">{{inquiryMessage()}}</small>}</form></section>
        <section class="legal-section" id="privacidad"><article><h3>Privacidad</h3><p>Los datos enviados se utilizan para gestionar la atención solicitada y quedan restringidos al personal autorizado.</p></article><article id="terminos"><h3>Términos de atención</h3><p>La solicitud web no confirma una cita hasta que recepción valide la disponibilidad del doctor y del sillón.</p></article><article id="reclamaciones"><h3>Libro de reclamaciones</h3><p>Recepción registra reclamos y entrega el código de seguimiento dentro del módulo de satisfacción.</p><a href="#contacto">Enviar una solicitud →</a></article></section>
      </main>
      <footer><div class="public-brand"><img [src]="clinic.logoUrl()" [alt]="'Logo ' + clinic.name()"><div><b>{{ clinic.name() }}</b><small>{{ clinic.config().tagline }}</small></div></div><p>© 2026 {{ clinic.name() }}. Plataforma de gestión odontológica.</p><div><a href="#privacidad">Privacidad</a><a href="#terminos">Términos</a><a href="#reclamaciones">Libro de reclamaciones</a></div></footer>
    </div>
  `,
  styles: [`
    .public-site{background:#fbfdfd;color:#143d40}.public-nav{height:78px;display:flex;align-items:center;gap:30px;justify-content:space-between;padding:0 max(4vw,24px);background:rgba(255,255,255,.94);backdrop-filter:blur(16px);position:sticky;top:0;z-index:20;border-bottom:1px solid #e8f0f0}.public-brand{display:flex;gap:10px;align-items:center;color:#203B4A}.public-brand>img{width:42px;height:42px;object-fit:contain}.public-brand b,.public-brand small{display:block}.public-brand small{font-size:10px;letter-spacing:.7px;color:#648184;text-transform:uppercase}.public-nav nav{display:flex;gap:24px}.public-nav nav a{color:#49696c;font-size:13px}.public-actions{display:flex;gap:10px}.appointment,.login-link,.primary,.secondary{padding:11px 17px;border-radius:10px;font-size:13px;font-weight:650}.appointment,.primary{background:var(--brand-orange);color:white;box-shadow:0 10px 25px rgba(208,107,79,.22)}.login-link,.secondary{border:1px solid #cddede;color:#285a5e;background:#fff}.hero{min-height:650px;padding:80px max(6vw,28px);display:grid;grid-template-columns:1.05fr .95fr;align-items:center;gap:60px;background:radial-gradient(circle at 85% 20%,#e8f0f6 0,transparent 30%),linear-gradient(135deg,#fff,#f5f7f8)}.eyebrow,.section-label{color:var(--brand-blue);font-weight:700;text-transform:uppercase;font-size:11px;letter-spacing:1.4px}.hero h1{font-size:clamp(42px,5.4vw,76px);line-height:1.02;letter-spacing:-3px;margin:20px 0;color:#203B4A}.hero h1 span{color:var(--brand-orange)}.hero-copy>p{font-size:17px;line-height:1.75;color:#587578;max-width:650px}.hero-buttons{display:flex;gap:12px;margin:30px 0}.hero-trust{display:flex;gap:34px;border-top:1px solid #dceaea;padding-top:25px}.hero-trust b,.hero-trust small{display:block}.hero-trust b{font-size:21px}.hero-trust small{font-size:11px;color:#718b8d;margin-top:4px}.hero-visual{min-height:480px;position:relative;display:grid;place-items:center}.tooth-card{width:320px;height:390px;border-radius:150px 150px 36px 36px;background:linear-gradient(150deg,var(--brand-blue),var(--brand-blue-dark));color:white;display:flex;flex-direction:column;align-items:center;justify-content:center;box-shadow:0 40px 80px rgba(7,75,81,.28);z-index:2}.tooth-card img{width:150px;height:150px;object-fit:contain;background:#fff;border-radius:36px;padding:8px;box-shadow:0 18px 32px rgba(0,0,0,.15)}.tooth-card span{font-size:110px;filter:drop-shadow(0 18px 20px rgba(0,0,0,.18))}.tooth-card b{font-size:22px;margin-top:22px}.tooth-card small{color:#bfe5e5;margin-top:8px}.visual-orbit{position:absolute;border:1px solid rgba(8,116,123,.18);border-radius:50%}.orbit-one{width:440px;height:440px}.orbit-two{width:540px;height:540px}.floating-card{position:absolute;background:#fff;border-radius:14px;padding:14px 17px;display:flex;align-items:center;gap:11px;box-shadow:0 18px 40px rgba(31,80,83,.15);z-index:4}.floating-card>span{width:34px;height:34px;border-radius:10px;display:grid;place-items:center;background:#e2f5f4;color:var(--brand-blue);font-weight:800}.floating-card b,.floating-card small{display:block}.floating-card b{font-size:13px}.floating-card small{font-size:10px;color:#739092;margin-top:3px}.card-a{left:0;top:85px}.card-b{right:-10px;bottom:75px}.quick-care{margin:-24px auto 20px;max-width:1180px;background:#fff;border-radius:18px;box-shadow:0 18px 55px rgba(29,81,84,.12);padding:22px 28px;display:flex;justify-content:space-between;align-items:center;position:relative;z-index:4}.quick-care>div{display:grid;grid-template-columns:auto 1fr;column-gap:12px}.quick-care span{font-size:27px;grid-row:1/3}.quick-care p{margin:4px 0;color:#6c8587;font-size:12px}.quick-care a{color:var(--brand-blue);font-weight:700}.section{padding:90px max(6vw,28px)}.section-heading{text-align:center;max-width:720px;margin:0 auto 44px}.section-heading span,.technology span,.contact span{color:var(--brand-blue);text-transform:uppercase;font-weight:800;font-size:11px;letter-spacing:1.5px}.section-heading h2,.about h2,.technology h2,.contact h2{font-size:clamp(30px,3.5vw,48px);line-height:1.15;letter-spacing:-1.5px;margin:13px 0}.section-heading p{color:#6a8587}.service-grid{display:grid;grid-template-columns:repeat(5,1fr);gap:16px}.service-grid article{background:#fff;border:1px solid #e3eded;border-radius:16px;padding:23px;min-height:220px;transition:.25s}.service-grid article:hover{transform:translateY(-6px);box-shadow:0 20px 45px rgba(20,76,80,.1);border-color:#b8dedd}.service-icon{width:46px;height:46px;display:grid;place-items:center;border-radius:13px;background:#e7f6f5;font-size:22px}.service-grid h3{font-size:16px;margin:20px 0 10px}.service-grid p{font-size:12px;color:#718a8c;line-height:1.6}.service-grid a{font-size:11px;font-weight:700;color:var(--brand-blue)}.about{display:grid;grid-template-columns:1fr 1fr;gap:80px;align-items:center;background:#eff8f7}.about-visual{position:relative;min-height:480px;display:grid;place-items:center}.clinic-room{width:82%;height:420px;background:linear-gradient(145deg,var(--brand-blue),var(--brand-blue-dark));border-radius:30px 110px 30px 30px;color:#fff;font-size:100px;display:flex;flex-direction:column;align-items:center;justify-content:center;box-shadow:0 35px 70px rgba(8,75,81,.25)}.clinic-room span{font-size:18px;text-align:center;margin-top:20px}.experience{position:absolute;right:0;bottom:20px;background:#fff;border-radius:18px;padding:22px;box-shadow:0 20px 45px rgba(22,73,77,.16)}.experience b,.experience small{display:block}.experience b{font-size:26px;color:var(--brand-blue)}.experience small{color:#6b8587}.about-copy>p{color:#638083;line-height:1.75}.about-copy ul{padding:0;list-style:none;margin:25px 0 32px}.about-copy li{padding:8px 0;color:#375c5f}.about-copy li::before{content:'✓';color:var(--brand-blue);font-weight:900;margin-right:10px}.doctor-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:20px}.doctor-grid article{display:grid;grid-template-columns:120px 1fr;gap:20px;background:#fff;border:1px solid #e2eded;padding:18px;border-radius:18px}.doctor-photo{height:150px;border-radius:14px;background:linear-gradient(145deg,#cdecea,#74c4c4);display:grid;place-items:center;color:#07555b;font-size:30px;font-weight:800}.doctor-grid small{color:var(--brand-blue);text-transform:uppercase;font-size:10px;font-weight:700}.doctor-grid h3{margin:6px 0}.doctor-grid p{font-size:12px;color:#6a8587;line-height:1.55}.availability{font-size:10px;color:#2f8467;font-weight:700}.technology{background:var(--brand-blue-dark);color:#fff;margin:0 max(3vw,12px);border-radius:30px;padding:70px max(5vw,24px);display:grid;grid-template-columns:1fr 1fr;gap:60px;align-items:center}.technology p{color:#b6d2d3;line-height:1.7}.tech-pills{display:flex;flex-wrap:wrap;gap:12px}.tech-pills span{padding:13px 16px;border:1px solid rgba(255,255,255,.18);border-radius:99px;color:#dffafa;background:rgba(255,255,255,.05);letter-spacing:0;text-transform:none;font-weight:500}.testimonial-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:20px}.testimonial-grid article{margin:0;background:#fff;border:1px solid #e4eeee;border-radius:16px;padding:28px;font-size:14px;line-height:1.7;color:#49696b}.testimonial-grid b{display:block;color:#153f42}.testimonial-grid p{font-size:12px;margin:12px 0 0;color:#6a8587}.contact{margin:60px max(4vw,20px);border-radius:28px;padding:70px max(5vw,28px);background:linear-gradient(135deg,var(--brand-blue),var(--clinic-sidebar-end));color:white;display:grid;grid-template-columns:1fr .8fr;gap:70px}.contact p{color:#c3e0e0}.contact-data{display:flex;flex-direction:column;gap:13px;margin-top:28px;font-size:13px}.contact form{background:#fff;color:#244b4e;border-radius:18px;padding:24px;display:grid;gap:13px}.contact label{font-size:11px;font-weight:700}.contact input,.contact select,.contact textarea{display:block;width:100%;box-sizing:border-box;margin-top:6px;padding:12px;border:1px solid #d9e6e7;border-radius:9px}.contact textarea{min-height:76px;resize:vertical}.contact button{border:0;background:var(--brand-blue);color:#fff;padding:13px;border-radius:9px;font-weight:700}.contact form small{color:#2f7b5d}.contact form small.error-text{color:#b94a48}.legal-section{padding:30px max(6vw,28px) 70px;display:grid;grid-template-columns:repeat(3,1fr);gap:16px}.legal-section article{background:#fff;border:1px solid #e0ebeb;border-radius:14px;padding:20px}.legal-section h3{font-size:14px;margin:0 0 9px}.legal-section p,.legal-section a{font-size:10px;line-height:1.6;color:#6a8587}.legal-section a{color:var(--brand-blue);font-weight:700}footer{padding:30px max(5vw,24px);display:flex;align-items:center;justify-content:space-between;border-top:1px solid #e3eded;font-size:11px;color:#71898b}footer div:last-child{display:flex;gap:18px}footer a{color:#45676a}@media(max-width:1100px){.legal-section{grid-template-columns:1fr}.public-nav nav{display:none}.service-grid{grid-template-columns:repeat(2,1fr)}.hero{grid-template-columns:1fr}.hero-visual{display:none}.doctor-grid{grid-template-columns:1fr}.about,.technology,.contact{grid-template-columns:1fr}.about-visual{min-height:360px}.clinic-room{height:330px}.testimonial-grid{grid-template-columns:1fr}}@media(max-width:650px){.public-actions .appointment{display:none}.hero{padding-top:55px}.hero h1{letter-spacing:-2px}.hero-trust{gap:16px}.hero-trust small{max-width:80px}.quick-care{margin:0 14px;align-items:flex-start;gap:12px;flex-direction:column}.service-grid{grid-template-columns:1fr}.section{padding:65px 20px}.about{gap:35px}.technology{padding:45px 24px}.contact{padding:45px 22px}.contact-data{font-size:11px}footer{flex-direction:column;gap:20px;text-align:center}footer div:last-child{flex-wrap:wrap;justify-content:center}}
  `]
})
export class HomeComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  readonly clinic = inject(ClinicConfigService);
  readonly sending = signal(false);
  readonly inquiryMessage = signal('');
  readonly inquiryError = signal(false);
  inquiry = { fullName:'', phone:'', specialty:'Evaluación general', message:'' };

  ngOnInit(): void {
    const slug = this.route.snapshot.paramMap.get('clinicSlug');
    if (slug) {
      this.clinic.loadPublicBySlug(slug).subscribe({ error: () => this.inquiryMessage.set('La marca solicitada no está disponible.') });
      return;
    }
    this.clinic.resolveCurrentDomain()?.subscribe({ error: () => undefined });
  }

  sendInquiry(): void {
    this.sending.set(true); this.inquiryMessage.set(''); this.inquiryError.set(false);
    this.api.post('/public/inquiries', this.inquiry).subscribe({
      next: () => {
        this.sending.set(false);
        this.inquiryMessage.set('Solicitud registrada. Recepción podrá verla y contactarte.');
        this.inquiry = { fullName:'', phone:'', specialty:'Evaluación general', message:'' };
      },
      error: error => {
        this.sending.set(false); this.inquiryError.set(true);
        this.inquiryMessage.set(error?.error?.message || 'No se pudo registrar la solicitud. Revisa el teléfono e inténtalo nuevamente.');
      }
    });
  }

  readonly services = [
    {icon:'🦷',title:'Odontología general',text:'Evaluación, prevención, restauraciones y cuidado integral.'},
    {icon:'✨',title:'Estética dental',text:'Blanqueamiento, carillas y diseño de sonrisa personalizado.'},
    {icon:'⌁',title:'Ortodoncia',text:'Alineación dental con alternativas modernas para cada edad.'},
    {icon:'⚙',title:'Implantología',text:'Reemplazo seguro de piezas ausentes y rehabilitación funcional.'},
    {icon:'◉',title:'Endodoncia',text:'Tratamiento especializado para conservar piezas comprometidas.'},
    {icon:'♨',title:'Periodoncia',text:'Prevención y tratamiento de enfermedades de encías.'},
    {icon:'☻',title:'Odontopediatría',text:'Experiencias positivas y prevención desde la infancia.'},
    {icon:'✚',title:'Cirugía oral',text:'Procedimientos planificados con control y seguimiento.'},
    {icon:'▤',title:'Rehabilitación oral',text:'Recuperación estética y funcional de la sonrisa.'},
    {icon:'▣',title:'Radiología digital',text:'Imágenes de apoyo para diagnósticos más precisos.'}
  ];
  readonly doctors = [
    {initials:'ER',name:'Equipo de rehabilitación',specialty:'Rehabilitación oral',description:'Planificación integral, estética e implantoprótesis.'},
    {initials:'EE',name:'Equipo de endodoncia',specialty:'Endodoncia',description:'Manejo especializado del dolor y conservación dental.'},
    {initials:'EO',name:'Equipo de odontopediatría',specialty:'Odontopediatría',description:'Prevención y atención adaptada para niños y adolescentes.'}
  ];
}
