package com.umlgen.analysis;

import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.umlgen.model.AnalysisReport;
import com.umlgen.parser.ParsedSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Índice de todos los tipos declarados en el proyecto, por nombre calificado y por nombre simple. */
public final class TypeIndex {
    private final Map<String, TypeInfo> byQualifiedName = new LinkedHashMap<>();
    private final Map<String, List<TypeInfo>> bySimpleName = new LinkedHashMap<>();
    private final Map<TypeDeclaration<?>, TypeInfo> byDeclaration = new IdentityHashMap<>();

    public TypeIndex(List<ParsedSource> sources, AnalysisReport report) {
        for (ParsedSource ps : sources) {
            String pkg = ps.unit().getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
            for (TypeDeclaration<?> td : ps.unit().getTypes()) {
                register(td, pkg, pkg.isEmpty() ? "" : pkg + ".", "", ps, report);
            }
        }
    }

    private void register(TypeDeclaration<?> td, String pkg, String fqnPrefix, String displayPrefix,
                          ParsedSource ps, AnalysisReport report) {
        if (td instanceof AnnotationDeclaration) {
            return; // las anotaciones (@interface) no forman parte del diagrama de clases
        }
        String simple = td.getNameAsString();
        String fqn = fqnPrefix + simple;
        String display = displayPrefix + simple;
        TypeInfo info = new TypeInfo(fqn, display, simple, pkg, td, ps);
        if (byQualifiedName.containsKey(fqn)) {
            report.addAmbiguous("El tipo " + fqn + " está declarado más de una vez ("
                    + ps.file().path().getFileName() + "); se usa la primera declaración");
        } else {
            byQualifiedName.put(fqn, info);
            bySimpleName.computeIfAbsent(simple, k -> new ArrayList<>()).add(info);
            byDeclaration.put(td, info);
        }
        for (BodyDeclaration<?> member : td.getMembers()) {
            if (member instanceof TypeDeclaration<?> nested) {
                register(nested, pkg, fqn + ".", display + ".", ps, report);
            }
        }
    }

    public TypeInfo get(String qualifiedName) {
        return byQualifiedName.get(qualifiedName);
    }

    public TypeInfo of(TypeDeclaration<?> declaration) {
        return byDeclaration.get(declaration);
    }

    public List<TypeInfo> withSimpleName(String simpleName) {
        return bySimpleName.getOrDefault(simpleName, List.of());
    }

    public Collection<TypeInfo> all() {
        return byQualifiedName.values();
    }
}
