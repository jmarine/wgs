
package org.wgs.wamp.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.wgs.wamp.type.WampMatchType;


@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface WampSubscribed {
    String topic() default "";
    WampMatchType match() default WampMatchType.exact;
    String[] metatopics() default {};
    boolean metaonly() default false;
}

