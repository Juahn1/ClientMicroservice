package com.uade.crm.clientes.domain.valor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tabla de §5.6 del documento semilla y requisito "Normalización del teléfono". */
class TelefonoTest {

    private static final String AR = "AR";

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "11 1234-5678        | +5491112345678",
            "011 15 1234-5678    | +5491112345678",
            "+54 9 11 1234-5678  | +5491112345678",
            "0351 15 412-3456    | +5493514123456",
            "351 412-3456        | +5493514123456",
            "+5491112345678      | +5491112345678"
    })
    void normalizaAMovilArgentinoEnE164(String entrada, String esperado) {
        assertThat(Telefono.de(entrada, AR).e164()).isEqualTo(esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1512345678", "123", "no es un teléfono", "+1 202 555 0143", "+34 612 345 678"})
    void rechazaLoQueNoEsUnMovilArgentino(String entrada) {
        assertThatThrownBy(() -> Telefono.de(entrada, AR))
                .isInstanceOf(TelefonoInvalidoException.class)
                .hasMessageNotContaining(entrada);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void ausenteEsOpcionalVacio(String entrada) {
        assertThat(Telefono.opcional(entrada, AR)).isEmpty();
    }

    @Test
    void opcionalInformadoSeNormaliza() {
        assertThat(Telefono.opcional("11 1234-5678", AR)).contains(Telefono.deE164("+5491112345678"));
    }

    @Test
    void elToStringEstaEnmascarado() {
        Telefono telefono = Telefono.de("11 1234-5678", AR);

        assertThat(telefono.toString()).isEqualTo("+54911****5678");
        assertThat(telefono.enmascarado()).isEqualTo("+54911****5678");
    }
}
