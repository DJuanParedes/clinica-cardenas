package pe.clinicacardenas.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Cálculo de importes con IGV incluido en el precio (servicios de salud gravados, tasa 18 %).
 * valor de venta = total / (1 + IGV); IGV = total - valor de venta.
 */
public final class InvoiceCalculator {

    private InvoiceCalculator() {}

    public record LineIn(String description, BigDecimal quantity, BigDecimal unitPrice) {}
    public record LineOut(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal unitValue,
                          BigDecimal lineValue, BigDecimal lineIgv, BigDecimal lineTotal) {}
    public record Totals(List<LineOut> lines, BigDecimal taxable, BigDecimal igv, BigDecimal total) {}

    public static Totals compute(List<LineIn> in, BigDecimal igvRate) {
        BigDecimal factor = BigDecimal.ONE.add(igvRate);
        List<LineOut> out = new ArrayList<>();
        BigDecimal taxable = BigDecimal.ZERO, igv = BigDecimal.ZERO, total = BigDecimal.ZERO;
        for (LineIn l : in) {
            BigDecimal qty = l.quantity().setScale(2, RoundingMode.HALF_UP);
            BigDecimal price = l.unitPrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = price.multiply(qty).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineValue = lineTotal.divide(factor, 2, RoundingMode.HALF_UP);
            BigDecimal lineIgv = lineTotal.subtract(lineValue);
            BigDecimal unitValue = price.divide(factor, 4, RoundingMode.HALF_UP);
            out.add(new LineOut(l.description(), qty, price, unitValue, lineValue, lineIgv, lineTotal));
            taxable = taxable.add(lineValue);
            igv = igv.add(lineIgv);
            total = total.add(lineTotal);
        }
        return new Totals(out, taxable, igv, total);
    }
}
