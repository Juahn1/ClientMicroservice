package com.uade.crm.clientes.domain.valor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EnmascaradorTest {

    @ParameterizedTest
    @CsvSource({
            "20-12345678-6, XX-XXXX5678-X",
            "20123456786, XX-XXXX5678-X",
            "27-30111222-5, XX-XXXX1222-X"
    })
    void cuil(String cuil, String esperado) {
        assertThat(Enmascarador.cuil(cuil)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource({
            "+5491112345678, +54911****5678",
            "11 1234-5678, ** ****-5678",
            "1112345678, ******5678"
    })
    void telefono(String telefono, String esperado) {
        assertThat(Enmascarador.telefono(telefono)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "cuil=20-12345678-6 tel=11 1234-5678 | cuil=XX-XXXX5678-X tel=** ****-5678",
            "{\"cuil\":\"20123456786\",\"telefono\":\"+5491112345678\"} | {\"cuil\":\"XX-XXXX5678-X\",\"telefono\":\"+54911****5678\"}",
            "cliente a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d en 2026-10-05T13:02:44Z | cliente a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d en 2026-10-05T13:02:44Z",
            "puerto 8082, 3 contactos | puerto 8082, 3 contactos"
    })
    void textoLibre(String texto, String esperado) {
        assertThat(Enmascarador.texto(texto)).isEqualTo(esperado);
    }
}
