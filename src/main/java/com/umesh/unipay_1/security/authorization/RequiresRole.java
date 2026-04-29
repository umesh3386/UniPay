package com.umesh.unipay_1.security.authorization;


import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresRole {
    String[] value();  // e.g. @RequiresRole({"ROLE_ADMIN", "ROLE_MODERATOR"})
}
