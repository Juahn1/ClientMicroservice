package com.uade.crm.clientes.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.EstadoCliente;
import com.uade.crm.clientes.domain.model.SolicitudAlta;
import com.uade.crm.clientes.domain.valor.Email;
import com.uade.crm.clientes.domain.valor.Telefono;
import com.uade.crm.clientes.domain.valor.TelefonoInvalidoException;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClienteFactoryTest {

    private static final Instant AHORA = Instant.parse("2026-10-05T13:00:00Z");

    private final ClienteFactory factory = new ClienteFactory("AR", Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    void creaUnClienteNuevoNormalizado() {
        SolicitudAlta solicitud = factory.solicitud("  Martina ", " López ", " Martina@Gmail.com ", "11 1234-5678",
                Provincia.BUENOS_AIRES, " San Isidro ", CanalOrigen.INSTAGRAM);

        Cliente cliente = factory.crear(solicitud);

        assertThat(cliente.id()).isNotNull();
        assertThat(cliente.nombre()).isEqualTo("Martina");
        assertThat(cliente.apellido()).isEqualTo("López");
        assertThat(cliente.nombreBusqueda().valor()).isEqualTo("martina lopez");
        assertThat(cliente.email()).isEqualTo(Email.de("martina@gmail.com"));
        assertThat(cliente.telefono()).contains(Telefono.deE164("+5491112345678"));
        assertThat(cliente.localidad()).contains("San Isidro");
        assertThat(cliente.canalOrigen()).contains(CanalOrigen.INSTAGRAM);
        assertThat(cliente.ultimoCanal()).contains(CanalOrigen.INSTAGRAM);
        assertThat(cliente.contactosRegistrados()).isEqualTo(1);
        assertThat(cliente.ultimoContactoEn()).isEqualTo(AHORA);
        assertThat(cliente.creadoEn()).isEqualTo(AHORA);
        assertThat(cliente.estado()).isEqualTo(EstadoCliente.ACTIVO);
        assertThat(cliente.version()).isEmpty();
    }

    @Test
    void creaUnClienteSinTelefonoNiCanal() {
        Cliente cliente = factory.crear(factory.solicitud("Martina", "López", "martina@gmail.com", null,
                Provincia.CORDOBA, "", null));

        assertThat(cliente.telefono()).isEmpty();
        assertThat(cliente.localidad()).isEmpty();
        assertThat(cliente.canalOrigen()).isEmpty();
        assertThat(cliente.ultimoCanal()).isEmpty();
    }

    @Test
    void rechazaUnTelefonoInformadoInvalido() {
        assertThatThrownBy(() -> factory.solicitud("Martina", "López", "martina@gmail.com", "1512345678",
                Provincia.CORDOBA, null, null))
                .isInstanceOf(TelefonoInvalidoException.class);
    }

    @Test
    void cadaClienteTieneSuPropioId() {
        SolicitudAlta solicitud = factory.solicitud("Martina", "López", "martina@gmail.com", null,
                Provincia.CORDOBA, null, null);

        assertThat(factory.crear(solicitud).id()).isNotEqualTo(factory.crear(solicitud).id());
    }

    @Test
    void elToStringNoTieneDatosPersonales() {
        Cliente cliente = factory.crear(factory.solicitud("Martina", "López", "martina@gmail.com", "11 1234-5678",
                Provincia.CORDOBA, null, null));

        assertThat(cliente.toString()).doesNotContain("Martina", "martina@gmail.com", "1112345678");
    }
}
