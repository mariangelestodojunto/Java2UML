package com.umlgen.config;

/**
 * Opciones de análisis y presentación del diagrama. Los valores por defecto reproducen el estilo de la cátedra
 * (atributos visibles; métodos y constructores ocultos; dependencias visibles).
 */
public final class DiagramOptions {
    public boolean includeSubpackages = false;
    public boolean showAttributes = true;
    public boolean showMethods = false;
    public boolean showConstructors = false;
    public boolean showParameterNames = false;
    public boolean includeInterfaces = true;
    public boolean includeEnums = true;
    public boolean showDependencies = true;
    public boolean includeExternalClasses = false;
    /** No diagramar clases de ejecución: las llamadas Test, Main o App y las que sólo tienen main(...). */
    public boolean excludeRunnerClasses = true;
    /** Omitir la dependencia A → B si A ya llega a B navegando sus asociaciones (A → X → B). */
    public boolean omitNavigableDependencies = true;
    /** No mostrar los constructores de las clases de servicio (sólo sus operaciones). */
    public boolean hideServiceConstructors = true;
    /** Usar la disposición por capas (jerarquía vertical) en lugar de la cuadrícula 2D. */
    public boolean layeredLayout = false;
    /** Ignorar el código de pruebas (carpetas src/test). */
    public boolean excludeTestSources = true;

    public DiagramOptions copy() {
        DiagramOptions o = new DiagramOptions();
        o.includeSubpackages = includeSubpackages;
        o.showAttributes = showAttributes;
        o.showMethods = showMethods;
        o.showConstructors = showConstructors;
        o.showParameterNames = showParameterNames;
        o.includeInterfaces = includeInterfaces;
        o.includeEnums = includeEnums;
        o.showDependencies = showDependencies;
        o.includeExternalClasses = includeExternalClasses;
        o.excludeTestSources = excludeTestSources;
        o.excludeRunnerClasses = excludeRunnerClasses;
        o.hideServiceConstructors = hideServiceConstructors;
        o.layeredLayout = layeredLayout;
        o.omitNavigableDependencies = omitNavigableDependencies;
        return o;
    }
}
