package com.atlas.pendingaction.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.atlas.identity.CurrentUser;
import com.atlas.pendingaction.PendingActionView;
import com.atlas.shared.idempotency.IdempotentExecutor;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** Chamado pela UI, nunca pela IA (ADR-002). */
@RestController
@RequestMapping("/pending-actions")
class PendingActionController {

    private final PendingActionService actions;
    private final IdempotentExecutor idempotency;
    private final CurrentUser currentUser;

    PendingActionController(PendingActionService actions, IdempotentExecutor idempotency, CurrentUser currentUser) {
        this.actions = actions;
        this.idempotency = idempotency;
        this.currentUser = currentUser;
    }

    @GetMapping
    List<PendingActionView> myActions() {
        return actions.myActions();
    }

    @GetMapping("/{id}")
    PendingActionView action(@PathVariable UUID id) {
        return actions.findMine(id).orElseThrow(() -> new PendingActionNotFoundException(id));
    }

    /**
     * Executa a ação. {@code cardToken} só é exigido quando a ação cobra algo (ex.: diferença de remarcação).
     * O corpo é opcional.
     */
    @ApiResponse(responseCode = "200", description = "Ação executada; o resultado vem em result",
            content = @Content(schema = @Schema(implementation = PendingActionView.class)))
    @PostMapping("/{id}/confirm")
    ResponseEntity<?> confirm(@RequestHeader(IdempotentExecutor.HEADER) String idempotencyKey,
                              @PathVariable UUID id,
                              @Valid @RequestBody(required = false) ConfirmRequest request) {
        String cardToken = request == null ? null : request.cardToken();
        return idempotency.execute(currentUser.id(), idempotencyKey, "pending-action.confirm",
                new ConfirmKey(id, cardToken),
                () -> ResponseEntity.ok(actions.confirm(id, cardToken)));
    }

    @ApiResponse(responseCode = "200", description = "Ação rejeitada",
            content = @Content(schema = @Schema(implementation = PendingActionView.class)))
    @PostMapping("/{id}/reject")
    ResponseEntity<?> reject(@RequestHeader(IdempotentExecutor.HEADER) String idempotencyKey, @PathVariable UUID id) {
        return idempotency.execute(currentUser.id(), idempotencyKey, "pending-action.reject", id,
                () -> ResponseEntity.ok(actions.reject(id)));
    }

    record ConfirmRequest(@Size(max = 100) String cardToken) {
    }

    record ConfirmKey(UUID actionId, String cardToken) {
    }
}
