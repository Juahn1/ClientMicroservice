package com.uade.crm.clientes.api.dto;

import com.uade.crm.clientes.domain.model.Pagina;
import java.util.List;
import java.util.function.Function;

/**
 * Forma estable de una página en la API (design D8). No se serializa el {@code Page} de Spring Data porque su JSON
 * no es estable entre versiones.
 */
public record PaginaResponse<T>(List<T> contenido, int pagina, int tamanio, long totalElementos, int totalPaginas) {

    public static <D, T> PaginaResponse<T> de(Pagina<D> pagina, Function<D, T> mapeo) {
        Pagina<T> mapeada = pagina.map(mapeo);
        return new PaginaResponse<>(mapeada.contenido(), mapeada.pagina(), mapeada.tamanio(),
                mapeada.totalElementos(), mapeada.totalPaginas());
    }
}
