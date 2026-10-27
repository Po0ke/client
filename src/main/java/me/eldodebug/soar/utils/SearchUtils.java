package me.eldodebug.soar.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

public class SearchUtils {
	
	/**
	 * Ranks a match so results can be ordered instead of merely filtered.
	 *
	 * @return -1 when there is no match, otherwise a higher score for a better match
	 */
	public static int score(String haystack, String query) {
		
		if (haystack == null || query == null) {
			return -1;
		}
		
		String haystackLower = haystack.toLowerCase(Locale.ENGLISH);
		String queryLower = query.toLowerCase(Locale.ENGLISH).trim();
		
		if (queryLower.isEmpty()) {
			return 0;
		}
		
		if (haystackLower.startsWith(queryLower)) {
			return 100;
		}
		
		if (haystackLower.contains(" " + queryLower)) {
			return 80;
		}
		
		if (haystackLower.contains(queryLower)) {
			return 60;
		}
		
		for (String word : haystackLower.split("\\s+")) {
			
			if (word.startsWith(queryLower)) {
				return 40;
			}
			
			if (StringUtils.getLevenshteinDistance(word, queryLower) <= 1) {
				return 20;
			}
		}
		
		return -1;
	}
	
	public static boolean isSimillar(String s1, String s2) {
		return isSimillar(s1, s2, 1);
	}
	
	public static boolean isSimillar(String s1, String s2, int searchDistance) {
		
        s1 = s1.toLowerCase(Locale.ENGLISH);
        s2 = s2.toLowerCase(Locale.ENGLISH);
        
        if (s1.length() <= searchDistance) {
            return s1.contains(s2);
        }
        
        boolean similar = false;
        
        for (String a : StringUtils.split(s1)) {
            similar = a.contains(s2) || StringUtils.getLevenshteinDistance(a, s2) <= searchDistance;
            if (similar) {
            	break;
            }
        }
        
        return similar || s1.contains(s2) || StringUtils.getLevenshteinDistance(s1, s2) <= searchDistance;
	}
}
