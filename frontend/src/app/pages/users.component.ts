import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { UserRole } from '../core/models';
import { AuthService } from '../core/auth.service';

interface SecurityAudit { id:number; occurredAt:string; actor:string; action:string; userId:number; previousRole:string|null; newRole:string|null; }
type RecoveryChannel='SMS'|'WHATSAPP';
interface ManagedUser { id:number; fullName:string; email:string; dni?:string|null; phone?:string|null; recoveryChannel?:RecoveryChannel|null; role:UserRole; clinicId:string; platformAdmin:boolean; enabled:boolean; mustResetPassword:boolean; lockedUntil?:string|null; lastLoginAt?:string|null; passwordChangedAt?:string|null; }
interface DoctorProfile { id:number; fullName:string; accountEmail?:string|null; specialty:string; branch:string; shiftStart:string; shiftEnd:string; active:boolean; }

@Component({
  selector:'app-users', standalone:true, imports:[FormsModule,DatePipe],
  template:`
    <section class="page-head"><div><span>Seguridad y acceso · {{auth.user()?.clinic?.name}}</span><h1>Usuarios de esta clínica</h1><p>Las cuentas creadas aquí pertenecen únicamente a {{auth.user()?.clinic?.shortName}}. El DNI se registra como clave temporal, pero el usuario debe activar su acceso con “Olvidé mi contraseña”.</p></div><button class="primary-btn" (click)="openNew()">＋ Nuevo usuario</button></section>
    @if(message()){<div class="msg" [class.error]="messageType()==='error'">{{message()}}</div>}
    <section class="flow"><div><b>1</b><span>ADMIN registra correo real, DNI y teléfono</span></div><i>→</i><div><b>2</b><span>DNI = clave temporal (no inicia sesión)</span></div><i>→</i><div><b>3</b><span>Usuario pulsa “Olvidé mi contraseña”</span></div><i>→</i><div><b>4</b><span>Código SMS/WhatsApp + nueva clave</span></div></section>
    @if(formOpen()){
      <section class="panel-card form-card"><div class="card-title"><div><span>{{editingId()?'Edición':'Alta de usuario'}}</span><h2>{{editingId()?'Actualizar cuenta':'Crear cuenta'}}</h2></div><button class="close" (click)="closeForm()">×</button></div>
      <form (ngSubmit)="save()" class="form-grid">
        <label>Nombre completo<input [(ngModel)]="draft.fullName" name="fullName" required maxlength="120"></label>
        <label>Correo electrónico real<input type="email" [(ngModel)]="draft.email" name="email" required maxlength="160" placeholder="persona@correo.com" [readonly]="!!editingId()"><small>{{editingId()?'El correo es la identidad histórica de la cuenta y no se modifica.':'Se usará para iniciar sesión y recuperar el acceso.'}}</small></label>
        <label>DNI<input [(ngModel)]="draft.dni" name="dni" [required]="draft.role!=='ADMIN'" maxlength="8" pattern="[0-9]{8}" inputmode="numeric"><small>{{draft.role==='ADMIN'?'Opcional para el administrador de clínica.':'Se registra como clave temporal; no permite iniciar sesión antes del OTP.'}}</small></label>
        <label>Rol<select [(ngModel)]="draft.role" name="role" required>@if(auth.isPlatformAdmin()){<option value="ADMIN">Administrador de clínica</option>}<option value="DOCTOR">Doctor</option><option value="RECEPCION">Recepción</option><option value="FINANZAS">Finanzas</option></select><small>@if(auth.isPlatformAdmin()){El administrador quedará asignado a la clínica seleccionada.}</small></label>
        <label>Teléfono de recuperación<input [(ngModel)]="draft.phone" name="phone" required placeholder="+51987654321"><small>Para Perú puedes escribir 9XXXXXXXX; se normaliza a +51.</small></label>
        <label>Enviar código por<select [(ngModel)]="draft.recoveryChannel" name="recoveryChannel" required><option value="SMS">SMS</option><option value="WHATSAPP">WhatsApp</option></select></label>
        @if(draft.role==='DOCTOR'){
          <label>Especialidad<input [(ngModel)]="draft.doctorSpecialty" name="doctorSpecialty" required placeholder="Ej. Endodoncia"></label>
          <label>Sede<input [(ngModel)]="draft.doctorBranch" name="doctorBranch" required placeholder="Sede principal"></label>
          <label>Horario inicio<input type="time" [(ngModel)]="draft.shiftStart" name="shiftStart" required min="08:00" max="22:00"></label>
          <label>Horario fin<input type="time" [(ngModel)]="draft.shiftEnd" name="shiftEnd" required min="08:00" max="22:00"><small>El perfil de Agenda se crea/sincroniza automáticamente.</small></label>
        }
        @if(editingId()){<label class="switch"><input type="checkbox" [(ngModel)]="draft.enabled" name="enabled" [disabled]="draft.role==='ADMIN'"> Cuenta habilitada</label>}
        @if(!editingId()){<div class="initial-info"><b>No debes inventar una contraseña.</b><span>El backend registra automáticamente el DNI como clave temporal, pero bloquea el inicio de sesión hasta que el usuario use “Olvidé mi contraseña”, verifique el código y cree su clave personal.</span></div>}
        <div class="actions"><button type="button" class="secondary-btn" (click)="closeForm()">Cancelar</button><button class="primary-btn" [disabled]="saving()">{{saving()?'Guardando...':'Guardar usuario'}}</button></div>
      </form></section>
    }
    <section class="panel-card"><div class="toolbar"><div class="search"><input [(ngModel)]="query" placeholder="Buscar por nombre, correo, DNI o rol"></div><button class="secondary-btn" (click)="load()">Actualizar</button></div>
      <div class="data-table-wrap"><table class="data-table"><thead><tr><th>Usuario</th><th>Identificación</th><th>Recuperación</th><th>Rol</th><th>Acceso</th><th>Acciones</th></tr></thead><tbody>
      @for(u of filtered();track u.id){<tr><td><b>{{u.fullName}}</b><small>{{u.email}}</small></td><td>{{u.dni||'—'}}</td><td><b>{{u.phone||'—'}}</b><small>{{channelLabel(u.recoveryChannel)}}</small></td><td><span class="role">{{roleLabel(u.role)}}</span></td><td><span class="status" [class.warn]="u.mustResetPassword" [class.off]="!u.enabled">{{!u.enabled?'BLOQUEADO':u.mustResetPassword?'CAMBIO DE CLAVE PENDIENTE':'HABILITADO'}}</span></td><td><div class="row-actions"><button (click)="edit(u)">Editar</button>@if(u.role!=='ADMIN'){<button (click)="toggle(u)">{{u.enabled?'Bloquear':'Habilitar'}}</button>@if(u.enabled){<button (click)="reissue(u)">Reemitir acceso</button>}}</div></td></tr>}
      </tbody></table></div>
    </section>
    <section class="panel-card"><div class="toolbar"><div><h2>Bitácora de seguridad</h2><p>Últimos 100 eventos de esta clínica. Horario local del navegador.</p></div><button class="secondary-btn" (click)="loadAudit()">Actualizar bitácora</button></div>
      @if(auditError()){<p role="alert">{{auditError()}}</p>}
      <div class="data-table-wrap"><table class="data-table"><thead><tr><th>Fecha</th><th>Actor</th><th>Evento</th><th>Usuario</th><th>Rol anterior</th><th>Rol nuevo</th></tr></thead><tbody>
      @for(event of auditEntries();track event.id){<tr><td>{{event.occurredAt | date:'dd/MM/yyyy HH:mm:ss'}}</td><td>{{event.actor}}</td><td>{{event.action}}</td><td>{{event.userId}}</td><td>{{event.previousRole||'—'}}</td><td>{{event.newRole||'—'}}</td></tr>}
      @empty{<tr><td colspan="6">No hay eventos cargados. Actualiza la bitácora para consultar el registro.</td></tr>}
      </tbody></table></div>
    </section>
  `,
  styles:[`
    .msg{padding:11px 14px;background:#e8f7ee;border:1px solid #cbe9d8;color:#2d7253;border-radius:10px;margin-bottom:14px;font-size:11px}.msg.error{background:#fff0f0;border-color:#efcaca;color:#a33}.flow{display:grid;grid-template-columns:1fr auto 1fr auto 1fr auto 1fr;align-items:center;gap:8px;margin-bottom:18px}.flow>div{background:#fff;border:1px solid #dfeaea;border-radius:12px;padding:12px;display:flex;gap:8px;align-items:center}.flow b{width:28px;height:28px;border-radius:9px;background:#e4f2f7;color:#3B78A6;display:grid;place-items:center}.flow span{font-size:9px;color:#587477}.flow i{font-style:normal;color:#98abad}.form-card{margin-bottom:18px}.form-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:14px}.form-grid label{font-size:10px;font-weight:700;color:#48696b}.form-grid input,.form-grid select{display:block;width:100%;box-sizing:border-box;border:1px solid #dce7e8;border-radius:9px;padding:11px;margin-top:6px}.form-grid small{display:block;margin-top:4px;color:#789092}.switch{display:flex!important;align-items:center;gap:8px;margin-top:22px}.switch input{width:auto!important;margin:0!important}.initial-info{padding:11px;border-radius:10px;background:#fff7e9;border:1px solid #f0dfb7;align-self:end}.initial-info b,.initial-info span{display:block}.initial-info b{font-size:9px;color:#765b25}.initial-info span{font-size:8px;color:#806f49;margin-top:3px;line-height:1.5}.actions{grid-column:1/-1;display:flex;justify-content:flex-end;gap:8px}.close{border:1px solid #dce7e8;background:#fff;border-radius:8px;width:32px;height:32px}.toolbar{display:flex;justify-content:space-between;gap:12px;margin-bottom:16px}.search{flex:1}.search input{width:100%;border:1px solid #dce7e8;border-radius:9px;padding:10px}.role,.status{font-size:8px;border-radius:99px;padding:6px 9px;background:#e6f5f4;color:#18747a}.status{background:#e8f7ee;color:#347556}.status.warn{background:#fff3d5;color:#866621}.status.off{background:#f5eeee;color:#9a4d4d}.row-actions{display:flex;gap:6px;flex-wrap:wrap}.row-actions button{border:1px solid #dce7e8;background:#fff;border-radius:7px;padding:6px 8px;font-size:8px}.data-table td small,.data-table td b{display:block}.data-table td small{font-size:8px;color:#789092;margin-top:3px}@media(max-width:850px){.flow{grid-template-columns:1fr}.flow i{display:none}}@media(max-width:650px){.form-grid{grid-template-columns:1fr}.actions{grid-column:auto}}
  `]
})
export class UsersComponent implements OnInit{
  private api=inject(ApiService);
  readonly auth=inject(AuthService);
  readonly users=signal<ManagedUser[]>([]);readonly doctorProfiles=signal<DoctorProfile[]>([]);readonly formOpen=signal(false);readonly editingId=signal<number|null>(null);readonly saving=signal(false);readonly message=signal('');readonly messageType=signal<'success'|'error'>('success');
  readonly auditEntries=signal<SecurityAudit[]>([]); readonly auditError=signal('');
  loadAudit(){this.auditError.set('');this.api.get<SecurityAudit[]>('/users/audit').subscribe({next:v=>this.auditEntries.set(v),error:()=>{this.auditEntries.set([]);this.auditError.set('No se pudo consultar la bitácora.');}});}
  query='';draft:any=this.empty();
  ngOnInit(){this.load();this.loadAudit();}
  load(){this.api.get<ManagedUser[]>('/users').subscribe({next:v=>this.users.set(v),error:e=>this.notify(e?.error?.detail||e?.error?.message||'No se pudieron cargar los usuarios.','error')});this.api.get<DoctorProfile[]>('/scheduling/doctors').subscribe({next:v=>this.doctorProfiles.set(v),error:()=>this.doctorProfiles.set([])});}
  filtered(){const q=this.query.trim().toLowerCase();return this.users().filter(u=>!q||`${u.fullName} ${u.email} ${u.dni||''} ${u.role}`.toLowerCase().includes(q));}
  openNew(){this.editingId.set(null);this.draft=this.empty();this.formOpen.set(true);}
  edit(u:ManagedUser){const dp=this.doctorProfiles().find(d=>d.accountEmail?.toLowerCase()===u.email.toLowerCase());this.editingId.set(u.id);this.draft={fullName:u.fullName,email:u.email,dni:u.dni||'',phone:u.phone||'',recoveryChannel:u.recoveryChannel||'SMS',role:u.role,enabled:u.enabled,doctorSpecialty:dp?.specialty||'',doctorBranch:dp?.branch||'Sede principal',shiftStart:(dp?.shiftStart||'08:00').slice(0,5),shiftEnd:(dp?.shiftEnd||'18:00').slice(0,5)};this.formOpen.set(true);window.scrollTo({top:0,behavior:'smooth'});}
  closeForm(){this.formOpen.set(false);this.editingId.set(null);this.draft=this.empty();}
  save(){this.saving.set(true);const req=this.editingId()?this.api.put<ManagedUser>(`/users/${this.editingId()}`,this.draft):this.api.post<ManagedUser>('/users',this.draft);req.subscribe({next:()=>{const isNew=!this.editingId();this.saving.set(false);this.closeForm();this.notify(isNew?'Usuario creado. El DNI queda como clave temporal de seguridad, pero no abre sesión. Indícale que use “Olvidé mi contraseña” para recibir su código y crear una contraseña personal.':'Usuario actualizado correctamente.');this.load();},error:e=>{this.saving.set(false);this.notify(e?.error?.detail||e?.error?.message||'No se pudo guardar el usuario.','error')}});}
  toggle(u:ManagedUser){this.api.patch<ManagedUser>(`/users/${u.id}/enabled?value=${!u.enabled}`).subscribe({next:v=>{Object.assign(u,v);this.notify('Estado de la cuenta actualizado.');},error:e=>this.notify(e?.error?.detail||e?.error?.message||'No se pudo cambiar el estado.','error')});}
  reissue(u:ManagedUser){if(!confirm(`¿Reemitir acceso inicial para ${u.fullName}? La contraseña temporal volverá a ser su DNI y deberá verificar un nuevo código.`))return;this.api.post<void>(`/users/${u.id}/reissue-initial-access`,{}).subscribe({next:()=>{this.notify('Acceso inicial reemitido. El usuario debe usar “Olvidé mi contraseña”.');this.load();},error:e=>this.notify(e?.error?.detail||e?.error?.message||'No se pudo reemitir el acceso.','error')});}
  roleLabel(role:UserRole){return({ADMIN:'Administrador',DOCTOR:'Doctor',RECEPCION:'Recepción',FINANZAS:'Finanzas'} as Record<UserRole,string>)[role];}
  channelLabel(v?:RecoveryChannel|null){return v==='WHATSAPP'?'Código por WhatsApp':v==='SMS'?'Código por SMS':'Sin recuperación configurada';}
  private empty(){return{fullName:'',email:'',dni:'',phone:'+51',recoveryChannel:'SMS' as RecoveryChannel,role:'RECEPCION' as UserRole,enabled:true,doctorSpecialty:'',doctorBranch:'Sede principal',shiftStart:'08:00',shiftEnd:'18:00'};}
  private notify(t:string,type:'success'|'error'='success'){this.message.set(t);this.messageType.set(type);}
}

