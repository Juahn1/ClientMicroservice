package com.uade.crm.contracts.clientes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uade.crm.contracts.EventoIntegracion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventosClientesTest {

    @Test
    void campoClienteTieneLosSieteCamposDelSpec() {
        assertThat(CampoCliente.values()).extracting(Enum::name).containsExactly(
                "NOMBRE", "APELLIDO", "EMAIL", "TELEFONO", "PROVINCIA", "LOCALIDAD", "NO_CONTACTAR");
    }

    @Test
    void clienteActualizadoCopiaLaListaDeCampos() {
        List<CampoCliente> campos = new ArrayList<>(List.of(CampoCliente.NOMBRE));
        ClienteActualizado evento = new ClienteActualizado(UUID.randomUUID(), campos);
        campos.add(CampoCliente.EMAIL);

        assertThat(evento.campos()).containsExactly(CampoCliente.NOMBRE);
    }

    @Test
    void elSobreExigeIdentificadorTipoFechaYPayload() {
        assertThatThrownBy(() -> new EventoIntegracion<>(null, "X", 1, Instant.now(), "c", "p"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EventoIntegracion<>(UUID.randomUUID(), "X", 1, Instant.now(), "c", null))
                .isInstanceOf(NullPointerException.class);
    }
}
