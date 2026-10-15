package com.atlas.catalog;

/** Critérios de busca inválidos. A mensagem é segura para mostrar ao cliente. */
public class InvalidFlightSearchException extends RuntimeException {

    public InvalidFlightSearchException(String message) {
        super(message);
    }
}
