package com.uade.crm.clientes.infrastructure.persistence;

import com.uade.crm.clientes.domain.model.SituacionCrediticia;
import com.uade.crm.clientes.domain.port.CacheSituacionCrediticia;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador del puerto {@link CacheSituacionCrediticia} sobre la tabla {@code situacion_crediticia} (design D4). */
@Repository
class CacheSituacionCrediticiaJpa implements CacheSituacionCrediticia {

    static final DateTimeFormatter PERIODO = DateTimeFormatter.ofPattern("yyyyMM");

    private final SituacionCrediticiaJpaRepository jpa;

    CacheSituacionCrediticiaJpa(SituacionCrediticiaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SituacionCrediticia> vigente(UUID clienteId, Instant ahora) {
        return jpa.findByClienteIdAndVigenteHastaAfter(clienteId, ahora).map(CacheSituacionCrediticiaJpa::aDominio);
    }

    /**
     * Transacción propia y corta: la consulta a la fuente ya terminó, y un error al guardar no debe deshacer nada más.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void guardar(UUID clienteId, SituacionCrediticia situacion, Instant vigenteHasta) {
        if (!situacion.esCacheable()) {
            throw new IllegalArgumentException("Solo se guardan resultados de la fuente: " + situacion.estado());
        }
        jpa.guardar(clienteId, situacion.estado().name(), situacion.situacion(), PERIODO.format(situacion.periodo()),
                situacion.fuente().name(), situacion.consultadaEn(), vigenteHasta);
    }

    @Override
    @Transactional
    public void eliminar(UUID clienteId) {
        jpa.eliminar(clienteId);
    }

    private static SituacionCrediticia aDominio(SituacionCrediticiaEntity e) {
        Integer situacion = e.getSituacion() == null ? null : e.getSituacion().intValue();
        return new SituacionCrediticia(e.getEstado(), situacion, YearMonth.parse(e.getPeriodo(), PERIODO),
                e.getFuente(), e.getConsultadaEn());
    }
}
