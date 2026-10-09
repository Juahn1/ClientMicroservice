package com.uade.crm.clientes.infrastructure.persistence;

import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.EstadoCliente;
import com.uade.crm.clientes.domain.model.Pagina;
import com.uade.crm.clientes.domain.port.ClienteRepositorio;
import com.uade.crm.clientes.domain.port.EmailDuplicadoException;
import com.uade.crm.clientes.domain.port.FiltroBusqueda;
import com.uade.crm.clientes.domain.valor.Email;
import com.uade.crm.clientes.domain.valor.Telefono;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** Adaptador del puerto {@link ClienteRepositorio} sobre Spring Data JPA: traduce entre entidad y dominio. */
@Repository
class ClienteRepositorioJpa implements ClienteRepositorio {

    static final String INDICE_EMAIL = "ux_cliente_email_activo";

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

    @Override
    public Optional<Cliente> buscarPorId(UUID id) {
        return jpa.findById(id).map(ClienteRepositorioJpa::aDominio);
    }

    @Override
    public Optional<Cliente> buscarPorIdParaActualizar(UUID id) {
        return jpa.findPorIdParaActualizar(id).map(ClienteRepositorioJpa::aDominio);
    }

    /** Orden estable para paginar: último contacto descendente y el id como desempate. */
    @Override
    public Pagina<Cliente> buscar(FiltroBusqueda filtro, int pagina, int tamanio) {
        Sort orden = Sort.by(Sort.Order.desc("ultimoContactoEn"), Sort.Order.asc("id"));
        Page<ClienteEntity> resultado = jpa.findAll(ClienteSpecs.de(filtro), PageRequest.of(pagina, tamanio, orden));
        return new Pagina<>(resultado.getContent().stream().map(ClienteRepositorioJpa::aDominio).toList(),
                resultado.getNumber(), resultado.getSize(), resultado.getTotalElements(), resultado.getTotalPages());
    }

    /**
     * {@code saveAndFlush} para que la violación del índice único aparezca acá y no al commit. Solo la violación de
     * {@value #INDICE_EMAIL} se traduce a {@link EmailDuplicadoException}; las demás siguen siendo inesperadas.
     */
    @Override
    public Cliente guardar(Cliente cliente) {
        try {
            return aDominio(jpa.saveAndFlush(aEntidad(cliente)));
        } catch (DataIntegrityViolationException violacion) {
            if (violaIndiceEmail(violacion)) {
                throw new EmailDuplicadoException(violacion);
            }
            throw violacion;
        }
    }

    /** Hibernate informa el nombre de la restricción; si no lo extrajo, se busca en el mensaje del driver. */
    static boolean violaIndiceEmail(DataIntegrityViolationException violacion) {
        for (Throwable causa = violacion; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException restriccion
                    && INDICE_EMAIL.equalsIgnoreCase(restriccion.getConstraintName())) {
                return true;
            }
            if (causa.getMessage() != null && causa.getMessage().contains(INDICE_EMAIL)) {
                return true;
            }
        }
        return false;
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
        e.setNoContactar(c.noContactar());
        e.setNoContactarDesde(c.noContactarDesde().orElse(null));
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
                .noContactar(e.isNoContactar())
                .noContactarDesde(e.getNoContactarDesde())
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
