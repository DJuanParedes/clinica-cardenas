import { Injectable, inject, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { AuthService } from './auth.service';

export interface DeleteAsk { entity: string; id: string | number; what?: string; done: Subject<boolean>; }

/**
 * v2.11: eliminar registros es exclusivo del administrador (permiso ELIMINAR_REGISTROS).
 * Cualquier pantalla pide la eliminación con ask(); el diálogo vive en la raíz de la app para quedar encima de todo.
 */
@Injectable({ providedIn: 'root' })
export class DeleteService {
  private auth = inject(AuthService);
  readonly current = signal<DeleteAsk | null>(null);

  get allowed(): boolean { return this.auth.can('ELIMINAR_REGISTROS'); }

  /** Abre la confirmación; emite true si se eliminó. */
  ask(entity: string, id: string | number, what?: string): Observable<boolean> {
    const done = new Subject<boolean>();
    this.current.set({ entity, id, what, done });
    return done.asObservable();
  }

  finish(ok: boolean) {
    const c = this.current();
    this.current.set(null);
    if (c) { c.done.next(ok); c.done.complete(); }
  }
}
