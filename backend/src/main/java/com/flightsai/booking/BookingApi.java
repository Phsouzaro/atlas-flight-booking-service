package com.flightsai.booking;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reservas do usuário autenticado ({@code identity.CurrentUser}); nunca de outro usuário.
 * <p>
 * Cancelar e remarcar aqui só cotam e propõem: o resultado é uma {@code PendingAction} que o usuário
 * confirma pela UI (ADR-002). Nada nesta interface cobra, estorna ou cancela.
 */
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

    /**
     * Quanto o usuário recebe de volta se cancelar agora, e uma ação pendente para confirmar.
     *
     * @throws BookingRejectedException reserva que não está confirmada ou voo já decolado
     */
    CancellationQuote quoteCancellation(UUID bookingId);

    /**
     * Quanto custa trocar para outro voo da mesma rota (e, se quiser, outra classe), e uma ação
     * pendente para confirmar. Os passageiros continuam os mesmos.
     *
     * @throws BookingRejectedException reserva não confirmada, rota diferente, voo de destino
     *         inexistente, decolado ou sem assento
     */
    RebookingProposal proposeRebooking(UUID bookingId, UUID newFlightId, String fareClass);
}
