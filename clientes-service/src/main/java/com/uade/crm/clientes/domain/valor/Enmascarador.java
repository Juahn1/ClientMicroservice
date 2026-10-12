package com.uade.crm.clientes.domain.valor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formas enmascaradas del CUIL y del teléfono (§5.6 del documento semilla).
 */
public final class Enmascarador {

    /** Prefijo (2) + DNI (8) + verificador (1), con o sin guiones. */
    private static final Pattern CUIL = Pattern.compile("(?<![\\w+-])\\d{2}-?(\\d{4})(\\d{4})-?\\d(?![\\w-])");

    /** Secuencia de al menos 8 dígitos con separadores de teléfono, opcionalmente con +. */
    private static final Pattern TELEFONO = Pattern.compile("(?<![\\w+-])\\+?\\d(?:[ \\-().]*\\d){7,}(?![\\w-])");

    private Enmascarador() {
    }

    /** {@code 20-12345678-6} → {@code XX-XXXX5678-X}. */
    public static String cuil(String cuil) {
        String digitos = cuil.replaceAll("\\D", "");
        if (digitos.length() != 11) {
            return "XX-XXXXXXXX-X";
        }
        return cuilDesdeUltimos4(digitos.substring(6, 10));
    }

    /** {@code 5678} → {@code XX-XXXX5678-X}: la ficha enmascara desde los últimos 4 guardados, sin descifrar. */
    public static String cuilDesdeUltimos4(String ultimos4) {
        return "XX-XXXX" + ultimos4 + "-X";
    }

    /** {@code +5491112345678} → {@code +54911****5678}; otros formatos conservan solo los últimos 4 dígitos. */
    public static String telefono(String telefono) {
        if (telefono.matches("\\+\\d{10,15}")) {
            return telefono.substring(0, 6) + "****" + telefono.substring(telefono.length() - 4);
        }
        int digitos = (int) telefono.chars().filter(Character::isDigit).count();
        StringBuilder resultado = new StringBuilder(telefono.length());
        int vistos = 0;
        for (char c : telefono.toCharArray()) {
            if (Character.isDigit(c)) {
                vistos++;
                resultado.append(vistos > digitos - 4 ? c : '*');
            } else {
                resultado.append(c);
            }
        }
        return resultado.toString();
    }

    /** Enmascara todo CUIL y todo número con forma de teléfono que aparezca en un texto libre (por ejemplo, un log). */
    public static String texto(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }
        String sinCuil = reemplazar(CUIL.matcher(texto), m -> cuil(m.group()));
        return reemplazar(TELEFONO.matcher(sinCuil), m -> telefono(m.group()));
    }

    private static String reemplazar(Matcher matcher, java.util.function.Function<Matcher, String> reemplazo) {
        StringBuilder resultado = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(resultado, Matcher.quoteReplacement(reemplazo.apply(matcher)));
        }
        matcher.appendTail(resultado);
        return resultado.toString();
    }
}
