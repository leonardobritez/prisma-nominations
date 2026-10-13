package com.prisma.nominations.worker.abm;

import com.prisma.nominations.persistence.domain.Nomination;

/**
 * Puerto de salida hacia el sistema ABM. La implementacion concreta (HTTP real o simulador)
 * se selecciona por perfil de Spring, manteniendo el dominio desacoplado del transporte.
 */
public interface AbmClient {

    /**
     * Envia la nominacion a ABM de forma asincronica.
     *
     * @return el resultado de la aceptacion (con referencia externa).
     * @throws AbmTechnicalException   ante fallas tecnicas recuperables (timeout, 5xx, conexion).
     * @throws AbmFunctionalException  ante rechazo funcional en el envio (no reintentable).
     */
    AbmDispatchResult dispatch(Nomination nomination);
}
