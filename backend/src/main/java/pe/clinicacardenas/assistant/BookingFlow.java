package pe.clinicacardenas.assistant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pe.clinicacardenas.agenda.*;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.Dates;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reserva de citas por WhatsApp en tres pasos, como lo haría una recepcionista:
 *   1. ¿Qué día? (se ofrecen los próximos días con cupo, o el paciente escribe "mañana", "lunes", "25/11"…)
 *   2. Odontólogos que atienden ese día con sus horas libres, numeradas.
 *   3. Resumen y confirmación: recién al responder SÍ se guarda la cita, volviendo a validar que el horario
 *      siga libre (otra persona pudo tomarlo mientras tanto; la agenda bloquea al odontólogo y revisa cruces).
 * Reglas: con al menos 2 h de anticipación y hasta 60 días; "salir" abandona; la sesión vence en 30 minutos.
 */
@Service
public class BookingFlow {

    private static final Locale ES = new Locale("es", "PE");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("h:mm a", ES);
    private static final int DURATION = 30;
    private static final int MAX_DAYS = 60;

    private final JdbcTemplate jdbc;
    private final AgendaService agenda;

    public BookingFlow(JdbcTemplate jdbc, AgendaService agenda) {
        this.jdbc = jdbc;
        this.agenda = agenda;
    }

    private record Option(long dentistId, String dentist, LocalDateTime start) {}

    public boolean active(Long patientId) {
        if (patientId == null) return false;
        Integer n = jdbc.queryForObject("select count(*) from assistant_bookings where patient_id = ? and expires_at > ?", Integer.class, patientId, Dates.now());
        return n != null && n > 0;
    }

    /** Paso 1. Con simulate no se guarda nada (probador del asistente en la web). */
    public String start(Long patientId, boolean simulate) {
        List<LocalDate> days = openDays(patientId, 6);
        if (days.isEmpty()) return "Por ahora no veo cupos en los próximos días. Recepción te escribirá para buscarte un horario.";
        StringBuilder sb = new StringBuilder("¡Claro! Te ayudo a reservar 🗓️\n*¿Para qué día quieres tu cita?* Estos días tienen horarios libres:\n");
        for (int i = 0; i < days.size(); i++) sb.append("\n").append(i + 1).append(". ").append(cap(days.get(i).format(DIA)));
        sb.append("\n\nResponde con el número, o escribe otra fecha (por ejemplo *mañana*, *lunes* o *28/11*). Escribe *salir* para cancelar.");
        if (!simulate) save(patientId, "FECHA", null, days.stream().map(LocalDate::toString).toList(), null, null);
        return sb.toString();
    }

    /** Siguiente paso de una reserva en curso. */
    public String handle(Long patientId, String text) {
        Map<String, Object> s = jdbc.queryForMap("select * from assistant_bookings where patient_id = ?", patientId);
        String n = TextSearch.normalize(text);
        if (n.matches("^(salir|cancelar|no quiero|ya no|olvidalo|dejalo|chau)\\b.*")) {
            clear(patientId);
            return "Listo, dejé la reserva sin guardar. Si quieres intentarlo de nuevo escribe *quiero una cita*. 🙂";
        }
        String step = (String) s.get("step");
        List<String> opts = s.get("options") == null ? List.of() : Arrays.asList(((String) s.get("options")).split("\\|"));
        return switch (step) {
            case "FECHA" -> pickDay(patientId, n, opts);
            case "HORA" -> pickSlot(patientId, n, opts, ((java.sql.Date) s.get("day")).toLocalDate());
            default -> confirm(patientId, n, s);
        };
    }

    private String pickDay(Long pid, String n, List<String> opts) {
        LocalDate day = null;
        if (n.matches("^\\d$") && Integer.parseInt(n) >= 1 && Integer.parseInt(n) <= opts.size()) day = LocalDate.parse(opts.get(Integer.parseInt(n) - 1));
        if (day == null) day = parseDate(n);
        if (day == null) return "No entendí la fecha 🤔. Responde con el número de la lista o escribe por ejemplo *mañana*, *viernes* o *28/11*.";
        if (day.isBefore(Dates.today())) return "Esa fecha ya pasó. ¿Qué otro día te acomoda?";
        if (day.isAfter(Dates.today().plusDays(MAX_DAYS))) return "Solo reservamos hasta " + MAX_DAYS + " días adelante. Elige una fecha más cercana.";
        List<Option> options = options(pid, day, 9);
        if (options.isEmpty()) {
            List<LocalDate> alt = openDays(pid, 3);
            save(pid, "FECHA", null, alt.stream().map(LocalDate::toString).toList(), null, null);
            StringBuilder sb = new StringBuilder("El *" + day.format(DIA) + "* no hay horarios libres (o la clínica no atiende). Días con cupo:\n");
            for (int i = 0; i < alt.size(); i++) sb.append("\n").append(i + 1).append(". ").append(cap(alt.get(i).format(DIA)));
            return sb.append("\n\nResponde con el número u otra fecha.").toString();
        }
        save(pid, "HORA", day, options.stream().map(o -> o.dentistId() + ";" + o.start()).toList(), null, null);
        return listSlots(day, options, "");
    }

