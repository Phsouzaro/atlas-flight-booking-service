package com.atlas.assistant.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Acha PII em texto livre. Função pura: quem decide o que fazer com cada achado é o {@link PiiVault}.
 * <p>
 * Cobre o que dá para reconhecer por formato: cartão (com Luhn), CPF, passaporte, email e telefone.
 * Nome de pessoa não dá para detectar com segurança e passa sem máscara (ADR-005). Os padrões não pegam
 * UUID, localizador de reserva nem número de voo, que o modelo precisa ver.
 */
final class PiiDetector {

    enum Kind {
        CARD("CARTAO"),
        DOCUMENT("DOC"),
        EMAIL("EMAIL"),
        PHONE("TEL");

        final String tokenPrefix;

        Kind(String tokenPrefix) {
            this.tokenPrefix = tokenPrefix;
        }
    }

    record Finding(Kind kind, int start, int end, String value) {
    }

    // não colar em letra, dígito, hífen ou ponto: evita pegar pedaço de UUID, código ou número maior
    private static final String L = "(?<![\\w.-])";
    private static final String R = "(?![\\w-]|\\.\\w)";

    /** Em ordem de prioridade: o que casa primeiro ocupa o trecho. */
    private static final List<Rule> RULES = List.of(
            new Rule(Kind.CARD, Pattern.compile(L + "(?:\\d[ -]?){12,18}\\d" + R)),
            new Rule(Kind.EMAIL, Pattern.compile(L + "[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}" + R)),
            new Rule(Kind.DOCUMENT, Pattern.compile(L + "\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}" + R)),
            new Rule(Kind.PHONE, Pattern.compile(L + "(?:\\+55\\s?)?\\(?\\d{2}\\)?[\\s-]?9?\\d{4}[\\s-]\\d{4}" + R)),
            new Rule(Kind.DOCUMENT, Pattern.compile(L + "\\d{11}" + R)),
            new Rule(Kind.DOCUMENT, Pattern.compile(L + "[A-Za-z]{2}\\d{6,7}" + R)));

    private record Rule(Kind kind, Pattern pattern) {
    }

    private PiiDetector() {
    }

    static List<Finding> find(String text) {
        List<Finding> findings = new ArrayList<>();
        boolean[] taken = new boolean[text.length()];
        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(text);
            while (m.find()) {
                if (overlaps(taken, m.start(), m.end())) {
                    continue;
                }
                if (rule.kind() == Kind.CARD && !luhn(m.group())) {
                    continue;
                }
                for (int i = m.start(); i < m.end(); i++) {
                    taken[i] = true;
                }
                findings.add(new Finding(rule.kind(), m.start(), m.end(), m.group()));
            }
        }
        findings.sort(Comparator.comparingInt(Finding::start));
        return findings;
    }

    private static boolean overlaps(boolean[] taken, int start, int end) {
        for (int i = start; i < end; i++) {
            if (taken[i]) {
                return true;
            }
        }
        return false;
    }

    private static boolean luhn(String candidate) {
        String digits = candidate.replaceAll("\\D", "");
        if (digits.length() < 13 || digits.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    /** Primeiro nome e inicial do último: o modelo sabe de quem se trata sem receber o nome inteiro. */
    static String shortName(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }
}
