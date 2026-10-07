package com.uade.crm.clientes.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.uade.crm.clientes.PruebaIntegracion;
import com.uade.crm.clientes.domain.ClienteFactory;
import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.Coincidencia;
import com.uade.crm.clientes.domain.model.ResultadoAlta;
import com.uade.crm.clientes.domain.model.SolicitudAlta;
import com.uade.crm.clientes.domain.port.ClienteRepositorio;
import com.uade.crm.clientes.domain.valor.Email;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Requirements: Alta de cliente y Deduplicación por email (a nivel de caso de uso, con PostgreSQL real). */
@PruebaIntegracion
class AltaClienteServiceTest {

    @Autowired
    AltaClienteService alta;

    @Autowired
    ClienteFactory factory;

    @Autowired
    ClienteRepositorio repositorio;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM cliente");
    }

    @Test
    void clienteNuevo() {
        ResultadoAlta resultado = alta.registrar(solicitud("martina@gmail.com", CanalOrigen.INSTAGRAM));

        assertThat(resultado.nuevo()).isTrue();
        assertThat(resultado.coincidencia()).isEqualTo(Coincidencia.NINGUNA);
        Cliente cliente = repositorio.buscarActivoPorId(resultado.clienteId()).orElseThrow();
        assertThat(cliente.contactosRegistrados()).isEqualTo(1);
        assertThat(cliente.ultimoCanal()).contains(CanalOrigen.INSTAGRAM);
    }

    @Test
    void mismoEmailConOtroFormato() {
        ResultadoAlta primero = alta.registrar(solicitud("martina@gmail.com", CanalOrigen.INSTAGRAM));

        ResultadoAlta segundo = alta.registrar(solicitud(" Martina@Gmail.com ", CanalOrigen.WHATSAPP));

        assertThat(segundo.clienteId()).isEqualTo(primero.clienteId());
        assertThat(segundo.nuevo()).isFalse();
        assertThat(segundo.coincidencia()).isEqualTo(Coincidencia.EMAIL);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cliente", Integer.class)).isEqualTo(1);
        Cliente cliente = repositorio.buscarActivoPorId(primero.clienteId()).orElseThrow();
        assertThat(cliente.contactosRegistrados()).isEqualTo(2);
        assertThat(cliente.ultimoCanal()).contains(CanalOrigen.WHATSAPP);
    }

    /** 2 es el escenario del spec; con más altas simultáneas también se ejercitan los recontactos en fila. */
    @ParameterizedTest
    @ValueSource(ints = {2, 6})
    void altasSimultaneasConElMismoEmail(int simultaneas) throws Exception {
        String email = "concurrente-" + simultaneas + "@gmail.com";
        CountDownLatch largada = new CountDownLatch(1);
        List<Callable<ResultadoAlta>> altas = new ArrayList<>();
        for (int i = 0; i < simultaneas; i++) {
            altas.add(() -> {
                largada.await();
                return alta.registrar(solicitud(email, CanalOrigen.OTRO));
            });
        }

        List<ResultadoAlta> resultados = new ArrayList<>();
        try (ExecutorService hilos = Executors.newFixedThreadPool(simultaneas)) {
            List<Future<ResultadoAlta>> futuros = altas.stream().map(hilos::submit).toList();
            largada.countDown();
            for (Future<ResultadoAlta> futuro : futuros) {
                resultados.add(futuro.get());
            }
        }

        assertThat(resultados).extracting(ResultadoAlta::clienteId).containsOnly(resultados.get(0).clienteId());
        assertThat(resultados).filteredOn(ResultadoAlta::nuevo).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cliente WHERE email = ?", Integer.class, email))
                .isEqualTo(1);
        assertThat(repositorio.buscarActivoPorEmail(Email.de(email)).orElseThrow().contactosRegistrados())
                .isEqualTo(simultaneas);
    }

    private SolicitudAlta solicitud(String email, CanalOrigen canal) {
        return factory.solicitud("Martina", "López", email, null, Provincia.BUENOS_AIRES, null, canal);
    }
}
