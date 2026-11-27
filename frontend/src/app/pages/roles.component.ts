import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ModalComponent } from '../shared/modal.component';
import { Api, PermissionMatrix } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ToastService } from '../core/toast.service';
import { apiError } from '../core/format';
import { IconComponent } from '../shared/icon.component';

import { OnlyDirective } from '../shared/only.directive';
interface Group { name: string; perms: { code: string; label: string; group: string; description?: string }[]; }

/** HU-26: asignar permisos a cada rol. Se aplica en el servidor en la siguiente petición del usuario. */
@Component({
  selector: 'app-roles',
  standalone: true,
  imports: [OnlyDirective, IconComponent, FormsModule, ModalComponent],
  template: `
    <div class="page wide">
      <div class="page-head">
        <div>
          <h1>Roles y permisos</h1>
          <p class="sub">Solo el administrador entra aquí. Marca qué puede hacer cada rol, crea, renombra, oculta o elimina roles, y cambia cómo se llaman los permisos. Los roles se asignan en Personal.</p>
        </div>
        <div class="actions">
          <button class="btn" type="button" (click)="openNew()"><app-icon name="plus" [size]="16" /> Nuevo rol</button>
          @if (dirtyRoles.length) {
            <button class="btn" type="button" (click)="discard()">Descartar</button>
            <button class="btn primary" type="button" (click)="saveAll()" [disabled]="saving">
              @if (saving) { <span class="spin"></span> Guardando… } @else { <app-icon name="check" [size]="16" /> Guardar cambios ({{ dirtyRoles.length }}) }
            </button>
          }
        </div>
      </div>

      @if (!m) {
        <div class="skeleton" style="height: 480px"></div>
      } @else {
        <section class="panel matrix">
          <table class="table keep">
            <thead>
              <tr>
                <th class="pcol">Permiso</th>
                @for (r of m.roles; track r.code) {
                  <th class="rcol" [class.changed]="isDirty(r.code)" [class.hid]="r.hidden">
                    <div class="rn">{{ r.label }}</div>
                    <div class="muted small">{{ r.users }} {{ r.users === 1 ? 'persona' : 'personas' }}</div>
                    @if (r.locked) { <span class="tag plain grey small"><app-icon name="lock" [size]="12" /> Todos</span> }
                    @if (r.hidden) { <span class="tag plain grey small">Oculto</span> }
                    @if (r.custom) { <div class="muted small">basado en {{ r.baseRoleLabel }}</div> }
                    <div class="ra">
                      <button type="button" class="btn sm ghost" (click)="openEdit(r)" [attr.aria-label]="'Editar rol ' + r.label" title="Editar nombre, descripción o visibilidad"><app-icon name="edit" [size]="13" /></button>
                      @if (r.custom) {
                        <button type="button" class="btn sm ghost del" (click)="openDelete(r)" [attr.aria-label]="'Eliminar rol ' + r.label" title="Eliminar rol"><app-icon name="trash" [size]="13" /></button>
                      }
                    </div>
                  </th>
                }
              </tr>
            </thead>
            <tbody>
              @for (g of groups; track g.name) {
                <tr class="grp"><td [attr.colspan]="m.roles.length + 1">{{ g.name }}</td></tr>
                @for (p of g.perms; track p.code) {
                  <tr>
                    <td>
                      <div class="pl"><div><div class="strong">{{ p.label }}</div>
                        @if (p.description) { <div class="muted small">{{ p.description }}</div> }
                        <div class="muted small code">{{ p.code }}@if (isAdminOnly(p.code)) { · solo administrador }</div></div>
                        <button type="button" class="btn sm ghost" (click)="openPerm(p)" [attr.aria-label]="'Editar permiso ' + p.label" title="Cambiar nombre, grupo o descripción"><app-icon name="edit" [size]="13" /></button>
                      </div>
                    </td>
                    @for (r of m.roles; track r.code) {
                      <td class="c">
                        <label class="box" [class.locked]="r.locked || isAdminOnly(p.code)" [attr.title]="r.locked ? 'El administrador conserva todos los permisos' : isAdminOnly(p.code) ? 'Exclusivo del administrador' : ''">
                          <input type="checkbox" [checked]="has(r.code, p.code)" [disabled]="r.locked || isAdminOnly(p.code)" (change)="toggle(r.code, p.code)"
                                 [attr.aria-label]="r.label + ': ' + p.label" />
                        </label>
                      </td>
                    }
                  </tr>
                }
              }
            </tbody>
          </table>
        </section>
        <p class="muted small note">
          <app-icon name="shield" [size]="14" /> Cada operación se valida en el servidor con estos permisos: aunque alguien escriba la dirección de una pantalla,
          sin el permiso recibe "Sin permiso". El administrador siempre conserva todos los permisos para no perder el acceso, y es el único que
          administra roles y permisos. Los roles del sistema no se borran porque el programa depende de ellos: se pueden renombrar u ocultar.
          Los permisos no se crean ni se borran desde aquí porque cada uno corresponde a una función del programa; sí puedes cambiar su nombre, grupo y descripción.
        </p>
      }
    </div>

    <app-modal [open]="!!editing" [title]="'Editar rol: ' + (editing?.label || '')" (close)="editing = null">
      @if (editing) {
        <form class="stack" (ngSubmit)="saveEdit()">
          <div class="field"><label class="req" for="en">Nombre</label><input id="en" class="input" name="en" [(ngModel)]="ef.name" maxlength="60" appOnly="text" /></div>
          <div class="field"><label for="ed">Descripción</label><input id="ed" class="input" name="ed" [(ngModel)]="ef.description" maxlength="255" appOnly="text" /></div>
          @if (!editing.locked) {
            <label class="check"><input type="checkbox" name="eh" [(ngModel)]="ef.hidden" />
              Ocultar (no aparece al asignar roles en Personal)@if (editing.users > 0) { <span class="muted small"> — primero cambia de rol a sus {{ editing.users }} persona(s)</span> }</label>
          }
        </form>
      }
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="editing = null">Cancelar</button>
        <button class="btn primary" type="button" (click)="saveEdit()" [disabled]="saving || !ef.name">Guardar</button>
      </ng-container>
    </app-modal>

    <app-modal [open]="!!deleting" [title]="'Eliminar rol: ' + (deleting?.label || '')" (close)="deleting = null">
      @if (deleting) {
        @if (deleting.users > 0) {
          <p>{{ deleting.users }} persona(s) tienen este rol. Elige a qué rol pasarlas antes de eliminarlo:</p>
          <div class="field"><label class="req" for="dr">Pasar a</label>
            <select id="dr" class="input" [(ngModel)]="reassignTo" name="dr"><option value="">Seleccionar…</option>
              @for (o of reassignOptions; track o.code) { <option [value]="o.code">{{ o.label }}</option> }
            </select></div>
        } @else {
          <p>Nadie tiene este rol. Se eliminará con sus permisos.</p>
        }
      }
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="deleting = null">Cancelar</button>
        <button class="btn danger" type="button" (click)="confirmDelete()" [disabled]="saving || (deleting?.users > 0 && !reassignTo)"><app-icon name="trash" [size]="15" /> Eliminar rol</button>
      </ng-container>
    </app-modal>

    <app-modal [open]="!!perm" [title]="'Editar permiso'" [subtitle]="perm?.code || ''" (close)="perm = null">
      <form class="stack" (ngSubmit)="savePerm()">
        <div class="field"><label class="req" for="pl">Nombre</label><input id="pl" class="input" name="pl" [(ngModel)]="pf.label" maxlength="120" appOnly="text" /></div>
        <div class="field"><label class="req" for="pg">Grupo</label><input id="pg" class="input" name="pg" [(ngModel)]="pf.group" maxlength="40" list="pgroups" appOnly="text" />
          <datalist id="pgroups">@for (g of groups; track g.name) { <option [value]="g.name"></option> }</datalist></div>
        <div class="field"><label for="pd">Descripción</label><input id="pd" class="input" name="pd" [(ngModel)]="pf.description" maxlength="255" appOnly="text" /></div>
      </form>
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="perm = null">Cancelar</button>
        <button class="btn primary" type="button" (click)="savePerm()" [disabled]="saving || !pf.label || !pf.group">Guardar</button>
      </ng-container>
    </app-modal>

    <app-modal [open]="newOpen" title="Nuevo rol" subtitle="Elige el puesto y de dónde copiar los permisos; luego ajústalos en la matriz." (close)="newOpen = false">
      <form class="stack" (ngSubmit)="create()">
        <div class="field"><label class="req" for="rn">Nombre del rol</label><input id="rn" class="input" name="rn" [(ngModel)]="nf.name" maxlength="60" placeholder="Asistente dental" appOnly="text" /></div>
        <div class="field"><label class="req" for="rb">Puesto base</label>
          <select id="rb" class="input" name="rb" [(ngModel)]="nf.baseRole"><option value="">Seleccionar…</option>
            @for (b of baseRoles; track b.code) { <option [value]="b.code">{{ b.label }}</option> }</select>
          <span class="muted small">El puesto define cómo funciona la cuenta (por ejemplo, un odontólogo tiene agenda propia); el rol define qué puede ver y hacer.</span></div>
        <div class="field"><label for="rc">Copiar permisos de</label>
          <select id="rc" class="input" name="rc" [(ngModel)]="nf.copyFrom"><option value="">El puesto base</option>
            @for (o of m?.roles || []; track o.code) { @if (!o.locked) { <option [value]="o.code">{{ o.label }}</option> } }
            <option value="VACIO">Ninguno (empezar sin permisos)</option></select></div>
        <div class="field"><label for="rd">Descripción</label><input id="rd" class="input" name="rd" [(ngModel)]="nf.description" maxlength="255" appOnly="text" /></div>
      </form>
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="newOpen = false">Cancelar</button>
        <button class="btn primary" type="button" (click)="create()" [disabled]="saving || !nf.name || !nf.baseRole">Crear rol</button>
      </ng-container>
    </app-modal>
  `,
  styles: [`
    .matrix { overflow-x: auto; }
    .matrix .table { min-width: 860px; }
    .pcol { width: 34%; }
    .rcol { text-align: center; vertical-align: bottom; }
    .rcol.changed { background: var(--orange-soft); }
    .rn { font-weight: 800; color: var(--ink); font-size: 13px; }
    .grp td { background: var(--surface-2); font-weight: 800; font-size: 12px; color: var(--teal-strong); letter-spacing: .02em; padding-top: 10px; padding-bottom: 8px; }
    .c { text-align: center; }
    .box { display: inline-grid; place-items: center; width: 34px; height: 34px; border-radius: 9px; cursor: pointer; }
    .box:hover { background: var(--brand-soft); }
    .box.locked { cursor: not-allowed; }
    .box input { width: 18px; height: 18px; accent-color: var(--teal); cursor: inherit; }
    .code { font-family: ui-monospace, monospace; font-size: 11px; }
    .ra { display: flex; justify-content: center; gap: 2px; margin-top: 4px; }
    .del { color: var(--gum); }
    .rcol.hid .rn { color: var(--muted); text-decoration: line-through; }
    .pl { display: flex; justify-content: space-between; align-items: flex-start; gap: 6px; }
    .check { display: flex; gap: 8px; align-items: flex-start; font-size: 14px; }
    .note { display: flex; gap: 6px; align-items: flex-start; margin-top: 12px; max-width: 900px; }
  `],
})
export class RolesComponent implements OnInit {
  private api = inject(Api);
  private toast = inject(ToastService);
  private auth = inject(AuthService);

