# Fuzzy Search Implementation Analysis

## Executive Summary

The fuzzy search implementation in Shuttle2 uses Jaro-Winkler distance to match songs, albums, and artists. While the core algorithm is implemented correctly, there are several critical issues in the ranking/sorting logic and search strategy that explain why users experience unexpected or poorly prioritized results.

## Critical Issues Found

### 1. **Bug: Copy-Paste Error in Song Sorting**
**Location**: `SearchPresenter.kt:173`
**Severity**: HIGH

```kotlin
.sortedByDescending { if (it.artistNameJaroSimilarity.score > StringComparison.threshold) it.albumArtistNameJaroSimilarity.score else 0.0 }
```

This line should be using `it.artistNameJaroSimilarity.score` but instead uses `it.albumArtistNameJaroSimilarity.score` (copy-paste error). This means:
- Artist name matches are incorrectly weighted
- The sorting is using albumArtistName score twice, making that field disproportionately important

**Impact**: Songs with matching artist names don't get properly prioritized.

---

### 2. **Backwards Sorting Priority**
**Location**: `SearchPresenter.kt:172-175` (Songs), similar in Albums and Artists
**Severity**: HIGH

The code uses multiple sequential `sortedByDescending()` calls:

```kotlin
.sortedByDescending { if (it.albumArtistNameJaroSimilarity.score > threshold) it.albumArtistNameJaroSimilarity.score else 0.0 }
.sortedByDescending { if (it.artistNameJaroSimilarity.score > threshold) it.albumArtistNameJaroSimilarity.score else 0.0 } // BUG
.sortedByDescending { if (it.albumNameJaroSimilarity.score > threshold) it.albumNameJaroSimilarity.score else 0.0 }
.sortedByDescending { if (it.nameJaroSimilarity.score > threshold) it.nameJaroSimilarity.score else 0.0 }
```

