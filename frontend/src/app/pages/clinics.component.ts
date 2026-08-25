import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ClinicBrand } from '../core/models';

type ClinicDraft = Omit<ClinicBrand, 'id' | 'active'>;

@Component({
  selector: 'app-clinics',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <section class="page-head">
      <div><span>Administración de plataforma</span><h1>Clínicas y marcas</h1><p>Una sola plataforma para todas las marcas. Cada clínica conserva su identidad visual, usuarios y datos completamente separados.</p></div>
      @if(auth.isPlatformAdmin()){<button class="primary-btn" (click)="openNew()">＋ Nueva clínica</button>}
    </section>

    @if(!auth.isPlatformAdmin()){
      <section class="access-note"><b>Administración de clínica</b><p>Puedes consultar la identidad de tu clínica. Solo el superadministrador crea o modifica marcas.</p></section>
    }
    @if(message()){<div class="msg" [class.error]="messageType()==='error'">{{message()}}</div>}

    <section class="summary-grid">
      <article><span>Clínicas registradas</span><b>{{clinics().length}}</b><small>en la misma plataforma</small></article>
      <article><span>Marcas activas</span><b>{{activeCount()}}</b><small>listas para operar</small></article>
      <article><span>Clínica en uso</span><b class="current-name">{{auth.user()?.clinic?.shortName}}</b><small>los módulos muestran solo sus datos</small></article>
    </section>

    @if(formOpen()){
      <section class="panel-card editor">
        <div class="card-title"><div><span>{{editingId() ? 'Editar identidad' : 'Alta de marca'}}</span><h2>{{editingId() ? 'Actualizar clínica' : 'Crear nueva clínica'}}</h2></div><button class="close" (click)="closeForm()">×</button></div>
        <form (ngSubmit)="save()" class="clinic-form">
          <fieldset><legend>Identidad</legend>
            <label>Código interno<input [(ngModel)]="draft.code" name="code" required pattern="[A-Za-z0-9][A-Za-z0-9_-]{1,39}" [readonly]="!!editingId()" placeholder="DENTAL_NOVA"><small>No se cambia después de crear la clínica.</small></label>
            <label>Dirección pública<input [(ngModel)]="draft.slug" name="slug" required pattern="[a-z0-9][a-z0-9-]{1,59}" placeholder="dental-nova"><small>Quedará disponible en /marca/{{draft.slug || 'nombre'}}.</small></label>
            <label>Nombre completo<input [(ngModel)]="draft.name" name="name" required maxlength="120" placeholder="Clínica Dental Nova"></label>
            <label>Nombre corto<input [(ngModel)]="draft.shortName" name="shortName" required maxlength="80" placeholder="Dental Nova"></label>
            <label>Lema<input [(ngModel)]="draft.tagline" name="tagline" maxlength="160" placeholder="Sonrisas que inspiran"></label>
            <label class="wide">Descripción<textarea [(ngModel)]="draft.description" name="description" maxlength="600" placeholder="Describe la propuesta de la clínica"></textarea></label>
          </fieldset>

          <fieldset><legend>Logo y colores</legend>
            <label>URL del logo<input [(ngModel)]="draft.logoUrl" name="logoUrl" maxlength="300" placeholder="https://.../logo.png"></label>
            <label>Imagen principal<input [(ngModel)]="draft.heroImageUrl" name="heroImageUrl" maxlength="300" placeholder="https://.../fachada.jpg"></label>
            <label>Color principal<div class="color-field"><input type="color" [(ngModel)]="draft.primaryColor" name="primaryColor"><input [(ngModel)]="draft.primaryColor" name="primaryColorText" required pattern="#[0-9A-Fa-f]{6}"></div></label>
            <label>Principal oscuro<div class="color-field"><input type="color" [(ngModel)]="draft.primaryDarkColor" name="primaryDarkColor"><input [(ngModel)]="draft.primaryDarkColor" name="primaryDarkColorText" required pattern="#[0-9A-Fa-f]{6}"></div></label>
            <label>Color de acento<div class="color-field"><input type="color" [(ngModel)]="draft.accentColor" name="accentColor"><input [(ngModel)]="draft.accentColor" name="accentColorText" required pattern="#[0-9A-Fa-f]{6}"></div></label>
            <label>Acento oscuro<div class="color-field"><input type="color" [(ngModel)]="draft.accentDarkColor" name="accentDarkColor"><input [(ngModel)]="draft.accentDarkColor" name="accentDarkColorText" required pattern="#[0-9A-Fa-f]{6}"></div></label>
            <label>Barra lateral<div class="color-field"><input type="color" [(ngModel)]="draft.sidebarDarkColor" name="sidebarDarkColor"><input [(ngModel)]="draft.sidebarDarkColor" name="sidebarDarkColorText" required pattern="#[0-9A-Fa-f]{6}"></div></label>
            <div class="brand-preview" [style.background]="'linear-gradient(135deg,'+draft.primaryDarkColor+','+draft.sidebarDarkColor+')'"><span [style.background]="draft.accentColor"></span><img [src]="draft.logoUrl || '/brand/logo-cardenas.svg'" alt="Vista del logo"><b>{{draft.shortName || 'Nueva marca'}}</b></div>
          </fieldset>

          <fieldset><legend>Contacto y dominio</legend>
            <label>Teléfono<input [(ngModel)]="draft.phone" name="phone" maxlength="30" placeholder="+51 999 999 999"></label>
            <label>Correo<input type="email" [(ngModel)]="draft.email" name="email" maxlength="160" placeholder="contacto@clinica.pe"></label>
            <label class="wide">Dirección<input [(ngModel)]="draft.address" name="address" maxlength="220" placeholder="Ciudad, distrito y dirección"></label>
            <label>Horario<input [(ngModel)]="draft.workingHours" name="workingHours" maxlength="160" placeholder="Lun a sáb, 8:00 a 20:00"></label>
            <label>Dominio propio (opcional)<input [(ngModel)]="draft.customDomain" name="customDomain" maxlength="160" placeholder="www.miclinica.pe"><small>La infraestructura debe apuntar este dominio al portal.</small></label>
          </fieldset>
          <div class="actions"><button type="button" class="secondary-btn" (click)="closeForm()">Cancelar</button><button class="primary-btn" [disabled]="saving()">{{saving() ? 'Guardando...' : 'Guardar clínica'}}</button></div>
        </form>
      </section>
    }

    <section class="clinic-grid">
      @for(item of clinics(); track item.id){
        <article class="clinic-card" [class.inactive]="!item.active">
          <div class="brand-strip" [style.background]="'linear-gradient(135deg,'+item.primaryDarkColor+','+item.sidebarDarkColor+')'"><img [src]="item.logoUrl || '/brand/logo-cardenas.svg'" [alt]="'Logo '+item.name"><span [style.background]="item.accentColor"></span></div>
          <div class="clinic-body"><div class="clinic-title"><div><small>{{item.code}}</small><h2>{{item.name}}</h2></div><span class="status" [class.off]="!item.active">{{item.active ? 'ACTIVA' : 'INACTIVA'}}</span></div>
            <p>{{item.tagline || item.description}}</p>
            <dl><div><dt>URL pública</dt><dd>/marca/{{item.slug}}</dd></div><div><dt>Contacto</dt><dd>{{item.phone || item.email || 'Por completar'}}</dd></div><div><dt>Dominio</dt><dd>{{item.customDomain || 'Sin dominio propio'}}</dd></div></dl>
            <div class="card-actions"><a [routerLink]="['/marca',item.slug]" target="_blank">Ver web pública</a>@if(auth.isPlatformAdmin()){<button (click)="edit(item)">Editar</button><button (click)="toggle(item)">{{item.active ? 'Desactivar' : 'Activar'}}</button>}</div>
          </div>
        </article>
      } @empty { <div class="empty">No hay clínicas registradas.</div> }
    </section>
  `,
  styles: [`
    .access-note,.msg{padding:18px 20px;border-radius:14px;margin-bottom:20px;background:#eef7fb;border:1px solid #d7e8f0;color:#315b6d}.access-note p{margin:5px 0 0}.msg{background:#e8f7ee;border-color:#cbe9d8;color:#2d7253}.msg.error{background:#fff0f0;border-color:#efcaca;color:#a33}.summary-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:18px;margin-bottom:24px}.summary-grid article{background:#fff;border:1px solid #dfe9ea;border-radius:18px;padding:22px}.summary-grid span,.summary-grid small,.summary-grid b{display:block}.summary-grid span{font-size:12px;color:#688285}.summary-grid b{font-size:32px;margin:8px 0;color:#173d41}.summary-grid .current-name{font-size:24px}.summary-grid small{font-size:11px;color:#829799}.editor{margin-bottom:24px;padding:26px}.close{border:1px solid #d7e4e5;background:#fff;border-radius:10px;width:42px;height:42px;font-size:22px}.clinic-form{display:grid;gap:20px}.clinic-form fieldset{border:1px solid #dce8e9;border-radius:16px;padding:20px;display:grid;grid-template-columns:repeat(2,1fr);gap:16px}.clinic-form legend{padding:0 10px;font-size:13px;font-weight:900;color:var(--brand-blue)}.clinic-form label{font-size:12px;font-weight:800;color:#456568}.clinic-form input,.clinic-form textarea{display:block;width:100%;box-sizing:border-box;border:1px solid #cfdddf;border-radius:10px;padding:12px;margin-top:7px;font:inherit}.clinic-form textarea{min-height:88px;resize:vertical}.clinic-form small{display:block;color:#819698;font-size:10px;margin-top:5px}.wide{grid-column:1/-1}.color-field{display:grid;grid-template-columns:50px 1fr;gap:8px}.color-field input[type=color]{padding:3px;height:44px}.brand-preview{border-radius:13px;min-height:86px;color:#fff;display:flex;align-items:center;gap:12px;padding:12px;position:relative;overflow:hidden}.brand-preview img{width:54px;height:54px;object-fit:contain;background:#fff;border-radius:12px}.brand-preview span{position:absolute;inset:0 0 auto;height:5px}.actions{display:flex;justify-content:flex-end;gap:10px}.clinic-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:20px}.clinic-card{background:#fff;border:1px solid #dce8e9;border-radius:20px;overflow:hidden;box-shadow:0 10px 30px rgba(20,60,64,.06)}.clinic-card.inactive{opacity:.68}.brand-strip{height:108px;padding:18px;display:flex;align-items:center;position:relative}.brand-strip>img{width:72px;height:72px;border-radius:16px;background:#fff;object-fit:contain;padding:4px}.brand-strip>span{position:absolute;right:20px;top:20px;width:26px;height:26px;border-radius:50%;border:4px solid rgba(255,255,255,.65)}.clinic-body{padding:22px}.clinic-title{display:flex;justify-content:space-between;gap:14px}.clinic-title small{font-size:10px;color:#718b8d;font-weight:900}.clinic-title h2{margin:5px 0;font-size:21px}.status{font-size:9px;height:max-content;padding:7px 9px;border-radius:99px;background:#e7f6ed;color:#287151;font-weight:900}.status.off{background:#f5eded;color:#9a4d4d}.clinic-body>p{color:#678184;min-height:38px}.clinic-body dl{display:grid;gap:9px;margin:20px 0}.clinic-body dl div{display:grid;grid-template-columns:110px 1fr;gap:10px}.clinic-body dt{font-size:10px;color:#829799}.clinic-body dd{font-size:11px;margin:0;color:#385b5e;font-weight:700;word-break:break-word}.card-actions{display:flex;gap:8px;flex-wrap:wrap}.card-actions a,.card-actions button{border:1px solid #cfdfe0;background:#fff;color:#315e62;border-radius:9px;padding:9px 11px;font-size:10px;font-weight:800;cursor:pointer}.card-actions a{background:var(--brand-blue);color:#fff;border-color:var(--brand-blue)}.empty{grid-column:1/-1;text-align:center;padding:50px;background:#fff;border-radius:18px;color:#718b8d}@media(max-width:980px){.clinic-grid{grid-template-columns:1fr}}@media(max-width:700px){.summary-grid{grid-template-columns:1fr}.clinic-form fieldset{grid-template-columns:1fr}.wide{grid-column:auto}.editor{padding:17px}}
  `]
})
export class ClinicsComponent implements OnInit {
  private readonly api = inject(ApiService);
  readonly auth = inject(AuthService);
  readonly clinics = signal<ClinicBrand[]>([]);
  readonly formOpen = signal(false);
  readonly editingId = signal<number | null>(null);
  readonly saving = signal(false);
  readonly message = signal('');
  readonly messageType = signal<'success' | 'error'>('success');
  draft: ClinicDraft = this.empty();

  ngOnInit(): void { this.load(); }
  activeCount(): number { return this.clinics().filter(item => item.active).length; }
  load(): void {
    this.api.get<ClinicBrand[]>('/clinics').subscribe({
      next: data => this.clinics.set(data),
      error: error => this.notify(this.errorText(error, 'No se pudieron cargar las clínicas.'), 'error')
    });
  }
  openNew(): void { this.editingId.set(null); this.draft = this.empty(); this.formOpen.set(true); }
  edit(item: ClinicBrand): void {
    const { id: _id, active: _active, ...draft } = item;
    this.editingId.set(item.id); this.draft = { ...draft }; this.formOpen.set(true);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
  closeForm(): void { this.formOpen.set(false); this.editingId.set(null); this.draft = this.empty(); }
  save(): void {
    this.saving.set(true);
    const id = this.editingId();
    const request = id ? this.api.put<ClinicBrand>(`/clinics/${id}`, this.draft) : this.api.post<ClinicBrand>('/clinics', this.draft);
    request.subscribe({
      next: () => { this.saving.set(false); this.closeForm(); this.notify(id ? 'Marca actualizada correctamente.' : 'Clínica creada. Ya puedes seleccionarla y crear sus usuarios.'); this.load(); },
      error: error => { this.saving.set(false); this.notify(this.errorText(error, 'No se pudo guardar la clínica.'), 'error'); }
    });
  }
  toggle(item: ClinicBrand): void {
    if (!confirm(`¿${item.active ? 'Desactivar' : 'Activar'} ${item.name}?`)) return;
    this.api.patch<ClinicBrand>(`/clinics/${item.id}/active?value=${!item.active}`).subscribe({
      next: value => { Object.assign(item, value); this.notify('Estado de la clínica actualizado.'); },
      error: error => this.notify(this.errorText(error, 'No se pudo cambiar el estado.'), 'error')
    });
  }
  private empty(): ClinicDraft {
    return { code:'', slug:'', name:'', shortName:'', tagline:'Odontología especializada', description:'Atención odontológica profesional, segura y cercana.',
      logoUrl:'/brand/logo-cardenas.svg', heroImageUrl:'/brand/cardenas-fachada.png', primaryColor:'#3B78A6', primaryDarkColor:'#245C80',
      accentColor:'#D06B4F', accentDarkColor:'#B8533C', sidebarDarkColor:'#193D52', phone:'', email:'', address:'', workingHours:'Lun a sáb, 8:00 a 20:00', customDomain:null };
  }
  private notify(text: string, type: 'success' | 'error' = 'success'): void { this.message.set(text); this.messageType.set(type); }
  private errorText(error: any, fallback: string): string { return error?.error?.detail || error?.error?.message || fallback; }
}
