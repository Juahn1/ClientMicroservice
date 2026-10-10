package com.uade.crm.clientes.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uade.crm.clientes.CuilDePrueba;
import com.uade.crm.clientes.PostgresDePrueba;
import com.uade.crm.clientes.domain.ClienteFactory;
import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.FuenteSituacion;
import com.uade.crm.clientes.domain.model.SituacionCrediticia;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caché de la situación crediticia sobre PostgreSQL real (design D4 del change 05). Sin la transacción de test que
 * agrega {@code @DataJpaTest}: el guardado corre en su propia transacción y tiene que ver al cliente confirmado.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresDePrueba.class, ClienteRepositorioJpa.class, CacheSituacionCrediticiaJpa.class})
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CacheSituacionCrediticiaJpaTest {

    private static final Instant AHORA = Instant.parse("2026-10-10T12:00:00Z").truncatedTo(ChronoUnit.MICROS);
    private static final YearMonth PERIODO = YearMonth.of(2026, 9);
    private static final Duration VIGENCIA = Duration.ofDays(30);

    private final ClienteFactory factory = CuilDePrueba.factory(Clock.systemUTC());

    @Autowired
    CacheSituacionCrediticiaJpa cache;

    @Autowired
    ClienteRepositorioJpa clientes;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM situacion_crediticia");
        jdbc.update("DELETE FROM consentimiento");
        jdbc.update("DELETE FROM cliente");
    }

    @Test
    void devuelveElResultadoMientrasEstaVigente() {
        UUID id = cliente("martina@gmail.com");
        var guardada = SituacionCrediticia.conDatos(1, PERIODO, FuenteSituacion.SIMULADA, AHORA);

        cache.guardar(id, guardada, AHORA.plus(VIGENCIA));

        assertThat(cache.vigente(id, AHORA.plusSeconds(1))).contains(guardada);
        assertThat(cache.vigente(id, AHORA.plus(VIGENCIA).minusSeconds(1))).contains(guardada);
    }

    @Test
    void noDevuelveUnResultadoVencido() {
        UUID id = cliente("martina@gmail.com");
        cache.guardar(id, SituacionCrediticia.sinRegistros(PERIODO, FuenteSituacion.SIMULADA, AHORA),
                AHORA.plus(VIGENCIA));

        assertThat(cache.vigente(id, AHORA.plus(VIGENCIA))).isEmpty();
        assertThat(cache.vigente(id, AHORA.plus(VIGENCIA).plusSeconds(1))).isEmpty();
    }

    @Test
    void guardarDosVecesDejaUnaFilaConElUltimoResultado() {
        UUID id = cliente("martina@gmail.com");
        cache.guardar(id, SituacionCrediticia.conDatos(1, PERIODO, FuenteSituacion.SIMULADA, AHORA),
                AHORA.plus(VIGENCIA));
        Instant despues = AHORA.plus(Duration.ofDays(31));
        var nueva = SituacionCrediticia.conDatos(5, YearMonth.of(2026, 10), FuenteSituacion.SIMULADA, despues);

        cache.guardar(id, nueva, despues.plus(VIGENCIA));

        assertThat(filas(id)).isEqualTo(1);
        assertThat(cache.vigente(id, despues)).contains(nueva);
    }

    @Test
    void noGuardaParaUnClienteAnonimizado() {
        UUID id = cliente("martina@gmail.com");
        Cliente cliente = clientes.buscarPorId(id).orElseThrow();
        cliente.anonimizar(AHORA);
        clientes.guardar(cliente);

        cache.guardar(id, SituacionCrediticia.conDatos(1, PERIODO, FuenteSituacion.SIMULADA, AHORA),
                AHORA.plus(VIGENCIA));

        assertThat(filas(id)).isZero();
    }

    @Test
    void eliminarBorraElResultado() {
        UUID id = cliente("martina@gmail.com");
        cache.guardar(id, SituacionCrediticia.conDatos(1, PERIODO, FuenteSituacion.SIMULADA, AHORA),
                AHORA.plus(VIGENCIA));

        cache.eliminar(id);
        cache.eliminar(id);

        assertThat(filas(id)).isZero();
    }

    @Test
    void dosGuardadosSimultaneosDejanUnaSolaFila() throws Exception {
        UUID id = cliente("martina@gmail.com");
        var situacion = SituacionCrediticia.conDatos(2, PERIODO, FuenteSituacion.SIMULADA, AHORA);
        CyclicBarrier largada = new CyclicBarrier(2);
        Runnable guardar = () -> {
            try {
                largada.await(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            cache.guardar(id, situacion, AHORA.plus(VIGENCIA));
        };

        CompletableFuture.allOf(CompletableFuture.runAsync(guardar), CompletableFuture.runAsync(guardar))
                .get(10, TimeUnit.SECONDS);

        assertThat(filas(id)).isEqualTo(1);
        assertThat(cache.vigente(id, AHORA)).contains(situacion);
    }

    @Test
    void laBaseRechazaUnNoDisponible() {
        UUID id = cliente("martina@gmail.com");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO situacion_crediticia (cliente_id, estado, periodo, fuente, consultada_en, vigente_hasta)
                VALUES (?, 'NO_DISPONIBLE', '202609', 'SIMULADA', now(), now())""", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_situacion_estado");
    }

    @Test
    void laBaseRechazaConDatosSinSituacion() {
        UUID id = cliente("martina@gmail.com");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO situacion_crediticia (cliente_id, estado, periodo, fuente, consultada_en, vigente_hasta)
                VALUES (?, 'CON_DATOS', '202609', 'SIMULADA', now(), now())""", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_situacion_valor");
    }

    private UUID cliente(String email) {
        return clientes.guardar(factory.crear(factory.solicitud("Martina", "López", email, null,
                Provincia.BUENOS_AIRES, null, CanalOrigen.INSTAGRAM, "20-12345678-6", true))).id();
    }

    private int filas(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM situacion_crediticia WHERE cliente_id = ?", Integer.class, id);
    }
}
