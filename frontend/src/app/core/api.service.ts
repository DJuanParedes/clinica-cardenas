import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  get<T>(path: string): Observable<T> {
    return this.http.get<T>(`/api${path}`);
  }

  post<T>(path: string, body: unknown): Observable<T> {
    return this.http.post<T>(`/api${path}`, body);
  }

  put<T>(path: string, body: unknown): Observable<T> {
    return this.http.put<T>(`/api${path}`, body);
  }

  patch<T>(path: string, body: unknown = {}): Observable<T> {
    return this.http.patch<T>(`/api${path}`, body);
  }

  delete<T>(path: string): Observable<T> {
    return this.http.delete<T>(`/api${path}`);
  }

  postForm<T>(path: string, form: FormData): Observable<T> {
    return this.http.post<T>(`/api${path}`, form);
  }

  download(path: string): Observable<Blob> {
    return this.http.get(`/api${path}`, { responseType: 'blob' });
  }
}
