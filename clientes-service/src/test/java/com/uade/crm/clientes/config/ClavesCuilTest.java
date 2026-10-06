package com.uade.crm.clientes.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class ClavesCuilTest {

    private static final String CLAVE_32 = "c29sby10ZXN0LWNsYXZlLWNpZnJhZG8tY3VpbC0zMmI=";
    private static final String CLAVE_33 = "c29sby10ZXN0LWNsYXZlLWhtYWMtZGVsLWN1aWwtMzJi";

    @Test
    void aceptaClavesValidas() {
        assertThat(new ClientesProperties.Cuil(CLAVE_32, CLAVE_33).claveCifrado()).isEqualTo(CLAVE_32);
    }

    @Test
    void rechazaUnPlaceholderSinResolver() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClientesProperties.Cuil("${CLIENTES_CUIL_CLAVE}", CLAVE_33))
                .withMessageContaining("clientes.cuil.clave-cifrado");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClientesProperties.Cuil(CLAVE_32, "${CLIENTES_CUIL_HMAC}"))
                .withMessageContaining("clientes.cuil.clave-hash");
    }

    @Test
    void laClaveDeCifradoTieneQueSerAes256() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClientesProperties.Cuil(CLAVE_33, CLAVE_33))
                .withMessageContaining("32 bytes");
    }

    @Test
    void elMensajeNoExponeLaClave() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClientesProperties.Cuil("c2hvcnQ=", CLAVE_33))
                .withMessageNotContaining("c2hvcnQ=");
    }
}