  m: PermissionMatrix | null = null;
  groups: Group[] = [];
  draft: Record<string, Set<string>> = {};
  saving = false;

  ngOnInit() { this.load(); }

  newOpen = false;
  nf = { name: '', baseRole: '', description: '', copyFrom: '' };
  openNew() { this.nf = { name: '', baseRole: '', description: '', copyFrom: '' }; this.newOpen = true; }

  /** Puestos del sistema (el administrador no tiene variantes). */
  get baseRoles() { return (this.m?.roles || []).filter((r) => !r.custom && !r.locked); }
  isAdminOnly(code: string) { return (this.m?.adminOnly || []).includes(code); }

  private fail(e: any) { this.saving = false; const a = apiError(e); this.toast.error(a.fields ? Object.values(a.fields)[0] as string : a.message); }

  editing: any = null;
  ef = { name: '', description: '', hidden: false };
  openEdit(r: any) { this.editing = r; this.ef = { name: r.label, description: r.description || '', hidden: !!r.hidden }; }
  saveEdit() {
    if (!this.editing) return;
    this.saving = true;
    this.api.editRole(this.editing.code, this.ef).subscribe({
      next: (m) => { this.saving = false; this.editing = null; this.apply(m); this.toast.ok('Rol actualizado.'); },
      error: (e) => this.fail(e),
    });
  }

