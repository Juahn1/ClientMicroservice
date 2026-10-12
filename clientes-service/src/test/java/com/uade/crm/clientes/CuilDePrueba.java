package com.uade.crm.clientes;

import com.uade.crm.clientes.domain.ClienteFactory;
import com.uade.crm.clientes.domain.port.CifradorCuil;
import com.uade.crm.clientes.domain.valor.Cuil;
import com.uade.crm.clientes.domain.valor.CuilProtegido;
import com.uade.crm.clientes.infrastructure.crypto.AesGcmCifradorCuil;
import java.time.Clock;

/** Cifrador y fábrica para tests unitarios, con las mismas claves ficticias de {@code application-test.yml}. */
public final class CuilDePrueba {

    public static final String CLAVE_CIFRADO = "c29sby10ZXN0LWNsYXZlLWNpZnJhZG8tY3VpbC0zMmI=";
    public static final String CLAVE_HASH = "c29sby10ZXN0LWNsYXZlLWhtYWMtZGVsLWN1aWwtMzJi";
    public static final String VERSION_TEXTO = "v1";

    public static final CifradorCuil CIFRADOR = new AesGcmCifradorCuil(CLAVE_CIFRADO, CLAVE_HASH);

    private CuilDePrueba() {
    }

    public static ClienteFactory factory(Clock reloj) {
        return new ClienteFactory("AR", reloj, CIFRADOR, VERSION_TEXTO);
    }

    public static CuilProtegido protegido(String cuil) {
        return CIFRADOR.proteger(Cuil.de(cuil));
    }
}
