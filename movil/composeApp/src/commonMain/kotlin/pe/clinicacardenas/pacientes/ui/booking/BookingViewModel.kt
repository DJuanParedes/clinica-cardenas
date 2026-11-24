package pe.clinicacardenas.pacientes.ui.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import pe.clinicacardenas.pacientes.data.remote.ApiException
import pe.clinicacardenas.pacientes.data.remote.BookingOptionsDto
import pe.clinicacardenas.pacientes.data.repository.ClinicRepository

/** Qué se está haciendo: una cita nueva o cambiar el horario de una existente. */
data class BookingTarget(val rescheduleId: Long? = null, val dentistId: Long? = null, val durationMin: Int? = null, val reason: String? = null)

data class BookingState(
    val loading: Boolean = true,
    val options: BookingOptionsDto? = null,
    val reasonCode: String? = null,
    val dentistId: Long? = null,
    val days: List<LocalDate> = emptyList(),
    val date: LocalDate? = null,
    val slots: List<String> = emptyList(),
    val works: Boolean = true,
    val loadingSlots: Boolean = false,
    val slot: String? = null,
    val notes: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    val done: String? = null,
)

/** Reserva y reprogramación de citas desde el teléfono (mínimo 2 h de anticipación; cambios hasta 12 h antes). */
class BookingViewModel(
    private val repo: ClinicRepository,
    private val requestAppointment: pe.clinicacardenas.pacientes.domain.usecase.RequestAppointmentUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(BookingState())
    val state: StateFlow<BookingState> = _state.asStateFlow()
    private var target = BookingTarget()
    private var slotsJob: Job? = null

    fun start(t: BookingTarget) {
        target = t
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val days = (0 until 21).map { today.plus(DatePeriod(days = it)) }.filter { it.dayOfWeek.ordinal != 6 }
        _state.update { BookingState(days = days, date = days.first(), dentistId = t.dentistId) }
        viewModelScope.launch {
            try {
                val o = repo.bookingOptions()
                val reason = when {
                    t.durationMin != null -> (o.reasons.firstOrNull { it.durationMin == t.durationMin } ?: o.reasons.first()).code
                    else -> null
                }
                _state.update {
                    it.copy(
                        loading = false,
                        options = o,
                        reasonCode = reason,
                        dentistId = it.dentistId ?: o.dentists.singleOrNull()?.id,
                    )
                }
                loadSlots()
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    val isReschedule get() = target.rescheduleId != null

    fun reason(code: String) { _state.update { it.copy(reasonCode = code, slot = null) }; loadSlots() }
    fun dentist(id: Long) { _state.update { it.copy(dentistId = id, slot = null) }; loadSlots() }
    fun date(d: LocalDate) { _state.update { it.copy(date = d, slot = null) }; loadSlots() }
    fun slot(s: String) = _state.update { it.copy(slot = s, error = null) }
    fun notes(n: String) = _state.update { it.copy(notes = n.take(300)) }

    private fun loadSlots() {
        val s = _state.value
        val dentist = s.dentistId ?: return
        val reason = s.reasonCode ?: return
        val date = s.date ?: return
        slotsJob?.cancel()
        _state.update { it.copy(loadingSlots = true, slots = emptyList()) }
        slotsJob = viewModelScope.launch {
            try {
                val r = repo.bookingSlots(dentist, date.toString(), reason, target.rescheduleId)
                _state.update { it.copy(slots = r.slots, works = r.works, loadingSlots = false) }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.message, loadingSlots = false) }
            }
        }
    }

    fun submit() {
        val s = _state.value
        val slot = s.slot ?: return _state.update { it.copy(error = "Elige un horario.") }
        val dentist = s.dentistId ?: return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val msg = if (target.rescheduleId != null) repo.reschedule(target.rescheduleId!!, dentist, slot)
                else bookOfflineFirst(s, dentist, slot)
                _state.update { it.copy(saving = false, done = msg) }
            } catch (e: ApiException) {
                _state.update { it.copy(saving = false, error = e.message) }
                loadSlots()
            } catch (e: pe.clinicacardenas.pacientes.domain.error.DomainError) {
                // La reserva ya quedó guardada en el teléfono; el error fue al actualizar la lista.
                _state.update { it.copy(saving = false, done = "Tu reserva quedó guardada. ${e.message}") }
            }
        }
    }

    /**
     * Reserva nueva, offline-first: se guarda primero en el teléfono (id provisional, marcada como pendiente) y se
     * envía en el acto si hay red; si no, sale en la próxima sincronización sin duplicarse (Idempotency-Key).
     */
    private suspend fun bookOfflineFirst(s: BookingState, dentistId: Long, slot: String): String {
        val o = s.options
        val d = o?.dentists?.firstOrNull { it.id == dentistId }
        val r = o?.reasons?.firstOrNull { it.code == s.reasonCode }
        val (_, result) = requestAppointment(
            pe.clinicacardenas.pacientes.domain.model.BookingRequest(
                dentistId = dentistId,
                dentistName = d?.fullName ?: "Odontólogo",
                dentistSpecialty = d?.specialty ?: "",
                dentistColor = d?.color ?: "#00A3D4",
                startAt = slot,
                durationMin = r?.durationMin ?: 30,
                reasonCode = s.reasonCode!!,
                reasonLabel = r?.label ?: "Cita",
                notes = s.notes.ifBlank { null },
            ),
        )
        return when {
            result.offline -> "Tu reserva quedó guardada en el teléfono y se enviará cuando vuelva la conexión."
            result.failed > 0 -> "No se pudo reservar ese horario. Revisa el aviso en Inicio para reintentar o descartar."
            result.retried > 0 -> "La clínica no respondió; reintentaremos el envío en unos segundos."
            else -> "¡Listo! Tu cita quedó reservada."
        }
    }
}
