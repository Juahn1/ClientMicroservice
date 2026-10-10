package com.uade.crm.clientes.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uade.crm.clientes.CuilDePrueba;
import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.ConsentimientoRequeridoException;
import com.uade.crm.clientes.domain.model.DatoInvalidoException;
import com.uade.crm.clientes.domain.model.EstadoCliente;
import com.uade.crm.clientes.domain.model.SolicitudAlta;
import com.uade.crm.clientes.domain.valor.CuilInvalidoException;
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

    private final ClienteFactory factory = CuilDePrueba.factory(Clock.fixed(AHORA, ZoneOffset.UTC));

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
    void sinCuilNiConsentimientoEsUnAltaNormal() {
        SolicitudAlta solicitud = conCuil(null, null);

        assertThat(solicitud.cuilInformado()).isEmpty();
        assertThat(factory.crear(solicitud).cuil()).isEmpty();
        assertThat(conCuil("  ", false).cuilInformado()).isEmpty();
    }

    @Test
    void consentimientoSinCuilEsInvalido() {
        assertThatThrownBy(() -> conCuil(null, true))
                .isInstanceOfSatisfying(DatoInvalidoException.class,
                        e -> assertThat(e.campo()).isEqualTo("consentimiento"));
    }

    @Test
    void elCuilInvalidoSeReportaAntesQueLaFaltaDeConsentimiento() {
        assertThatThrownBy(() -> conCuil("20-12345678-5", null)).isInstanceOf(CuilInvalidoException.class);
        assertThatThrownBy(() -> conCuil("20-12345678-5", true)).isInstanceOf(CuilInvalidoException.class);
    }

    @Test
    void cuilSinConsentimientoLoRequiere() {
        assertThatThrownBy(() -> conCuil("20-12345678-6", null))
                .isInstanceOf(ConsentimientoRequeridoException.class);
        assertThatThrownBy(() -> conCuil("20-12345678-6", false))
                .isInstanceOf(ConsentimientoRequeridoException.class);
    }

    @Test
    void cuilConConsentimientoCreaElClienteConAmbos() {
        SolicitudAlta solicitud = conCuil("20-12345678-6", true);

        Cliente cliente = factory.crear(solicitud);

        assertThat(cliente.cuil()).get().satisfies(c -> {
            assertThat(c.enmascarado()).isEqualTo("XX-XXXX5678-X");
            assertThat(c.mismoCuil(CuilDePrueba.protegido("20123456786"))).isTrue();
        });
        assertThat(cliente.consentimientosVigentes()).singleElement().satisfies(c -> {
            assertThat(c.versionTexto()).isEqualTo(CuilDePrueba.VERSION_TEXTO);
            assertThat(c.otorgadoEn()).isEqualTo(AHORA);
        });
        assertThat(solicitud.toString()).doesNotContain("20123456786", "Martina", "martina@gmail.com");
    }

    @Test
    void elToStringNoTieneDatosPersonales() {
        Cliente cliente = factory.crear(factory.solicitud("Martina", "López", "martina@gmail.com", "11 1234-5678",
                Provincia.CORDOBA, null, null));

        assertThat(cliente.toString()).doesNotContain("Martina", "martina@gmail.com", "1112345678");
    }

    private SolicitudAlta conCuil(String cuil, Boolean consentimiento) {
        return factory.solicitud("Martina", "López", "martina@gmail.com", null, Provincia.CORDOBA, null, null, cuil,
                consentimiento);
    }
}
