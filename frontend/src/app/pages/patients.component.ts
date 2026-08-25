import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';

interface Patient {
  id?: number; fullName: string; dni: string; birthDate: string; district: string; address: string;
  phone: string; email: string; occupation: string; emergencyContact: string; emergencyPhone: string;
  currentCondition: string; familyHistory: string; allergies: string; status?: string;
}

@Component({
  selector: 'app-patients', standalone: true, imports: [FormsModule, RouterLink],
  template: `
    <section class="page-head"><div><span>Atención integral</span><h1>Gestión de pacientes</h1><p>Registro administrativo, datos de contacto, antecedentes y búsqueda centralizada.</p></div>@if (canEdit()) {<button class="primary-btn" (click)="newPatient()">＋ Registrar paciente</button>}</section>
    @if(message()){<div class="inline-message" [class.error]="messageType()==='error'">{{message()}}</div>}
    @if (showForm()) {
      <section class="panel-card form-panel"><div class="card-title"><div><span>{{editingId() ? 'Editar ficha' : 'Nuevo registro'}}</span><h2>Datos del paciente</h2></div><button class="close-btn" (click)="closeForm()">×</button></div>
        <form class="form-grid" (ngSubmit)="save()">
          <label class="span-2">Nombres y apellidos<input name="fullName" [(ngModel)]="draft.fullName" required></label>
          <label>DNI<input name="dni" [(ngModel)]="draft.dni" maxlength="8" minlength="8" pattern="[0-9]{8}" required></label>
          <label>Fecha de nacimiento<input type="date" name="birthDate" [(ngModel)]="draft.birthDate"></label>
          <label>Distrito<input name="district" [(ngModel)]="draft.district"></label>
          <label class="span-2">Dirección<input name="address" [(ngModel)]="draft.address"></label>
          <label>Teléfono<input name="phone" [(ngModel)]="draft.phone" maxlength="9" minlength="9" pattern="[0-9]{9}"></label>
          <label>Correo<input type="email" name="email" [(ngModel)]="draft.email"></label>
          <label>Ocupación<input name="occupation" [(ngModel)]="draft.occupation"></label>
          <label class="span-2">Contacto de emergencia<input name="emergencyContact" [(ngModel)]="draft.emergencyContact"></label>
          <label>Teléfono de emergencia<input name="emergencyPhone" [(ngModel)]="draft.emergencyPhone"></label>
          <label class="span-2">Enfermedad actual<textarea name="currentCondition" [(ngModel)]="draft.currentCondition"></textarea></label>
          <label class="span-2">Antecedentes familiares<textarea name="familyHistory" [(ngModel)]="draft.familyHistory"></textarea></label>
          <label class="span-2">Alergias<textarea name="allergies" [(ngModel)]="draft.allergies"></textarea></label>
          <div class="form-actions span-4"><button type="button" class="secondary-btn" (click)="closeForm()">Cancelar</button><button class="primary-btn" type="submit" [disabled]="saving()">{{saving() ? 'Guardando...' : (editingId() ? 'Actualizar paciente' : 'Guardar paciente')}}</button></div>
        </form>
      </section>
    }
    <section class="panel-card"><div class="table-toolbar"><div class="search"><span>⌕</span><input [(ngModel)]="query" placeholder="Buscar por nombre, DNI o teléfono"></div><div class="table-meta">{{ filtered().length }} pacientes</div></div>
      <div class="data-table-wrap"><table class="data-table"><thead><tr><th>Paciente</th><th>DNI</th><th>Contacto</th><th>Distrito</th><th>Antecedente relevante</th><th>Estado</th><th>Acciones</th></tr></thead><tbody>@for (p of filtered(); track p.id) {<tr><td><div class="person-cell"><span>{{ initials(p.fullName) }}</span><div><b>{{ p.fullName }}</b><small>{{ p.occupation || 'Sin ocupación registrada' }}</small></div></div></td><td>{{ p.dni }}</td><td><b>{{ p.phone || '—' }}</b><small>{{ p.email || '—' }}</small></td><td>{{ p.district || '—' }}</td><td>{{ p.allergies || p.currentCondition || 'Sin alertas registradas' }}</td><td><em class="status" [class.inactive]="p.status!=='ACTIVO'">{{ p.status }}</em></td><td><div class="row-buttons"><button (click)="selected.set(p)">Ver</button><a [routerLink]="['/app/paciente-360']" class="mini-link">360</a>@if(canEdit()){<button (click)="edit(p)">Editar</button><select [ngModel]="p.status" (ngModelChange)="changeStatus(p,$event)"><option value="ACTIVO">ACTIVO</option><option value="INACTIVO">INACTIVO</option></select>}</div></td></tr>}</tbody></table></div>
    </section>
    @if(selected();as p){<div class="drawer-backdrop" (click)="selected.set(null)"></div><aside class="detail-drawer"><div class="drawer-head"><div><span>Ficha del paciente</span><h2>{{p.fullName}}</h2></div><button (click)="selected.set(null)">×</button></div><div class="details"><div><small>DNI</small><b>{{p.dni}}</b></div><div><small>Fecha de nacimiento</small><b>{{p.birthDate||'—'}}</b></div><div><small>Dirección</small><b>{{p.district}} · {{p.address}}</b></div><div><small>Contacto</small><b>{{p.phone}} · {{p.email}}</b></div><div><small>Emergencia</small><b>{{p.emergencyContact}} · {{p.emergencyPhone}}</b></div><div><small>Enfermedad actual</small><b>{{p.currentCondition||'Sin registro'}}</b></div><div><small>Antecedentes familiares</small><b>{{p.familyHistory||'Sin registro'}}</b></div><div><small>Alergias</small><b>{{p.allergies||'Sin registro'}}</b></div></div>@if(canEdit()){<button class="primary-btn" (click)="edit(p);selected.set(null)">Editar ficha</button>}</aside>}
  `,
  styles: [`
    .inline-message{padding:11px 14px;border-radius:10px;background:#e7f7ee;color:#36795a;border:1px solid #cbead8;margin-bottom:14px;font-size:11px}.inline-message.error{background:#fff0f0;color:#a94444;border-color:#f1cccc}.form-panel{margin-bottom:18px}.form-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:14px}.form-grid label{font-size:10px;font-weight:700;color:#48696b}.form-grid input,.form-grid textarea{display:block;width:100%;box-sizing:border-box;border:1px solid #dce7e8;border-radius:9px;padding:11px;margin-top:6px;background:#fbfdfd}.form-grid textarea{min-height:78px;resize:vertical}.span-2{grid-column:span 2}.span-4{grid-column:span 4}.form-actions{display:flex;justify-content:flex-end;gap:10px}.close-btn{border:0;background:#f0f5f5;border-radius:8px;cursor:pointer;width:32px;height:32px}.table-toolbar{display:flex;justify-content:space-between;align-items:center;margin-bottom:15px}.search{width:min(440px,70%);position:relative}.search span{position:absolute;left:12px;top:9px}.search input{width:100%;box-sizing:border-box;border:1px solid #dce7e8;border-radius:9px;padding:10px 12px 10px 35px}.table-meta{font-size:10px;color:#71898b}.person-cell{display:flex;align-items:center;gap:10px}.person-cell>span{width:34px;height:34px;border-radius:10px;background:#e3f4f3;color:#3B78A6;display:grid;place-items:center;font-size:10px;font-weight:800}.person-cell b,.person-cell small,td b,td small{display:block}.person-cell small,td small{font-size:9px;color:#789092;margin-top:3px}.status{font-style:normal;font-size:9px;padding:5px 8px;border-radius:99px;background:#e6f7ed;color:#367c5a}.status.inactive{background:#f0f0f0;color:#737d7e}.row-buttons{display:flex;gap:5px;align-items:center}.row-buttons button,.row-buttons select,.mini-link{border:1px solid #dce7e8;background:#fff;border-radius:7px;padding:6px;font-size:8px;color:#4d6e71}.drawer-backdrop{position:fixed;inset:0;background:rgba(7,38,42,.35);z-index:60}.detail-drawer{position:fixed;right:0;top:0;bottom:0;width:min(460px,92vw);background:#fff;z-index:61;padding:25px;box-shadow:-20px 0 60px rgba(6,39,43,.22);overflow:auto}.drawer-head{display:flex;justify-content:space-between;border-bottom:1px solid #e3eded;padding-bottom:17px}.drawer-head span{font-size:9px;text-transform:uppercase;color:#3B78A6;font-weight:800}.drawer-head h2{font-size:20px;margin:5px 0}.drawer-head button{border:0;background:#eef4f4;border-radius:8px;width:34px;height:34px}.details{margin:10px 0 22px}.details div{padding:12px 0;border-bottom:1px solid #edf2f2}.details small,.details b{display:block}.details small{font-size:8px;text-transform:uppercase;color:#789092}.details b{font-size:11px;line-height:1.5;margin-top:4px}@media(max-width:900px){.form-grid{grid-template-columns:1fr 1fr}.span-4{grid-column:span 2}}@media(max-width:600px){.form-grid{grid-template-columns:1fr}.span-2,.span-4{grid-column:span 1}.table-toolbar{align-items:flex-start;gap:10px;flex-direction:column}.search{width:100%}}
  `]
})
export class PatientsComponent implements OnInit {
  private readonly api = inject(ApiService); readonly auth = inject(AuthService);
  readonly patients = signal<Patient[]>([]); readonly showForm = signal(false); readonly selected=signal<Patient|null>(null);
  readonly editingId=signal<number|null>(null); readonly saving=signal(false); readonly message=signal(''); readonly messageType=signal<'success'|'error'>('success'); query = '';
  draft: Patient = this.empty();
  canEdit(): boolean { return this.auth.hasRole(['ADMIN','RECEPCION']); }
  ngOnInit(): void { this.load(); }
  load(): void { this.api.get<Patient[]>('/patients').subscribe({next:v=>this.patients.set(v),error:e=>this.notify(e?.error?.message||'No se pudo cargar pacientes.','error')}); }
  filtered(): Patient[] { const q=this.query.toLowerCase().trim(); return this.patients().filter(p=>!q||[p.fullName,p.dni,p.phone].some(v=>v?.toLowerCase().includes(q))); }
  initials(name:string):string { return name.split(' ').slice(0,2).map(x=>x[0]).join('').toUpperCase(); }
  newPatient(){this.editingId.set(null);this.draft=this.empty();this.showForm.set(true);}
  edit(p:Patient){this.editingId.set(p.id||null);this.draft={...p};this.showForm.set(true);window.scrollTo({top:0,behavior:'smooth'});}
  closeForm(){this.showForm.set(false);this.editingId.set(null);this.draft=this.empty();}
  save(): void { this.saving.set(true);const request=this.editingId()?this.api.put<Patient>(`/patients/${this.editingId()}`,this.draft):this.api.post<Patient>('/patients',this.draft);request.subscribe({next:()=>{this.saving.set(false);this.closeForm();this.notify('Paciente guardado correctamente.');this.load();},error:e=>{this.saving.set(false);this.notify(e?.error?.message||'No se pudo guardar el paciente.','error')}}); }
  changeStatus(p:Patient,value:string){const prev=p.status;p.status=value;this.api.patch<Patient>(`/patients/${p.id}/status?value=${value}`).subscribe({next:v=>Object.assign(p,v),error:e=>{p.status=prev;this.notify(e?.error?.message||'No se pudo cambiar el estado.','error')}});}
  private notify(text:string,type:'success'|'error'='success'){this.message.set(text);this.messageType.set(type);}
  private empty():Patient { return {fullName:'',dni:'',birthDate:'',district:'',address:'',phone:'',email:'',occupation:'',emergencyContact:'',emergencyPhone:'',currentCondition:'',familyHistory:'',allergies:''}; }
}
