package com.uade.crm.clientes.domain.valor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tabla de §5.6 del documento semilla y requisito "Validación del CUIL" de clientes-privacidad. */
class CuilTest {

    @ParameterizedTest
    @CsvSource({
            "20-12345678-6, 20123456786",
            "20123456786, 20123456786",
            "27-30111222-5, 27301112225",
            "23-30000002-9, 23300000029",
            "' 20 12345678 6 ', 20123456786"
    })
    void aceptaYNormaliza(String entrada, String esperado) {
        assertThat(Cuil.de(entrada).digitos()).isEqualTo(esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "20-12345678-5",   // verificador
            "30-71234567-1",   // persona jurídica, verificador correcto
            "20-1234567-6",    // 10 dígitos
            "20-123456789-6",  // 12 dígitos
            "20-1234567A-6"
    })
    void rechazaInvalidos(String entrada) {
        assertThatThrownBy(() -> Cuil.de(entrada))
                .isInstanceOf(CuilInvalidoException.class)
                .hasMessageNotContaining(entrada.replace("-", ""));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rechazaAusente(String entrada) {
        assertThatThrownBy(() -> Cuil.de(entrada)).isInstanceOf(CuilInvalidoException.class);
    }

    @Test
    void toStringEnmascarado() {
        Cuil cuil = Cuil.de("20-12345678-6");
        assertThat(cuil.toString()).isEqualTo("XX-XXXX5678-X").doesNotContain("20123456786");
        assertThat(cuil.ultimos4()).isEqualTo("5678");
    }

    @Test
    void igualdadPorDigitos() {
        assertThat(Cuil.de("20-12345678-6")).isEqualTo(Cuil.de("20123456786"));
    }
}
