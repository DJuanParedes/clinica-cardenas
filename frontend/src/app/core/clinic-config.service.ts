import { DOCUMENT } from '@angular/common';
import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { ClinicBrand } from './models';

export interface ClinicConfig {
  id: string;
  backendId?: number;
  slug: string;
  name: string;
  shortName: string;
  tagline: string;
  description: string;
  logoUrl: string;
  heroImageUrl: string;
  primaryColor: string;
  primaryDarkColor: string;
  accentColor: string;
  accentDarkColor: string;
  sidebarDarkColor: string;
  phone: string;
  email: string;
  address: string;
  workingHours: string;
  customDomain?: string | null;
  active: boolean;
}

declare global {
  interface Window { __CLINIC_CONFIG__?: Partial<ClinicConfig>; }
}

const DEFAULT_CLINIC: ClinicConfig = {
  id: 'CARDENAS',
  slug: 'cardenas',
  name: 'Clínica Cárdenas',
  shortName: 'Cárdenas',
  tagline: 'Atención odontológica en La Floresta',
  description: 'Atención odontológica para pacientes de La Floresta y zonas cercanas.',
  logoUrl: '/brand/logo-cardenas.svg',
  heroImageUrl: '/brand/cardenas-fachada.png',
  primaryColor: '#3B78A6',
  primaryDarkColor: '#245C80',
  accentColor: '#D06B4F',
  accentDarkColor: '#B8533C',
  sidebarDarkColor: '#193D52',
  phone: '',
  email: '',
  address: 'La Floresta, Lima, Perú',
  workingHours: 'Atención previa coordinación',
  active: true
};

@Injectable({ providedIn: 'root' })
export class ClinicConfigService {
  private readonly document = inject(DOCUMENT);
  private readonly http = inject(HttpClient);
  private readonly state = signal<ClinicConfig>(this.readRuntimeConfig());

  readonly config = this.state.asReadonly();
  readonly name = computed(() => this.state().name);
  readonly shortName = computed(() => this.state().shortName);
  readonly logoUrl = computed(() => this.state().logoUrl);

  constructor() { this.applyBranding(this.state()); }

  applyPlatformBranding(): void {
    const root = this.document.documentElement;
    root.style.setProperty('--brand-blue', '#1D7981');
    root.style.setProperty('--brand-blue-dark', '#173A59');
    root.style.setProperty('--brand-orange', '#2FA89F');
    root.style.setProperty('--brand-orange-dark', '#247F7A');
    root.style.setProperty('--clinic-sidebar-start', '#173A59');
    root.style.setProperty('--clinic-sidebar-end', '#102E47');
    this.document.title = 'Portal odontológico · Plataforma multiclínica';
    this.setMeta('description', 'Portal general de acceso y directorio de clínicas odontológicas.');
    this.setMeta('theme-color', '#173A59');
    const favicon = this.document.querySelector<HTMLLinkElement>('link[rel="icon"]');
    if (favicon) favicon.href = '/brand/platform-logo.svg';
  }

  applyClinic(brand: ClinicBrand | ClinicConfig): void {
    const config = 'code' in brand ? this.fromBrand(brand) : this.sanitize(brand);
    this.state.set(config);
    this.applyBranding(config);
  }

  loadPublicBySlug(slug: string): Observable<ClinicBrand> {
    return this.http.get<ClinicBrand>(`/api/public/clinics/${encodeURIComponent(slug)}`).pipe(
      tap(brand => this.applyClinic(brand))
    );
  }

  resolveCurrentDomain(): Observable<ClinicBrand> | null {
    if (typeof window === 'undefined') return null;
    const host = window.location.hostname.toLowerCase();
    if (host === 'localhost' || host === '127.0.0.1') return null;
    return this.http.get<ClinicBrand>(`/api/public/clinics/resolve-domain?domain=${encodeURIComponent(host)}`).pipe(
      tap(brand => this.applyClinic(brand))
    );
  }

  private readRuntimeConfig(): ClinicConfig {
    const runtime = typeof window === 'undefined' ? {} : (window.__CLINIC_CONFIG__ ?? {});
    const name = this.text(runtime.name, DEFAULT_CLINIC.name, 100);
    return {
      id: this.identifier(runtime.id, DEFAULT_CLINIC.id),
      slug: this.slug(runtime.slug, DEFAULT_CLINIC.slug),
      name,
      shortName: this.text(runtime.shortName, name.replace(/^Clínica\s+/i, ''), 50),
      tagline: this.text(runtime.tagline, DEFAULT_CLINIC.tagline, 120),
      description: this.text(runtime.description, DEFAULT_CLINIC.description, 240),
      logoUrl: this.assetUrl(runtime.logoUrl, DEFAULT_CLINIC.logoUrl),
      heroImageUrl: this.assetUrl(runtime.heroImageUrl, DEFAULT_CLINIC.heroImageUrl),
      primaryColor: this.color(runtime.primaryColor, DEFAULT_CLINIC.primaryColor),
      primaryDarkColor: this.color(runtime.primaryDarkColor, DEFAULT_CLINIC.primaryDarkColor),
      accentColor: this.color(runtime.accentColor, DEFAULT_CLINIC.accentColor),
      accentDarkColor: this.color(runtime.accentDarkColor, DEFAULT_CLINIC.accentDarkColor),
      sidebarDarkColor: this.color(runtime.sidebarDarkColor, DEFAULT_CLINIC.sidebarDarkColor),
      phone: this.text(runtime.phone, DEFAULT_CLINIC.phone, 40),
      email: this.text(runtime.email, DEFAULT_CLINIC.email, 120),
      address: this.text(runtime.address, DEFAULT_CLINIC.address, 180),
      workingHours: this.text(runtime.workingHours, DEFAULT_CLINIC.workingHours, 120),
      customDomain: this.text(runtime.customDomain, '', 160) || null,
      active: runtime.active !== false
    };
  }

