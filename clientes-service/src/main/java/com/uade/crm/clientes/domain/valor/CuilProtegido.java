package com.uade.crm.clientes.domain.valor;

import java.util.Arrays;
import java.util.Objects;

/**
 * Forma en que el dominio guarda el CUIL (§5.8 del documento semilla, design D2 del change 04): cifrado
 * (AES-GCM, IV antepuesto), índice ciego (HMAC-SHA256 en hex, para unicidad y búsqueda exacta) y los últimos 4
 * dígitos del DNI (para enmascarar sin descifrar). Dos CUIL son el mismo si tienen el mismo {@code hash}.
 */
public final class CuilProtegido {

    private final byte[] cifrado;
    private final String hash;
    private final String ultimos4;

    public CuilProtegido(byte[] cifrado, String hash, String ultimos4) {
        this.cifrado = Objects.requireNonNull(cifrado, "cifrado").clone();
        this.hash = Objects.requireNonNull(hash, "hash");
        this.ultimos4 = Objects.requireNonNull(ultimos4, "ultimos4");
    }

    public byte[] cifrado() {
        return cifrado.clone();
    }

    public String hash() {
        return hash;
    }

    public String ultimos4() {
        return ultimos4;
    }

    public String enmascarado() {
        return Enmascarador.cuilDesdeUltimos4(ultimos4);
    }

    public boolean mismoCuil(CuilProtegido otro) {
        return otro != null && hash.equals(otro.hash);
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof CuilProtegido c && hash.equals(c.hash) && ultimos4.equals(c.ultimos4)
                && Arrays.equals(cifrado, c.cifrado);
    }

    @Override
    public int hashCode() {
        return hash.hashCode();
    }

    /** Sin hash ni cifrado: con la clave filtrada, el hash de un espacio de 11 dígitos se revierte por fuerza bruta. */
    @Override
    public String toString() {
        return enmascarado();
    }
}
