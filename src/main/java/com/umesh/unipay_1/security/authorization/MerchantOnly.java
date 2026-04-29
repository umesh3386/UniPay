package com.umesh.unipay_1.security.authorization;

import java.lang.annotation.*;

/**
 * Restricts access to users with the MERCHANT role.
 * Apply to methods (processed by RoleCheckAspect via AOP).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MerchantOnly {
}
