package com.dawnduskterminal.compat;

import com.dawnduskterminal.DawnDuskTerminal;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

public final class FeatureOrderCompat {
    private static final Comparator<FeatureData> ORDER =
        Comparator.comparingInt(FeatureData::step).thenComparingInt(FeatureData::featureIndex);

    private FeatureOrderCompat() {}

    private record FeatureData(int featureIndex, int step, PlacedFeature feature) {
    }

    public static <T> List<FeatureSorter.StepFeatureData> buildFeaturesPerStep(
            List<T> biomes, Function<T, List<HolderSet<PlacedFeature>>> features) {
        Object2IntMap<PlacedFeature> indices = new Object2IntOpenHashMap<>();
        MutableInt counter = new MutableInt(0);
        Map<FeatureData, Set<FeatureData>> graph = new TreeMap<>(ORDER);
        int stepCount = 0;

        for (T biome : biomes) {
            List<FeatureData> entries = new ArrayList<>();
            List<HolderSet<PlacedFeature>> steps = features.apply(biome);
            stepCount = Math.max(stepCount, steps.size());
            for (int step = 0; step < steps.size(); step++) {
                for (Holder<PlacedFeature> holder : steps.get(step)) {
                    PlacedFeature feature = holder.value();
                    int index;
                    if (indices.containsKey(feature)) {
                        index = indices.getInt(feature);
                    } else {
                        index = counter.getAndIncrement();
                        indices.put(feature, index);
                    }
                    entries.add(new FeatureData(index, step, feature));
                }
            }
            for (int i = 0; i < entries.size(); i++) {
                Set<FeatureData> successors = graph.computeIfAbsent(entries.get(i), key -> new TreeSet<>(ORDER));
                if (i < entries.size() - 1) {
                    successors.add(entries.get(i + 1));
                }
            }
        }

        List<FeatureData> order = topological(graph);
        List<FeatureSorter.StepFeatureData> result = new ArrayList<>(stepCount);
        for (int step = 0; step < stepCount; step++) {
            List<PlacedFeature> perStep = new ArrayList<>();
            for (FeatureData data : order) {
                if (data.step() == step) {
                    perStep.add(data.feature());
                }
            }
            result.add(new FeatureSorter.StepFeatureData(perStep, Util.createIndexIdentityLookup(perStep)));
        }
        return result;
    }

    private static List<FeatureData> topological(Map<FeatureData, Set<FeatureData>> graph) {
        Map<FeatureData, Integer> indegree = new HashMap<>();
        for (FeatureData node : graph.keySet()) {
            indegree.put(node, 0);
        }
        for (Set<FeatureData> successors : graph.values()) {
            for (FeatureData successor : successors) {
                indegree.merge(successor, 1, Integer::sum);
            }
        }

        TreeSet<FeatureData> ready = new TreeSet<>(ORDER);
        for (Map.Entry<FeatureData, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
            }
        }

        Set<FeatureData> emitted = new HashSet<>();
        List<FeatureData> order = new ArrayList<>(indegree.size());
        int broken = 0;

        while (emitted.size() < indegree.size()) {
            FeatureData node = null;
            while (!ready.isEmpty()) {
                FeatureData candidate = ready.pollFirst();
                if (!emitted.contains(candidate)) {
                    node = candidate;
                    break;
                }
            }
            if (node == null) {
                FeatureData best = null;
                for (FeatureData candidate : indegree.keySet()) {
                    if (emitted.contains(candidate)) {
                        continue;
                    }
                    if (best == null || ORDER.compare(candidate, best) < 0) {
                        best = candidate;
                    }
                }
                if (best == null) {
                    break;
                }
                node = best;
                broken++;
            }
            emitted.add(node);
            order.add(node);
            for (FeatureData successor : graph.getOrDefault(node, Set.of())) {
                if (emitted.contains(successor)) {
                    continue;
                }
                if (indegree.merge(successor, -1, Integer::sum) <= 0) {
                    ready.add(successor);
                }
            }
        }

        if (broken > 0) {
            DawnDuskTerminal.LOGGER.warn(
                "DawnDuskTerminal relaxed {} feature order cycle(s) across {} feature entries, biome families were mixed",
                broken, order.size());
        }
        return order;
    }
}
