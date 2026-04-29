package com.umesh.unipay_1.security.authorization;

import java.lang.annotation.*;

/**
 * Restricts access to users with the ADMIN role.
 * Note: maps to Role.ADMIN — the highest privilege level in this system.
 * Apply to methods (processed by RoleCheckAspect via AOP).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SuperAdminOnly {
}
