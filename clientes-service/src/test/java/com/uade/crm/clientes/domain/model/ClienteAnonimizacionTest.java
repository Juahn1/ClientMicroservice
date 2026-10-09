package com.uade.crm.clientes.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Requirement: Anonimización a pedido del titular (reglas del dominio, design D6 del change 03). */
class ClienteAnonimizacionTest {

    private static final Instant BAJA = Instant.parse("2026-10-08T12:00:00Z");
    private static final Instant DESPUES = Instant.parse("2026-10-09T09:00:00Z");

    @Test
    void datosConservadosYEliminados() {
        Cliente martina = ClienteEdicionTest.martina();
        martina.marcarNoContactar(BAJA);
        var id = martina.id();
        var creadoEn = martina.creadoEn();

        assertThat(martina.anonimizar(BAJA)).isTrue();

        assertThat(martina.estado()).isEqualTo(EstadoCliente.ANONIMIZADO);
        assertThat(martina.nombre()).isEqualTo(Cliente.ANONIMIZADO);
        assertThat(martina.apellido()).isEqualTo(Cliente.ANONIMIZADO);
        assertThat(martina.nombreBusqueda().valor()).isEqualTo("anonimizado anonimizado");
        assertThat(martina.email()).isNull();
        assertThat(martina.telefono()).isEmpty();
        assertThat(martina.telefonoAlternativo()).isEmpty();
        assertThat(martina.localidad()).isEmpty();
        assertThat(martina.anonimizadoEn()).contains(BAJA);

        assertThat(martina.id()).isEqualTo(id);
        assertThat(martina.provincia()).isEqualTo(Provincia.BUENOS_AIRES);
        assertThat(martina.canalOrigen()).contains(CanalOrigen.INSTAGRAM);
        assertThat(martina.ultimoCanal()).contains(CanalOrigen.WHATSAPP);
        assertThat(martina.contactosRegistrados()).isEqualTo(3);
        assertThat(martina.creadoEn()).isEqualTo(creadoEn);
        assertThat(martina.noContactar()).isTrue();
    }

    @Test
    void anonimizarDosVecesNoCambiaNada() {
        Cliente martina = ClienteEdicionTest.martina();
        martina.anonimizar(BAJA);

        assertThat(martina.anonimizar(DESPUES)).isFalse();
        assertThat(martina.anonimizadoEn()).contains(BAJA);
        assertThat(martina.actualizadoEn()).isEqualTo(BAJA);
    }
}
