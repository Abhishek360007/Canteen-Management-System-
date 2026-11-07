package com.tulas.canteen.repo;

/** Builds LIKE patterns for search boxes. */
public final class Search {

    private Search() {}

    /**
     * "  Asha " → "%asha%"; blank → "%" (matches everything). The user's own % and _ are escaped
     * so they're searched for literally instead of acting as wildcards.
     */
    public static String pattern(String q) {
        if (q == null || q.isBlank()) return "%";
        String escaped = q.trim().toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
