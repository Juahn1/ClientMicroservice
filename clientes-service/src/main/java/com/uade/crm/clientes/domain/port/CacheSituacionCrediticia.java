package com.uade.crm.clientes.domain.port;

import com.uade.crm.clientes.domain.model.SituacionCrediticia;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Resultados de la fuente guardados por cliente, en la base del servicio para que las réplicas los compartan (S-01,
 * design D4 del change 05). Solo guarda estados cacheables.
 */
public interface CacheSituacionCrediticia {

    /** El resultado guardado del cliente si sigue vigente en {@code ahora}. */
    Optional<SituacionCrediticia> vigente(UUID clienteId, Instant ahora);

    /**
     * Guarda o reemplaza el resultado del cliente, en su propia transacción. No guarda nada si el cliente ya no está
     * activo (por ejemplo, si se anonimizó mientras se consultaba la fuente).
     */
    void guardar(UUID clienteId, SituacionCrediticia situacion, Instant vigenteHasta);

    /** Borra el resultado del cliente, si lo hay, en la transacción en curso. */
    void eliminar(UUID clienteId);
}
