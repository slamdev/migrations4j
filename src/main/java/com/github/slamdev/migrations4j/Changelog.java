package com.github.slamdev.migrations4j;

import lombok.NonNull;
import lombok.Value;

@SuppressWarnings("WeakerAccess")
@Value
public class Changelog {

    @NonNull
    String name;

    @NonNull
    String version;
}
