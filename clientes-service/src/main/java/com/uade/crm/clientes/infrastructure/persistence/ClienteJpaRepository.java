package com.uade.crm.clientes.infrastructure.persistence;

import com.uade.crm.clientes.domain.model.EstadoCliente;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ClienteJpaRepository extends JpaRepository<ClienteEntity, UUID>, JpaSpecificationExecutor<ClienteEntity> {

    Optional<ClienteEntity> findByEmailAndEstado(String email, EstadoCliente estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ClienteEntity c where c.email = :email and c.estado = :estado")
    Optional<ClienteEntity> findParaActualizar(String email, EstadoCliente estado);

    Optional<ClienteEntity> findByIdAndEstado(UUID id, EstadoCliente estado);
}
