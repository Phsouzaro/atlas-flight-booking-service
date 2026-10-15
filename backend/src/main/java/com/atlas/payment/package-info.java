/**
 * Módulo de pagamento. O projeto só simula pagamento: a implementação em {@code payment.internal}
 * aprova ou recusa sem falar com ninguém de fora.
 * <p>
 * API pública (este pacote): {@link com.atlas.payment.PaymentGateway}. Trocar por um provedor
 * real é escrever outra implementação dessa interface.
 */
@ApplicationModule(displayName = "Payment")
package com.atlas.payment;

import org.springframework.modulith.ApplicationModule;