  deleting: any = null;
  reassignTo = '';
  openDelete(r: any) { this.deleting = r; this.reassignTo = ''; }
  get reassignOptions() { return (this.m?.roles || []).filter((r) => r.code !== this.deleting?.code && !r.locked && !r.hidden); }
  confirmDelete() {
    if (!this.deleting) return;
    this.saving = true;
    this.api.deleteRole(this.deleting.code, this.reassignTo || undefined).subscribe({
      next: (m) => { this.saving = false; this.deleting = null; this.apply(m); this.toast.ok('Rol eliminado.'); },
      error: (e) => this.fail(e),
    });
  }

  perm: any = null;
  pf = { label: '', group: '', description: '' };
  openPerm(p: any) { this.perm = p; this.pf = { label: p.label, group: p.group, description: p.description || '' }; }
  savePerm() {
    if (!this.perm) return;
    this.saving = true;
    this.api.editPermission(this.perm.code, this.pf).subscribe({
      next: (m) => { this.saving = false; this.perm = null; this.apply(m); this.toast.ok('Permiso actualizado.'); },
      error: (e) => this.fail(e),
    });
  }
  create() {
    this.saving = true;
    this.api.createRole(this.nf).subscribe({
      next: (m) => { this.saving = false; this.newOpen = false; this.apply(m); this.toast.ok('Rol creado. Ajusta sus permisos y guarda.'); },
      error: (e) => { this.saving = false; const a = apiError(e); this.toast.error(a.fields ? Object.values(a.fields)[0] : a.message); },
    });
  }

