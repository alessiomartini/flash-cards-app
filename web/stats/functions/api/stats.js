// Cloudflare Pages Function - GET /api/stats
//
// Reads the same D1 database the EngVocab app syncs vocabulary from and pushes
// review/card-add events to (see D1Client.kt). Runs a handful of cheap
// aggregate queries and returns one JSON payload the dashboard renders from.
//
// Requires a D1 binding named "DB" on the Pages project (see wrangler.toml).

const RATING_LABELS = { 1: "Again", 2: "Hard", 3: "Good", 4: "Easy" };

export async function onRequestGet(context) {
  const db = context.env.DB;

  const [cardsByLanguage, ratingRows, cardsAddedRows, reviewsByDayRows, reviewDayRows, reviewsTodayRow, totalReviewsRow] =
    await Promise.all([
      db.prepare("SELECT language, COUNT(*) AS count FROM words WHERE is_deleted = 0 GROUP BY language").all(),
      db.prepare("SELECT rating, COUNT(*) AS count FROM review_events GROUP BY rating").all(),
      db
        .prepare(
          `SELECT day, language, SUM(count) AS count FROM (
             SELECT date(created_at / 1000, 'unixepoch') AS day, language, COUNT(*) AS count
             FROM words WHERE is_deleted = 0 GROUP BY day, language
             UNION ALL
             SELECT date(added_at / 1000, 'unixepoch') AS day, language, COUNT(*) AS count
             FROM card_add_events GROUP BY day, language
           ) GROUP BY day, language ORDER BY day`,
        )
        .all(),
      db
        .prepare(
          `SELECT date(reviewed_at / 1000, 'unixepoch') AS day, language, COUNT(*) AS count
           FROM review_events GROUP BY day, language ORDER BY day`,
        )
        .all(),
      db
        .prepare(
          "SELECT DISTINCT date(reviewed_at / 1000, 'unixepoch') AS day FROM review_events ORDER BY day DESC LIMIT 400",
        )
        .all(),
      db
        .prepare(
          "SELECT COUNT(*) AS count FROM review_events WHERE date(reviewed_at / 1000, 'unixepoch') = date('now')",
        )
        .first(),
      db.prepare("SELECT COUNT(*) AS count FROM review_events").first(),
    ]);

  const cardsByLanguageMap = {};
  let totalCards = 0;
  for (const row of cardsByLanguage.results) {
    cardsByLanguageMap[row.language] = row.count;
    totalCards += row.count;
  }

  const ratingDistribution = [1, 2, 3, 4].map((rating) => ({
    rating,
    label: RATING_LABELS[rating],
    count: ratingRows.results.find((r) => r.rating === rating)?.count ?? 0,
  }));

  const body = {
    generatedAt: Date.now(),
    totals: {
      cardsByLanguage: cardsByLanguageMap,
      cards: totalCards,
      reviews: totalReviewsRow?.count ?? 0,
      reviewsToday: reviewsTodayRow?.count ?? 0,
      streakDays: currentStreakDays(reviewDayRows.results.map((r) => r.day)),
    },
    ratingDistribution,
    cardsAddedByDay: cardsAddedRows.results,
    reviewsByDay: reviewsByDayRows.results,
  };

  return Response.json(body, { headers: { "Cache-Control": "no-store" } });
}

/**
 * Consecutive days (including today, if not yet reviewed) with at least one review - mirrors
 * CardRepository.currentStreakDays() in the Android app. Days are UTC here (SQLite's date()
 * has no timezone concept) vs. the phone's local calendar day, so right around midnight in the
 * user's timezone this can read a day off from the in-app streak - a rare, self-correcting edge
 * case, not worth a timezone parameter for a single-user dashboard.
 */
function currentStreakDays(distinctDaysDesc) {
  if (distinctDaysDesc.length === 0) return 0;
  const days = new Set(distinctDaysDesc);

  const cursor = new Date();
  const dayKey = (d) => d.toISOString().slice(0, 10);
  if (!days.has(dayKey(cursor))) {
    cursor.setUTCDate(cursor.getUTCDate() - 1);
  }

  let streak = 0;
  while (days.has(dayKey(cursor))) {
    streak++;
    cursor.setUTCDate(cursor.getUTCDate() - 1);
  }
  return streak;
}
