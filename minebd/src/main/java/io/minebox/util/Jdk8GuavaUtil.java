package io.minebox.util;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;
import java.util.stream.Collector;

public class Jdk8GuavaUtil {


    public static <T> com.google.common.base.Optional<T> convert(java.util.Optional<T> jdkOptional) {
        if (jdkOptional.isPresent()) return com.google.common.base.Optional.of(jdkOptional.get());
        return com.google.common.base.Optional.absent();
    }

    public static <T> java.util.Optional<T> convert(com.google.common.base.Optional<T> guavaOptional) {
        if (guavaOptional.isPresent()) return java.util.Optional.of(guavaOptional.get());
        return java.util.Optional.empty();
    }

    public static <T> Collector<T, ?, ImmutableList<T>> toImmutableList() {
        Supplier<ImmutableList.Builder<T>> supplier = ImmutableList.Builder::new;
        BiConsumer<ImmutableList.Builder<T>, T> accumulator = ImmutableList.Builder::add;
        BinaryOperator<ImmutableList.Builder<T>> combiner = (l, r) -> l.addAll(r.build());
        Function<ImmutableList.Builder<T>, ImmutableList<T>> finisher = ImmutableList.Builder::build;

        return Collector.of(supplier, accumulator, combiner, finisher);
    }

    public static <T> Collector<T, ?, ImmutableSet<T>> toImmutableSet() {
        Supplier<ImmutableSet.Builder<T>> supplier = ImmutableSet.Builder::new;
        BiConsumer<ImmutableSet.Builder<T>, T> accumulator = ImmutableSet.Builder::add;
        BinaryOperator<ImmutableSet.Builder<T>> combiner = (l, r) -> l.addAll(r.build());
        Function<ImmutableSet.Builder<T>, ImmutableSet<T>> finisher = ImmutableSet.Builder::build;
        return Collector.of(supplier, accumulator, combiner, finisher);
    }

    /**
     * Stateful filter. T is type of stream element, K is type of extracted key.
     */
    public static class DistinctByKey<T, K> implements Predicate<T> {
        final Map<K, Boolean> seen = new ConcurrentHashMap<>();
        final Function<T, K> keyExtractor;

        public DistinctByKey(Function<T, K> ke) {
            this.keyExtractor = ke;
        }

        public boolean filter(T t) {
            return seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
        }

        @Override
        public boolean test(T t) {
            return filter(t);
        }
    }

}
