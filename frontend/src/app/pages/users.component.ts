import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ToastService } from '../core/toast.service';
import { AuditEntry } from '../core/models';
import { apiError, dateTime, initials } from '../core/format';
import { IconComponent } from '../shared/icon.component';
import { ModalComponent } from '../shared/modal.component';

const ROLE_TONE: Record<string, string> = { ADMIN: 'gum', RECEPCION: 'teal', ODONTOLOGO: 'blue', CAJA: 'amber', DIRECTOR: 'grey' };
const DAYS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];

const PERMISSIONS: { role: string; can: string }[] = [
  { role: 'Administrador', can: 'Todo el sistema, cuentas de usuario y horarios' },
  { role: 'Recepcionista', can: 'Pacientes, citas y agenda' },
  { role: 'Odontólogo', can: 'Su agenda, historia clínica, odontograma y plan de tratamiento' },
  { role: 'Responsable de caja', can: 'Comprobantes y envío a SUNAT' },
  { role: 'Director', can: 'Inicio, agenda y pacientes (consulta)' },
];

/** HU-27 crear, editar y desactivar cuentas · HU-25 control de bloqueos. */
@Component({
  selector: 'app-users',
  standalone: true,
  imports: [FormsModule, IconComponent, ModalComponent, RouterLink],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <h1>Personal</h1>
          <p class="sub">Cada persona entra con su propia cuenta. El <strong>puesto</strong> define su función en la clínica; el <strong>rol</strong> define qué puede ver y hacer (se configura en Roles y permisos).</p>
        </div>
        <div class="actions"><button class="btn primary" type="button" (click)="openNew()"><app-icon name="plus" [size]="16" /> Crear cuenta</button></div>
      </div>

      <div class="layout">
        <section class="panel">
          <div class="panel-head spread">
            <input class="input search" type="search" [(ngModel)]="q" placeholder="Buscar por nombre, usuario, correo o puesto" aria-label="Buscar personal" />
            <span class="muted small">{{ shown.length }} de {{ users.length }}</span>
          </div>
          <table class="table">
            <thead><tr><th>Persona</th><th>Rol</th><th>Estado</th><th>Último acceso</th><th></th></tr></thead>
            <tbody>
              @for (u of shown; track u.id) {
                <tr [class.off]="!u.active">
                  <td>
                    <div class="row">
                      @if (u.dentist?.photoUrl) { <img class="avatar ph" [src]="u.dentist.photoUrl" [alt]="u.fullName" /> }
                      @else { <span class="avatar">{{ ini(u.fullName) }}</span> }
                      <div><div class="strong">{{ u.fullName }}</div><div class="muted small">{{ u.username }} · {{ u.email }}</div></div>
                    </div>
                  </td>
                  <td>
                    <span class="tag plain" [class]="'tag plain ' + roleTone(u.role)">{{ u.accessRole ? u.accessRoleLabel : u.roleLabel }}</span>
                    @if (u.accessRole) { <div class="muted small">basado en {{ u.roleLabel }}</div> }
                    @if (u.dentist) { <div class="muted small">{{ u.dentist.specialty }}</div> }
                  </td>
                  <td>
                    @if (!u.active) { <span class="tag grey">Desactivada</span> }
                    @else if (u.locked) { <span class="tag amber">Bloqueada</span> }
                    @else { <span class="tag green">Activa</span> }
                  </td>
                  <td class="muted small nowrap">{{ u.lastLoginAt ? dt(u.lastLoginAt) : 'Nunca' }}</td>
                  <td class="right">
                    <div class="row end">
                      @if (u.locked && u.active) { <button class="btn sm" type="button" (click)="unlock(u)"><app-icon name="unlock" [size]="14" /> Desbloquear</button> }
                      <button class="btn sm ghost" type="button" (click)="viewing = u"><app-icon name="eye" [size]="14" /> Ver</button>
                      <button class="btn sm" type="button" (click)="openEdit(u)"><app-icon name="edit" [size]="14" /> Editar</button>
                    </div>
                  </td>
                </tr>
              }
              @empty { <tr><td colspan="5" class="muted" style="text-align:center;padding:24px">Nadie coincide con la búsqueda.</td></tr> }
            </tbody>
          </table>
        </section>

        <aside class="panel perms">
          <div class="panel-head"><h3>Qué puede hacer cada rol</h3></div>
          <ul>
            @for (p of permissions; track p.role) { <li><span class="strong">{{ p.role }}</span><span class="muted small">{{ p.can }}</span></li> }
          </ul>
          <div class="panel-foot small muted">5 intentos fallidos bloquean la cuenta 15 minutos.</div>
        </aside>
      </div>
    </div>

    <app-modal [open]="!!viewing" [title]="viewing?.fullName || ''" [subtitle]="viewing ? viewing.username : ''" (close)="viewing = null">
      @if (viewing) {
        <dl class="kv small">
          <dt>Rol</dt><dd>{{ viewing.accessRole ? viewing.accessRoleLabel : viewing.roleLabel }}</dd>
          @if (viewing.accessRole) { <dt>Basado en</dt><dd>{{ viewing.roleLabel }}</dd> }
          <dt>Correo</dt><dd>{{ viewing.email }}</dd>
          <dt>Celular</dt><dd>{{ viewing.phone || '—' }}</dd>
          @if (viewing.dentist) { <dt>Especialidad</dt><dd>{{ viewing.dentist.specialty }}{{ viewing.dentist.cop ? ' · ' + viewing.dentist.cop : '' }}</dd> }
          <dt>Verificación en 2 pasos</dt><dd>{{ viewing.twoFactorMethod === 'CORREO' ? 'Por correo' : viewing.twoFactorMethod === 'APP' ? 'App de autenticación' : 'No activada' }}</dd>
          <dt>Estado</dt><dd>{{ !viewing.active ? 'Desactivada' : viewing.locked ? 'Bloqueada' : 'Activa' }}</dd>
          <dt>Último acceso</dt><dd>{{ viewing.lastLoginAt ? dt(viewing.lastLoginAt) : 'Nunca' }}</dd>
          <dt>Creada</dt><dd>{{ viewing.createdAt ? dt(viewing.createdAt) : '—' }}</dd>
          @if (viewing.updatedBy) { <dt>Último cambio</dt><dd>{{ dt(viewing.updatedAt) }} · {{ viewing.updatedBy }}</dd> }
        </dl>
      }
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="viewing = null">Cerrar</button>
        <button class="btn primary" type="button" (click)="openEdit(viewing); viewing = null">Editar</button>
      </ng-container>
    </app-modal>

    <app-modal [open]="modal" [title]="editing ? 'Editar cuenta' : 'Crear cuenta'" [subtitle]="editing ? editing.username : ''" [width]="620" (close)="modal = false">
      <form class="grid cols-2" (ngSubmit)="save()" autocomplete="off">
        <div class="field span-2">
          <label class="req" for="fn">Nombres y apellidos</label>
          <input id="fn" name="fn" class="input" [class.invalid]="e['fullName']" [(ngModel)]="f.fullName" />
          @if (e['fullName']) { <span class="err">{{ e['fullName'] }}</span> }
        </div>
        <div class="field">
          <label class="req" for="un">Usuario</label>
          <input id="un" name="un" class="input" [class.invalid]="e['username']" [(ngModel)]="f.username" [disabled]="!!editing" autocapitalize="none" />
          @if (e['username']) { <span class="err">{{ e['username'] }}</span> }
        </div>
        <div class="field">
          <label class="req" for="em">Correo</label>
          <input id="em" name="em" type="email" class="input" [class.invalid]="e['email']" [(ngModel)]="f.email" />
          @if (e['email']) { <span class="err">{{ e['email'] }}</span> }
        </div>
        <div class="field span-2">
          <label class="req" for="rl">Rol</label>
          <select id="rl" name="rl" class="input" [class.invalid]="e['role'] || e['accessRole']" [(ngModel)]="roleChoice" (ngModelChange)="pickRole($event)">
            <option value="">Seleccionar…</option>
            @for (g of roleGroups; track g.base) {
              <optgroup [label]="g.label">
                @for (r of g.roles; track r.code) { <option [value]="r.code">{{ r.custom ? r.label : r.label + ' (estándar)' }}</option> }
              </optgroup>
            }
          </select>
          @if (e['role'] || e['accessRole']) { <span class="err">{{ e['role'] || e['accessRole'] }}</span> }
          @else { <span class="hint">Si dos personas del mismo puesto necesitan accesos distintos, crea un rol para cada una
            @if (auth.can('ROLES')) { en <a routerLink="/roles">Roles y permisos</a> } y elígelo aquí.</span> }
          @if (chosen) {
            <details class="role-preview">
              <summary>{{ chosen.label }}: {{ chosen.permissionCount }} permisos{{ chosen.custom ? ' · basado en ' + chosen.baseRoleLabel : '' }}</summary>
              @if (chosen.description) { <p class="muted small">{{ chosen.description }}</p> }
              <dl>
                @for (g of groupsOf(chosen); track g[0]) { <dt>{{ g[0] }}</dt><dd>{{ g[1].join(' · ') }}</dd> }
              </dl>
            </details>
          }
        </div>
        <div class="field">
          <label for="ph">Celular</label>
          <input id="ph" name="ph" class="input" [class.invalid]="e['phone']" [(ngModel)]="f.phone" maxlength="9" inputmode="numeric" placeholder="9XXXXXXXX" />
          @if (e['phone']) { <span class="err">{{ e['phone'] }}</span> }
        </div>
        @if (!editing) {
          <div class="field">
            <label class="req" for="pw">Contraseña inicial</label>
            <input id="pw" name="pw" class="input" [class.invalid]="e['password']" [(ngModel)]="f.password" autocomplete="new-password" />
            @if (e['password']) { <span class="err">{{ e['password'] }}</span> } @else { <span class="hint">Mínimo 8 caracteres, una letra y un número.</span> }
          </div>
        } @else { <div></div> }
        @if (f.role === 'ODONTOLOGO') {
          <div class="field">
            <label class="req" for="sp">Especialidad</label>
            <input id="sp" name="sp" class="input" [class.invalid]="e['specialty']" [(ngModel)]="f.specialty" list="specs" />
            <datalist id="specs"><option value="Odontología general"></option><option value="Ortodoncia"></option><option value="Endodoncia"></option><option value="Periodoncia"></option><option value="Rehabilitación oral"></option><option value="Odontopediatría"></option><option value="Cirugía bucal"></option></datalist>
            @if (e['specialty']) { <span class="err">{{ e['specialty'] }}</span> }
          </div>
          <div class="field">
            <label for="cop">Colegiatura (COP)</label>
            <input id="cop" name="cop" class="input" [(ngModel)]="f.cop" />
          </div>
        }
      </form>

      @if (editing) {
        <div class="danger-zone">
          @if (editing.dentist) {
            <div class="dz-row">
              <div class="row">
                @if (editing.dentist.photoUrl) { <img class="avatar ph lg" [src]="editing.dentist.photoUrl" alt="Foto del odontólogo" /> }
                @else { <span class="avatar lg">{{ ini(editing.fullName) }}</span> }
                <div><div class="strong small">Foto del odontólogo</div><div class="muted small">Se ve en la agenda y en la app del paciente al reservar. JPG, PNG o WEBP hasta 4 MB.</div></div>
              </div>
              <div class="row">
                <label class="btn sm"><app-icon name="upload" [size]="14" /> {{ photoBusy ? 'Subiendo…' : (editing.dentist.photoUrl ? 'Cambiar' : 'Subir foto') }}
                  <input type="file" accept="image/jpeg,image/png,image/webp" hidden (change)="uploadPhoto($event)" [disabled]="photoBusy" /></label>
                @if (editing.dentist.photoUrl) { <button class="btn sm ghost" type="button" (click)="removePhoto()">Quitar</button> }
              </div>
            </div>
            <div class="dz-row">
              <div><div class="strong small">Horario de atención</div><div class="muted small">Define los bloques en los que se pueden programar citas.</div></div>
              <button class="btn sm" type="button" (click)="openSchedule()"><app-icon name="calendar" [size]="14" /> Editar horario</button>
            </div>
          }
          <div class="dz-row">
            <div><div class="strong small">Contraseña temporal</div><div class="muted small">Desbloquea la cuenta y reemplaza la contraseña.</div></div>
            <div class="row">
              <input class="input pwi" [(ngModel)]="tempPassword" placeholder="Nueva contraseña" aria-label="Contraseña temporal" />
              <button class="btn sm" type="button" (click)="setPassword()" [disabled]="tempPassword.length < 8">Asignar</button>
            </div>
          </div>
          <div class="dz-row">
            <div><div class="strong small">{{ editing.active ? 'Desactivar cuenta' : 'Reactivar cuenta' }}</div>
              <div class="muted small">{{ editing.active ? 'La persona pierde el acceso de inmediato. Su historial se conserva.' : 'La persona podrá volver a iniciar sesión.' }}</div></div>
            <button class="btn sm" [class.danger]="editing.active" type="button" (click)="toggleActive()" [disabled]="editing.id === auth.user()?.id">
              {{ editing.active ? 'Desactivar' : 'Reactivar' }}
            </button>
          </div>
          @if (audit.length) {
            <details>
              <summary class="small strong">Registro de cambios ({{ audit.length }})</summary>
              <ul class="audit">@for (a of audit; track a.id) { <li><span class="muted">{{ dt(a.createdAt) }}</span> {{ a.username }} · {{ a.detail }}</li> }</ul>
            </details>
          }
        </div>
      }

      <ng-container modal-actions>
        <button class="btn" type="button" (click)="modal = false">Cancelar</button>
        <button class="btn primary" type="button" (click)="save()" [disabled]="saving">{{ editing ? 'Guardar cambios' : 'Crear cuenta' }}</button>
      </ng-container>
    </app-modal>

    <app-modal [open]="scheduleOpen" title="Horario de atención" [subtitle]="editing?.fullName" [width]="640" (close)="scheduleOpen = false">
      <div class="sched">
        @for (day of days; track $index; let di = $index) {
          <div class="srow">
            <span class="dname">{{ day }}</span>
            <div class="blocks">
              @for (b of blocksOf(di + 1); track $index) {
                <span class="blk">
                  <input type="time" class="input t" [(ngModel)]="b.start" step="900" [attr.aria-label]="day + ' inicio'" />
                  <span class="muted">a</span>
                  <input type="time" class="input t" [(ngModel)]="b.end" step="900" [attr.aria-label]="day + ' fin'" />
                  <button type="button" class="btn ghost sm" (click)="removeBlock(b)" aria-label="Quitar bloque"><app-icon name="x" [size]="14" /></button>
                </span>
              } @empty { <span class="muted small">No atiende</span> }
              <button type="button" class="btn ghost sm" (click)="addBlock(di + 1)"><app-icon name="plus" [size]="14" /> Bloque</button>
            </div>
          </div>
        }
      </div>
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="scheduleOpen = false">Cancelar</button>
        <button class="btn primary" type="button" (click)="saveSchedule()">Guardar horario</button>
      </ng-container>
    </app-modal>
  `,
  styles: [`
    .role-preview { margin-top: 8px; border: 1px solid var(--line); border-radius: 12px; padding: 8px 12px; background: #f8fbfc; }
    .role-preview summary { cursor: pointer; font-weight: 700; font-size: 13px; }
    .role-preview dl { display: grid; grid-template-columns: 150px minmax(0, 1fr); gap: 4px 10px; margin: 8px 0 0; font-size: 12.5px; }
    .role-preview dt { color: var(--muted); font-weight: 600; }
    .role-preview dd { margin: 0; }
    @media (max-width: 600px) { .role-preview dl { grid-template-columns: 1fr; } }
    .avatar.ph { object-fit: cover; padding: 0; }
    .avatar.lg { width: 52px; height: 52px; font-size: 16px; flex: none; }
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 18px; align-items: start; }
    tr.off td { opacity: .6; }
    .nowrap { white-space: nowrap; }
    .end { justify-content: flex-end; }
    .perms ul { list-style: none; margin: 0; padding: 6px 18px; }
    .perms li { display: flex; flex-direction: column; padding: 10px 0; border-bottom: 1px solid var(--line); font-size: var(--step--1); }
    .perms li:last-child { border-bottom: 0; }
    .danger-zone { margin-top: 20px; border: 1px solid var(--line); border-radius: var(--radius); }
    .dz-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 12px 14px; border-bottom: 1px solid var(--line); flex-wrap: wrap; }
    .danger-zone details { padding: 12px 14px; }
    .pwi { width: 170px; height: 30px; font-size: 13px; }
    .audit { list-style: none; padding: 0; margin: 8px 0 0; font-size: 12.5px; display: flex; flex-direction: column; gap: 4px; }
    .sched { display: flex; flex-direction: column; }
    .srow { display: grid; grid-template-columns: 100px 1fr; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--line); align-items: center; }
    .dname { font-weight: 600; font-size: var(--step--1); }
    .blocks { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
    .blk { display: inline-flex; align-items: center; gap: 6px; padding: 3px 4px 3px 8px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface-2); }
    .t { width: 104px; height: 30px; font-size: 13px; }
    @media (max-width: 1360px) { .layout { grid-template-columns: minmax(0, 1fr); } }
  `],
})
export class UsersComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  auth = inject(AuthService);

  users: any[] = [];
  roles: { code: string; label: string }[] = [];
  permissions = PERMISSIONS;
  days = DAYS;

  modal = false;
  editing: any = null;
  photoBusy = false;
  f: any = {};
  e: Record<string, string> = {};
  saving = false;
  tempPassword = '';
  audit: AuditEntry[] = [];

  scheduleOpen = false;
  blocks: { weekday: number; start: string; end: string }[] = [];

  q = '';
  viewing: any = null;
  customRoles: any[] = [];
  get shown() {
    const q = this.q.trim().toLowerCase();
    return this.users.filter((u) => !q || [u.fullName, u.username, u.email, u.roleLabel, u.accessRoleLabel, u.dentist?.specialty].some((x) => (x || '').toLowerCase().includes(q)));
  }
  customFor(role: string) { return this.customRoles.filter((r) => r.baseRole === role); }

  /** v2.10: un solo campo "Rol": los del sistema y los creados, agrupados por puesto. */
  assignable: any[] = [];
  roleChoice = '';
  get roleGroups() {
    const order = ['RECEPCION', 'CAJA', 'ODONTOLOGO', 'DIRECTOR', 'ADMIN'];
    const bases = [...new Set(this.assignable.map((r) => r.baseRole))].sort((a, b) => (order.indexOf(a) + 1 || 99) - (order.indexOf(b) + 1 || 99));
    return bases.map((b) => ({ base: b, label: this.assignable.find((r) => r.baseRole === b)?.baseRoleLabel || b,
      roles: this.assignable.filter((r) => r.baseRole === b).sort((x, y) => Number(x.custom) - Number(y.custom) || x.label.localeCompare(y.label)) }));
  }
  get chosen() { return this.assignable.find((r) => r.code === this.roleChoice) || null; }
  groupsOf(r: any): [string, string[]][] { return Object.entries(r.permissionsByGroup || {}) as [string, string[]][]; }
  pickRole(code: string) {
    const r = this.assignable.find((x) => x.code === code);
    this.f.role = r ? r.baseRole : '';
    this.f.accessRole = r && r.custom ? r.code : '';
  }

  ngOnInit() {
    this.load();
    this.api.roles().subscribe((r) => (this.roles = r));
    this.api.assignableRoles().subscribe((r) => (this.assignable = r));
    if (this.auth.can('ROLES')) this.api.permissions().subscribe((m) => (this.customRoles = m.roles.filter((r: any) => r.custom)));
  }

  load() { this.api.users().subscribe((u) => (this.users = u)); }

  openNew() {
    this.editing = null;
    this.f = { fullName: '', username: '', email: '', role: '', accessRole: '', phone: '', password: '', specialty: '', cop: '' };
    this.roleChoice = '';
    this.e = {};
    this.modal = true;
  }

  openEdit(u: any) {
    this.editing = u;
    this.f = { fullName: u.fullName, username: u.username, email: u.email, role: u.role, accessRole: u.accessRole || '', phone: u.phone || '', specialty: u.dentist?.specialty || '', cop: u.dentist?.cop || '' };
    this.roleChoice = u.accessRole || u.role;
    this.e = {};
    this.tempPassword = '';
    this.audit = [];
    this.api.userAudit(u.id).subscribe((a) => (this.audit = a));
    this.modal = true;
  }

  save() {
    this.saving = true;
    this.e = {};
    const body = { ...this.f, role: this.f.role || null, accessRole: this.f.accessRole || null, phone: this.f.phone || '' };
    const req = this.editing ? this.api.updateUser(this.editing.id, body) : this.api.createUser(body);
    req.subscribe({
      next: (u) => {
        this.saving = false;
        this.modal = false;
        this.toast.ok(this.editing ? `Cuenta de ${u.fullName} actualizada.` : `Cuenta creada: ${u.username}.`);
        this.load();
      },
      error: (err) => {
        this.saving = false;
        const a = apiError(err);
        this.e = a.fields || {};
        this.toast.error(a.message);
      },
    });
  }

  toggleActive() {
    const u = this.editing;
    if (u.active && !confirm(`¿Desactivar la cuenta de ${u.fullName}? Perderá el acceso de inmediato.`)) return;
    this.api.setUserActive(u.id, !u.active).subscribe({
      next: (r) => { this.editing = r; this.toast.ok(r.active ? 'Cuenta reactivada.' : 'Cuenta desactivada.'); this.load(); this.api.userAudit(r.id).subscribe((a) => (this.audit = a)); },
      error: (err) => this.toast.error(apiError(err).message),
    });
  }

  unlock(u: any) {
    this.api.unlockUser(u.id).subscribe({
      next: () => { this.toast.ok(`Cuenta de ${u.fullName} desbloqueada.`); this.load(); },
      error: (err) => this.toast.error(apiError(err).message),
    });
  }

  setPassword() {
    this.api.setUserPassword(this.editing.id, this.tempPassword).subscribe({
      next: () => { this.toast.ok('Contraseña temporal asignada. Compártela de forma privada.'); this.tempPassword = ''; this.load(); },
      error: (err) => this.toast.error(apiError(err).message),
    });
  }

  openSchedule() {
    this.api.schedules(this.editing.dentist.id).subscribe((list) => {
      this.blocks = list.map((s) => ({ weekday: s.weekday, start: s.startTime, end: s.endTime }));
      this.scheduleOpen = true;
    });
  }

  blocksOf(day: number) { return this.blocks.filter((b) => b.weekday === day); }
  addBlock(day: number) { this.blocks.push({ weekday: day, start: '09:00', end: '13:00' }); }
  removeBlock(b: any) { this.blocks = this.blocks.filter((x) => x !== b); }

  saveSchedule() {
    this.api.saveSchedules(this.editing.dentist.id, this.blocks).subscribe({
      next: () => { this.scheduleOpen = false; this.toast.ok('Horario actualizado.'); },
      error: (err) => this.toast.error(apiError(err).message),
    });
  }

  roleTone(r: string) { return ROLE_TONE[r] || 'grey'; }
  ini = initials;
  dt = dateTime;

  uploadPhoto(ev: Event) {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || !this.editing?.dentist) return;
    this.photoBusy = true;
    this.api.dentistPhoto(this.editing.dentist.id, file).subscribe({
      next: (d) => { this.photoBusy = false; this.setPhoto(d.photoUrl || null); this.toast.ok('Foto actualizada.'); },
      error: (err) => { this.photoBusy = false; this.toast.error(apiError(err).message); },
    });
  }

  removePhoto() {
    if (!this.editing?.dentist) return;
    this.api.removeDentistPhoto(this.editing.dentist.id).subscribe({
      next: () => { this.setPhoto(null); this.toast.ok('Foto quitada.'); },
      error: (err) => this.toast.error(apiError(err).message),
    });
  }

  private setPhoto(url: string | null) {
    this.editing.dentist.photoUrl = url;
    const u = this.users.find((x: any) => x.id === this.editing.id);
    if (u?.dentist) u.dentist.photoUrl = url;
  }
}
