const LANGUAGES = {
  en: { name: "Inglese", flag: "🇬🇧" },
  de: { name: "Tedesco", flag: "🇩🇪" },
  fr: { name: "Francese", flag: "🇫🇷" },
  nl: { name: "Olandese", flag: "🇳🇱" },
};

const RATING_COLORS = { 1: "var(--again)", 2: "var(--hard)", 3: "var(--good)", 4: "var(--easy)" };

let state = { data: null, lang: "all" };

async function main() {
  try {
    const res = await fetch("/api/stats", { cache: "no-store" });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    state.data = await res.json();
  } catch (err) {
    setStatus(`Impossibile caricare le statistiche (${err.message}). Riprova più tardi.`, true);
    return;
  }

  buildLanguageFilter(state.data);
  document.getElementById("status").hidden = true;
  for (const id of ["kpi-row", "heatmap-card", "added-card", "rating-card"]) {
    document.getElementById(id).hidden = false;
  }
  render();

  const generated = new Date(state.data.generatedAt);
  document.getElementById("generated-at").textContent =
    `Aggiornato al ${generated.toLocaleString("it-IT")}`;
}

function setStatus(message, isError) {
  const el = document.getElementById("status");
  el.textContent = message;
  el.hidden = false;
  el.classList.toggle("error", Boolean(isError));
}

function buildLanguageFilter(data) {
  const nav = document.getElementById("lang-filter");
  const codes = Object.keys(data.totals.cardsByLanguage).sort();
  for (const code of codes) {
    const info = LANGUAGES[code] ?? { name: code, flag: "" };
    const button = document.createElement("button");
    button.dataset.lang = code;
    button.textContent = `${info.flag} ${info.name}`.trim();
    button.addEventListener("click", () => selectLanguage(code));
    nav.appendChild(button);
  }
  document.querySelector('[data-lang="all"]').addEventListener("click", () => selectLanguage("all"));
}

function selectLanguage(lang) {
  state.lang = lang;
  for (const button of document.querySelectorAll(".lang-filter button")) {
    button.classList.toggle("active", button.dataset.lang === lang);
  }
  render();
}

function render() {
  const { data, lang } = state;
  const filterRows = (rows) => (lang === "all" ? rows : rows.filter((r) => r.language === lang));

  const cardsAdded = filterRows(data.cardsAddedByDay);
  const reviews = filterRows(data.reviewsByDay);

  renderKpis(data, lang, cardsAdded, reviews);
  renderHeatmap(reviews);
  renderAddedChart(cardsAdded);
  renderRatingChart(lang === "all" ? data.ratingDistribution : null, reviews);
}

function renderKpis(data, lang, cardsAdded, reviews) {
  const totalCards =
    lang === "all" ? data.totals.cards : data.totals.cardsByLanguage[lang] ?? 0;
  const totalReviews = sumBy(reviews, (r) => r.count);
  const todayKey = new Date().toISOString().slice(0, 10);
  const reviewsToday = lang === "all" ? data.totals.reviewsToday : sumBy(
    reviews.filter((r) => r.day === todayKey),
    (r) => r.count,
  );
  const streak =
    lang === "all" ? data.totals.streakDays : computeStreak(new Set(reviews.map((r) => r.day)));

  document.getElementById("kpi-cards").textContent = totalCards.toLocaleString("it-IT");
  document.getElementById("kpi-reviews").textContent = totalReviews.toLocaleString("it-IT");
  document.getElementById("kpi-reviews-today").textContent = reviewsToday.toLocaleString("it-IT");
  document.getElementById("kpi-streak").textContent = streak.toLocaleString("it-IT");
}

/** Mirrors CardRepository.currentStreakDays() (app) / currentStreakDays() (stats.js), UTC days. */
function computeStreak(daySet) {
  if (daySet.size === 0) return 0;
  const cursor = new Date();
  const dayKey = (d) => d.toISOString().slice(0, 10);
  if (!daySet.has(dayKey(cursor))) cursor.setUTCDate(cursor.getUTCDate() - 1);
  let streak = 0;
  while (daySet.has(dayKey(cursor))) {
    streak++;
    cursor.setUTCDate(cursor.getUTCDate() - 1);
  }
  return streak;
}

