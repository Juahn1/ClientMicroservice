package com.uade.crm.clientes.infrastructure.precalificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uade.crm.clientes.domain.model.SituacionCrediticia;
import com.uade.crm.clientes.domain.port.ConsultaSituacion;
import com.uade.crm.clientes.domain.port.FuenteNoDisponibleException;
import com.uade.crm.clientes.domain.port.SituacionCrediticiaPort;
import com.uade.crm.clientes.domain.valor.Cuil;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contrato que toda fuente de situación crediticia tiene que cumplir (design D5 y D10 del change 05): responder con un
 * estado cacheable o fallar con {@link FuenteNoDisponibleException} dentro del timeout. Cada fuente lo verifica
 * extendiendo esta clase: el simulado en el change 05 y el adaptador SOAP en {@code clientes-09}, con un servidor stub
 * con retardo como backend lento.
 */
public abstract class ContratoFuenteSituacionTest {

    protected static final Duration TIMEOUT = Duration.ofMillis(300);

    /** Margen para el arranque del hilo y el reloj del sistema operativo. */
    private static final Duration MARGEN = Duration.ofMillis(400);

    /** La fuente contra un backend que responde normalmente. */
    protected abstract SituacionCrediticiaPort fuente(Duration timeout);

    /** La fuente contra un backend que tarda bastante más que {@code timeout} en responder. */
    protected abstract SituacionCrediticiaPort fuenteLenta(Duration timeout);

    @Test
    void respondeConUnEstadoCacheable() {
        SituacionCrediticia situacion = fuente(TIMEOUT).consultar(consulta("27-30111222-5"));

        assertThat(situacion.esCacheable()).isTrue();
        assertThat(situacion.periodo()).isNotNull();
        assertThat(situacion.fuente()).isNotNull();
        assertThat(situacion.consultadaEn()).isNotNull();
    }

    @Test
    void fallaDentroDelTimeoutConUnBackendLento() {
        SituacionCrediticiaPort lenta = fuenteLenta(TIMEOUT);
        long inicio = System.nanoTime();

        assertThatThrownBy(() -> lenta.consultar(consulta("27-30111222-5")))
                .isInstanceOf(FuenteNoDisponibleException.class)
                .hasMessageNotContaining("27301112225");

        Duration transcurrido = Duration.ofNanos(System.nanoTime() - inicio);
        assertThat(transcurrido).isLessThan(TIMEOUT.plus(MARGEN));
    }

    protected static ConsultaSituacion consulta(String cuil) {
        return new ConsultaSituacion(UUID.randomUUID(), Cuil.de(cuil));
    }
}
