package com.umlgen.uxf;

import com.umlgen.config.DiagramOptions;
import com.umlgen.model.ClassifierKind;
import com.umlgen.model.UmlAttribute;
import com.umlgen.model.UmlClassifier;
import com.umlgen.model.UmlOperation;
import com.umlgen.model.UmlParameter;

import java.util.ArrayList;
import java.util.List;

/**
 * Genera el texto "panel_attributes" de una caja UMLClass. Toda caja tiene tres compartimentos separados por "--" (los enums sin operaciones,
 * sólo dos):
 * <pre>
 * &lt;&lt;interface&gt;&gt; / &lt;&lt;enum&gt;&gt;   (sólo interfaces y enums)
 * Nombre                       (en itálica /Nombre/ si es abstracta)
 * --
 * - atributo: Tipo             (sólo atributos simples; los de clases del dominio son asociaciones)
 * _- CONTADOR: int_            (static: subrayado)
 * _- MAX: int = 100_           (static final con valor literal; los final de instancia con literal se omiten)
 * - dni: String               (final sin valor literal: atributo normal)
 * _+ TRIUNFO_                  (constante de un enum)
 * --
 * + Clase(Tipo, Tipo)          (constructores primero)
 * + metodo(Tipo): Retorno      (sólo tipos en los parámetros, ": void" explícito)
 * </pre>
 */
public final class PanelTextFormatter {
    public static final String SEPARATOR = "--";
    private final DiagramOptions options;

    public PanelTextFormatter(DiagramOptions options) {
        this.options = options;
    }

    public List<String> lines(UmlClassifier c) {
        List<String> lines = new ArrayList<>();
        // 1. nombre
        if (c.kind() == ClassifierKind.INTERFACE) {
            lines.add("<<interface>>");
        } else if (c.kind() == ClassifierKind.ENUM) {
            lines.add("<<enum>>");
        }
        String name = c.displayName();
        if (!c.typeParameters().isEmpty()) {
            name += "<" + String.join(", ", c.typeParameters()) + ">";
        }
        lines.add(c.kind() == ClassifierKind.ABSTRACT_CLASS ? "/" + name + "/" : name);
        if (c.isExternal()) {
            lines.add("{paquete " + (c.packageName().isEmpty() ? "por defecto" : c.packageName()) + "}");
        }

        // 2. atributos (en los enums, primero los valores, subrayados como static)
        lines.add(SEPARATOR);
        if (c.kind() == ClassifierKind.ENUM) {
            for (String constant : c.enumConstants()) {
                lines.add("_+ " + constant + "_");
            }
        }
        if (options.showAttributes) {
            for (UmlAttribute a : c.attributes()) {
                if (!a.isShownAsAssociation() && !a.isInstanceDefaultValue()) {
                    lines.add(formatAttribute(a));
                }
            }
        }

        // 3. constructores y luego métodos
        List<String> operations = new ArrayList<>();
        if (options.showConstructors && !(options.hideServiceConstructors && c.isService())) {
            for (UmlOperation op : c.operations()) {
                if (op.isConstructor()) {
                    operations.add(formatOperation(op));
                }
            }
        }
        if (options.showMethods) {
            for (UmlOperation op : c.operations()) {
                if (!op.isConstructor() && !(options.hideSimpleAccessors && op.isSimpleAccessor())) {
                    operations.add(formatOperation(op));
                }
            }
        }
        // los enums sin operaciones propias no llevan tercer compartimento
        if (c.kind() != ClassifierKind.ENUM || !operations.isEmpty()) {
            lines.add(SEPARATOR);
            lines.addAll(operations);
        }
        return lines;
    }

    public String text(UmlClassifier c) {
        return String.join("\n", lines(c));
    }

    /**
     * Formato "[visibilidad] [nombre]: [Tipo]". Constantes static final con valor literal: "= valor".
     * Los static van subrayados. Los final sin literal se muestran como un atributo normal.
     */
    String formatAttribute(UmlAttribute a) {
        String text = a.visibility().symbol() + " " + a.name() + ": " + a.typeText();
        if (a.isLiteralConstant()) {
            text += " = " + a.literalValue();
        }
        return decorate(text, a.isStatic(), false);
    }

    private String formatOperation(UmlOperation op) {
        StringBuilder sb = new StringBuilder();
        sb.append(op.visibility().symbol()).append(' ').append(op.name()).append('(');
        List<String> params = new ArrayList<>();
        for (UmlParameter p : op.parameters()) {
            params.add(options.showParameterNames ? p.name() + ": " + p.typeText() : p.typeText());
        }
        sb.append(String.join(", ", params)).append(')');
        if (!op.isConstructor()) {
            sb.append(": ").append(op.returnType());
        }
        return decorate(sb.toString(), op.isStatic(), op.isAbstract());
    }

    /**
     * Notación UML en la sintaxis de UMLetino: estáticos subrayados (_texto_) y abstractos en itálica (/texto/).
     * UMLetino sólo interpreta estas marcas cuando encierran la línea completa, por lo que nombres como
     * MAX_VALOR no se ven afectados.
     */
    private static String decorate(String text, boolean isStatic, boolean isAbstract) {
        String result = text;
        if (isAbstract && !result.contains("/")) {
            result = "/" + result + "/";
        }
        if (isStatic) {
            result = "_" + result + "_";
        }
        return result;
    }
}
