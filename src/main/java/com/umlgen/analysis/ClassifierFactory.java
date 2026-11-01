package com.umlgen.analysis;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.type.TypeParameter;
import com.umlgen.model.ClassifierKind;
import com.umlgen.model.UmlAttribute;
import com.umlgen.model.UmlClassifier;
import com.umlgen.model.UmlOperation;
import com.umlgen.model.UmlParameter;
import com.umlgen.model.Visibility;

import java.util.ArrayList;
import java.util.List;

/** Construye un {@link UmlClassifier} (nombre, tipo, atributos, métodos, constantes) desde su declaración. */
public final class ClassifierFactory {

    public UmlClassifier create(TypeInfo info) {
        TypeDeclaration<?> td = info.declaration();
        ClassifierKind kind = kindOf(td);
        List<String> typeParams = new ArrayList<>();
        if (td instanceof ClassOrInterfaceDeclaration cid) {
            for (TypeParameter tp : cid.getTypeParameters()) {
                typeParams.add(tp.toString());
            }
        }
        UmlClassifier c = new UmlClassifier(info.qualifiedName(), info.displayName(), info.packageName(), kind,
                typeParams, info.source().file().path());

        boolean inInterface = kind == ClassifierKind.INTERFACE;
        boolean inEnum = kind == ClassifierKind.ENUM;

        if (td instanceof EnumDeclaration ed) {
            for (EnumConstantDeclaration ec : ed.getEntries()) {
                c.enumConstants().add(ec.getNameAsString());
            }
        }
        addRecordComponents(td, c);

        for (BodyDeclaration<?> member : td.getMembers()) {
            if (member instanceof FieldDeclaration fd) {
                Visibility v = visibility(fd.getModifiers(), inInterface, false);
                boolean isStatic = fd.isStatic() || inInterface;
                boolean isFinal = fd.isFinal() || inInterface;
                for (VariableDeclarator var : fd.getVariables()) {
                    c.attributes().add(new UmlAttribute(v, var.getNameAsString(), var.getType().asString(),
                            isStatic, isFinal, literalOf(var)));
                }
            } else if (member instanceof MethodDeclaration md) {
                Visibility v = visibility(md.getModifiers(), inInterface, false);
                boolean isAbstract = md.isAbstract()
                        || (inInterface && md.getBody().isEmpty() && !md.isStatic() && !md.isDefault()
                        && !md.isPrivate());
                c.operations().add(new UmlOperation(v, md.getNameAsString(), parameters(md.getParameters()),
                        md.getType().asString(), false, md.isStatic(), isAbstract, isSimpleAccessor(md, fieldNames(td))));
            } else if (member instanceof ConstructorDeclaration cd) {
                Visibility v = visibility(cd.getModifiers(), false, inEnum);
                c.operations().add(new UmlOperation(v, cd.getNameAsString(), parameters(cd.getParameters()),
                        null, true, false, false));
            }
        }
        return c;
    }

    /**
     * Texto del valor si la variable se inicializa con un literal de una sola línea
     * (número, texto, carácter, boolean, o un número negativo como -1). En otro caso null.
     */
    static String literalOf(VariableDeclarator var) {
        return var.getInitializer().map(ClassifierFactory::literalText).orElse(null);
    }

    private static String literalText(Expression e) {
        if (e instanceof UnaryExpr u && (u.getOperator() == UnaryExpr.Operator.MINUS
                || u.getOperator() == UnaryExpr.Operator.PLUS)) {
            String inner = literalText(u.getExpression());
            return inner == null ? null : u.getOperator().asString() + inner;
        }
        if (e instanceof LiteralExpr && !(e instanceof NullLiteralExpr) && !e.getClass().getSimpleName().equals("TextBlockLiteralExpr")) {
            String text = e.toString();
            return text.contains("\n") || text.length() > 60 ? null : text;
        }
        return null;
    }

    /**
     * Getter o setter simple, de los que no hace falta diagramar:
     * <ul>
     *   <li>getX()/isX() sin parámetros cuyo cuerpo es sólo {@code return atributo;} (o {@code return this.atributo;})</li>
     *   <li>setX(valor) con un parámetro cuyo cuerpo es sólo {@code atributo = valor;}</li>
     * </ul>
     * Un setter con validaciones (p. ej. un if) o un getter que calcula algo no se considera simple.
     */
    static boolean isSimpleAccessor(MethodDeclaration md, java.util.Set<String> fields) {
        String name = md.getNameAsString();
        java.util.Optional<com.github.javaparser.ast.stmt.BlockStmt> body = md.getBody();
        if (body.isEmpty() || body.get().getStatements().size() != 1) {
            return false;
        }
        com.github.javaparser.ast.stmt.Statement only = body.get().getStatement(0);
        boolean getterName = (name.length() > 3 && name.startsWith("get") && Character.isUpperCase(name.charAt(3)))
                || (name.length() > 2 && name.startsWith("is") && Character.isUpperCase(name.charAt(2)));
        if (getterName && md.getParameters().isEmpty() && only instanceof com.github.javaparser.ast.stmt.ReturnStmt ret) {
            return ret.getExpression().map(e -> fieldReferenced(e, fields)).orElse(false);
        }
        boolean setterName = name.length() > 3 && name.startsWith("set") && Character.isUpperCase(name.charAt(3));
        if (setterName && md.getParameters().size() == 1
                && only instanceof com.github.javaparser.ast.stmt.ExpressionStmt es
                && es.getExpression() instanceof com.github.javaparser.ast.expr.AssignExpr assign
                && assign.getOperator() == com.github.javaparser.ast.expr.AssignExpr.Operator.ASSIGN) {
            String param = md.getParameter(0).getNameAsString();
            return fieldReferenced(assign.getTarget(), fields)
                    && assign.getValue() instanceof com.github.javaparser.ast.expr.NameExpr value
                    && value.getNameAsString().equals(param);
        }
        return false;
    }

