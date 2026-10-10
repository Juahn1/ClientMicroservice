package com.uade.crm.clientes.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.uade.crm.clientes.domain.port.ConsultaSituacion;
import com.uade.crm.clientes.domain.valor.Cuil;
import com.uade.crm.contracts.clientes.EstadoSituacionCrediticia;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Invariantes de la situación crediticia (design D3 del change 05). */
class SituacionCrediticiaTest {

    private static final YearMonth PERIODO = YearMonth.of(2026, 9);
    private static final Instant AHORA = Instant.parse("2026-10-10T12:00:00Z");

    @Test
    void conDatosEsCacheable() {
        var situacion = SituacionCrediticia.conDatos(1, PERIODO, FuenteSituacion.SIMULADA, AHORA);

        assertThat(situacion.estado()).isEqualTo(EstadoSituacionCrediticia.CON_DATOS);
        assertThat(situacion.esCacheable()).isTrue();
    }

    @Test
    void sinRegistrosEsCacheableYNoTieneSituacion() {
        var situacion = SituacionCrediticia.sinRegistros(PERIODO, FuenteSituacion.SIMULADA, AHORA);

        assertThat(situacion.situacion()).isNull();
        assertThat(situacion.esCacheable()).isTrue();
    }

    @Test
    void sinCuilYNoDisponibleNoSonCacheablesNiTienenDatos() {
        for (var situacion : new SituacionCrediticia[] {SituacionCrediticia.sinCuil(),
                SituacionCrediticia.noDisponible()}) {
            assertThat(situacion.esCacheable()).isFalse();
            assertThat(situacion.situacion()).isNull();
            assertThat(situacion.periodo()).isNull();
            assertThat(situacion.fuente()).isNull();
            assertThat(situacion.consultadaEn()).isNull();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7, -1})
    void rechazaUnaSituacionFueraDeRango(int valor) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SituacionCrediticia.conDatos(valor, PERIODO, FuenteSituacion.SIMULADA, AHORA));
    }

    @Test
    void rechazaSituacionConSinRegistros() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SituacionCrediticia(
                EstadoSituacionCrediticia.SIN_REGISTROS, 2, PERIODO, FuenteSituacion.SIMULADA, AHORA));
    }

    @Test
    void rechazaConDatosSinPeriodo() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SituacionCrediticia.conDatos(1, null, FuenteSituacion.SIMULADA, AHORA));
    }

    @Test
    void rechazaNoDisponibleConFuente() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SituacionCrediticia(
                EstadoSituacionCrediticia.NO_DISPONIBLE, null, null, FuenteSituacion.SIMULADA, null));
    }

    @Test
    void laConsultaNoImprimeElCuil() {
        var consulta = new ConsultaSituacion(UUID.randomUUID(), Cuil.de("20-12345678-6"));

        assertThat(consulta.toString()).doesNotContain("20123456786").doesNotContain("5678")
                .contains(consulta.clienteId().toString());
    }
}
