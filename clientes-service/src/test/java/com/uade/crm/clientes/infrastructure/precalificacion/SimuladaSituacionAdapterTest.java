package com.uade.crm.clientes.infrastructure.precalificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uade.crm.clientes.domain.model.FuenteSituacion;
import com.uade.crm.clientes.domain.model.SituacionCrediticia;
import com.uade.crm.clientes.domain.port.FuenteNoDisponibleException;
import com.uade.crm.clientes.domain.port.SituacionCrediticiaPort;
import com.uade.crm.contracts.clientes.EstadoSituacionCrediticia;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Requirement: Adaptador simulado determinístico (tabla §5.10 del seed) y contrato de la fuente. */
class SimuladaSituacionAdapterTest extends ContratoFuenteSituacionTest {

    private static final Instant AHORA = Instant.parse("2026-10-10T12:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    @Override
    protected SituacionCrediticiaPort fuente(Duration timeout) {
        return new SimuladaSituacionAdapter(timeout, Duration.ZERO, false, RELOJ);
    }

    @Override
    protected SituacionCrediticiaPort fuenteLenta(Duration timeout) {
        return new SimuladaSituacionAdapter(timeout, timeout.multipliedBy(10), false, RELOJ);
    }

    @ParameterizedTest
    @CsvSource({
            "27-30111222-5, 1",
            "27-38765430-0, 1",
            "20-41000006-8, 2",
            "20-35000007-1, 3",
            "20-12345678-6, 5"})
    void conDatosSegunElUltimoDigitoDelDni(String cuil, int esperada) {
        SituacionCrediticia situacion = fuente(TIMEOUT).consultar(consulta(cuil));

        assertThat(situacion.estado()).isEqualTo(EstadoSituacionCrediticia.CON_DATOS);
        assertThat(situacion.situacion()).isEqualTo(esperada);
        assertThat(situacion.fuente()).isEqualTo(FuenteSituacion.SIMULADA);
        assertThat(situacion.consultadaEn()).isEqualTo(AHORA);
    }

    @Test
    void sinRegistrosConElUltimoDigito9() {
        SituacionCrediticia situacion = fuente(TIMEOUT).consultar(consulta("20-28555449-8"));

        assertThat(situacion.estado()).isEqualTo(EstadoSituacionCrediticia.SIN_REGISTROS);
        assertThat(situacion.situacion()).isNull();
    }

    @Test
    void informaElMesAnteriorComoPeriodo() {
        assertThat(fuente(TIMEOUT).consultar(consulta("27-30111222-5")).periodo()).isEqualTo(YearMonth.of(2026, 9));
    }

    @Test
    void enEneroElPeriodoEsDiciembreDelAnioAnterior() {
        Clock enero = Clock.fixed(Instant.parse("2027-01-15T12:00:00Z"), ZoneOffset.UTC);
        var fuente = new SimuladaSituacionAdapter(TIMEOUT, Duration.ZERO, false, enero);

        assertThat(fuente.consultar(consulta("27-30111222-5")).periodo()).isEqualTo(YearMonth.of(2026, 12));
    }

    @Test
    void conUnaDemoraMenorQueElTimeoutResponde() {
        var fuente = new SimuladaSituacionAdapter(TIMEOUT, Duration.ofMillis(50), false, RELOJ);

        assertThat(fuente.consultar(consulta("27-30111222-5")).esCacheable()).isTrue();
    }

    @Test
    void forzadaNoDisponibleFallaDeInmediato() {
        var fuente = new SimuladaSituacionAdapter(TIMEOUT, Duration.ofSeconds(5), true, RELOJ);
        long inicio = System.nanoTime();

        assertThatThrownBy(() -> fuente.consultar(consulta("27-30111222-5")))
                .isInstanceOf(FuenteNoDisponibleException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofMillis(200));
    }
}
