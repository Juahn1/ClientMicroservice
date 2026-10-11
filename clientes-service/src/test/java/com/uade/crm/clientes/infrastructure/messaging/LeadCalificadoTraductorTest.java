package com.uade.crm.clientes.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uade.crm.clientes.domain.model.CalificacionHistorica;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Formato de {@code LeadCalificado} y su fecha (design D2 y D3 del change 08). */
class LeadCalificadoTraductorTest {

    private static final String LEAD = "550e8400-e29b-41d4-a716-446655440000";
    private static final String CLIENTE = "3fa85f64-5717-4562-b3fc-2c963f66afa6";

    private final LeadCalificadoTraductor traductor =
            new LeadCalificadoTraductor(new ObjectMapper(), ZoneId.of("America/Argentina/Buenos_Aires"));

    /** El ejemplo que mandó el responsable de calificacion. */
    private static final String EJEMPLO = """
            {
              "leadId": "550e8400-e29b-41d4-a716-446655440000",
              "clienteId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
              "puntos": 60,
              "nivel": "MUY_POTABLE",
              "origen": "AUTOMATICA",
              "fecha": "2026-10-11T15:30:00",
              "observacion": "Calificación automática calculada por motor de reglas",
              "motivos": [
                "[REGLA_COBERTURA]: Cobertura aportada: 60.00% (>= 50% (+60 pts)) (+60 pts)",
                "[REGLA_FINANCIACION]: No solicita financiación (0 pts) (+0 pts)",
                "[REGLA_UBICACION]: Ubicación en zona preferencial (BUENOS_AIRES): 0 pts (+0 pts)"
              ]
            }""";

    @Test
    void traduceElEventoPlano() {
        LeadCalificadoTraductor.Traducido traducido = traducir(EJEMPLO);

        CalificacionHistorica c = traducido.calificacion();
        assertThat(c.leadId()).isEqualTo(UUID.fromString(LEAD));
        assertThat(c.clienteId()).isEqualTo(UUID.fromString(CLIENTE));
        assertThat(c.puntaje()).isEqualTo(60);
        assertThat(c.nivel()).isEqualTo("MUY_POTABLE");
        assertThat(c.origen()).isEqualTo("AUTOMATICA");
        assertThat(c.calculadaEn()).isEqualTo(Instant.parse("2026-10-11T18:30:00Z"));
        assertThat(c.observacion()).isEqualTo("Calificación automática calculada por motor de reglas");
        assertThat(c.motivos()).hasSize(3).first().asString().startsWith("[REGLA_COBERTURA]");
        assertThat(traducido.correlationId()).isNull();
    }

    @Test
    void traduceElSobreComun() {
        LeadCalificadoTraductor.Traducido traducido = traducir("""
                {"eventId": "5d1e7c3a-2b4f-4a6e-9c8d-7e6f5a4b3c2d", "eventType": "LeadCalificado", "version": 1,
                 "occurredAt": "2026-10-11T18:30:01Z", "correlationId": "abc-123",
                 "payload": %s}""".formatted(EJEMPLO));

        assertThat(traducido.calificacion()).isEqualTo(traducir(EJEMPLO).calificacion());
        assertThat(traducido.correlationId()).isEqualTo("abc-123");
    }

    @Test
    void rechazaUnSobreDeOtroEvento() {
        assertInvalido("""
                {"eventType": "ClienteActualizado", "payload": %s}""".formatted(EJEMPLO), "eventType");
    }

    @Test
    void ignoraLosCamposQueNoConoce() {
        assertThat(traducir(EJEMPLO.replace("\"puntos\": 60,", "\"puntos\": 60, \"sucursal\": {\"id\": 3},"))
                .calificacion().puntaje()).isEqualTo(60);
    }

