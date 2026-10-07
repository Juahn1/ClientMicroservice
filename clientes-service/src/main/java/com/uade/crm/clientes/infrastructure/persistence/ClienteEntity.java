package com.uade.crm.clientes.infrastructure.persistence;

import com.uade.crm.clientes.domain.model.EstadoCliente;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Fila de la tabla {@code cliente} ({@code V1__cliente.sql}). Solo la usa {@link ClienteRepositorioJpa}. */
@Entity
@Table(name = "cliente")
public class ClienteEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 120)
    private String apellido;

    @Column(name = "nombre_busqueda", nullable = false, length = 241)
    private String nombreBusqueda;

    @Column(length = 254)
    private String email;

    @Column(name = "telefono_e164", length = 20)
    private String telefonoE164;

    @Column(name = "telefono_alternativo", length = 20)
    private String telefonoAlternativo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Provincia provincia;

    @Column(length = 120)
    private String localidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal_origen", length = 20)
    private CanalOrigen canalOrigen;

    @Enumerated(EnumType.STRING)
    @Column(name = "ultimo_canal", length = 20)
    private CanalOrigen ultimoCanal;

    @Column(name = "contactos_registrados", nullable = false)
    private int contactosRegistrados;

    @Column(name = "ultimo_contacto_en", nullable = false)
    private Instant ultimoContactoEn;

    @Column(name = "requiere_revision", nullable = false)
    private boolean requiereRevision;

    @Column(name = "motivo_revision", length = 40)
    private String motivoRevision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private EstadoCliente estado;

    /** Control optimista; alimenta el {@code ETag}. Null en una entidad nueva, así Spring Data la inserta. */
    @Version
    private Long version;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Column(name = "anonimizado_en")
    private Instant anonimizadoEn;

    protected ClienteEntity() {
    }

    // Getters y setters de acceso por el mapeo de ClienteRepositorioJpa.

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    String getNombre() {
        return nombre;
    }

    void setNombre(String nombre) {
        this.nombre = nombre;
    }

    String getApellido() {
        return apellido;
    }

    void setApellido(String apellido) {
        this.apellido = apellido;
    }

    String getNombreBusqueda() {
        return nombreBusqueda;
    }

    void setNombreBusqueda(String nombreBusqueda) {
        this.nombreBusqueda = nombreBusqueda;
    }

    String getEmail() {
        return email;
    }

    void setEmail(String email) {
        this.email = email;
    }

    String getTelefonoE164() {
        return telefonoE164;
    }

    void setTelefonoE164(String telefonoE164) {
        this.telefonoE164 = telefonoE164;
    }

    String getTelefonoAlternativo() {
        return telefonoAlternativo;
    }

    void setTelefonoAlternativo(String telefonoAlternativo) {
        this.telefonoAlternativo = telefonoAlternativo;
    }

    Provincia getProvincia() {
        return provincia;
    }

    void setProvincia(Provincia provincia) {
        this.provincia = provincia;
    }

    String getLocalidad() {
        return localidad;
    }

    void setLocalidad(String localidad) {
        this.localidad = localidad;
    }

    CanalOrigen getCanalOrigen() {
        return canalOrigen;
    }

    void setCanalOrigen(CanalOrigen canalOrigen) {
        this.canalOrigen = canalOrigen;
    }

    CanalOrigen getUltimoCanal() {
        return ultimoCanal;
    }

    void setUltimoCanal(CanalOrigen ultimoCanal) {
        this.ultimoCanal = ultimoCanal;
    }

    int getContactosRegistrados() {
        return contactosRegistrados;
    }

    void setContactosRegistrados(int contactosRegistrados) {
        this.contactosRegistrados = contactosRegistrados;
    }

    Instant getUltimoContactoEn() {
        return ultimoContactoEn;
    }

    void setUltimoContactoEn(Instant ultimoContactoEn) {
        this.ultimoContactoEn = ultimoContactoEn;
    }

    boolean isRequiereRevision() {
        return requiereRevision;
    }

    void setRequiereRevision(boolean requiereRevision) {
        this.requiereRevision = requiereRevision;
    }

    String getMotivoRevision() {
        return motivoRevision;
    }

    void setMotivoRevision(String motivoRevision) {
        this.motivoRevision = motivoRevision;
    }

    EstadoCliente getEstado() {
        return estado;
    }

    void setEstado(EstadoCliente estado) {
        this.estado = estado;
    }

    Long getVersion() {
        return version;
    }

    void setVersion(Long version) {
        this.version = version;
    }

    Instant getCreadoEn() {
        return creadoEn;
    }

    void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }

    Instant getActualizadoEn() {
        return actualizadoEn;
    }

    void setActualizadoEn(Instant actualizadoEn) {
        this.actualizadoEn = actualizadoEn;
    }

    Instant getAnonimizadoEn() {
        return anonimizadoEn;
    }

    void setAnonimizadoEn(Instant anonimizadoEn) {
        this.anonimizadoEn = anonimizadoEn;
    }

    @Override
    public String toString() {
        return "ClienteEntity[id=" + id + "]";
    }
}
