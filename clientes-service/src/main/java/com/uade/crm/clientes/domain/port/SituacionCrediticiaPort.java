package com.uade.crm.clientes.domain.port;

import com.uade.crm.clientes.domain.model.SituacionCrediticia;

/**
 * Consulta la situación crediticia de un CUIL (design D2 y D5 del change 05). Lo implementan las fuentes (el adaptador
 * simulado y, en la Segunda Parte, el SOAP contra {@code precalificacion}) y los decorators que las envuelven (caché
 * y resiliencia), que forman una cadena.
 *
 * <p><b>Contrato de toda fuente:</b> devolver {@code CON_DATOS} o {@code SIN_REGISTROS}, o lanzar
 * {@link FuenteNoDisponibleException}, dentro de {@code clientes.situacion-crediticia.timeout}. El timeout es
 * responsabilidad de la fuente, con el mecanismo natural de su tecnología (por ejemplo, los timeouts de conexión y de
 * lectura de su cliente HTTP): los decorators no lo imponen. Toda fuente nueva lo verifica extendiendo
 * {@code ContratoFuenteSituacionTest}.
 *
 * <p>La cadena completa nunca lanza por una falla de la fuente: devuelve {@code NO_DISPONIBLE}.
 */
public interface SituacionCrediticiaPort {

    SituacionCrediticia consultar(ConsultaSituacion consulta);
}
