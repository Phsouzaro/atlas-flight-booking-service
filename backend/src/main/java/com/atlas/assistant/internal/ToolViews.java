package com.atlas.assistant.internal;

import java.util.List;
import java.util.UUID;

import com.atlas.booking.BookingView;
import com.atlas.catalog.FareOption;
import com.atlas.catalog.FlightView;
import com.atlas.shared.Money;

/**
 * O que as tools devolvem ao modelo. Datas como texto ISO (o conversor de resultado do Spring AI não
 * garante o formato), dinheiro como texto com moeda e nenhum documento ou data de nascimento de passageiro.
 */
final class ToolViews {

    private ToolViews() {
    }

    record FareInfo(String fareClass, String name, int availableSeats, String pricePerPassenger) {
        static FareInfo from(FareOption fare) {
            return new FareInfo(fare.fareClass(), fare.name(), fare.availableSeats(), money(fare.price()));
        }
    }

    record FlightInfo(UUID flightId, String flightNumber, String origin, String destination, String departureLocal,
                      String arrivalLocal, long durationMinutes, List<FareInfo> fares) {
        static FlightInfo from(FlightView f) {
            return new FlightInfo(f.id(), f.flightNumber(), f.origin().code() + " (" + f.origin().city() + ")",
                    f.destination().code() + " (" + f.destination().city() + ")", f.departureAt().toString(),
                    f.arrivalAt().toString(), f.durationMinutes(), f.fares().stream().map(FareInfo::from).toList());
        }
    }

    /** @param departureLocal partida com o offset do fuso da origem, como o usuário espera ler */
    record BookingInfo(UUID bookingId, String code, String status, String flightNumber, String route,
                       String departureLocal, String fareClass, int passengers, List<String> passengerNames,
                       String total, String holdExpiresAt) {
        static BookingInfo from(BookingView b, String departureLocal) {
            BookingView.Item item = b.items().get(0);
            return new BookingInfo(b.id(), b.code(), b.status().name(), item.flightNumber(),
                    item.origin() + " -> " + item.destination(), departureLocal, item.fareClass(),
                    b.passengers().size(),
                    b.passengers().stream().map(p -> PiiDetector.shortName(p.fullName())).toList(),
                    money(b.total()), b.holdExpiresAt() == null ? null : b.holdExpiresAt().toString());
        }
    }

    record PolicyExcerpt(String source, String section, String text) {
    }

    static String money(Money money) {
        return money == null ? null : money.currency() + " " + money.amount().toPlainString();
    }
}
