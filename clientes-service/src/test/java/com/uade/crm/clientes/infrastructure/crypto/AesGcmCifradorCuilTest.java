package com.uade.crm.clientes.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import com.uade.crm.clientes.domain.valor.Cuil;
import com.uade.crm.clientes.domain.valor.CuilProtegido;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** Tarea 4.2 del seed: ida y vuelta, IV distinto en cada cifrado y hash estable. */
class AesGcmCifradorCuilTest {

    private static final String CLAVE_CIFRADO = "c29sby10ZXN0LWNsYXZlLWNpZnJhZG8tY3VpbC0zMmI=";
    private static final String CLAVE_HASH = "c29sby10ZXN0LWNsYXZlLWhtYWMtZGVsLWN1aWwtMzJi";
    private static final String OTRA_CLAVE_HASH = "b3RyYS1jbGF2ZS1obWFjLWRlbC1jdWlsLTMyYnl0ZXM=";

    private final AesGcmCifradorCuil cifrador = new AesGcmCifradorCuil(CLAVE_CIFRADO, CLAVE_HASH);
    private final Cuil cuil = Cuil.de("20-12345678-6");

    @Test
    void idaYVuelta() throws Exception {
        assertThat(descifrar(cifrador.proteger(cuil).cifrado())).isEqualTo("20123456786");
    }

    @Test
    void ivDistintoEnCadaCifrado() {
        byte[] primero = cifrador.proteger(cuil).cifrado();
        byte[] segundo = cifrador.proteger(cuil).cifrado();
        assertThat(Arrays.copyOf(primero, AesGcmCifradorCuil.BYTES_IV))
                .isNotEqualTo(Arrays.copyOf(segundo, AesGcmCifradorCuil.BYTES_IV));
        assertThat(primero).isNotEqualTo(segundo);
    }

    @Test
    void elCifradoNoContieneElCuil() {
        String comoTexto = new String(cifrador.proteger(cuil).cifrado(), StandardCharsets.ISO_8859_1);
        assertThat(comoTexto).doesNotContain("20123456786");
    }

    @Test
    void hashEstableDe64CaracteresHex() {
        CuilProtegido primero = cifrador.proteger(cuil);
        CuilProtegido segundo = cifrador.proteger(Cuil.de("20123456786"));
        assertThat(primero.hash()).hasSize(64).matches("[0-9a-f]{64}").isEqualTo(segundo.hash());
        assertThat(primero.mismoCuil(segundo)).isTrue();
    }

    @Test
    void hashDistintoConOtraClaveYConOtroCuil() {
        String hash = cifrador.proteger(cuil).hash();
        assertThat(new AesGcmCifradorCuil(CLAVE_CIFRADO, OTRA_CLAVE_HASH).proteger(cuil).hash()).isNotEqualTo(hash);
        assertThat(cifrador.proteger(Cuil.de("27-30111222-5")).hash()).isNotEqualTo(hash);
    }

    @Test
    void ultimos4YEnmascarado() {
        CuilProtegido protegido = cifrador.proteger(cuil);
        assertThat(protegido.ultimos4()).isEqualTo("5678");
        assertThat(protegido.enmascarado()).isEqualTo("XX-XXXX5678-X");
        assertThat(protegido.toString()).isEqualTo("XX-XXXX5678-X").doesNotContain(protegido.hash());
    }

    private static String descifrar(byte[] ivYCifrado) throws Exception {
        Cipher descifrador = Cipher.getInstance(AesGcmCifradorCuil.TRANSFORMACION);
        descifrador.init(Cipher.DECRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(CLAVE_CIFRADO), "AES"),
                new GCMParameterSpec(AesGcmCifradorCuil.BITS_TAG, ivYCifrado, 0, AesGcmCifradorCuil.BYTES_IV));
        byte[] texto = descifrador.doFinal(ivYCifrado, AesGcmCifradorCuil.BYTES_IV,
                ivYCifrado.length - AesGcmCifradorCuil.BYTES_IV);
        return new String(texto, StandardCharsets.US_ASCII);
    }
}
