package com.atlas.booking.internal;

import static com.atlas.booking.internal.BookingTestData.DEPARTURE;
import static com.atlas.booking.internal.BookingTestData.NOW;
import static com.atlas.booking.internal.BookingTestData.available;
import static com.atlas.booking.internal.BookingTestData.holdBody;
import static com.atlas.booking.internal.BookingTestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.IllegalTransactionStateException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.atlas.audit.AuditEntry;
import com.atlas.audit.AuditLog;
import com.atlas.shared.MutableClock;
import com.atlas.shared.idempotency.IdempotencyKeyCleanup;
import com.jayway.jsonpath.JsonPath;

/** Hold, confirmação, expiração e Idempotency-Key contra um PostgreSQL real. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "app.security.jwt.secret=test-secret-with-at-least-32-bytes-0123456789",
        "app.booking.expiration-job-enabled=false"})
class BookingFlowTest {

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

    @Autowired
    BookingService bookingService;

    @Autowired
    AuditLog auditLog;

    @Autowired
    IdempotencyKeyCleanup idempotencyCleanup;

    UUID me = UUID.randomUUID();
    UUID flight;

    @BeforeEach
    void fixture() {
        clock.set(NOW);
        BookingTestData.reset(jdbc);
        flight = BookingTestData.flight(jdbc, "AT100", DEPARTURE, 10);
    }

    @Test
    void holdsSeatsThenConfirmsWithPayment() throws Exception {
        String id = hold(me, "k-1", holdBody(flight, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("[A-Z2-9]{6}")))
                .andExpect(jsonPath("$.total.amount").value(799.80))
                .andExpect(jsonPath("$.total.currency").value("BRL"))
                .andExpect(jsonPath("$.holdExpiresAt").value("2026-03-10T12:15:00Z"))
                .andExpect(jsonPath("$.items[0].flightNumber").value("AT100"))
                .andExpect(jsonPath("$.items[0].origin").value("GRU"))
                .andExpect(jsonPath("$.items[0].seats").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice.amount").value(399.90))
                .andExpect(jsonPath("$.passengers[*].fullName", contains("Passageira 0", "Passageira 1")))
                .andReturn().getResponse().getContentAsString();
        String bookingId = JsonPath.read(id, "$.id");
        assertThat(available(jdbc, flight)).isEqualTo(8);

        clock.advance(Duration.ofMinutes(5));
        confirm(me, "c-1", bookingId, "tok_visa")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").value("2026-03-10T12:05:00Z"))
                .andExpect(jsonPath("$.paymentReference").value(startsWith("SIM-")));

        mvc.perform(get("/bookings/{id}", bookingId).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(available(jdbc, flight)).isEqualTo(8);
        assertThat(auditActions(bookingId)).containsExactly("BOOKING_HELD", "BOOKING_CONFIRMED");
    }

    @Test
    void auditNeverContainsPassengerData() throws Exception {
        String bookingId = holdId(me, "k-1", holdBody(flight, 1));

        String details = jdbc.queryForObject("SELECT details::text FROM audit_log WHERE entity_id = ?",
                String.class, bookingId);
        assertThat(details).contains("\"seats\"").doesNotContain("Passageira").doesNotContain("DOC0");
        assertThat(jdbc.queryForObject("SELECT actor_user_id FROM audit_log WHERE entity_id = ?", UUID.class, bookingId))
                .isEqualTo(me);
    }

    @Test
    void auditRequiresTheCallersTransaction() {
        assertThatThrownBy(() -> auditLog.record(new AuditEntry(me, "TEST", "test", "1", Map.of())))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void repeatingHoldWithSameKeyReturnsSameBookingOnce() throws Exception {
        String first = hold(me, "k-1", holdBody(flight, 2))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String second = hold(me, "k-1", holdBody(flight, 2))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(second, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
        assertThat(available(jdbc, flight)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings", Long.class)).isEqualTo(1);
    }

    @Test
    void sameKeyFromAnotherUserIsIndependent() throws Exception {
        hold(me, "k-1", holdBody(flight, 1)).andExpect(status().isCreated());
        hold(UUID.randomUUID(), "k-1", holdBody(flight, 1))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));

        assertThat(available(jdbc, flight)).isEqualTo(8);
    }

    @Test
    void rejectsKeyReusedWithDifferentBody() throws Exception {
        hold(me, "k-1", holdBody(flight, 1)).andExpect(status().isCreated());

        hold(me, "k-1", holdBody(flight, 2))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(available(jdbc, flight)).isEqualTo(9);
    }

    @Test
    void requiresValidIdempotencyKey() throws Exception {
        mvc.perform(post("/bookings").with(user(me)).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(holdBody(flight, 1))))
                .andExpect(status().isBadRequest());
        hold(me, " ", holdBody(flight, 1)).andExpect(status().isBadRequest());
        hold(me, "x".repeat(101), holdBody(flight, 1)).andExpect(status().isBadRequest());

        assertThat(available(jdbc, flight)).isEqualTo(10);
    }

    @Test
    void rejectsWhenNotEnoughSeatsAndAllowsRetryWithSameKey() throws Exception {
        UUID small = BookingTestData.flight(jdbc, "AT102", DEPARTURE, 1);

        hold(me, "k-1", holdBody(small, 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("SEATS_UNAVAILABLE"));
        assertThat(available(jdbc, small)).isEqualTo(1);

        // erro não fica guardado: a mesma chave funciona quando o pedido passa a ser possível
        jdbc.update("UPDATE seat_inventory SET available_seats = 2 WHERE flight_id = ?", small);
        hold(me, "k-1", holdBody(small, 2)).andExpect(status().isCreated());
        assertThat(available(jdbc, small)).isZero();
    }

    @Test
    void rejectsUnknownFareAndDepartedFlight() throws Exception {
        hold(me, "k-1", Map.of("flightId", flight, "fareClass", "BUSINESS",
                        "passengers", List.of(Map.of("fullName", "A", "documentNumber", "D1", "birthDate", "1990-01-01"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("FARE_NOT_FOUND"));

        UUID departed = BookingTestData.flight(jdbc, "AT104", NOW.minusSeconds(60), 10);
        hold(me, "k-2", holdBody(departed, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("FLIGHT_DEPARTED"));
        assertThat(available(jdbc, departed)).isEqualTo(10);
    }

    @Test
    void validatesPassengers() throws Exception {
        hold(me, "k-1", holdBody(flight, 0)).andExpect(status().isBadRequest());
        hold(me, "k-2", holdBody(flight, 10)).andExpect(status().isBadRequest());
        hold(me, "k-3", Map.of("flightId", flight, "fareClass", "ECONOMY_LIGHT",
                        "passengers", List.of(Map.of("fullName", "A", "documentNumber", "D1", "birthDate", "2030-01-01"))))
                .andExpect(status().isBadRequest());

        assertThat(available(jdbc, flight)).isEqualTo(10);
    }

    @Test
    void declinedPaymentKeepsTheHold() throws Exception {
        String bookingId = holdId(me, "k-1", holdBody(flight, 1));

        confirm(me, "c-1", bookingId, "tok_decline")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.reason").value("PAYMENT_DECLINED"));
        mvc.perform(get("/bookings/{id}", bookingId).with(user(me))).andExpect(jsonPath("$.status").value("HELD"));

        confirm(me, "c-2", bookingId, "tok_visa").andExpect(status().isOk());
    }

    @Test
    void repeatingConfirmWithSameKeyDoesNotChargeTwice() throws Exception {
        String bookingId = holdId(me, "k-1", holdBody(flight, 1));

        String first = confirm(me, "c-1", bookingId, "tok_visa").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String second = confirm(me, "c-1", bookingId, "tok_visa").andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(second, "$.paymentReference"))
                .isEqualTo(JsonPath.read(first, "$.paymentReference"));
        assertThat(auditActions(bookingId)).containsExactly("BOOKING_HELD", "BOOKING_CONFIRMED");

        // outra chave é outra tentativa: a reserva já está confirmada
        confirm(me, "c-2", bookingId, "tok_visa")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("INVALID_STATE"));
    }

    @Test
    void expiredHoldCannotBeConfirmedAndJobReturnsSeats() throws Exception {
        String bookingId = holdId(me, "k-1", holdBody(flight, 3));
        assertThat(available(jdbc, flight)).isEqualTo(7);

        clock.advance(Duration.ofMinutes(15));
        confirm(me, "c-1", bookingId, "tok_visa")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("HOLD_EXPIRED"));

        assertThat(bookingService.expireDueHolds()).isEqualTo(1);
        assertThat(available(jdbc, flight)).isEqualTo(10);
        mvc.perform(get("/bookings/{id}", bookingId).with(user(me)))
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.expiredAt").value("2026-03-10T12:15:00Z"));
        assertThat(auditActions(bookingId)).containsExactly("BOOKING_HELD", "BOOKING_EXPIRED");

        // rodar de novo não devolve assento em dobro
        assertThat(bookingService.expireDueHolds()).isZero();
        assertThat(available(jdbc, flight)).isEqualTo(10);
    }

    @Test
    void jobLeavesValidHoldsAndConfirmedBookingsAlone() throws Exception {
        String confirmed = holdId(me, "k-1", holdBody(flight, 1));
        confirm(me, "c-1", confirmed, "tok_visa").andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(10));
        holdId(me, "k-2", holdBody(flight, 1));

        clock.advance(Duration.ofMinutes(6)); // o primeiro hold já teria vencido, o segundo não
        assertThat(bookingService.expireDueHolds()).isZero();
        assertThat(available(jdbc, flight)).isEqualTo(8);
    }

    @Test
    void usersOnlySeeTheirOwnBookings() throws Exception {
        UUID other = UUID.randomUUID();
        String mine = holdId(me, "k-1", holdBody(flight, 1));
        clock.advance(Duration.ofSeconds(1));
        String mineLater = holdId(me, "k-2", holdBody(flight, 1));
        holdId(other, "k-1", holdBody(flight, 1));

        mvc.perform(get("/bookings").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", contains(mineLater, mine)));

        mvc.perform(get("/bookings/{id}", mine).with(user(other)))
                .andExpect(status().isNotFound());
        confirm(other, "c-1", mine, "tok_visa").andExpect(status().isNotFound());
    }

    @Test
    void openApiDocumentsTheTypedResponsesOfIdempotentPosts() throws Exception {
        // o cliente da fase 5 é gerado daqui: sem isso os POSTs idempotentes sairiam sem tipo
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/bookings'].post.responses['201'].content.*.schema['$ref']",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.endsWith("/BookingView"))))
                .andExpect(jsonPath("$.paths['/bookings/{id}/confirm'].post.responses['200'].content.*.schema['$ref']",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.endsWith("/BookingView"))))
                .andExpect(jsonPath("$.paths['/pending-actions/{id}/confirm'].post.responses['200'].content.*.schema['$ref']",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.endsWith("/PendingActionView"))))
                .andExpect(jsonPath("$.paths['/bookings'].post.parameters[*].name",
                        org.hamcrest.Matchers.hasItem("Idempotency-Key")));
    }

    @Test
    void releasingAHoldReturnsTheSeatsRightAway() throws Exception {
        String bookingId = holdId(me, "k-1", holdBody(flight, 3));
        assertThat(available(jdbc, flight)).isEqualTo(7);
        clock.advance(Duration.ofMinutes(1));

        release(me, "r-1", bookingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.refund.amount").value(0));
        assertThat(available(jdbc, flight)).isEqualTo(10);
        assertThat(auditActions(bookingId)).containsExactly("BOOKING_HELD", "BOOKING_HOLD_RELEASED");

        // repetir com a mesma chave não devolve assento de novo; outra chave encontra a reserva já cancelada
        release(me, "r-1", bookingId).andExpect(status().isOk());
        release(me, "r-2", bookingId).andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("INVALID_STATE"));
        assertThat(available(jdbc, flight)).isEqualTo(10);
    }

    @Test
    void onlyHeldBookingsOfTheOwnerCanBeReleased() throws Exception {
        String bookingId = holdId(me, "k-1", holdBody(flight, 1));
        release(UUID.randomUUID(), "r-1", bookingId).andExpect(status().isNotFound());

        confirm(me, "c-1", bookingId, "tok_visa").andExpect(status().isOk());
        release(me, "r-2", bookingId).andExpect(status().isConflict());
        assertThat(available(jdbc, flight)).isEqualTo(9);
    }

    @Test
    void oldIdempotencyKeysArePurged() throws Exception {
        hold(me, "old", holdBody(flight, 1)).andExpect(status().isCreated());
        clock.advance(Duration.ofHours(25));
        hold(me, "recent", holdBody(flight, 1)).andExpect(status().isCreated());

        assertThat(idempotencyCleanup.purge()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT idempotency_key FROM idempotency_keys", String.class)).containsExactly("recent");
    }

    @Test
    void bookingsRequireAuthentication() throws Exception {
        mvc.perform(get("/bookings")).andExpect(status().isUnauthorized());
        mvc.perform(post("/bookings").header("Idempotency-Key", "k").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(holdBody(flight, 1))))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions hold(UUID userId, String key, Object body) throws Exception {
        return mvc.perform(post("/bookings").with(user(userId))
                .header("Idempotency-Key", key)
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private String holdId(UUID userId, String key, Object body) throws Exception {
        String response = hold(userId, key, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private ResultActions release(UUID userId, String key, String bookingId) throws Exception {
        return mvc.perform(post("/bookings/{id}/release", bookingId).with(user(userId)).header("Idempotency-Key", key));
    }

    private ResultActions confirm(UUID userId, String key, String bookingId, String cardToken) throws Exception {
        return mvc.perform(post("/bookings/{id}/confirm", bookingId).with(user(userId))
                .header("Idempotency-Key", key)
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("cardToken", cardToken))));
    }

    private List<String> auditActions(String bookingId) {
        return jdbc.queryForList("SELECT action FROM audit_log WHERE entity_type = 'booking' AND entity_id = ? "
                + "ORDER BY occurred_at, action DESC", String.class, bookingId);
    }
}
