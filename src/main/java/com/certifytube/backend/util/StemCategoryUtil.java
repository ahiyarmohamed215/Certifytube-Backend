package com.certifytube.backend.util;

import java.util.Set;

/**
 * Utility for determining whether a YouTube video category qualifies as STEM.
 * YouTube category IDs: 26 = How-to & Style, 27 = Education, 28 = Science &
 * Technology.
 */
public final class StemCategoryUtil {

    private static final Set<String> STEM_CATEGORIES = Set.of("26", "27", "28");

    private StemCategoryUtil() {
    }

    /**
     * Returns true if the given YouTube category ID is considered STEM.
     */
    public static boolean isStemCategory(String categoryId) {
        if (categoryId == null || categoryId.isBlank())
            return false;
        return STEM_CATEGORIES.contains(categoryId.trim());
    }
}
