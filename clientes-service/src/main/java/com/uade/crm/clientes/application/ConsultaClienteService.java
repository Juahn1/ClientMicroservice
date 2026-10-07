package com.uade.crm.clientes.application;

import com.uade.crm.clientes.config.ClientesProperties;
import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.ClienteNoEncontradoException;
import com.uade.crm.clientes.domain.model.Pagina;
import com.uade.crm.clientes.domain.port.ClienteRepositorio;
import com.uade.crm.clientes.domain.port.FiltroBusqueda;
import com.uade.crm.clientes.domain.valor.Email;
import com.uade.crm.clientes.domain.valor.NombreBusqueda;
import com.uade.crm.clientes.domain.valor.Telefono;
import com.uade.crm.contracts.Provincia;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta de la ficha y búsqueda de clientes activos (design D7 y D8). */
@Service
@Transactional(readOnly = true)
public class ConsultaClienteService {

    private final ClienteRepositorio repositorio;
    private final ClientesProperties properties;

    public ConsultaClienteService(ClienteRepositorio repositorio, ClientesProperties properties) {
        this.repositorio = repositorio;
        this.properties = properties;
    }

    /** Un cliente que no existe o que no está activo responde igual: 404 (el 03 decide entre 410 y 404). */
    public Cliente obtener(UUID id) {
        return repositorio.buscarActivoPorId(id).orElseThrow(() -> new ClienteNoEncontradoException(id));
    }

    /**
     * Normaliza los filtros con las mismas reglas del alta. Un teléfono informado que no se puede normalizar lanza
     * {@code TelefonoInvalidoException}; un tamaño mayor al máximo configurado se limita sin avisar.
     */
    public Pagina<Cliente> buscar(String telefono, String email, String q, Provincia provincia, int pagina,
            Integer tamanio) {
        FiltroBusqueda filtro = new FiltroBusqueda(
                Telefono.opcional(telefono, properties.telefono().regionPorDefecto()).orElse(null),
                email == null || email.isBlank() ? null : Email.de(email),
                NombreBusqueda.normalizar(q),
                provincia);
        return repositorio.buscar(filtro, pagina, tamanioEfectivo(tamanio));
    }

    private int tamanioEfectivo(Integer pedido) {
        ClientesProperties.Paginacion paginacion = properties.paginacion();
        return pedido == null ? paginacion.tamanioPorDefecto() : Math.min(pedido, paginacion.tamanioMaximo());
    }
}
