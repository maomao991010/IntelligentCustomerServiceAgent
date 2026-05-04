package com.ticketing.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {
    String value() default "";
    String module() default "";
    OperType type() default OperType.OTHER;

    enum OperType {
        CREATE, UPDATE, DELETE, QUERY, LOGIN, LOGOUT, OTHER
    }
}
