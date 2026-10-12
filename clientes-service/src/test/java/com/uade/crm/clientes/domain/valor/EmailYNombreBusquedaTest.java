package com.uade.crm.clientes.domain.valor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailYNombreBusquedaTest {

    @Test
    void elEmailSeRecortaYPasaAMinusculas() {
        assertThat(Email.de(" Martina@Gmail.com ").valor()).isEqualTo("martina@gmail.com");
        assertThat(Email.de("MARTINA@gmail.com")).isEqualTo(Email.de("martina@GMAIL.COM"));
    }

    @Test
    void noSeQuitanPuntosNiSufijos() {
        assertThat(Email.de("mar.tina+crm@gmail.com").valor()).isEqualTo("mar.tina+crm@gmail.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "  ")
    void elEmailEsObligatorio(String crudo) {
        assertThatIllegalArgumentException().isThrownBy(() -> Email.de(crudo));
    }

    @Test
    void elToStringDelEmailNoExponeLaDireccion() {
        assertThat(Email.de("martina@gmail.com").toString()).isEqualTo("m***@gmail.com");
    }

    @Test
    void elNombreDeBusquedaQuitaAcentosYUneNombreYApellido() {
        assertThat(NombreBusqueda.de("  María José", "PEÑA").valor()).isEqualTo("maria jose pena");
        assertThat(NombreBusqueda.de("Martina", "López").valor()).isEqualTo("martina lopez");
    }

    @Test
    void normalizaElTextoBuscadoIgualQueElNombre() {
        assertThat(NombreBusqueda.normalizar("  Núñez   D'Angelo ")).isEqualTo("nunez d'angelo");
        assertThat(NombreBusqueda.normalizar(null)).isEmpty();
    }
}
