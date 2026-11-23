package pe.clinicacardenas.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pe.clinicacardenas.care.CareService;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.security.CurrentUser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Conversaciones de WhatsApp entre la clínica y cualquier paciente, en los dos sentidos:
 *   - Lo que escribe el paciente llega por el microservicio de notificaciones. "1" / "2" los resuelve la agenda; lo
 *     demás lo contesta el asistente virtual según el parámetro asistente.modo, o lo atiende el personal.
 *   - El personal responde desde la web (página Mensajes); al escribir, el asistente se pausa en esa conversación.
 */
@Service
public class ConversationService {
    private static final String ASISTENTE = "ASISTENTE";
    private static final String AUTOMATICO = "AUTOMATICO";
    private static final String ENVIADO = "ENVIADO";
    private static final String PERSONAL = "PERSONAL";
    private static final String ACCION = "accion";
    private static final String ASISTENTE_MODO = "asistente.modo";
    private static final String ASISTENTE_PAUSA_MINUTOS = "asistente.pausa_minutos";
    private static final String DISPLAY_NAME = "display_name";
    private static final String PATIENT_ID = "patient_id";
    private static final String RESPUESTA = "respuesta";
    private static final String UPDATE_WA_THREADS_SET_NEEDS_ATTENTION_TR = "update wa_threads set needs_attention = true where phone = ?";


    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    public static final List<String> MODES = List.of(AUTOMATICO, "FUERA_HORARIO", "APAGADO");

    private final org.springframework.beans.factory.ObjectProvider<pe.clinicacardenas.crm.LeadService> leadsProvider;
    private final JdbcTemplate jdbc;
    private final CareService care;
    private final AssistantService assistant;
    private final KnowledgeBase kb;
    private final LanguageModel llm;
    private final AuditService audit;
    private final ObjectMapper json;
    private final String notifUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public ConversationService(JdbcTemplate jdbc, CareService care, AssistantService assistant, KnowledgeBase kb, LanguageModel llm,
                               AuditService audit, ObjectMapper json, @Value("${app.notifications.url}") String notifUrl, org.springframework.beans.factory.ObjectProvider<pe.clinicacardenas.crm.LeadService> leadsProvider) {
        this.leadsProvider = leadsProvider;
        this.jdbc = jdbc;
        this.care = care;
        this.assistant = assistant;
        this.kb = kb;
        this.llm = llm;
        this.audit = audit;
        this.json = json;
        this.notifUrl = notifUrl.replaceAll("/+$", "");
    }

    public record SendRequest(String text) {}
    public record ThreadUpdate(Boolean aiEnabled, Boolean attended) {}
    public record SettingsRequest(String mode, Integer pauseMinutes) {}
    public record FaqRequest(String question, String answer, Boolean active) {}
    public record TestRequest(String text, Long patientId) {}

    static String phone(String raw) {
        String d = raw == null ? "" : raw.replaceAll("\\D", "");
        if (d.startsWith("51") && d.length() == 11) d = d.substring(2);
        return d.matches("9\\d{8}") ? d : null;
    }

    // ------------------------------------------------------------------ parámetros

    public String setting(String key, String def) {
        List<String> v = jdbc.queryForList("select v from app_settings where k = ?", String.class, key);
        return v.isEmpty() ? def : v.get(0);
    }

    private void putSetting(String key, String value) {
        int n = jdbc.update("update app_settings set v = ?, updated_at = ?, updated_by = ? where k = ?", value, Dates.now(), CurrentUser.usernameOrSystem(), key);
        if (n == 0) jdbc.update("insert into app_settings (k, v, updated_at, updated_by) values (?, ?, ?, ?)", key, value, Dates.now(), CurrentUser.usernameOrSystem());
    }

