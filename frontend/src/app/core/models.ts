export type Role = 'ADMIN' | 'RECEPCION' | 'ODONTOLOGO' | 'CAJA' | 'DIRECTOR';

export interface SessionUser {
  id: number; username: string; fullName: string; email: string;
  role: Role; roleLabel: string; dentistId?: number;
}

export interface ApiError {
  status: number; code: string; message: string; fields?: Record<string, string>;
}

export interface Patient {
  id: number; docType: string; docNumber: string; firstName: string; lastName: string; fullName: string;
  birthDate: string; age: number; sex: 'F' | 'M'; phone: string; email?: string; address?: string; district?: string;
  emergencyContactName?: string; emergencyContactPhone?: string; notes?: string; active: boolean;
  createdAt: string; createdBy: string; updatedAt?: string; updatedBy?: string;
}

export interface UserLite { id: number; username: string; fullName: string; role: Role; active: boolean; }

export interface Dentist { id: number; specialty: string; cop?: string; color: string; active: boolean; fullName: string; user: UserLite; }

export type AppointmentStatus = 'PROGRAMADA' | 'CONFIRMADA' | 'EN_ESPERA' | 'EN_ATENCION' | 'ATENDIDA' | 'CANCELADA' | 'NO_ASISTIO';

export interface Appointment {
  id: number; patient: Patient; dentist: Dentist; startAt: string; endAt: string; durationMin: number;
  reason: string; notes?: string; status: AppointmentStatus; statusReason?: string; arrivedAt?: string;
  createdAt: string; createdBy: string;
}

export interface Slot { start: string; end: string; available: boolean; reason?: string; }

export interface Block { start: string; end: string; }

export interface Procedure { id: number; code: string; name: string; category: string; price: number; }

export interface ClinicalHistory {
  id?: number; bloodType?: string; allergies?: string; medicalBackground?: string; currentMedication?: string;
  surgeries?: string; habits?: string; familyBackground?: string; observations?: string; updatedAt?: string; updatedBy?: string;
}

export interface Diagnosis { id: number; cie10Code?: string; description: string; tooth?: number; createdAt: string; createdBy: string; dentist?: Dentist; }

export interface OdontogramEntry { tooth: number; face: string; condition: string; note?: string; recordedAt?: string; recordedBy?: string; }

export interface TreatmentItem { id: number; planId: number; procedure: Procedure; tooth?: number; price: number; status: string; doneAt?: string; invoiceId?: number; }
export interface Evolution { id: number; planId: number; itemId?: number; sessionDate: string; notes: string; createdBy: string; dentist?: Dentist; }
export interface TreatmentPlan { id: number; title: string; notes?: string; status: string; createdAt: string; createdBy: string; dentist?: Dentist; }
export interface PlanView { plan: TreatmentPlan; items: TreatmentItem[]; evolutions: Evolution[]; total: number; doneAmount: number; doneCount: number; itemCount: number; }

export interface Invoice {
  id: number; docType: '01' | '03'; docTypeLabel: string; series: string; number: number; fullNumber: string;
  issueDate: string; issueTime: string; patientId?: number; customerDocType: string; customerDocNumber: string;
  customerName: string; customerAddress?: string; currency: string; taxableAmount: number; igvAmount: number; totalAmount: number;
  status: string; hash?: string; sunatCode?: string; sunatDescription?: string; sentAt?: string; hasCdr: boolean;
  createdAt: string; createdBy: string;
}
export interface InvoiceItem { id: number; lineNumber: number; description: string; quantity: number; unitPrice: number; unitValue: number; lineValue: number; lineIgv: number; lineTotal: number; treatmentItemId?: number; }
export interface Submission { id: number; attemptAt: string; environment: string; status: string; responseCode?: string; responseDescription?: string; durationMs?: number; createdBy: string; }
export interface Company { ruc: string; businessName: string; tradeName: string; address: string; ubigeo: string; }

export interface AuditEntry { id: number; entity: string; entityId: number; action: string; detail?: string; username: string; createdAt: string; }

export interface Page<T> { content: T[]; total: number; page: number; pages: number; mode?: string; }
