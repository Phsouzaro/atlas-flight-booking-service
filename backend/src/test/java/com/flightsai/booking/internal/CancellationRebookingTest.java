package com.flightsai.booking.internal;

import static com.flightsai.booking.internal.BookingTestData.NOW;
import static com.flightsai.booking.internal.BookingTestData.addFare;
import static com.flightsai.booking.internal.BookingTestData.available;
import static com.flightsai.booking.internal.BookingTestData.flightOnRoute;
import static com.flightsai.booking.internal.BookingTestData.holdBody;
import static com.flightsai.booking.internal.BookingTestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightsai.shared.MutableClock;
import com.jayway.jsonpath.JsonPath;

/**
 * Cancelamento e remarcação em duas etapas (cotação, depois confirmação da ação pendente) contra um
 * PostgreSQL real. Relógio começa em 2026-03-10 12:00 UTC; os voos saem 15 dias depois.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "app.security.jwt.secret=test-secret-with-at-least-32-bytes-0123456789",
        "app.booking.expiration-job-enabled=false"})
class CancellationRebookingTest {

    static final Instant DEPARTURE = NOW.plus(Duration.ofDays(15));
    static final BigDecimal LIGHT = new BigDecimal("399.90");
    static final BigDecimal FLEX = new BigDecimal("559.90");
    static final BigDecimal BUSINESS = new BigDecimal("1279.90");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @TestConfiguration
    static class Clocks {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(NOW);
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    UUID me = UUID.randomUUID();
    UUID flight;
    UUID laterFlight;

    @BeforeEach
    void fixture() {
        clock.set(NOW);
        BookingTestData.reset(jdbc);
        flight = flightWithAllFares("AT100", DEPARTURE);
        laterFlight = flightWithAllFares("AT102", DEPARTURE.plus(Duration.ofHours(6)));
    }

    // ---- cancelamento ---------------------------------------------------------------------------

    @Test
    void quoteHasNoEffectAndConfirmationCancelsWithFullRefundInsideFreeWindow() throws Exception {
        String booking = confirmedBooking(flight, "ECONOMY_LIGHT", 2);
        clock.advance(Duration.ofHours(1));

        String action = quoteCancellation(booking)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.freeWindow").value(true))
                .andExpect(jsonPath("$.paid.amount").value(799.80))
                .andExpect(jsonPath("$.refund.amount").value(799.80))
                .andExpect(jsonPath("$.retained.amount").value(0))
                .andExpect(jsonPath("$.expiresAt").value("2026-03-10T13:10:00Z"))
                .andReturn().getResponse().getContentAsString();
        String actionId = JsonPath.read(action, "$.pendingActionId");

        // a cotação sozinha não muda nada
        bookingStatus(booking, "CONFIRMED");
        assertThat(available(jdbc, flight, "ECONOMY_LIGHT")).isEqualTo(8);
        mvc.perform(get("/pending-actions/{id}", actionId).with(user(me)))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.type").value("BOOKING_CANCEL"))
                .andExpect(jsonPath("$.summary").value(startsWith("Cancelar a reserva")));

        confirmAction(actionId, "a-1", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.result.status").value("CANCELLED"))
                .andExpect(jsonPath("$.result.refund.amount").value(799.80));

        bookingStatus(booking, "CANCELLED");
        assertThat(available(jdbc, flight, "ECONOMY_LIGHT")).isEqualTo(10); // devolvido pelo evento
        assertThat(jdbc.queryForObject("SELECT refund_reference FROM bookings WHERE id = ?::uuid", String.class, booking))
                .startsWith("SIMR-");
        assertThat(auditActions()).contains("PENDING_ACTION_PROPOSED", "BOOKING_CANCELLED", "PENDING_ACTION_CONFIRMED");
    }

    @Test
    void appliesFareRulesOutsideTheFreeWindow() throws Exception {
        String light = confirmedBooking(flight, "ECONOMY_LIGHT", 1);
        String flex = confirmedBooking(flight, "ECONOMY_FLEX", 2);
        String business = confirmedBooking(flight, "BUSINESS", 1);
        clock.advance(Duration.ofDays(2));

        quoteCancellation(light).andExpect(jsonPath("$.freeWindow").value(false))
                .andExpect(jsonPath("$.refund.amount").value(0))
                .andExpect(jsonPath("$.retained.amount").value(399.90));
        quoteCancellation(flex).andExpect(jsonPath("$.refund.amount").value(819.80))
                .andExpect(jsonPath("$.retained.amount").value(300.00));
        quoteCancellation(business).andExpect(jsonPath("$.refund.amount").value(1279.90));

        // reembolso zero cancela sem estorno
        confirmAction(actionFrom(quoteCancellation(light)), "a-1", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CANCELLED"));
        assertThat(jdbc.queryForObject("SELECT refund_reference FROM bookings WHERE id = ?::uuid", String.class, light)).isNull();
        assertThat(available(jdbc, flight, "ECONOMY_LIGHT")).isEqualTo(10);
    }

    @Test
    void freeWindowNeedsSevenDaysOfAdvance() throws Exception {
        UUID soon = flightWithAllFares("AT104", NOW.plus(Duration.ofDays(3)));
        String booking = confirmedBooking(soon, "ECONOMY_LIGHT", 1);

        quoteCancellation(booking)
                .andExpect(jsonPath("$.freeWindow").value(false))
                .andExpect(jsonPath("$.refund.amount").value(0));
    }

    @Test
    void confirmationRevalidatesAndRefusesWhenTheQuoteNoLongerHolds() throws Exception {
        String booking = confirmedBooking(flight, "ECONOMY_LIGHT", 1);
        clock.advance(Duration.ofHours(23).plusMinutes(55));
        String actionId = actionFrom(quoteCancellation(booking)
                .andExpect(jsonPath("$.refund.amount").value(399.90)));

        clock.advance(Duration.ofMinutes(6)); // passou das 24h: o reembolso agora é zero
        confirmAction(actionId, "a-1", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("QUOTE_CHANGED"));

        bookingStatus(booking, "CONFIRMED");
        mvc.perform(get("/pending-actions/{id}", actionId).with(user(me))).andExpect(jsonPath("$.status").value("PENDING"));

        String fresh = actionFrom(quoteCancellation(booking).andExpect(jsonPath("$.refund.amount").value(0)));
        confirmAction(fresh, "a-2", null).andExpect(status().isOk());
        bookingStatus(booking, "CANCELLED");
    }

    @Test
    void expiredOrRejectedActionsCannotBeConfirmed() throws Exception {
        String booking = confirmedBooking(flight, "ECONOMY_LIGHT", 1);

        String expiring = actionFrom(quoteCancellation(booking));
        clock.advance(Duration.ofMinutes(10));
        mvc.perform(get("/pending-actions/{id}", expiring).with(user(me))).andExpect(jsonPath("$.status").value("EXPIRED"));
        confirmAction(expiring, "a-1", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("ACTION_CLOSED"));

        String rejected = actionFrom(quoteCancellation(booking));
        mvc.perform(post("/pending-actions/{id}/reject", rejected).with(user(me)).header("Idempotency-Key", "r-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        confirmAction(rejected, "a-2", null).andExpect(status().isConflict());

        bookingStatus(booking, "CONFIRMED");
        assertThat(available(jdbc, flight, "ECONOMY_LIGHT")).isEqualTo(9);
    }

    @Test
    void confirmingTwiceRunsOnce() throws Exception {
        String booking = confirmedBooking(flight, "ECONOMY_LIGHT", 1);
        String actionId = actionFrom(quoteCancellation(booking));

        confirmAction(actionId, "a-1", null).andExpect(status().isOk());
        confirmAction(actionId, "a-1", null).andExpect(status().isOk()); // replay pela Idempotency-Key
        confirmAction(actionId, "a-2", null).andExpect(status().isConflict()); // outra chave: já confirmada

        assertThat(available(jdbc, flight, "ECONOMY_LIGHT")).isEqualTo(10);
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'BOOKING_CANCELLED'")).isEqualTo(1);
    }

    @Test
    void simultaneousConfirmationsWithDifferentKeysRunOnce() throws Exception {
        String booking = confirmedBooking(flight, "BUSINESS", 1);
        String actionId = actionFrom(quoteCancellation(booking));

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                String key = "a-" + i;
                results.add(pool.submit(() -> {
                    start.await();
                    return confirmAction(actionId, key, null).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(60, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsOnly(200, 409).filteredOn(s -> s == 200).hasSize(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(available(jdbc, flight, "BUSINESS")).isEqualTo(10);
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'BOOKING_CANCELLED'")).isEqualTo(1);
    }

    @Test
    void onlyConfirmedFutureBookingsCanBeCancelled() throws Exception {
        String held = holdId(flight, "ECONOMY_LIGHT", 1, "k-held");
        quoteCancellation(held).andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("INVALID_STATE"));

        String booking = confirmedBooking(flight, "ECONOMY_LIGHT", 1);
        clock.set(DEPARTURE);
        quoteCancellation(booking).andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("FLIGHT_DEPARTED"));
    }

    @Test
    void otherUsersCannotSeeOrConfirmMyActions() throws Exception {
        String booking = confirmedBooking(flight, "ECONOMY_LIGHT", 1);
        String actionId = actionFrom(quoteCancellation(booking));
        UUID other = UUID.randomUUID();

        mvc.perform(get("/pending-actions/{id}", actionId).with(user(other))).andExpect(status().isNotFound());
        mvc.perform(post("/pending-actions/{id}/confirm", actionId).with(user(other)).header("Idempotency-Key", "x"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/bookings/{id}/cancellation-quote", booking).with(user(other))).andExpect(status().isNotFound());
        bookingStatus(booking, "CONFIRMED");
    }

    // ---- remarcação -----------------------------------------------------------------------------

    @Test
    void rebooksInOneTransactionChargingChangeFee() throws Exception {
        String booking = confirmedBooking(flight, "ECONOMY_LIGHT", 2);

        String actionId = actionFrom(proposeRebooking(booking, laterFlight, "ECONOMY_LIGHT")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.newFlightNumber").value("AT102"))
                .andExpect(jsonPath("$.seats").value(2))
                .andExpect(jsonPath("$.changeFee.amount").value(500.00))
                .andExpect(jsonPath("$.fareDifference.amount").value(0))
                .andExpect(jsonPath("$.amountDue.amount").value(500.00)));
        assertThat(available(jdbc, laterFlight, "ECONOMY_LIGHT")).isEqualTo(10); // proposta não reserva

        confirmAction(actionId, "a-1", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reason").value("PAYMENT_REQUIRED"));

        String result = confirmAction(actionId, "a-2", "tok_visa")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.result.rebookedFromId").value(booking))
                .andExpect(jsonPath("$.result.items[0].flightNumber").value("AT102"))
                .andExpect(jsonPath("$.result.passengers.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        String replacement = JsonPath.read(result, "$.result.id");

        mvc.perform(get("/bookings/{id}", booking).with(user(me)))
                .andExpect(jsonPath("$.status").value("REBOOKED"))
                .andExpect(jsonPath("$.rebookedToId").value(replacement));
        assertThat(available(jdbc, flight, "ECONOMY_LIGHT")).isEqualTo(10);
        assertThat(available(jdbc, laterFlight, "ECONOMY_LIGHT")).isEqualTo(8);
        assertThat(auditActions()).contains("BOOKING_REBOOKED", "BOOKING_CREATED_BY_REBOOKING");
    }

    @Test
    void upgradeChargesDifferenceAndDowngradeChargesNothing() throws Exception {
        String flex = confirmedBooking(flight, "ECONOMY_FLEX", 1);
        proposeRebooking(flex, flight, "BUSINESS")
                .andExpect(jsonPath("$.changeFee.amount").value(0))
                .andExpect(jsonPath("$.fareDifference.amount").value(720.00))
                .andExpect(jsonPath("$.amountDue.amount").value(720.00));

        String business = confirmedBooking(flight, "BUSINESS", 1);
        String originalPayment = jdbc.queryForObject("SELECT payment_reference FROM bookings WHERE id = ?::uuid",
                String.class, business);
        String actionId = actionFrom(proposeRebooking(business, laterFlight, "ECONOMY_LIGHT")
                .andExpect(jsonPath("$.fareDifference.amount").value(-880.00))
                .andExpect(jsonPath("$.amountDue.amount").value(0)));

        // nada a pagar: confirma sem cartão e a reserva nova herda a referência do pagamento original
        confirmAction(actionId, "a-1", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.paymentReference").value(originalPayment))
                .andExpect(jsonPath("$.result.total.amount").value(399.90));
    }

    @Test
    void rebookingKeepsTheRoute() throws Exception {
        String booking = confirmedBooking(flight, "ECONOMY_LIGHT", 1);
        UUID otherRoute = flightOnRoute(jdbc, "AT900", "CGH", "GIG", DEPARTURE);
        addFare(jdbc, otherRoute, "ECONOMY_LIGHT", 10, LIGHT);

        proposeRebooking(booking, otherRoute, "ECONOMY_LIGHT")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reason").value("REBOOKING_NOT_ALLOWED"));
        proposeRebooking(booking, flight, "ECONOMY_LIGHT")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reason").value("REBOOKING_NOT_ALLOWED"));
    }

    @Test
    void confirmationFailsCleanlyWhenNewFlightSoldOutOrPriceChanged() throws Exception {
        String booking = confirmedBooking(flight, "ECONOMY_LIGHT", 1);
        String soldOut = actionFrom(proposeRebooking(booking, laterFlight, "BUSINESS"));
        String priced = actionFrom(proposeRebooking(booking, laterFlight, "ECONOMY_FLEX"));

        jdbc.update("UPDATE seat_inventory SET available_seats = 0 WHERE flight_id = ? AND fare_class_code = 'BUSINESS'",
                laterFlight);
        confirmAction(soldOut, "a-1", "tok_visa")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("SEATS_UNAVAILABLE"));

        jdbc.update("UPDATE seat_inventory SET price_amount = 999.90 WHERE flight_id = ? AND fare_class_code = 'ECONOMY_FLEX'",
                laterFlight);
        confirmAction(priced, "a-2", "tok_visa")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("QUOTE_CHANGED"));

        // nada mudou: reserva original intacta, assentos no lugar
        bookingStatus(booking, "CONFIRMED");
        assertThat(available(jdbc, flight, "ECONOMY_LIGHT")).isEqualTo(9);
        assertThat(available(jdbc, laterFlight, "ECONOMY_FLEX")).isEqualTo(10);
        assertThat(count("SELECT count(*) FROM bookings")).isEqualTo(1);
    }

    @Test
    void declinedPaymentRollsBackTheWholeRebooking() throws Exception {
        String booking = confirmedBooking(flight, "ECONOMY_LIGHT", 1);
        String actionId = actionFrom(proposeRebooking(booking, laterFlight, "ECONOMY_LIGHT"));

        confirmAction(actionId, "a-1", "tok_decline")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.reason").value("PAYMENT_DECLINED"));

        bookingStatus(booking, "CONFIRMED");
        assertThat(available(jdbc, laterFlight, "ECONOMY_LIGHT")).isEqualTo(10);
        assertThat(available(jdbc, flight, "ECONOMY_LIGHT")).isEqualTo(9);
    }

    @Test
    void rebookedBookingsFollowTheirOwnRules() throws Exception {
        String booking = confirmedBooking(flight, "ECONOMY_FLEX", 1);
        String actionId = actionFrom(proposeRebooking(booking, laterFlight, "ECONOMY_FLEX"));
        String replacement = JsonPath.read(confirmAction(actionId, "a-1", null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.result.id");

        // a antiga não cancela nem remarca de novo
        quoteCancellation(booking).andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("INVALID_STATE"));
        // a nova não ganha uma janela de 24h nova: Flex com multa, mesmo uma hora depois
        quoteCancellation(replacement)
                .andExpect(jsonPath("$.freeWindow").value(false))
                .andExpect(jsonPath("$.refund.amount").value(409.90));
    }

    // ---- apoio ----------------------------------------------------------------------------------

    private UUID flightWithAllFares(String number, Instant departure) {
        UUID id = flightOnRoute(jdbc, number, "GRU", "GIG", departure);
        addFare(jdbc, id, "ECONOMY_LIGHT", 10, LIGHT);
        addFare(jdbc, id, "ECONOMY_FLEX", 10, FLEX);
        addFare(jdbc, id, "BUSINESS", 10, BUSINESS);
        return id;
    }

    private String holdId(UUID flightId, String fareClass, int passengers, String key) throws Exception {
        String response = mvc.perform(post("/bookings").with(user(me))
                        .header("Idempotency-Key", key)
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(holdBody(flightId, fareClass, passengers))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String confirmedBooking(UUID flightId, String fareClass, int passengers) throws Exception {
        String key = UUID.randomUUID().toString();
        String id = holdId(flightId, fareClass, passengers, key);
        mvc.perform(post("/bookings/{id}/confirm", id).with(user(me))
                        .header("Idempotency-Key", key + "-c")
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("cardToken", "tok_visa"))))
                .andExpect(status().isOk());
        return id;
    }

    private ResultActions quoteCancellation(String bookingId) throws Exception {
        return mvc.perform(post("/bookings/{id}/cancellation-quote", bookingId).with(user(me)));
    }

    private ResultActions proposeRebooking(String bookingId, UUID flightId, String fareClass) throws Exception {
        return mvc.perform(post("/bookings/{id}/rebooking-proposal", bookingId).with(user(me))
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("flightId", flightId, "fareClass", fareClass))));
    }

    private ResultActions confirmAction(String actionId, String key, String cardToken) throws Exception {
        var request = post("/pending-actions/{id}/confirm", actionId).with(user(me)).header("Idempotency-Key", key);
        if (cardToken != null) {
            request.contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("cardToken", cardToken)));
        }
        return mvc.perform(request);
    }

    private static String actionFrom(ResultActions quote) throws Exception {
        return JsonPath.read(quote.andReturn().getResponse().getContentAsString(), "$.pendingActionId");
    }

    private void bookingStatus(String bookingId, String expected) throws Exception {
        mvc.perform(get("/bookings/{id}", bookingId).with(user(me))).andExpect(jsonPath("$.status").value(expected));
    }

    private List<String> auditActions() {
        return jdbc.queryForList("SELECT action FROM audit_log ORDER BY occurred_at", String.class);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
