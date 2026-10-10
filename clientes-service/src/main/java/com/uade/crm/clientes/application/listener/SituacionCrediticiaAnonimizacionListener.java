package com.uade.crm.clientes.application.listener;

import com.uade.crm.clientes.domain.event.ClienteAnonimizadoEvento;
import com.uade.crm.clientes.domain.port.CacheSituacionCrediticia;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Borra la situación crediticia guardada de un cliente que se anonimiza, en la misma transacción (design D9 del
 * change 05, previsto en el D6 del change 03). Si el borrado falla, la anonimización completa se revierte. La caché vive
 * fuera del agregado, por eso no la alcanza {@code Cliente.anonimizar}.
 */
@Component
public class SituacionCrediticiaAnonimizacionListener {

    private final CacheSituacionCrediticia cache;

    public SituacionCrediticiaAnonimizacionListener(CacheSituacionCrediticia cache) {
        this.cache = cache;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void clienteAnonimizado(ClienteAnonimizadoEvento evento) {
        cache.eliminar(evento.clienteId());
    }
}
