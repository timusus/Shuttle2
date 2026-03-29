# Music Search Redesign: First Principles Approach

## Problem Analysis

### Current Approach Issues
1. **Performance**: Jaro-Winkler on 10,000+ songs is O(n*m) - computing similarity for every item, every keystroke
2. **User expectations mismatch**:
   - Users expect instant prefix matching ("beat" → "Beatles")
   - Current approach treats "beat" and "Beatles" as fuzzy (0.71 similarity) rather than prefix
3. **No indexing**: Linear scan through all items on every search

### What Users Actually Expect

From studying Spotify, Apple Music, YouTube Music:

1. **Speed**: Results in < 50ms as they type
2. **Prefix matching**: "beat" finds "Beatles", "Beat It", "Beautiful"
3. **Substring matching**: "moon" finds "Blue Moon", "Fly Me to the Moon"
4. **Typo tolerance**: "beatels" → "Beatles" (1-2 character mistakes)
5. **Multi-word**: "dark side" finds "The Dark Side of the Moon"
6. **Smart ranking**:
   - Exact matches rank highest
   - Prefix matches next
   - Song name matches > Artist > Album
   - Popular songs rank higher

## Industry Best Practices

### What Spotify/Apple Music Use

1. **Elasticsearch/Solr**: Inverted indices with:
   - N-gram tokenization for fuzzy matching
   - Prefix trees for autocomplete
   - BM25 ranking algorithm

2. **Multi-tier search**:
   - Tier 1: Exact/prefix from index (fast, 90% of queries)
   - Tier 2: N-gram fuzzy from index (medium, 9% of queries)
   - Tier 3: Edit distance re-ranking (slow, 1% of queries, top-N only)

3. **Ranking signals**:
   - Field priority (title > artist > album)
   - Match type (exact > prefix > substring > fuzzy)
   - Popularity (play count, recency)
   - Edit distance (for typos)

### Why SQLite FTS is Perfect for This

Android music apps have a unique advantage: **SQLite FTS5**

Benefits:
- ✅ Built into Android, no dependencies
- ✅ Blazing fast prefix queries (indexed)
- ✅ BM25 ranking built-in
- ✅ Trigram support for substring matching
- ✅ Highlight/snippet support (for UI)
- ✅ Memory efficient (disk-based indices)
- ✅ Works with 100,000+ songs

## Optimal Solution: Three-Tier Search

### Architecture

```
Query: "beat"
    ↓
┌─────────────────────────────────────┐
│ Tier 1: FTS Prefix Match (indexed) │ ← 90% of queries end here
│ - "Beatles", "Beat It", "Beatbox"  │   < 10ms
└─────────────────────────────────────┘
    ↓ (if < 10 results)
┌─────────────────────────────────────┐
│ Tier 2: FTS Trigram (indexed)      │ ← 9% of queries
│ - "Heartbeat", "Upbeat"             │   < 30ms
└─────────────────────────────────────┘
    ↓ (if < 10 results)
┌─────────────────────────────────────┐
│ Tier 3: Levenshtein on Top-N       │ ← 1% of queries
│ - "Beatels" → "Beatles"             │   < 50ms (only top 100)
└─────────────────────────────────────┘
```

### Tier 1: FTS5 Exact/Prefix Matching

**Database Schema:**
```sql
CREATE VIRTUAL TABLE song_fts USING fts5(
    name,
    artist,
    album,
    content=songs,  -- Link to real table
    tokenize='porter unicode61'
);

-- Triggers to keep FTS in sync
CREATE TRIGGER songs_ai AFTER INSERT ON songs BEGIN
    INSERT INTO song_fts(rowid, name, artist, album)
    VALUES (new.id, new.name, new.artistName, new.albumName);
END;
```

**Query:**
```sql
-- Prefix query (beat*)
SELECT
    s.*,
    fts.rank,
    highlight(song_fts, 0, '<b>', '</b>') as name_highlight
FROM song_fts fts
JOIN songs s ON s.id = fts.rowid
WHERE song_fts MATCH 'name:beat* OR artist:beat* OR album:beat*'
ORDER BY
    CASE
        WHEN name LIKE 'beat%' THEN 1000  -- Exact prefix
        WHEN artist LIKE 'beat%' THEN 900
        WHEN album LIKE 'beat%' THEN 800
        ELSE 0
    END + rank DESC
LIMIT 50;
```

**Performance**: ~5-10ms for 10,000 songs (indexed)

### Tier 2: Trigram Substring Matching

**For queries ≥ 3 characters, use trigrams:**
```sql
-- "moon" → ["moo", "oon"]
CREATE INDEX idx_song_name_trigram ON songs((SUBSTR(name, 1, 3)));
CREATE INDEX idx_song_name_trigram2 ON songs((SUBSTR(name, 2, 3)));
-- etc...
```

**Or use FTS5 with substring:**
```sql
WHERE song_fts MATCH 'name:*moon* OR artist:*moon* OR album:*moon*'
```

**Performance**: ~20-30ms

### Tier 3: Typo Tolerance (Levenshtein)

**Only for top N candidates from Tier 1/2:**
```kotlin
// Levenshtein is simpler and faster than Jaro-Winkler
fun levenshteinDistance(a: String, b: String): Int {
    // Classic dynamic programming
    // Only compute for top 100 candidates
}

// Apply only if edit distance ≤ 2
results.filter { levenshteinDistance(query, it.name) <= 2 }
```

