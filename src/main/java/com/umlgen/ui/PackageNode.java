package com.umlgen.ui;

/** Nodo del árbol de paquetes. "real" indica que el paquete contiene archivos .java propios. */
final class PackageNode {
    final String fullName;
    final String label;
    boolean real;
    int fileCount;

    PackageNode(String fullName, String label) {
        this.fullName = fullName;
        this.label = label;
    }

    @Override
    public String toString() {
        return real ? label + "  (" + fileCount + (fileCount == 1 ? " archivo)" : " archivos)") : label;
    }
}
