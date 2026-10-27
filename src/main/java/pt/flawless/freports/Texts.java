package pt.flawless.freports;

import java.util.ArrayList;
import java.util.List;

/** Utilitários de texto: corte seguro e quebra de linhas para lore. */
final class Texts {

    static final int WRAP = 40;

    private Texts() {
    }

    /** Corta a {@code max} chars sem partir um par de surrogates. */
    static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        int end = max;
        if (end > 0 && Character.isHighSurrogate(s.charAt(end - 1))) end--;
        return s.substring(0, end);
    }

    /** Parte o texto em linhas de ~{@link #WRAP} caracteres, quebrando em espaços. */
    static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (word.isEmpty()) continue;
            while (word.length() > WRAP) {
                if (cur.length() > 0) {
                    lines.add(cur.toString());
                    cur.setLength(0);
                }
                String part = truncate(word, WRAP);
                lines.add(part);
                word = word.substring(part.length());
            }
            if (cur.length() > 0 && cur.length() + 1 + word.length() > WRAP) {
                lines.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(word);
        }
        if (cur.length() > 0) lines.add(cur.toString());
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    /** Acrescenta {@code template} com o texto na primeira linha e o resto em linhas seguintes. */
    static void addWrapped(List<String> lore, String template, String placeholder, String text) {
        List<String> lines = wrap(text);
        lore.add(template.replace(placeholder, lines.get(0)));
        for (int i = 1; i < lines.size(); i++) lore.add("§f" + lines.get(i));
    }
}
