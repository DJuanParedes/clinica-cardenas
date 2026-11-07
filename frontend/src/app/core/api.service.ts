import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Appointment, AuditEntry, ClinicalHistory, Company, Dentist, Diagnosis, Invoice, InvoiceItem, OdontogramEntry,
  Page, Patient, PlanView, Procedure, Slot, Block, Submission,
} from './models';

function params(obj: Record<string, any>): HttpParams {
  let p = new HttpParams();
  for (const [k, v] of Object.entries(obj)) {
    if (v !== null && v !== undefined && v !== '') p = p.set(k, String(v));
  }
  return p;
}

@Injectable({ providedIn: 'root' })
export class Api {
  private http = inject(HttpClient);

  // ---------- inicio
  dashboard(): Observable<any> { return this.http.get('/api/dashboard'); }

  // ---------- pacientes (HU-01, HU-02, HU-03)
  searchPatients(q: string, page = 0, size = 20): Observable<Page<Patient>> {
    return this.http.get<Page<Patient>>('/api/patients', { params: params({ q, page, size }) });
  }
  patient(id: number): Observable<{ patient: Patient; nextAppointment?: Appointment; lastVisit?: Appointment; appointmentsCount: number }> {
    return this.http.get<any>(`/api/patients/${id}`);
  }
  checkDocument(type: string, number: string, excludeId?: number): Observable<{ exists: boolean; patient?: { id: number; fullName: string } }> {
    return this.http.get<any>('/api/patients/check-document', { params: params({ type, number, excludeId }) });
  }
  createPatient(body: any): Observable<Patient> { return this.http.post<Patient>('/api/patients', body); }
  updatePatient(id: number, body: any): Observable<Patient> { return this.http.put<Patient>(`/api/patients/${id}`, body); }
  patientAppointments(id: number): Observable<Appointment[]> { return this.http.get<Appointment[]>(`/api/patients/${id}/appointments`); }
  patientAudit(id: number): Observable<AuditEntry[]> { return this.http.get<AuditEntry[]>(`/api/patients/${id}/audit`); }

  // ---------- agenda (HU-07, HU-08, HU-09)
  dentists(): Observable<Dentist[]> { return this.http.get<Dentist[]>('/api/dentists'); }
  slots(dentistId: number, date: string, duration: number, excludeId?: number):
      Observable<{ works: boolean; blocks: Block[]; slots: Slot[]; freeCount: number; dentist: Dentist }> {
    return this.http.get<any>('/api/agenda/slots', { params: params({ dentistId, date, duration, excludeId }) });
  }
  day(date: string, dentistId?: number): Observable<{ columns: { dentist: Dentist; blocks: Block[] }[]; appointments: Appointment[]; counts: Record<string, number> }> {
    return this.http.get<any>('/api/agenda/day', { params: params({ date, dentistId }) });
  }
  appointment(id: number): Observable<Appointment> { return this.http.get<Appointment>(`/api/appointments/${id}`); }
  createAppointment(body: any): Observable<Appointment> { return this.http.post<Appointment>('/api/appointments', body); }
  reschedule(id: number, body: any): Observable<Appointment> { return this.http.put<Appointment>(`/api/appointments/${id}`, body); }
  setStatus(id: number, status: string, reason?: string): Observable<Appointment> {
    return this.http.patch<Appointment>(`/api/appointments/${id}/status`, { status, reason });
  }
  appointmentAudit(id: number): Observable<AuditEntry[]> { return this.http.get<AuditEntry[]>(`/api/appointments/${id}/audit`); }

