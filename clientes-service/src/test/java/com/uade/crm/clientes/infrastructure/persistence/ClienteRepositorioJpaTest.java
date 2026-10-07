package com.uade.crm.clientes.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uade.crm.clientes.PostgresDePrueba;
import com.uade.crm.clientes.domain.ClienteFactory;
import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.SolicitudAlta;
import com.uade.crm.clientes.domain.valor.Email;
import com.uade.crm.clientes.domain.valor.Telefono;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import java.time.Clock;
import java.time.Instant;
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
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private SolicitudAlta solicitud(String email, String telefono) {
        return factory.solicitud("Martina", "López", email, telefono, Provincia.BUENOS_AIRES, null,
                CanalOrigen.INSTAGRAM);
    }
}
