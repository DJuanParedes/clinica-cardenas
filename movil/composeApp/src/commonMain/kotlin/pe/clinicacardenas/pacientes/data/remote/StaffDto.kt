package pe.clinicacardenas.pacientes.data.remote

import kotlinx.serialization.Serializable

/* Modelos del API del personal (/api). Solo los campos que usa la app; el resto se ignora. */

@Serializable
data class StaffLoginRequest(val username: String, val password: String, val code: String? = null)

@Serializable
data class StaffUserDto(
    val id: Long,
    val username: String,
    val fullName: String,
    val role: String,
    val roleLabel: String = "",
    val dentistId: Long? = null,
    val permissions: List<String> = emptyList(),
    val twoFactor: Boolean = false,
)

@Serializable
data class StaffSessionDto(val token: String, val expiresInMinutes: Long = 0, val user: StaffUserDto)

@Serializable
data class SPatientDto(
    val id: Long,
    val fullName: String,
    val docType: String = "DNI",
    val docNumber: String = "",
    val age: Int? = null,
    val phone: String? = null,
    val email: String? = null,
    val birthDate: String? = null,
    val sex: String? = null,
    val notes: String? = null,
)

@Serializable
data class PatientPageDto(val content: List<SPatientDto> = emptyList(), val total: Long = 0)

@Serializable
data class PatientDetailDto(val patient: SPatientDto, val appointmentsCount: Int = 0)

@Serializable
data class SDentistDto(val id: Long, val fullName: String, val specialty: String = "", val color: String = "#00A3D4")

@Serializable
data class SAppointmentDto(
    val id: Long,
    val patient: SPatientDto,
    val dentist: SDentistDto,
    val startAt: String,
    val endAt: String,
    val durationMin: Int = 30,
    val reason: String = "",
    val notes: String? = null,
    val status: String,
    val arrivedAt: String? = null,
    val rescheduleRequestedAt: String? = null,
)

@Serializable
data class DayDto(val date: String, val appointments: List<SAppointmentDto> = emptyList())

@Serializable
data class StatusRequest(val status: String, val reason: String? = null)

@Serializable
data class SlotDto(val start: String, val end: String, val available: Boolean = true)

@Serializable
data class StaffSlotsDto(val works: Boolean = true, val slots: List<SlotDto> = emptyList())

@Serializable
data class NewAppointmentRequest(val patientId: Long, val dentistId: Long, val startAt: String, val durationMin: Int, val reason: String, val notes: String? = null)

@Serializable
data class HistoryDto(
    val bloodType: String? = null,
    val allergies: String? = null,
    val medicalBackground: String? = null,
    val currentMedication: String? = null,
    val observations: String? = null,
)

@Serializable
data class DiagnosisDto(val id: Long, val description: String = "", val tooth: Int? = null, val createdAt: String? = null)

@Serializable
data class HistoryViewDto(val history: HistoryDto? = null, val diagnoses: List<DiagnosisDto> = emptyList(), val alerts: List<String> = emptyList())

@Serializable
data class ProcedureDto(val id: Long, val code: String = "", val name: String, val price: Double = 0.0)

@Serializable
data class PlanItemDto(val id: Long, val procedure: ProcedureDto, val tooth: Int? = null, val price: Double = 0.0, val status: String)

@Serializable
data class EvolutionDto(val id: Long, val itemId: Long? = null, val sessionDate: String, val notes: String, val createdBy: String = "")

@Serializable
data class PlanInfoDto(val id: Long, val title: String, val status: String)

@Serializable
data class PlanViewDto(
    val plan: PlanInfoDto,
    val items: List<PlanItemDto> = emptyList(),
    val evolutions: List<EvolutionDto> = emptyList(),
    val total: Double = 0.0,
    val doneAmount: Double = 0.0,
)

@Serializable
data class EvolutionRequest(val itemId: Long?, val sessionDate: String, val notes: String, val markDone: Boolean)

@Serializable
data class SFileDto(
    val id: Long,
    val kind: String,
    val title: String,
    val contentType: String,
    val sizeBytes: Long = 0,
    val createdAt: String,
    val createdBy: String? = null,
    val sharedWithPatient: Boolean = false,
    val tooth: Int? = null,
)

@Serializable
data class ObligationDto(val treatmentItemId: Long, val procedure: String, val tooth: Int? = null, val price: Double = 0.0, val paid: Double = 0.0, val pending: Double = 0.0)

@Serializable
data class SAccountDto(val balance: Double = 0.0, val inFavor: Double = 0.0, val obligations: List<ObligationDto> = emptyList())

@Serializable
data class PaymentRequest(val patientId: Long, val treatmentItemId: Long?, val amount: Double, val method: String, val reference: String?, val notes: String?)

@Serializable
data class MethodTotalDto(val method: String, val label: String = "", val income: Double = 0.0, val outgo: Double = 0.0)

@Serializable
data class CashDetailDto(val type: String, val id: Long, val date: String, val concept: String = "", val party: String? = null, val method: String = "", val amount: Double = 0.0)

@Serializable
data class CashTodayDto(
    val income: Double = 0.0,
    val outgo: Double = 0.0,
    val net: Double = 0.0,
    val byMethod: List<MethodTotalDto> = emptyList(),
    val detail: List<CashDetailDto> = emptyList(),
)

@Serializable
data class InvoiceLine(val description: String, val quantity: Double, val unitPrice: Double, val treatmentItemId: Long? = null)

@Serializable
data class InvoiceCreateRequest(
    val docType: String,
    val patientId: Long,
    val customerDocType: String,
    val customerDocNumber: String,
    val customerName: String,
    val customerAddress: String? = null,
    val items: List<InvoiceLine>,
)

@Serializable
data class InvoiceHeadDto(val id: Long, val fullNumber: String = "", val status: String = "")

@Serializable
data class InvoiceCreatedDto(val invoice: InvoiceHeadDto, val statusMessage: String? = null)

@Serializable
data class StatusCountsDto(
    val PROGRAMADA: Int = 0,
    val CONFIRMADA: Int = 0,
    val EN_ESPERA: Int = 0,
    val EN_ATENCION: Int = 0,
    val ATENDIDA: Int = 0,
    val CANCELADA: Int = 0,
    val NO_ASISTIO: Int = 0,
)

@Serializable
data class DashboardDto(
    val appointmentsToday: Int = 0,
    val statusCounts: StatusCountsDto = StatusCountsDto(),
    val patientsTotal: Long = 0,
    val collectedToday: Double = 0.0,
    val collectedMonth: Double = 0.0,
    val receivable: Double = 0.0,
    val pendingSunat: Long = 0,
    val lowStock: Long = 0,
    val satisfaction: Double = 0.0,
    val surveysCount: Long = 0,
    val rescheduleRequests: Long = 0,
    val waitlist: Long = 0,
)
