package com.cimse.seguridad;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * AOP: cualquier metodo marcado con @Auditable queda registrado en la tabla auditoria
 * con el usuario que viene en el token JWT.
 */
@Aspect
@Component
public class AuditoriaAspect {

    private final AuditoriaService auditoria;

    public AuditoriaAspect(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    public void registrarAuditoria(JoinPoint joinPoint, Auditable auditable, Object result) {
        auditoria.registrar(auditoria.idUsuarioActual(), auditable.accion(), auditable.tabla(), null,
                null, "Operacion exitosa: " + joinPoint.getSignature().getName());
    }
}
