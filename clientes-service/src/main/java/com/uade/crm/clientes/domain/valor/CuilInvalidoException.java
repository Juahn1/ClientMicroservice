package com.uade.crm.clientes.domain.valor;

/** El CUIL no tiene 11 dígitos, su prefijo no es de persona humana o su verificador no es correcto. Sin el CUIL. */
public class CuilInvalidoException extends RuntimeException {

    public CuilInvalidoException() {
        super("El CUIL no es válido");
    }
}
