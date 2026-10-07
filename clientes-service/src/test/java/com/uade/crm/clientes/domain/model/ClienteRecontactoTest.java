package com.uade.crm.clientes.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.uade.crm.clientes.domain.valor.Email;
import com.uade.crm.clientes.domain.valor.Telefono;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Requirement: El recontacto no sobrescribe datos existentes. */
class ClienteRecontactoTest {

    private static final Instant ALTA = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant RECONTACTO = Instant.parse("2026-10-05T13:00:00Z");

    @Test
    void recontactoConDatosDistintos() {
        Cliente martina = cliente(Telefono.deE164("+5491112345678"), null, CanalOrigen.INSTAGRAM);

        martina.registrarRecontacto(new SolicitudAlta("Martu", "L", Email.de("martina@gmail.com"),
                Telefono.deE164("+5493514123456"), Provincia.CORDOBA, "Lanús", CanalOrigen.WHATSAPP), RECONTACTO);

        assertThat(martina.nombre()).isEqualTo("Martina");
        assertThat(martina.apellido()).isEqualTo("López");
        assertThat(martina.provincia()).isEqualTo(Provincia.BUENOS_AIRES);
        assertThat(martina.telefono()).contains(Telefono.deE164("+5491112345678"));
        assertThat(martina.localidad()).contains("Lanús");
        assertThat(martina.telefonoAlternativo()).contains(Telefono.deE164("+5493514123456"));
        assertThat(martina.canalOrigen()).contains(CanalOrigen.INSTAGRAM);
        assertThat(martina.ultimoCanal()).contains(CanalOrigen.WHATSAPP);
        assertThat(martina.contactosRegistrados()).isEqualTo(2);
        assertThat(martina.ultimoContactoEn()).isEqualTo(RECONTACTO);
        assertThat(martina.actualizadoEn()).isEqualTo(RECONTACTO);
    }

    @Test
    void recontactoQueCompletaElTelefono() {
        Cliente sinTelefono = cliente(null, null, null);

        sinTelefono.registrarRecontacto(solicitud(Telefono.deE164("+5491112345678"), null), RECONTACTO);

        assertThat(sinTelefono.telefono()).contains(Telefono.deE164("+5491112345678"));
        assertThat(sinTelefono.telefonoAlternativo()).isEmpty();
    }

    @Test
    void noPisaUnTelefonoAlternativoExistente() {
        Cliente cliente = Cliente.builder().id(UUID.randomUUID()).nombre("Martina").apellido("López")
                .email(Email.de("martina@gmail.com")).provincia(Provincia.BUENOS_AIRES)
                .telefono(Telefono.deE164("+5491112345678"))
                .telefonoAlternativo(Telefono.deE164("+5491187654321"))
                .ultimoContactoEn(ALTA).creadoEn(ALTA).actualizadoEn(ALTA).build();

        cliente.registrarRecontacto(solicitud(Telefono.deE164("+5493514123456"), null), RECONTACTO);

        assertThat(cliente.telefonoAlternativo()).contains(Telefono.deE164("+5491187654321"));
    }

    @Test
    void elMismoTelefonoNoSeGuardaComoAlternativo() {
        Cliente cliente = cliente(Telefono.deE164("+5491112345678"), null, null);

        cliente.registrarRecontacto(solicitud(Telefono.deE164("+5491112345678"), null), RECONTACTO);

        assertThat(cliente.telefonoAlternativo()).isEmpty();
    }

    @Test
    void sinCanalConservaElUltimoCanalYCompletaElDeOrigenSoloSiFaltaba() {
        Cliente conCanal = cliente(null, null, CanalOrigen.INSTAGRAM);
        conCanal.registrarRecontacto(solicitud(null, null), RECONTACTO);
        assertThat(conCanal.ultimoCanal()).contains(CanalOrigen.INSTAGRAM);

        Cliente sinCanal = cliente(null, null, null);
        sinCanal.registrarRecontacto(solicitud(null, CanalOrigen.TIKTOK), RECONTACTO);
        assertThat(sinCanal.canalOrigen()).contains(CanalOrigen.TIKTOK);
        assertThat(sinCanal.ultimoCanal()).contains(CanalOrigen.TIKTOK);
    }

    @Test
    void noPisaUnaLocalidadExistente() {
        Cliente cliente = cliente(null, "San Isidro", null);

        cliente.registrarRecontacto(new SolicitudAlta("Martina", "López", Email.de("martina@gmail.com"), null,
                Provincia.BUENOS_AIRES, "Lanús", null), RECONTACTO);

        assertThat(cliente.localidad()).contains("San Isidro");
    }

    @Test
    void unClienteAnonimizadoNoRegistraRecontactos() {
        Cliente anonimizado = Cliente.builder().id(UUID.randomUUID()).nombre("ANONIMIZADO").apellido("ANONIMIZADO")
                .provincia(Provincia.BUENOS_AIRES).estado(EstadoCliente.ANONIMIZADO)
                .ultimoContactoEn(ALTA).creadoEn(ALTA).actualizadoEn(ALTA).anonimizadoEn(ALTA).build();

        assertThatIllegalStateException()
                .isThrownBy(() -> anonimizado.registrarRecontacto(solicitud(null, null), RECONTACTO));
    }

    private static Cliente cliente(Telefono telefono, String localidad, CanalOrigen canal) {
        return Cliente.builder()
                .id(UUID.randomUUID())
                .nombre("Martina").apellido("López")
                .email(Email.de("martina@gmail.com"))
                .telefono(telefono)
                .provincia(Provincia.BUENOS_AIRES)
                .localidad(localidad)
                .canalOrigen(canal).ultimoCanal(canal)
                .ultimoContactoEn(ALTA).creadoEn(ALTA).actualizadoEn(ALTA)
                .build();
    }

    private static SolicitudAlta solicitud(Telefono telefono, CanalOrigen canal) {
        return new SolicitudAlta("Martina", "López", Email.de("martina@gmail.com"), telefono,
                Provincia.BUENOS_AIRES, null, canal);
    }
}