function renderHeatmap(reviews) {
  const byDay = new Map();
  for (const row of reviews) byDay.set(row.day, (byDay.get(row.day) ?? 0) + row.count);

  const today = new Date();
  today.setUTCHours(0, 0, 0, 0);
  const weeks = 26;
  const start = new Date(today);
  start.setUTCDate(start.getUTCDate() - weeks * 7);
  // Align to the start of that week (Sunday) so columns line up cleanly.
  start.setUTCDate(start.getUTCDate() - start.getUTCDay());

  const maxCount = Math.max(1, ...byDay.values());
  const levelFor = (count) => {
    if (count === 0) return 0;
    const ratio = count / maxCount;
    if (ratio > 0.75) return 4;
    if (ratio > 0.5) return 3;
    if (ratio > 0.25) return 2;
    return 1;
  };

  const container = document.getElementById("heatmap");
  container.innerHTML = "";
  const cursor = new Date(start);
  while (cursor <= today) {
    const key = cursor.toISOString().slice(0, 10);
    const count = byDay.get(key) ?? 0;
    const cell = document.createElement("div");
    cell.className = "heatmap-day";
    cell.dataset.level = String(levelFor(count));
    cell.title = `${key}: ${count} ripass${count === 1 ? "o" : "i"}`;
    container.appendChild(cell);
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
}

function renderAddedChart(cardsAdded) {
  const byDay = new Map();
  for (const row of cardsAdded) byDay.set(row.day, (byDay.get(row.day) ?? 0) + row.count);
  const days = [...byDay.keys()].sort();
  renderBarChart("added-chart", days.map((day) => ({ label: day, value: byDay.get(day), color: "var(--accent)" })));
}

function renderRatingChart(overallDistribution, reviews) {
  let bars;
  if (overallDistribution) {
    bars = overallDistribution.map((r) => ({ label: r.label, value: r.count, color: RATING_COLORS[r.rating] }));
  } else {
    // Per-language filter: reviewsByDay has no rating breakdown, so fall back to a simple total.
    const total = sumBy(reviews, (r) => r.count);
    bars = [{ label: "Ripassi", value: total, color: "var(--accent)" }];
  }
  renderBarChart("rating-chart", bars);
}

/** Minimal hand-rolled SVG bar chart - no charting library needed for a handful of series. */
function renderBarChart(containerId, bars) {
  const container = document.getElementById(containerId);
  container.innerHTML = "";
  if (bars.length === 0 || bars.every((b) => b.value === 0)) {
    const empty = document.createElement("p");
    empty.className = "chart-empty";
    empty.textContent = "Ancora nessun dato.";
    container.appendChild(empty);
    return;
  }

  const width = Math.max(320, bars.length * 18);
  const height = 160;
  const barWidth = Math.max(3, (width / bars.length) * 0.7);
  const maxValue = Math.max(...bars.map((b) => b.value));
  const showLabels = bars.length <= 12;

  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("viewBox", `0 0 ${width} ${height}`);
  svg.setAttribute("width", width);
  svg.setAttribute("height", height);

  bars.forEach((bar, index) => {
    const barHeight = maxValue === 0 ? 0 : (bar.value / maxValue) * (height - 28);
    const x = (index + 0.15) * (width / bars.length);
    const y = height - 20 - barHeight;

    const rect = document.createElementNS("http://www.w3.org/2000/svg", "rect");
    rect.setAttribute("x", x);
    rect.setAttribute("y", y);
    rect.setAttribute("width", barWidth);
    rect.setAttribute("height", barHeight);
    rect.setAttribute("rx", 2);
    rect.setAttribute("fill", bar.color);
    const title = document.createElementNS("http://www.w3.org/2000/svg", "title");
    title.textContent = `${bar.label}: ${bar.value}`;
    rect.appendChild(title);
    svg.appendChild(rect);

    if (showLabels) {
      const text = document.createElementNS("http://www.w3.org/2000/svg", "text");
      text.setAttribute("x", x + barWidth / 2);
      text.setAttribute("y", height - 6);
      text.setAttribute("text-anchor", "middle");
      text.setAttribute("font-size", "9");
      text.setAttribute("fill", "var(--text-muted)");
      text.textContent = bar.label.length > 10 ? bar.label.slice(5) : bar.label;
      svg.appendChild(text);
    }
  });

  container.appendChild(svg);
}

function sumBy(rows, fn) {
  return rows.reduce((total, row) => total + fn(row), 0);
}

main();
