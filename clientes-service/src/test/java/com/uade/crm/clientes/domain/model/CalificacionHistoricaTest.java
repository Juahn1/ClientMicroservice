package com.uade.crm.clientes.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CalificacionHistoricaTest {

    private static final Instant CALCULADA = Instant.parse("2026-10-11T18:30:00Z");

    @Test
    void toStringNoIncluyeMotivosNiObservacion() {
        CalificacionHistorica calificacion = new CalificacionHistorica(UUID.randomUUID(), UUID.randomUUID(),
                "MUY_POTABLE", 60, "AUTOMATICA", CALCULADA, "Anticipo confirmado",
                List.of("[REGLA_COBERTURA]: Cobertura aportada: 60.00%"));

        assertThat(calificacion.toString())
                .contains("MUY_POTABLE", calificacion.leadId().toString())
                .doesNotContain("Anticipo confirmado")
                .doesNotContain("Cobertura aportada");
    }

    @Test
    void losMotivosNoSePuedenModificarNiDependenDeLaListaOriginal() {
        List<String> original = new ArrayList<>(List.of("uno"));
        CalificacionHistorica calificacion = new CalificacionHistorica(UUID.randomUUID(), UUID.randomUUID(),
                "POTABLE", 35, "MANUAL", CALCULADA, null, original);

        original.add("dos");

        assertThat(calificacion.motivos()).containsExactly("uno");
        assertThatThrownBy(() -> calificacion.motivos().add("tres"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void sinMotivosQuedaUnaListaVacia() {
        CalificacionHistorica calificacion = new CalificacionHistorica(UUID.randomUUID(), UUID.randomUUID(),
                "NO_POTABLE", -25, "AUTOMATICA", CALCULADA, null, null);

        assertThat(calificacion.motivos()).isEmpty();
    }

    @Test
    void exigeLosCamposObligatorios() {
        assertThatNullPointerException().isThrownBy(() -> new CalificacionHistorica(UUID.randomUUID(), null,
                "POTABLE", 35, "MANUAL", CALCULADA, null, List.of()));
        assertThatNullPointerException().isThrownBy(() -> new CalificacionHistorica(UUID.randomUUID(),
                UUID.randomUUID(), "POTABLE", 35, "MANUAL", null, null, List.of()));
    }
}
