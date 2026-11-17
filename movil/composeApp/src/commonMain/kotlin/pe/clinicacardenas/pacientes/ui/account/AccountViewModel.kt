package pe.clinicacardenas.pacientes.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.clinicacardenas.pacientes.data.remote.AccountDto
import pe.clinicacardenas.pacientes.data.remote.ApiException
import pe.clinicacardenas.pacientes.data.remote.OnlineChargeDto
import pe.clinicacardenas.pacientes.data.remote.PayRequest
import pe.clinicacardenas.pacientes.data.remote.PayResultDto
import pe.clinicacardenas.pacientes.data.repository.ClinicRepository

data class AccountState(
    val loading: Boolean = true,
    val data: AccountDto? = null,
    val error: String? = null,
    val paying: Boolean = false,
    val payError: String? = null,
    val paid: PayResultDto? = null,
    /** Mercado Pago activo en el servidor: se paga en su página segura (tarjeta o Yape). */
    val online: Boolean = false,
    val onlineTest: Boolean = false,
    val charge: OnlineChargeDto? = null,
)

/** Estado de cuenta del paciente: cargos, pagos y saldo pendiente. */
class AccountViewModel(private val repo: ClinicRepository) : ViewModel() {
    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    private var watch: Job? = null

    init {
        load()
        viewModelScope.launch {
            val c = repo.onlineConfig()
            _state.update { it.copy(online = c.enabled, onlineTest = c.testMode) }
        }
    }

    /** Crea el link de Mercado Pago, lo abre y espera la aprobación (consulta cada 4 s, hasta 20 minutos). */
    fun payOnline(amount: Double, open: (String) -> Unit) {
        _state.update { it.copy(paying = true, payError = null) }
        viewModelScope.launch {
            try {
                val c = repo.onlinePay(amount)
                _state.update { it.copy(charge = c) }
                c.checkoutUrl?.let(open)
                watch?.cancel()
                watch = viewModelScope.launch {
                    repeat(300) {
                        delay(4000)
                        val s = runCatching { repo.onlineStatus(c.id) }.getOrNull() ?: return@repeat
                        _state.update { it.copy(charge = s) }
                        if (s.status != "PENDIENTE") {
                            if (s.status == "APROBADO") {
                                _state.update { it.copy(paid = PayResultDto(operationId = "MP-" + (s.mpPaymentId ?: ""), amount = s.amount, message = "Pago aprobado por Mercado Pago. ¡Gracias!")) }
                                load()
                            }
                            return@launch
                        }
                    }
                }
            } catch (e: ApiException) {
                _state.update { it.copy(payError = e.fields.values.firstOrNull() ?: e.message) }
            } finally {
                _state.update { it.copy(paying = false) }
            }
        }
    }

    fun load() {
        _state.update { it.copy(loading = it.data == null, error = null) }
        viewModelScope.launch {
            try {
                val data = repo.account()
                _state.update { it.copy(data = data) }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    /** Pago en línea simulado (Yape, Plin o tarjeta). Si se aprueba, la clínica emite la boleta SUNAT al instante. */
    fun pay(body: PayRequest) {
        _state.update { it.copy(paying = true, payError = null) }
        viewModelScope.launch {
            try {
                val r = repo.pay(body)
                _state.update { it.copy(paid = r) }
                load()
            } catch (e: ApiException) {
                _state.update { it.copy(payError = e.fields.values.firstOrNull() ?: e.message) }
            } finally {
                _state.update { it.copy(paying = false) }
            }
        }
    }

    fun clearPay() {
        watch?.cancel()
        _state.update { it.copy(paid = null, payError = null, charge = null) }
    }

    suspend fun invoiceUrl(id: Long): String? = runCatching { repo.invoiceUrl(id) }.getOrNull()
}
