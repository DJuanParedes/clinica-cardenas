package pe.clinicacardenas.billing;

import java.util.Map;
import java.util.Optional;

/**
 * Puerto hacia el servicio de facturación electrónica de SUNAT.
 * <ul>
 *   <li>{@link SunatSoapGateway} (modo {@code soap}, el predeterminado): envía el XML firmado y comprimido
 *       al servicio SOAP billService, igual que al entorno beta o de producción. En docker-compose apunta a la
 *       API simulada de SUNAT (servicio {@code sunat-sim}).</li>
 *   <li>{@link SimulatedSunatGateway} (modo {@code interno}): validación dentro del mismo backend, sin red.</li>
 * </ul>
 */
public interface SunatGateway {

    record Response(String status, String code, String description, String cdrXml, String environment) {}

    /** Envía el comprobante firmado (sendBill). */
    Response send(Invoice invoice, String xml);

    /** Consulta el CDR de un comprobante ya enviado (getStatusCdr). Vacío si SUNAT no lo tiene registrado. */
    default Optional<Response> status(Invoice invoice) {
        return Optional.empty();
    }

    /** Consulta RUC en el padrón. Vacío si no existe; lanza excepción si el servicio no responde. */
    default Optional<Map<String, Object>> lookupRuc(String ruc) {
        return Optional.empty();
    }
}
