package com.umlgen.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Clase, clase abstracta, interfaz o enumeración del modelo UML. */
public final class UmlClassifier {
    private final String qualifiedName;
    private final String displayName;
    private final String packageName;
    private final ClassifierKind kind;
    private final List<String> typeParameters;
    private final List<UmlAttribute> attributes = new ArrayList<>();
    private final List<UmlOperation> operations = new ArrayList<>();
    private final List<String> enumConstants = new ArrayList<>();
    private final Path sourceFile;
    /** true si pertenece al proyecto pero está fuera del paquete seleccionado. */
    private boolean external;
    /** true si es una clase de servicio (ver ModelBuilder#markServiceClasses). */
    private boolean service;

    public UmlClassifier(String qualifiedName, String displayName, String packageName, ClassifierKind kind,
                         List<String> typeParameters, Path sourceFile) {
        this.qualifiedName = qualifiedName;
        this.displayName = displayName;
        this.packageName = packageName;
        this.kind = kind;
        this.typeParameters = List.copyOf(typeParameters);
        this.sourceFile = sourceFile;
    }

    public String qualifiedName() { return qualifiedName; }
    /** Nombre visible: simple para clases de nivel superior, "Externa.Interna" para anidadas. */
    public String displayName() { return displayName; }
    public String packageName() { return packageName; }
    public ClassifierKind kind() { return kind; }
    public List<String> typeParameters() { return typeParameters; }
    public List<UmlAttribute> attributes() { return attributes; }
    public List<UmlOperation> operations() { return operations; }
    public List<String> enumConstants() { return enumConstants; }
    public Path sourceFile() { return sourceFile; }
    public boolean isExternal() { return external; }
    public boolean isService() { return service; }
    public void setService(boolean service) { this.service = service; }
    public void setExternal(boolean external) { this.external = external; }

    public boolean isInterface() { return kind == ClassifierKind.INTERFACE; }
    public boolean isEnum() { return kind == ClassifierKind.ENUM; }

    @Override
    public String toString() {
        return qualifiedName;
    }
}
