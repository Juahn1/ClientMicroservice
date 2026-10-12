package com.uade.crm.clientes.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface HistorialCalificacionJpaRepository extends JpaRepository<HistorialCalificacionEntity, UUID> {

    List<HistorialCalificacionEntity> findByClienteIdOrderByCalculadaEnDescRegistradaEnDescIdAsc(UUID clienteId);

    /**
     * Registra la calificación solo si el cliente sigue activo y si no estaba registrada (design D4 del change 08).
     * {@code FOR SHARE} sobre la fila del cliente la ordena con una anonimización concurrente, que la toma con
     * {@code FOR UPDATE}: si la anonimización llegó antes, el registro espera, ve {@code ANONIMIZADO} y no inserta; si
     * llegó después, espera a que el registro confirme y su borrado lo encuentra. {@code ON CONFLICT} descarta el
     * duplicado de un evento repetido. Devuelve las filas insertadas (0 o 1).
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO historial_calificacion
                (id, cliente_id, lead_id, nivel, puntaje, origen, calculada_en, observacion, motivos, registrada_en)
            SELECT :id, c.id, :leadId, :nivel, :puntaje, :origen, :calculadaEn, :observacion,
                   CAST(:motivos AS jsonb), :registradaEn
              FROM cliente c
             WHERE c.id = :clienteId AND c.estado = 'ACTIVO'
               FOR SHARE
            ON CONFLICT (lead_id, origen, calculada_en) DO NOTHING
            """)
    int registrar(UUID id, UUID clienteId, UUID leadId, String nivel, int puntaje, String origen,
            Instant calculadaEn, String observacion, String motivos, Instant registradaEn);

    @Modifying
    @Query("delete from HistorialCalificacionEntity h where h.clienteId = :clienteId")
    int eliminarDelCliente(UUID clienteId);
}