    @ParameterizedTest
    @ValueSource(strings = {"leadId", "clienteId", "puntos", "nivel", "origen", "fecha"})
    void exigeLosCamposObligatorios(String campo) {
        assertInvalido(sinCampo(campo), campo);
        assertInvalido(conValor(campo, "null"), campo);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "leadId    | \"no-es-uuid\"",
            "leadId    | 123",
            "clienteId | \"3fa85f64\"",
            "puntos    | \"60\"",
            "puntos    | 60.5",
            "puntos    | 99999999999",
            "nivel     | \"   \"",
            "nivel     | 3",
            "origen    | \"\"",
            "fecha     | \"11/10/2026 15:30\"",
            "fecha     | 1760196600",
            "observacion | 42",
            "motivos   | \"un texto\"",
            "motivos   | [\"ok\", 3]",
            "motivos   | [\"ok\", null]"})
    void rechazaTiposIncorrectos(String campo, String valor) {
        assertInvalido(conValor(campo, valor), campo);
    }

    @Test
    void rechazaTextosDemasiadoLargos() {
        assertInvalido(conValor("nivel", "\"" + "N".repeat(41) + "\""), "nivel");
        assertInvalido(conValor("origen", "\"" + "O".repeat(41) + "\""), "origen");
        assertInvalido(conValor("observacion", "\"" + "x".repeat(2001) + "\""), "observacion");
        assertThat(traducir(conValor("nivel", "\"" + "N".repeat(40) + "\"")).calificacion().nivel()).hasSize(40);
    }

    @Test
    void aceptaUnNivelDesconocidoYPuntosNegativos() {
        CalificacionHistorica c = traducir(conValor("nivel", "\"RECONTRA_POTABLE\"").replace("60", "-25"))
                .calificacion();

        assertThat(c.nivel()).isEqualTo("RECONTRA_POTABLE");
        assertThat(c.puntaje()).isEqualTo(-25);
    }

    @Test
    void sinMotivosNiObservacionQuedanVacios() {
        CalificacionHistorica sinCampos = traducir(sinCampo("observacion").replaceAll("(?s),\\s*\"motivos\": \\[.*]",
                "")).calificacion();
        CalificacionHistorica nulos = traducir(conValor("observacion", "null").replaceAll(
                "(?s)\"motivos\": \\[.*]", "\"motivos\": null")).calificacion();

        assertThat(sinCampos.motivos()).isEmpty();
        assertThat(sinCampos.observacion()).isNull();
        assertThat(nulos.motivos()).isEmpty();
        assertThat(nulos.observacion()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-11T15:30:00,        2026-10-11T18:30:00Z",
            "2026-10-11T15:30:00Z,       2026-10-11T15:30:00Z",
            "2026-10-11T15:30:00-03:00,  2026-10-11T18:30:00Z",
            "2026-10-11T15:30:00+02:00,  2026-10-11T13:30:00Z",
            "2026-10-11T15:30:00.123456789, 2026-10-11T18:30:00.123456Z"})
    void interpretaLaFecha(String fecha, String esperada) {
        assertThat(traducir(conValor("fecha", "\"" + fecha + "\"")).calificacion().calculadaEn())
                .isEqualTo(Instant.parse(esperada));
    }

    @Test
    void usaLaZonaConfigurada() {
        LeadCalificadoTraductor enUtc = new LeadCalificadoTraductor(new ObjectMapper(), ZoneId.of("UTC"));

        assertThat(enUtc.traducir(bytes(EJEMPLO)).calificacion().calculadaEn())
                .isEqualTo(Instant.parse("2026-10-11T15:30:00Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"hola", "", "[1, 2]", "\"texto\"", "{\"leadId\": "})
    void rechazaLoQueNoEsUnObjetoJson(String mensaje) {
        assertInvalido(mensaje, "mensaje");
    }

    @Test
    void rechazaUnMensajeNulo() {
        assertThatThrownBy(() -> traductor.traducir(null)).isInstanceOf(MensajeInvalidoException.class);
    }

    @Test
    void elErrorNombraElCampoPeroNoSuValor() {
        assertThatThrownBy(() -> traducir(conValor("clienteId", "\"secreto-que-no-es-uuid\"")))
                .isInstanceOf(MensajeInvalidoException.class)
                .hasMessageContaining("clienteId")
                .hasMessageNotContaining("secreto-que-no-es-uuid");
        assertThatThrownBy(() -> traducir(conValor("observacion", "\"" + "Anticipo ".repeat(300) + "\"")))
                .hasMessageNotContaining("Anticipo");
    }

    private LeadCalificadoTraductor.Traducido traducir(String mensaje) {
        return traductor.traducir(bytes(mensaje));
    }

    private void assertInvalido(String mensaje, String campo) {
        assertThatThrownBy(() -> traducir(mensaje))
                .isInstanceOf(MensajeInvalidoException.class)
                .satisfies(e -> assertThat(((MensajeInvalidoException) e).campo()).isEqualTo(campo));
    }

    private static String sinCampo(String campo) {
        return EJEMPLO.replaceAll("(?m)^\\s*\"" + campo + "\": [^\\[\\n]*,\\n", "");
    }

    /** Reemplaza el valor de un campo del ejemplo (para {@code motivos}, la lista entera). */
    private static String conValor(String campo, String valor) {
        if (campo.equals("motivos")) {
            return EJEMPLO.replaceAll("(?s)\"motivos\": \\[.*]", "\"motivos\": " + valor);
        }
        return EJEMPLO.replaceAll("(?m)(^\\s*\"" + campo + "\": )[^,\\n]*", "$1" + java.util.regex.Matcher
                .quoteReplacement(valor));
    }

    private static byte[] bytes(String texto) {
        return texto.getBytes(StandardCharsets.UTF_8);
    }
}
