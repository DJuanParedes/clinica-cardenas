package pe.clinicacardenas.assistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Base de conocimiento del asistente: se arma con los datos reales de la clínica (dirección, horarios de cada
 * odontólogo, precios del catálogo de procedimientos, medios de pago y reglas de reserva) y con las preguntas
 * frecuentes que el personal mantiene desde la web. Se recalcula cada minuto.
 */
@Component
public class KnowledgeBase {

    static final String[] DIAS = {"", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"};

    /** Cómo llaman los pacientes a cada tratamiento: se agrega al documento para que la búsqueda lo encuentre. */
    private static final Map<String, String> SINONIMOS = Map.ofEntries(
            Map.entry("profilaxis", "limpieza dental limpieza de dientes"),
            Map.entry("destartraje", "sarro limpieza profunda"),
            Map.entry("resina", "curacion empaste calza tapar caries"),
            Map.entry("restauracion", "curacion empaste calza"),
            Map.entry("endodoncia", "tratamiento de conducto matar el nervio"),
            Map.entry("exodoncia", "extraccion sacar muela sacar diente"),
            Map.entry("extraccion", "sacar muela sacar diente"),
            Map.entry("cirugia", "muela del juicio tercera molar"),
            Map.entry("ortodoncia", "brackets frenillos aparatos"),
            Map.entry("blanqueamiento", "aclarar dientes dientes blancos"),
            Map.entry("radiografia", "placa rayos x"),
            Map.entry("corona", "funda"),
            Map.entry("protesis", "dentadura placa dental postiza"),
            Map.entry("consulta", "evaluacion revision chequeo"),
            Map.entry("periodon", "encias sangrado de encias"),
            Map.entry("sellante", "sellador prevencion caries ninos"),
            Map.entry("fluor", "fluorizacion prevencion"));

    static String sinonimos(String name) {
        String n = TextSearch.normalize(name);
        StringBuilder sb = new StringBuilder();
        SINONIMOS.forEach((k, v) -> { if (n.contains(k)) sb.append(' ').append(v); });
        return sb.toString();
    }

    private final JdbcTemplate jdbc;
    final String clinic;
    final String address;
    final String phone;
    private final java.util.concurrent.atomic.AtomicReference<List<TextSearch.Doc>> cache = new java.util.concurrent.atomic.AtomicReference<>(List.of());
    private volatile long cachedAt = 0;

    public KnowledgeBase(JdbcTemplate jdbc,
                         @Value("${app.company.trade-name:Clínica Dental Cárdenas}") String clinic,
                         @Value("${app.company.address:Urb. La Floresta, Chaclacayo - Lima}") String address,
                         @Value("${app.notifications.clinic-phone:987 654 000}") String phone) {
        this.jdbc = jdbc;
        this.clinic = clinic;
        this.address = address;
        this.phone = phone;
    }

    public void invalidate() { cachedAt = 0; }

    public List<TextSearch.Doc> docs() {
        if (System.currentTimeMillis() - cachedAt < 60_000 && !cache.get().isEmpty()) return cache.get();
        List<TextSearch.Doc> d = new ArrayList<>();
        d.add(new TextSearch.Doc("clinica", "CLINICA", "Dirección, ubicación y teléfono de la clínica",
                clinic + " queda en " + address + ". Teléfono y WhatsApp: " + phone + "."));
        d.add(new TextSearch.Doc("horario", "HORARIO", "Horario de atención", horarioTexto()));
        d.add(new TextSearch.Doc("pagos", "PAGOS", "Medios de pago, pagar, saldo y cuotas",
                "Se puede pagar en recepción con efectivo, tarjeta, Yape, Plin o transferencia, y también desde la app de la clínica "
                        + "(Mi cuenta → Pagar). Por cada pago se emite boleta o factura electrónica. Los tratamientos largos se pueden pagar en cuotas."));
        d.add(new TextSearch.Doc("reservas", "RESERVAS", "Reservar, reprogramar o cancelar citas",
                "Las citas se reservan desde la app de la clínica con al menos 2 horas de anticipación y hasta 60 días antes. "
                        + "Se pueden reprogramar o cancelar desde la app hasta 12 horas antes; después hay que comunicarse con recepción. "
                        + "Responder 1 a un recordatorio confirma la cita y 2 pide reprogramar."));
        for (Map<String, Object> r : jdbc.queryForList(
                "select d.id, u.full_name, d.specialty from dentists d join users u on u.id = d.user_id where d.active = true and u.active = true")) {
            d.add(new TextSearch.Doc("odontologo-" + r.get("id"), "ODONTOLOGO", r.get("full_name") + " · " + r.get("specialty"),
                    r.get("full_name") + " atiende " + r.get("specialty") + ". " + horarioDe(((Number) r.get("id")).longValue())));
        }
        for (Map<String, Object> r : jdbc.queryForList("select code, name, category, price from procedures where active = true")) {
            BigDecimal price = ((BigDecimal) r.get("price")).setScale(2, RoundingMode.HALF_UP);
            String name = (String) r.get("name");
            d.add(new TextSearch.Doc("proc-" + r.get("code"), "PRECIO", name + " ·" + sinonimos(name),
                    name + " (" + r.get("category") + "): S/ " + price.toPlainString() + ". Precio referencial; el costo final se confirma en la evaluación."));
        }
        for (Map<String, Object> r : jdbc.queryForList("select id, question, answer from assistant_faq where active = true")) {
            d.add(new TextSearch.Doc("faq-" + r.get("id"), "FAQ", (String) r.get("question"), (String) r.get("answer")));
        }
        cache.set(List.copyOf(d));
        cachedAt = System.currentTimeMillis();
        return cache.get();
    }

    String horarioDe(long dentistId) {
        Map<Integer, List<String>> byDay = new TreeMap<>();
        jdbc.query("select weekday, start_time, end_time from dentist_schedules where dentist_id = ? order by weekday, start_time",
                rs -> { byDay.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>()).add(rs.getString(2).substring(0, 5) + " a " + rs.getString(3).substring(0, 5)); },
                dentistId);
        if (byDay.isEmpty()) return "Por ahora no tiene horario de atención registrado.";
        List<String> parts = new ArrayList<>();
        byDay.forEach((day, blocks) -> parts.add(DIAS[day] + " " + String.join(" y ", blocks)));
        return "Horario: " + String.join("; ", parts) + ".";
    }

