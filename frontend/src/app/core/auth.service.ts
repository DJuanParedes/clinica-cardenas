import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { Role, SessionUser } from './models';

const TOKEN_KEY = 'cc.token';
const USER_KEY = 'cc.user';

function read<T>(key: string): T | null {
  try {
    const v = sessionStorage.getItem(key);
    return v ? (JSON.parse(v) as T) : null;
  } catch {
    return null;
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  readonly user = signal<SessionUser | null>(read<SessionUser>(USER_KEY));
  readonly isLoggedIn = computed(() => !!this.user());

  get token(): string | null {
    return read<string>(TOKEN_KEY);
  }

  login(username: string, password: string): Observable<any> {
    return this.http.post<any>('/api/auth/login', { username, password }).pipe(
      tap((res) => {
        sessionStorage.setItem(TOKEN_KEY, JSON.stringify(res.token));
        sessionStorage.setItem(USER_KEY, JSON.stringify(res.user));
        this.user.set(res.user);
      }),
    );
  }

  recover(login: string): Observable<any> { return this.http.post('/api/auth/recover', { login }); }

  reset(login: string, code: string, newPassword: string): Observable<any> {
    return this.http.post('/api/auth/reset', { login, code, newPassword });
  }

  logout(callServer = true) {
    if (callServer && this.token) {
      this.http.post('/api/auth/logout', {}).subscribe({ error: () => {} });
    }
    this.clear();
    this.router.navigate(['/login']);
  }

  clear() {
    try {
      sessionStorage.removeItem(TOKEN_KEY);
      sessionStorage.removeItem(USER_KEY);
    } catch { /* almacenamiento no disponible */ }
    this.user.set(null);
  }

  hasRole(...roles: Role[]): boolean {
    const u = this.user();
    return !!u && roles.includes(u.role);
  }

  /** Pantalla inicial según el rol. */
  home(): string {
    return '/inicio';
  }
}
