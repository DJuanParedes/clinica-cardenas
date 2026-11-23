package pe.clinicacardenas.billing;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import pe.clinicacardenas.files.SimplePdf;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Representación impresa de la boleta o factura electrónica en A4, con lo que exige SUNAT
 * (RS 097-2012/SUNAT y modificatorias): datos del emisor y recuadro con RUC, tipo y número; datos del adquiriente;
 * detalle con cantidad, unidad, descripción, valor unitario e importe; operación gravada, IGV y total; importe en
 * letras; código QR con RUC|tipo|serie|número|IGV|total|fecha|tipo doc.|n.° doc.|hash; y la leyenda de consulta.
 */
public final class InvoicePdf {
    private static final String V_1_1_1 = "1 1 1";


    private static final String INK = "0.11 0.17 0.2";
    private static final String MUTED = "0.42 0.48 0.52";
    private static final String BRAND = "0.02 0.37 0.53";
    private static final String LINE = "0.75 0.8 0.83";
    private static final String SOFT = "0.93 0.96 0.97";

    private InvoicePdf() {}

    private static String money(BigDecimal v) {
        return v == null ? "0.00" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String docName(String code) {
        return switch (code == null ? "" : code) {
            case "6" -> "RUC";
            case "1" -> "DNI";
            case "4" -> "C.E.";
            case "7" -> "PASAPORTE";
            default -> "DOC.";
        };
    }

    /** Texto que lleva el QR según SUNAT. */
    static String qrText(Invoice i, UblBuilder.Company c) {
        return String.join("|", c.ruc(), i.docType, i.series, String.valueOf(i.number), money(i.igvAmount), money(i.totalAmount),
                i.issueDate.toString(), i.customerDocType == null ? "" : i.customerDocType, i.customerDocNumber == null ? "" : i.customerDocNumber,
                i.hash == null ? "" : i.hash) + "|";
    }

    static boolean[][] qr(String text) {
        try {
            ByteMatrix m = Encoder.encode(text, ErrorCorrectionLevel.Q).getMatrix();
            boolean[][] out = new boolean[m.getHeight()][m.getWidth()];
            for (int r = 0; r < m.getHeight(); r++) for (int c = 0; c < m.getWidth(); c++) out[r][c] = m.get(c, r) == 1;
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] render(Invoice i, List<InvoiceItem> items, UblBuilder.Company c, String words, String phone) {
        SimplePdf pdf = new SimplePdf();
        float pageW = SimplePdf.pageWidth();
        float pageH = SimplePdf.pageHeight();
        float left = 40;
        float right = pageW - 40;
        boolean factura = "01".equals(i.docType);

        // Emisor
        float y = pageH - 58;
        pdf.text(c.tradeName() == null ? c.businessName() : c.tradeName(), left, y, 17, true, BRAND, 'L');
        pdf.text(c.businessName(), left, y - 17, 9.5f, true, INK, 'L');
        pdf.text(c.address(), left, y - 30, 9, false, MUTED, 'L');
        pdf.text("Teléfono / WhatsApp: " + phone, left, y - 42, 9, false, MUTED, 'L');

        // Recuadro del comprobante
        float bx = right - 200;
        float bw = 200;
        float bh = 74;
        float by = pageH - 104;
        pdf.rect(bx, by, bw, bh, null, BRAND, 1.4f);
        pdf.text("R.U.C. N.° " + c.ruc(), bx + bw / 2, by + bh - 20, 11, true, INK, 'C');
        pdf.rect(bx, by + 24, bw, 22, BRAND, null, 0);
        pdf.text(factura ? "FACTURA ELECTRÓNICA" : "BOLETA DE VENTA ELECTRÓNICA", bx + bw / 2, by + 31, 9.5f, true, V_1_1_1, 'C');
        pdf.text(i.series + " - " + String.format("%08d", i.number), bx + bw / 2, by + 8, 12, true, INK, 'C');

        // Adquiriente
        float cy = pageH - 200;
        pdf.rect(left, cy, right - left, 74, SOFT, LINE, 0.6f);
        pdf.text(factura ? "Razón social:" : "Señor(es):", left + 10, cy + 56, 9, true, INK, 'L');
        pdf.text(i.customerName, left + 90, cy + 56, 9, false, INK, 'L');
        pdf.text(docName(i.customerDocType) + ":", left + 10, cy + 41, 9, true, INK, 'L');
        pdf.text(i.customerDocNumber, left + 90, cy + 41, 9, false, INK, 'L');
        pdf.text("Dirección:", left + 10, cy + 26, 9, true, INK, 'L');
        pdf.text(i.customerAddress == null || i.customerAddress.isBlank() ? "-" : i.customerAddress, left + 90, cy + 26, 9, false, INK, 'L');
        pdf.text("Forma de pago:", left + 10, cy + 11, 9, true, INK, 'L');
        pdf.text("Contado", left + 90, cy + 11, 9, false, INK, 'L');
        float rx = left + 330;
        pdf.text("Fecha de emisión:", rx, cy + 56, 9, true, INK, 'L');
        pdf.text(i.issueDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), rx + 95, cy + 56, 9, false, INK, 'L');
        pdf.text("Hora:", rx, cy + 41, 9, true, INK, 'L');
        pdf.text(i.issueTime == null ? "-" : i.issueTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")), rx + 95, cy + 41, 9, false, INK, 'L');
        pdf.text("Moneda:", rx, cy + 26, 9, true, INK, 'L');
        pdf.text("SOLES (PEN)", rx + 95, cy + 26, 9, false, INK, 'L');

        // Detalle
        float ty = cy - 30;
        float cQty = left + 8;
        float cUnit = left + 52;
        float cDesc = left + 110;
        float cPu = right - 92;
        float cTot = right - 8;
        pdf.rect(left, ty, right - left, 20, BRAND, null, 0);
        pdf.text("CANT.", cQty, ty + 6.5f, 8.5f, true, V_1_1_1, 'L');
        pdf.text("UNIDAD", cUnit, ty + 6.5f, 8.5f, true, V_1_1_1, 'L');
        pdf.text("DESCRIPCIÓN", cDesc, ty + 6.5f, 8.5f, true, V_1_1_1, 'L');
        pdf.text("P. UNIT.", cPu, ty + 6.5f, 8.5f, true, V_1_1_1, 'R');
        pdf.text("IMPORTE", cTot, ty + 6.5f, 8.5f, true, V_1_1_1, 'R');
        float row = ty;
        for (InvoiceItem it : items) {
            String desc = it.description == null ? "" : it.description;
            List<String> lines = wrap(desc, 58);
            float h = 8f + 12f * lines.size();
            row -= h;
            pdf.text(it.quantity.stripTrailingZeros().toPlainString(), cQty, row + h - 14, 9, false, INK, 'L');
            pdf.text("SERVICIO", cUnit, row + h - 14, 8, false, MUTED, 'L');
            for (int k = 0; k < lines.size(); k++) pdf.text(lines.get(k), cDesc, row + h - 14 - 12 * k, 9, false, INK, 'L');
            pdf.text(money(it.unitPrice), cPu, row + h - 14, 9, false, INK, 'R');
            pdf.text(money(it.lineTotal), cTot, row + h - 14, 9, false, INK, 'R');
            pdf.line(left, row, right, row, LINE, 0.5f);
        }

        // Totales
        float tY = row - 18;
        float tx = right - 200;
        String[][] totals = {
                {"Op. gravada", money(i.taxableAmount)},
                {"Op. exonerada", "0.00"},
                {"Op. inafecta", "0.00"},
                {"IGV (18 %)", money(i.igvAmount)},
        };
        for (String[] t : totals) {
            pdf.text(t[0], tx, tY, 9, false, INK, 'L');
            pdf.text("S/ " + t[1], right - 8, tY, 9, false, INK, 'R');
            tY -= 14;
        }
        tY -= 8;
        pdf.rect(tx - 6, tY - 8, right - tx + 6, 22, SOFT, BRAND, 1f);
        pdf.text("IMPORTE TOTAL", tx, tY - 1, 10, true, INK, 'L');
        pdf.text("S/ " + money(i.totalAmount), right - 8, tY - 1, 11, true, INK, 'R');

        // Importe en letras
        String son = words == null ? "" : words.toUpperCase().replaceFirst("^SON:?\\s*", "");
        pdf.text("SON: " + son, left, row - 18, 9, true, INK, 'L');

        // QR y leyendas
        float qy = Math.max(tY - 130, 90);
        boolean[][] q = qr(qrText(i, c));
        if (q != null) pdf.modules(q, left, qy, 92);
        float lx = left + 108;
        pdf.text("Representación impresa de la " + (factura ? "Factura" : "Boleta de Venta") + " Electrónica.", lx, qy + 78, 9, true, INK, 'L');
        pdf.text("Puede consultarla en www.sunat.gob.pe (Consulta de validez del comprobante).", lx, qy + 64, 8.5f, false, MUTED, 'L');
        pdf.text("Valor resumen (hash): " + (i.hash == null ? "-" : i.hash), lx, qy + 50, 8, false, MUTED, 'L');
        String estado = i.status == null ? "" : i.status;
        pdf.text("Estado en SUNAT: " + estado + (i.sunatDescription == null ? "" : " · " + cut(i.sunatDescription, 60)), lx, qy + 36, 8, false, MUTED, 'L');
        pdf.text("Servicios de salud prestados en " + c.address() + ".", lx, qy + 22, 8, false, MUTED, 'L');

        pdf.line(left, 70, right, 70, LINE, 0.6f);
        pdf.text("Gracias por su preferencia · " + (c.tradeName() == null ? c.businessName() : c.tradeName()), pageW / 2, 54, 9, true, BRAND, 'C');
        return pdf.build();
    }

    private static String cut(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    private static List<String> wrap(String s, int max) {
        List<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : s.split(" ")) {
            if (cur.length() + w.length() + 1 > max && cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
            if (cur.length() > 0) cur.append(' ');
            cur.append(w);
        }
        if (cur.length() > 0) out.add(cur.toString());
        if (out.isEmpty()) out.add("");
        return out;
    }
}
