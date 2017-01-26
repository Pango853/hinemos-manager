package com.clustercontrol.accesscontrol.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface HinemosObjectPrivilege {
	String objectType();
	boolean isModifyCheck() default false;
}