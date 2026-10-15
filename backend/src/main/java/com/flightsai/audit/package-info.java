/**
 * Módulo de auditoria: registra cada escrita de negócio na tabela {@code audit_log}.
 * <p>
 * API pública (este pacote): {@link com.flightsai.audit.AuditLog}. A gravação acontece na
 * transação de quem chama, então a auditoria e a escrita auditada são confirmadas ou desfeitas juntas.
 */
@ApplicationModule(displayName = "Audit")
package com.flightsai.audit;

import org.springframework.modulith.ApplicationModule;
