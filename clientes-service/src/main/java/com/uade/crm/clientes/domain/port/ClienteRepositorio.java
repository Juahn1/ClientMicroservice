package com.uade.crm.clientes.domain.port;

import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.Pagina;
import com.uade.crm.clientes.domain.valor.Email;
import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia de clientes; lo implementa la infraestructura (patrón Repository). */
public interface ClienteRepositorio {

    Optional<Cliente> buscarActivoPorEmail(Email email);

    /**
     * Igual que {@link #buscarActivoPorEmail} pero bloquea la fila hasta el fin de la transacción: los recontactos
     * simultáneos de la misma persona se ejecutan en fila en lugar de fallar por control optimista.
     */
    Optional<Cliente> buscarActivoPorEmailParaActualizar(Email email);

    /**
     * Cliente activo con ese índice ciego de CUIL, con la fila bloqueada hasta el fin de la transacción (design D5 del
     * change 04). Recibe el hash, nunca el CUIL.
     */
    Optional<Cliente> buscarActivoPorCuilParaActualizar(String cuilHash);

    Optional<Cliente> buscarActivoPorId(UUID id);

    /** Cliente en cualquier estado: distingue un ID inexistente (404) de uno anonimizado (410). */
    Optional<Cliente> buscarPorId(UUID id);

    /**
     * Igual que {@link #buscarPorId} pero bloquea la fila hasta el fin de la transacción: las operaciones sin
     * {@code If-Match} (marca no contactar, anonimización) esperan a un recontacto en curso en lugar de fallar por
     * control optimista.
     */
    Optional<Cliente> buscarPorIdParaActualizar(UUID id);

    /** Clientes activos que cumplen todos los filtros, del último contacto más reciente al más viejo. */
    Pagina<Cliente> buscar(FiltroBusqueda filtro, int pagina, int tamanio);

    /**
     * Inserta o actualiza el cliente y devuelve la versión guardada. Si ya hay otro cliente activo con el mismo
     * email o el mismo CUIL lanza {@link ClaveDuplicadaException}; si la versión quedó desactualizada,
     * {@code OptimisticLockingFailureException}.
     */
    Cliente guardar(Cliente cliente);
}
