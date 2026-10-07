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

    Optional<Cliente> buscarActivoPorId(UUID id);

    /** Clientes activos que cumplen todos los filtros, del último contacto más reciente al más viejo. */
    Pagina<Cliente> buscar(FiltroBusqueda filtro, int pagina, int tamanio);

    /**
     * Inserta o actualiza el cliente y devuelve la versión guardada. Si ya hay otro cliente activo con el mismo
     * email lanza {@code DataIntegrityViolationException}; si la versión quedó desactualizada,
     * {@code OptimisticLockingFailureException}.
     */
    Cliente guardar(Cliente cliente);
}
