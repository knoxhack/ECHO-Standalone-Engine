package dev.echo.engine.runtime.module;

import java.util.ArrayList;
import java.util.List;

/** Small deterministic semantic-version constraint evaluator for module dependency checks. */
final class VersionConstraint {
    private VersionConstraint() {
    }

    static boolean matches(String version, String expression) {
        String constraint = expression == null || expression.isBlank() ? "*" : expression.trim();
        if (constraint.equals("*") || constraint.equalsIgnoreCase("latest")) return true;
        SemanticVersion actual = SemanticVersion.parse(version);
        for (String part : constraint.split(",")) {
            String term = part.trim();
            if (term.isEmpty()) continue;
            if (term.startsWith(">=")) {
                if (actual.compareTo(SemanticVersion.parse(term.substring(2))) < 0) return false;
            } else if (term.startsWith("<=")) {
                if (actual.compareTo(SemanticVersion.parse(term.substring(2))) > 0) return false;
            } else if (term.startsWith(">")) {
                if (actual.compareTo(SemanticVersion.parse(term.substring(1))) <= 0) return false;
            } else if (term.startsWith("<")) {
                if (actual.compareTo(SemanticVersion.parse(term.substring(1))) >= 0) return false;
            } else if (term.startsWith("^")) {
                SemanticVersion floor = SemanticVersion.parse(term.substring(1));
                SemanticVersion ceiling = new SemanticVersion(floor.major() + 1, 0, 0);
                if (actual.compareTo(floor) < 0 || actual.compareTo(ceiling) >= 0) return false;
            } else if (term.startsWith("~")) {
                SemanticVersion floor = SemanticVersion.parse(term.substring(1));
                SemanticVersion ceiling = new SemanticVersion(floor.major(), floor.minor() + 1, 0);
                if (actual.compareTo(floor) < 0 || actual.compareTo(ceiling) >= 0) return false;
            } else if (!actual.equals(SemanticVersion.parse(term))) {
                return false;
            }
        }
        return true;
    }

    private record SemanticVersion(int major, int minor, int patch) implements Comparable<SemanticVersion> {
        static SemanticVersion parse(String value) {
            String clean = value == null ? "" : value.trim();
            int delimiter = clean.indexOf('-');
            if (delimiter >= 0) clean = clean.substring(0, delimiter);
            delimiter = clean.indexOf('+');
            if (delimiter >= 0) clean = clean.substring(0, delimiter);
            String[] parts = clean.split("\\.");
            return new SemanticVersion(number(parts, 0), number(parts, 1), number(parts, 2));
        }

        private static int number(String[] values, int index) {
            if (index >= values.length) return 0;
            String digits = values[index].replaceAll("[^0-9].*$", "");
            if (digits.isBlank()) return 0;
            return Integer.parseInt(digits);
        }

        @Override
        public int compareTo(SemanticVersion other) {
            int result = Integer.compare(major, other.major);
            if (result != 0) return result;
            result = Integer.compare(minor, other.minor);
            return result != 0 ? result : Integer.compare(patch, other.patch);
        }
    }
}
