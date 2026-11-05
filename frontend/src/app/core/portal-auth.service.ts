import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

const TOKEN_KEY = 'cc.pac.token';
const USER_KEY = 'cc.pac.user';

export interface PortalPatient {
  id: number; firstName: string; fullName: string; docType: string; docNumber: string;
  phone: string; email?: string; notifyChannel: string;
  clinic: { name: string; address: string; phone: string };
}

/* La app del paciente se usa en el celular: la sesión se recuerda en el dispositivo (localStorage). */
function read<T>(key: string): T | null {
  try {
    const v = localStorage.getItem(key);
    return v ? (JSON.parse(v) as T) : null;
  } catch {
    return null;
  }
}
function write(key: string, v: unknown) {
  try { localStorage.setItem(key, JSON.stringify(v)); } catch { /* almacenamiento no disponible */ }
}

/** HU-22: sesión del paciente en la aplicación móvil. */
@Injectable({ providedIn: 'root' })
export class PortalAuth {
  private http = inject(HttpClient);
  private router = inject(Router);

  readonly patient = signal<PortalPatient | null>(read<PortalPatient>(USER_KEY));
  readonly isLoggedIn = computed(() => !!this.patient());

  get token(): string | null { return read<string>(TOKEN_KEY); }

  private store(res: any) {
    write(TOKEN_KEY, res.token);
    write(USER_KEY, res.patient);
    this.patient.set(res.patient);
  }

  login(docType: string, docNumber: string, password: string): Observable<any> {
    return this.http.post<any>('/api/portal/auth/login', { docType, docNumber, password }).pipe(tap((r) => this.store(r)));
  }

  requestCode(docType: string, docNumber: string, birthDate: string): Observable<any> {
    return this.http.post('/api/portal/auth/code', { docType, docNumber, birthDate });
  }

  activate(docType: string, docNumber: string, code: string, password: string): Observable<any> {
    return this.http.post<any>('/api/portal/auth/activate', { docType, docNumber, code, password }).pipe(tap((r) => this.store(r)));
  }

  setPatient(p: PortalPatient) {
    write(USER_KEY, p);
    this.patient.set(p);
  }

  logout(callServer = true) {
    if (callServer && this.token) this.http.post('/api/portal/logout', {}).subscribe({ error: () => {} });
    try {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
    } catch { /* sin almacenamiento */ }
    this.patient.set(null);
    this.router.navigate(['/app/ingresar']);
  }
}
