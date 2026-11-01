package com.umlgen.service;

/** Error esperable (proyecto vacío, paquete sin clases, etc.) con un mensaje apto para el usuario. */
public class GenerationException extends Exception {
    public GenerationException(String message) {
        super(message);
    }

    public GenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
