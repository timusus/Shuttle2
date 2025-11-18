# Search Highlighting Implementation

## Overview

The search highlighting system works in conjunction with the composite scoring system to provide visual feedback about which parts of search results matched the user's query.

## Two-Stage System

### Stage 1: Filtering (Composite Score)
The **composite score** determines whether an item appears in search results at all:
```kotlin
.filter { it.compositeScore > StringComparison.threshold }
```

The composite score:
- Weighs different fields by importance (name > artist > album)
- Takes the maximum weighted score across all fields
- Boosts exact matches

### Stage 2: Highlighting (Individual Field Scores)
Once an item passes the filter, **individual field scores** determine what gets highlighted:

```kotlin
if (jaroSimilarity.nameJaroSimilarity.score >= StringComparison.threshold) {
    jaroSimilarity.nameJaroSimilarity.bMatchedIndices.forEach { (index, score) ->
        // Highlight character at 'index' with color based on 'score'
    }
}
```

## Why This Design Works

### ✅ Transparency
Users can see exactly which fields matched their query. If they search "beatles" and see a song, they'll see highlighting on the artist name, making it clear why it matched.

### ✅ Accuracy
Only fields that meaningfully contributed to the match (score >= threshold) are highlighted. Weak matches aren't misleadingly emphasized.

### ✅ Visual Feedback
The color intensity of highlighting reflects how well each character matched:
```kotlin
ArgbEvaluator().evaluate(score.toFloat() - 0.25f, textColor, accentColor)
```
- Higher scores → More accent color (stronger match)
- Lower scores → More text color (weaker match)

## Examples

### Example 1: Artist Search
**Query**: "beatles"
**Result**: Song "Help!" by "The Beatles"

- **Composite score**: 0.85 (artist match weighted 0.85) → Item appears
- **Song name score**: 0.20 → Not highlighted (< threshold)
- **Artist name score**: 1.0 → **Highlighted** (≥ threshold)
- **Album name score**: 0.30 → Not highlighted (< threshold)

User sees: "Help!" with **"The Beatles"** highlighted, making it obvious why it matched.

### Example 2: Multi-Field Match
**Query**: "abbey road"
**Result**: Song "Come Together" from "Abbey Road" by "The Beatles"

- **Composite score**: 1.0 (exact album name match) → Item appears
- **Song name score**: 0.25 → Not highlighted
- **Artist name score**: 0.30 → Not highlighted
- **Album name score**: 1.0 → **Highlighted**

User sees: "Come Together" with **"Abbey Road"** highlighted.

### Example 3: Song Name Match
**Query**: "help"
**Result**: Song "Help!" by "The Beatles"

- **Composite score**: 0.95 (song name match weighted 1.0) → Item appears
- **Song name score**: 0.95 → **Highlighted** (≥ threshold)
- **Artist name score**: 0.20 → Not highlighted
- **Album name score**: 0.95 → **Highlighted** (album also named "Help!")

User sees: **"Help!"** by "The Beatles" • **"Help!"**

## Multi-Word Matching and Index Offsets

When matching queries against multi-word strings, the `bMatchedIndices` are correctly offset:

### Example: "beatles" → "The Beatles"
The multi-word matching algorithm:
1. Tries full string match: "beatles" vs "the beatles" → score ~0.88
2. Falls back to word-by-word: "beatles" vs "the" (0.30), "beatles" vs "beatles" (1.0)
3. Returns best match with **offset indices**

```kotlin
// "The Beatles"
// Indices: 01234567890
// Match: "beatles" at indices 4-10

bMatchedIndices = {
    4: 1.0,  // 'b'
    5: 1.0,  // 'e'
    6: 1.0,  // 'a'
    7: 1.0,  // 't'
    8: 1.0,  // 'l'
    9: 1.0,  // 'e'
    10: 1.0  // 's'
}
```

The UI applies these indices directly to "The Beatles", correctly highlighting positions 4-10.

## Edge Cases Handled

### 1. Unicode Normalization
The Jaro-Winkler algorithm normalizes strings (NFD), which can cause index mismatches:

```kotlin
try {
    nameStringBuilder.setSpan(
        ForegroundColorSpan(...),
        index,
        index + 1,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
} catch (e: IndexOutOfBoundsException) {
    // Normalization caused index mismatch - gracefully skip
}
```

