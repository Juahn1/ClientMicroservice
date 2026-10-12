package com.uade.crm.clientes.domain.valor;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Forma de búsqueda de nombre y apellido (design D6): sin acentos, en minúsculas y con un solo espacio entre
 * palabras. La {@code ñ} queda como {@code n}, así que "Nunez" encuentra "Núñez". Se calcula en Java y se guarda
 * en una columna para no depender de la extensión {@code unaccent} de PostgreSQL.
 */
public record NombreBusqueda(String valor) {

    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");

    public static NombreBusqueda de(String nombre, String apellido) {
        return new NombreBusqueda(normalizar(nombre + " " + apellido));
    }

    /** Misma transformación que se aplica al texto que busca el usuario ({@code q}). */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinMarcas = MARCAS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD)).replaceAll("");
        return ESPACIOS.matcher(sinMarcas.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }
}
