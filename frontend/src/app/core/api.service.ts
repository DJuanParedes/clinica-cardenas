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