    private static boolean fieldReferenced(Expression e, java.util.Set<String> fields) {
        if (e instanceof com.github.javaparser.ast.expr.NameExpr n) {
            return fields.contains(n.getNameAsString());
        }
        return e instanceof com.github.javaparser.ast.expr.FieldAccessExpr f
                && f.getScope() instanceof com.github.javaparser.ast.expr.ThisExpr
                && fields.contains(f.getNameAsString());
    }

    private static java.util.Set<String> fieldNames(TypeDeclaration<?> td) {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (BodyDeclaration<?> m : td.getMembers()) {
            if (m instanceof FieldDeclaration fd) {
                fd.getVariables().forEach(v -> names.add(v.getNameAsString()));
            }
        }
        if (td.getClass().getSimpleName().equals("RecordDeclaration")) {
            td.findAll(Parameter.class).forEach(p -> {
                if (p.getParentNode().filter(n -> n == td).isPresent()) {
                    names.add(p.getNameAsString());
                }
            });
        }
        return names;
    }

    static ClassifierKind kindOf(TypeDeclaration<?> td) {
        if (td instanceof ClassOrInterfaceDeclaration cid) {
            if (cid.isInterface()) {
                return ClassifierKind.INTERFACE;
            }
            return cid.isAbstract() ? ClassifierKind.ABSTRACT_CLASS : ClassifierKind.CLASS;
        }
        if (td instanceof EnumDeclaration) {
            return ClassifierKind.ENUM;
        }
        return ClassifierKind.CLASS; // records u otros tipos de versiones nuevas de Java
    }

    private static List<UmlParameter> parameters(NodeList<Parameter> params) {
        List<UmlParameter> result = new ArrayList<>();
        for (Parameter p : params) {
            result.add(new UmlParameter(p.getNameAsString(), p.getType().asString() + (p.isVarArgs() ? "..." : "")));
        }
        return result;
    }

    /**
     * Modificador de acceso de un miembro. En interfaces lo implícito es public;
     * los constructores de enum son implícitamente private.
     */
    static Visibility visibility(NodeList<Modifier> modifiers, boolean inInterface, boolean enumConstructor) {
        for (Modifier m : modifiers) {
            switch (m.getKeyword()) {
                case PUBLIC: return Visibility.PUBLIC;
                case PRIVATE: return Visibility.PRIVATE;
                case PROTECTED: return Visibility.PROTECTED;
                default: break;
            }
        }
        if (inInterface) {
            return Visibility.PUBLIC;
        }
        return enumConstructor ? Visibility.PRIVATE : Visibility.PACKAGE;
    }

    /**
     * Componentes de un record (sólo con versiones de JavaParser que soportan records).
     * Se accede por reflexión para no depender de una versión específica de la biblioteca.
     */
    @SuppressWarnings("unchecked")
    private static void addRecordComponents(TypeDeclaration<?> td, UmlClassifier c) {
        if (!td.getClass().getSimpleName().equals("RecordDeclaration")) {
            return;
        }
        try {
            Object params = td.getClass().getMethod("getParameters").invoke(td);
            List<UmlParameter> canonical = new ArrayList<>();
            for (Parameter p : (NodeList<Parameter>) params) {
                c.attributes().add(new UmlAttribute(Visibility.PRIVATE, p.getNameAsString(), p.getType().asString(),
                        false, true));
                canonical.add(new UmlParameter(p.getNameAsString(), p.getType().asString()));
            }
            // constructor canónico implícito del record (los explícitos se agregan después si existen)
            boolean explicitCanonical = td.getMembers().stream().anyMatch(m -> m instanceof ConstructorDeclaration cd
                    && cd.getParameters().size() == canonical.size());
            if (!explicitCanonical) {
                c.operations().add(new UmlOperation(Visibility.PUBLIC, td.getNameAsString(), canonical, null,
                        true, false, false));
            }
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            // versión sin soporte de records: se muestran sólo los miembros declarados
        }
    }
}
