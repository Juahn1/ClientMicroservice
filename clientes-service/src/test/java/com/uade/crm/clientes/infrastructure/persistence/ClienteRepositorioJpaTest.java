package com.uade.crm.clientes.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uade.crm.clientes.CuilDePrueba;
import com.uade.crm.clientes.PostgresDePrueba;
import com.uade.crm.clientes.domain.ClienteFactory;
import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.EstadoCliente;
import com.uade.crm.clientes.domain.model.FinalidadConsentimiento;
import com.uade.crm.clientes.domain.model.MotivoRevision;
import com.uade.crm.clientes.domain.model.SolicitudAlta;
import com.uade.crm.clientes.domain.port.ClaveDuplicadaException;
import com.uade.crm.clientes.domain.port.ClaveDuplicadaException.Clave;
import com.uade.crm.clientes.domain.valor.Email;
import com.uade.crm.clientes.domain.valor.Telefono;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import jakarta.persistence.EntityManager;
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

    private final ClienteFactory factory = CuilDePrueba.factory(Clock.systemUTC());

    @Autowired
    ClienteRepositorioJpa repositorio;

    @Autowired
    EntityManager entityManager;

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
                .isInstanceOf(ClaveDuplicadaException.class)
                .extracting(e -> ((ClaveDuplicadaException) e).clave()).isEqualTo(Clave.EMAIL);
    }

    @Test
    void unSegundoClienteActivoConElMismoCuilViolaElIndiceUnico() {
        repositorio.guardar(factory.crear(solicitudConCuil("martina@gmail.com", "20-12345678-6")));

        assertThatThrownBy(() -> repositorio.guardar(factory.crear(solicitudConCuil("otra@gmail.com", "20123456786"))))
                .isInstanceOf(ClaveDuplicadaException.class)
                .extracting(e -> ((ClaveDuplicadaException) e).clave()).isEqualTo(Clave.CUIL);
    }

    @Test
    void guardaYRecuperaElCuilYElConsentimiento() {
        Cliente guardado = repositorio.guardar(factory.crear(solicitudConCuil("martina@gmail.com", "20-12345678-6")));
        String hash = CuilDePrueba.protegido("20-12345678-6").hash();

        Cliente porCuil = repositorio.buscarActivoPorCuilParaActualizar(hash).orElseThrow();
        assertThat(porCuil.id()).isEqualTo(guardado.id());
        assertThat(porCuil.cuil()).get().satisfies(c -> {
            assertThat(c.enmascarado()).isEqualTo("XX-XXXX5678-X");
            assertThat(c.hash()).isEqualTo(hash);
        });
        assertThat(porCuil.consentimientosVigentes()).singleElement().satisfies(c -> {
            assertThat(c.finalidad()).isEqualTo(FinalidadConsentimiento.PRECALIFICACION_CREDITICIA);
            assertThat(c.versionTexto()).isEqualTo("v1");
        });
        assertThat(repositorio.buscarActivoPorCuilParaActualizar(CuilDePrueba.protegido("27-30111222-5").hash()))
                .isEmpty();
    }

    @Test
    void guardarDosVecesNoDuplicaElConsentimientoYAgregarloIncrementaLaVersion() {
        Cliente guardado = repositorio.guardar(factory.crear(solicitud("martina@gmail.com", null)));
        assertThat(guardado.version()).contains(0L);

        guardado.registrarCuil(CuilDePrueba.protegido("20-12345678-6"), "v1", Instant.now());
        Cliente conCuil = repositorio.guardar(guardado);
        assertThat(conCuil.version()).contains(1L);

        conCuil.registrarRecontacto(solicitud("martina@gmail.com", null), Instant.now());
        repositorio.guardar(conCuil);
        entityManager.flush();
        entityManager.clear();

        assertThat(contar("select count(*) from consentimiento where cliente_id = ?1", guardado.id())).isEqualTo(1);
        assertThat(repositorio.buscarPorId(guardado.id()).orElseThrow().consentimientos()).hasSize(1);
    }

    @Test
    void anonimizarBorraElCuilYRevocaLosConsentimientos() {
        Cliente guardado = repositorio.guardar(factory.crear(solicitudConCuil("martina@gmail.com", "20-12345678-6")));
        guardado.anonimizar(Instant.now());
        repositorio.guardar(guardado);
        entityManager.flush();
        entityManager.clear();

        Cliente anonimizado = repositorio.buscarPorId(guardado.id()).orElseThrow();
        assertThat(anonimizado.cuil()).isEmpty();
        assertThat(anonimizado.consentimientos()).singleElement()
                .satisfies(c -> assertThat(c.revocadoEn()).isPresent());
        assertThat(contar("select count(*) from cliente where id = ?1 and cuil_hash is null", guardado.id()))
                .isEqualTo(1);
    }

    @Test
    void guardaYRecuperaLaMarcaDeRevision() {
        Cliente guardado = repositorio.guardar(factory.crear(solicitud("martina@gmail.com", null)));
        guardado.marcarRevision(MotivoRevision.CUIL_DISTINTO, Instant.now());
        repositorio.guardar(guardado);

        assertThat(repositorio.buscarPorId(guardado.id()).orElseThrow().motivoRevision())
                .contains(MotivoRevision.CUIL_DISTINTO);
    }

    @Test
    void laBaseRechazaUnCuilIncompleto() {
        Cliente guardado = repositorio.guardar(factory.crear(solicitudConCuil("martina@gmail.com", "20-12345678-6")));
        entityManager.flush();

        assertThatThrownBy(() -> ejecutar("update cliente set cuil_cifrado = null where id = ?1", guardado.id()))
                .hasStackTraceContaining("ck_cliente_cuil_completo");
    }

    @Test
    void laBaseRechazaUnAnonimizadoConCuil() {
        Cliente guardado = repositorio.guardar(factory.crear(solicitudConCuil("martina@gmail.com", "20-12345678-6")));
        entityManager.flush();

        assertThatThrownBy(() -> ejecutar("update cliente set estado = 'ANONIMIZADO', email = null, "
                + "telefono_e164 = null, anonimizado_en = now() where id = ?1", guardado.id()))
                .hasStackTraceContaining("ck_cliente_anonimizado_sin_datos");
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
    void soloLasViolacionesDeLosIndicesUnicosSeTraducen() {
        DataIntegrityViolationException email = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"ux_cliente_email_activo\"");
        DataIntegrityViolationException cuil = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"ux_cliente_cuil_activo\"");
        DataIntegrityViolationException check = new DataIntegrityViolationException(
                "new row violates check constraint \"ck_cliente_anonimizado_sin_datos\"");
        assertThat(ClienteRepositorioJpa.violaIndice(email, ClienteRepositorioJpa.INDICE_EMAIL)).isTrue();
        assertThat(ClienteRepositorioJpa.violaIndice(cuil, ClienteRepositorioJpa.INDICE_EMAIL)).isFalse();
        assertThat(ClienteRepositorioJpa.violaIndice(cuil, ClienteRepositorioJpa.INDICE_CUIL)).isTrue();
        assertThat(ClienteRepositorioJpa.violaIndice(check, ClienteRepositorioJpa.INDICE_CUIL)).isFalse();
    }

    private SolicitudAlta solicitud(String email, String telefono) {
        return factory.solicitud("Martina", "López", email, telefono, Provincia.BUENOS_AIRES, null,
                CanalOrigen.INSTAGRAM);
    }

    private SolicitudAlta solicitudConCuil(String email, String cuil) {
        return factory.solicitud("Martina", "López", email, null, Provincia.BUENOS_AIRES, null, CanalOrigen.INSTAGRAM,
                cuil, true);
    }

    private long contar(String sql, Object id) {
        return ((Number) entityManager.createNativeQuery(sql).setParameter(1, id).getSingleResult()).longValue();
    }

    private void ejecutar(String sql, Object id) {
        entityManager.createNativeQuery(sql).setParameter(1, id).executeUpdate();
    }
}
