package com.umesh.unipay_1.security;

import com.umesh.unipay_1.security.authorization.MerchantOnly;
import com.umesh.unipay_1.security.authorization.RequiresRole;
import com.umesh.unipay_1.security.authorization.SuperAdminOnly;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * AOP Aspect that enforces custom role-based access control annotations.
 * Role authorities are stored as "ROLE_<ROLENAME>" by JwtAuthFilter
 * (e.g. ROLE_ADMIN, ROLE_MERCHANT, ROLE_STUDENT).
 */
@Aspect
@Component
public class RoleCheckAspect {

    /**
     * Restricts access to ADMIN role only.
     * Use @SuperAdminOnly on any method to enforce this.
     */
    @Around("@annotation(com.umesh.unipay_1.security.authorization.SuperAdminOnly)")
    public Object checkSuperAdminOnly(ProceedingJoinPoint joinPoint) throws Throwable {
        checkRole("ROLE_ADMIN");
        return joinPoint.proceed();
    }

    /**
     * Restricts access to MERCHANT role only.
     * Use @MerchantOnly on any method to enforce this.
     */
    @Around("@annotation(com.umesh.unipay_1.security.authorization.MerchantOnly)")
    public Object checkMerchantOnly(ProceedingJoinPoint joinPoint) throws Throwable {
        checkRole("ROLE_MERCHANT");
        return joinPoint.proceed();
    }

    /**
     * Allows access if the authenticated user has ANY of the specified roles.
     * Role values should be prefixed with "ROLE_" (e.g. "ROLE_ADMIN", "ROLE_MERCHANT").
     *
     * Example: @RequiresRole({"ROLE_ADMIN", "ROLE_MERCHANT"})
     */
    @Around("@annotation(requiresRole)")
    public Object checkRequiresRole(ProceedingJoinPoint joinPoint, RequiresRole requiresRole) throws Throwable {
        Authentication auth = getAuthentication();
        boolean hasAnyRole = Arrays.stream(requiresRole.value())
                .anyMatch(role -> auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals(role)));
        if (!hasAnyRole) {
            throw new AccessDeniedException("Access denied. Required roles: "
                    + Arrays.toString(requiresRole.value()));
        }
        return joinPoint.proceed();
    }

    private void checkRole(String requiredAuthority) {
        Authentication auth = getAuthentication();
        boolean hasRole = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(requiredAuthority));
        if (!hasRole) {
            throw new AccessDeniedException("Access denied. Required authority: " + requiredAuthority);
        }
    }

    private Authentication getAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("User is not authenticated");
        }
        return auth;
    }
}
