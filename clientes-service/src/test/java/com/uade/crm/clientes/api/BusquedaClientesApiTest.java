package com.uade.crm.clientes.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uade.crm.clientes.PruebaIntegracion;
import com.uade.crm.clientes.domain.ClienteFactory;
import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.port.ClienteRepositorio;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Requirements: Búsqueda paginada de clientes, Búsqueda por nombre sin distinguir mayúsculas ni acentos y
 * Tamaño de página limitado. Los datos se cargan con el repositorio para no depender del alta.
 */
@PruebaIntegracion
class BusquedaClientesApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ClienteFactory factory;

    @Autowired
    ClienteRepositorio repositorio;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM situacion_crediticia");
        jdbc.update("DELETE FROM consentimiento");
        jdbc.update("DELETE FROM cliente");
    }

    // --- Búsqueda paginada de clientes

    @Test
    void busquedaPorTelefonoEnFormatoLibre() throws Exception {
        UUID martina = cliente("Martina", "López", "martina@gmail.com", "+5491112345678", Provincia.BUENOS_AIRES);
        cliente("Martín", "López", "martin@gmail.com", "+5493514123456", Provincia.CORDOBA);

        mvc.perform(get("/clientes").param("telefono", "11 1234 5678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].id").value(martina.toString()))
                .andExpect(jsonPath("$.contenido[0].telefono").value("+5491112345678"));
    }

    @Test
    void busquedaPorEmail() throws Exception {
        UUID martina = cliente("Martina", "López", "martina@gmail.com", null, Provincia.BUENOS_AIRES);
        cliente("Martín", "López", "martin@gmail.com", null, Provincia.CORDOBA);

        mvc.perform(get("/clientes").param("email", "MARTINA@gmail.com"))
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].id").value(martina.toString()));
    }

    @Test
    void filtrosCombinados() throws Exception {
        cliente("Martina", "López", "martina@gmail.com", null, Provincia.BUENOS_AIRES);
        UUID martin = cliente("Martín", "López", "martin@gmail.com", null, Provincia.CORDOBA);

        mvc.perform(get("/clientes").param("q", "lopez").param("provincia", "CORDOBA"))
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].id").value(martin.toString()));
    }

    @Test
    void sinFiltrosDevuelveTodosLosActivosOrdenadosPorUltimoContacto() throws Exception {
        UUID viejo = cliente("Ana", "Paz", "ana@gmail.com", null, Provincia.SALTA);
        UUID nuevo = cliente("Bruno", "Paz", "bruno@gmail.com", null, Provincia.SALTA);
        UUID anonimizado = cliente("Carla", "Paz", "carla@gmail.com", null, Provincia.SALTA);
        ultimoContacto(viejo, "2026-10-01T10:00:00Z");
        ultimoContacto(nuevo, "2026-10-05T10:00:00Z");
        jdbc.update("UPDATE cliente SET estado = 'ANONIMIZADO', email = NULL, localidad = NULL WHERE id = ?",
                anonimizado);

        mvc.perform(get("/clientes"))
                .andExpect(jsonPath("$.contenido", hasSize(2)))
                .andExpect(jsonPath("$.contenido[0].id").value(nuevo.toString()))
                .andExpect(jsonPath("$.contenido[1].id").value(viejo.toString()))
                .andExpect(jsonPath("$.totalElementos").value(2));
    }

    @Test
    void telefonoInvalidoEnLaBusqueda() throws Exception {
        mvc.perform(get("/clientes").param("telefono", "123"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TELEFONO_INVALIDO"));
    }

    @Test
    void provinciaInvalidaEnLaBusqueda() throws Exception {
        mvc.perform(get("/clientes").param("provincia", "BS AS"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("provincia"));
    }

    @Test
    void paginaOTamanioInvalidos() throws Exception {
        mvc.perform(get("/clientes").param("page", "-1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("page")));
        mvc.perform(get("/clientes").param("size", "0"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("size")));
    }

    @Test
    void cuilFueraDeLaBusqueda() throws Exception {
        mvc.perform(post("/clientes").header("X-Api-Key", AltaClienteApiTest.CLAVE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AltaConCuilApiTest.cuerpo("martina@gmail.com", null, "20-12345678-6", true)))
                .andExpect(status().isCreated());

        mvc.perform(get("/clientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].cuil").doesNotExist())
                .andExpect(jsonPath("$.contenido[0].consentimientosVigentes").doesNotExist());
    }

    @Test
    void formaDeLaRespuesta() throws Exception {
        cliente("Martina", "López", "martina@gmail.com", "+5491112345678", Provincia.BUENOS_AIRES);

        mvc.perform(get("/clientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isArray())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanio").value(20))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(1))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Martina"))
                .andExpect(jsonPath("$.contenido[0].apellido").value("López"))
                .andExpect(jsonPath("$.contenido[0].email").value("martina@gmail.com"))
                .andExpect(jsonPath("$.contenido[0].provincia").value("BUENOS_AIRES"))
                .andExpect(jsonPath("$.contenido[0].ultimoCanal").value("INSTAGRAM"))
                .andExpect(jsonPath("$.contenido[0].contactosRegistrados").value(1))
                .andExpect(jsonPath("$.contenido[0].ultimoContactoEn").isString())
                .andExpect(jsonPath("$.contenido[0].noContactar").value(false))
                // Es un resumen: la ficha completa se pide por id.
                .andExpect(jsonPath("$.contenido[0].localidad").doesNotExist())
                .andExpect(jsonPath("$.contenido[0].telefonoAlternativo").doesNotExist());
    }

    // --- Búsqueda por nombre sin distinguir mayúsculas ni acentos

    @Test
    void busquedaSinAcentos() throws Exception {
        UUID martina = cliente("Martina", "López", "martina@gmail.com", null, Provincia.BUENOS_AIRES);

        mvc.perform(get("/clientes").param("q", "lopez"))
                .andExpect(jsonPath("$.contenido[*].id", hasItem(martina.toString())));
    }

    @Test
    void busquedaSobreNombreYApellido() throws Exception {
        UUID martina = cliente("Martina", "López", "martina@gmail.com", null, Provincia.BUENOS_AIRES);

        mvc.perform(get("/clientes").param("q", "TINA LOP"))
                .andExpect(jsonPath("$.contenido[*].id", hasItem(martina.toString())));
    }

    @Test
    void laEnieSeBuscaComoN() throws Exception {
        UUID nunez = cliente("Ramón", "Núñez", "ramon@gmail.com", null, Provincia.MENDOZA);

        mvc.perform(get("/clientes").param("q", "nunez"))
                .andExpect(jsonPath("$.contenido[*].id", hasItem(nunez.toString())));
    }

    @Test
    void comodinesComoTextoLiteral() throws Exception {
        cliente("Martina", "López", "martina@gmail.com", null, Provincia.BUENOS_AIRES);

        mvc.perform(get("/clientes").param("q", "%"))
                .andExpect(jsonPath("$.contenido", hasSize(0)));
        mvc.perform(get("/clientes").param("q", "m_rtina"))
                .andExpect(jsonPath("$.contenido", hasSize(0)));
    }

    // --- Tamaño de página limitado

    @Test
    void tamanioDePaginaExcedido() throws Exception {
        for (int i = 0; i < 51; i++) {
            cliente("Cliente", "Número", "cliente" + i + "@gmail.com", null, Provincia.CHACO);
        }

        mvc.perform(get("/clientes").param("size", "500"))
                .andExpect(jsonPath("$.tamanio").value(50))
                .andExpect(jsonPath("$.contenido", hasSize(lessThanOrEqualTo(50))))
                .andExpect(jsonPath("$.totalElementos").value(51))
                .andExpect(jsonPath("$.totalPaginas").value(2));
    }

    @Test
    void tamanioPorDefecto() throws Exception {
        mvc.perform(get("/clientes"))
                .andExpect(jsonPath("$.tamanio").value(20));
    }

    private UUID cliente(String nombre, String apellido, String email, String telefono, Provincia provincia) {
        Cliente cliente = factory.crear(factory.solicitud(nombre, apellido, email, telefono, provincia, "Centro",
                CanalOrigen.INSTAGRAM));
        return repositorio.guardar(cliente).id();
    }

    private void ultimoContacto(UUID id, String instante) {
        jdbc.update("UPDATE cliente SET ultimo_contacto_en = ? WHERE id = ?",
                Timestamp.from(Instant.parse(instante)), id);
    }
}
