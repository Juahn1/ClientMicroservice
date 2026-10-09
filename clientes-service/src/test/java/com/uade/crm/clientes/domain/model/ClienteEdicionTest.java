package com.uade.crm.clientes.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.uade.crm.clientes.domain.ClienteFactory;
import com.uade.crm.clientes.domain.valor.Email;
import com.uade.crm.clientes.domain.valor.Telefono;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import com.uade.crm.contracts.clientes.CampoCliente;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Requirement: Edición con control de concurrencia (reglas del dominio, design D2 del change 03). */
class ClienteEdicionTest {

    private static final Instant ALTA = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant EDICION = Instant.parse("2026-10-08T12:00:00Z");

    private final ClienteFactory factory = new ClienteFactory("AR", Clock.systemUTC());

    @Test
    void cambiaElApellidoYRecalculaElNombreDeBusqueda() {
        Cliente martina = martina();

        var campos = martina.editar(factory.cambios(null, "Gómez", null, null, null, null), EDICION);

        assertThat(campos).containsExactly(CampoCliente.APELLIDO);
        assertThat(martina.apellido()).isEqualTo("Gómez");
        assertThat(martina.nombre()).isEqualTo("Martina");
        assertThat(martina.nombreBusqueda().valor()).isEqualTo("martina gomez");
        assertThat(martina.actualizadoEn()).isEqualTo(EDICION);
    }

    @Test
    void unNullOUnBlancoNoModifica() {
        Cliente martina = martina();

        var campos = martina.editar(factory.cambios(null, "  ", "", " ", null, ""), EDICION);

        assertThat(campos).isEmpty();
        assertThat(martina.apellido()).isEqualTo("López");
        assertThat(martina.email()).isEqualTo(Email.de("martina@gmail.com"));
        assertThat(martina.telefono()).contains(Telefono.deE164("+5491112345678"));
        assertThat(martina.localidad()).contains("San Isidro");
        assertThat(martina.actualizadoEn()).isEqualTo(ALTA);
    }

    @Test
    void elMismoEmailOTelefonoEnOtroFormatoNoEsUnCambio() {
        Cliente martina = martina();

        var campos = martina.editar(factory.cambios("Martina", "López", " MARTINA@gmail.com ", "11 1234-5678",
                Provincia.BUENOS_AIRES, "San Isidro"), EDICION);

        assertThat(campos).isEmpty();
        assertThat(martina.actualizadoEn()).isEqualTo(ALTA);
    }

    @Test
    void informaTodosLosCamposModificadosEnElOrdenDelEnum() {
        Cliente martina = martina();

        var campos = martina.editar(factory.cambios("Martu", "Gómez", "martu@gmail.com", "0351 15 412-3456",
                Provincia.CORDOBA, "Villa Carlos Paz"), EDICION);

        assertThat(campos).containsExactly(CampoCliente.NOMBRE, CampoCliente.APELLIDO, CampoCliente.EMAIL,
                CampoCliente.TELEFONO, CampoCliente.PROVINCIA, CampoCliente.LOCALIDAD);
        assertThat(martina.email()).isEqualTo(Email.de("martu@gmail.com"));
        assertThat(martina.telefono()).contains(Telefono.deE164("+5493514123456"));
        assertThat(martina.provincia()).isEqualTo(Provincia.CORDOBA);
    }

    @Test
    void noSePuedeEditarUnClienteAnonimizado() {
        Cliente martina = martina();
        martina.anonimizar(EDICION);

        assertThatIllegalStateException().isThrownBy(
                () -> martina.editar(factory.cambios("Martu", null, null, null, null, null), EDICION));
    }

    static Cliente martina() {
        return Cliente.builder()
                .id(UUID.randomUUID())
                .nombre("Martina").apellido("López")
                .email(Email.de("martina@gmail.com"))
                .telefono(Telefono.deE164("+5491112345678"))
                .telefonoAlternativo(Telefono.deE164("+5491187654321"))
                .provincia(Provincia.BUENOS_AIRES)
                .localidad("San Isidro")
                .canalOrigen(CanalOrigen.INSTAGRAM).ultimoCanal(CanalOrigen.WHATSAPP)
                .contactosRegistrados(3)
                .version(5L)
                .ultimoContactoEn(ALTA).creadoEn(ALTA).actualizadoEn(ALTA)
                .build();
    }
}