  // ---------- historia clínica (HU-04, HU-05, HU-06)
  history(pid: number): Observable<{ history: ClinicalHistory | null; diagnoses: Diagnosis[]; alerts: string[] }> {
    return this.http.get<any>(`/api/patients/${pid}/history`);
  }
  saveHistory(pid: number, body: ClinicalHistory): Observable<ClinicalHistory> { return this.http.put<ClinicalHistory>(`/api/patients/${pid}/history`, body); }
  addDiagnosis(pid: number, body: any): Observable<Diagnosis> { return this.http.post<Diagnosis>(`/api/patients/${pid}/diagnoses`, body); }
  odontogram(pid: number): Observable<{ entries: OdontogramEntry[]; updatedAt?: string; updatedBy?: string }> {
    return this.http.get<any>(`/api/patients/${pid}/odontogram`);
  }
  saveOdontogram(pid: number, entries: OdontogramEntry[]): Observable<{ entries: OdontogramEntry[]; updatedAt?: string; updatedBy?: string }> {
    return this.http.put<any>(`/api/patients/${pid}/odontogram`, { entries });
  }
  procedures(): Observable<Procedure[]> { return this.http.get<Procedure[]>('/api/procedures'); }
  plans(pid: number): Observable<PlanView[]> { return this.http.get<PlanView[]>(`/api/patients/${pid}/plans`); }
  createPlan(pid: number, body: any): Observable<PlanView> { return this.http.post<PlanView>(`/api/patients/${pid}/plans`, body); }
  addPlanItem(planId: number, body: any): Observable<PlanView> { return this.http.post<PlanView>(`/api/plans/${planId}/items`, body); }
  setItemStatus(itemId: number, status: string): Observable<PlanView> { return this.http.patch<PlanView>(`/api/plan-items/${itemId}`, { status }); }
  addEvolution(planId: number, body: any): Observable<PlanView> { return this.http.post<PlanView>(`/api/plans/${planId}/evolutions`, body); }
  cancelPlan(planId: number): Observable<PlanView> { return this.http.post<PlanView>(`/api/plans/${planId}/cancel`, {}); }

  // ---------- facturación (HU-10, HU-11, HU-12)
  company(): Observable<Company> { return this.http.get<Company>('/api/billing/company'); }
  billableItems(pid: number): Observable<any[]> { return this.http.get<any[]>(`/api/billing/patients/${pid}/billable-items`); }
  invoices(f: { q?: string; status?: string; docType?: string; from?: string; to?: string; page?: number }): Observable<Page<Invoice>> {
    return this.http.get<Page<Invoice>>('/api/billing/invoices', { params: params(f) });
  }
  createInvoice(body: any): Observable<InvoiceDetail> { return this.http.post<InvoiceDetail>('/api/billing/invoices', body); }
  invoice(id: number): Observable<InvoiceDetail> { return this.http.get<InvoiceDetail>(`/api/billing/invoices/${id}`); }
  sendInvoice(id: number): Observable<InvoiceDetail> { return this.http.post<InvoiceDetail>(`/api/billing/invoices/${id}/send`, {}); }
  invoiceStatus(id: number): Observable<InvoiceDetail> { return this.http.post<InvoiceDetail>(`/api/billing/invoices/${id}/status`, {}); }
  lookupRuc(ruc: string): Observable<RucInfo> { return this.http.get<RucInfo>(`/api/billing/ruc/${ruc}`); }
  download(path: string): Observable<Blob> { return this.http.get(path, { responseType: 'blob' }); }

  // ---------- usuarios (HU-25, HU-27)
  users(): Observable<any[]> { return this.http.get<any[]>('/api/users'); }
  roles(): Observable<{ code: string; label: string }[]> { return this.http.get<any[]>('/api/roles'); }
  createUser(body: any): Observable<any> { return this.http.post('/api/users', body); }
  updateUser(id: number, body: any): Observable<any> { return this.http.put(`/api/users/${id}`, body); }
  setUserActive(id: number, active: boolean): Observable<any> { return this.http.patch(`/api/users/${id}/active`, { active }); }
  setUserPassword(id: number, newPassword: string): Observable<any> { return this.http.post(`/api/users/${id}/password`, { newPassword }); }
  unlockUser(id: number): Observable<any> { return this.http.post(`/api/users/${id}/unlock`, {}); }
  userAudit(id: number): Observable<AuditEntry[]> { return this.http.get<AuditEntry[]>(`/api/users/${id}/audit`); }
  schedules(dentistId: number): Observable<any[]> { return this.http.get<any[]>(`/api/dentists/${dentistId}/schedules`); }
  saveSchedules(dentistId: number, blocks: { weekday: number; start: string; end: string }[]): Observable<any[]> {
    return this.http.put<any[]>(`/api/dentists/${dentistId}/schedules`, blocks);
  }