**Performance**: ~10ms for 100 candidates

## Ranking Algorithm

```kotlin
fun rankScore(result: SearchResult, query: String): Double {
    var score = 0.0

    // 1. Match type (1000-0)
    score += when {
        result.name.equals(query, ignoreCase = true) -> 1000.0  // Exact
        result.name.startsWith(query, ignoreCase = true) -> 900.0  // Prefix
        result.name.contains(query, ignoreCase = true) -> 700.0  // Substring
        else -> 500.0  // Fuzzy
    }

    // 2. Field priority (100-0)
    score += when (result.matchedField) {
        Field.SONG_NAME -> 100.0
        Field.ARTIST -> 80.0
        Field.ALBUM -> 60.0
    }

    // 3. Match position (50-0)
    score += 50.0 * (1.0 - result.matchPosition / result.name.length)

    // 4. Popularity (50-0)
    score += min(50.0, result.playCount / 10.0)

    // 5. Recency (25-0)
    score += if (result.lastPlayed != null) 25.0 else 0.0

    // 6. Edit distance penalty (-50-0)
    score -= levenshteinDistance(query, result.name) * 10.0

    // 7. Length penalty (prefer shorter, more relevant)
    score += 20.0 * (1.0 - result.name.length / 100.0)

    return score
}
```

## Implementation Plan

### Phase 1: Database Schema
1. Add FTS5 virtual tables for songs, albums, artists
2. Add triggers to keep FTS in sync
3. Add migration

### Phase 2: Repository Layer
```kotlin
interface SearchRepository {
    suspend fun searchFTS(query: String): List<SearchResult>
    suspend fun searchTrigram(query: String): List<SearchResult>
}
```

### Phase 3: Search Service
```kotlin
class MusicSearchService {
    suspend fun search(query: String): List<SearchResult> {
        if (query.length < 2) return emptyList()

        val results = mutableListOf<SearchResult>()

        // Tier 1: FTS prefix
        val ftsResults = searchRepository.searchFTS(query)
        results.addAll(ftsResults)

        // Tier 2: Trigram (if needed)
        if (results.size < 10 && query.length >= 3) {
            val trigramResults = searchRepository.searchTrigram(query)
            results.addAll(trigramResults.filter { it !in results })
        }

        // Tier 3: Fuzzy re-rank (if needed)
        if (results.size < 10) {
            val candidates = getTopCandidates(100)
            val fuzzyResults = fuzzyMatch(query, candidates)
            results.addAll(fuzzyResults)
        }

        // Rank and return
        return results
            .map { it to rankScore(it, query) }
            .sortedByDescending { it.second }
            .take(50)
            .map { it.first }
    }
}
```

### Phase 4: UI Layer
- Keep existing SearchPresenter structure
- Replace Jaro-Winkler computation with searchService.search()
- Use FTS highlight() for matched character highlighting

## Comparison: Current vs Proposed

| Aspect | Current (Jaro-Winkler) | Proposed (FTS + Tiered) |
|--------|------------------------|-------------------------|
| **Performance (10K songs)** | ~500ms (linear scan) | ~10ms (indexed) |
| **Prefix match** | No (treats as fuzzy) | Yes (instant) |
| **Substring match** | No | Yes (trigram) |
| **Typo tolerance** | Yes (but slow) | Yes (fast, top-N only) |
| **Multi-word** | Limited | Excellent (FTS phrases) |
| **Ranking quality** | Single metric | Multi-signal |
| **Memory usage** | High (in-memory scan) | Low (disk indices) |
| **Scales to 100K+** | No | Yes |

## Expected User Experience

### Query: "beat"
**Current**:
- Computes Jaro-Winkler for all 10,000 songs
- Returns fuzzy matches (0.7+ similarity)
- ~500ms

**Proposed**:
1. FTS prefix: `beat*` → Beatles, Beat It, Heartbeat
2. Ranked by: exact prefix > song name > popularity
3. Results in ~10ms ✨

### Query: "dark side"
**Current**:
- Splits to ["dark", "side"]
- Matches each word separately
- Complex scoring

**Proposed**:
1. FTS phrase: `"dark side"`
2. Matches: "Dark Side of the Moon"
3. Perfect ranking
4. ~10ms ✨

### Query: "beatels" (typo)
**Current**:
- Jaro-Winkler finds "Beatles" (0.93 similarity)
- Works but slow

**Proposed**:
1. FTS finds "beatles" (soundex/metaphone)
2. Levenshtein confirms (edit distance = 1)
3. ~15ms ✨

## Migration Strategy

### Option A: Big Bang (Recommended)
1. Add FTS tables in single migration
2. Switch SearchPresenter to new service
3. Remove Jaro-Winkler code
4. Ship it

### Option B: Progressive
1. Add FTS alongside existing
2. A/B test performance
3. Gradually shift traffic
4. Remove old code

## Conclusion

The current Jaro-Winkler approach is **academically interesting but practically suboptimal** for music search:

- ❌ Too slow (linear scan)
- ❌ Doesn't match user expectations (prefix, substring)
- ❌ Single ranking metric
- ❌ Doesn't scale

The **FTS + tiered approach** is what industry uses:

- ✅ 50x faster
- ✅ Matches user expectations perfectly
- ✅ Multi-signal ranking
- ✅ Scales to millions of songs
- ✅ Built into Android (no dependencies)

**Recommendation**: Implement the FTS-based solution. It's how Spotify, Apple Music, and every professional music app does search.
