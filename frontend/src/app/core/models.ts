export type UserRole = 'ADMIN' | 'DOCTOR' | 'RECEPCION' | 'FINANZAS';

export interface ClinicBrand {
  id: number;
  code: string;
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

export interface AuthResponse {
  token: string;
  name: string;
  email: string;
  role: UserRole;
  platformAdmin: boolean;
  clinic: ClinicBrand;
}

export interface SessionUser {
  name: string;
  email: string;
  role: UserRole;
  platformAdmin: boolean;
  clinic: ClinicBrand;
}

export interface NavItem {
  label: string;
  icon: string;
  route: string;
  roles: UserRole[];
  group: 'principal' | 'clinica' | 'operaciones' | 'administracion';
}

export type FieldType = 'text' | 'number' | 'date' | 'datetime-local' | 'textarea' | 'select';

export interface ModuleField {
  key: string;
  label: string;
  type?: FieldType;
  required?: boolean;
  options?: string[];
  list?: boolean;
  placeholder?: string;
}

export interface ModuleConfig extends NavItem {
  title: string;
  subtitle: string;
  eyebrow: string;
  endpoint: string;
  createRoles: UserRole[];
  editRoles: UserRole[];
  statusRoles: UserRole[];
  fields: ModuleField[];
  statusField: string;
  statusOptions: string[];
  readOnly?: boolean;
}
