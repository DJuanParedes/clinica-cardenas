import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ModalComponent } from '../shared/modal.component';
import { Api, PermissionMatrix } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ToastService } from '../core/toast.service';
import { apiError } from '../core/format';
import { IconComponent } from '../shared/icon.component';

interface Group { name: string; perms: { code: string; label: string }[]; }

/** HU-26: asignar permisos a cada rol. Se aplica en el servidor en la siguiente petición del usuario. */
@Component({
  selector: 'app-roles',
  standalone: true,
  imports: [IconComponent, FormsModule, ModalComponent],
  template: `
    <div class="page wide">
      <div class="page-head">
        <div>
          <h1>Roles y permisos</h1>
          <p class="sub">Marca qué puede hacer cada rol. Además de los 5 puestos base puedes crear roles propios (p. ej. "Asistente dental" o "Recepción sin caja") y asignarlos en Usuarios y accesos.</p>
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
          <table class="table">
            <thead>
              <tr>
                <th class="pcol">Permiso</th>
                @for (r of m.roles; track r.code) {
                  <th class="rcol" [class.changed]="isDirty(r.code)">
                    <div class="rn">{{ r.label }}</div>
                    <div class="muted small">{{ r.users }} {{ r.users === 1 ? 'persona' : 'personas' }}</div>
                    @if (r.locked) { <span class="tag plain grey small"><app-icon name="lock" [size]="12" /> Todos</span> }
                    @if (r.custom) {
                      <div class="muted small">basado en {{ r.baseRoleLabel }}</div>
                      <button type="button" class="btn sm ghost del" (click)="remove(r)" [disabled]="r.users > 0" [attr.title]="r.users > 0 ? 'Tiene usuarios asignados' : 'Eliminar rol'"><app-icon name="x" [size]="12" /> Eliminar</button>
                    }
                  </th>
                }
              </tr>
            </thead>
            <tbody>
              @for (g of groups; track g.name) {
                <tr class="grp"><td [attr.colspan]="m.roles.length + 1">{{ g.name }}</td></tr>
                @for (p of g.perms; track p.code) {
                  <tr>
                    <td><div class="strong">{{ p.label }}</div><div class="muted small code">{{ p.code }}</div></td>
                    @for (r of m.roles; track r.code) {
                      <td class="c">
                        <label class="box" [class.locked]="r.locked" [attr.title]="r.locked ? 'El administrador conserva todos los permisos' : ''">
                          <input type="checkbox" [checked]="has(r.code, p.code)" [disabled]="r.locked" (change)="toggle(r.code, p.code)"
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
          sin el permiso recibe "Sin permiso". El administrador siempre conserva todos los permisos para no perder el acceso.
          Las cuentas y su rol se asignan en <strong>Usuarios y accesos</strong>.
        </p>
      }
    </div>

    <app-modal [open]="newOpen" title="Nuevo rol" subtitle="Parte de los permisos de un puesto base; luego ajústalos en la matriz." (close)="newOpen = false">
      <form class="stack" (ngSubmit)="create()">
        <div class="field"><label class="req" for="rn">Nombre del rol</label><input id="rn" class="input" name="rn" [(ngModel)]="nf.name" maxlength="60" placeholder="Asistente dental" /></div>
        <div class="field"><label class="req" for="rb">Puesto base</label>
          <select id="rb" class="input" name="rb" [(ngModel)]="nf.baseRole"><option value="">Seleccionar…</option>
            <option value="RECEPCION">Recepcionista</option><option value="ODONTOLOGO">Odontólogo</option><option value="CAJA">Responsable de caja</option><option value="DIRECTOR">Director de la clínica</option></select>
          <span class="muted small">El puesto define cómo funciona la cuenta (por ejemplo, un odontólogo tiene agenda propia); el rol define qué puede ver y hacer.</span></div>
        <div class="field"><label for="rd">Descripción</label><input id="rd" class="input" name="rd" [(ngModel)]="nf.description" maxlength="255" /></div>
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
    .del { margin-top: 4px; font-size: 12px; }
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
  nf = { name: '', baseRole: '', description: '' };
  openNew() { this.nf = { name: '', baseRole: '', description: '' }; this.newOpen = true; }
  create() {
    this.saving = true;
    this.api.createRole(this.nf).subscribe({
      next: (m) => { this.saving = false; this.newOpen = false; this.apply(m); this.toast.ok('Rol creado. Ajusta sus permisos y guarda.'); },
      error: (e) => { this.saving = false; const a = apiError(e); this.toast.error(a.fields ? Object.values(a.fields)[0] : a.message); },
    });
  }
  remove(r: any) {
    this.api.deleteRole(r.code).subscribe({
      next: (m) => { this.apply(m); this.toast.ok('Rol eliminado.'); },
      error: (e) => this.toast.error(apiError(e).message),
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
