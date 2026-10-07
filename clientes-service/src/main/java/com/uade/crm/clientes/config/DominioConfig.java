package com.uade.crm.clientes.config;

import com.uade.crm.clientes.domain.ClienteFactory;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Beans de las piezas del dominio, que no dependen de Spring. */
@Configuration
public class DominioConfig {

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    @Bean
    ClienteFactory clienteFactory(ClientesProperties properties, Clock reloj) {
        return new ClienteFactory(properties.telefono().regionPorDefecto(), reloj);
    }
}
