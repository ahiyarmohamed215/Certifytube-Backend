package com.certifytube.backend.util;

import com.certifytube.backend.model.YouTubeVideoCache;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility for determining whether a YouTube video category qualifies as STEM.
 * YouTube category IDs: 26 = How-to & Style, 27 = Education, 28 = Science & Technology.
 */
public final class StemCategoryUtil {

    private static final Set<String> STEM_CATEGORIES = Set.of("26", "27", "28");
    
    // Fallback regex pattern for videos uploaded under wrong categories (like People & Blogs)
    private static final Pattern STEM_KEYWORDS = Pattern.compile(
        "(?i)\\b(oop|object\\s*oriented|programming|coding|tutorial|python|java|javascript|react|vue|angular|spring\\s*boot|machine\\s*learning|data\\s*science|algorithm|software|developer|sql|database|c\\+\\+|html|css|php|ruby|golang|rust|swift|kotlin|c#|dotnet|\\.net|aws|cloud|azure|docker|kubernetes|devops|math|calculus|algebra|physics|chemistry|biology|engineering|electronics|arduino|raspberry\\s*pi)\\b"
    );

    private StemCategoryUtil() {
    }

    /**
     * Returns true if the given YouTube video is considered STEM, either by category or keyword fallback.
     */
    public static boolean isStemVideo(YouTubeVideoCache video) {
        if (video == null) {
            return false;
        }
        
        // 1. Strict Category Match
        if (video.getCategoryId() != null && STEM_CATEGORIES.contains(video.getCategoryId().trim())) {
            return true;
        }
        
        // 2. Keyword Fallback: Check title and description for STEM topics
        String title = video.getTitle() != null ? video.getTitle() : "";
        String desc = video.getDescription() != null ? video.getDescription() : "";
        
        return STEM_KEYWORDS.matcher(title).find() || STEM_KEYWORDS.matcher(desc).find();
    }
}
