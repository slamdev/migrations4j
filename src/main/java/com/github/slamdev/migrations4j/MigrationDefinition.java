package com.github.slamdev.migrations4j;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@SuppressWarnings("WeakerAccess")
@Target(METHOD)
@Retention(RUNTIME)
public @interface MigrationDefinition {

    String name();

    String version();
}
