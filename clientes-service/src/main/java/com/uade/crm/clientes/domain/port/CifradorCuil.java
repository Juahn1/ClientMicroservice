package com.uade.crm.clientes.domain.port;

import com.uade.crm.clientes.domain.valor.Cuil;
import com.uade.crm.clientes.domain.valor.CuilProtegido;

/**
 * Protege el CUIL antes de que llegue al modelo (design D2 del change 04). El hash se necesita antes de persistir,
 * para deduplicar por CUIL. No hay {@code descifrar} todavía: lo suma el change 05, el primero que lo necesita.
 */
public interface CifradorCuil {

    CuilProtegido proteger(Cuil cuil);
}
