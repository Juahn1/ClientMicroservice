package com.uade.crm.clientes.api;

import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Traducción entre la versión del cliente y el {@code ETag} débil de la ficha ({@code W/"3"}). Para el
 * {@code If-Match} acepta también la forma fuerte ({@code "3"}); cualquier otro valor, incluido {@code *}, no
 * coincide con ninguna versión (design D4 del change 03).
 */
final class VersionEtag {

    private static final Pattern FORMATO = Pattern.compile("(?:W/)?\"(\\d{1,18})\"");

    private VersionEtag() {
    }

    static String de(long version) {
        return "W/\"" + version + "\"";
    }

    /** Vacío si el valor no es un ETag de versión: se responde 412, no 400. */
    static OptionalLong version(String ifMatch) {
        Matcher coincidencia = FORMATO.matcher(ifMatch.strip());
        return coincidencia.matches() ? OptionalLong.of(Long.parseLong(coincidencia.group(1))) : OptionalLong.empty();
    }
}
