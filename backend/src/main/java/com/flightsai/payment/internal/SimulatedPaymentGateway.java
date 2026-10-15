package com.flightsai.payment.internal;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.flightsai.payment.PaymentGateway;
import com.flightsai.payment.PaymentRequest;
import com.flightsai.payment.PaymentResult;

/**
 * Pagamento simulado. Aprova qualquer token, menos {@value #DECLINE_TOKEN}, que existe para testar
 * a recusa pela API e pela UI.
 */
@Component
class SimulatedPaymentGateway implements PaymentGateway {

    static final String DECLINE_TOKEN = "tok_decline";

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);

    @Override
    public PaymentResult charge(PaymentRequest request) {
        if (DECLINE_TOKEN.equals(request.cardToken())) {
            log.info("Simulated payment declined for booking {}", request.bookingId());
            return PaymentResult.declined("Card declined (simulated)");
        }
        String reference = "SIM-" + UUID.randomUUID();
        log.info("Simulated payment {} approved for booking {}: {} {}", reference, request.bookingId(),
                request.amount().amount(), request.amount().currency());
        return PaymentResult.approved(reference);
    }
}
