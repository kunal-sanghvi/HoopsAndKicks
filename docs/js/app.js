// Read-only web viewer for a live Hoops & Kicks room. Mirrors the Android viewer (ViewerStore / TournamentScreens):
// hub with the live match, fixtures, standings, teams and top scorers. All logic lives in model.js; this file is
// Firestore wiring + rendering. Every value that reaches the DOM goes through the `html` tag below, which escapes
// it, so a hostile team or player name can never inject markup.
import { createBackend } from "./backend.js";
import * as M from "./model.js";

// ------------------------------------------------------------------ safe html templating

class Safe { constructor(s) { this.s = s; } }
const ESC = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ESC[c]);
const part = (v) => (v instanceof Safe ? v.s : Array.isArray(v) ? v.map(part).join("") : v == null || v === false ? "" : esc(v));
/** Tagged template: interpolations are HTML-escaped unless they are themselves the result of `html`. */
const html = (strings, ...vals) => new Safe(strings.reduce((out, s, i) => out + s + (i < vals.length ? part(vals[i]) : ""), ""));

// ------------------------------------------------------------------ state

const LAST_CODE_KEY = "hk.lastRoom";
const state = {
  phase: "join", // join | connecting | notfound | failed | live
  code: "", message: "",
  base: null, // tournament snapshot from rooms/{code}
  events: new Map(), // matchId -> MatchEvent[] (only for matches we listen to / fetched)
  t: null, // base + replayed live matches
  tab: "home", sel: { fixtures: -1, board: 0, stats: 0 }, expanded: new Set(), feedAll: new Set(),
};
let backend = null;
let session = 0;
let unsubRoom = null;
const listeners = new Map(); // matchId -> unsubscribe
const fetching = new Set();

const storage = {
  get() { try { return localStorage.getItem(LAST_CODE_KEY); } catch { return null; } },
  set(v) { try { v ? localStorage.setItem(LAST_CODE_KEY, v) : localStorage.removeItem(LAST_CODE_KEY); } catch { /* private mode */ } },
};

// ------------------------------------------------------------------ connection

function teardown() {
  session++;
  unsubRoom?.();
  unsubRoom = null;
  listeners.forEach((u) => u());
  listeners.clear();
  fetching.clear();
  Object.assign(state, { base: null, t: null, events: new Map(), expanded: new Set(), feedAll: new Set(), tab: "home", sel: { fixtures: -1, board: 0, stats: 0 } });
}

function setUrl(code) {
  try {
    const u = new URL(location.href);
    code ? u.searchParams.set("room", code) : u.searchParams.delete("room");
    history.replaceState(null, "", u);
  } catch { /* file:// or sandboxed */ }
}

async function join(rawCode) {
  const code = M.normalizeCode(rawCode);
  teardown();
  const mine = session;
  if (code.length !== M.CODE_LENGTH) {
    Object.assign(state, { phase: "join", code, message: `A room code has ${M.CODE_LENGTH} letters and numbers.` });
    return render();
  }
  Object.assign(state, { phase: "connecting", code, message: "" });
  storage.set(code);
  setUrl(code);
  render();
  try {
    backend ??= await createBackend();
  } catch {
    if (mine === session) fail("Couldn't load the live-update library. Check your connection and reload.");
    return;
  }
  if (mine !== session) return;
  unsubRoom = backend.watchRoom(code, (raw) => onRoom(mine, raw), (err) => {
    if (mine !== session) return;
    fail(err?.code === "permission-denied" ? "This room can't be read. Check the code." : "Could not reach the room.");
  });
}

function fail(message) {
  Object.assign(state, { phase: "failed", message });
  render();
}

function onRoom(mine, raw) {
  if (mine !== session) return;
  if (!raw) {
    // Wrong code, or the organiser stopped hosting: nothing to resume later.
    storage.set(null);
    listeners.forEach((u) => u());
    listeners.clear();
    Object.assign(state, { phase: "notfound", base: null, t: null });
    return render();
  }
  let parsed;
  try { parsed = M.parseTournament(raw); } catch { return fail("This room's data could not be read."); }
  state.base = parsed;
  state.phase = "live";
  syncListeners(mine);
  recompute();
  render();
}

/** Only matches in progress get a live listener (a finished match's result is already in the snapshot). */
function syncListeners(mine) {
  for (const m of M.realMatches(state.base)) {
    if (!M.isLiveStatus(m.status) || listeners.has(m.id)) continue;
    listeners.set(m.id, backend.watchEvents(state.code, m.id, (evs) => onEvents(mine, m.id, evs), () => { /* keep the snapshot view */ }));
  }
}

function onEvents(mine, matchId, evs) {
  if (mine !== session) return;
  state.events.set(matchId, evs.map(M.parseEvent).filter(Boolean));
  recompute();
  scheduleRender();
}

