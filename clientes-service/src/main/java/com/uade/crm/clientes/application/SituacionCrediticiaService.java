package com.uade.crm.clientes.application;

import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.SituacionCrediticia;
import com.uade.crm.clientes.domain.port.CifradorCuil;
import com.uade.crm.clientes.domain.port.ConsultaSituacion;
import com.uade.crm.clientes.domain.port.SituacionCrediticiaPort;
import com.uade.crm.clientes.domain.valor.CuilProtegido;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Situación crediticia de un cliente para {@code calificacion} (design D2 y D8 del change 05). Resuelve las reglas que
 * necesitan el agregado (404, 410, sin CUIL) y delega el resto en la cadena de {@link SituacionCrediticiaPort}.
 *
 * <p>No es transaccional a propósito: el cliente se lee en la transacción corta de {@link ConsultaClienteService} y la
 * fuente se consulta sin ninguna conexión tomada. {@code SIN_CONSENTIMIENTO} no tiene rama: con la invariante del
 * agregado (CUIL si y solo si hay consentimiento de precalificación vigente) no puede ocurrir hasta que exista la
 * revocación (backlog {@code clientes-15}).
 */
@Service
public class SituacionCrediticiaService {

    private static final Logger log = LoggerFactory.getLogger(SituacionCrediticiaService.class);

    private final ConsultaClienteService clientes;
    private final CifradorCuil cifrador;
    private final SituacionCrediticiaPort situaciones;

    public SituacionCrediticiaService(ConsultaClienteService clientes, CifradorCuil cifrador,
            SituacionCrediticiaPort situaciones) {
        this.clientes = clientes;
        this.cifrador = cifrador;
        this.situaciones = situaciones;
    }

    public SituacionCrediticia consultar(UUID clienteId) {
        Cliente cliente = clientes.obtener(clienteId);
        Optional<CuilProtegido> cuil = cliente.cuil();
        SituacionCrediticia situacion = cuil.isEmpty()
                ? SituacionCrediticia.sinCuil()
                : situaciones.consultar(new ConsultaSituacion(clienteId, cifrador.descifrar(cuil.get())));
        log.info("Situación crediticia del cliente {}: {}", clienteId, situacion.estado());
        return situacion;
    }
}
