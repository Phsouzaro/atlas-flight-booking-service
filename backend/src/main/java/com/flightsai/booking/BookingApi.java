package com.flightsai.booking;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Reservas do usuário autenticado ({@code identity.CurrentUser}); nunca de outro usuário. */
public interface BookingApi {

    /**
     * Segura os assentos por um tempo limitado (status {@link BookingStatus#HELD}). Sem confirmação
     * dentro do prazo, o hold expira e os assentos voltam ao catálogo.
     *
     * @throws BookingRejectedException tarifa inexistente, voo já decolado ou sem assento suficiente
     */
    BookingView hold(HoldBookingCommand command);

    /** Reservas do usuário, da mais recente para a mais antiga. */
    List<BookingView> myBookings();

    /** Vazio também quando a reserva existe mas é de outro usuário. */
    Optional<BookingView> findMyBooking(UUID bookingId);
}