  // ---------- roles y permisos (HU-26)
  permissions(): Observable<PermissionMatrix> { return this.http.get<PermissionMatrix>('/api/permissions'); }
  savePermissions(role: string, permissions: string[]): Observable<PermissionMatrix> {
    return this.http.put<PermissionMatrix>(`/api/permissions/${role}`, { permissions });
  }

  // ---------- caja (HU-13, HU-14, HU-15)
  cashMeta(): Observable<{ methods: string[]; methodLabels: Record<string, string>; expenseCategories: string[] }> { return this.http.get<any>('/api/cash/meta'); }
  account(pid: number): Observable<Account> { return this.http.get<Account>(`/api/cash/patients/${pid}/account`); }
  pay(body: any): Observable<{ payment: Payment; concept: string; account: Account }> { return this.http.post<any>('/api/cash/payments', body); }
  voidPayment(id: number, reason: string): Observable<Payment> { return this.http.post<Payment>(`/api/cash/payments/${id}/void`, { reason }); }
  addExpense(body: any): Observable<any> { return this.http.post('/api/cash/expenses', body); }
  voidExpense(id: number, reason: string): Observable<any> { return this.http.post(`/api/cash/expenses/${id}/void`, { reason }); }
  cashToday(): Observable<CashReport> { return this.http.get<CashReport>('/api/cash/today'); }
  cashReport(from: string, to: string): Observable<CashReport> { return this.http.get<CashReport>('/api/cash/report', { params: params({ from, to }) }); }

  // ---------- comisiones (HU-16, HU-17, HU-18)
  commissionSummary(): Observable<any[]> { return this.http.get<any[]>('/api/commissions/summary'); }
  commissionRules(dentistId?: number): Observable<CommissionRule[]> { return this.http.get<CommissionRule[]>('/api/commissions/rules', { params: params({ dentistId }) }); }
  saveCommissionRule(id: number | null, body: any): Observable<CommissionRule> {
    return id ? this.http.put<CommissionRule>(`/api/commissions/rules/${id}`, body) : this.http.post<CommissionRule>('/api/commissions/rules', body);
  }
  calculateCommissions(dentistId: number, from: string, to: string): Observable<any> {
    return this.http.get('/api/commissions/calculate', { params: params({ dentistId, from, to }) });
  }
  settlements(dentistId?: number, status?: string): Observable<any[]> { return this.http.get<any[]>('/api/commissions/settlements', { params: params({ dentistId, status }) }); }
  settle(body: any): Observable<any> { return this.http.post('/api/commissions/settlements', body); }
  settlement(id: number): Observable<any> { return this.http.get(`/api/commissions/settlements/${id}`); }
  paySettlement(id: number, body: any): Observable<any> { return this.http.post(`/api/commissions/settlements/${id}/payouts`, body); }
  annulSettlement(id: number, reason: string): Observable<any> { return this.http.post(`/api/commissions/settlements/${id}/annul`, { reason }); }

  // ---------- indicadores (HU-19, HU-20, HU-21)
  incomeReport(from: string, to: string, group?: string): Observable<any> { return this.http.get('/api/reports/income', { params: params({ from, to, group }) }); }
  appointmentsReport(from: string, to: string, dentistId?: number | null, group?: string): Observable<any> {
    return this.http.get('/api/reports/appointments', { params: params({ from, to, dentistId, group }) });
  }
  treatmentsReport(from: string, to: string, dentistId?: number | null): Observable<any> {
    return this.http.get('/api/reports/treatments', { params: params({ from, to, dentistId }) });
  }

  // ---------- notificaciones (HU-23, HU-24)
  notifications(status?: string, type?: string): Observable<{ counters: Record<string, number>; items: NotificationItem[]; panelUrl: string }> {
    return this.http.get<any>('/api/notifications', { params: params({ status, type }) });
  }
  retryNotification(id: number): Observable<NotificationItem> { return this.http.post<NotificationItem>(`/api/notifications/${id}/retry`, {}); }
  patientNotifications(pid: number): Observable<NotificationItem[]> { return this.http.get<NotificationItem[]>(`/api/patients/${pid}/notifications`); }
  setNotificationChannel(pid: number, channel: string): Observable<Patient> {
    return this.http.patch<Patient>(`/api/patients/${pid}/notification-channel`, { channel });
  }
  remind(appointmentId: number): Observable<{ queued: number; duplicated: number; message: string }> {
    return this.http.post<any>(`/api/appointments/${appointmentId}/remind`, {});
  }

