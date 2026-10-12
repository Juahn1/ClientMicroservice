package com.uade.crm.clientes.domain.valor;

import java.util.Set;

/**
 * CUIL de persona humana validado y normalizado a 11 dígitos (§5.6 del documento semilla, design D2 del change 04).
 * Existe solo en memoria durante el request: el dominio guarda {@link CuilProtegido}. {@code toString()} lo enmascara.
 */
public final class Cuil {

    private static final Set<String> PREFIJOS_PERSONA_HUMANA = Set.of("20", "23", "24", "27");
    private static final int[] PESOS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private final String digitos;

    private Cuil(String digitos) {
        this.digitos = digitos;
    }

    /** Acepta el CUIL con o sin guiones o espacios; lanza {@link CuilInvalidoException} si no es válido. */
    public static Cuil de(String crudo) {
        if (crudo == null) {
            throw new CuilInvalidoException();
        }
        String digitos = crudo.replaceAll("[\\s-]", "");
        if (!digitos.matches("\\d{11}")
                || !PREFIJOS_PERSONA_HUMANA.contains(digitos.substring(0, 2))
                || verificador(digitos) != digitos.charAt(10) - '0') {
            throw new CuilInvalidoException();
        }
        return new Cuil(digitos);
    }

    /** Módulo 11: 11 − (suma ponderada mod 11); 11 vale 0 y 10 no es un verificador posible. */
    private static int verificador(String digitos) {
        int suma = 0;
        for (int i = 0; i < PESOS.length; i++) {
            suma += (digitos.charAt(i) - '0') * PESOS[i];
        }
        int v = 11 - suma % 11;
        return v == 11 ? 0 : v;
    }

    /** Los 11 dígitos. Solo para el cifrador: nunca se loguea ni se devuelve. */
    public String digitos() {
        return digitos;
    }

    /** Dígitos 7 a 10: los últimos 4 del DNI, que alimentan el enmascarado. */
    public String ultimos4() {
        return digitos.substring(6, 10);
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof Cuil cuil && digitos.equals(cuil.digitos);
    }

    @Override
    public int hashCode() {
        return digitos.hashCode();
    }

    @Override
    public String toString() {
        return Enmascarador.cuilDesdeUltimos4(ultimos4());
    }
}
