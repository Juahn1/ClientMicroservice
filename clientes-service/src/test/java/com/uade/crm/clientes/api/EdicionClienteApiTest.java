package com.uade.crm.clientes.api;

import static com.uade.crm.clientes.api.AltaClienteApiTest.CLAVE;
import static com.uade.crm.clientes.api.AltaClienteApiTest.martina;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uade.crm.clientes.MensajesKafka;
import com.uade.crm.clientes.PruebaIntegracion;
import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.SolicitudAlta;
import com.uade.crm.clientes.domain.port.ClienteRepositorio;
import com.uade.crm.clientes.domain.valor.Email;
import com.uade.crm.contracts.Provincia;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Requirements: Edición con control de concurrencia y Email único al editar (clientes-gestion), y la publicación de
 * ClienteActualizado al editar (clientes-eventos).
 */
@PruebaIntegracion
@Import(MensajesKafka.class)
class EdicionClienteApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MensajesKafka mensajes;

    @Autowired
    ClienteRepositorio repositorio;

    @Autowired
    TransactionTemplate transaccion;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM situacion_crediticia");
        jdbc.update("DELETE FROM consentimiento");
        jdbc.update("DELETE FROM cliente");
    }

    @Test
    void edicionExitosa() throws Exception {
        UUID id = alta(martina("martina@gmail.com", "11 1234-5678", "INSTAGRAM"));

        String etag = editar(id, "W/\"0\"", "{ \"apellido\": \"Gómez\" }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apellido").value("Gómez"))
                .andExpect(jsonPath("$.nombre").value("Martina"))
                .andExpect(jsonPath("$.email").value("martina@gmail.com"))
                .andExpect(jsonPath("$.telefono").value("+5491112345678"))
                .andExpect(jsonPath("$.provincia").value("BUENOS_AIRES"))
                .andReturn().getResponse().getHeader("ETag");

        assertThat(etag).isNotEqualTo("W/\"0\"");
        mvc.perform(get("/clientes/{id}", id))
                .andExpect(header().string("ETag", etag))
                .andExpect(jsonPath("$.apellido").value("Gómez"));
    }

    // --- Teléfono alternativo y resolución de la revisión (change 04)

    @Test
    void telefonoAlternativo() throws Exception {
        UUID id = alta(martina("martina@gmail.com", "11 1234-5678", null));

        editar(id, "W/\"0\"", "{ \"telefonoAlternativo\": \"11 8765-4321\" }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefonoAlternativo").value("+5491187654321"))
                .andExpect(header().string("ETag", "W/\"1\""));
    }

    @Test
    void telefonoAlternativoIgualAlPrincipal() throws Exception {
        UUID id = alta(martina("martina@gmail.com", "11 1234-5678", null));

        editar(id, "W/\"0\"", "{ \"telefonoAlternativo\": \"11 1234-5678\" }")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("telefonoAlternativo"));

        mvc.perform(get("/clientes/{id}", id))
                .andExpect(header().string("ETag", "W/\"0\""))
                .andExpect(jsonPath("$.telefonoAlternativo").doesNotExist());
    }

    @Test
    void resolverUnaRevision() throws Exception {
        UUID id = alta(AltaConCuilApiTest.cuerpo("martina@gmail.com", null, "27-30111222-5", true));
        alta(AltaConCuilApiTest.cuerpo("martina@gmail.com", null, "20-12345678-6", true));
        String etag = mvc.perform(get("/clientes/{id}", id))
                .andExpect(jsonPath("$.motivoRevision").value("CUIL_DISTINTO"))
                .andReturn().getResponse().getHeader("ETag");

        editar(id, etag, "{ \"requiereRevision\": false }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiereRevision").value(false))
                .andExpect(jsonPath("$.motivoRevision").doesNotExist());
    }

    @Test
    void resolverUnClienteSinRevision() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));

        editar(id, "W/\"0\"", "{ \"requiereRevision\": false }")
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "W/\"0\""));
    }

    @Test
    void marcarAMano() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));

        editar(id, "W/\"0\"", "{ \"requiereRevision\": true }")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("requiereRevision"));
    }

    /** clientes-eventos: "Edición de datos que no copia leads" y "Edición mixta". */
    @Test
    void soloLosCamposPublicablesGeneranEvento() throws Exception {
        UUID soloAlternativo = alta(martina("martina@gmail.com", "11 1234-5678", null));
        UUID mixta = alta(martina("diego@gmail.com", "11 1234-5678", null));

        editar(soloAlternativo, "W/\"0\"", "{ \"telefonoAlternativo\": \"11 8765-4321\" }")
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "W/\"1\""));
        editar(mixta, "W/\"0\"", "{ \"apellido\": \"Gómez\", \"telefonoAlternativo\": \"11 8765-4321\" }")
                .andExpect(status().isOk());

        JsonNode sobre = json.readTree(mensajes.esperar("cliente-actualizado", mixta, 1).getFirst().value());
        assertThat(sobre.at("/payload/campos").toString()).isEqualTo("[\"APELLIDO\"]");
        assertThat(mensajes.ninguno("cliente-actualizado", soloAlternativo)).isEmpty();
    }

    /** clientes-eventos, escenario "Recontacto": registrar el CUIL o marcar una revisión no publica eventos. */
    @Test
    void elRecontactoQueRegistraElCuilOMarcaRevisionNoPublica() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));

        alta(AltaConCuilApiTest.cuerpo("martina@gmail.com", null, "27-30111222-5", true));
        alta(AltaConCuilApiTest.cuerpo("martina@gmail.com", null, "20-12345678-6", true));

        mvc.perform(get("/clientes/{id}", id))
                .andExpect(jsonPath("$.cuil").value("XX-XXXX1222-X"))
                .andExpect(jsonPath("$.motivoRevision").value("CUIL_DISTINTO"));
        assertThat(mensajes.ninguno("cliente-actualizado", id)).isEmpty();
    }

    @Test
    void aceptaElEtagFuerte() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));

        editar(id, "\"0\"", "{ \"nombre\": \"Martu\" }").andExpect(status().isOk());
    }

    @Test
    void versionDesactualizada() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));
        editar(id, "W/\"0\"", "{ \"nombre\": \"Martu\" }").andExpect(status().isOk());

        editar(id, "W/\"0\"", "{ \"apellido\": \"Gómez\" }")
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.codigo").value("VERSION_DESACTUALIZADA"));

        mvc.perform(get("/clientes/{id}", id)).andExpect(jsonPath("$.apellido").value("López"));
    }

    @Test
    void unIfMatchComodinOInvalidoNoCoincide() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));

        for (String ifMatch : new String[] {"*", "W/\"abc\"", "0"}) {
            editar(id, ifMatch, "{ \"nombre\": \"Martu\" }")
                    .andExpect(status().isPreconditionFailed())
                    .andExpect(jsonPath("$.codigo").value("VERSION_DESACTUALIZADA"));
        }
    }

    @Test
    void faltaIfMatch() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));

        mvc.perform(patch("/clientes/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nombre\": \"Martu\" }"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.codigo").value("PRECONDICION_REQUERIDA"));
    }

    @Test
    void faltaIfMatchTienePrioridadSobreLaValidacion() throws Exception {
        mvc.perform(patch("/clientes/{id}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"apellido\": \"L0pez\" }"))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void edicionSinCambiosRealesNoCambiaLaVersionNiPublica() throws Exception {
        UUID id = alta(martina("martina@gmail.com", "11 1234-5678", null));

        editar(id, "W/\"0\"", """
                { "nombre": "Martina", "email": " MARTINA@gmail.com ", "telefono": "11 1234 5678",
                  "provincia": "BUENOS_AIRES" }
                """)
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "W/\"0\""));

        assertThat(mensajes.ninguno("cliente-actualizado", id)).isEmpty();
    }

    @Test
    void telefonoNormalizadoAlEditar() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));

        editar(id, "W/\"0\"", "{ \"telefono\": \"0351 15 412-3456\" }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefono").value("+5493514123456"));
    }

    @Test
    void datosInvalidosAlEditar() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));

        editar(id, "W/\"0\"", "{ \"apellido\": \"L0pez\" }")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("apellido"));
        editar(id, "W/\"0\"", "{ \"telefono\": \"1512345678\" }")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TELEFONO_INVALIDO"));

        mvc.perform(get("/clientes/{id}", id))
                .andExpect(header().string("ETag", "W/\"0\""))
                .andExpect(jsonPath("$.apellido").value("López"));
    }

    @Test
    void edicionDeUnClienteInexistente() throws Exception {
        editar(UUID.randomUUID(), "W/\"0\"", "{ \"nombre\": \"Martu\" }")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CLIENTE_NO_ENCONTRADO"));
    }

    @Test
    void edicionDeUnClienteAnonimizado() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));
        jdbc.update("UPDATE cliente SET estado = 'ANONIMIZADO', email = NULL WHERE id = ?", id);

        editar(id, "W/\"0\"", "{ \"nombre\": \"Martu\" }")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("CLIENTE_ANONIMIZADO"));
    }

    @Test
    void emailDeOtroCliente() throws Exception {
        alta(martina("diego@gmail.com", null, null));
        UUID id = alta(martina("martina@gmail.com", null, null));

        editar(id, "W/\"0\"", "{ \"email\": \" Diego@Gmail.com \" }")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EMAIL_EN_USO"))
                .andExpect(jsonPath("$.errores[0].campo").value("email"));

        mvc.perform(get("/clientes/{id}", id)).andExpect(jsonPath("$.email").value("martina@gmail.com"));
    }

    @Test
    void emailNuevoUsadoEnUnAltaPosterior() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));
        editar(id, "W/\"0\"", "{ \"email\": \"martina.lopez@gmail.com\" }").andExpect(status().isOk());

        mvc.perform(post("/clientes").header("X-Api-Key", CLAVE).contentType(MediaType.APPLICATION_JSON)
                        .content(martina("martina.lopez@gmail.com", null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(id.toString()));
    }

    /** Scenario de clientes-eventos: Cambio de nombre y email. */
    @Test
    void publicaClienteActualizadoConLosCamposModificados() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));

        editar(id, "W/\"0\"", "{ \"email\": \"martu@gmail.com\", \"nombre\": \"Martu\" }")
                .andExpect(status().isOk());

        JsonNode sobre = json.readTree(mensajes.esperar("cliente-actualizado", id, 1).getFirst().value());
        assertThat(sobre.get("correlationId").asText()).isEqualTo("edicion-1");
        assertThat(sobre.get("payload").get("campos")).extracting(JsonNode::asText)
                .containsExactly("NOMBRE", "EMAIL");
        assertThat(sobre.toString()).doesNotContain("martu@gmail.com").doesNotContain("Martu");
    }

    /**
     * Concurrencia con un recontacto (design D4): el recontacto confirma después de que el PATCH leyó la versión, o
     * antes; en los dos casos el PATCH con el ETag previo responde 412 y el cliente conserva el recontacto.
     */
    @Test
    void unRecontactoConcurrenteGanaYElPatchRespondePrecondicionFallida() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));
        CountDownLatch bloqueado = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);

        CompletableFuture<Void> recontacto = CompletableFuture.runAsync(() -> transaccion.executeWithoutResult(e -> {
            Cliente cliente = repositorio.buscarActivoPorEmailParaActualizar(Email.de("martina@gmail.com"))
                    .orElseThrow();
            cliente.registrarRecontacto(new SolicitudAlta("Martina", "López",
                    Email.de("martina@gmail.com"), null, Provincia.BUENOS_AIRES, null, null),
                    Instant.now());
            repositorio.guardar(cliente);
            bloqueado.countDown();
            esperar(liberar);
        }));
        esperar(bloqueado);

        CompletableFuture<MockHttpServletResponse> edicion = CompletableFuture.supplyAsync(() -> {
            try {
                return editar(id, "W/\"0\"", "{ \"apellido\": \"Gómez\" }").andReturn().getResponse();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        Thread.sleep(500);
        liberar.countDown();
        recontacto.get(10, TimeUnit.SECONDS);

        assertThat(edicion.get(10, TimeUnit.SECONDS).getStatus()).isEqualTo(412);
        mvc.perform(get("/clientes/{id}", id))
                .andExpect(jsonPath("$.contactosRegistrados").value(2))
                .andExpect(jsonPath("$.apellido").value("López"));
    }

    private ResultActions editar(UUID id, String ifMatch, String cuerpo) throws Exception {
        return mvc.perform(patch("/clientes/{id}", id).header("If-Match", ifMatch)
                .header("X-Correlation-Id", "edicion-1").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private UUID alta(String cuerpo) throws Exception {
        String respuesta = mvc.perform(post("/clientes").header("X-Api-Key", CLAVE)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(respuesta).get("clienteId").asText());
    }

    private static void esperar(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
