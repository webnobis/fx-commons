package com.webnobis.commons.fx.test;

import static java.lang.annotation.ElementType.MODULE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Java Fx test extension usable with test jar
 * 
 * @author steffen
 *
 */
@Documented
@Retention(SOURCE)
@Target(MODULE)
public @interface FxTestExtensions {

}
