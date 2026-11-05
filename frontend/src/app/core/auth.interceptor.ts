import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { ToastService } from './toast.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const toast = inject(ToastService);
  const token = auth.token;
  const authReq = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(authReq).pipe(
    catchError((err: HttpErrorResponse) => {
      const isAuthCall = req.url.startsWith('/api/auth/login') || req.url.startsWith('/api/auth/recover') || req.url.startsWith('/api/auth/reset');
      if (err.status === 401 && !isAuthCall && token) {
        toast.warn('Tu sesión terminó. Inicia sesión nuevamente.');
        auth.logout(false);
      } else if (err.status === 403 && !isAuthCall) {
        toast.error(err.error?.message || 'Tu rol no tiene permiso para esta operación.');
      } else if (err.status === 0) {
        toast.error('No hay conexión con el servidor. Revisa que el sistema esté en marcha.');
      } else if (err.status >= 500) {
        toast.error(err.error?.message || 'Ocurrió un error en el servidor.');
      }
      return throwError(() => err);
    }),
  );
};
