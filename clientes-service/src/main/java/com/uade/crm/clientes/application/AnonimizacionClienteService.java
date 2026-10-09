package com.uade.crm.clientes.application;

import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.ClienteNoEncontradoException;
import com.uade.crm.clientes.domain.port.ClienteRepositorio;
import com.uade.crm.clientes.domain.valor.Email;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anonimización a pedido del titular (Ley 25.326, design D6 del change 03). Exige el email del cliente como
 * confirmación y es idempotente. Publica {@code ClienteAnonimizadoEvento} dentro de la transacción: los módulos que
 * guarden datos del cliente (CUIL, consentimientos, situación crediticia) los borran en la misma transacción
 * escuchando ese evento, sin que este servicio los conozca; el envío a Kafka ocurre después del commit.
 */
@Service
public class AnonimizacionClienteService {

    private static final Logger log = LoggerFactory.getLogger(AnonimizacionClienteService.class);

    private final ClienteRepositorio repositorio;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    public AnonimizacionClienteService(ClienteRepositorio repositorio, ApplicationEventPublisher eventos,
            Clock reloj) {
        this.repositorio = repositorio;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /** Un cliente ya anonimizado no se vuelve a comparar ni publica: ya no tiene email contra el cual comparar. */
    @Transactional
    public void anonimizar(UUID id, String emailConfirmacion) {
        Cliente cliente = repositorio.buscarPorIdParaActualizar(id)
                .orElseThrow(() -> new ClienteNoEncontradoException(id));
        if (!cliente.activo()) {
            return;
        }
        if (!Email.de(emailConfirmacion).equals(cliente.email())) {
            throw new ConfirmacionInvalidaException(id);
        }
        Instant ahora = reloj.instant();
        cliente.anonimizar(ahora);
        repositorio.guardar(cliente);
        eventos.publishEvent(EventosCliente.anonimizado(id, ahora));
        log.info("Cliente {} anonimizado", id);
    }
}
