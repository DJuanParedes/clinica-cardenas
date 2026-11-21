package pe.clinicacardenas.portal;

import org.springframework.stereotype.Component;
import pe.clinicacardenas.common.ApiException;

import java.security.SecureRandom;

/**
 * Pasarela de pagos simulada (como el entorno de pruebas de Culqi o Izipay).
 * Yape/Plin: aprueba con un código de aprobación de 6 dígitos (el "000000" se rechaza para probar el error).
 * Tarjeta: valida el número con Luhn; la tarjeta de prueba 4000 0000 0000 0002 se rechaza por fondos insuficientes.
 * Nunca se guarda el número de tarjeta: solo los últimos 4 dígitos en la referencia.
 */
@Component
public class SimulatedPaymentGateway {

    private final SecureRandom random = new SecureRandom();

    public record Charge(String method, String approvalCode, String cardNumber, String cardExpiry, String cardCvv) {}

    public record Result(String operationId, String reference) {}

    public Result charge(Charge c, java.math.BigDecimal amount) {
        String op = "SIM-" + (100000 + random.nextInt(900000));
        switch (c.method()) {
            case "YAPE", "PLIN" -> {
                String code = c.approvalCode() == null ? "" : c.approvalCode().replaceAll("\\D", "");
                if (code.length() != 6) throw ApiException.field("approvalCode", "Ingresa el código de aprobación de 6 dígitos de tu app " + label(c.method()) + ".");
                if ("000000".equals(code)) throw ApiException.badRequest("PAGO_RECHAZADO", "El código de aprobación venció o no es válido. Genera uno nuevo en " + label(c.method()) + ".");
                return new Result(op, label(c.method()) + " " + op);
            }
            case "TARJETA" -> {
                String pan = c.cardNumber() == null ? "" : c.cardNumber().replaceAll("\\D", "");
                if (pan.length() < 15 || pan.length() > 16 || !luhn(pan)) throw ApiException.field("cardNumber", "El número de tarjeta no es válido.");
                if (c.cardExpiry() == null || !c.cardExpiry().matches("(0[1-9]|1[0-2])/\\d{2}")) throw ApiException.field("cardExpiry", "Usa el formato MM/AA.");
                if (c.cardCvv() == null || !c.cardCvv().matches("\\d{3,4}")) throw ApiException.field("cardCvv", "El CVV tiene 3 o 4 dígitos.");
                if (pan.equals("4000000000000002")) throw ApiException.badRequest("PAGO_RECHAZADO", "Tu banco rechazó el pago por fondos insuficientes.");
                return new Result(op, "Tarjeta ****" + pan.substring(pan.length() - 4) + " " + op);
            }
            default -> throw ApiException.field("method", "Elige Yape, Plin o tarjeta.");
        }
    }

    private static String label(String m) {
        return "YAPE".equals(m) ? "Yape" : "Plin";
    }

    static boolean luhn(String pan) {
        int sum = 0;
        boolean dbl = false;
        for (int i = pan.length() - 1; i >= 0; i--) {
            int d = pan.charAt(i) - '0';
            if (dbl) { d *= 2; if (d > 9) d -= 9; }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }
}
