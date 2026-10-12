package com.uade.crm.clientes.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uade.crm.clientes.CuilDePrueba;
import com.uade.crm.clientes.PruebaIntegracion;
import com.uade.crm.clientes.application.AnonimizacionClienteService;
import com.uade.crm.clientes.domain.ClienteFactory;
import com.uade.crm.clientes.domain.model.CalificacionHistorica;
import com.uade.crm.clientes.domain.port.ClienteRepositorio;
import com.uade.crm.clientes.domain.port.HistorialCalificaciones;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Requirement "Consulta del historial de calificaciones" (clientes-historial-calificaciones, design D8). */
@PruebaIntegracion
class HistorialApiTest {

    private static final Instant DIEZ = Instant.parse("2026-10-11T13:00:00Z");
    private static final Instant ONCE = Instant.parse("2026-10-11T14:00:00Z");
    private static final Instant DOCE = Instant.parse("2026-10-11T15:00:00Z");

    private final ClienteFactory factory = CuilDePrueba.factory(Clock.systemUTC());

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ClienteRepositorio clientes;

    @Autowired
    HistorialCalificaciones historial;

    @Autowired
    AnonimizacionClienteService anonimizacion;

    /** Antes y después: las demás clases del contexto compartido limpian al empezar y dejan sus datos. */
    @BeforeEach
    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM historial_calificacion");
        jdbc.update("DELETE FROM consentimiento");
        jdbc.update("DELETE FROM cliente");
    }

    @Test
    void historialConCalificacionesDeDosLeads() throws Exception {
        UUID cliente = cliente("martina@gmail.com");
        UUID leadA = UUID.randomUUID();
        UUID leadB = UUID.randomUUID();
        registrar(new CalificacionHistorica(leadA, cliente, "MUY_POTABLE", 60, "AUTOMATICA", DIEZ,
                "Calificación automática", List.of("[REGLA_COBERTURA]: +60", "[REGLA_UBICACION]: 0")));
        registrar(new CalificacionHistorica(leadB, cliente, "NO_POTABLE", -25, "AUTOMATICA", ONCE, null, List.of()));
        registrar(new CalificacionHistorica(leadA, cliente, "POTABLE", 35, "MANUAL", DOCE, "Anticipo confirmado",
                List.of()));

        mvc.perform(get("/clientes/{id}/historial", cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(cliente.toString()))
                .andExpect(jsonPath("$.calificaciones", hasSize(3)))
                .andExpect(jsonPath("$.calificaciones[*].leadId",
                        contains(leadA.toString(), leadB.toString(), leadA.toString())))
                .andExpect(jsonPath("$.calificaciones[*].origen", contains("MANUAL", "AUTOMATICA", "AUTOMATICA")))
                .andExpect(jsonPath("$.calificaciones[0].nivel").value("POTABLE"))
                .andExpect(jsonPath("$.calificaciones[0].puntaje").value(35))
                .andExpect(jsonPath("$.calificaciones[0].calculadaEn").value("2026-10-11T15:00:00Z"))
                .andExpect(jsonPath("$.calificaciones[0].observacion").value("Anticipo confirmado"))
                .andExpect(jsonPath("$.calificaciones[0].motivos", hasSize(0)))
                .andExpect(jsonPath("$.calificaciones[1].puntaje").value(-25))
                .andExpect(jsonPath("$.calificaciones[1].observacion").value(nullValue()))
                .andExpect(jsonPath("$.calificaciones[2].motivos",
                        contains("[REGLA_COBERTURA]: +60", "[REGLA_UBICACION]: 0")));
    }

    @Test
    void clienteSinCalificaciones() throws Exception {
        UUID cliente = cliente("martina@gmail.com");

        mvc.perform(get("/clientes/{id}/historial", cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calificaciones", hasSize(0)));
    }

    /** Ningún request de esta clase manda X-Api-Key; este escenario lo hace explícito. */
    @Test
    void sinClaveDeServicio() throws Exception {
        UUID cliente = cliente("martina@gmail.com");

        mvc.perform(get("/clientes/{id}/historial", cliente)).andExpect(status().isOk());
    }

    @Test
    void clienteInexistente() throws Exception {
        mvc.perform(get("/clientes/{id}/historial", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CLIENTE_NO_ENCONTRADO"));
    }

    @Test
    void clienteAnonimizado() throws Exception {
        UUID cliente = cliente("martina@gmail.com");
        anonimizacion.anonimizar(cliente, "martina@gmail.com");

        mvc.perform(get("/clientes/{id}/historial", cliente))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("CLIENTE_ANONIMIZADO"));
    }

    @Test
    void elContratoOpenApiIncluyeElHistorial() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/clientes/{id}/historial'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/clientes/{id}/historial'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/clientes/{id}/historial'].get.responses['410']").exists())
                .andExpect(jsonPath("$.components.schemas.CalificacionResponse.properties.puntaje").exists());
    }

    private void registrar(CalificacionHistorica calificacion) {
        historial.registrar(calificacion, Instant.now());
    }

    private UUID cliente(String email) {
        return clientes.guardar(factory.crear(factory.solicitud("Martina", "López", email, null,
                Provincia.BUENOS_AIRES, null, CanalOrigen.INSTAGRAM))).id();
    }
}
