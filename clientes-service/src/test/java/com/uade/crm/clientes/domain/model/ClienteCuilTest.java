package com.uade.crm.clientes.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.uade.crm.clientes.CuilDePrueba;
import com.uade.crm.clientes.domain.valor.CuilProtegido;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Reglas del agregado sobre CUIL, consentimiento y marca de revisión (design D3 y D5 del change 04). */
class ClienteCuilTest {

    private static final Instant OTORGADO = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant DESPUES = Instant.parse("2026-10-09T09:00:00Z");
    private static final CuilProtegido CUIL = CuilDePrueba.protegido("20-12345678-6");

    @Test
    void registrarCuilAgregaElConsentimientoDePrecalificacion() {
        Cliente martina = ClienteEdicionTest.martina();

        martina.registrarCuil(CUIL, "v1", OTORGADO);

        assertThat(martina.cuil()).contains(CUIL);
        assertThat(martina.consentimientosVigentes()).singleElement().satisfies(c -> {
            assertThat(c.finalidad()).isEqualTo(FinalidadConsentimiento.PRECALIFICACION_CREDITICIA);
            assertThat(c.versionTexto()).isEqualTo("v1");
            assertThat(c.origen()).isEqualTo(OrigenConsentimiento.PREFORMULARIO);
            assertThat(c.otorgadoEn()).isEqualTo(OTORGADO);
        });
        assertThat(martina.actualizadoEn()).isEqualTo(OTORGADO);
    }

    @Test
    void noSeReemplazaUnCuilRegistrado() {
        Cliente martina = ClienteEdicionTest.martina();
        martina.registrarCuil(CUIL, "v1", OTORGADO);

        assertThatIllegalStateException()
                .isThrownBy(() -> martina.registrarCuil(CuilDePrueba.protegido("27-30111222-5"), "v1", DESPUES));
        assertThat(martina.cuil()).contains(CUIL);
        assertThat(martina.consentimientos()).hasSize(1);
    }

    @Test
    void laReconstruccionExigeCuilSiYSoloSiHayConsentimientoVigente() {
        Consentimiento vigente = new Consentimiento(UUID.randomUUID(),
                FinalidadConsentimiento.PRECALIFICACION_CREDITICIA, "v1", OrigenConsentimiento.PREFORMULARIO, OTORGADO,
                null);

        assertThatIllegalArgumentException().isThrownBy(() -> ClienteEdicionTest.martinaBuilder().cuil(CUIL).build());
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ClienteEdicionTest.martinaBuilder().consentimientos(List.of(vigente)).build());
        assertThat(ClienteEdicionTest.martinaBuilder().cuil(CUIL).consentimientos(List.of(vigente)).build().cuil())
                .contains(CUIL);
    }

    @Test
    void anonimizarBorraElCuilYRevocaLosConsentimientos() {
        Cliente martina = ClienteEdicionTest.martina();
        martina.registrarCuil(CUIL, "v1", OTORGADO);

        martina.anonimizar(DESPUES);

        assertThat(martina.cuil()).isEmpty();
        assertThat(martina.consentimientosVigentes()).isEmpty();
        assertThat(martina.consentimientos()).singleElement()
                .satisfies(c -> assertThat(c.revocadoEn()).contains(DESPUES));
    }

    @Test
    void marcarRevisionConservaElPrimerMotivo() {
        Cliente martina = ClienteEdicionTest.martina();

        assertThat(martina.marcarRevision(MotivoRevision.CUIL_DISTINTO, OTORGADO)).isTrue();
        assertThat(martina.marcarRevision(MotivoRevision.POSIBLE_DUPLICADO, DESPUES)).isFalse();

        assertThat(martina.requiereRevision()).isTrue();
        assertThat(martina.motivoRevision()).contains(MotivoRevision.CUIL_DISTINTO);
        assertThat(martina.actualizadoEn()).isEqualTo(OTORGADO);
    }

    @Test
    void resolverRevisionEsIdempotente() {
        Cliente martina = ClienteEdicionTest.martina();
        martina.marcarRevision(MotivoRevision.POSIBLE_DUPLICADO, OTORGADO);

        assertThat(martina.resolverRevision(DESPUES)).isTrue();
        assertThat(martina.requiereRevision()).isFalse();
        assertThat(martina.motivoRevision()).isEmpty();
        assertThat(martina.resolverRevision(DESPUES.plusSeconds(60))).isFalse();
        assertThat(martina.actualizadoEn()).isEqualTo(DESPUES);
    }
}