  load() {
    this.api.permissions().subscribe((m) => this.apply(m));
  }

  private apply(m: PermissionMatrix) {
    this.m = m;
    const gs: Group[] = [];
    for (const p of m.permissions) {
      let g = gs.find((x) => x.name === p.group);
      if (!g) gs.push((g = { name: p.group, perms: [] }));
      g.perms.push(p);
    }
    this.groups = gs;
    this.draft = {};
    for (const r of m.roles) this.draft[r.code] = new Set(r.permissions);
  }

  has(role: string, perm: string) { return this.draft[role]?.has(perm); }

  toggle(role: string, perm: string) {
    const s = this.draft[role];
    if (s.has(perm)) s.delete(perm); else s.add(perm);
  }

  isDirty(role: string): boolean {
    const r = this.m?.roles.find((x) => x.code === role);
    if (!r || r.locked) return false;
    const a = new Set(r.permissions), b = this.draft[role];
    return a.size !== b.size || [...a].some((x) => !b.has(x));
  }

  get dirtyRoles(): string[] { return (this.m?.roles || []).filter((r) => this.isDirty(r.code)).map((r) => r.code); }

  discard() { if (this.m) this.apply(this.m); }

  saveAll() {
    const roles = this.dirtyRoles;
    if (!roles.length) return;
    this.saving = true;
    const next = (i: number) => {
      if (i >= roles.length) {
        this.saving = false;
        this.toast.ok('Permisos actualizados.');
        this.auth.refresh();
        return;
      }
      const code = roles[i];
      this.api.savePermissions(code, [...this.draft[code]]).subscribe({
        next: (m) => {
          const keep = { ...this.draft };
          this.apply(m);
          for (const r of roles.slice(i + 1)) this.draft[r] = keep[r];
          next(i + 1);
        },
        error: (e) => { this.saving = false; this.toast.error(apiError(e).message); },
      });
    };
    next(0);
  }
}
