package com.uade.crm.clientes.domain.model;

import com.uade.crm.clientes.domain.valor.CuilProtegido;
import com.uade.crm.clientes.domain.valor.Email;
import com.uade.crm.clientes.domain.valor.NombreBusqueda;
import com.uade.crm.clientes.domain.valor.Telefono;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import com.uade.crm.contracts.clientes.CampoCliente;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Ficha única del comprador. Los clientes nuevos los crea {@code ClienteFactory}; la persistencia los
 * reconstruye con {@link #builder()}. Las operaciones que modifican la ficha devuelven qué cambió, para que la
 * aplicación decida si guarda y si publica eventos (design D2 del change 03).
 */
public class Cliente {

    /** Reemplaza nombre y apellido al anonimizar: las columnas son obligatorias. */
    public static final String ANONIMIZADO = "ANONIMIZADO";

    private final UUID id;
    private String nombre;
    private String apellido;
    private NombreBusqueda nombreBusqueda;
    private Email email;
    private Telefono telefono;
    private Telefono telefonoAlternativo;
    private Provincia provincia;
    private String localidad;
    private CanalOrigen canalOrigen;
    private CanalOrigen ultimoCanal;
    private int contactosRegistrados;
    private Instant ultimoContactoEn;
    private boolean noContactar;
    private Instant noContactarDesde;
    private boolean requiereRevision;
    private MotivoRevision motivoRevision;
    private CuilProtegido cuil;
    private final List<Consentimiento> consentimientos;
    private EstadoCliente estado;
    private final Long version;
    private final Instant creadoEn;
    private Instant actualizadoEn;
    private Instant anonimizadoEn;

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
        if (b.noContactar != (b.noContactarDesde != null)) {
            throw new IllegalArgumentException("noContactarDesde se informa si y solo si el cliente tiene la marca");
        }
        this.noContactar = b.noContactar;
        this.noContactarDesde = b.noContactarDesde;
        if (b.motivoRevision != null && !b.requiereRevision) {
            throw new IllegalArgumentException("motivoRevision se informa solo si el cliente requiere revisión");
        }
        this.requiereRevision = b.requiereRevision;
        this.motivoRevision = b.motivoRevision;
        this.cuil = b.cuil;
        this.consentimientos = new ArrayList<>(b.consentimientos);
        if ((cuil != null) != tieneConsentimientoVigente(FinalidadConsentimiento.PRECALIFICACION_CREDITICIA)) {
            throw new IllegalArgumentException(
                    "Un cliente tiene CUIL si y solo si tiene vigente el consentimiento de precalificación");
        }
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
     * contacto y actualiza el último contacto y canal. Si tenía la marca no contactar, la quita: la persona volvió
     * a pedir contacto (change 03).
     */
    public void registrarRecontacto(SolicitudAlta solicitud, Instant ahora) {
        exigirActivo("registrar un recontacto");
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
        noContactar = false;
        noContactarDesde = null;
        contactosRegistrados++;
        ultimoContactoEn = ahora;
        actualizadoEn = ahora;
    }

    /**
     * Edición por parte del personal (design D2 del change 03 y D7 del change 04): aplica los valores informados y
     * devuelve si algo cambió y qué campos publicables cambiaron. Los valores llegan normalizados, así que el mismo
     * email o teléfono en otro formato no cuenta como cambio. Un null no modifica. Un teléfono alternativo igual al
     * principal (el actual o el que llega en la misma edición) se rechaza sin modificar nada.
     */
    public ResultadoEdicion editar(CambiosCliente cambios, Instant ahora) {
        exigirActivo("editarse");
        Telefono principal = cambios.telefono() != null ? cambios.telefono() : telefono;
        if (cambios.telefonoAlternativo() != null && cambios.telefonoAlternativo().equals(principal)) {
            throw new DatoInvalidoException("telefonoAlternativo", "no puede ser igual al teléfono principal");
        }
        boolean otrosCambios = false;
        if (cambios.telefonoAlternativo() != null && !cambios.telefonoAlternativo().equals(telefonoAlternativo)) {
            telefonoAlternativo = cambios.telefonoAlternativo();
            otrosCambios = true;
        }
        if (cambios.resolverRevision() && requiereRevision) {
            requiereRevision = false;
            motivoRevision = null;
            otrosCambios = true;
        }
        Set<CampoCliente> modificados = EnumSet.noneOf(CampoCliente.class);
        if (cambios.nombre() != null && !cambios.nombre().equals(nombre)) {
            nombre = cambios.nombre();
            modificados.add(CampoCliente.NOMBRE);
        }
        if (cambios.apellido() != null && !cambios.apellido().equals(apellido)) {
            apellido = cambios.apellido();
            modificados.add(CampoCliente.APELLIDO);
        }
        if (cambios.email() != null && !cambios.email().equals(email)) {
            email = cambios.email();
            modificados.add(CampoCliente.EMAIL);
        }
        if (cambios.telefono() != null && !cambios.telefono().equals(telefono)) {
            telefono = cambios.telefono();
            modificados.add(CampoCliente.TELEFONO);
        }
        if (cambios.provincia() != null && cambios.provincia() != provincia) {
            provincia = cambios.provincia();
            modificados.add(CampoCliente.PROVINCIA);
        }
        if (cambios.localidad() != null && !cambios.localidad().equals(localidad)) {
            localidad = cambios.localidad();
            modificados.add(CampoCliente.LOCALIDAD);
        }
        if (modificados.contains(CampoCliente.NOMBRE) || modificados.contains(CampoCliente.APELLIDO)) {
            nombreBusqueda = NombreBusqueda.de(nombre, apellido);
        }
        boolean huboCambios = otrosCambios || !modificados.isEmpty();
        if (huboCambios) {
            actualizadoEn = ahora;
        }
        return new ResultadoEdicion(huboCambios, modificados);
    }

    /**
     * Registra el CUIL junto con el consentimiento de precalificación que lo habilita (design D3 del change 04). Solo
     * para un cliente sin CUIL: reemplazarlo no es parte de este change. Si ya hubiera un consentimiento vigente de
     * esa finalidad, se conserva el original.
     */
    public void registrarCuil(CuilProtegido nuevo, String versionTexto, Instant ahora) {
        exigirActivo("registrar un CUIL");
        Objects.requireNonNull(nuevo, "cuil");
        if (cuil != null) {
            throw new IllegalStateException("El cliente ya tiene CUIL");
        }
        cuil = nuevo;
        if (!tieneConsentimientoVigente(FinalidadConsentimiento.PRECALIFICACION_CREDITICIA)) {
            consentimientos.add(Consentimiento.otorgar(FinalidadConsentimiento.PRECALIFICACION_CREDITICIA,
                    versionTexto, OrigenConsentimiento.PREFORMULARIO, ahora));
        }
        actualizadoEn = ahora;
    }

    /** Marca al cliente para revisión. Si ya estaba marcado conserva el primer motivo y devuelve false. */
    public boolean marcarRevision(MotivoRevision motivo, Instant ahora) {
        exigirActivo("marcarse para revisión");
        if (requiereRevision) {
            return false;
        }
        requiereRevision = true;
        motivoRevision = Objects.requireNonNull(motivo, "motivo");
        actualizadoEn = ahora;
        return true;
    }

    /** El personal resolvió la revisión. Devuelve false si el cliente no estaba marcado. */
    public boolean resolverRevision(Instant ahora) {
        exigirActivo("resolver una revisión");
        if (!requiereRevision) {
            return false;
        }
        requiereRevision = false;
        motivoRevision = null;
        actualizadoEn = ahora;
        return true;
    }

    /** El cliente pidió que no lo contacten más (design D5 del change 03). Devuelve false si ya tenía la marca. */
    public boolean marcarNoContactar(Instant ahora) {
        exigirActivo("marcarse como no contactar");
        if (noContactar) {
            return false;
        }
        noContactar = true;
        noContactarDesde = ahora;
        actualizadoEn = ahora;
        return true;
    }

    /** Quita la marca no contactar. Devuelve false si el cliente no la tenía. */
    public boolean quitarNoContactar(Instant ahora) {
        exigirActivo("desmarcarse como no contactar");
        if (!noContactar) {
            return false;
        }
        noContactar = false;
        noContactarDesde = null;
        actualizadoEn = ahora;
        return true;
    }

    /**
     * Baja por derecho de supresión (Ley 25.326, design D6 del change 03): elimina los datos personales y conserva
     * el id, la provincia, los canales, el contador, las fechas y la marca no contactar. El CUIL se elimina y los
     * consentimientos vigentes se revocan; sus filas quedan como constancia de la base legal, sin datos personales
     * (change 04). Devuelve false si ya estaba anonimizado.
     */
    public boolean anonimizar(Instant ahora) {
        if (estado == EstadoCliente.ANONIMIZADO) {
            return false;
        }
        nombre = ANONIMIZADO;
        apellido = ANONIMIZADO;
        nombreBusqueda = NombreBusqueda.de(nombre, apellido);
        email = null;
        telefono = null;
        telefonoAlternativo = null;
        localidad = null;
        cuil = null;
        consentimientos.forEach(c -> c.revocar(ahora));
        estado = EstadoCliente.ANONIMIZADO;
        anonimizadoEn = ahora;
        actualizadoEn = ahora;
        return true;
    }

    private void exigirActivo(String operacion) {
        if (estado != EstadoCliente.ACTIVO) {
            throw new IllegalStateException("Solo un cliente activo puede " + operacion);
        }
    }

    private boolean tieneConsentimientoVigente(FinalidadConsentimiento finalidad) {
        return consentimientos.stream().anyMatch(c -> c.vigente() && c.finalidad() == finalidad);
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

    /** Null solo en un cliente anonimizado. */
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

    public boolean noContactar() {
        return noContactar;
    }

    public Optional<Instant> noContactarDesde() {
        return Optional.ofNullable(noContactarDesde);
    }

    public boolean requiereRevision() {
        return requiereRevision;
    }

    public Optional<MotivoRevision> motivoRevision() {
        return Optional.ofNullable(motivoRevision);
    }

    public Optional<CuilProtegido> cuil() {
        return Optional.ofNullable(cuil);
    }

    /** Todos los consentimientos, vigentes y revocados, en el orden en que se otorgaron. */
    public List<Consentimiento> consentimientos() {
        return Collections.unmodifiableList(consentimientos);
    }

    public List<Consentimiento> consentimientosVigentes() {
        return consentimientos.stream().filter(Consentimiento::vigente).toList();
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
        private boolean noContactar;
        private Instant noContactarDesde;
        private boolean requiereRevision;
        private MotivoRevision motivoRevision;
        private CuilProtegido cuil;
        private List<Consentimiento> consentimientos = List.of();
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

        public Builder noContactar(boolean noContactar) {
            this.noContactar = noContactar;
            return this;
        }

        public Builder noContactarDesde(Instant noContactarDesde) {
            this.noContactarDesde = noContactarDesde;
            return this;
        }

        public Builder requiereRevision(boolean requiereRevision) {
            this.requiereRevision = requiereRevision;
            return this;
        }

        public Builder motivoRevision(MotivoRevision motivoRevision) {
            this.motivoRevision = motivoRevision;
            return this;
        }

        public Builder cuil(CuilProtegido cuil) {
            this.cuil = cuil;
            return this;
        }

        public Builder consentimientos(List<Consentimiento> consentimientos) {
            this.consentimientos = List.copyOf(consentimientos);
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
