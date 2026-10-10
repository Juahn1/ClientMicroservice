package com.uade.crm.clientes.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface SituacionCrediticiaJpaRepository extends JpaRepository<SituacionCrediticiaEntity, UUID> {

    Optional<SituacionCrediticiaEntity> findByClienteIdAndVigenteHastaAfter(UUID clienteId, Instant ahora);

    /**
     * Upsert que solo inserta si el cliente sigue activo (design D4 del change 05). {@code FOR SHARE} sobre la fila del
     * cliente ordena el guardado con una anonimización concurrente, que la toma con {@code FOR UPDATE}: si la
     * anonimización llegó antes, el guardado espera, ve {@code ANONIMIZADO} y no inserta; si llegó después, espera a
     * que el guardado confirme y su borrado lo encuentra. Devuelve las filas afectadas (0 o 1).
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO situacion_crediticia
                (cliente_id, estado, situacion, periodo, fuente, consultada_en, vigente_hasta)
            SELECT c.id, :estado, CAST(:situacion AS SMALLINT), :periodo, :fuente, :consultadaEn, :vigenteHasta
              FROM cliente c
             WHERE c.id = :clienteId AND c.estado = 'ACTIVO'
               FOR SHARE
            ON CONFLICT (cliente_id) DO UPDATE SET
                estado = EXCLUDED.estado,
                situacion = EXCLUDED.situacion,
                periodo = EXCLUDED.periodo,
                fuente = EXCLUDED.fuente,
                consultada_en = EXCLUDED.consultada_en,
                vigente_hasta = EXCLUDED.vigente_hasta
            """)
    int guardar(UUID clienteId, String estado, Integer situacion, String periodo, String fuente, Instant consultadaEn,
            Instant vigenteHasta);

    @Modifying
    @Query("delete from SituacionCrediticiaEntity s where s.clienteId = :clienteId")
    int eliminar(UUID clienteId);
}
