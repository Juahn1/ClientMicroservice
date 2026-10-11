package com.uade.crm.clientes.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.uade.crm.clientes.CuilDePrueba;
import com.uade.crm.clientes.application.RegistroCalificacionService.Resultado;
import com.uade.crm.clientes.domain.ClienteFactory;
import com.uade.crm.clientes.domain.model.CalificacionHistorica;
import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.port.ClienteRepositorio;
import com.uade.crm.clientes.domain.port.HistorialCalificaciones;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Registro y clasificación del descarte (design D6 del change 08). */
class RegistroCalificacionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-11T20:00:00Z");

    private final HistorialCalificaciones historial = mock(HistorialCalificaciones.class);
    private final ClienteRepositorio clientes = mock(ClienteRepositorio.class);
    private final RegistroCalificacionService servicio =
            new RegistroCalificacionService(historial, clientes, Clock.fixed(AHORA, ZoneOffset.UTC));
    private final ClienteFactory factory = CuilDePrueba.factory(Clock.systemUTC());

    private final Logger logger = (Logger) LoggerFactory.getLogger(RegistroCalificacionService.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Level nivelAnterior;

    private final CalificacionHistorica calificacion = new CalificacionHistorica(UUID.randomUUID(),
            UUID.randomUUID(), "MUY_POTABLE", 60, "AUTOMATICA", Instant.parse("2026-10-11T18:30:00Z"),
            "Anticipo confirmado", List.of("Cobertura aportada: 60.00%"));

    @BeforeEach
    void capturarLogs() {
        nivelAnterior = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void soltarLogs() {
        logger.detachAppender(logs);
        logger.setLevel(nivelAnterior);
    }

    @Test
    void registraConLaHoraDelRelojSinConsultarAlCliente() {
        when(historial.registrar(calificacion, AHORA)).thenReturn(true);

        assertThat(servicio.registrar(calificacion)).isEqualTo(Resultado.REGISTRADA);

        verify(historial).registrar(eq(calificacion), eq(AHORA));
        verifyNoInteractions(clientes);
        assertLog(Level.INFO, "Calificación registrada");
    }

    @Test
    void clienteInexistenteSeDescartaConWarn() {
        when(historial.registrar(any(), any())).thenReturn(false);
        when(clientes.buscarPorId(calificacion.clienteId())).thenReturn(Optional.empty());

        assertThat(servicio.registrar(calificacion)).isEqualTo(Resultado.CLIENTE_INEXISTENTE);

        assertLog(Level.WARN, "no existe");
    }

    @Test
    void clienteAnonimizadoSeDescartaConInfo() {
        Cliente anonimizado = cliente();
        anonimizado.anonimizar(AHORA);
        when(historial.registrar(any(), any())).thenReturn(false);
        when(clientes.buscarPorId(calificacion.clienteId())).thenReturn(Optional.of(anonimizado));

        assertThat(servicio.registrar(calificacion)).isEqualTo(Resultado.CLIENTE_ANONIMIZADO);

        assertLog(Level.INFO, "anonimizado");
    }

    @Test
    void duplicadoDeUnClienteActivoSeDescartaConDebug() {
        when(historial.registrar(any(), any())).thenReturn(false);
        when(clientes.buscarPorId(calificacion.clienteId())).thenReturn(Optional.of(cliente()));

        assertThat(servicio.registrar(calificacion)).isEqualTo(Resultado.DUPLICADA);

        assertLog(Level.DEBUG, "ya estaba registrada");
    }

    private void assertLog(Level nivel, String texto) {
        assertThat(logs.list).singleElement().satisfies(evento -> {
            assertThat(evento.getLevel()).isEqualTo(nivel);
            assertThat(evento.getFormattedMessage())
                    .contains(texto)
                    .contains(calificacion.leadId().toString())
                    .contains(calificacion.clienteId().toString())
                    .doesNotContain("Anticipo confirmado")
                    .doesNotContain("Cobertura aportada");
        });
    }

    private Cliente cliente() {
        return factory.crear(factory.solicitud("Martina", "López", "martina@gmail.com", null, Provincia.BUENOS_AIRES,
                null, CanalOrigen.INSTAGRAM));
    }
}
