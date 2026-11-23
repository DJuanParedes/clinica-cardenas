package pe.clinicacardenas.files;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Generador mínimo de PDF (A4, Helvetica, imagen JPEG) para consentimientos, recetas y presupuestos.
 * Sin librerías externas: escribe los objetos PDF a mano. Admite tildes y ñ (codificación WinAnsi).
 */
public final class SimplePdf {

    private static final Charset WIN = Charset.forName("windows-1252");
    private static final float W = 595;
    private static final float H = 842;
    private static final float MARGIN = 56;

    private final List<StringBuilder> pages = new ArrayList<>();
    private final List<byte[]> images = new ArrayList<>();
    private final List<int[]> imageSizes = new ArrayList<>();
    private StringBuilder page;
    private float y;

    public SimplePdf() {
        newPage();
    }

    private void newPage() {
        page = new StringBuilder();
        pages.add(page);
        y = H - MARGIN;
    }

    private void ensure(float needed) {
        if (y - needed < MARGIN) newPage();
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '(' || c == ')' || c == '\\') b.append('\\');
            if (c == '\t') c = ' ';
            b.append(c);
        }
        return b.toString();
    }

    private void textAt(String text, float x, float yy, float size, boolean bold, String rgb) {
        page.append("BT ").append(rgb).append(" rg /").append(bold ? "F2" : "F1").append(' ').append(size).append(" Tf ")
                .append(x).append(' ').append(yy).append(" Td (").append(esc(text)).append(") Tj ET\n");
    }

    /** Párrafo con salto de línea automático. */
    public SimplePdf paragraph(String text, float size, boolean bold) {
        int maxChars = (int) ((W - 2 * MARGIN) / (size * (bold ? 0.56f : 0.52f)));
        for (String raw : text.split("\n", -1)) {
            List<String> lines = wrap(raw, maxChars);
            if (lines.isEmpty()) lines.add("");
            for (String line : lines) {
                ensure(size * 1.45f);
                y -= size * 1.45f;
                textAt(line, MARGIN, y, size, bold, "0.06 0.16 0.23");
            }
        }
        return this;
    }

    private static List<String> wrap(String s, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            if (cur.length() + word.length() + 1 > max && cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(word);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    public SimplePdf space(float pts) {
        y -= pts;
        return this;
    }

    /** Encabezado con el nombre de la clínica y una línea de color. */
    public SimplePdf header(String clinic, String subtitle) {
        textAt(clinic, MARGIN, y - 18, 16, true, "0.02 0.37 0.53");
        textAt(subtitle, MARGIN, y - 34, 9, false, "0.41 0.52 0.58");
        page.append("0 0.69 0.88 RG 2 w ").append(MARGIN).append(' ').append(y - 44).append(" m ")
                .append(W - MARGIN).append(' ').append(y - 44).append(" l S\n");
        y -= 60;
        return this;
    }

    public SimplePdf title(String t) {
        space(6);
        return paragraph(t, 14, true).space(6);
    }

    /** Imagen JPEG (por ejemplo, la firma) con el ancho indicado. */
    public SimplePdf jpeg(byte[] jpeg, float width) {
        int[] wh = jpegSize(jpeg);
        if (wh == null) return this;
        float h = width * wh[1] / wh[0];
        ensure(h + 10);
        y -= h;
        images.add(jpeg);
        imageSizes.add(wh);
        int idx = images.size();
        page.append("q ").append(width).append(" 0 0 ").append(h).append(' ').append(MARGIN).append(' ').append(y)
                .append(" cm /Im").append(idx).append(" Do Q\n");
        return this;
    }

    // ------------------------------------------------------------------ dibujo libre (coordenadas desde abajo-izquierda)

    /** v2.8: tabla con encabezado sombreado; align por columna: L o R. Salta de página si no entra. */
    public SimplePdf table(String[] headers, float[] widths, List<String[]> rows, char[] align, float size) {
        float rowH = size * 1.9f;
        float total = 0;
        for (float w : widths) total += w;
        float scale = (W - 2 * MARGIN) / total;
        Runnable head = () -> {
            ensure(rowH * 2);
            y -= rowH;
            rect(MARGIN, y - size * 0.55f, W - 2 * MARGIN, rowH, "0.90 0.96 0.97", null, 0);
            float x = MARGIN;
            for (int i = 0; i < headers.length; i++) {
                float w = widths[i] * scale;
                text(headers[i], align[i] == 'R' ? x + w - 4 : x + 4, y, size, true, "0.05 0.30 0.35", align[i] == 'R' ? 'R' : 'L');
                x += w;
            }
        };
        head.run();
        for (String[] r : rows) {
            if (y - rowH < MARGIN) { newPage(); head.run(); }
            y -= rowH;
            float x = MARGIN;
            for (int i = 0; i < r.length && i < widths.length; i++) {
                float w = widths[i] * scale;
                int max = (int) (w / (size * 0.5f)) - 1;
                String t = r[i] == null ? "" : r[i].length() > max ? r[i].substring(0, Math.max(1, max - 1)) + "…" : r[i];
                text(t, align[i] == 'R' ? x + w - 4 : x + 4, y, size, false, "0.1 0.1 0.1", align[i] == 'R' ? 'R' : 'L');
                x += w;
            }
            line(MARGIN, y - size * 0.6f, W - MARGIN, y - size * 0.6f, "0.88 0.90 0.92", 0.5f);
        }
        y -= size;
        return this;
    }

    /** v2.8: gráfico de barras verticales simple (una serie). */
    public SimplePdf bars(String[] labels, double[] values, String rgb, String unitPrefix) {
        float h = 120;
        float top;
        ensure(h + 40);
        top = y - 10;
        double max = 0;
        for (double v : values) max = Math.max(max, v);
        if (max <= 0) max = 1;
        float slot = (W - 2 * MARGIN) / labels.length;
        float base = top - h;
        line(MARGIN, base, W - MARGIN, base, "0.75 0.78 0.80", 0.6f);
        for (int i = 0; i < labels.length; i++) {
            float bh = (float) (values[i] / max * (h - 18));
            float x = MARGIN + i * slot + slot * 0.18f;
            if (bh > 0) rect(x, base, slot * 0.64f, bh, rgb, null, 0);
            text(labels[i], x + slot * 0.32f, base - 11, 7.5f, false, "0.35 0.38 0.40", 'C');
            if (values[i] > 0) text(compact(values[i], unitPrefix), x + slot * 0.32f, base + bh + 3, 6.5f, false, "0.25 0.28 0.30", 'C');
        }
        y = base - 24;
        return this;
    }

    private static String compact(double v, String prefix) {
        String p = prefix == null ? "" : prefix;
        if (v >= 1_000_000) return p + String.format(java.util.Locale.US, "%.1fM", v / 1_000_000);
        if (v >= 10_000) return p + String.format(java.util.Locale.US, "%.0fk", v / 1000);
        if (v >= 1000) return p + String.format(java.util.Locale.US, "%.1fk", v / 1000);
        return p + String.format(java.util.Locale.US, "%.0f", v);
    }

    public static float pageWidth() { return W; }
    public static float pageHeight() { return H; }

    /** Texto en una posición; align: L (izquierda), C (centro) o R (derecha, x = borde derecho). */
    public SimplePdf text(String t, float x, float yy, float size, boolean bold, String rgb, char align) {
        String s = t == null ? "" : t;
        float w = s.length() * size * (bold ? 0.55f : 0.5f);
        float xx = align == 'C' ? x - w / 2 : align == 'R' ? x - w : x;
        textAt(s, xx, yy, size, bold, rgb);
        return this;
    }

    public SimplePdf rect(float x, float yy, float w, float h, String fillRgb, String strokeRgb, float lineWidth) {
        page.append("q ");
        if (fillRgb != null) page.append(fillRgb).append(" rg ");
        if (strokeRgb != null) page.append(strokeRgb).append(" RG ").append(lineWidth).append(" w ");
        page.append(x).append(' ').append(yy).append(' ').append(w).append(' ').append(h).append(" re ")
                .append(fillRgb != null && strokeRgb != null ? "B" : fillRgb != null ? "f" : "S").append(" Q\n");
        return this;
    }

    public SimplePdf line(float x1, float y1, float x2, float y2, String rgb, float lineWidth) {
        page.append("q ").append(rgb).append(" RG ").append(lineWidth).append(" w ").append(x1).append(' ').append(y1)
                .append(" m ").append(x2).append(' ').append(y2).append(" l S Q\n");
        return this;
    }

    /** Matriz de módulos (por ejemplo, un código QR) dibujada con cuadrados negros. */
    public SimplePdf modules(boolean[][] m, float x, float yy, float size) {
        int n = m.length;
        float cell = size / n;
        page.append("q 0 0 0 rg ");
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) if (m[r][c]) {
            page.append(x + c * cell).append(' ').append(yy + size - (r + 1) * cell).append(' ').append(cell).append(' ').append(cell).append(" re ");
        }
        page.append("f Q\n");
        return this;
    }

    /** Línea para firma con el texto debajo. */
    public SimplePdf signatureLine(String label) {
        ensure(30);
        y -= 4;
        page.append("0.41 0.52 0.58 RG 0.7 w ").append(MARGIN).append(' ').append(y).append(" m ")
                .append(MARGIN + 220).append(' ').append(y).append(" l S\n");
        y -= 12;
        textAt(label, MARGIN, y, 9, false, "0.41 0.52 0.58");
        return this;
    }

    public static int[] jpegSize(byte[] b) {
        int i = 2;
        while (i + 9 < b.length) {
            if ((b[i] & 0xFF) != 0xFF) { i++; continue; }
            int marker = b[i + 1] & 0xFF;
            int len = ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
            if (marker >= 0xC0 && marker <= 0xC3) {
                int h = ((b[i + 5] & 0xFF) << 8) | (b[i + 6] & 0xFF);
                int w = ((b[i + 7] & 0xFF) << 8) | (b[i + 8] & 0xFF);
                return new int[]{w, h};
            }
            i += 2 + len;
        }
        return null;
    }

    public byte[] build() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        // 1 catálogo, 2 páginas, 3 fuente normal, 4 fuente negrita, luego imágenes, luego (página, contenido) x n
        int firstImg = 5;
        int firstPage = firstImg + images.size();
        int total = firstPage + pages.size() * 2 - 1;
        write(out, "%PDF-1.4\n%âãÏÓ\n");
        StringBuilder kids = new StringBuilder();
        for (int p = 0; p < pages.size(); p++) kids.append(firstPage + p * 2).append(" 0 R ");
        obj(out, offsets, 1, "<< /Type /Catalog /Pages 2 0 R >>");
        obj(out, offsets, 2, "<< /Type /Pages /Kids [" + kids + "] /Count " + pages.size() + " >>");
        obj(out, offsets, 3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        obj(out, offsets, 4, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
        StringBuilder xobjects = new StringBuilder();
        for (int i = 0; i < images.size(); i++) {
            int n = firstImg + i;
            xobjects.append("/Im").append(i + 1).append(' ').append(n).append(" 0 R ");
            offsets.add(out.size());
            byte[] img = images.get(i);
            write(out, n + " 0 obj\n<< /Type /XObject /Subtype /Image /Width " + imageSizes.get(i)[0] + " /Height "
                    + imageSizes.get(i)[1] + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length "
                    + img.length + " >>\nstream\n");
            out.writeBytes(img);
            write(out, "\nendstream\nendobj\n");
        }
        for (int p = 0; p < pages.size(); p++) {
            int pageObj = firstPage + p * 2;
            int contentObj = pageObj + 1;
            obj(out, offsets, pageObj, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + (int) W + " " + (int) H + "] "
                    + "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> /XObject << " + xobjects + ">> >> /Contents "
                    + contentObj + " 0 R >>");
            byte[] content = pages.get(p).toString().getBytes(WIN);
            offsets.add(out.size());
            write(out, contentObj + " 0 obj\n<< /Length " + content.length + " >>\nstream\n");
            out.writeBytes(content);
            write(out, "\nendstream\nendobj\n");
        }
        int xref = out.size();
        StringBuilder x = new StringBuilder("xref\n0 " + (total + 1) + "\n0000000000 65535 f \n");
        for (int off : offsets) x.append(String.format("%010d 00000 n \n", off));
        x.append("trailer\n<< /Size ").append(total + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        write(out, x.toString());
        return out.toByteArray();
    }

    private static void obj(ByteArrayOutputStream out, List<Integer> offsets, int n, String body) {
        offsets.add(out.size());
        write(out, n + " 0 obj\n" + body + "\nendobj\n");
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(WIN));
    }
}
