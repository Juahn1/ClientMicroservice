package com.uade.crm.clientes.domain.valor;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import java.util.Objects;
import java.util.Optional;

/**
 * Teléfono móvil argentino en E.164 (§5.6 del documento semilla). Todo el contacto con la agencia es por WhatsApp,
 * por eso se exige móvil: libphonenumber interpreta {@code 11 1234-5678} como fijo, y en ese caso se inserta el
 * {@code 9} después de {@code +54} y se vuelve a validar.
 */
public final class Telefono {

    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();
    private static final int CODIGO_ARGENTINA = 54;

    private final String e164;

    private Telefono(String e164) {
        this.e164 = e164;
    }

    /** Normaliza un teléfono informado; lanza {@link TelefonoInvalidoException} si no es un móvil argentino. */
    public static Telefono de(String crudo, String regionPorDefecto) {
        if (crudo == null || crudo.isBlank()) {
            throw new TelefonoInvalidoException();
        }
        PhoneNumber numero = parsear(crudo, regionPorDefecto).orElseThrow(TelefonoInvalidoException::new);
        if (numero.getCountryCode() != CODIGO_ARGENTINA) {
            throw new TelefonoInvalidoException();
        }
        if (esMovilValido(numero)) {
            return new Telefono(UTIL.format(numero, PhoneNumberFormat.E164));
        }
        return parsear("+549" + numero.getNationalNumber(), regionPorDefecto)
                .filter(Telefono::esMovilValido)
                .map(movil -> new Telefono(UTIL.format(movil, PhoneNumberFormat.E164)))
                .orElseThrow(TelefonoInvalidoException::new);
    }

    /** Un teléfono ausente o en blanco no es un error: el contrato lo hace opcional. */
    public static Optional<Telefono> opcional(String crudo, String regionPorDefecto) {
        return crudo == null || crudo.isBlank() ? Optional.empty() : Optional.of(de(crudo, regionPorDefecto));
    }

    /** Reconstruye un teléfono ya normalizado (por ejemplo, leído de la base). */
    public static Telefono deE164(String e164) {
        return new Telefono(Objects.requireNonNull(e164));
    }

    private static Optional<PhoneNumber> parsear(String crudo, String region) {
        try {
            return Optional.of(UTIL.parse(crudo, region));
        } catch (NumberParseException e) {
            return Optional.empty();
        }
    }

    private static boolean esMovilValido(PhoneNumber numero) {
        return UTIL.isValidNumber(numero) && UTIL.getNumberType(numero) == PhoneNumberType.MOBILE;
    }

    public String e164() {
        return e164;
    }

    /** {@code +5491112345678} → {@code +54911****5678}. */
    public String enmascarado() {
        return Enmascarador.telefono(e164);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Telefono otro && e164.equals(otro.e164);
    }

    @Override
    public int hashCode() {
        return e164.hashCode();
    }

    /** Enmascarado para que un log accidental no exponga el número. */
    @Override
    public String toString() {
        return enmascarado();
    }
}
