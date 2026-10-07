package com.uade.crm.clientes.infrastructure.persistence;

import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.EstadoCliente;
import com.uade.crm.clientes.domain.model.Pagina;
import com.uade.crm.clientes.domain.port.ClienteRepositorio;
import com.uade.crm.clientes.domain.port.FiltroBusqueda;
import com.uade.crm.clientes.domain.valor.Email;
import com.uade.crm.clientes.domain.valor.Telefono;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** Adaptador del puerto {@link ClienteRepositorio} sobre Spring Data JPA: traduce entre entidad y dominio. */
@Repository
class ClienteRepositorioJpa implements ClienteRepositorio {

    private final ClienteJpaRepository jpa;

    ClienteRepositorioJpa(ClienteJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Cliente> buscarActivoPorEmail(Email email) {
        return jpa.findByEmailAndEstado(email.valor(), EstadoCliente.ACTIVO).map(ClienteRepositorioJpa::aDominio);
    }

    @Override
    public Optional<Cliente> buscarActivoPorEmailParaActualizar(Email email) {
        return jpa.findParaActualizar(email.valor(), EstadoCliente.ACTIVO).map(ClienteRepositorioJpa::aDominio);
    }

    @Override
    public Optional<Cliente> buscarActivoPorId(UUID id) {
        return jpa.findByIdAndEstado(id, EstadoCliente.ACTIVO).map(ClienteRepositorioJpa::aDominio);
    }

    /** Orden estable para paginar: último contacto descendente y el id como desempate. */
    @Override
    public Pagina<Cliente> buscar(FiltroBusqueda filtro, int pagina, int tamanio) {
        Sort orden = Sort.by(Sort.Order.desc("ultimoContactoEn"), Sort.Order.asc("id"));
        Page<ClienteEntity> resultado = jpa.findAll(ClienteSpecs.de(filtro), PageRequest.of(pagina, tamanio, orden));
        return new Pagina<>(resultado.getContent().stream().map(ClienteRepositorioJpa::aDominio).toList(),
                resultado.getNumber(), resultado.getSize(), resultado.getTotalElements(), resultado.getTotalPages());
    }

    /** {@code saveAndFlush} para que la violación del índice único aparezca acá y no al commit. */
    @Override
    public Cliente guardar(Cliente cliente) {
        return aDominio(jpa.saveAndFlush(aEntidad(cliente)));
    }

    static ClienteEntity aEntidad(Cliente c) {
        ClienteEntity e = new ClienteEntity();
        e.setId(c.id());
        e.setNombre(c.nombre());
        e.setApellido(c.apellido());
        e.setNombreBusqueda(c.nombreBusqueda().valor());
        e.setEmail(c.email() == null ? null : c.email().valor());
        e.setTelefonoE164(c.telefono().map(Telefono::e164).orElse(null));
        e.setTelefonoAlternativo(c.telefonoAlternativo().map(Telefono::e164).orElse(null));
        e.setProvincia(c.provincia());
        e.setLocalidad(c.localidad().orElse(null));
        e.setCanalOrigen(c.canalOrigen().orElse(null));
        e.setUltimoCanal(c.ultimoCanal().orElse(null));
        e.setContactosRegistrados(c.contactosRegistrados());
        e.setUltimoContactoEn(c.ultimoContactoEn());
        e.setRequiereRevision(c.requiereRevision());
        e.setMotivoRevision(c.motivoRevision().orElse(null));
        e.setEstado(c.estado());
        e.setVersion(c.version().orElse(null));
        e.setCreadoEn(c.creadoEn());
        e.setActualizadoEn(c.actualizadoEn());
        e.setAnonimizadoEn(c.anonimizadoEn().orElse(null));
        return e;
    }

    static Cliente aDominio(ClienteEntity e) {
        return Cliente.builder()
                .id(e.getId())
                .nombre(e.getNombre())
                .apellido(e.getApellido())
                .email(e.getEmail() == null ? null : Email.de(e.getEmail()))
                .telefono(e.getTelefonoE164() == null ? null : Telefono.deE164(e.getTelefonoE164()))
                .telefonoAlternativo(e.getTelefonoAlternativo() == null ? null
                        : Telefono.deE164(e.getTelefonoAlternativo()))
                .provincia(e.getProvincia())
                .localidad(e.getLocalidad())
                .canalOrigen(e.getCanalOrigen())
                .ultimoCanal(e.getUltimoCanal())
                .contactosRegistrados(e.getContactosRegistrados())
                .ultimoContactoEn(e.getUltimoContactoEn())
                .requiereRevision(e.isRequiereRevision())
                .motivoRevision(e.getMotivoRevision())
                .estado(e.getEstado())
                .version(e.getVersion())
                .creadoEn(e.getCreadoEn())
                .actualizadoEn(e.getActualizadoEn())
                .anonimizadoEn(e.getAnonimizadoEn())
                .build();
    }
}
