package com.atlas.assistant.internal;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.atlas.assistant.internal.ToolViews.BookingInfo;
import com.atlas.assistant.internal.ToolViews.FlightInfo;
import com.atlas.assistant.internal.ToolViews.PolicyExcerpt;
import com.atlas.booking.BookingApi;
import com.atlas.booking.BookingView;
import com.atlas.booking.CancellationQuote;
import com.atlas.booking.HoldBookingCommand;
import com.atlas.booking.PassengerInfo;
import com.atlas.booking.RebookingProposal;
import com.atlas.catalog.FlightSearchApi;
import com.atlas.catalog.FlightSearchCriteria;

/**
 * Tudo o que o modelo pode fazer. Só consulta e proposta (ADR-002): não há tool que pague, confirme,
 * cancele ou remarque. O usuário vem do token, pelas APIs de catalog e booking; nenhuma tool recebe id
 * de usuário.
 * <p>
 * Exceções viram texto para o modelo (o Spring AI devolve a mensagem como resultado da tool), então as
 * mensagens das APIs precisam ser seguras para mostrar.
 */
@Component
class AssistantTools {

    /** Nomes que o modelo pode chamar. Qualquer outro é bloqueado antes de executar. */
    static final Set<String> NAMES = Set.of("searchFlights", "getFlightDetails", "getMyBookings", "getBookingDetails",
            "createBookingHold", "quoteCancellation", "proposeRebooking", "searchPolicies");

    private static final int MAX_FLIGHTS = 10;

    private final FlightSearchApi flights;
    private final BookingApi bookings;
    private final PolicyStore policies;
    private final PiiVault vault;

    AssistantTools(FlightSearchApi flights, BookingApi bookings, PolicyStore policies, PiiVault vault) {
        this.flights = flights;
        this.bookings = bookings;
        this.policies = policies;
        this.vault = vault;
    }

    @Tool(description = "Busca voos só de ida numa data. Devolve até 10 voos com tarifas, assentos e preço por "
            + "passageiro. Única fonte de preço e disponibilidade.")
    public List<FlightInfo> searchFlights(
            @ToolParam(description = "Código IATA da origem, ex.: GRU") String origin,
            @ToolParam(description = "Código IATA do destino, ex.: GIG") String destination,
            @ToolParam(description = "Data da partida no formato AAAA-MM-DD, no fuso da origem") String date,
            @ToolParam(description = "Quantidade de passageiros, de 1 a 9") int passengers) {
        return flights.search(new FlightSearchCriteria(origin, destination, parseDate(date), passengers)).stream()
                .limit(MAX_FLIGHTS)
                .map(FlightInfo::from)
                .toList();
    }

    @Tool(description = "Detalhe de um voo com todas as tarifas, inclusive esgotadas.")
    public FlightInfo getFlightDetails(@ToolParam(description = "flightId devolvido por searchFlights") String flightId) {
        return flights.findFlight(parseId(flightId, "flightId")).map(FlightInfo::from)
                .orElseThrow(() -> new IllegalArgumentException("Voo não encontrado: " + flightId));
    }

    @Tool(description = "Reservas do usuário logado, da mais recente para a mais antiga.")
    public List<BookingInfo> getMyBookings() {
        return bookings.myBookings().stream().map(this::bookingInfo).toList();
    }

    @Tool(description = "Detalhe de uma reserva do usuário logado.")
    public BookingInfo getBookingDetails(@ToolParam(description = "bookingId devolvido por getMyBookings") String bookingId) {
        return bookings.findMyBooking(parseId(bookingId, "bookingId")).map(this::bookingInfo)
                .orElseThrow(() -> new IllegalArgumentException("Reserva não encontrada: " + bookingId));
    }

    @Tool(description = "Segura assentos por 15 minutos (status HELD). Não cobra nada: o pagamento é feito pelo "
            + "usuário na tela de reservas. Use só depois de o usuário escolher voo e tarifa e informar os passageiros.")
    public BookingInfo createBookingHold(
            @ToolParam(description = "flightId devolvido por searchFlights") String flightId,
            @ToolParam(description = "Classe tarifária, ex.: ECONOMY_LIGHT") String fareClass,
            @ToolParam(description = "Passageiros. documentNumber pode ser um token como [DOC_1]; use exatamente o "
                    + "token que aparece na conversa") List<PassengerInput> passengers) {
        UUID conversationId = TurnContext.current().conversationId();
        List<PassengerInfo> info = passengers.stream()
                .map(p -> new PassengerInfo(p.fullName(), vault.detokenize(conversationId, p.documentNumber()),
                        parseDate(p.birthDate())))
                .toList();
        if (info.isEmpty() || info.size() > HoldBookingCommand.MAX_PASSENGERS) {
            throw new IllegalArgumentException("Informe de 1 a " + HoldBookingCommand.MAX_PASSENGERS + " passageiros");
        }
        return bookingInfo(bookings.hold(new HoldBookingCommand(parseId(flightId, "flightId"), fareClass, info)));
    }

    @Tool(description = "Cota o cancelamento de uma reserva confirmada e cria uma proposta para o usuário confirmar "
            + "na tela. NÃO cancela: nada acontece até o usuário clicar em Confirmar.")
    public CancellationQuote quoteCancellation(@ToolParam(description = "bookingId da reserva") String bookingId) {
        CancellationQuote quote = bookings.quoteCancellation(parseId(bookingId, "bookingId"));
        TurnContext.current().recordPendingAction(quote.pendingActionId());
        return quote;
    }

    @Tool(description = "Propõe trocar uma reserva confirmada para outro voo da mesma rota (e, se quiser, outra "
            + "classe), com multa e diferença. NÃO remarca: nada acontece até o usuário clicar em Confirmar.")
    public RebookingProposal proposeRebooking(
            @ToolParam(description = "bookingId da reserva atual") String bookingId,
            @ToolParam(description = "flightId do voo novo, devolvido por searchFlights") String newFlightId,
            @ToolParam(description = "Classe tarifária no voo novo") String fareClass) {
        RebookingProposal proposal = bookings.proposeRebooking(parseId(bookingId, "bookingId"),
                parseId(newFlightId, "newFlightId"), fareClass);
        TurnContext.current().recordPendingAction(proposal.pendingActionId());
        return proposal;
    }

    @Tool(description = "Consulta as políticas da companhia (bagagem, cancelamento, remarcação, tarifas, reserva e "
            + "pagamento). Não traz preço nem disponibilidade.")
    public List<PolicyExcerpt> searchPolicies(@ToolParam(description = "Pergunta sobre a política") String question) {
        return policies.search(question);
    }

    /**
     * A reserva guarda a partida em UTC; o horário local vem do catálogo, no fuso da origem. Sem o voo
     * (removido do catálogo), cai para UTC explícito.
     */
    private BookingInfo bookingInfo(BookingView booking) {
        BookingView.Item item = booking.items().get(0);
        String departure = flights.findFlight(item.flightId())
                .map(f -> f.departureAt().toString())
                .orElse(item.departureAt().toString());
        return BookingInfo.from(booking, departure);
    }

    /** Passageiro como o modelo informa. Datas e documentos chegam como texto e são validados aqui. */
    public record PassengerInput(
            @ToolParam(description = "Nome completo") String fullName,
            @ToolParam(description = "Documento ou o token dele, ex.: [DOC_1]") String documentNumber,
            @ToolParam(description = "Data de nascimento AAAA-MM-DD") String birthDate) {
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("Data inválida, use AAAA-MM-DD: " + value);
        }
    }

    private static UUID parseId(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(field + " inválido: " + value);
        }
    }
}
