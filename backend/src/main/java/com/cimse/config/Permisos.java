package com.cimse.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Consulta los permisos del usuario que hace la peticion (la suma de los permisos de todos sus roles). */
public final class Permisos {

    private Permisos() {
    }

    public static boolean tiene(String codigo) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) {
            return false;
        }
        for (GrantedAuthority g : a.getAuthorities()) {
            if (("PERM_" + codigo).equals(g.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
