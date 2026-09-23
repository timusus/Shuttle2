---
name: reply-reviews
description: Weekly Play Store review flow for S2 — fetch new reviews, draft replies, get owner approval, then post. Use when asked to check Play reviews, draft review replies, or reply to app store reviews.
user_invocable: true
---

# Reply to Play Reviews

Fetch recent Google Play reviews for S2, draft a reply for each unanswered one, and stop for the
owner's approval. Never post a reply without explicit approval.

**API window:** the Play Developer API `reviews.list` endpoint only returns reviews with text from
roughly the **last 7 days**. Reviews older than that are invisible to this tool — the owner must
answer them manually in [Play Console](https://play.google.com/console).

## Steps

1. **Fetch unreplied reviews:**

   ```bash
   support/scripts/play-reviews list --unreplied --json
   ```

   If this fails with a 401/403, the `reviews-reader` service account's grant may still be
   propagating (can take up to 24h) — report that and stop, don't retry in a loop.

2. **Draft one reply per review.** Write them to `support/reviews/drafts-YYYY-MM-DD.json` (today's
   date; this directory is gitignored) as a JSON array, one object per review:

   ```json
   {
     "reviewId": "...",
     "stars": 4,
     "reviewText": "...",
     "replyText": "...",
     "approved": false
   }
   ```

   **Reply guidelines:**
   - Vary the sign-off so replies don't read as a bot: some end "Tim" or "– Tim", many have no
     sign-off at all. Vary openings too.
   - Don't repeat the complaint back to them, and don't placate ("that's a real gap", "that's fair
     feedback", "I know it hurt"). Answer plainly with what's true or what's happening.
   - Specific to the review, but short. Only reference what they said when it adds something.
   - Under 350 characters.
   - No em dashes.
   - No promises of dates ("next release", "next week", etc.) — commit to nothing time-bound.
   - Thank the reviewer for praise in positive reviews.
   - For bug reports, ask them to email tim@shuttlemusicplayer.app with details (device, steps to
     reproduce) so it can be investigated — don't try to diagnose or fix in the reply.
   - For widget complaints specifically, say widget improvements are being worked on. Only say this
     because it is currently true — check before reusing this line if that ever changes.
   - Never argue with a reviewer, even an unfair one. Acknowledge and move on.

3. **Show the drafts to the owner and stop.** Do not run `reply` or `post-approved` yet. Wait for the
   owner to review the drafts file and flip `approved` to `true` on the ones they want sent (editing
   `replyText` first if they want changes).

4. **Post only on explicit approval.** Once the owner confirms, run:

   ```bash
   support/scripts/play-reviews post-approved support/reviews/drafts-YYYY-MM-DD.json
   ```

   This posts every entry with `"approved": true` and no `"posted"` flag, then marks each as posted
   in the file. Use `--dry-run` first if there's any doubt about what would be sent.
