package com.uade.crm.clientes.domain;

import com.uade.crm.clientes.domain.model.CambiosCliente;
import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.ConsentimientoRequeridoException;
import com.uade.crm.clientes.domain.model.DatoInvalidoException;
import com.uade.crm.clientes.domain.model.SolicitudAlta;
import com.uade.crm.clientes.domain.port.CifradorCuil;
import com.uade.crm.clientes.domain.valor.Cuil;
import com.uade.crm.clientes.domain.valor.CuilProtegido;
import com.uade.crm.clientes.domain.valor.Email;
import com.uade.crm.clientes.domain.valor.Telefono;
import com.uade.crm.clientes.domain.valor.TelefonoInvalidoException;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Patrón Factory: normaliza los datos de un alta y crea clientes válidos (§5.12 del documento semilla). Concentra
 * en un lugar las reglas de un cliente nuevo: teléfono móvil en E.164, email normalizado, nombre recortado,
 * un contacto, el canal de origen como último canal y, desde el change 04, las reglas de la Ley 25.326 sobre el
 * CUIL: solo con consentimiento de precalificación y protegido antes de llegar al modelo. Sin dependencias de Spring.
 */
public class ClienteFactory {

    private final String regionTelefono;
    private final Clock reloj;
    private final CifradorCuil cifrador;
    private final String versionTextoConsentimiento;

    public ClienteFactory(String regionTelefono, Clock reloj, CifradorCuil cifrador,
            String versionTextoConsentimiento) {
        this.regionTelefono = regionTelefono;
        this.reloj = reloj;
        this.cifrador = Objects.requireNonNull(cifrador, "cifrador");
        this.versionTextoConsentimiento = Objects.requireNonNull(versionTextoConsentimiento,
                "versionTextoConsentimiento");
    }

    /** Normaliza los datos crudos de un alta sin CUIL. */
    public SolicitudAlta solicitud(String nombre, String apellido, String email, String telefono,
            Provincia provincia, String localidad, CanalOrigen canal) {
        return solicitud(nombre, apellido, email, telefono, provincia, localidad, canal, null, null);
    }

    /**
     * Normaliza los datos crudos de un alta (design D4 del change 04). Lanza {@code TelefonoInvalidoException} si el
     * teléfono informado no es válido; {@code CuilInvalidoException} si el CUIL no lo es (se valida antes que el
     * consentimiento, para reportar el error útil); {@link ConsentimientoRequeridoException} si hay CUIL sin
     * consentimiento, y {@link DatoInvalidoException} si hay consentimiento sin CUIL.
     */
    public SolicitudAlta solicitud(String nombre, String apellido, String email, String telefono,
            Provincia provincia, String localidad, CanalOrigen canal, String cuil, Boolean consentimiento) {
        Email emailNormalizado = Email.de(email);
        Telefono telefonoNormalizado = Telefono.opcional(telefono, regionTelefono).orElse(null);
        boolean otorgado = Boolean.TRUE.equals(consentimiento);
        CuilProtegido cuilProtegido = null;
        if (cuil != null && !cuil.isBlank()) {
            Cuil validado = Cuil.de(cuil);
            if (!otorgado) {
                throw new ConsentimientoRequeridoException();
            }
            cuilProtegido = cifrador.proteger(validado);
        } else if (otorgado) {
            throw new DatoInvalidoException("consentimiento", "solo se informa junto con el CUIL");
        }
        return new SolicitudAlta(nombre, apellido, emailNormalizado, telefonoNormalizado, provincia, localidad, canal,
                cuilProtegido);
    }

    /** Versión del texto legal que se registra con cada consentimiento ({@code clientes.consentimiento}). */
    public String versionTextoConsentimiento() {
        return versionTextoConsentimiento;
    }

    /**
     * Normaliza los datos de una edición con las mismas reglas que el alta (design D2 del change 03). Un valor null
     * o en blanco queda null ("no modificar"); un teléfono informado inválido lanza {@code TelefonoInvalidoException}.
     */
    public CambiosCliente cambios(String nombre, String apellido, String email, String telefono, Provincia provincia,
            String localidad) {
        return cambios(nombre, apellido, email, telefono, provincia, localidad, null, false);
    }

    /** Igual que la anterior, más el teléfono alternativo y la resolución de la revisión (design D7 del change 04). */
    public CambiosCliente cambios(String nombre, String apellido, String email, String telefono, Provincia provincia,
            String localidad, String telefonoAlternativo, boolean resolverRevision) {
        return new CambiosCliente(nombre, apellido,
                email == null || email.isBlank() ? null : Email.de(email),
                Telefono.opcional(telefono, regionTelefono).orElse(null), provincia, localidad,
                opcionalAlternativo(telefonoAlternativo), resolverRevision);
    }

    /** El alternativo inválido se reporta en su propio campo, no en "telefono". */
    private Telefono opcionalAlternativo(String telefonoAlternativo) {
        try {
            return Telefono.opcional(telefonoAlternativo, regionTelefono).orElse(null);
        } catch (TelefonoInvalidoException e) {
            throw new DatoInvalidoException("telefonoAlternativo", "no es un móvil argentino válido");
        }
    }

    /** Cliente nuevo; si la solicitud trae CUIL, lo registra con su consentimiento. */
    public Cliente crear(SolicitudAlta solicitud) {
        Instant ahora = reloj.instant();
        Cliente cliente = Cliente.builder()
                .id(UUID.randomUUID())
                .nombre(solicitud.nombre())
                .apellido(solicitud.apellido())
                .email(solicitud.email())
                .telefono(solicitud.telefono())
                .provincia(solicitud.provincia())
                .localidad(solicitud.localidad())
                .canalOrigen(solicitud.canal())
                .ultimoCanal(solicitud.canal())
                .contactosRegistrados(1)
                .ultimoContactoEn(ahora)
                .creadoEn(ahora)
                .actualizadoEn(ahora)
                .build();
        solicitud.cuilInformado().ifPresent(cuil -> cliente.registrarCuil(cuil, versionTextoConsentimiento, ahora));
        return cliente;
    }
}
