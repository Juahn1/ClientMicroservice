package com.uade.crm.clientes.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uade.crm.clientes.PostgresDePrueba;
import com.uade.crm.clientes.domain.ClienteFactory;
import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.EstadoCliente;
import com.uade.crm.clientes.domain.model.SolicitudAlta;
import com.uade.crm.clientes.domain.port.EmailDuplicadoException;
import com.uade.crm.clientes.domain.valor.Email;
import com.uade.crm.clientes.domain.valor.Telefono;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresDePrueba.class, ClienteRepositorioJpa.class})
@ActiveProfiles("test")
class ClienteRepositorioJpaTest {

    private final ClienteFactory factory = new ClienteFactory("AR", Clock.systemUTC());

    @Autowired
    ClienteRepositorioJpa repositorio;

    @Test
    void guardaYRecuperaUnClienteActivoPorEmailYPorId() {
        Cliente guardado = repositorio.guardar(factory.crear(solicitud("martina@gmail.com", "11 1234-5678")));

        Cliente porEmail = repositorio.buscarActivoPorEmail(Email.de("MARTINA@gmail.com")).orElseThrow();
        assertThat(porEmail.id()).isEqualTo(guardado.id());
        assertThat(porEmail.telefono()).contains(Telefono.deE164("+5491112345678"));
        assertThat(porEmail.nombreBusqueda().valor()).isEqualTo("martina lopez");
        assertThat(porEmail.canalOrigen()).contains(CanalOrigen.INSTAGRAM);
        assertThat(porEmail.version()).contains(0L);
        assertThat(repositorio.buscarActivoPorId(guardado.id())).isPresent();
    }

    @Test
    void unRecontactoIncrementaLaVersion() {
        Cliente guardado = repositorio.guardar(factory.crear(solicitud("martina@gmail.com", null)));

        guardado.registrarRecontacto(solicitud("martina@gmail.com", "11 1234-5678"), Instant.now());
        Cliente actualizado = repositorio.guardar(guardado);

        assertThat(actualizado.version()).contains(1L);
        assertThat(actualizado.contactosRegistrados()).isEqualTo(2);
        assertThat(actualizado.telefono()).contains(Telefono.deE164("+5491112345678"));
    }

    @Test
    void unSegundoClienteActivoConElMismoEmailViolaElIndiceUnico() {
        repositorio.guardar(factory.crear(solicitud("martina@gmail.com", null)));

        assertThatThrownBy(() -> repositorio.guardar(factory.crear(solicitud("martina@gmail.com", null))))
                .isInstanceOf(EmailDuplicadoException.class);
    }

    @Test
    void guardaYRecuperaLaMarcaNoContactar() {
        Cliente guardado = repositorio.guardar(factory.crear(solicitud("martina@gmail.com", null)));
        Instant ahora = Instant.parse("2026-10-08T12:00:00Z");

        guardado.marcarNoContactar(ahora);
        Cliente marcado = repositorio.guardar(guardado);

        assertThat(marcado.noContactar()).isTrue();
        assertThat(marcado.noContactarDesde()).contains(ahora);
        assertThat(marcado.version()).contains(1L);
    }

    @Test
    void laBaseRechazaUnAnonimizadoConDatosPersonales() {
        Instant ahora = Instant.now();
        Cliente conEmail = Cliente.builder().id(UUID.randomUUID()).nombre(Cliente.ANONIMIZADO)
                .apellido(Cliente.ANONIMIZADO).email(Email.de("martina@gmail.com")).provincia(Provincia.CABA)
                .estado(EstadoCliente.ANONIMIZADO).ultimoContactoEn(ahora).creadoEn(ahora).actualizadoEn(ahora)
                .anonimizadoEn(ahora).build();

        assertThatThrownBy(() -> repositorio.guardar(conEmail))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_cliente_anonimizado_sin_datos");
    }

    @Test
    void buscarPorIdDevuelveClientesAnonimizados() {
        Cliente guardado = repositorio.guardar(factory.crear(solicitud("martina@gmail.com", "11 1234-5678")));
        guardado.anonimizar(Instant.now());
        repositorio.guardar(guardado);

        assertThat(repositorio.buscarActivoPorId(guardado.id())).isEmpty();
        assertThat(repositorio.buscarPorId(guardado.id())).get()
                .extracting(Cliente::estado).isEqualTo(EstadoCliente.ANONIMIZADO);
        assertThat(repositorio.buscarPorIdParaActualizar(guardado.id())).get()
                .satisfies(c -> assertThat(c.email()).isNull());
    }

    @Test
    void soloLaViolacionDelIndiceDeEmailSeTraduce() {
        assertThat(ClienteRepositorioJpa.violaIndiceEmail(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"ux_cliente_email_activo\""))).isTrue();
        assertThat(ClienteRepositorioJpa.violaIndiceEmail(new DataIntegrityViolationException(
                "new row violates check constraint \"ck_cliente_anonimizado_sin_datos\""))).isFalse();
    }

    private SolicitudAlta solicitud(String email, String telefono) {
        return factory.solicitud("Martina", "López", email, telefono, Provincia.BUENOS_AIRES, null,
                CanalOrigen.INSTAGRAM);
    }
}
