import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { AuthResponse, SessionUser, UserRole } from './models';
import { ClinicConfigService } from './clinic-config.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly clinicConfig = inject(ClinicConfigService);
  private readonly tokenKey = 'cc_token';
  private readonly userKey = 'cc_user';
  private readonly current = signal<SessionUser | null>(this.readUser());

  readonly user = this.current.asReadonly();
  readonly authenticated = computed(() => !!this.current() && !!sessionStorage.getItem(this.tokenKey));

  constructor() {
    const user = this.current();
    if (user?.clinic) this.clinicConfig.applyClinic(user.clinic);
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', { email, password }).pipe(
      tap(response => this.persistSession(response))
    );
  }

  switchClinic(clinicCode: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`/api/auth/switch-clinic/${encodeURIComponent(clinicCode)}`, {}).pipe(
      tap(response => this.persistSession(response))
    );
  }


  requestPasswordReset(email: string): Observable<{message:string;destination?:string|null;channel?:string|null}> {
    return this.http.post<{message:string;destination?:string|null;channel?:string|null}>('/api/auth/password-reset/request', { email });
  }

  confirmPasswordReset(email: string, code: string, newPassword: string): Observable<void> {
    return this.http.post<void>('/api/auth/password-reset/confirm', { email, code, newPassword });
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.patch<void>('/api/auth/change-password', { currentPassword, newPassword });
  }

  logout(): void {
    sessionStorage.removeItem(this.tokenKey);
    sessionStorage.removeItem(this.userKey);
    this.current.set(null);
    void this.router.navigateByUrl('/login');
  }

  token(): string | null {
    return sessionStorage.getItem(this.tokenKey);
  }

  hasRole(roles: UserRole[]): boolean {
    const role = this.current()?.role;
    return !!role && roles.includes(role);
  }

  isPlatformAdmin(): boolean { return this.current()?.platformAdmin === true; }

  homeForRole(): string {
    return '/app/dashboard';
  }

  private readUser(): SessionUser | null {
    const value = sessionStorage.getItem(this.userKey);
    if (!value) return null;
    try {
      return JSON.parse(value) as SessionUser;
    } catch {
      sessionStorage.removeItem(this.userKey);
      return null;
    }
  }

  private persistSession(response: AuthResponse): void {
    const user: SessionUser = {
      name: response.name, email: response.email, role: response.role,
      platformAdmin: response.platformAdmin, clinic: response.clinic
    };
    sessionStorage.setItem(this.tokenKey, response.token);
    sessionStorage.setItem(this.userKey, JSON.stringify(user));
    this.current.set(user);
    this.clinicConfig.applyClinic(response.clinic);
  }
}