### 2. Null Fields
If a field is null, it gets a default score of 0.0:

```kotlin
val nameJaroSimilarity = song.name?.let {
    StringComparison.jaroWinklerMultiDistance(query, it)
} ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())
```

No highlighting occurs for null fields.

### 3. Exact Matches Above Threshold
Even though the composite score boosts exact matches (> 1.0), highlighting uses the **original field score** (≤ 1.0), so the color calculation remains correct.

## Composite Score vs Individual Scores

### Scenario: One Field Barely Passes, Others Don't

```
Song: "Yesterday" by "The Beatles" from "Help!"
Query: "yesterda" (typo)

nameScore = 0.95        → weighted: 0.95 * 1.0 = 0.95
artistScore = 0.15      → weighted: 0.15 * 0.85 = 0.13
albumScore = 0.20       → weighted: 0.20 * 0.75 = 0.15

compositeScore = max(0.95, 0.13, 0.15) = 0.95 > 0.85 ✓ (appears in results)

Highlighting:
- Song name: 0.95 >= 0.85 → Highlighted ✓
- Artist: 0.15 < 0.85 → Not highlighted ✓
- Album: 0.20 < 0.85 → Not highlighted ✓
```

Perfect! Only the song name is highlighted, showing exactly what matched.

## Implementation Details

### SearchSongBinder
```kotlin
private fun highlightMatchedStrings(viewBinder: SearchSongBinder) {
    // 1. Song name
    if (viewBinder.jaroSimilarity.nameJaroSimilarity.score >= StringComparison.threshold) {
        // Highlight matched indices in song name
    }

    // 2. Artist vs Album Artist (show whichever has higher score)
    if (artistScore >= albumArtistScore) {
        if (viewBinder.jaroSimilarity.artistNameJaroSimilarity.score >= threshold) {
            // Highlight artist name
        }
    } else {
        if (viewBinder.jaroSimilarity.albumArtistNameJaroSimilarity.score >= threshold) {
            // Highlight album artist name
        }
    }

    // 3. Album name
    if (viewBinder.jaroSimilarity.albumNameJaroSimilarity.score >= StringComparison.threshold) {
        // Highlight album name
    }
}
```

### SearchAlbumBinder
```kotlin
private fun highlightMatchedStrings(viewBinder: SearchAlbumBinder) {
    // 1. Album name
    if (viewBinder.jaroSimilarity.nameJaroSimilarity.score >= threshold) {
        // Highlight album name
    }

    // 2. Artist name
    if (viewBinder.jaroSimilarity.albumArtistNameJaroSimilarity.score >= threshold) {
        // Highlight artist name
    }
}
```

### SearchAlbumArtistBinder
```kotlin
private fun highlightMatchedStrings(viewBinder: SearchAlbumArtistBinder) {
    // Artist name
    if (viewBinder.jaroSimilarity.albumArtistNameJaroSimilarity.score >= threshold) {
        // Highlight artist name
    }
}
```

## Color Intensity Calculation

The `ArgbEvaluator` interpolates between text color and accent color based on match strength:

```kotlin
val color = ArgbEvaluator().evaluate(
    score.toFloat() - 0.25f,  // Adjust score to 0.0-0.75 range
    textColor,                 // Weak match color
    accentColor                // Strong match color
) as Int
```

- Score 1.0 (perfect) → 0.75 blend → More accent color
- Score 0.90 → 0.65 blend → Mix of both
- Score 0.85 (threshold) → 0.60 blend → More text color

## Testing

Comprehensive tests verify:
- ✅ Index offsets for multi-word matching (`StringComparisonTest.kt`)
- ✅ Composite scoring behavior (`SearchScoringTest.kt`)
- ✅ Edge cases (normalization, null fields, transpositions)
- ✅ Real-world highlighting scenarios

## Summary

The highlighting system is **well-designed and correctly aligned** with the new composite scoring:

1. **Composite scores** determine visibility (what appears)
2. **Individual field scores** determine highlighting (what's emphasized)
3. **Index offsets** correctly handle multi-word matching
4. **Color intensity** reflects match strength
5. **Edge cases** are gracefully handled with try-catch

This provides an intuitive, transparent search experience where users always understand why results appeared and which parts matched their query.