**Problem**: With stable sorting, the LAST `sortedByDescending` becomes the PRIMARY sort key. This means:
1. **Primary**: Song name match score
2. **Secondary**: Album name match score
3. **Tertiary**: Artist name match score (buggy - see issue #1)
4. **Quaternary**: Album artist name match score

This is likely backwards from user expectations. When searching for "beatles", users probably expect:
- Exact artist matches to rank highest
- Then album matches
- Then song name matches

But currently, songs with "beatles" in the title rank higher than songs BY the Beatles.

---

### 3. **No Composite Scoring**
**Location**: `SearchPresenter.kt:142-176`
**Severity**: MEDIUM-HIGH

Currently, each field is sorted independently. There's no concept of a "best overall match". This causes issues like:

**Example**: Searching for "help"
- Song A: "Help!" by The Beatles (perfect song name match: 1.0)
- Song B: "Helpless" by Neil Young (good song name match: 0.92)
- Song C: Random song by "Help Me Foundation" (artist match: 0.91)

Current logic sorts primarily by song name, so A > B > C. But there's no weighting to say "an exact match on any field should rank very high". A better approach would be to compute a composite score considering:
- The highest score across all fields
- Or a weighted combination of field scores
- Or prioritize exact matches (score = 1.0)

---

### 4. **Threshold Too Strict**
**Location**: `StringComparison.kt:8`
**Severity**: MEDIUM

```kotlin
const val threshold = 0.90
```

A Jaro-Winkler threshold of 0.90 is quite strict. This means:
- "Beatles" matches "beatles" (1.0) ✓
- "Beatles" matches "Beatle" (0.96) ✓
- "Beatles" matches "The Beatles" (0.88) ✗ **REJECTED**
- "Led Zeppelin" matches "Led Zepplin" (0.97) ✓
- "Led Zeppelin" matches "Zeppelin" (0.68) ✗ **REJECTED**

**Impact**: Partial matches, common prefixes like "The", and substring queries are often rejected entirely.

**Considerations**:
- Users might search "zeppelin" expecting to find "Led Zeppelin"
- Users might omit "The" from band names
- Typos with 1-2 character differences might get rejected

---

### 5. **Multi-Word Matching Only Splits Target, Not Query**
**Location**: `StringComparison.kt:132-150`
**Severity**: MEDIUM

The `jaroWinklerMultiDistance()` function splits the target string `b` on spaces but not the query string `a`:

```kotlin
val bSplit = b.split(" ")
```

**Problem**: If you search for "dark side moon", it won't intelligently match against "The Dark Side of the Moon". The function will try:
- "dark side moon" vs "The" → poor match
- "dark side moon" vs "Dark" → poor match
- "dark side moon" vs "Side" → poor match
- etc.

**What users expect**: Multi-word queries should match multi-word targets more intelligently, perhaps:
- Token-based matching (split both strings)
- Order-independent matching for better results
- Partial phrase matching

---

### 6. **No Field-Specific Prioritization**
**Location**: Throughout search logic
**Severity**: MEDIUM

When searching songs, all fields are treated equally in filtering:
- Song name
- Album name
- Album artist name
- Artist name

**User expectation**: When searching in the songs view, matches on the song name should rank higher than matches on the album or artist name. Similarly:
- When searching artists → artist name should be prioritized
- When searching albums → album name should be prioritized

**Current behavior**: The sorting attempts this, but because of issue #2 (backwards priority) and issue #3 (no composite scoring), it doesn't work well.

---

## Additional Observations

### 7. **Potential Index Calculation Issue**
**Location**: `StringComparison.kt:147`
**Severity**: LOW (affects highlighting, not matching)

When remapping matched indices for multi-word matching:

```kotlin
bMatchedIndices = splitSimilarity.bMatchedIndices.mapKeys {
    it.key + bIndex + bSplit.take(bIndex).sumBy { it.length }
}
```

The `bIndex` accounts for spaces between words, and `sumBy { it.length }` accounts for previous word lengths. This appears correct, but should be verified with visual highlighting tests.

### 8. **Performance Considerations**
**Location**: `SearchPresenter.kt:169-175`
**Severity**: LOW

Using `.asSequence()` for songs is good, but the multiple `sortedByDescending` calls still create intermediate collections. This could be optimized with `sortedWith(compareByDescending { ... }.thenByDescending { ... })`.

---

## Architecture Analysis

### Data Flow
1. User types in SearchFragment → 500ms debounce
2. SearchPresenter.loadData(query) called
3. For each entity type (songs/albums/artists):
   - Load all entities from repository
   - Compute Jaro-Winkler scores for all relevant fields
   - Filter by threshold (0.90)
   - Sort by individual fields (multiple passes)
4. Combine results and display

### Scoring Process (per Song)
```kotlin
SongJaroSimilarity(song, query) {
    nameJaroSimilarity = jaroWinklerMultiDistance(query, song.name)
    albumNameJaroSimilarity = jaroWinklerMultiDistance(query, song.album)
    albumArtistNameJaroSimilarity = jaroWinklerMultiDistance(query, song.albumArtist)
    artistNameJaroSimilarity = jaroWinklerMultiDistance(query, song.friendlyArtistName)
}
```

Each score is independent, and filtering accepts items where ANY score exceeds threshold.

---

## Recommendations Summary

1. **Fix the copy-paste bug** in SearchPresenter.kt:173
2. **Implement composite scoring** - compute a single "best match" score per item
3. **Reverse the sorting priority** or use `compareBy().thenBy()` for clearer intent
4. **Consider lowering threshold** to 0.85 or make it configurable
5. **Add field-specific weighting** (e.g., song name matches weighted higher when searching songs)
6. **Improve multi-word matching** by tokenizing both query and target
7. **Add exact match boosting** (score = 1.0 should rank very high)
8. **Add substring/prefix matching** as a fallback for very low Jaro scores

---

## Test Cases to Consider

### Current Failures (Hypothesized)

1. **Query: "beatles"**
   - Expected: Songs BY The Beatles rank highest
   - Actual: Songs with "beatles" in TITLE might rank higher than songs by The Beatles

2. **Query: "the beatles"**
   - Expected: Same as "beatles"
   - Actual: Lower scores due to "the" prefix (might not meet threshold)

3. **Query: "dark side"**
   - Expected: "Dark Side of the Moon" album/songs rank high
   - Actual: May rank below songs with "dark" or "side" in title

4. **Query: "zeppelin"**
   - Expected: Led Zeppelin songs/albums
   - Actual: May not match due to threshold (0.68 < 0.90)

5. **Query: "help" (short words)**
   - Expected: "Help!" by Beatles ranks high
   - Actual: May match too many things ("Helpless", "Helper", "Helping Hand", etc.)

---

## Files Involved

- `android/mediaprovider/core/src/main/java/com/simplecityapps/mediaprovider/StringComparison.kt` - Core algorithm
- `android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/home/search/SearchPresenter.kt` - Search logic and sorting
- `android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/home/search/SongJaroSimilarity.kt` - Song scoring
- `android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/home/search/AlbumJaroSimilarity.kt` - Album scoring
- `android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/home/search/ArtistJaroSimilarity.kt` - Artist scoring

---

## Next Steps

Would you like me to:
1. Fix the immediate bug (copy-paste error)?
2. Implement a comprehensive scoring and ranking overhaul?
3. Create unit tests to validate the changes?
4. Make the threshold configurable?
5. All of the above?