    public Map<String, Object> settings() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mode", setting(ASISTENTE_MODO, AUTOMATICO));
        m.put("pauseMinutes", Integer.parseInt(setting(ASISTENTE_PAUSA_MINUTOS, "120")));
        m.put("openNow", kb.openNow(Dates.now()));
        m.put("schedule", kb.horarioTexto());
        m.put("model", llm.status());
        m.put("knowledgeDocs", kb.docs().size());
        return m;
    }

    public Map<String, Object> updateSettings(SettingsRequest r) {
        if (r.mode() != null) {
            if (!MODES.contains(r.mode())) throw ApiException.field("mode", "Elige automático, fuera de horario o apagado.");
            putSetting(ASISTENTE_MODO, r.mode());
        }
        if (r.pauseMinutes() != null) {
            if (r.pauseMinutes() < 5 || r.pauseMinutes() > 1440) throw ApiException.field("pauseMinutes", "Entre 5 minutos y 24 horas.");
            putSetting(ASISTENTE_PAUSA_MINUTOS, String.valueOf(r.pauseMinutes()));
        }
        audit.register(ASISTENTE, 0L, "CONFIGURAR", "Modo " + setting(ASISTENTE_MODO, "") + " · pausa " + setting(ASISTENTE_PAUSA_MINUTOS, "") + " min");
        return settings();
    }

    // ------------------------------------------------------------------ hilo y mensajes

    private Long patientOf(String phone) {
        List<Long> ids = jdbc.queryForList("""
                select id from patients where replace(replace(phone, ' ', ''), '-', '') = ? and active = true order by id desc
                """, Long.class, phone);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Map<String, Object> thread(String phone) {
        List<Map<String, Object>> t = jdbc.queryForList("select * from wa_threads where phone = ?", phone);
        return t.isEmpty() ? null : t.get(0);
    }

    private Map<String, Object> ensureThread(String phone) {
        Map<String, Object> t = thread(phone);
        Long pid = patientOf(phone);
        String name = pid == null ? null : jdbc.queryForObject("select concat(first_name, ' ', last_name) from patients where id = ?", String.class, pid);
        if (t == null) {
            jdbc.update("insert into wa_threads (phone, patient_id, display_name, last_message_at) values (?, ?, ?, ?)", phone, pid, name, Dates.now());
        } else if (!Objects.equals(t.get(PATIENT_ID) == null ? null : ((Number) t.get(PATIENT_ID)).longValue(), pid)) {
            jdbc.update("update wa_threads set patient_id = ?, display_name = ? where phone = ?", pid, name, phone);
        }
        Map<String, Object> created = thread(phone);
        return created == null ? Map.of() : created;
    }

    private long storeMessage(String phone, String direction, String source, String body, String intent, String engine, String status, String author) {
        LocalDateTime now = Dates.now();
        jdbc.update("""
                insert into wa_messages (phone, direction, source, body, intent, engine, status, author, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, phone, direction, source, body, intent, engine, status, author, now);
        String preview = body.replaceAll("[*_~]", "").replaceAll("\\s+", " ");
        if (preview.length() > 190) preview = preview.substring(0, 190) + "…";
        jdbc.update("update wa_threads set last_message_at = ?, last_preview = ?" + ("IN".equals(direction) ? ", unread = unread + 1" : "") + " where phone = ?",
                now, prefix(source) + preview, phone);
        Long id = jdbc.queryForObject("select max(id) from wa_messages where phone = ?", Long.class, phone);
        return id == null ? 0 : id;
    }

    private static String prefix(String source) {
        return switch (source) {
            case "IA" -> "🤖 ";
            case PERSONAL -> "Tú: ";
            case "SISTEMA" -> "🔔 ";
            default -> "";
        };
    }

    private List<Map<String, Object>> recent(String phone, int n) {
        List<Map<String, Object>> l = new ArrayList<>(jdbc.queryForList(
                "select direction, body from wa_messages where phone = ? order by id desc limit ?", phone, n));
        Collections.reverse(l);
        return l;
    }

    /** ¿Responde el asistente en esta conversación ahora? */
    private String silenceReason(Map<String, Object> t) {
        String mode = setting(ASISTENTE_MODO, AUTOMATICO);
        if ("APAGADO".equals(mode)) return "El asistente está apagado: responde el personal.";
        if ("FUERA_HORARIO".equals(mode) && kb.openNow(Dates.now())) return "En horario de atención responde el personal.";
        if (!Boolean.TRUE.equals(t.get("ai_enabled"))) return "El asistente está desactivado en esta conversación.";
        Object until = t.get("manual_until");
        LocalDateTime u = until == null ? null : Dates.toLocalDateTime(until);
        if (u != null && u.isAfter(Dates.now())) return "El personal está atendiendo esta conversación.";
        return null;
    }

    private BookingFlow booking;

    @org.springframework.beans.factory.annotation.Autowired
    void setBooking(BookingFlow booking) {
        this.booking = booking;
    }

    /** Mensaje entrante del paciente (lo reenvía el microservicio de notificaciones). Devuelve la respuesta a enviar. */
    public Map<String, Object> inbound(CareService.ReplyRequest r) {
        String phone = phone(r.telefono());
        String text = r.texto() == null ? "" : r.texto().trim();
        if (phone == null || text.isEmpty()) return Map.of(ACCION, "IGNORADO");
        Map<String, Object> t = ensureThread(phone);
        storeMessage(phone, "IN", "PACIENTE", text, null, null, null, null);
        // v2.7: quien escribe sin ser paciente entra al embudo de ventas como lead nuevo
        if (t.get(PATIENT_ID) == null) leadsProvider.ifAvailable(l -> l.fromWhatsApp(phone, text));

        // Reserva por WhatsApp en curso: los números que escribe son opciones de fecha/hora, no "1 confirma / 2 reprograma".
        Long bookingPid = t.get(PATIENT_ID) == null ? null : ((Number) t.get(PATIENT_ID)).longValue();
        if (booking != null && silenceReason(t) == null && booking.active(bookingPid)) {
            String reply;
            try {
                reply = booking.handle(bookingPid, text);
            } catch (RuntimeException e) {
                log.warn("Reserva por WhatsApp falló para {}: {}", phone, e.toString());
                jdbc.update(UPDATE_WA_THREADS_SET_NEEDS_ATTENTION_TR, phone);
                reply = "Tuve un problema al revisar la agenda. Recepción te escribirá para terminar tu reserva.";
            }
            storeMessage(phone, "OUT", "IA", reply, "RESERVA", "agenda", ENVIADO, null);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put(ACCION, ASISTENTE);
            out.put("intencion", "RESERVA");
            out.put(RESPUESTA, reply);
            return out;
        }

        // "1" confirma y "2" pide reprogramar: lo resuelve la agenda como siempre.
        Map<String, Object> agendaReply = care.whatsappReply(r);
        Object respuesta = agendaReply.get(RESPUESTA);
        if (respuesta != null) {
            storeMessage(phone, "OUT", "SISTEMA", String.valueOf(respuesta), String.valueOf(agendaReply.get(ACCION)), "agenda", ENVIADO, null);
            if ("REPROGRAMAR".equals(agendaReply.get(ACCION))) jdbc.update(UPDATE_WA_THREADS_SET_NEEDS_ATTENTION_TR, phone);
            return agendaReply;
        }

        String silence = silenceReason(t);
        if (silence != null) {
            jdbc.update(UPDATE_WA_THREADS_SET_NEEDS_ATTENTION_TR, phone);
            Map<String, Object> out = new LinkedHashMap<>(agendaReply);
            out.put(ACCION, PERSONAL);
            out.put("motivo", silence);
            out.remove(RESPUESTA);
            return out;
        }
        Long pid = t.get(PATIENT_ID) == null ? null : ((Number) t.get(PATIENT_ID)).longValue();
        AssistantService.Reply rep;
        try {
            rep = assistant.answer(text, pid, recent(phone, 8), false);
        } catch (RuntimeException e) {
            log.warn("El asistente falló con el mensaje de {}: {}", phone, e.toString());
            jdbc.update(UPDATE_WA_THREADS_SET_NEEDS_ATTENTION_TR, phone);
            return Map.of(ACCION, PERSONAL, "motivo", "Error del asistente");
        }
        storeMessage(phone, "OUT", "IA", rep.text(), rep.intent(), rep.engine(), ENVIADO, null);
        if (rep.needsHuman()) jdbc.update(UPDATE_WA_THREADS_SET_NEEDS_ATTENTION_TR, phone);
        if (rep.pauseAi()) jdbc.update("update wa_threads set manual_until = ? where phone = ?", Dates.now().plusMinutes(pause()), phone);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(ACCION, ASISTENTE);
        out.put("intencion", rep.intent());
        out.put(RESPUESTA, rep.text());
        return out;
    }

    private int pause() {
        try { return Integer.parseInt(setting(ASISTENTE_PAUSA_MINUTOS, "120")); } catch (NumberFormatException e) { return 120; }
    }

    /** El personal escribe al paciente desde la web. */
    public Map<String, Object> send(String rawPhone, SendRequest r) {
        String phone = phone(rawPhone);
        if (phone == null) throw ApiException.field("phone", "El número debe ser un celular de 9 dígitos.");
        String text = r == null || r.text() == null ? "" : r.text().trim();
        if (text.isEmpty()) throw ApiException.field("text", "Escribe el mensaje.");
        if (text.length() > 1500) throw ApiException.field("text", "Máximo 1500 caracteres.");
        Map<String, Object> t = ensureThread(phone);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clave", "chat-" + UUID.randomUUID());
        body.put("canal", "WHATSAPP");
        body.put("destino", phone);
        body.put("cuerpo", text);
        body.put("tipo", "MENSAJE");
        if (t.get(DISPLAY_NAME) != null) body.put("paciente", Map.of("id", t.get(PATIENT_ID), "nombre", t.get(DISPLAY_NAME), "demo", false));
        String status = "FALLIDO";
        String error = null;
        try {
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(notifUrl + "/v1/mensajes"))
                    .timeout(Duration.ofSeconds(12)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
            Map<?, ?> m = res.body() == null || res.body().isBlank() ? Map.of() : json.readValue(res.body(), Map.class);
            if (res.statusCode() / 100 == 2 && !"FALLIDO".equals(m.get("estado"))) status = ENVIADO;
            else error = m.get("error") == null ? "HTTP " + res.statusCode() : String.valueOf(m.get("error"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            error = "El servicio de mensajes no respondió";
        } catch (Exception e) {
            error = "El servicio de mensajes no respondió";
        }
        long id = storeMessage(phone, "OUT", PERSONAL, text, null, null, status, CurrentUser.usernameOrSystem());
        jdbc.update("update wa_threads set unread = 0, needs_attention = false, manual_until = ? where phone = ?",
                Dates.now().plusMinutes(pause()), phone);
        if (!ENVIADO.equals(status)) throw new ApiException(HttpStatus.BAD_GATEWAY, "ENVIO_FALLIDO", "No se pudo enviar: " + error);
        return Map.of("id", id, "status", status);
    }

    // ------------------------------------------------------------------ consultas para la web

    public Map<String, Object> threads(String q) {
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        List<Map<String, Object>> list = jdbc.queryForList("""
                select t.phone, t.patient_id as patientId, coalesce(t.display_name, '') as name, t.ai_enabled as aiEnabled,
                       t.manual_until as manualUntil, t.needs_attention as needsAttention, t.unread, t.last_message_at as lastMessageAt,
                       t.last_preview as lastPreview
                  from wa_threads t
                 where (? is null or lower(coalesce(t.display_name, '')) like ? or t.phone like ?)
                 order by t.last_message_at desc limit 200
                """, like, like, like);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", list);
        m.putAll(summary());
        return m;
    }

    public Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unread", jdbc.queryForObject("select coalesce(sum(unread), 0) from wa_threads", Long.class));
        m.put("attention", jdbc.queryForObject("select count(*) from wa_threads where needs_attention = true", Long.class));
        m.put("mode", setting(ASISTENTE_MODO, AUTOMATICO));
        return m;
    }

    public Map<String, Object> conversation(String rawPhone) {
        String phone = phone(rawPhone);
        if (phone == null) throw ApiException.notFound("La conversación");
        Map<String, Object> t = ensureThread(phone);
        jdbc.update("update wa_threads set unread = 0 where phone = ?", phone);
        List<Map<String, Object>> msgs = new ArrayList<>(jdbc.queryForList("""
                select concat('m', id) as id, direction, source, body, intent, engine, status, author, created_at as at
                  from wa_messages where phone = ? order by id desc limit 300
                """, phone));
        // Avisos automáticos que el sistema envió por WhatsApp a ese paciente (recordatorios, cambios de cita...).
        if (t.get(PATIENT_ID) != null) {
            msgs.addAll(jdbc.queryForList("""
                    select concat('n', id) as id, 'OUT' as direction, 'SISTEMA' as source, body, type as intent, null as engine,
                           status, null as author, coalesce(sent_at, created_at) as at
                      from notifications
                     where patient_id = ? and channel = 'WHATSAPP' and type <> 'CODIGO_ACCESO' and operation <> 'ANULAR'
                       and sent_at is not null and status in ('ENVIADO', 'ENTREGADO', 'LEIDO', 'FALLIDO')
                     order by id desc limit 100
                    """, t.get(PATIENT_ID)));
        }
        msgs.sort(Comparator.comparing(x -> Dates.toLocalDateTime(x.get("at"))));
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> th = new LinkedHashMap<>();
        th.put("phone", phone);
        th.put("patientId", t.get(PATIENT_ID));
        th.put("name", t.get(DISPLAY_NAME));
        th.put("aiEnabled", t.get("ai_enabled"));
        th.put("manualUntil", t.get("manual_until"));
        th.put("needsAttention", t.get("needs_attention"));
        th.put("silence", silenceReason(t));
        m.put("thread", th);
        m.put("messages", msgs);
        if (t.get(PATIENT_ID) != null) {
            Long pid = ((Number) t.get(PATIENT_ID)).longValue();
            m.put("upcoming", assistant.upcoming(pid).stream().map(a -> Map.of("id", a.id, "startAt", a.startAt, "dentist", a.dentist.getFullName(),
                    "reason", a.reason, "status", a.status.name())).toList());
        }
        return m;
    }

    public Map<String, Object> updateThread(String rawPhone, ThreadUpdate r) {
        String phone = phone(rawPhone);
        if (phone == null || thread(phone) == null) throw ApiException.notFound("La conversación");
        if (r.aiEnabled() != null) {
            jdbc.update("update wa_threads set ai_enabled = ?" + (Boolean.TRUE.equals(r.aiEnabled()) ? ", manual_until = null" : "") + " where phone = ?", r.aiEnabled(), phone);
        }
        if (Boolean.TRUE.equals(r.attended())) jdbc.update("update wa_threads set needs_attention = false where phone = ?", phone);
        return conversation(phone);
    }

    // ------------------------------------------------------------------ base de conocimiento

    public List<Map<String, Object>> faq() {
        return jdbc.queryForList("select id, question, answer, active, updated_at as updatedAt, updated_by as updatedBy from assistant_faq order by id");
    }

    private void validFaq(FaqRequest r) {
        Map<String, String> e = new LinkedHashMap<>();
        if (r.question() == null || r.question().trim().length() < 5) e.put("question", "Escribe la pregunta (mínimo 5 caracteres).");
        else if (r.question().length() > 300) e.put("question", "Máximo 300 caracteres.");
        if (r.answer() == null || r.answer().trim().length() < 5) e.put("answer", "Escribe la respuesta.");
        else if (r.answer().length() > 1500) e.put("answer", "Máximo 1500 caracteres.");
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa la pregunta frecuente.", e);
    }

    public List<Map<String, Object>> addFaq(FaqRequest r) {
        validFaq(r);
        jdbc.update("insert into assistant_faq (question, answer, active, created_at, updated_at, updated_by) values (?, ?, ?, ?, ?, ?)",
                r.question().trim(), r.answer().trim(), r.active() == null || r.active(), Dates.now(), Dates.now(), CurrentUser.usernameOrSystem());
        kb.invalidate();
        return faq();
    }

    public List<Map<String, Object>> updateFaq(Long id, FaqRequest r) {
        validFaq(r);
        int n = jdbc.update("update assistant_faq set question = ?, answer = ?, active = ?, updated_at = ?, updated_by = ? where id = ?",
                r.question().trim(), r.answer().trim(), r.active() == null || r.active(), Dates.now(), CurrentUser.usernameOrSystem(), id);
        if (n == 0) throw ApiException.notFound("La pregunta frecuente");
        kb.invalidate();
        return faq();
    }

    public List<Map<String, Object>> deleteFaq(Long id) {
        jdbc.update("delete from assistant_faq where id = ?", id);
        kb.invalidate();
        return faq();
    }

    /** Probador de la web: qué respondería el asistente, sin enviar nada ni ejecutar acciones. */
    public Map<String, Object> test(TestRequest r) {
        if (r.text() == null || r.text().isBlank()) throw ApiException.field("text", "Escribe un mensaje de prueba.");
        long t0 = System.currentTimeMillis();
        AssistantService.Reply rep = assistant.answer(r.text().trim(), r.patientId(), List.of(), true);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("text", rep.text());
        m.put("intent", rep.intent());
        m.put("needsHuman", rep.needsHuman());
        m.put("engine", rep.engine());
        m.put("sources", rep.sources());
        m.put("ms", System.currentTimeMillis() - t0);
        return m;
    }
}