/** A finished match's event log (for its match feed) is fetched once, when the viewer taps the card. */
async function loadFinishedEvents(matchId) {
  if (listeners.has(matchId) || state.events.has(matchId) || fetching.has(matchId)) return;
  const mine = session;
  fetching.add(matchId);
  try {
    const evs = await backend.getEvents(state.code, matchId);
    if (mine === session) onEvents(mine, matchId, evs);
  } catch { /* the scorers from the room snapshot are still shown */ } finally {
    fetching.delete(matchId);
    if (mine === session) scheduleRender();
  }
}

function recompute() {
  if (state.base) state.t = M.mergeRemote(state.base, state.events);
}

let raf = 0;
function scheduleRender() {
  if (raf) return;
  raf = requestAnimationFrame(() => { raf = 0; render(); });
}

// ------------------------------------------------------------------ small view helpers

const fmtSec = (s) => `${String(Math.floor(s / 60)).padStart(2, "0")}:${String(s % 60).padStart(2, "0")}`;
const fmtTime = (ms) => (ms > 0 ? new Date(ms).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" }) : "");
const fmtDate = (ms) => (ms > 0 ? new Date(ms).toLocaleDateString([], { weekday: "short", day: "numeric", month: "short" }) : "");
const slotText = (m) => [m.court ? `Court ${m.court}` : "", fmtTime(m.scheduledAt ?? 0)].filter(Boolean).join(" · ");
const colorClass = (t, id) => { const tm = M.team(t, id); return tm ? `c${M.teamColorIndex(tm.color)}` : "cmute"; };
const formatSummary = (f) => (f.type === "HALVES" ? `2 × ${f.periodMin} min · ${f.breakMin} min break` : `${f.periodMin} min continuous`);

const icon = {
  home: "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z",
  list: "M4 10.5c-.83 0-1.5.67-1.5 1.5s.67 1.5 1.5 1.5 1.5-.67 1.5-1.5-.67-1.5-1.5-1.5zm0-6c-.83 0-1.5.67-1.5 1.5S3.17 7.5 4 7.5 5.5 6.83 5.5 6 4.83 4.5 4 4.5zm0 12c-.83 0-1.5.68-1.5 1.5s.68 1.5 1.5 1.5 1.5-.68 1.5-1.5-.67-1.5-1.5-1.5zM7 19h14v-2H7v2zm0-6h14v-2H7v2zm0-8v2h14V5H7z",
  chart: "M5 9.2h3V19H5zM10.6 5h2.8v14h-2.8zm5.6 8H19v6h-2.8z",
  groups: "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z",
  star: "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z",
  trophy: "M19 5h-2V3H7v2H5c-1.1 0-2 .9-2 2v1c0 2.55 1.92 4.63 4.39 4.94A5.01 5.01 0 0011 15.9V19H7v2h10v-2h-4v-3.1a5.01 5.01 0 003.61-2.96C19.08 12.63 21 10.55 21 8V7c0-1.1-.9-2-2-2zM5 8V7h2v3.82C5.84 10.4 5 9.3 5 8zm14 0c0 1.3-.84 2.4-2 2.82V7h2v1z",
};
const svg = (d, cls = "") => html`<svg class="ic ${cls}" viewBox="0 0 24 24" aria-hidden="true"><path d="${d}"/></svg>`;

const badge = (text, kind = "") => html`<span class="badge ${kind}">${text}</span>`;
const dot = (cls) => html`<i class="dot ${cls}"></i>`;
const teamPill = (t, id) => html`<span class="pill">${dot(colorClass(t, id))}<span class="pill-name">${M.teamName(t, id)}</span></span>`;

function chips(key, names, selected) {
  return html`<div class="chips" data-keep="${key}">${names.map((n, i) => html`<button class="chip ${i === selected ? "on" : ""}" data-act="chip" data-k="${key}" data-v="${i}">${n}</button>`)}</div>`;
}

// ------------------------------------------------------------------ shootout views (ShootoutViews.kt)

function shotDot(made) {
  return made === true ? html`<b class="shot made" aria-label="Made">✓</b>` : made === false ? html`<b class="shot miss" aria-label="Missed">✕</b>` : html`<b class="shot todo"></b>`;
}

function shootoutRows(t, m, so, names) {
  const row = (id, attempts) => {
    const line = names ? M.shooterNames(t, attempts) : null;
    return html`<div class="so-team">
      <div class="so-row">${dot(colorClass(t, id))}<span class="so-name">${M.teamName(t, id)}</span>
        <span class="so-dots">${Array.from({ length: so.slots }, (_, i) => shotDot(attempts[i]?.made ?? null))}</span>
        <span class="so-tally">${attempts.filter((a) => a.made).length}/${attempts.length}</span></div>
      ${line ? html`<div class="so-names">${line}</div>` : ""}</div>`;
  };
  return html`<div class="so-rows">${row(m.teamAId, so.attemptsA)}${row(m.teamBId, so.attemptsB)}</div>`;
}

function shootoutLivePanel(t, m, dark) {
  const sp = M.sportInfo(t.sport);
  const so = M.shootout(m, t.sport);
  const nextTeam = so.nextIsA ? m.teamAId : m.teamBId;
  let status;
  if (so.winnerId != null) {
    const aWon = so.winnerId === m.teamAId;
    status = `${M.teamName(t, so.winnerId)} won ${aWon ? so.madeA : so.madeB}–${aWon ? so.madeB : so.madeA} on ${sp.shootoutAttemptPlural}`;
  } else if (so.suddenDeath) status = `${M.teamName(t, nextTeam)} to shoot · sudden death`;
  else status = `${M.teamName(t, nextTeam)} to shoot · attempt ${so.nextAttemptNumber} of ${so.perTeam}`;
  const last = so.all[so.all.length - 1];
  const lastLine = last
    ? `Last: ${M.player(t, last.playerId)?.name ?? M.teamName(t, last.teamId)} (${last.made ? sp.shootoutMadeLabel.toLowerCase() : "missed"})`
    : "";
  return html`<div class="so-panel ${dark ? "dark" : ""}">
    <div class="so-title">${`${sp.shootoutName} · ${so.perTeam} each, alternating`}</div>
    ${shootoutRows(t, m, so, false)}
    <div class="so-status">${dot(colorClass(t, so.winnerId ?? nextTeam))}<span>${status}</span></div>
    ${lastLine ? html`<div class="so-last">${lastLine}</div>` : ""}
    ${so.suddenDeath && so.winnerId == null ? html`<div class="so-foot">Still level after ${so.perTeam} each: one ${sp.shootoutAttemptSingular} at a time until one side is ahead.</div>` : ""}
  </div>`;
}

// ------------------------------------------------------------------ match feed

const FEED_PREVIEW = 5;

function feedRow(t, it) {
  const marker = it.kind === "halftime" || it.kind === "secondhalf" || it.kind === "fulltime" || it.kind === "shootout";
  const lead = it.kind === "attempt" ? shotDot(it.made) : it.teamId && !marker ? dot(colorClass(t, it.teamId)) : "";
  return html`<li class="fi k-${it.kind}"><span class="fi-time">${it.time ?? ""}</span><span class="fi-lead">${lead}</span>
    <div class="fi-body"><div class="fi-title">${it.title}</div>${it.subtitle ? html`<div class="fi-note">${it.subtitle}</div>` : ""}</div></li>`;
}

const feedList = (t, items) => html`<ol class="feed">${items.map((it) => feedRow(t, it))}</ol>`;

/** Live card: the latest few items, newest first, with a toggle for the rest. */
function liveFeed(t, m) {
  const items = M.buildMatchFeed(t, m);
  if (!items.length) return "";
  const all = state.feedAll.has(m.id);
  return html`<div class="feed-box"><div class="feed-head">Match feed</div>
    ${feedList(t, M.newestFirst(items, all ? Infinity : FEED_PREVIEW))}
    ${items.length > FEED_PREVIEW ? html`<button class="feed-more" data-act="feed-all" data-mid="${m.id}">${all ? "Show less" : `Show all (${items.length})`}</button>` : ""}</div>`;
}

function resultNoteLine(t, m) {
  const note = M.resultNote(m, t);
  if (!note) return "";
  return html`<div class="note ${M.shootoutResult(m, t.sport) ? "strong" : ""}">${note}</div>`;
}

// ------------------------------------------------------------------ live score (ticks locally)

function phaseText(t, m) {
  if (m.status === "TIEBREAK") { const so = M.shootout(m, t.sport); return `${M.sportInfo(t.sport).shootoutName} · ${so.madeA}–${so.madeB}`; }
  const value = m.status === "BREAK" ? m.breakRemainingSec : m.remainingSec;
  const sec = M.computeDisplaySeconds(value, m.clockEpochMs, m.clockRunning);
  return m.status === "BREAK" ? `Break · ${fmtSec(sec)}` : fmtSec(sec) + (m.clockRunning ? "" : " · paused");
}

function liveCard(t, m) {
  const live = M.isLiveStatus(m.status);
  const slot = slotText(m);
  return html`<section class="live-card">
    <div class="eyebrow">${`${live ? "LIVE NOW" : "UP NEXT"} · ${M.matchTitle(m)}${slot ? ` · ${slot}` : ""}`.toUpperCase()}</div>
    <div class="vs-row"><div class="vs-team">${M.teamName(t, m.teamAId)}</div><span class="vs">vs</span><div class="vs-team right">${M.teamName(t, m.teamBId)}</div></div>
    <div class="fmt">${formatSummary(M.formatForMatch(t.format, m))}</div>
    ${live ? html`<div class="score-line"><span class="score">${m.scoreA} – ${m.scoreB}</span><span class="phase" data-phase="${m.id}">${phaseText(t, m)}</span></div>` : ""}
    ${m.status === "TIEBREAK" ? shootoutLivePanel(t, m, true) : ""}
    ${live ? liveFeed(t, m) : ""}
  </section>`;
}

// ------------------------------------------------------------------ home

function stageProgress(t) {
  const existing = M.stages(t);
  const names = existing.map((s) => s[1]);
  const steps = [...existing.map(([idx, name]) => ({ name, idx })), ...M.expectedStages(t).filter((n) => !names.includes(n)).map((name) => ({ name, idx: -1 }))];
  const states = steps.map((s) => {
    if (s.idx < 0) return 2;
    const ms = M.realMatches(t).filter((m) => m.stageIndex === s.idx);
    return ms.length && ms.every((m) => m.status === "FINISHED") ? 0 : 1;
  });
  const nowIdx = states.indexOf(1);
  const stage = M.currentStageName(t);
  const stageMatches = M.realMatches(t).filter((m) => m.stage === stage);
  const done = stageMatches.filter((m) => m.status === "FINISHED").length;
  return html`<section class="card">
    ${steps.length > 1 ? html`<ol class="steps">${steps.map((s, i) => {
      const st = states[i] === 0 ? 0 : i === nowIdx ? 1 : 2;
      return html`<li class="step s${st}"><span class="step-dot">${st === 0 ? "✓" : ""}</span><span class="step-name">${s.name}</span></li>`;
    })}</ol>` : ""}
    <div class="center mute small">${t.status === "COMPLETED" ? `Tournament complete · ${M.playedCount(t)} matches played` : `${stage} · ${done} of ${stageMatches.length} matches played`}</div>
  </section>`;
}

function homeTab(t) {
  const real = M.realMatches(t);
  const live = real.filter((m) => M.isLiveStatus(m.status));
  const next = real.filter((m) => m.status === "SCHEDULED").sort((a, b) => a.number - b.number)[0];
  const meta = [M.sportInfo(t.sport).displayName, t.venue, fmtDate(t.startAt)].filter(Boolean).join(" · ");
  return html`
    <header class="top"><div><h1>${t.name}</h1><div class="mute small">${meta}</div></div>${badge("Watching live", "accent")}</header>
    <div class="stack">
      ${stageProgress(t)}
      ${t.status === "COMPLETED"
        ? html`<section class="card champ">${svg(icon.trophy, "gold")}<div><div class="mute small">Champion</div><div class="display big">${M.teamName(t, t.championId)}</div></div></section>`
        : live.length ? live.map((m) => liveCard(t, m)) : next ? liveCard(t, next) : ""}
      <section class="card"><div class="mute small">Game format</div><div class="strong">${formatSummary(t.format)}</div></section>
      <div class="tiles">
        <button class="tile" data-act="tab" data-v="fixtures">${svg(icon.list)}<b>Fixtures</b><span>${real.length} matches so far</span></button>
        <button class="tile" data-act="tab" data-v="board">${svg(icon.chart)}<b>Standings</b><span>Across all stages</span></button>
        <button class="tile" data-act="tab" data-v="teams">${svg(icon.groups)}<b>Teams &amp; players</b><span>${t.teams.length} teams · ${t.players.length} players</span></button>
        <button class="tile" data-act="tab" data-v="stats">${svg(icon.star)}<b>Top scorers</b><span>Player stats</span></button>
      </div>
    </div>`;
}

// ------------------------------------------------------------------ fixtures

function matchCard(t, m) {
  const finished = m.status === "FINISHED";
  const items = m.status === "SCHEDULED" ? [] : M.buildMatchFeed(t, m);
  // A shootout result without a loaded log still has a timeline to fetch.
  const canExpand = items.length > 0 || M.shootoutResult(m, t.sport) != null;
  const open = state.expanded.has(m.id);
  const badgeEl = { FINISHED: badge("Final", "green"), SCHEDULED: badge("Upcoming", "line"), LIVE: badge("Live", "solid"), BREAK: badge("Break", "accent"), TIEBREAK: badge("Tie-break", "accent") }[m.status];
  const meta = [`Match ${m.number}`, slotText(m), m.label && m.label !== "Match" ? m.label : ""].filter(Boolean).join(" · ");
  let feed = "";
  if (canExpand) {
    feed = !open ? html`<div class="mute small tap">Tap to see the match feed</div>`
      : items.length ? feedList(t, items)
      : html`<div class="mute small">${fetching.has(m.id) ? "Loading…" : "Nothing recorded yet."}</div>`;
  }
  const extra = html`${m.status === "TIEBREAK" ? shootoutLivePanel(t, m, false) : ""}${feed}`;
  return html`<article class="card match ${canExpand ? "tappable" : ""}" ${canExpand ? html`data-act="expand" data-mid="${m.id}"` : ""}>
    <div class="match-row">
      <div class="match-teams">${teamPill(t, m.teamAId)}${teamPill(t, m.teamBId)}</div>
      ${m.status !== "SCHEDULED"
        ? html`<div class="match-score"><span class="${finished && m.winnerId === m.teamAId ? "w" : ""}">${m.scoreA}</span><span class="${finished && m.winnerId === m.teamBId ? "w" : ""}">${m.scoreB}</span></div>`
        : html`<span class="mute vs">vs</span>`}
      <div class="match-badge">${badgeEl}</div>
    </div>
    <div class="mute small">${meta}</div>
    ${resultNoteLine(t, m)}${extra}
  </article>`;
}

function sectionTitle(title, sub) {
  return html`<div class="section"><h2>${title}</h2>${sub ? html`<span class="mute small ellipsis">${sub}</span>` : ""}</div>`;
}

function fixturesTab(t) {
  const existing = M.stages(t);
  const names = existing.map((s) => s[1]);
  const chipNames = [...names, ...M.expectedStages(t).filter((n) => !names.includes(n))];
  const open = M.realMatches(t).filter((m) => m.status !== "FINISHED").sort((a, b) => M.frontier(a) - M.frontier(b))[0];
  const si = open?.stageIndex ?? existing[existing.length - 1]?.[0] ?? 0;
  const defaultIdx = Math.max(0, existing.findIndex((s) => s[0] === si));
  const cur = Math.min(Math.max(state.sel.fixtures < 0 ? defaultIdx : state.sel.fixtures, 0), Math.max(0, chipNames.length - 1));
  let body;
  if (cur >= existing.length) {
    body = html`<div class="card mute small">🔒 Locked. This round is drawn automatically once the earlier stage is finished.</div>`;
  } else {
    const ms = t.matches.filter((m) => m.stageIndex === existing[cur][0]);
    const real = ms.filter((m) => !m.bye);
    if (ms[0]?.stageType === "GROUP") {
      body = [...new Set(real.map((m) => m.group))].sort().map((g) => {
        const gm = real.filter((m) => m.group === g);
        const teamNames = [...new Set(gm.flatMap((m) => [m.teamAId, m.teamBId].filter(Boolean)))].map((id) => M.teamName(t, id)).join(" · ");
        return html`${sectionTitle(g, teamNames)}${gm.map((m) => matchCard(t, m))}`;
      });
    } else {
      const rounds = [...new Set(real.map((m) => m.round))].sort((a, b) => a - b);
      body = html`${rounds.map((r) => html`${rounds.length > 1 ? sectionTitle(`Round ${r}`, "") : ""}${real.filter((m) => m.round === r).map((m) => matchCard(t, m))}`)}
        ${ms.filter((m) => m.bye).map((m) => html`<div class="mute small">${M.teamName(t, m.teamAId)} has a bye (${m.label || "advances"})</div>`)}`;
    }
  }
  return html`<header class="top"><h1>Fixtures</h1></header>${chips("fixtures", chipNames, cur)}<div class="stack">${body}</div>`;
}

// ------------------------------------------------------------------ standings

const scoringLine = (t) => `Win ${t.winPoints} · tie ${t.tiePoints} · loss ${t.lossPoints} points.`;

function resultCard(t, m) {
  const fin = m.status === "FINISHED";
  const pens = M.shootoutResult(m, t.sport);
  const side = (id, score) => {
    const won = fin && m.winnerId === id;
    return html`<div class="rc-row ${won ? "won" : ""}">${dot(colorClass(t, id))}<span class="rc-name">${M.teamName(t, id)}</span>${pens ? html`<span class="mute small">(${M.madeBy(pens, id)})</span>` : ""}<span class="rc-score">${m.status === "SCHEDULED" ? "–" : score}</span></div>`;
  };
  const inProgress = m.status !== "SCHEDULED" && !fin
    ? (m.status === "TIEBREAK" ? (() => { const so = M.shootout(m, t.sport); return `Tied, ${M.sportInfo(t.sport).shootoutName.toLowerCase()} in progress · ${so.madeA}–${so.madeB}`; })() : "In progress")
    : "";
  return html`<div><div class="eyebrow dim">${(m.label || m.stage).toUpperCase()}</div>
    <div class="card tight">${side(m.teamAId, m.scoreA)}<hr>${side(m.teamBId, m.scoreB)}</div>
    ${resultNoteLine(t, m)}${inProgress ? html`<div class="note accent">${inProgress}</div>` : ""}</div>`;
}

function pointsTable(t, rows, qualify) {
  return html`<div class="table">
    <div class="tr th"><span class="c-rank">#</span><span class="c-team">Team</span><span class="c-n" title="Played">P</span><span class="c-n" title="Won">W</span><span class="c-n" title="Lost">L</span><span class="c-n" title="Tied">T</span><span class="c-n w40" title="Total points">Pts</span></div>
    ${rows.map((r, i) => html`<div class="tr ${i < qualify ? "hi" : ""}"><span class="c-rank b">${i + 1}</span><span class="c-team">${teamPill(t, r.teamId)}</span><span class="c-n">${r.played}</span><span class="c-n">${r.wins}</span><span class="c-n">${r.losses}</span><span class="c-n">${r.ties}</span><span class="c-n w40 b">${r.points}</span></div>`)}
  </div>`;
}

function overallContent(t) {
  if (!t.matches.length) return html`<div class="mute small">Standings appear once matches are played.</div>`;
  const showPoints = M.usesPoints(t);
  const rows = M.overall(t);
  const knockout = t.matches.filter((m) => !m.bye && (m.stageType === "KNOCKOUT" || m.isFinal)).sort((a, b) => a.number - b.number);
  return html`
    <div class="mute xs">${showPoints
      ? `Played, won, lost and tied count group and round-robin matches only. ${scoringLine(t)} Knockouts, semi-finals and the final award no points. Ranked by furthest stage reached, then total, then point difference.`
      : "Knockout format: no points. Teams are ranked by how far they got."}</div>
    <div class="card table">
      <div class="tr th"><span class="c-rank">#</span><span class="c-team">Team</span>${showPoints ? html`<span class="c-n" title="Played">P</span><span class="c-n" title="Won">W</span><span class="c-n" title="Lost">L</span><span class="c-n" title="Tied">T</span><span class="c-n w40" title="Total points">Pts</span>` : ""}</div>
      ${rows.map((r, i) => html`<div class="tr tall ${i === 0 && t.status === "COMPLETED" ? "hi" : ""}"><span class="c-rank b">${i + 1}</span>
        <span class="c-team">${teamPill(t, r.teamId)}<div class="mute xs indent">${r.statusText}</div></span>
        ${showPoints ? html`<span class="c-n">${r.played}</span><span class="c-n">${r.won}</span><span class="c-n">${r.lost}</span><span class="c-n">${r.tied}</span><span class="c-n w40 b">${r.total}</span>` : ""}</div>`)}
    </div>
    ${knockout.length ? html`<div class="strong mute small">Knockout results</div>${knockout.map((m) => resultCard(t, m))}` : ""}`;
}

function stageContent(t, stageIndex) {
  const ms = t.matches.filter((m) => m.stageIndex === stageIndex);
  const real = ms.filter((m) => !m.bye);
  const type = ms[0]?.stageType;
  if (!type) return html`<div class="mute small">Nothing here yet.</div>`;
  if (type === "KNOCKOUT" || real.some((m) => m.isFinal)) {
    const rounds = [...new Set(real.map((m) => m.round))].sort((a, b) => a - b);
    return html`${rounds.map((r) => html`${rounds.length > 1 ? html`<div class="strong mute small">Round ${r}</div>` : ""}${real.filter((m) => m.round === r).map((m) => resultCard(t, m))}`)}
      ${ms.filter((m) => m.bye).map((m) => html`<div class="mute small">${M.teamName(t, m.teamAId)} advances on a bye.</div>`)}
      ${t.status === "COMPLETED" && real.some((m) => m.isFinal)
        ? html`<section class="card champ">${svg(icon.trophy, "gold")}<div><div class="mute small">Champion</div><div class="strong">${M.teamName(t, t.championId)}</div></div></section>` : ""}`;
  }
  if (type === "GROUP") {
    const adv = M.clampedAdvance(t);
    return html`<div class="mute small">${scoringLine(t)}</div>
      ${[...new Set(real.map((m) => m.group))].sort().map((g) => {
        const gm = real.filter((m) => m.group === g);
        const ids = [...new Set(gm.flatMap((m) => [m.teamAId, m.teamBId].filter(Boolean)))];
        return html`<section class="card"><h2 class="pad">${g}</h2>${pointsTable(t, M.computeStandings(ids, gm, M.pointsRule(t)), t.algorithm === "GROUP_KO" ? adv : 0)}</section>`;
      })}
      ${t.algorithm === "GROUP_KO" ? html`<div class="mute xs">Top ${adv} from each group advance · ties split by head-to-head, then point difference.</div>` : ""}`;
  }
  const qualify = t.algorithm === "ROUND_ROBIN" ? (M.rrSemisOn(t) ? 4 : M.rrFinalOn(t) ? 2 : 0) : 0;
  const goesTo = qualify === 4 ? "Top 4 advance to the semi-finals · " : qualify === 2 ? "Top 2 play the final · " : "";
  const tail = `${goesTo}ties split by head-to-head, then point difference.`;
  return html`<div class="mute small">${scoringLine(t)}</div>
    <section class="card">${pointsTable(t, M.computeStandings(t.teams.map((x) => x.id), ms, M.pointsRule(t)), qualify)}</section>
    <div class="mute xs">${tail[0].toUpperCase() + tail.slice(1)}</div>`;
}

function boardTab(t) {
  const st = M.stages(t);
  const cur = Math.min(Math.max(state.sel.board, 0), st.length);
  return html`<header class="top"><h1>Standings</h1></header>${chips("board", ["Overall", ...st.map((s) => s[1])], cur)}
    <div class="stack">${cur === 0 ? overallContent(t) : stageContent(t, st[cur - 1][0])}</div>`;
}

// ------------------------------------------------------------------ teams + stats

function teamsTab(t) {
  return html`<header class="top"><h1>Teams</h1></header><div class="stack">
    ${t.teams.map((tm) => html`<section class="card">
      <div class="team-head"><span class="team-badge ${`c${M.teamColorIndex(tm.color)}`}">${tm.name.slice(0, 1).toUpperCase()}</span>
        <div><div class="strong big-s">${tm.name}</div><div class="mute small">${tm.playerIds.length} players</div></div></div>
      ${tm.playerIds.map((pid) => { const p = M.player(t, pid); return html`<div class="player"><span>${p?.name ?? "?"}</span><span class="mute small">${p?.position ?? ""}</span></div>`; })}
    </section>`)}</div>`;
}

function statsTab(t) {
  const st = M.stages(t);
  const cur = Math.min(Math.max(state.sel.stats, 0), st.length);
  const stats = M.playerStats(t, cur === 0 ? null : st[cur - 1][0]);
  const football = t.sport === "FOOTBALL";
  const empty = !stats.length || stats.every((s) => s.points === 0);
  const lead = stats[0];
  return html`<header class="top"><h1>Top scorers</h1></header>${chips("stats", ["Overall", ...st.map((s) => s[1])], cur)}
    <div class="stack">${empty ? html`<div class="mute small">Player stats appear once ${M.sportInfo(t.sport).scoreEventPlural} are credited to players.</div>` : html`
      <section class="card champ">${svg(icon.star, "gold")}<div class="grow"><div class="mute small">Scoring leader</div><div class="strong">${`${M.player(t, lead.playerId)?.name ?? "?"} · ${M.teamName(t, lead.teamId)}`}</div></div><div class="display big">${lead.points}</div></section>
      <section class="card table">
        <div class="tr th"><span class="c-rank">#</span><span class="c-team">Player</span><span class="c-n w28">G</span>${football ? "" : html`<span class="c-n w32">3PT</span>`}<span class="c-n w44">PPG</span><span class="c-n w40">${football ? "Goals" : "Pts"}</span></div>
        ${stats.slice(0, 30).map((s, i) => html`<div class="tr tall ${i === 0 ? "hi" : ""}"><span class="c-rank b">${i + 1}</span>
          <span class="c-team"><div class="strong">${M.player(t, s.playerId)?.name ?? "?"}</div><div class="mute xs">${M.teamName(t, s.teamId)}</div></span>
          <span class="c-n w28">${s.games}</span>${football ? "" : html`<span class="c-n w32">${s.threes}</span>`}<span class="c-n w44">${s.ppg.toFixed(1)}</span><span class="c-n w40 b">${s.points}</span></div>`)}
      </section>`}</div>`;
}

// ------------------------------------------------------------------ shell + screens

const TABS = [["home", "Home", icon.home], ["fixtures", "Fixtures", icon.list], ["board", "Standings", icon.chart], ["teams", "Teams", icon.groups], ["stats", "Scorers", icon.star]];

function liveScreen() {
  const t = state.t;
  const body = { home: homeTab, fixtures: fixturesTab, board: boardTab, teams: teamsTab, stats: statsTab }[state.tab](t);
  return html`<div class="bar"><span class="room">Room <b>${state.code}</b></span><button class="link" data-act="leave">Leave</button></div>
    <main class="page">${body}</main>
    <nav class="nav" aria-label="Sections">${TABS.map(([k, label, d]) => html`<button class="nav-item ${state.tab === k ? "on" : ""}" data-act="tab" data-v="${k}"><span class="nav-pill">${svg(d)}</span>${label}</button>`)}</nav>`;
}

function joinScreen() {
  const last = storage.get();
  return html`<main class="page center-screen">
    <div class="hero">🏀⚽</div>
    <h1 class="display">Hoops &amp; Kicks</h1>
    <p class="mute">Watch a live tournament. Enter the room code from the organiser.</p>
    <form class="join" data-act="join-form" autocomplete="off">
      <input name="code" value="${state.code}" maxlength="6" inputmode="text" autocapitalize="characters" autocorrect="off" spellcheck="false" placeholder="ROOM CODE" aria-label="Room code">
      <button class="btn" type="submit">Join</button>
    </form>
    ${state.message ? html`<p class="error" role="alert">${state.message}</p>` : ""}
    ${last && last !== state.code ? html`<button class="btn ghost" data-act="resume" data-v="${last}">Resume watching room ${last}</button>` : ""}
    <p class="mute xs">Read-only: you can watch scores, fixtures and standings, not change anything.</p>
  </main>`;
}

function waitingScreen() {
  const msg = {
    connecting: `Connecting to ${state.code}…`,
    notfound: `No live tournament with code ${state.code}. Check the code with the organiser, or the room may have closed.`,
    failed: `Couldn't join: ${state.message}`,
  }[state.phase];
  return html`<div class="bar"><span class="room">Room <b>${state.code}</b></span><button class="link" data-act="leave">Back</button></div>
    <main class="page center-screen"><p class="${state.phase === "connecting" ? "mute" : "error"}" role="status">${msg}</p>
      ${state.phase !== "connecting" ? html`<button class="btn" data-act="retry">Try again</button>` : html`<div class="spinner" aria-hidden="true"></div>`}</main>`;
}

const app = document.getElementById("app");

function render() {
  const y = window.scrollY;
  const keep = new Map([...app.querySelectorAll("[data-keep]")].map((el) => [el.dataset.keep, el.scrollLeft]));
  const focused = document.activeElement?.tagName === "INPUT" ? document.activeElement.value : null;
  const screen = state.phase === "live" && state.t ? liveScreen() : state.phase === "join" ? joinScreen() : waitingScreen();
  app.innerHTML = screen.s;
  app.querySelectorAll("[data-keep]").forEach((el) => { el.scrollLeft = keep.get(el.dataset.keep) ?? 0; });
  if (focused != null) { const i = app.querySelector("input"); if (i) { i.value = focused; i.focus(); } }
  window.scrollTo(0, y);
  document.title = state.phase === "live" && state.t ? `${state.t.name} · Hoops & Kicks` : "Hoops & Kicks — live viewer";
}

// Clocks tick locally from (value, epoch, running); nothing is re-rendered for a tick.
setInterval(() => {
  if (state.phase !== "live" || !state.t) return;
  app.querySelectorAll("[data-phase]").forEach((el) => {
    const m = state.t.matches.find((x) => x.id === el.dataset.phase);
    if (m) el.textContent = phaseText(state.t, m);
  });
}, 500);

app.addEventListener("click", (ev) => {
  const el = ev.target.closest("[data-act]");
  if (!el) return;
  const { act, v, k, mid } = el.dataset;
  if (act === "tab") { state.tab = v; render(); window.scrollTo(0, 0); }
  else if (act === "chip") { state.sel[k] = Number(v); render(); }
  else if (act === "expand") {
    state.expanded.has(mid) ? state.expanded.delete(mid) : state.expanded.add(mid);
    if (state.expanded.has(mid)) loadFinishedEvents(mid);
    render();
  } else if (act === "feed-all") {
    state.feedAll.has(mid) ? state.feedAll.delete(mid) : state.feedAll.add(mid);
    render();
  } else if (act === "leave") {
    // Keep the saved code so the join screen can offer "Resume watching" (same as the Android app).
    teardown();
    Object.assign(state, { phase: "join", code: "", message: "" });
    setUrl(null);
    render();
  } else if (act === "resume") join(v);
  else if (act === "retry") join(state.code);
});

app.addEventListener("submit", (ev) => {
  if (ev.target.dataset.act !== "join-form") return;
  ev.preventDefault();
  join(new FormData(ev.target).get("code"));
});

app.addEventListener("input", (ev) => {
  if (ev.target.name === "code") ev.target.value = M.normalizeCode(ev.target.value);
});

const fromUrl = new URLSearchParams(location.search).get("room");
if (fromUrl) join(fromUrl); else render();