  // ---------- archivos clínicos y documentos firmados
  patientFiles(pid: number): Observable<PatientFile[]> { return this.http.get<PatientFile[]>(`/api/patients/${pid}/files`); }
  uploadFile(pid: number, file: Blob, name: string, meta: { kind: string; title?: string; note?: string; tooth?: number | null; share?: boolean }): Observable<PatientFile> {
    const fd = new FormData();
    fd.append('file', file, name);
    for (const [k, v] of Object.entries(meta)) if (v !== null && v !== undefined && v !== '') fd.append(k, String(v));
    return this.http.post<PatientFile>(`/api/patients/${pid}/files`, fd);
  }
  createDocument(pid: number, body: { kind: string; title: string; body: string; signerName: string; signatureJpeg: string; shareWithPatient: boolean }): Observable<PatientFile> {
    return this.http.post<PatientFile>(`/api/patients/${pid}/documents`, body);
  }
  fileContent(id: number): Observable<Blob> { return this.http.get(`/api/files/${id}/content`, { responseType: 'blob' }); }
  shareFile(id: number, sharedWithPatient: boolean): Observable<PatientFile> { return this.http.patch<PatientFile>(`/api/files/${id}`, { sharedWithPatient }); }
  deleteFile(id: number): Observable<any> { return this.http.delete(`/api/files/${id}`); }

  // ---------- inventario
  inventory(): Observable<{ items: InventoryItem[]; lowCount: number; recent: any[] }> { return this.http.get<any>('/api/inventory'); }
  createInventoryItem(body: any): Observable<InventoryItem> { return this.http.post<InventoryItem>('/api/inventory', body); }
  updateInventoryItem(id: number, body: any): Observable<InventoryItem> { return this.http.put<InventoryItem>(`/api/inventory/${id}`, body); }
  inventoryMove(id: number, body: { kind: string; quantity: number; reason: string }): Observable<any> { return this.http.post(`/api/inventory/${id}/moves`, body); }
  inventoryMoves(id: number): Observable<any[]> { return this.http.get<any[]>(`/api/inventory/${id}/moves`); }
  supplies(): Observable<any[]> { return this.http.get<any[]>('/api/inventory/supplies'); }
  saveSupplies(procedureId: number, list: { itemId: number; quantity: number }[]): Observable<any> { return this.http.put(`/api/inventory/supplies/${procedureId}`, list); }

  // ---------- seguimiento: lista de espera, cuotas, controles, satisfacción
  waitlist(): Observable<any[]> { return this.http.get<any[]>('/api/waitlist'); }
  addWaitlist(body: { patientId: number; dentistId?: number | null; reason: string; preference?: string }): Observable<any> { return this.http.post('/api/waitlist', body); }
  closeWaitlist(id: number): Observable<any> { return this.http.post(`/api/waitlist/${id}/close`, {}); }
  installments(planId: number): Observable<Installments> { return this.http.get<Installments>(`/api/plans/${planId}/installments`); }
  createInstallments(planId: number, count: number, firstDueDate: string): Observable<Installments> {
    return this.http.post<Installments>(`/api/plans/${planId}/installments`, { count, firstDueDate });
  }
  recalls(): Observable<any[]> { return this.http.get<any[]>('/api/recalls'); }
  sendRecall(patientId: number, kind: string): Observable<{ queued: number; message: string }> { return this.http.post<any>(`/api/recalls/${patientId}/send`, { kind }); }
  rescheduleRequests(): Observable<any[]> { return this.http.get<any[]>('/api/agenda/reschedule-requests'); }
  satisfaction(days = 90): Observable<any> { return this.http.get('/api/reports/satisfaction', { params: params({ days }) }); }

  // ---------- verificación en 2 pasos
  totpSetup(): Observable<{ secret: string; otpauthUrl: string }> { return this.http.post<any>('/api/auth/2fa/setup', {}); }
  totpEnable(code: string): Observable<any> { return this.http.post('/api/auth/2fa/enable', { code }); }
  totpDisable(code: string): Observable<any> { return this.http.post('/api/auth/2fa/disable', { code }); }

