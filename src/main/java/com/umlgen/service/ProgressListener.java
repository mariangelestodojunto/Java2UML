package com.umlgen.service;

/** Recibe avances del proceso (0-100) con un mensaje descriptivo. */
@FunctionalInterface
public interface ProgressListener {
    void progress(int percent, String message);

    ProgressListener NONE = (p, m) -> { };
}
