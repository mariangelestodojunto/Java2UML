package com.umlgen.analysis;

/** Uso estructural de un tipo del proyecto dentro de un atributo, con su multiplicidad inferida (o null). */
public record TypeUse(TypeInfo target, String multiplicity) {
}
