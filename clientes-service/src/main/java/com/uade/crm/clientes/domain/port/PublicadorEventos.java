package com.uade.crm.clientes.domain.port;

import com.uade.crm.contracts.EventoIntegracion;
import java.util.UUID;

/**
 * Puerto de salida para los eventos de integración (design D8 del change 03); lo implementa la infraestructura de
 * mensajería. La publicación es asincrónica: una falla se registra y nunca se propaga a quien llama.
 */
public interface PublicadorEventos {

    /** Publica el evento en el tópico con {@code clave} como clave del mensaje (orden por cliente). */
    void publicar(String topico, UUID clave, EventoIntegracion<?> evento);
}
