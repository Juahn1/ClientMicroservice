package com.uade.crm.clientes.domain.port;

import com.uade.crm.clientes.domain.valor.Cuil;
import com.uade.crm.clientes.domain.valor.CuilProtegido;

/**
 * Protege el CUIL antes de que llegue al modelo (design D2 del change 04). El hash se necesita antes de persistir,
 * para deduplicar por CUIL.
 */
public interface CifradorCuil {

    CuilProtegido proteger(Cuil cuil);

    /**
     * Recupera el CUIL en claro (design D8 del change 05). Solo se usa para consultar la situación crediticia: el
     * resultado vive en memoria durante esa consulta y nunca se loguea ni se devuelve. Si el texto cifrado está
     * alterado o se cifró con otra clave, lanza {@link IllegalStateException}.
     */
    Cuil descifrar(CuilProtegido protegido);
}
