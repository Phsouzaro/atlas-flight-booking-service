package com.atlas.identity;

import java.util.UUID;

/**
 * Usuário autenticado na requisição atual.
 * <p>
 * Esta é a única forma de outros módulos (inclusive as tools da IA) saberem "quem está
 * pedindo". O id vem do token validado, nunca de um parâmetro que o cliente ou o modelo escolhe.
 */
public interface CurrentUser {

    /** @throws IllegalStateException se não houver usuário autenticado. */
    UUID id();
}
