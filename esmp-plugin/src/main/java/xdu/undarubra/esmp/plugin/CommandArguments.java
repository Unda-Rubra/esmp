package xdu.undarubra.esmp.plugin;

import java.util.ArrayList;

/** Decodes double-quoted arguments without changing whitespace inside them. */
public final class CommandArguments {
    private CommandArguments() {}

    public static String[] parse(String[] raw) {
        boolean hasQuotes = false;
        for (String arg : raw)
            if (arg.indexOf('"') >= 0) {
                hasQuotes = true;
                break;
            }
        if (!hasQuotes) return raw;
        String input = String.join(" ", raw);
        var result = new ArrayList<String>();
        int i = 0;
        while (i < input.length()) {
            if (input.charAt(i) == ' ') {
                i++;
                continue;
            }
            if (input.charAt(i) == '"') {
                int start = ++i;
                while (i < input.length() && input.charAt(i) != '"') i++;
                if (i == input.length())
                    throw new IllegalArgumentException("Close the double quote around the name.");
                result.add(input.substring(start, i++));
                if (i < input.length() && input.charAt(i) != ' ')
                    throw new IllegalArgumentException("Separate quoted arguments with a space.");
            } else {
                int start = i;
                while (i < input.length() && input.charAt(i) != ' ') {
                    if (input.charAt(i) == '"')
                        throw new IllegalArgumentException(
                                "Put double quotes around the whole name.");
                    i++;
                }
                result.add(input.substring(start, i));
            }
        }
        return result.toArray(String[]::new);
    }

    public static String quote(String name) {
        return name.indexOf(' ') < 0 ? name : '"' + name + '"';
    }
}
