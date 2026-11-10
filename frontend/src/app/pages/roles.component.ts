import { Component, OnInit, inject } from '@angular/core';
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
  imports: [IconComponent],
  template: `
    <div class="page wide">
      <div class="page-head">
        <div>
          <h1>Roles y permisos</h1>
          <p class="sub">Marca qué puede hacer cada rol. El cambio rige al instante, también en el servidor.</p>
        </div>
        <div class="actions">
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
