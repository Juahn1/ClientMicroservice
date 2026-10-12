package com.uade.crm.clientes.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;
import com.uade.crm.clientes.domain.valor.Enmascarador;

/**
 * Converter de logback ({@code %mask(...)}) que enmascara CUIL y teléfonos en el texto que envuelve,
 * incluidos los mensajes y excepciones de frameworks.
 */
public class EnmascararDatosPersonalesConverter extends CompositeConverter<ILoggingEvent> {

    @Override
    protected String transform(ILoggingEvent event, String in) {
        return Enmascarador.texto(in);
    }
}
