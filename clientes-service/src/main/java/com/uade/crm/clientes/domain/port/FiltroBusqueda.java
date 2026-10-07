package com.uade.crm.clientes.domain.port;

import com.uade.crm.clientes.domain.valor.Email;
import com.uade.crm.clientes.domain.valor.NombreBusqueda;
import com.uade.crm.clientes.domain.valor.Telefono;
import com.uade.crm.contracts.Provincia;

/**
 * Filtros de la búsqueda de clientes activos, ya normalizados; un filtro en null no se aplica.
 * {@code nombre} está en la forma de {@link NombreBusqueda} y se busca como "contiene".
 */
public record FiltroBusqueda(Telefono telefono, Email email, String nombre, Provincia provincia) {

    public FiltroBusqueda {
        nombre = nombre == null || nombre.isBlank() ? null : nombre;
    }
}
