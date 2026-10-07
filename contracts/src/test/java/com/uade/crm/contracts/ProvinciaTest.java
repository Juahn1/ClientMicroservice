package com.uade.crm.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProvinciaTest {

    @Test
    void tieneLasVeinticuatroJurisdicciones() {
        assertThat(Provincia.values()).hasSize(24).contains(Provincia.CABA, Provincia.BUENOS_AIRES);
    }

    @Test
    void canalOrigenTieneLosValoresDelSeed() {
        assertThat(CanalOrigen.values()).extracting(Enum::name).containsExactly(
                "INSTAGRAM", "TIKTOK", "MARKETPLACE", "FACEBOOK", "META_ADS", "WHATSAPP", "REFERIDO", "OTRO");
    }
}
