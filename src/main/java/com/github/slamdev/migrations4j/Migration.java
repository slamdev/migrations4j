package com.github.slamdev.migrations4j;

import lombok.NonNull;
import lombok.Value;

import java.util.function.Consumer;

@SuppressWarnings("WeakerAccess")
@Value
public class Migration<T> {

    @NonNull
    Consumer<T> runner;

    @NonNull
    String name;

    @NonNull
    String version;
}