  private fromBrand(brand: ClinicBrand): ClinicConfig {
    return this.sanitize({
      id: brand.code, backendId: brand.id, slug: brand.slug, name: brand.name, shortName: brand.shortName,
      tagline: brand.tagline, description: brand.description, logoUrl: brand.logoUrl,
      heroImageUrl: brand.heroImageUrl, primaryColor: brand.primaryColor,
      primaryDarkColor: brand.primaryDarkColor, accentColor: brand.accentColor,
      accentDarkColor: brand.accentDarkColor, sidebarDarkColor: brand.sidebarDarkColor,
      phone: brand.phone, email: brand.email, address: brand.address,
      workingHours: brand.workingHours, customDomain: brand.customDomain, active: brand.active
    });
  }

  private sanitize(value: Partial<ClinicConfig>): ClinicConfig {
    const name = this.text(value.name, DEFAULT_CLINIC.name, 100);
    return {
      id: this.identifier(value.id, DEFAULT_CLINIC.id), backendId: value.backendId,
      slug: this.slug(value.slug, DEFAULT_CLINIC.slug), name,
      shortName: this.text(value.shortName, name.replace(/^Clínica\s+/i, ''), 50),
      tagline: this.text(value.tagline, DEFAULT_CLINIC.tagline, 120),
      description: this.text(value.description, DEFAULT_CLINIC.description, 600),
      logoUrl: this.assetUrl(value.logoUrl, DEFAULT_CLINIC.logoUrl),
      heroImageUrl: this.assetUrl(value.heroImageUrl, DEFAULT_CLINIC.heroImageUrl),
      primaryColor: this.color(value.primaryColor, DEFAULT_CLINIC.primaryColor),
      primaryDarkColor: this.color(value.primaryDarkColor, DEFAULT_CLINIC.primaryDarkColor),
      accentColor: this.color(value.accentColor, DEFAULT_CLINIC.accentColor),
      accentDarkColor: this.color(value.accentDarkColor, DEFAULT_CLINIC.accentDarkColor),
      sidebarDarkColor: this.color(value.sidebarDarkColor, DEFAULT_CLINIC.sidebarDarkColor),
      phone: this.text(value.phone, DEFAULT_CLINIC.phone, 40),
      email: this.text(value.email, DEFAULT_CLINIC.email, 120),
      address: this.text(value.address, DEFAULT_CLINIC.address, 220),
      workingHours: this.text(value.workingHours, DEFAULT_CLINIC.workingHours, 160),
      customDomain: this.text(value.customDomain, '', 160) || null,
      active: value.active !== false
    };
  }

  private applyBranding(config: ClinicConfig): void {
    const root = this.document.documentElement;
    root.style.setProperty('--brand-blue', config.primaryColor);
    root.style.setProperty('--brand-blue-dark', config.primaryDarkColor);
    root.style.setProperty('--brand-orange', config.accentColor);
    root.style.setProperty('--brand-orange-dark', config.accentDarkColor);
    root.style.setProperty('--clinic-sidebar-start', config.primaryDarkColor);
    root.style.setProperty('--clinic-sidebar-end', config.sidebarDarkColor);
    this.document.title = `${config.name} · Gestión odontológica`;
    this.setMeta('description', `${config.name}. ${config.description}`);
    this.setMeta('theme-color', config.primaryDarkColor);
    const favicon = this.document.querySelector<HTMLLinkElement>('link[rel="icon"]');
    if (favicon) favicon.href = config.logoUrl;
  }

  private setMeta(name: string, content: string): void {
    const meta = this.document.querySelector<HTMLMetaElement>(`meta[name="${name}"]`);
    if (meta) meta.content = content;
  }

  private text(value: unknown, fallback: string, max: number): string {
    const normalized = typeof value === 'string' ? value.trim().replace(/[\r\n\t]+/g, ' ') : '';
    return normalized ? normalized.slice(0, max) : fallback;
  }

  private identifier(value: unknown, fallback: string): string {
    const normalized = typeof value === 'string' ? value.trim().toUpperCase() : '';
    return /^[A-Z0-9_-]{2,40}$/.test(normalized) ? normalized : fallback;
  }

  private slug(value: unknown, fallback: string): string {
    const normalized = typeof value === 'string' ? value.trim().toLowerCase() : '';
    return /^[a-z0-9][a-z0-9-]{1,59}$/.test(normalized) ? normalized : fallback;
  }

  private color(value: unknown, fallback: string): string {
    return typeof value === 'string' && /^#[0-9A-Fa-f]{6}$/.test(value.trim()) ? value.trim().toUpperCase() : fallback;
  }

  private assetUrl(value: unknown, fallback: string): string {
    if (typeof value !== 'string') return fallback;
    const normalized = value.trim();
    return /^(\/[^\s]*|https:\/\/[^\s]+)$/i.test(normalized) ? normalized : fallback;
  }
}
