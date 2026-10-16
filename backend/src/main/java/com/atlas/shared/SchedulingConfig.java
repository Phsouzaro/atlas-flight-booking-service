package com.atlas.shared;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga o {@code @Scheduled} da aplicação inteira. Fica aqui, e não num módulo, para nenhum job depender de
 * outro estar ligado (antes vinha junto do job de expiração de holds e sumia se ele fosse desligado).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
