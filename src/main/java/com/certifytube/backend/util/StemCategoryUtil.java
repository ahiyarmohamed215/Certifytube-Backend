package com.certifytube.backend.util;

import com.certifytube.backend.model.YouTubeVideoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility for determining whether a YouTube video category qualifies as STEM.
 * YouTube category IDs: 26 = How-to & Style, 27 = Education, 28 = Science & Technology.
 */
public final class StemCategoryUtil {

    private static final Logger log = LoggerFactory.getLogger(StemCategoryUtil.class);

    private static final Set<String> STEM_CATEGORIES = Set.of("26", "27", "28");
    
    // Fallback regex pattern for videos uploaded under wrong categories (like People & Blogs).
    // Uses (?<!\w) / (?!\w) instead of \b so that keywords ending in special characters
    // (c++, c#, .net) are matched correctly. \b fails between two non-word characters.
    private static final Pattern STEM_KEYWORDS = Pattern.compile(
        "(?i)(?<!\\w)(oop|object\\s*oriented|programming|coding|tutorial|python|java|javascript|react|vue|angular|spring\\s*boot|machine\\s*learning|data\\s*science|algorithm|software|developer|sql|database|c\\+\\+|html|css|php|ruby|golang|rust|swift|kotlin|c#|dotnet|\\.net|aws|cloud|azure|docker|kubernetes|devops|math|calculus|algebra|physics|chemistry|biology|engineering|electronics|arduino|raspberry\\s*pi)(?!\\w)"
    );

    private StemCategoryUtil() {
    }

    /**
     * Returns true if the given YouTube video is considered STEM, either by category or keyword fallback.
     */
    public static boolean isStemVideo(YouTubeVideoCache video) {
        if (video == null) {
            log.debug("STEM check: video is null → false");
            return false;
        }
        return isStemContent(video.getCategoryId(), video.getTitle(), video.getDescription());
    }

    /**
     * Returns true if the given category / title / description qualifies as STEM.
     * Use this overload when you don't have a full {@link YouTubeVideoCache} entity.
     */
    public static boolean isStemContent(String categoryId, String title, String description) {
        // 1. Strict Category Match
        if (categoryId != null && STEM_CATEGORIES.contains(categoryId.trim())) {
            log.debug("STEM check: categoryId={} matched → true", categoryId);
            return true;
        }
        
        // 2. Keyword Fallback: Check title and description for STEM topics
        String safeTitle = title != null ? title : "";
        String safeDesc  = description != null ? description : "";
        
        boolean titleMatch = STEM_KEYWORDS.matcher(safeTitle).find();
        boolean descMatch  = !titleMatch && STEM_KEYWORDS.matcher(safeDesc).find();
        
        if (titleMatch || descMatch) {
            log.debug("STEM check: keyword matched in {} → true", titleMatch ? "title" : "description");
            return true;
        }
        
        log.debug("STEM check: categoryId={}, title='{}' → false (no category or keyword match)",
                categoryId, safeTitle);
        return false;
    }
}
