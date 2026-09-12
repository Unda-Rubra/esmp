package xdu.undarubra.esmp.economy;

import java.util.Objects;

public record CurrencyDefinition(
        String id, String singularName, String pluralName, String symbol, String format) {
    public CurrencyDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(singularName);
        Objects.requireNonNull(pluralName);
        Objects.requireNonNull(symbol);
        Objects.requireNonNull(format);
        if (!id.matches("[a-z][a-z0-9_.:-]{0,63}")
                || singularName.isBlank()
                || pluralName.isBlank()
                || singularName.length() > 48
                || pluralName.length() > 48
                || symbol.length() > 12
                || format.length() > 128
                || !format.contains("{amount}")
                || singularName.contains("\n")
                || pluralName.contains("\n")
                || format.contains("\n")) {
            throw new IllegalArgumentException("Invalid currency identity or display format");
        }
    }

    public String display(long amount) {
        return format.replace("{amount}", Long.toString(amount))
                .replace("{name}", amount == 1 ? singularName : pluralName)
                .replace("{symbol}", symbol);
    }

    public static CurrencyDefinition defaults() {
        return new CurrencyDefinition("esmp:coin", "coin", "coins", "¤", "{amount} {name}");
    }
}
