package com.uade.crm.clientes.infrastructure.persistence;

import com.uade.crm.clientes.domain.model.EstadoCliente;
import com.uade.crm.clientes.domain.port.FiltroBusqueda;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Traduce {@link FiltroBusqueda} a una {@link Specification}; los filtros presentes se combinan con AND. */
final class ClienteSpecs {

    private static final char ESCAPE = '\\';

    private ClienteSpecs() {
    }

    static Specification<ClienteEntity> de(FiltroBusqueda filtro) {
        return (raiz, consulta, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            condiciones.add(cb.equal(raiz.get("estado"), EstadoCliente.ACTIVO));
            if (filtro.telefono() != null) {
                condiciones.add(cb.equal(raiz.get("telefonoE164"), filtro.telefono().e164()));
            }
            if (filtro.email() != null) {
                condiciones.add(cb.equal(raiz.get("email"), filtro.email().valor()));
            }
            if (filtro.provincia() != null) {
                condiciones.add(cb.equal(raiz.get("provincia"), filtro.provincia()));
            }
            if (filtro.nombre() != null) {
                condiciones.add(cb.like(raiz.get("nombreBusqueda"), "%" + escaparComodines(filtro.nombre()) + "%",
                        ESCAPE));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }

    /** {@code %} y {@code _} del texto buscado se tratan como texto literal, no como comodines de LIKE. */
    static String escaparComodines(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
