package org.wgs.wamp.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface WampClientConfig {
    String url() default "";
    String authmethod() default "";
    String realm() default "";
    String user() default "";
    String password() default "";
    boolean digestPasswordMD5() default false;
}

