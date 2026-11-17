package pe.clinicacardenas.assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pe.clinicacardenas.agenda.*;
import pe.clinicacardenas.cash.CashService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.patient.Patient;
import pe.clinicacardenas.patient.PatientRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Asistente virtual de WhatsApp de la clínica (RAG + modelo de lenguaje).
 *
 *  1. Entiende la intención del mensaje (cita, horario, precio, pago, ubicación, urgencia, hablar con alguien...).
 *  2. Ejecuta solo acciones seguras: confirmar la próxima cita, registrar el pedido de reprogramación, consultar
 *     horarios libres y el saldo, o avisar a recepción. Nunca cancela ni cobra.
 *  3. Recupera lo relevante de la base de conocimiento (datos reales de la clínica + preguntas frecuentes) con BM25.
 *  4. Arma una respuesta base fiel a los datos. Si hay un modelo de lenguaje configurado, este la redacta de forma
 *     natural usando solo ese contexto; si no, o si falla, se envía la respuesta base.
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final Locale ES = new Locale("es", "PE");
    private static final DateTimeFormatter CUANDO = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'a las' h:mm a", ES);
    private static final DateTimeFormatter CORTO = DateTimeFormatter.ofPattern("EEE d/MM h:mm a", ES);

    public record Reply(String text, String intent, boolean needsHuman, boolean pauseAi, String engine, List<String> sources) {}

    private final KnowledgeBase kb;
    private final LanguageModel llm;
    private final AppointmentRepository appointments;
    private final DentistRepository dentists;
    private final AgendaService agenda;
    private final CashService cash;
    private final PatientRepository patients;
    private final JdbcTemplate jdbc;

    public AssistantService(KnowledgeBase kb, LanguageModel llm, AppointmentRepository appointments, DentistRepository dentists,
                            AgendaService agenda, CashService cash, PatientRepository patients, JdbcTemplate jdbc) {
        this.kb = kb;
        this.llm = llm;
        this.appointments = appointments;
        this.dentists = dentists;
        this.agenda = agenda;
        this.cash = cash;
        this.patients = patients;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ 1. intención

    static String intent(String text) {
        String n = TextSearch.normalize(text);
        if (n.isBlank()) return "OTRO";
        if (n.matches(".*\\b(urgen\\w*|emergencia\\w*|sangr\\w*|hinchad\\w*|hinchazon|inflamad\\w*|accidente|golpe\\w*|se me (rompio|cayo|partio)|me duele mucho|dolor (fuerte|intenso|insoportable))\\b.*")) return "URGENCIA";
        if (n.matches(".*\\b(hablar con|persona real|humano|asesor\\w*|recepcion(ista)?|que me llamen|llamenme|me pueden llamar|atencion al cliente|operador\\w*)\\b.*")) return "HUMANO";
        if (n.matches(".*\\b(cancel\\w*|anul\\w*|ya no (ire|voy|podre)|no (podre|voy a poder) (ir|asistir))\\b.*")) return "CANCELAR";
        if (n.matches(".*\\b(reprogram\\w*|cambiar (mi |la )?(cita|hora|fecha)|mover (mi |la )?cita|otro dia|otra hora|otra fecha|postergar|adelantar)\\b.*")) return "REPROGRAMAR";
        if (n.matches("^(si|ok|okey|confirmo|confirmado|claro|perfecto|de acuerdo)( (alli|ahi) estare| asistire| gracias)?$") || n.matches(".*\\b(confirmo|si asistire|ahi estare|alli estare|confirmar (mi )?(cita|asistencia))\\b.*")) return "CONFIRMAR";
        if (n.matches(".*\\b(mi cita|mis citas|tengo cita|cuando es|a que hora (es|tengo)|proxima cita|para cuando)\\b.*")) return "MI_CITA";
        if (n.matches(".*\\b(disponib\\w*|hay (espacio|cupo|turno)|cupos?|turnos?|separar (una )?cita|sacar (una )?cita|reservar|agendar|quiero (una )?cita|necesito (una )?cita|pedir (una )?cita|atender(me)? (hoy|manana))\\b.*")) return "DISPONIBILIDAD";
        if (n.matches(".*\\b(deuda|saldo|debo|debemos|mi cuenta|pendiente de pago)\\b.*")) return "PAGO";
        if (n.matches(".*\\b(cuanto|precio\\w*|costo\\w*|cuesta|vale|tarifa\\w*|cobran)\\b.*")) return "PRECIO";
        if (n.matches(".*\\b(pagar|pago\\w*|yape|plin|tarjeta|transferencia|cuota\\w*|boleta|factura)\\b.*")) return "PAGO";
        if (n.matches(".*\\b(horario\\w*|abren|cierran|hasta que hora|a que hora atienden|que dias atienden|atienden (hoy|manana|los|el|en)? ?(domingo|sabado|feriado|lunes|martes|miercoles|jueves|viernes|hoy|manana)\\w*)\\b.*")) return "HORARIO";
        if (n.matches(".*\\b(donde|direccion|ubicacion|ubicados|como llego|quedan|local|mapa|referencia)\\b.*")) return "UBICACION";
        if (n.matches("^(muchas )?(gracias|grax|thank\\w*|mil gracias|ok gracias|listo gracias)\\b.*")) return "GRACIAS";
        if (n.matches("^(chau|chao|adios|hasta luego|nos vemos|bye)\\b.*")) return "DESPEDIDA";
        if (n.matches("^(hola|buenas|buenos dias|buenas tardes|buenas noches|hey|holi|alo)\\b.*") && n.split(" ").length <= 4) return "SALUDO";
        return "OTRO";
    }

    // ------------------------------------------------------------------ 2-4. respuesta

    /**
     * @param simulate true para el probador de la web: no ejecuta acciones (no confirma ni marca reprogramación).
     */
    public Reply answer(String phone, String text, Long patientId, List<Map<String, Object>> history, boolean simulate) {
        String intent = intent(text);
        Patient p = patientId == null ? null : patients.findById(patientId).orElse(null);
        String nombre = p == null ? null : p.firstName.split(" ")[0];
        List<Appointment> proximas = p == null ? List.of() : upcoming(p.id);
        Appointment next = proximas.isEmpty() ? null : proximas.get(0);

        List<TextSearch.Hit> hits = TextSearch.search(kb.docs(), text, 5);
        List<String> sources = hits.stream().map(h -> h.doc().title().split(" ·")[0]).toList();
        StringBuilder r = new StringBuilder();
        boolean human = false, pause = false;
        String hola = nombre == null ? "¡Hola! 👋" : "¡Hola, " + nombre + "! 👋";

        switch (intent) {
            case "SALUDO" -> {
                r.append(hola).append(" Soy el asistente virtual de *").append(kb.clinic).append("*. ");
                r.append("Puedo ayudarte con tus citas, horarios, precios, ubicación y pagos.");
                if (next != null) r.append("\n\nTu próxima cita es el *").append(when(next)).append("* con ").append(next.dentist.getFullName()).append(".");
                r.append("\n\n¿En qué te ayudo?");
            }
            case "GRACIAS" -> r.append("¡Con gusto").append(nombre == null ? "" : ", " + nombre).append("! 😊 Si necesitas algo más, escríbenos por aquí.");
            case "DESPEDIDA" -> r.append("¡Hasta pronto").append(nombre == null ? "" : ", " + nombre).append("! Que tengas un excelente día 🦷✨");
            case "MI_CITA" -> {
                if (p == null) {
                    r.append("No encuentro un paciente registrado con este número. Indícanos tu nombre completo y DNI, y recepción te ayudará a revisar tu cita.");
                    human = true;
                } else if (proximas.isEmpty()) {
                    r.append(nombre).append(", no tienes citas pendientes. Puedes reservar desde la app de la clínica o escribir *quiero una cita* y te muestro horarios libres.");
                } else {
                    r.append(proximas.size() == 1 ? "Tu próxima cita:" : "Tus próximas citas:");
                    for (Appointment a : proximas.subList(0, Math.min(3, proximas.size()))) {
                        r.append("\n📅 *").append(when(a)).append("*\n🦷 ").append(a.dentist.getFullName()).append(" · ").append(a.reason)
                                .append(a.status == AppointmentStatus.CONFIRMADA ? " (confirmada ✅)" : "");
                    }
                    r.append("\n📍 ").append(kb.address);
                    if (next.status == AppointmentStatus.PROGRAMADA) r.append("\n\nResponde *1* para confirmar o *2* si necesitas reprogramar.");
                }
            }
            case "CONFIRMAR" -> {
                if (next == null) {
                    r.append(p == null ? "No encuentro una cita con este número. ¿Me indicas tu nombre completo y DNI?" : "No tienes citas pendientes por confirmar.");
                    human = p == null;
                } else if (next.status == AppointmentStatus.CONFIRMADA) {
                    r.append("Tu cita del *").append(when(next)).append("* ya está confirmada ✅. ¡Te esperamos!");
                } else {
                    boolean ok = simulate || confirm(next);
                    r.append(ok ? "¡Listo! Tu cita del *" + when(next) + "* con " + next.dentist.getFullName() + " quedó *confirmada* ✅. ¡Te esperamos!"
                            : "No pude confirmar tu cita automáticamente; recepción la revisará y te escribirá.");
                    human = !ok;
                }
            }
            case "REPROGRAMAR", "CANCELAR" -> {
                boolean cancelar = "CANCELAR".equals(intent);
                if (next == null) {
                    r.append(p == null ? "No encuentro una cita con este número. Indícanos tu nombre completo y DNI para ayudarte."
                            : "No tienes citas pendientes" + (cancelar ? " que cancelar." : " que reprogramar. ¿Quieres reservar una nueva?"));
                    human = p == null;
                } else {
                    if (!simulate) markReschedule(next);
                    r.append(cancelar ? "Entendido. Registré que deseas *cancelar* tu cita del *" : "Entendido. Registré tu pedido para *reprogramar* tu cita del *")
                            .append(when(next)).append("*: recepción te escribirá por aquí para ").append(cancelar ? "confirmar la cancelación." : "acordar el nuevo horario.");
                    if (!cancelar) {
                        List<String> libres = freeSlots(next.dentist, p.id, next.durationMin, next.id, 3);
                        if (!libres.isEmpty()) r.append("\n\nHorarios libres cercanos con ").append(next.dentist.getFullName()).append(":\n• ").append(String.join("\n• ", libres));
                    }
                    r.append("\n\nTambién puedes hacerlo tú mismo desde la app (Mis citas) hasta 12 horas antes.");
                    human = true;
                }
            }
            case "DISPONIBILIDAD" -> {
                r.append("Estos son los próximos horarios libres:");
                int n = 0;
                for (Dentist d : agenda.activeDentists()) {
                    List<String> libres = freeSlots(d, p == null ? null : p.id, 30, null, 2);
                    if (libres.isEmpty()) continue;
                    r.append("\n\n🦷 *").append(d.getFullName()).append("* (").append(d.specialty).append(")\n• ").append(String.join("\n• ", libres));
                    n++;
                }
                if (n == 0) r.setLength(0);
                if (n == 0) r.append("Por ahora no veo horarios libres en los próximos días.");
                r.append("\n\nPuedes reservar al instante desde la app de la clínica, o dime qué horario prefieres y recepción lo agenda por ti.");
                human = true;
            }
            case "PRECIO" -> {
                List<TextSearch.Hit> precios = TextSearch.search(kb.docs().stream().filter(d -> "PRECIO".equals(d.kind())).toList(), text, 3);
                if (precios.isEmpty()) {
                    r.append("El costo depende del tratamiento. La *consulta de evaluación* es el primer paso: ahí el odontólogo te explica el plan y el presupuesto exacto.");
                    kb.docs().stream().filter(d -> "PRECIO".equals(d.kind()) && TextSearch.normalize(d.title()).contains("consulta")).findFirst()
                            .ifPresent(d -> r.append("\n\n").append(d.text()));
                    r.append("\n\n¿Qué tratamiento te interesa? (limpieza, resina, ortodoncia, endodoncia, extracción, blanqueamiento...)");
                } else {
                    r.append("Precios referenciales:");
                    for (TextSearch.Hit h : precios) r.append("\n• ").append(h.doc().text().replace(" Precio referencial; el costo final se confirma en la evaluación.", ""));
                    r.append("\n\nEl costo final se confirma en la evaluación, según cada caso.");
                }
            }
            case "PAGO" -> {
                r.append(doc("pagos"));
                if (p != null) {
                    try {
                        Map<String, Object> b = cash.balance(p.id);
                        BigDecimal saldo = (BigDecimal) b.get("balance");
                        BigDecimal favor = (BigDecimal) b.get("inFavor");
                        if (saldo != null && saldo.signum() > 0) r.append("\n\nTu saldo pendiente es *S/ ").append(saldo.toPlainString()).append("*.");
                        else if (favor != null && favor.signum() > 0) r.append("\n\nTienes *S/ ").append(favor.toPlainString()).append("* a tu favor.");
                        else r.append("\n\nNo tienes saldo pendiente. ✅");
                    } catch (RuntimeException e) {
                        log.debug("saldo no disponible: {}", e.getMessage());
                    }
                }
            }
            case "HORARIO" -> r.append(doc("horario")).append("\n\n📍 ").append(kb.address).append(" · ☎ ").append(kb.phone);
            case "UBICACION" -> r.append("📍 Estamos en *").append(kb.address).append("*.\n☎ Teléfono y WhatsApp: ").append(kb.phone).append("\n\n").append(doc("horario"));
            case "URGENCIA" -> {
                r.append("Lamento mucho lo que te pasa 🙏. Si el dolor es intenso, hay sangrado que no se detiene o hinchazón, *llámanos ahora al ")
                        .append(kb.phone).append("* para atenderte lo antes posible.\n\nYa avisé a recepción para que te contacten.");
                r.append("\n\nSi es de noche o la hinchazón dificulta respirar o tragar, acude a emergencias.");
                human = true;
                pause = true;
            }
            case "HUMANO" -> {
                r.append("¡Claro! Ya avisé a recepción: una persona del equipo te escribirá por aquí en breve. 🙌");
                human = true;
                pause = true;
            }
            default -> {
                TextSearch.Hit best = hits.isEmpty() ? null : hits.get(0);
                if (best != null && best.score() >= 1.6) {
                    r.append(best.doc().text());
                    if (hits.size() > 1 && hits.get(1).score() >= best.score() * 0.85 && !"FAQ".equals(hits.get(1).doc().kind())) {
                        r.append("\n\n").append(hits.get(1).doc().text());
                    }
                } else {
                    r.append("Gracias por tu mensaje").append(nombre == null ? "" : ", " + nombre)
                            .append(". No tengo esa información a la mano, así que ya se la pasé a recepción y te responderán por aquí en breve. 🙌");
                    human = true;
                }
            }
        }
        String base = r.toString().trim();
        String finalText = base;
        String engine = "local";
        if (llm.enabled() && !"GRACIAS".equals(intent) && !"DESPEDIDA".equals(intent)) {
            String redacted = llm.complete(systemPrompt(), List.of(Map.of("role", "user", "content", userPrompt(text, base, hits, p, proximas, history))));
            if (redacted != null && redacted.length() <= 1500) {
                finalText = redacted;
                engine = llm.engineName();
            }
        }
        return new Reply(finalText, intent, human, pause, engine, sources);
    }

    // ------------------------------------------------------------------ prompts del modelo de lenguaje

    private String systemPrompt() {
        return "Eres el asistente virtual de WhatsApp de " + kb.clinic + " (Perú). Respondes a pacientes por WhatsApp.\n"
                + "Reglas:\n"
                + "- Español peruano, cálido y profesional; máximo 6 líneas; formato de WhatsApp (*negrita* con moderación, 1 o 2 emojis).\n"
                + "- Usa SOLO los datos del CONTEXTO y de la RESPUESTA BASE. No inventes precios, horarios, nombres, citas ni disponibilidad.\n"
                + "- La RESPUESTA BASE es correcta: conserva todas sus fechas, horas, montos y acciones (cita confirmada, pedido registrado, aviso a recepción). Puedes mejorar la redacción y responder dudas del mensaje con el CONTEXTO.\n"
                + "- No des diagnósticos, medicamentos ni dosis. Ante dolor fuerte, sangrado o hinchazón indica llamar al " + kb.phone + ".\n"
                + "- Nunca pidas contraseñas, códigos ni datos de tarjetas. No prometas descuentos.\n"
                + "- Si no sabes algo, di que recepción le responderá por este chat.\n"
                + "Devuelve solo el texto del mensaje para el paciente.";
    }

    private String userPrompt(String text, String base, List<TextSearch.Hit> hits, Patient p, List<Appointment> proximas,
                              List<Map<String, Object>> history) {
        StringBuilder u = new StringBuilder("CONTEXTO (base de conocimiento de la clínica):\n");
        Set<String> seen = new HashSet<>();
        for (TextSearch.Hit h : hits) if (seen.add(h.doc().id())) u.append("- ").append(h.doc().title().split(" ·")[0]).append(": ").append(h.doc().text()).append('\n');
        u.append("- Horario general: ").append(doc("horario")).append('\n');
        u.append("- Dirección: ").append(kb.address).append(". Teléfono: ").append(kb.phone).append('\n');
        u.append("\nPACIENTE: ");
        if (p == null) u.append("número no registrado en la clínica.\n");
        else {
            u.append(p.getFullName()).append('\n');
            for (Appointment a : proximas) u.append("- Cita: ").append(when(a)).append(" con ").append(a.dentist.getFullName()).append(" (").append(a.reason).append(", ").append(a.status.name().toLowerCase()).append(")\n");
        }
        if (history != null && !history.isEmpty()) {
            u.append("\nCONVERSACIÓN RECIENTE:\n");
            for (Map<String, Object> m : history) u.append("IN".equals(m.get("direction")) ? "Paciente: " : "Clínica: ").append(String.valueOf(m.get("body")).replace('\n', ' ')).append('\n');
        }
        u.append("\nRESPUESTA BASE:\n").append(base).append("\n\nMENSAJE DEL PACIENTE:\n").append(text);
        return u.toString();
    }

    // ------------------------------------------------------------------ acciones y datos

    private String doc(String id) {
        return kb.docs().stream().filter(d -> d.id().equals(id)).map(TextSearch.Doc::text).findFirst().orElse("");
    }

    private static String when(Appointment a) { return a.startAt.format(CUANDO); }

    List<Appointment> upcoming(Long patientId) {
        LocalDateTime now = Dates.now();
        return appointments.findByPatientIdOrderByStartAtDesc(patientId).stream()
                .filter(a -> (a.status == AppointmentStatus.PROGRAMADA || a.status == AppointmentStatus.CONFIRMADA) && a.startAt.isAfter(now))
                .sorted(Comparator.comparing(a -> a.startAt)).toList();
    }

    private boolean confirm(Appointment a) {
        try {
            agenda.changeStatus(a.id, new AgendaService.StatusRequest(AppointmentStatus.CONFIRMADA, "Confirmada por WhatsApp (asistente)"));
            return true;
        } catch (RuntimeException e) {
            log.info("El asistente no pudo confirmar la cita {}: {}", a.id, e.getMessage());
            return false;
        }
    }

    private void markReschedule(Appointment a) {
        jdbc.update("update appointments set reschedule_requested_at = ? where id = ?", Dates.now(), a.id);
    }

    /** Próximos horarios libres de un odontólogo (respetando que el paciente no tenga otra cita a esa hora). */
    List<String> freeSlots(Dentist d, Long patientId, int duration, Long excludeId, int max) {
        List<String> out = new ArrayList<>();
        LocalDateTime min = Dates.now().plusHours(2);
        int dur = Math.max(15, Math.min(240, duration - duration % 15));
        for (int i = 0; i < 14 && out.size() < max; i++) {
            LocalDate day = Dates.today().plusDays(i);
            try {
                Object list = agenda.slots(d.id, day, dur, excludeId, patientId).get("slots");
                if (!(list instanceof List<?> l)) continue;
                for (Object o : l) {
                    if (o instanceof SlotCalculator.Slot s && s.available() && !s.start().isBefore(min)) {
                        out.add(s.start().format(CORTO));
                        if (out.size() >= max) break;
                    }
                }
            } catch (RuntimeException e) {
                break;
            }
        }
        return out;
    }
}