    /** Horario general de la clínica: la unión de los horarios de todos los odontólogos por día. */
    String horarioTexto() {
        Map<Integer, String[]> range = new TreeMap<>();
        jdbc.query("""
                select s.weekday, min(s.start_time), max(s.end_time) from dentist_schedules s
                  join dentists d on d.id = s.dentist_id join users u on u.id = d.user_id
                 where d.active = true and u.active = true group by s.weekday order by s.weekday
                """, rs -> { range.put(rs.getInt(1), new String[]{rs.getString(2).substring(0, 5), rs.getString(3).substring(0, 5)}); });
        if (range.isEmpty()) return "El horario de atención se confirma por teléfono al " + phone + ".";
        List<String> parts = new ArrayList<>();
        range.forEach((day, r) -> parts.add(DIAS[day] + " de " + r[0] + " a " + r[1]));
        Set<Integer> closed = new TreeSet<>(List.of(1, 2, 3, 4, 5, 6, 7));
        closed.removeAll(range.keySet());
        String cerrado = closed.isEmpty() ? "" : " No atendemos " + String.join(" ni ", closed.stream().map(x -> DIAS[x]).toList()) + ".";
        return "Atendemos " + String.join("; ", parts) + "." + cerrado;
    }

    /** ¿La clínica está atendiendo en este momento? (algún odontólogo activo tiene un bloque que incluye la hora). */
    boolean openNow(java.time.LocalDateTime now) {
        Integer n = jdbc.queryForObject("""
                select count(*) from dentist_schedules s join dentists d on d.id = s.dentist_id join users u on u.id = d.user_id
                 where d.active = true and u.active = true and s.weekday = ? and s.start_time <= ? and s.end_time > ?
                """, Integer.class, now.getDayOfWeek().getValue(), java.sql.Time.valueOf(now.toLocalTime().withNano(0)), java.sql.Time.valueOf(now.toLocalTime().withNano(0)));
        return n != null && n > 0;
    }
}
