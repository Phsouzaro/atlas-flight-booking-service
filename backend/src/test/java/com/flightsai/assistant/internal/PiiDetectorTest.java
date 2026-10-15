package com.flightsai.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.flightsai.assistant.internal.PiiDetector.Finding;
import com.flightsai.assistant.internal.PiiDetector.Kind;

class PiiDetectorTest {

    @Test
    void findsDocumentsContactsAndCards() {
        assertThat(kinds("CPF 123.456.789-09")).containsExactly(Kind.DOCUMENT);
        assertThat(kinds("cpf 12345678909")).containsExactly(Kind.DOCUMENT);
        assertThat(kinds("passaporte FX1234567")).containsExactly(Kind.DOCUMENT);
        assertThat(kinds("email ana.souza+viagem@exemplo.com.br")).containsExactly(Kind.EMAIL);
        assertThat(kinds("tel (11) 98765-4321")).containsExactly(Kind.PHONE);
        assertThat(kinds("tel +55 11 98765-4321")).containsExactly(Kind.PHONE);
        assertThat(kinds("cartão 4111 1111 1111 1111")).containsExactly(Kind.CARD);
        assertThat(kinds("cartão 4111111111111111")).containsExactly(Kind.CARD);
    }

    @Test
    void ignoresWhatTheModelNeedsToSee() {
        assertThat(PiiDetector.find("""
                voo AT100 de GRU para GIG em 2026-03-15 às 08:00, reserva Q78VHS, 2 passageiros,
                total BRL 799.80, id 5b197a5e-1a63-38c6-b57e-8f23451b9786, flight 11111111-2222-3333-4444-555555555555
                """)).isEmpty();
    }

    @Test
    void cardWithInvalidChecksumIsNotACard() {
        assertThat(kinds("número 4111 1111 1111 1112")).doesNotContain(Kind.CARD);
    }

    @Test
    void reportsPositionsInOrder() {
        String text = "sou ana@exemplo.com, CPF 123.456.789-09";
        List<Finding> findings = PiiDetector.find(text);

        assertThat(findings).extracting(Finding::value).containsExactly("ana@exemplo.com", "123.456.789-09");
        assertThat(text.substring(findings.get(1).start(), findings.get(1).end())).isEqualTo("123.456.789-09");
    }

    @Test
    void shortensNames() {
        assertThat(PiiDetector.shortName("Ana Maria Souza")).isEqualTo("Ana S.");
        assertThat(PiiDetector.shortName("Ana")).isEqualTo("Ana");
    }

    private static List<Kind> kinds(String text) {
        return PiiDetector.find(text).stream().map(Finding::kind).toList();
    }
}
