package com.atlas.pendingaction.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.atlas.audit.AuditEntry;
import com.atlas.audit.AuditLog;
import com.atlas.identity.CurrentUser;
import com.atlas.pendingaction.PendingActionExecution;
import com.atlas.pendingaction.PendingActionHandler;
import com.atlas.pendingaction.PendingActionView;
import com.atlas.pendingaction.PendingActions;
import com.atlas.pendingaction.ProposedAction;

@Service
class PendingActionService implements PendingActions, SmartInitializingSingleton {

    private static final String ENTITY = "pending_action";
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
    };

    private final PendingActionRepository actions;
    private final ObjectProvider<PendingActionHandler> handlerBeans;
    private Map<String, PendingActionHandler> handlers = Map.of();
    private final CurrentUser currentUser;
    private final AuditLog audit;
    private final ObjectMapper json;
    private final PendingActionProperties props;
    private final Clock clock;

    PendingActionService(PendingActionRepository actions, ObjectProvider<PendingActionHandler> handlerBeans, CurrentUser currentUser,
                         AuditLog audit, ObjectMapper json, PendingActionProperties props, Clock clock) {
        this.actions = actions;
        this.handlerBeans = handlerBeans;
        this.currentUser = currentUser;
        this.audit = audit;
        this.json = json;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Os handlers dependem dos serviços dos seus módulos, que dependem deste: pegar a lista no construtor
     * fecharia um ciclo de beans. Por isso o mapa é montado depois que todos os singletons existem.
     * {@code toUnmodifiableMap} falha na subida se dois handlers declararem o mesmo tipo.
     */
    @Override
    public void afterSingletonsInstantiated() {
        this.handlers = handlerBeans.orderedStream()
                .collect(Collectors.toUnmodifiableMap(PendingActionHandler::type, Function.identity()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PendingActionView propose(ProposedAction proposal) {
        if (!handlers.containsKey(proposal.type())) {
            throw new IllegalArgumentException("No handler for pending action type " + proposal.type());
        }
        UUID userId = currentUser.id();
        Instant now = clock.instant();
        PendingAction action = new PendingAction(userId, proposal.type(), proposal.summary(),
                json.convertValue(proposal.payload(), JSON_OBJECT), now, now.plus(props.ttl()));
        actions.save(action);
        audit.record(new AuditEntry(userId, "PENDING_ACTION_PROPOSED", ENTITY, action.getId().toString(),
                Map.of("type", action.getType(), "expiresAt", action.getExpiresAt())));
        return action.toView(now);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingActionView> myActions() {
        Instant now = clock.instant();
        return actions.findByUserIdOrderByCreatedAtDesc(currentUser.id()).stream().map(a -> a.toView(now)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PendingActionView> findMine(UUID actionId) {
        return actions.findByIdAndUserId(actionId, currentUser.id()).map(a -> a.toView(clock.instant()));
    }

    /**
     * Executa a ação. A linha fica travada até o fim, então dois cliques simultâneos executam uma vez.
     * Se o handler recusar (revalidação falhou), a transação é desfeita e a ação continua pendente.
     */
    @Transactional
    PendingActionView confirm(UUID actionId, String cardToken) {
        UUID userId = currentUser.id();
        PendingAction action = lockPending(actionId, userId);
        PendingActionHandler handler = handlers.get(action.getType());
        if (handler == null) {
            throw new IllegalStateException("No handler for pending action type " + action.getType());
        }
        Object result = handler.execute(new PendingActionExecution(action.getId(), userId, action.getPayload(), cardToken));

        Instant now = clock.instant();
        action.confirm(now, json.convertValue(result, JSON_OBJECT));
        audit.record(new AuditEntry(userId, "PENDING_ACTION_CONFIRMED", ENTITY, action.getId().toString(),
                Map.of("type", action.getType())));
        return action.toView(now);
    }

    @Transactional
    PendingActionView reject(UUID actionId) {
        UUID userId = currentUser.id();
        PendingAction action = lockPending(actionId, userId);
        Instant now = clock.instant();
        action.reject(now);
        audit.record(new AuditEntry(userId, "PENDING_ACTION_REJECTED", ENTITY, action.getId().toString(),
                Map.of("type", action.getType())));
        return action.toView(now);
    }

    private PendingAction lockPending(UUID actionId, UUID userId) {
        PendingAction action = actions.lockByIdAndUserId(actionId, userId)
                .orElseThrow(() -> new PendingActionNotFoundException(actionId));
        if (!action.isPending()) {
            throw new PendingActionClosedException("Pending action is already " + action.getStatus());
        }
        if (action.isExpired(clock.instant())) {
            throw new PendingActionClosedException("Pending action expired at " + action.getExpiresAt());
        }
        return action;
    }
}
