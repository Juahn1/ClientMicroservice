package com.uade.crm.clientes.infrastructure.persistence;

import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.Consentimiento;
import com.uade.crm.clientes.domain.model.EstadoCliente;
import com.uade.crm.clientes.domain.model.MotivoRevision;
import com.uade.crm.clientes.domain.model.Pagina;
import com.uade.crm.clientes.domain.port.ClienteRepositorio;
import com.uade.crm.clientes.domain.port.ClaveDuplicadaException;
import com.uade.crm.clientes.domain.port.ClaveDuplicadaException.Clave;
import com.uade.crm.clientes.domain.port.FiltroBusqueda;
import com.uade.crm.clientes.domain.valor.CuilProtegido;
import com.uade.crm.clientes.domain.valor.Email;
import com.uade.crm.clientes.domain.valor.Telefono;
import java.util.ArrayList;
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
    static final String INDICE_CUIL = "ux_cliente_cuil_activo";

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
    public Optional<Cliente> buscarActivoPorCuilParaActualizar(String cuilHash) {
        return jpa.findPorCuilParaActualizar(cuilHash, EstadoCliente.ACTIVO).map(ClienteRepositorioJpa::aDominio);
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
     * {@code saveAndFlush} para que la violación del índice único aparezca acá y no al commit. Solo las violaciones
     * de {@value #INDICE_EMAIL} y {@value #INDICE_CUIL} se traducen a {@link ClaveDuplicadaException}; las demás
     * siguen siendo inesperadas.
     */
    @Override
    public Cliente guardar(Cliente cliente) {
        try {
            return aDominio(jpa.saveAndFlush(aEntidad(cliente)));
        } catch (DataIntegrityViolationException violacion) {
            if (violaIndice(violacion, INDICE_EMAIL)) {
                throw new ClaveDuplicadaException(Clave.EMAIL, violacion);
            }
            if (violaIndice(violacion, INDICE_CUIL)) {
                throw new ClaveDuplicadaException(Clave.CUIL, violacion);
            }
            throw violacion;
        }
    }

    /** Hibernate informa el nombre de la restricción; si no lo extrajo, se busca en el mensaje del driver. */
    static boolean violaIndice(DataIntegrityViolationException violacion, String indice) {
        for (Throwable causa = violacion; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException restriccion
                    && indice.equalsIgnoreCase(restriccion.getConstraintName())) {
                return true;
            }
            if (causa.getMessage() != null && causa.getMessage().contains(indice)) {
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
        e.setMotivoRevision(c.motivoRevision().map(Enum::name).orElse(null));
        CuilProtegido cuil = c.cuil().orElse(null);
        e.setCuilCifrado(cuil == null ? null : cuil.cifrado());
        e.setCuilHash(cuil == null ? null : cuil.hash());
        e.setCuilUltimos4(cuil == null ? null : cuil.ultimos4());
        e.setConsentimientos(
                new ArrayList<>(c.consentimientos().stream().map(ClienteRepositorioJpa::aEntidad).toList()));
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
                .motivoRevision(e.getMotivoRevision() == null ? null : MotivoRevision.valueOf(e.getMotivoRevision()))
                .cuil(e.getCuilHash() == null ? null
                        : new CuilProtegido(e.getCuilCifrado(), e.getCuilHash(), e.getCuilUltimos4()))
                .consentimientos(e.getConsentimientos().stream().map(ClienteRepositorioJpa::aDominio).toList())
                .estado(e.getEstado())
                .version(e.getVersion())
                .creadoEn(e.getCreadoEn())
                .actualizadoEn(e.getActualizadoEn())
                .anonimizadoEn(e.getAnonimizadoEn())
                .build();
    }

    static ConsentimientoEntity aEntidad(Consentimiento c) {
        return new ConsentimientoEntity(c.id(), c.finalidad(), c.versionTexto(), c.origen(), c.otorgadoEn(),
                c.revocadoEn().orElse(null));
    }

    static Consentimiento aDominio(ConsentimientoEntity e) {
        return new Consentimiento(e.getId(), e.getFinalidad(), e.getVersionTexto(), e.getOrigen(), e.getOtorgadoEn(),
                e.getRevocadoEn());
    }
}
