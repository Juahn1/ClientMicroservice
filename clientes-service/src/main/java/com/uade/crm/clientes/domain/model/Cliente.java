package com.uade.crm.clientes.domain.model;

import com.uade.crm.clientes.domain.valor.Email;
import com.uade.crm.clientes.domain.valor.NombreBusqueda;
import com.uade.crm.clientes.domain.valor.Telefono;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Ficha única del comprador. Los clientes nuevos los crea {@code ClienteFactory}; la persistencia los
 * reconstruye con {@link #builder()}.
 */
public class Cliente {

    private final UUID id;
    private final String nombre;
    private final String apellido;
    private final NombreBusqueda nombreBusqueda;
    private final Email email;
    private Telefono telefono;
    private Telefono telefonoAlternativo;
    private final Provincia provincia;
    private String localidad;
    private CanalOrigen canalOrigen;
    private CanalOrigen ultimoCanal;
    private int contactosRegistrados;
    private Instant ultimoContactoEn;
    private final boolean requiereRevision;
    private final String motivoRevision;
    private final EstadoCliente estado;
    private final Long version;
    private final Instant creadoEn;
    private Instant actualizadoEn;
    private final Instant anonimizadoEn;

    private Cliente(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.nombre = Objects.requireNonNull(b.nombre, "nombre");
        this.apellido = Objects.requireNonNull(b.apellido, "apellido");
        this.nombreBusqueda = NombreBusqueda.de(nombre, apellido);
        this.estado = Objects.requireNonNull(b.estado, "estado");
        if (estado == EstadoCliente.ACTIVO) {
            Objects.requireNonNull(b.email, "email");
        }
        this.email = b.email;
        this.telefono = b.telefono;
        this.telefonoAlternativo = b.telefonoAlternativo;
        this.provincia = Objects.requireNonNull(b.provincia, "provincia");
        this.localidad = b.localidad;
        this.canalOrigen = b.canalOrigen;
        this.ultimoCanal = b.ultimoCanal;
        if (b.contactosRegistrados < 1) {
            throw new IllegalArgumentException("contactosRegistrados tiene que ser al menos 1");
        }
        this.contactosRegistrados = b.contactosRegistrados;
        this.ultimoContactoEn = Objects.requireNonNull(b.ultimoContactoEn, "ultimoContactoEn");
        this.requiereRevision = b.requiereRevision;
        this.motivoRevision = b.motivoRevision;
        this.version = b.version;
        this.creadoEn = Objects.requireNonNull(b.creadoEn, "creadoEn");
        this.actualizadoEn = Objects.requireNonNull(b.actualizadoEn, "actualizadoEn");
        this.anonimizadoEn = b.anonimizadoEn;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * La misma persona volvió a contactar a la agencia (design D4, paso 4). El alta lo puede disparar cualquiera
     * que conozca el email, así que nunca pisa nombre, apellido ni provincia: solo completa lo que falta, suma un
     * contacto y actualiza el último contacto y canal.
     */
    public void registrarRecontacto(SolicitudAlta solicitud, Instant ahora) {
        if (estado != EstadoCliente.ACTIVO) {
            throw new IllegalStateException("Solo un cliente activo puede registrar un recontacto");
        }
        if (localidad == null) {
            localidad = solicitud.localidad();
        }
        if (canalOrigen == null) {
            canalOrigen = solicitud.canal();
        }
        if (solicitud.canal() != null) {
            ultimoCanal = solicitud.canal();
        }
        completarTelefono(solicitud.telefono());
        contactosRegistrados++;
        ultimoContactoEn = ahora;
        actualizadoEn = ahora;
    }

    /** Un teléfono distinto del principal se guarda como alternativo solo si no había otro. */
    private void completarTelefono(Telefono informado) {
        if (informado == null) {
            return;
        }
        if (telefono == null) {
            telefono = informado;
        } else if (!telefono.equals(informado) && telefonoAlternativo == null) {
            telefonoAlternativo = informado;
        }
    }

    public UUID id() {
        return id;
    }

    public String nombre() {
        return nombre;
    }

    public String apellido() {
        return apellido;
    }

    public NombreBusqueda nombreBusqueda() {
        return nombreBusqueda;
    }

    public Email email() {
        return email;
    }

    public Optional<Telefono> telefono() {
        return Optional.ofNullable(telefono);
    }

    public Optional<Telefono> telefonoAlternativo() {
        return Optional.ofNullable(telefonoAlternativo);
    }

    public Provincia provincia() {
        return provincia;
    }

    public Optional<String> localidad() {
        return Optional.ofNullable(localidad);
    }

    public Optional<CanalOrigen> canalOrigen() {
        return Optional.ofNullable(canalOrigen);
    }

    public Optional<CanalOrigen> ultimoCanal() {
        return Optional.ofNullable(ultimoCanal);
    }

    public int contactosRegistrados() {
        return contactosRegistrados;
    }

    public Instant ultimoContactoEn() {
        return ultimoContactoEn;
    }

    public boolean requiereRevision() {
        return requiereRevision;
    }

    public Optional<String> motivoRevision() {
        return Optional.ofNullable(motivoRevision);
    }

    public EstadoCliente estado() {
        return estado;
    }

    public boolean activo() {
        return estado == EstadoCliente.ACTIVO;
    }

    /** Versión para el control optimista; null mientras el cliente no se guardó. */
    public Optional<Long> version() {
        return Optional.ofNullable(version);
    }

    public Instant creadoEn() {
        return creadoEn;
    }

    public Instant actualizadoEn() {
        return actualizadoEn;
    }

    public Optional<Instant> anonimizadoEn() {
        return Optional.ofNullable(anonimizadoEn);
    }

    /** Solo el id: el resto son datos personales. */
    @Override
    public String toString() {
        return "Cliente[id=" + id + "]";
    }

    public static final class Builder {

        private UUID id;
        private String nombre;
        private String apellido;
        private Email email;
        private Telefono telefono;
        private Telefono telefonoAlternativo;
        private Provincia provincia;
        private String localidad;
        private CanalOrigen canalOrigen;
        private CanalOrigen ultimoCanal;
        private int contactosRegistrados = 1;
        private Instant ultimoContactoEn;
        private boolean requiereRevision;
        private String motivoRevision;
        private EstadoCliente estado = EstadoCliente.ACTIVO;
        private Long version;
        private Instant creadoEn;
        private Instant actualizadoEn;
        private Instant anonimizadoEn;

        private Builder() {
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder nombre(String nombre) {
            this.nombre = nombre;
            return this;
        }

        public Builder apellido(String apellido) {
            this.apellido = apellido;
            return this;
        }

        public Builder email(Email email) {
            this.email = email;
            return this;
        }

        public Builder telefono(Telefono telefono) {
            this.telefono = telefono;
            return this;
        }

        public Builder telefonoAlternativo(Telefono telefonoAlternativo) {
            this.telefonoAlternativo = telefonoAlternativo;
            return this;
        }

        public Builder provincia(Provincia provincia) {
            this.provincia = provincia;
            return this;
        }

        public Builder localidad(String localidad) {
            this.localidad = localidad;
            return this;
        }

        public Builder canalOrigen(CanalOrigen canalOrigen) {
            this.canalOrigen = canalOrigen;
            return this;
        }

        public Builder ultimoCanal(CanalOrigen ultimoCanal) {
            this.ultimoCanal = ultimoCanal;
            return this;
        }

        public Builder contactosRegistrados(int contactosRegistrados) {
            this.contactosRegistrados = contactosRegistrados;
            return this;
        }

        public Builder ultimoContactoEn(Instant ultimoContactoEn) {
            this.ultimoContactoEn = ultimoContactoEn;
            return this;
        }

        public Builder requiereRevision(boolean requiereRevision) {
            this.requiereRevision = requiereRevision;
            return this;
        }

        public Builder motivoRevision(String motivoRevision) {
            this.motivoRevision = motivoRevision;
            return this;
        }

        public Builder estado(EstadoCliente estado) {
            this.estado = estado;
            return this;
        }

        public Builder version(Long version) {
            this.version = version;
            return this;
        }

        public Builder creadoEn(Instant creadoEn) {
            this.creadoEn = creadoEn;
            return this;
        }

        public Builder actualizadoEn(Instant actualizadoEn) {
            this.actualizadoEn = actualizadoEn;
            return this;
        }

        public Builder anonimizadoEn(Instant anonimizadoEn) {
            this.anonimizadoEn = anonimizadoEn;
            return this;
        }

        public Cliente build() {
            return new Cliente(this);
        }
    }
}
