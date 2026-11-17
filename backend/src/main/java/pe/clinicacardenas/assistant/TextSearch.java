package pe.clinicacardenas.assistant;

import java.text.Normalizer;
import java.util.*;

/**
 * Búsqueda de texto en español para la base de conocimiento del asistente (la "R" de RAG).
 * Normaliza (minúsculas, sin tildes), quita palabras vacías, reduce cada palabra a su raíz aproximada y ordena los
 * documentos con BM25. Lógica pura, sin base de datos, para poder probarla.
 */
public final class TextSearch {

    private TextSearch() {}

    public record Doc(String id, String kind, String title, String text) {}
    public record Hit(Doc doc, double score) {}

    private static final Set<String> STOP = Set.of(
            "a", "al", "algo", "ante", "con", "como", "cual", "cuales", "de", "del", "desde", "donde", "el", "ella", "en",
            "entre", "es", "esa", "ese", "eso", "esta", "este", "esto", "estoy", "fue", "ha", "hay", "la", "las", "le", "les",
            "lo", "los", "me", "mi", "mis", "muy", "ni", "no", "nos", "o", "para", "pero", "por", "que", "quiero", "se", "si",
            "sin", "sobre", "su", "sus", "te", "tengo", "ti", "tu", "tus", "un", "una", "uno", "unos", "unas", "y", "ya", "yo",
            "puedo", "puede", "pueden", "seria", "hola", "buenas", "buenos", "dias", "tardes", "noches", "favor", "gracias",
            "usted", "ustedes", "hacen", "tienen", "tiene", "saber", "quisiera", "pregunta");

    public static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.replaceAll("[^a-z0-9ñ ]+", " ").replaceAll("\\s+", " ").trim();
    }

    /** Raíz aproximada: quita plurales y terminaciones frecuentes ("limpiezas" → "limpiez", "ortodoncia" → "ortodonc"). */
    static String stem(String w) {
        if (w.length() <= 4) return w;
        if (w.endsWith("s") && w.length() > 4) w = w.substring(0, w.length() - 1);   // plural
        if (w.endsWith("e") && w.length() > 5 && (w.endsWith("ne") || w.endsWith("le") || w.endsWith("re"))) w = w.substring(0, w.length() - 1);
        for (String suf : List.of("amiento", "imiento", "mente", "acion", "icion", "cion", "ancia", "encia", "able", "ible",
                "ista", "oso", "osa", "al", "ar", "er", "ir", "a", "o", "e")) {
            if (w.endsWith(suf) && w.length() - suf.length() >= 4) return w.substring(0, w.length() - suf.length());
        }
        return w;
    }

    public static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        for (String w : normalize(s).split(" ")) {
            if (w.isBlank() || STOP.contains(w)) continue;
            out.add(stem(w));
        }
        return out;
    }

    /** BM25 (k1 = 1.4, b = 0.75). El título pesa el doble. Devuelve los mejores con puntaje mayor que cero. */
    public static List<Hit> search(List<Doc> docs, String query, int limit) {
        List<String> q = tokens(query);
        if (q.isEmpty() || docs.isEmpty()) return List.of();
        List<List<String>> toks = new ArrayList<>();
        Map<String, Integer> df = new HashMap<>();
        double total = 0;
        for (Doc d : docs) {
            List<String> t = new ArrayList<>(tokens(d.title()));
            t.addAll(tokens(d.title()));
            t.addAll(tokens(d.text()));
            toks.add(t);
            total += t.size();
            for (String w : new HashSet<>(t)) df.merge(w, 1, Integer::sum);
        }
        double avg = total / docs.size();
        int n = docs.size();
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            List<String> t = toks.get(i);
            Map<String, Integer> tf = new HashMap<>();
            for (String w : t) tf.merge(w, 1, Integer::sum);
            double score = 0;
            for (String w : new LinkedHashSet<>(q)) {
                Integer f = tf.get(w);
                if (f == null) continue;
                double idf = Math.log(1 + (n - df.get(w) + 0.5) / (df.get(w) + 0.5));
                score += idf * (f * 2.4) / (f + 1.4 * (1 - 0.75 + 0.75 * t.size() / avg));
            }
            if (score > 0) hits.add(new Hit(docs.get(i), score));
        }
        hits.sort((a, b) -> Double.compare(b.score(), a.score()));
        return hits.size() > limit ? hits.subList(0, limit) : hits;
    }

    /** ¿El texto contiene alguna de estas frases? (comparación normalizada, por palabras completas). */
    public static boolean has(String normalized, String... phrases) {
        String padded = " " + normalized + " ";
        for (String p : phrases) if (padded.contains(" " + p + " ")) return true;
        return false;
    }
}
