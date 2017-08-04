package com.github.slamdev.migrations4j;

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.*;

import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;

@SuppressWarnings({"WeakerAccess", "unused"})
@Slf4j
@Value
public class MigrationsApplier<T> {

    @NonNull
    T executor;

    @NonNull
    BiConsumer<T, Changelog> changelogCreator;

    @NonNull
    BiPredicate<T, Changelog> changelogPresenceChecker;

    public MigrationsApplier<T> applyTo(@NonNull Package packageToSearch) {
        return applyTo(packageToSearch.getName());
    }

    public MigrationsApplier<T> applyTo(@NonNull Class<?> type) {
        return applyTo(type.getName());
    }

    private MigrationsApplier<T> applyTo(String scanSpec) {
        Map<Class<?>, List<Method>> classes = new HashMap<>();
        new FastClasspathScanner(scanSpec)
                .matchClassesWithMethodAnnotation(MigrationDefinition.class,
                        (matchingClass, matchingMethodOrConstructor) -> classes.compute(matchingClass, (type, methods) -> {
                            if (methods == null) {
                                methods = new ArrayList<>();
                            }
                            methods.add((Method) matchingMethodOrConstructor);
                            return methods;
                        }))
                .scan();
        List<Migration<T>> migrations = classes.entrySet().stream()
                .flatMap(e -> e.getValue().stream().map(method -> toMigration(e.getKey(), method)))
                .collect(toList());
        return applyTo(migrations);
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows(ReflectiveOperationException.class)
    private Migration<T> toMigration(Class<?> type, Method method) {
        Object instance = type.getConstructor().newInstance();
        Consumer<T> runner = (Consumer<T>) method.invoke(instance);
        MigrationDefinition definition = method.getAnnotation(MigrationDefinition.class);
        return new Migration<>(runner, definition.name(), definition.version());
    }

    public MigrationsApplier<T> applyTo(@NonNull Migration<T> migrations) {
        return applyTo(singleton(migrations));
    }

    public MigrationsApplier<T> applyTo(@NonNull Collection<Migration<T>> migrations) {
        migrations.stream()
                .sorted(this::compareMigrations)
                .map(peek(m -> log.debug("Start processing migration: {}", m)))
                .filter(not(this::isMigrationApplied))
                .map(peek(m -> log.debug("Start applying migration: {}", m)))
                .map(peek(this::applyMigration))
                .forEach(m -> log.info("Migration applied: {}", m));
        return this;
    }

    private int compareMigrations(Migration<T> migration1, Migration<T> migration2) {
        int result = toVersion(migration1.getVersion()).compareTo(toVersion(migration2.getVersion()));
        if (result == 0) {
            throw new IllegalStateException("Migrations have the same version: " + migration1.getVersion() + " and " + migration2.getVersion());
        }
        return result;
    }

    private Version toVersion(String version) {
        String[] parts = version.split(".");
        BiFunction<String[], Integer, Integer> parser = (versions, index) -> versions.length >= index ? toSafeInt(versions[index]) : 0;
        return new Version(
                parser.apply(parts, 0),
                parser.apply(parts, 1),
                parser.apply(parts, 2)
        );
    }

    private int toSafeInt(String string) {
        try {
            return Integer.parseInt(string);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean isMigrationApplied(Migration<T> migration) {
        return changelogPresenceChecker.test(executor, toChangelog(migration));
    }

    private void applyMigration(Migration<T> migration) {
        migration.getRunner().accept(executor);
        changelogCreator.accept(executor, toChangelog(migration));
    }

    private Changelog toChangelog(Migration<T> migration) {
        return new Changelog(migration.getName(), migration.getVersion());
    }

    private static <T> Predicate<T> not(Predicate<T> t) {
        return t.negate();
    }

    private static <T> UnaryOperator<T> peek(Consumer<T> c) {
        return t -> {
            c.accept(t);
            return t;
        };
    }

    @Value
    private static class Version implements Comparable<Version> {

        int major;
        int minor;
        int patch;

        @Override
        public int compareTo(Version other) {
            int result = major - other.major;
            if (result == 0) {
                result = minor - other.minor;
                if (result == 0) {
                    result = patch - other.patch;
                }
            }
            return result;
        }
    }
}
