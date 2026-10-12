package com.uade.crm.clientes.infrastructure.messaging;

import com.uade.crm.clientes.config.ClientesProperties;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Qué pasa con un mensaje de {@code lead-calificado} que no se pudo registrar (design D5 del change 08). Spring Boot
 * conecta este {@code CommonErrorHandler} a la fábrica de listeners.
 *
 * <ul>
 *   <li>{@link MensajeInvalidoException}: sin reintentos, directo al tópico de fallidos.</li>
 *   <li>Cualquier otra excepción (base caída, timeout): reintentos con espera exponencial acotada y, si persiste, al
 *       tópico de fallidos.</li>
 * </ul>
 * En los dos casos se confirma el offset y el consumidor sigue con el próximo mensaje. Si el broker no acepta el envío
 * al tópico de fallidos, el offset no se confirma y el mensaje se vuelve a procesar.
 */
@Configuration
class ConsumidorKafkaConfig implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorKafkaConfig.class);

    private DefaultKafkaProducerFactory<String, byte[]> productorFallidos;

    /**
     * El productor de fallidos copia el mensaje original byte a byte, así que no puede usar el {@code JsonSerializer}
     * del publicador de eventos. Tampoco puede ser un bean: un {@code KafkaTemplate} o un {@code ProducerFactory}
     * propios harían que Spring Boot dejara de crear los suyos. Toma la configuración del productor de Boot (incluido
     * el broker de {@code KafkaConnectionDetails}) con sus propios serializers, y se cierra en {@link #destroy()}.
     */
    @Bean
    DefaultErrorHandler manejadorErroresHistorial(ProducerFactory<?, ?> productores, ClientesProperties properties) {
        productorFallidos = new DefaultKafkaProducerFactory<>(productores.getConfigurationProperties(),
                new StringSerializer(), new ByteArraySerializer());
        String topicoFallidos = properties.kafka().topicos().leadCalificadoDlt();
        // Partición -1: decide Kafka. Por defecto se usa la del mensaje original, y no sabemos cuántas tiene el tópico
        // de calificacion.
        DeadLetterPublishingRecoverer recuperador = new DeadLetterPublishingRecoverer(
                new KafkaTemplate<>(productorFallidos), (mensaje, falla) -> new TopicPartition(topicoFallidos, -1));
        DefaultErrorHandler manejador = new DefaultErrorHandler((mensaje, falla) -> {
            log.warn("Mensaje de {} enviado a {}: partición {}, offset {}, clave {}, causa: {}", mensaje.topic(),
                    topicoFallidos, mensaje.partition(), mensaje.offset(), mensaje.key(), causa(falla));
            recuperador.accept(mensaje, falla);
        }, espera(properties.historial().reintentos()));
        manejador.addNotRetryableExceptions(MensajeInvalidoException.class);
        return manejador;
    }

    /**
     * La causa sin valores del mensaje: el detalle de {@link MensajeInvalidoException} (nombra campo y regla) o, para
     * cualquier otra falla, solo el tipo de la excepción de origen, porque su texto puede incluir datos de la sentencia.
     */
    static String causa(Throwable falla) {
        Throwable origen = falla;
        for (Throwable t = falla; t != null; t = t.getCause()) {
            if (t instanceof MensajeInvalidoException invalido) {
                return invalido.getMessage();
            }
            origen = t;
        }
        return origen.getClass().getSimpleName();
    }

    static BackOff espera(ClientesProperties.Historial.Reintentos reintentos) {
        if (reintentos.intentos() == 0) {
            return new FixedBackOff(0, 0);
        }
        ExponentialBackOffWithMaxRetries espera = new ExponentialBackOffWithMaxRetries(reintentos.intentos());
        long inicial = reintentos.esperaInicial().toMillis();
        espera.setInitialInterval(inicial);
        espera.setMultiplier(reintentos.multiplicador());
        // Sin el tope por defecto de 30 s: la última espera es la que dice la configuración.
        espera.setMaxInterval(Math.max(inicial, (long) (inicial
                * Math.pow(reintentos.multiplicador(), Math.max(0, reintentos.intentos() - 1)))));
        return espera;
    }

    @Override
    public void destroy() {
        if (productorFallidos != null) {
            productorFallidos.destroy();
        }
    }
}
