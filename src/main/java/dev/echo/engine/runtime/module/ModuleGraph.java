package dev.echo.engine.runtime.module;

import dev.echo.engine.api.ModuleDependency;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ModuleGraph {
    private ModuleGraph() {
    }

    static List<ModuleCandidate> sort(List<ModuleCandidate> candidates) {
        LinkedHashMap<String, ModuleCandidate> byId = new LinkedHashMap<>();
        for (ModuleCandidate candidate : candidates) {
            if (byId.putIfAbsent(candidate.descriptor().id(), candidate) != null) {
                throw new IllegalArgumentException("Duplicate module id: " + candidate.descriptor().id());
            }
        }

        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        byId.keySet().forEach(id -> indegree.put(id, 0));

        for (ModuleCandidate candidate : candidates) {
            for (ModuleDependency dependency : candidate.descriptor().dependencies()) {
                ModuleCandidate target = byId.get(dependency.id());
                if (target == null) {
                    if (!dependency.optional()) {
                        throw new IllegalArgumentException(
                                "Module " + candidate.descriptor().id() + " requires missing dependency " + dependency.id()
                        );
                    }
                    continue;
                }
                if (!VersionConstraint.matches(target.descriptor().version(), dependency.versionRange())) {
                    throw new IllegalArgumentException(
                            "Module " + candidate.descriptor().id() + " requires " + dependency.id() + " "
                                    + dependency.versionRange() + " but found " + target.descriptor().version()
                    );
                }
                indegree.merge(candidate.descriptor().id(), 1, Integer::sum);
                dependents.computeIfAbsent(dependency.id(), ignored -> new ArrayList<>())
                        .add(candidate.descriptor().id());
            }
        }

        ArrayDeque<String> ready = new ArrayDeque<>();
        indegree.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey)
                .sorted()
                .forEach(ready::add);

        ArrayList<ModuleCandidate> result = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.removeFirst();
            result.add(byId.get(id));
            for (String dependent : dependents.getOrDefault(id, List.of())) {
                int remaining = indegree.merge(dependent, -1, Integer::sum);
                if (remaining == 0) ready.addLast(dependent);
            }
        }
        if (result.size() != candidates.size()) throw new IllegalArgumentException("Module dependency cycle detected");
        return List.copyOf(result);
    }
}
