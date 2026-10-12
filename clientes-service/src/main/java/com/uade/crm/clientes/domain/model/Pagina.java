package com.uade.crm.clientes.domain.model;

import java.util.List;
import java.util.function.Function;

/** Una página de resultados, independiente de Spring Data. {@code pagina} empieza en 0. */
public record Pagina<T>(List<T> contenido, int pagina, int tamanio, long totalElementos, int totalPaginas) {

    public Pagina {
        contenido = List.copyOf(contenido);
    }

    public <R> Pagina<R> map(Function<T, R> transformacion) {
        return new Pagina<>(contenido.stream().map(transformacion).toList(), pagina, tamanio, totalElementos,
                totalPaginas);
    }
}