    private String listSlots(LocalDate day, List<Option> options, String prefix) {
        StringBuilder sb = new StringBuilder(prefix + "Para el *" + day.format(DIA) + "* tenemos:\n");
        String last = null;
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            if (!o.dentist().equals(last)) { sb.append("\n🦷 *").append(o.dentist()).append("*"); last = o.dentist(); }
            sb.append("\n   ").append(i + 1).append(". ").append(o.start().format(HORA));
        }
        return sb.append("\n\nResponde con el *número* del horario. Escribe *otro día* para cambiar la fecha o *salir* para cancelar.").toString();
    }

    private String pickSlot(Long pid, String n, List<String> opts, LocalDate day) {
        if (n.matches(".*\\b(otro dia|otra fecha|cambiar (el )?dia|otro)\\b.*")) return start(pid, false);
        if (!n.matches("^\\d{1,2}$")) return "Responde solo con el *número* del horario (por ejemplo *2*), *otro día* o *salir*.";
        int k = Integer.parseInt(n);
        if (k < 1 || k > opts.size()) return "Ese número no está en la lista. Elige entre 1 y " + opts.size() + ".";
        String[] p = opts.get(k - 1).split(";");
        long dentistId = Long.parseLong(p[0]);
        LocalDateTime start = LocalDateTime.parse(p[1]);
        String dentist = agenda.activeDentists().stream().filter(d -> d.id == dentistId).map(Dentist::getFullName).findFirst().orElse("el odontólogo");
        save(pid, "CONFIRMAR", day, opts, dentistId, start);
        return "Revisa tu reserva:\n📅 *" + cap(start.format(DIA)) + "* a las *" + start.format(HORA) + "*\n🦷 " + dentist
                + "\n⏱ Consulta de " + DURATION + " minutos\n\n¿La confirmo? Responde *SÍ* para reservar o *NO* para elegir otro horario.";
    }

    private String confirm(Long pid, String n, Map<String, Object> s) {
        LocalDate day = ((java.sql.Date) s.get("day")).toLocalDate();
        if (n.matches("^(no|nop|otro|otra|cambiar)\\b.*")) {
            List<Option> options = options(pid, day, 9);
            if (options.isEmpty()) return start(pid, false);
            save(pid, "HORA", day, options.stream().map(o -> o.dentistId() + ";" + o.start()).toList(), null, null);
            return listSlots(day, options, "Sin problema. ");
        }
        if (!n.matches("^(si|sí|ok|okey|confirmo|confirmar|dale|de acuerdo|claro|correcto|reservar?)\\b.*")) {
            return "Responde *SÍ* para reservar o *NO* para elegir otro horario.";
        }
        long dentistId = ((Number) s.get("dentist_id")).longValue();
        LocalDateTime start = pe.clinicacardenas.common.Dates.toLocalDateTime(s.get("start_at"));
        try {
            // Revalida todo al guardar: horario de atención, cruces del odontólogo y del paciente, anticipación.
            Appointment a = agenda.create(new AgendaService.AppointmentRequest(pid, dentistId, start, DURATION,
                    "Consulta (reservada por WhatsApp)", "Reservada por el paciente con el asistente de WhatsApp", null));
            clear(pid);
            return "¡Listo! ✅ Tu cita quedó *reservada* para el *" + a.startAt.format(DIA) + "* a las *" + a.startAt.format(HORA) + "* con "
                    + a.dentist.getFullName() + ".\nTe enviaremos un recordatorio un día antes. Llega 10 minutos antes con tu DNI.";
        } catch (ApiException e) {
            List<Option> options = options(pid, day, 9);
            if (options.isEmpty()) {
                clear(pid);
                return "Lo siento, ese horario acaba de ocuparse y ya no quedan cupos ese día 😕. Escribe *quiero una cita* para elegir otro día.";
            }
            save(pid, "HORA", day, options.stream().map(o -> o.dentistId() + ";" + o.start()).toList(), null, null);
            return listSlots(day, options, "Lo siento, ese horario acaba de ocuparse (" + e.getMessage() + ").\n");
        }
    }

    // ------------------------------------------------------------------ apoyo

    private List<Option> options(Long pid, LocalDate day, int max) {
        List<Option> out = new ArrayList<>();
        LocalDateTime min = Dates.now().plusHours(2);
        List<Dentist> ds = agenda.activeDentists();
        int perDentist = Math.max(2, max / Math.max(1, ds.size()));
        for (Dentist d : ds) {
            int c = 0;
            Object list = agenda.slots(d.id, day, DURATION, null, pid).get("slots");
            if (!(list instanceof List<?> l)) continue;
            for (Object o : l) {
                if (o instanceof SlotCalculator.Slot sl && sl.available() && !sl.start().isBefore(min)) {
                    // Horas "redondas" (cada 30 min) para que la lista sea corta y fácil de leer
                    if (sl.start().getMinute() % 30 != 0) continue;
                    out.add(new Option(d.id, d.getFullName(), sl.start()));
                    if (++c >= perDentist || out.size() >= max) break;
                }
            }
            if (out.size() >= max) break;
        }
        return out;
    }

    private List<LocalDate> openDays(Long pid, int max) {
        List<LocalDate> days = new ArrayList<>();
        for (int i = 0; i <= 21 && days.size() < max; i++) {
            LocalDate d = Dates.today().plusDays(i);
            if (!options(pid, d, 1).isEmpty()) days.add(d);
        }
        return days;
    }

    private static final Map<String, DayOfWeek> DOW = Map.of("lunes", DayOfWeek.MONDAY, "martes", DayOfWeek.TUESDAY, "miercoles", DayOfWeek.WEDNESDAY,
            "jueves", DayOfWeek.THURSDAY, "viernes", DayOfWeek.FRIDAY, "sabado", DayOfWeek.SATURDAY, "domingo", DayOfWeek.SUNDAY);
    private static final List<String> MESES = List.of("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre");
    // El texto llega normalizado (sin "/" ni "-"): "28/11" se ve como "28 11"
    private static final Pattern DMY = Pattern.compile("\\b(\\d{1,2})[/ .-](\\d{1,2})(?:[/ .-](\\d{2,4}))?\\b");
    private static final Pattern D_DE_M = Pattern.compile("\\b(\\d{1,2}) de (" + String.join("|", MESES) + "|setiembre)\\b");

    /** "hoy", "mañana", "pasado mañana", "lunes", "28/11", "28/11/2026", "28 de noviembre". */
    static LocalDate parseDate(String n) {
        return parseDate(n, Dates.today());
    }

    static LocalDate parseDate(String text, LocalDate today) {
        // "en la mañana" es la hora del día, no "mañana" (el día siguiente)
        String n = text.replaceAll("\\b(en|por|de|a) la (manana|tarde|noche)\\b", " ").trim();
        if (n.contains("pasado manana")) return today.plusDays(2);
        if (n.matches(".*\\bmanana\\b.*")) return today.plusDays(1);
        if (n.matches(".*\\bhoy\\b.*")) return today;
        Matcher m = DMY.matcher(n);
        if (m.find()) {
            int d = Integer.parseInt(m.group(1)), mo = Integer.parseInt(m.group(2));
            int y = m.group(3) == null ? today.getYear() : Integer.parseInt(m.group(3)) % 100 + 2000;
            try {
                LocalDate r = LocalDate.of(y, mo, d);
                return m.group(3) == null && r.isBefore(today) ? r.plusYears(1) : r;
            } catch (Exception e) { return null; }
        }
        m = D_DE_M.matcher(n);
        if (m.find()) {
            int mo = m.group(2).equals("setiembre") ? 9 : MESES.indexOf(m.group(2)) + 1;
            try {
                LocalDate r = LocalDate.of(today.getYear(), mo, Integer.parseInt(m.group(1)));
                return r.isBefore(today) ? r.plusYears(1) : r;
            } catch (Exception e) { return null; }
        }
        for (Map.Entry<String, DayOfWeek> e : DOW.entrySet()) {
            if (n.matches(".*\\b" + e.getKey() + "\\b.*")) {
                LocalDate r = today.plusDays(1);
                while (r.getDayOfWeek() != e.getValue()) r = r.plusDays(1);
                return r;
            }
        }
        return null;
    }

    private void save(Long pid, String step, LocalDate day, List<String> options, Long dentistId, LocalDateTime start) {
        jdbc.update("""
                insert into assistant_bookings (patient_id, step, day, options, dentist_id, start_at, expires_at) values (?, ?, ?, ?, ?, ?, ?)
                on duplicate key update step = values(step), day = values(day), options = values(options), dentist_id = values(dentist_id),
                       start_at = values(start_at), expires_at = values(expires_at)""",
                pid, step, day, String.join("|", options), dentistId, start, Dates.now().plusMinutes(30));
    }

    private void clear(Long pid) {
        jdbc.update("delete from assistant_bookings where patient_id = ?", pid);
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
