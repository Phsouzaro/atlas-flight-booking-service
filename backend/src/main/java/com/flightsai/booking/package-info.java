/**
 * Módulo de reservas: hold de assento, confirmação com pagamento e expiração do hold.
 * <p>
 * API pública (este pacote): {@link com.flightsai.booking.BookingApi}, usada pelo REST e, na fase 4,
 * pelas tools da IA. Confirmar (pagar) não está na API pública de propósito: só o usuário confirma,
 * pela UI (ADR-002). Tudo em {@code booking.internal} é privado.
 */
@ApplicationModule(displayName = "Booking")
package com.flightsai.booking;

import org.springframework.modulith.ApplicationModule;
