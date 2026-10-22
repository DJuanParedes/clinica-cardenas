import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { apiBaseUrl, backendPending, pendingMessage } from './deployment';
import { AuthService } from './auth.service';
import { ClinicConfigService } from './clinic-config.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!/^\/api(?:\/|$)/.test(req.url)) return next(req);
  if (backendPending) return throwError(() => new HttpErrorResponse({
    status: 503, statusText: 'Backend pendiente', error: { message: pendingMessage }
  }));
  const auth = inject(AuthService);
  const router = inject(Router);
  const clinic = inject(ClinicConfigService);
  const token = auth.token();
  const headers: Record<string,string> = { 'X-Clinic-Id': clinic.config().id };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const request = req.clone({ url: apiBaseUrl + req.url, setHeaders: headers });

  return next(request).pipe(
    catchError((error: unknown) => {
      const status = (error as { status?: number }).status;
      if (status === 401) {
        auth.logout();
      } else if (status === 403) {
        void router.navigateByUrl('/app/no-autorizado');
      }
      return throwError(() => error);
    })
  );
};