  // ---------- app del paciente (HU-22)
  portalMe(): Observable<any> { return this.http.get('/api/portal/me'); }
  portalAppointments(): Observable<{ next: PortalAppointment | null; upcoming: PortalAppointment[]; past: PortalAppointment[] }> {
    return this.http.get<any>('/api/portal/appointments');
  }
  portalConfirm(id: number): Observable<any> { return this.http.post(`/api/portal/appointments/${id}/confirm`, {}); }
  portalChannel(channel: string): Observable<any> { return this.http.patch('/api/portal/me/notifications', { channel }); }
  portalAccount(): Observable<any> { return this.http.get('/api/portal/account'); }
}

export interface PatientFile {
  id: number; patientId: number; kind: string; title: string; note?: string; tooth?: number; contentType: string; sizeBytes: number;
  sharedWithPatient: boolean; signedAt?: string; createdAt: string; createdBy: string; image?: boolean;
}

export interface InventoryItem { id: number; name: string; unit: string; stock: number; minStock: number; active: boolean; low: boolean; updatedAt?: string; }

export interface Installments {
  planId: number; total: number; paid: number;
  items: { id: number; number: number; dueDate: string; amount: number; status: 'PAGADA' | 'PENDIENTE' | 'VENCIDA' }[];
}

export interface PermissionMatrix {
  permissions: { code: string; group: string; label: string }[];
  roles: { code: string; label: string; locked: boolean; permissions: string[]; users: number }[];
}

export interface Payment {
  id: number; patientId: number; treatmentItemId?: number; amount: number; method: string; reference?: string; notes?: string;
  paidAt: string; cashier: string; status: string; voidReason?: string; voidedAt?: string; voidedBy?: string;
}

export interface Obligation {
  treatmentItemId: number; procedure: string; category: string; tooth?: number; planId: number; planTitle: string;
  status: string; doneAt?: string; price: number; paid: number; pending: number;
}

export interface Account {
  patient: { id: number; fullName: string; docType: string; docNumber: string };
  charges: number; credits: number; balance: number; inFavor: number; unapplied: number;
  obligations: Obligation[];
  movements: { date: string; type: 'CARGO' | 'ABONO'; concept: string; detail: string; charge: number; credit: number; balance: number; paymentId?: number }[];
  payments: Payment[];
}

export interface CashReport {
  from: string; to: string; income: number; outgo: number; net: number; incomeCount: number; outgoCount: number; voided: number;
  byMethod: { method: string; label: string; income: number; outgo: number; net: number }[];
  byDay: { date: string; income: number; outgo: number }[];
  byCategory: { category: string; amount: number }[];
  detail: { type: 'INGRESO' | 'EGRESO'; id: number; date: string; concept: string; party?: string; patientId?: number; category?: string;
    method: string; reference?: string; amount: number; user: string }[];
}

export interface CommissionRule {
  id: number; dentist: Dentist; procedure?: Procedure; calcType: string; value: number; base: string;
  validFrom: string; validTo?: string; active: boolean; notes?: string; label: string; createdBy: string;
}

export interface NotificationItem {
  id: number; patientId?: number; appointmentId?: number; type: string; operation: string; channel: string; destination: string;
  subject?: string; body: string; idempotencyKey: string; scheduledFor?: string; status: string; attempts: number;
  lastError?: string; providerId?: string; sentAt?: string; createdAt: string;
}

export interface PortalAppointment {
  id: number; startAt: string; endAt: string; durationMin: number; reason: string; status: string;
  dentist: { fullName: string; specialty: string; color: string }; canConfirm: boolean;
}

export interface InvoiceDetail {
  invoice: Invoice; items: InvoiceItem[]; submissions: Submission[]; amountInWords: string; company: Company;
  patient?: { id: number; fullName: string };
  statusMessage?: string;
}

export interface RucInfo {
  ruc: string; razonSocial: string; estado: string; condicion: string;
  direccion: string; distrito: string; provincia: string; departamento: string; tipo: string;
}
