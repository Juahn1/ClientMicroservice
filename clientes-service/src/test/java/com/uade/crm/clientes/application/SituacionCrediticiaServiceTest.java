package com.uade.crm.clientes.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uade.crm.clientes.CuilDePrueba;
import com.uade.crm.clientes.domain.ClienteFactory;
import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.ClienteAnonimizadoException;
import com.uade.crm.clientes.domain.model.ClienteNoEncontradoException;
import com.uade.crm.clientes.domain.model.FuenteSituacion;
import com.uade.crm.clientes.domain.model.SituacionCrediticia;
import com.uade.crm.clientes.domain.port.ConsultaSituacion;
import com.uade.crm.clientes.domain.port.SituacionCrediticiaPort;
import com.uade.crm.clientes.domain.valor.Cuil;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import com.uade.crm.contracts.clientes.EstadoSituacionCrediticia;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Ramas del servicio (design D2 del change 05): la fuente solo se consulta para un cliente activo con CUIL. */
class SituacionCrediticiaServiceTest {

    private final ClienteFactory factory = CuilDePrueba.factory(Clock.systemUTC());
    private final ConsultaClienteService clientes = mock(ConsultaClienteService.class);
    private final SituacionCrediticiaPort puerto = mock(SituacionCrediticiaPort.class);
    private final SituacionCrediticiaService servicio =
            new SituacionCrediticiaService(clientes, CuilDePrueba.CIFRADOR, puerto);

    @Test
    void conCuilDescifraYConsultaElPuerto() {
        Cliente cliente = cliente("20-12345678-6");
        when(clientes.obtener(cliente.id())).thenReturn(cliente);
        var esperada = SituacionCrediticia.conDatos(5, YearMonth.of(2026, 9), FuenteSituacion.SIMULADA, Instant.now());
        when(puerto.consultar(any())).thenReturn(esperada);

        assertThat(servicio.consultar(cliente.id())).isEqualTo(esperada);

        ArgumentCaptor<ConsultaSituacion> consulta = ArgumentCaptor.forClass(ConsultaSituacion.class);
        verify(puerto).consultar(consulta.capture());
        assertThat(consulta.getValue().clienteId()).isEqualTo(cliente.id());
        assertThat(consulta.getValue().cuil()).isEqualTo(Cuil.de("20-12345678-6"));
    }

    @Test
    void sinCuilNoConsultaElPuerto() {
        Cliente cliente = cliente(null);
        when(clientes.obtener(cliente.id())).thenReturn(cliente);

        assertThat(servicio.consultar(cliente.id()).estado()).isEqualTo(EstadoSituacionCrediticia.SIN_CUIL);
        verifyNoInteractions(puerto);
    }

    @Test
    void inexistenteNoConsultaElPuerto() {
        UUID id = UUID.randomUUID();
        when(clientes.obtener(id)).thenThrow(new ClienteNoEncontradoException(id));

        assertThatThrownBy(() -> servicio.consultar(id)).isInstanceOf(ClienteNoEncontradoException.class);
        verifyNoInteractions(puerto);
    }

    @Test
    void anonimizadoNoConsultaElPuerto() {
        UUID id = UUID.randomUUID();
        when(clientes.obtener(id)).thenThrow(new ClienteAnonimizadoException(id));

        assertThatThrownBy(() -> servicio.consultar(id)).isInstanceOf(ClienteAnonimizadoException.class);
        verifyNoInteractions(puerto);
    }

    private Cliente cliente(String cuil) {
        return factory.crear(factory.solicitud("Martina", "López", "martina@gmail.com", null, Provincia.BUENOS_AIRES,
                null, CanalOrigen.INSTAGRAM, cuil, cuil != null));
    }
}
