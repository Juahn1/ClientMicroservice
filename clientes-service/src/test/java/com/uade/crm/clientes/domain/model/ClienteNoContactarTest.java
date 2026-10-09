package com.uade.crm.clientes.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.uade.crm.clientes.domain.valor.Email;
import com.uade.crm.contracts.Provincia;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Requirement: Marca no contactar (reglas del dominio, design D2 y D5 del change 03). */
class ClienteNoContactarTest {

    private static final Instant MARCA = Instant.parse("2026-10-08T12:00:00Z");
    private static final Instant DESPUES = Instant.parse("2026-10-09T09:00:00Z");

    @Test
    void marcarGuardaLaFechaYConservaLosDatos() {
        Cliente martina = ClienteEdicionTest.martina();

        assertThat(martina.marcarNoContactar(MARCA)).isTrue();

        assertThat(martina.noContactar()).isTrue();
        assertThat(martina.noContactarDesde()).contains(MARCA);
        assertThat(martina.activo()).isTrue();
        assertThat(martina.email()).isEqualTo(Email.de("martina@gmail.com"));
        assertThat(martina.actualizadoEn()).isEqualTo(MARCA);
    }

    @Test
    void marcarDosVecesConservaLaFechaOriginal() {
        Cliente martina = ClienteEdicionTest.martina();
        martina.marcarNoContactar(MARCA);

        assertThat(martina.marcarNoContactar(DESPUES)).isFalse();

        assertThat(martina.noContactarDesde()).contains(MARCA);
        assertThat(martina.actualizadoEn()).isEqualTo(MARCA);
    }

    @Test
    void quitarLaMarca() {
        Cliente martina = ClienteEdicionTest.martina();
        martina.marcarNoContactar(MARCA);

        assertThat(martina.quitarNoContactar(DESPUES)).isTrue();

        assertThat(martina.noContactar()).isFalse();
        assertThat(martina.noContactarDesde()).isEmpty();
    }

    @Test
    void quitarUnaMarcaInexistenteNoHaceNada() {
        Cliente martina = ClienteEdicionTest.martina();

        assertThat(martina.quitarNoContactar(DESPUES)).isFalse();
        assertThat(martina.actualizadoEn()).isNotEqualTo(DESPUES);
    }

    @Test
    void unRecontactoQuitaLaMarca() {
        Cliente martina = ClienteEdicionTest.martina();
        martina.marcarNoContactar(MARCA);

        martina.registrarRecontacto(new SolicitudAlta("Martina", "López", Email.de("martina@gmail.com"), null,
                Provincia.BUENOS_AIRES, null, null), DESPUES);

        assertThat(martina.noContactar()).isFalse();
        assertThat(martina.noContactarDesde()).isEmpty();
    }

    @Test
    void unClienteAnonimizadoNoSeMarca() {
        Cliente martina = ClienteEdicionTest.martina();
        martina.anonimizar(MARCA);

        assertThatIllegalStateException().isThrownBy(() -> martina.marcarNoContactar(DESPUES));
        assertThatIllegalStateException().isThrownBy(() -> martina.quitarNoContactar(DESPUES));
    }

    @Test
    void laMarcaYSuFechaVanJuntas() {
        assertThatIllegalArgumentException().isThrownBy(() -> Cliente.builder().id(UUID.randomUUID())
                .nombre("Martina").apellido("López").email(Email.de("martina@gmail.com"))
                .provincia(Provincia.CABA).noContactar(true)
                .ultimoContactoEn(MARCA).creadoEn(MARCA).actualizadoEn(MARCA).build());
    }
}
