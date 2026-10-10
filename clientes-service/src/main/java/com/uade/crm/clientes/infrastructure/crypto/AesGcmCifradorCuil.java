package com.uade.crm.clientes.infrastructure.crypto;

import com.uade.crm.clientes.config.ClientesProperties;
import com.uade.crm.clientes.domain.port.CifradorCuil;
import com.uade.crm.clientes.domain.valor.Cuil;
import com.uade.crm.clientes.domain.valor.CuilProtegido;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Adaptador de {@link CifradorCuil} con {@code javax.crypto} (§5.8 del documento semilla, design D2 del change 04):
 * AES-256-GCM con IV aleatorio de 12 bytes antepuesto al texto cifrado (tag de 128 bits) y HMAC-SHA256 en hex
 * como índice ciego. Las claves llegan validadas por {@link ClientesProperties.Cuil}.
 */
@Component
public class AesGcmCifradorCuil implements CifradorCuil {

    static final String TRANSFORMACION = "AES/GCM/NoPadding";
    static final int BYTES_IV = 12;
    static final int BITS_TAG = 128;
    private static final String HMAC = "HmacSHA256";

    private final SecretKeySpec claveCifrado;
    private final SecretKeySpec claveHash;
    private final SecureRandom aleatorio = new SecureRandom();

    @Autowired
    public AesGcmCifradorCuil(ClientesProperties properties) {
        this(properties.cuil().claveCifrado(), properties.cuil().claveHash());
    }

    /** Claves en base64, ya validadas. */
    public AesGcmCifradorCuil(String claveCifradoBase64, String claveHashBase64) {
        this.claveCifrado = new SecretKeySpec(Base64.getDecoder().decode(claveCifradoBase64), "AES");
        this.claveHash = new SecretKeySpec(Base64.getDecoder().decode(claveHashBase64), HMAC);
    }

    @Override
    public CuilProtegido proteger(Cuil cuil) {
        byte[] texto = cuil.digitos().getBytes(StandardCharsets.US_ASCII);
        return new CuilProtegido(cifrar(texto), hash(texto), cuil.ultimos4());
    }

    private byte[] cifrar(byte[] texto) {
        byte[] iv = new byte[BYTES_IV];
        aleatorio.nextBytes(iv);
        try {
            Cipher cifrador = Cipher.getInstance(TRANSFORMACION);
            cifrador.init(Cipher.ENCRYPT_MODE, claveCifrado, new GCMParameterSpec(BITS_TAG, iv));
            byte[] cifrado = cifrador.doFinal(texto);
            return ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el CUIL", e);
        }
    }

    private String hash(byte[] texto) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(claveHash);
            return HexFormat.of().formatHex(mac.doFinal(texto));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular el hash del CUIL", e);
        }
    }
}
