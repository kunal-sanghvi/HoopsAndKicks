// Pure, DOM-free port of the Android app's read-only viewer logic (no Firebase here, so it is unit-testable in Node).
//
// Kotlin sources this mirrors:
//   data/Model.kt         -> parseTournament, sport info, match helpers
//   data/MatchLog.kt      -> computeDisplaySeconds, replay, applyRemote
//   data/Shootout.kt      -> shootout
//   data/ShootoutResult.kt-> shootoutResult, resultNote
//   data/Scheduler.kt     -> Standings.compute, Leaderboards.overall/players, expectedStages
//   (match feed)          -> buildMatchFeed: the Android viewer's feed uses the same wording
// Keep them in step when the Android side changes.

export const ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
export const CODE_LENGTH = 6;
export const DRAW_NOTE = "Draw";

/** Upper-cases and drops anything outside the room-code alphabet (same as RemoteSync.normalizeCode). */
export function normalizeCode(raw) {
  return [...String(raw ?? "").toUpperCase()].filter((c) => ALPHABET.includes(c)).slice(0, CODE_LENGTH).join("");
}

export const SPORTS = {
  BASKETBALL: {
    displayName: "Basketball", emoji: "🏀", scoreEventPlural: "baskets",
    shootoutAttempts: 3, shootoutName: "Free-throw shootout", shootoutAttemptPlural: "free throws",
    shootoutAttemptSingular: "shot", shootoutMadeLabel: "Made",
  },
  FOOTBALL: {
    displayName: "Football", emoji: "⚽", scoreEventPlural: "goals",
    shootoutAttempts: 5, shootoutName: "Free-kick shootout", shootoutAttemptPlural: "free kicks",
    shootoutAttemptSingular: "free kick", shootoutMadeLabel: "Scored",
  },
};

export function sportInfo(sport) {
  return SPORTS[sport] ?? SPORTS.BASKETBALL;
}

/** Same palette as ui/Theme.kt; the page maps these indexes to CSS classes. */
export const TEAM_PALETTE_SIZE = 8;
export function teamColorIndex(color) {
  const n = Number.isFinite(color) ? color : 0;
  return ((n % TEAM_PALETTE_SIZE) + TEAM_PALETTE_SIZE) % TEAM_PALETTE_SIZE;
}

// ------------------------------------------------------------------ parsing

const EVENT_TYPES = new Set([
  "MATCH_START", "PERIOD_START", "PAUSE", "RESUME", "BREAK_START", "BREAK_END", "SCORE", "SUB",
  "TIEBREAK_START", "VOID", "MATCH_END", "SHOOTOUT_ATTEMPT", "REOPEN",
]);

const arr = (v) => (Array.isArray(v) ? v : []);

/** Firestore drops nothing, but optional JSON keys are simply absent: fill the same defaults as Json.fromJson. */
export function parseEvent(o) {
  if (!o || typeof o.id !== "string" || !Number.isFinite(o.seq) || !EVENT_TYPES.has(o.type)) return null;
  return {
    id: o.id, seq: o.seq, type: o.type, epochMs: o.epochMs ?? 0,
    teamId: o.teamId ?? null, playerId: o.playerId ?? null, points: o.points ?? null,
    outPlayerId: o.outPlayerId ?? null, inPlayerId: o.inPlayerId ?? null, voidsSeq: o.voidsSeq ?? null,
    note: o.note ?? null, clockSec: o.clockSec ?? null,
  };
}

function parseMatch(o) {
  return {
    id: o.id, number: o.number ?? 0, stageIndex: o.stageIndex ?? 0, stage: o.stage ?? "",
    stageType: o.stageType ?? "LEAGUE", group: o.group ?? "", round: o.round ?? 1, label: o.label ?? "",
    teamAId: o.teamAId ?? null, teamBId: o.teamBId ?? null, scoreA: o.scoreA ?? 0, scoreB: o.scoreB ?? 0,
    status: o.status ?? "SCHEDULED", isFinal: !!o.isFinal, bye: !!o.bye, period: o.period ?? 1,
    remainingSec: o.remainingSec ?? 0, breakRemainingSec: o.breakRemainingSec ?? 0,
    lineupA: arr(o.lineupA), lineupB: arr(o.lineupB),
    events: arr(o.events).map((e) => ({
      id: e.id, teamId: e.teamId ?? "", playerId: e.playerId ?? null, points: e.points ?? 0,
      period: e.period ?? "", clock: e.clock ?? "", createdAt: e.createdAt ?? 0,
    })),
    winnerId: o.winnerId ?? null, tieNote: o.tieNote ?? "",
    log: arr(o.log).map(parseEvent).filter(Boolean),
    clockRunning: !!o.clockRunning, clockEpochMs: o.clockEpochMs ?? 0,
    scheduledAt: o.scheduledAt ?? null, court: o.court ?? null,
  };
}

export function parseTournament(o) {
  const f = o.format ?? {};
  return {
    id: o.id, name: o.name ?? "Tournament", startAt: o.startAt ?? 0, endAt: o.endAt ?? 0,
    courts: Math.max(1, o.courts ?? 1), venue: o.venue ?? "",
    players: arr(o.players).map((p) => ({ id: p.id, name: p.name ?? "?", position: p.position ?? "", skill: p.skill ?? 3 })),
    teams: arr(o.teams).map((t) => ({ id: t.id, name: t.name ?? "Team", color: t.color ?? 0, playerIds: arr(t.playerIds) })),
    sport: SPORTS[o.sport] ? o.sport : "BASKETBALL",
    algorithm: o.algorithm ?? "GROUP_KO", groups: o.groups ?? 2, advance: o.advance ?? 2,
    rrSemis: !!o.rrSemis,
    // Tournaments saved before the option existed always played a final.
    rrFinal: o.rrFinal ?? true,
    winPoints: o.winPoints ?? 2, tiePoints: o.tiePoints ?? 1, lossPoints: o.lossPoints ?? 0,
    format: {
      type: f.type ?? "HALVES", periodMin: f.periodMin ?? 12, breakMin: Math.max(1, f.breakMin ?? 5),
      finalsDifferent: !!f.finalsDifferent, finalsPeriodMin: f.finalsPeriodMin ?? 15,
      semisDifferent: !!f.semisDifferent, semisPeriodMin: f.semisPeriodMin ?? 15,
    },
    matches: arr(o.matches).map(parseMatch),
    status: o.status ?? "DRAFT", championId: o.championId ?? null,
  };
}

// ------------------------------------------------------------------ helpers (Model.kt / Helpers.kt)

export const team = (t, id) => (id == null ? null : t.teams.find((x) => x.id === id) ?? null);
export const player = (t, id) => (id == null ? null : t.players.find((x) => x.id === id) ?? null);
export const teamName = (t, id) => team(t, id)?.name ?? "TBD";
export const realMatches = (t) => t.matches.filter((m) => !m.bye);
export const playedCount = (t) => realMatches(t).filter((m) => m.status === "FINISHED").length;
export const isLiveStatus = (s) => s === "LIVE" || s === "BREAK" || s === "TIEBREAK";

export function roundName(n) {
  if (n === 2) return "Final";
  if (n === 4) return "Semi-finals";
  if (n === 8) return "Quarter-finals";
  return `Round of ${n}`;
}

export const usesPoints = (t) => t.algorithm === "GROUP_KO" || t.algorithm === "ROUND_ROBIN" || t.algorithm === "SWISS";
export const awardsPoints = (m) => !m.bye && !m.isFinal && m.stageType !== "KNOCKOUT";
export const isDraw = (m) => m.status === "FINISHED" && !m.bye && m.winnerId == null;
export const isSemi = (m) => !m.isFinal && !m.bye && m.stage === roundName(4);
export const frontier = (m) => m.stageIndex * 1000 + m.round;

export function formatForMatch(format, m) {
  if (m.isFinal && format.finalsDifferent) return { ...format, periodMin: format.finalsPeriodMin };
  if (isSemi(m) && format.semisDifferent) return { ...format, periodMin: format.semisPeriodMin };
  return format;
}

/** Ordered distinct stages that exist, as [stageIndex, name]. */
export function stages(t) {
  const seen = new Set();
  const out = [];
  for (const m of t.matches) {
    const key = `${m.stageIndex}|${m.stage}`;
    if (!seen.has(key)) { seen.add(key); out.push([m.stageIndex, m.stage]); }
  }
  return out.sort((a, b) => a[0] - b[0]);
}

export function currentStageName(t) {
  const open = realMatches(t).filter((m) => m.status !== "FINISHED").sort((a, b) => frontier(a) - frontier(b))[0];
  if (open) return open.stage;
  const last = t.matches.reduce((best, m) => (best == null || frontier(m) > frontier(best) ? m : best), null);
  return last?.stage ?? "";
}

/** "Group A · Match 3" / "Semi-finals · Match 9". */
export function matchTitle(m) {
  const head = m.group ? m.group : m.stage;
  return m.number > 0 ? `${head} · Match ${m.number}` : head;
}

// ------------------------------------------------------------------ expected stages (Scheduler.kt)

const nextPow2 = (n) => { let p = 1; while (p < n) p *= 2; return p; };
const clamp = (v, lo, hi) => Math.min(Math.max(v, lo), hi);
const maxGroups = (t) => Math.max(1, Math.floor(t.teams.length / 2));
const clampedGroups = (t) => clamp(t.groups, 1, maxGroups(t));

function clampedAdvance(t) {
  const g = clampedGroups(t);
  const minSize = Math.max(1, Math.floor(t.teams.length / g));
  let adv = clamp(t.advance, 1, minSize);
  if (g * adv < 2) adv = Math.max(Math.min(2, minSize), 1);
  return adv;
}

const rrSemisOn = (t) => t.rrSemis && t.teams.length > 4;
const rrFinalOn = (t) => t.teams.length > 2 && (t.rrFinal || rrSemisOn(t));

function koNames(qualifiers) {
  const out = [];
  let size = nextPow2(Math.max(2, qualifiers));
  while (size >= 2) { out.push(roundName(size)); size = Math.floor(size / 2); }
  return out;
}

export function expectedStages(t) {
  switch (t.algorithm) {
    case "GROUP_KO": return ["Group Stage", ...koNames(clampedGroups(t) * clampedAdvance(t))];
    case "SINGLE_ELIM": return koNames(t.teams.length);
    case "ROUND_ROBIN":
      return ["Round Robin", ...(rrSemisOn(t) ? [roundName(4)] : []), ...(rrFinalOn(t) ? [roundName(2)] : [])];
    case "DOUBLE_ELIM": return ["Double Elimination"];
    case "SWISS": return ["Swiss"];
    default: return [];
  }
}

export { clampedAdvance, rrSemisOn, rrFinalOn };

// ------------------------------------------------------------------ clock + replay (MatchLog.kt)

/** Clock value to show right now; elapsed time is floored at 0 so a slow viewer clock never jumps upward. */
export function computeDisplaySeconds(valueAtChange, changedAtEpochMs, running, nowEpochMs = Date.now()) {
  if (!running) return valueAtChange;
  const elapsedSec = Math.floor(Math.max(0, nowEpochMs - changedAtEpochMs) / 1000);
  return Math.max(0, valueAtChange - elapsedSec);
}

const substringBefore = (s, ch) => { const i = s.indexOf(ch); return i < 0 ? s : s.slice(0, i); };
const substringAfter = (s, ch) => { const i = s.indexOf(ch); return i < 0 ? "" : s.slice(i + 1); };

function clockOf(m, e, running) {
  const sec = e.clockSec;
  let v = m;
  if (sec != null) v = m.status === "BREAK" ? { ...m, breakRemainingSec: sec } : { ...m, remainingSec: sec };
  return { ...v, clockRunning: running, clockEpochMs: e.epochMs };
}

function applyEvent(m, e, all) {
  switch (e.type) {
    case "MATCH_START": return clockOf({ ...m, status: "LIVE", period: 1 }, e, false);
    case "PERIOD_START":
      return clockOf({ ...m, status: "LIVE", period: m.period + 1, breakRemainingSec: 0 }, e, (e.clockSec ?? 0) > 0);
    case "PAUSE": return clockOf(m, e, false);
    case "RESUME": return clockOf(m, e, true);
    case "BREAK_START": return clockOf({ ...m, status: "BREAK" }, e, true);
    case "BREAK_END": return { ...m, status: "LIVE", breakRemainingSec: 0 };
    case "SCORE": {
      const isA = e.teamId === m.teamAId;
      const pts = e.points ?? 0;
      const note = e.note ?? "";
      const se = {
        id: e.id, teamId: e.teamId ?? "", playerId: e.playerId, points: pts,
        period: substringBefore(note, " "), clock: substringAfter(note, " "), createdAt: e.epochMs,
      };
      const scored = {
        ...m, scoreA: isA ? m.scoreA + pts : m.scoreA, scoreB: isA ? m.scoreB : m.scoreB + pts, events: [...m.events, se],
      };
      return e.clockSec != null ? clockOf(scored, e, m.clockRunning) : scored;
    }
    case "VOID": {
      if (all.some((x) => x.type === "SHOOTOUT_ATTEMPT" && x.seq === e.voidsSeq)) return m; // shootout is derived from the log
      const target = all.find((x) => x.type === "SCORE" && x.seq === e.voidsSeq);
      let r = { ...m, status: m.status === "TIEBREAK" ? "LIVE" : m.status };
      if (target) {
        const isA = target.teamId === r.teamAId;
        const pts = target.points ?? 0;
        r = {
          ...r,
          scoreA: isA ? Math.max(0, r.scoreA - pts) : r.scoreA,
          scoreB: isA ? r.scoreB : Math.max(0, r.scoreB - pts),
          events: r.events.filter((x) => x.id !== target.id),
        };
      }
      return e.clockSec != null ? clockOf(r, e, false) : r;
    }
    case "SUB": {
      const isA = e.teamId === m.teamAId;
      const out = e.outPlayerId;
      const inn = e.inPlayerId;
      if (out == null || inn == null) return m;
      const cur = isA ? m.lineupA : m.lineupB;
      const next = cur.includes(out) ? cur.map((x) => (x === out ? inn : x)) : cur;
      const fixed = next.includes(inn) ? next : [...next, inn];
      return isA ? { ...m, lineupA: fixed } : { ...m, lineupB: fixed };
    }
    case "TIEBREAK_START": return clockOf({ ...m, status: "TIEBREAK", remainingSec: 0 }, e, false);
    case "SHOOTOUT_ATTEMPT": return m;
    case "MATCH_END":
      return { ...m, status: "FINISHED", winnerId: e.teamId ?? null, tieNote: e.note ?? "", clockRunning: false, clockEpochMs: e.epochMs };
    case "REOPEN":
      return clockOf({ ...m, status: "LIVE", winnerId: null, tieNote: "", remainingSec: 0 }, e, false);
    default: return m;
  }
}

/** Rebuilds a match's live state from its log, starting from [base]'s fixed data (teams, stage, lineups). */
export function replay(base, events, format) {
  const f = formatForMatch(format, base);
  const ordered = [...events].sort((a, b) => a.seq - b.seq);
  let m = {
    ...base, status: "SCHEDULED", period: 1, scoreA: 0, scoreB: 0, remainingSec: f.periodMin * 60, breakRemainingSec: 0,
    events: [], winnerId: null, tieNote: "", clockRunning: false, clockEpochMs: 0, log: ordered,
  };
  for (const e of ordered) m = applyEvent(m, e, ordered);
  return m;
}

/**
 * Merges remotely received events (Map of matchId -> events) into a tournament snapshot. Matches whose merged
 * log contains a MATCH_START are rebuilt from the log; others are taken from the snapshot unchanged.
 */
export function applyRemote(base, remote) {
  return {
    ...base,
    matches: base.matches.map((m) => {
      const seen = new Set();
      const merged = [...m.log, ...(remote.get(m.id) ?? [])]
        .filter((e) => (seen.has(e.id) ? false : (seen.add(e.id), true)))
        .sort((a, b) => a.seq - b.seq);
      return merged.some((e) => e.type === "MATCH_START") ? replay(m, merged, base.format) : m;
    }),
  };
}

/**
 * [applyRemote] plus one web-only guard. The room snapshot is pushed before a match's last events arrive, and the
 * viewer only listens to events for matches it saw in progress, so a partially loaded log can be missing MATCH_END.
 * A match the snapshot already calls FINISHED must never flip back to live because of that: keep the snapshot's
 * result and just attach the log (so the shootout detail still works).
 */
export function mergeRemote(base, remote) {
  const merged = applyRemote(base, remote);
  return {
    ...merged,
    matches: merged.matches.map((m, i) => {
      const b = base.matches[i];
      return b.status === "FINISHED" && m.status !== "FINISHED" ? { ...b, log: m.log } : m;
    }),
  };
}

// ------------------------------------------------------------------ shootout (Shootout.kt, ShootoutResult.kt)

function shootoutWinner(a, b, perTeam) {
  const madeA = a.filter((x) => x.made).length;
  const madeB = b.filter((x) => x.made).length;
  const nA = a.length;
  const nB = b.length;
  const winA = a[0]?.teamId ?? null;
  const winB = b[0]?.teamId ?? null;
  if (nA < perTeam || nB < perTeam) {
    // Regular rounds: decided early once one side can no longer be caught.
    if (madeA > madeB + (perTeam - nB)) return winA;
    if (madeB > madeA + (perTeam - nA)) return winB;
    return null;
  }
  if (nA === nB && madeA !== madeB) return madeA > madeB ? winA : winB;
  return null;
}

/** The shootout for the current tie-break: attempts after the latest TIEBREAK_START, minus voided ones. */
export function shootout(m, sport) {
  const perTeam = sportInfo(sport).shootoutAttempts;
  let start = null;
  for (const e of m.log) if (e.type === "TIEBREAK_START") start = e.seq; // log is sorted by seq
  let attemptsA = [];
  let attemptsB = [];
  if (start != null) {
    const voided = new Set(m.log.filter((e) => e.type === "VOID" && e.voidsSeq != null).map((e) => e.voidsSeq));
    const attempts = m.log
      .filter((e) => e.type === "SHOOTOUT_ATTEMPT" && e.seq > start && !voided.has(e.seq) && e.teamId != null)
      .sort((a, b) => a.seq - b.seq)
      .map((e) => ({ seq: e.seq, teamId: e.teamId, playerId: e.playerId, made: (e.points ?? 0) > 0 }));
    attemptsA = attempts.filter((x) => x.teamId === m.teamAId);
    attemptsB = attempts.filter((x) => x.teamId === m.teamBId);
  }
  const winnerId = start == null ? null : shootoutWinner(attemptsA, attemptsB, perTeam);
  const made = (l) => l.filter((x) => x.made).length;
  const nextIsA = attemptsA.length <= attemptsB.length;
  const nextAttemptNumber = (nextIsA ? attemptsA.length : attemptsB.length) + 1;
  const round = Math.max(attemptsA.length, attemptsB.length);
  const next = winnerId == null && attemptsA.length === attemptsB.length ? round + 1 : round;
  return {
    perTeam, attemptsA, attemptsB, winnerId, madeA: made(attemptsA), madeB: made(attemptsB),
    all: [...attemptsA, ...attemptsB].sort((a, b) => a.seq - b.seq),
    decided: winnerId != null, nextIsA, nextAttemptNumber, suddenDeath: nextAttemptNumber > perTeam,
    slots: Math.max(perTeam, next),
  };
}

const TALLY = /(\d+)\s*[–-]\s*(\d+)/;

/** Result of a finished match that was level at full time and won on the tie-breaker, else null. */
export function shootoutResult(m, sport) {
  if (m.status !== "FINISHED" || m.bye || m.scoreA !== m.scoreB) return null;
  const winner = m.winnerId;
  if (winner == null) return null;
  const so = shootout(m, sport);
  if (so.winnerId === winner) {
    const aWon = winner === m.teamAId;
    return { winnerId: winner, winnerMade: aWon ? so.madeA : so.madeB, loserMade: aWon ? so.madeB : so.madeA };
  }
  const tally = TALLY.exec(m.tieNote ?? "");
  if (!tally) return null; // log missing and no tally in the note
  return { winnerId: winner, winnerMade: Number(tally[1]), loserMade: Number(tally[2]) };
}

export const madeBy = (res, teamId) => (teamId === res.winnerId ? res.winnerMade : res.loserMade);
export const wonLine = (res, sport) => `Won ${res.winnerMade}–${res.loserMade} on ${sportInfo(sport).shootoutAttemptPlural}`;

/** "Red Hawks won 3–2 on free throws", "Draw", any other stored note, else null. */
export function resultNote(m, t) {
  if (m.status !== "FINISHED" || m.bye) return null;
  if (isDraw(m)) return DRAW_NOTE;
  const so = shootoutResult(m, t.sport);
  if (so) return `${teamName(t, so.winnerId)} won ${so.winnerMade}–${so.loserMade} on ${sportInfo(t.sport).shootoutAttemptPlural}`;
  return m.tieNote || null;
}

/** "Maya ✓ · Leo ✗" for one team's attempts; null when no attempt names a shooter. */
export function shooterNames(t, attempts) {
  if (attempts.every((a) => a.playerId == null)) return null;
  return attempts.map((a) => (player(t, a.playerId)?.name ?? "Team") + (a.made ? " ✓" : " ✗")).join(" · ");
}

/** True when a finished match's shootout can be shown attempt by attempt. */
export const hasShootoutDetail = (m, t) => shootoutResult(m, t.sport) != null && shootout(m, t.sport).all.length > 0;

// ------------------------------------------------------------------ match feed (wording shared with the Android viewer)

/** "1 03:12" from a score's period tag and clock text; null when there is no clock. */
const feedTime = (period, clock) => (clock ? `${period ?? ""} ${clock}`.trim() : null);

/** Line under "Full time": "Red won", "Draw", or the shootout note without its " · tie-breaker recorded" tail. */
function fullTimeResult(t, winnerId, note) {
  if (winnerId == null) return DRAW_NOTE;
  const shootoutNote = (note ?? "").split(" · ")[0].trim();
  return shootoutNote || `${teamName(t, winnerId)} won`;
}

/**
 * The match's story as feed items, oldest first. Each item: { id, kind, title, subtitle, time, teamId, ... } with
 * kind one of score | sub | halftime | fulltime | shootout | attempt. Score items also carry playerName (null when
 * unknown), points and the running scoreA/scoreB after that score. Built from the event log (voided entries
 * dropped); if the log holds no SCORE entries (not loaded yet, or older data) the scorers come from m.events.
 */
export function buildMatchFeed(t, m) {
  const sp = sportInfo(t.sport);
  const football = t.sport === "FOOTBALL";
  const log = [...(m.log ?? [])].sort((a, b) => a.seq - b.seq);
  const voided = new Set(log.filter((e) => e.type === "VOID" && e.voidsSeq != null).map((e) => e.voidsSeq));
  const useLog = log.some((e) => e.type === "SCORE");
  const name = (id) => player(t, id)?.name ?? null;
  const items = [];
  let scoreA = 0;
  let scoreB = 0;
  const addScore = (s) => {
    if (s.teamId === m.teamAId) scoreA += s.points; else scoreB += s.points;
    const who = name(s.playerId);
    const tn = teamName(t, s.teamId);
    items.push({
      id: s.id, kind: "score", teamId: s.teamId, playerName: who, points: s.points, scoreA, scoreB,
      time: feedTime(s.period, s.clock),
      title: football ? `Goal · ${who ?? tn}` : `${who ?? tn} +${s.points}`,
      subtitle: `${tn} · ${scoreA}–${scoreB}`,
    });
  };
  if (!useLog) for (const s of m.events) addScore(s);

  let shootoutAt = -1; // index of the open shootout header while the match is in the tie-break
  for (const e of log) {
    if (voided.has(e.seq)) continue;
    switch (e.type) {
      case "SCORE": {
        if (!useLog) break;
        const note = e.note ?? "";
        addScore({
          id: e.id, teamId: e.teamId ?? "", playerId: e.playerId, points: e.points ?? 0,
          period: substringBefore(note, " "), clock: substringAfter(note, " "),
        });
        break;
      }
      case "VOID":
        // Undoing a score from the tie-break sends the match back to play (as in replay): drop that shootout.
        if (shootoutAt >= 0 && log.some((x) => x.type === "SCORE" && x.seq === e.voidsSeq)) {
          items.splice(shootoutAt);
          shootoutAt = -1;
        }
        break;
      case "SUB":
        if (e.inPlayerId == null || e.outPlayerId == null) break;
        items.push({
          id: e.id, kind: "sub", teamId: e.teamId, time: null,
          title: `${name(e.inPlayerId) ?? "?"} on for ${name(e.outPlayerId) ?? "?"}`, subtitle: teamName(t, e.teamId),
        });
        break;
      case "BREAK_START":
        items.push({ id: e.id, kind: "halftime", teamId: null, time: null, title: "Half-time", subtitle: null });
        break;
      case "TIEBREAK_START":
        shootoutAt = items.length;
        items.push({ id: e.id, kind: "shootout", teamId: null, time: null, title: sp.shootoutName, subtitle: null });
        break;
      case "SHOOTOUT_ATTEMPT": {
        const made = (e.points ?? 0) > 0;
        const tn = teamName(t, e.teamId);
        items.push({
          id: e.id, kind: "attempt", teamId: e.teamId, playerName: name(e.playerId), made, time: null,
          title: `${name(e.playerId) ?? tn} ${made ? sp.shootoutMadeLabel.toLowerCase() : "missed"}`, subtitle: tn,
        });
        break;
      }
      case "MATCH_END":
        shootoutAt = -1;
        items.push({
          id: e.id, kind: "fulltime", teamId: e.teamId, time: null, title: "Full time",
          subtitle: fullTimeResult(t, e.teamId, e.note),
        });
        break;
      case "REOPEN": {
        const last = items.findLastIndex((x) => x.kind === "fulltime");
        if (last >= 0) items.splice(last, 1);
        break;
      }
      default: break; // MATCH_START, PERIOD_START, PAUSE, RESUME, BREAK_END: not worth a line
    }
  }
  return items;
}

/** Feed items newest first, at most [limit] of them (the live card shows the latest few). */
export const newestFirst = (items, limit = Infinity) => items.slice().reverse().slice(0, limit);

// ------------------------------------------------------------------ standings + leaderboards (Scheduler.kt)

export const pointsRule = (t) => ({ win: t.winPoints, tie: t.tiePoints, loss: t.lossPoints });

/** Ranks by points, then head-to-head (two-way ties only), then point difference, then points scored. */
export function computeStandings(teamIds, matches, rule = { win: 2, tie: 1, loss: 0 }) {
  const rows = new Map(teamIds.map((id) => [id, { teamId: id, played: 0, wins: 0, losses: 0, ties: 0, pf: 0, pa: 0 }]));
  const finished = matches.filter((m) => m.status === "FINISHED");
  for (const m of finished) {
    const a = rows.get(m.teamAId);
    if (!a) continue;
    if (m.bye) { a.played++; a.wins++; continue; }
    const b = rows.get(m.teamBId);
    if (!b) continue;
    a.played++; b.played++;
    a.pf += m.scoreA; a.pa += m.scoreB; b.pf += m.scoreB; b.pa += m.scoreA;
    if (m.winnerId === m.teamAId) { a.wins++; b.losses++; }
    else if (m.winnerId === m.teamBId) { b.wins++; a.losses++; }
    else { a.ties++; b.ties++; }
  }
  const order = new Map(teamIds.map((id, i) => [id, i]));
  const list = teamIds.map((id) => {
    const r = rows.get(id);
    return { ...r, pd: r.pf - r.pa, points: r.wins * rule.win + r.ties * rule.tie + r.losses * rule.loss };
  });
  const sorted = list.sort((x, y) => y.points - x.points || y.pd - x.pd || y.pf - x.pf || order.get(x.teamId) - order.get(y.teamId));
  // Head-to-head fix-up for runs of exactly two teams level on points.
  let i = 0;
  while (i < sorted.length) {
    let j = i;
    while (j + 1 < sorted.length && sorted[j + 1].points === sorted[i].points) j++;
    if (j - i + 1 === 2) {
      const x = sorted[i];
      const y = sorted[j];
      const h2h = finished.find((m) => !m.bye && ((m.teamAId === x.teamId && m.teamBId === y.teamId) || (m.teamAId === y.teamId && m.teamBId === x.teamId)));
      if (h2h && h2h.winnerId === y.teamId) { sorted[i] = y; sorted[j] = x; }
    }
    i = j + 1;
  }
  return sorted;
}

function lastKoLosses(id, t) {
  return t.matches.filter(
    (m) => !m.bye && m.status === "FINISHED" && m.winnerId != null && m.winnerId !== id && (m.teamAId === id || m.teamBId === id),
  ).length;
}

/** One row per team, ranked by furthest stage reached, then total points, then point difference. */
export function overall(t) {
  const ids = t.teams.map((x) => x.id);
  const knockoutDrawn = t.matches.some((m) => !m.bye && (m.isFinal || m.stageType === "KNOCKOUT"));
  const standing = new Map();
  if (usesPoints(t)) {
    const scored = t.matches.filter((m) => awardsPoints(m) || (m.bye && m.stageType !== "KNOCKOUT"));
    for (const r of computeStandings(ids, scored, pointsRule(t))) standing.set(r.teamId, r);
  }
  const rows = ids.map((id) => {
    const st = standing.get(id);
    const mine = t.matches.filter((m) => m.teamAId === id || m.teamBId === id);
    const depth = mine.reduce((d, m) => Math.max(d, frontier(m)), 0);
    const champ = t.championId === id;
    const playedFinal = mine.some((m) => m.isFinal && !m.bye && m.status === "FINISHED");
    const inLater = mine.some((m) => !m.bye && (m.isFinal || m.stageType === "KNOCKOUT"));
    const ko = mine.filter((m) => m.stageType === "KNOCKOUT" && !m.bye);
    const lastKo = ko.reduce((best, m) => (best == null || frontier(m) > frontier(best) ? m : best), null);
    let statusText;
    if (champ) statusText = "Champion";
    else if (playedFinal && t.status === "COMPLETED") statusText = "Finalist";
    else if (t.algorithm === "DOUBLE_ELIM") statusText = lastKoLosses(id, t) >= 2 ? "Eliminated" : "Still in";
    else if (lastKo) {
      if (lastKo.status === "FINISHED" && lastKo.winnerId !== id) statusText = `Out in ${lastKo.stage}`;
      else if (t.status === "COMPLETED") statusText = `Out in ${lastKo.stage}`;
      else statusText = "Still in";
    } else if (knockoutDrawn && !inLater) statusText = t.algorithm === "GROUP_KO" ? "Out in groups" : "Out in round robin";
    else if (inLater) statusText = "Still in";
    else if (t.status === "COMPLETED") statusText = "Completed";
    else statusText = "In progress";
    return {
      teamId: id, statusText, played: st?.played ?? 0, won: st?.wins ?? 0, lost: st?.losses ?? 0, tied: st?.ties ?? 0,
      total: st?.points ?? 0, pd: st?.pd ?? 0, depth, champ,
    };
  });
  return rows.sort((a, b) => Number(b.champ) - Number(a.champ) || b.depth - a.depth || b.total - a.total || b.pd - a.pd);
}

/** Player stats, optionally restricted to one stage index. */
export function playerStats(t, stageIndex = null) {
  const ms = t.matches.filter(
    (m) => !m.bye && ["FINISHED", "LIVE", "BREAK", "TIEBREAK"].includes(m.status) && (stageIndex == null || m.stageIndex === stageIndex),
  );
  const pts = new Map();
  const threes = new Map();
  const games = new Map();
  const teamOf = new Map();
  const addGame = (p, mid) => { if (!games.has(p)) games.set(p, new Set()); games.get(p).add(mid); };
  for (const m of ms) {
    for (const p of m.lineupA) { addGame(p, m.id); if (m.teamAId) teamOf.set(p, m.teamAId); }
    for (const p of m.lineupB) { addGame(p, m.id); if (m.teamBId) teamOf.set(p, m.teamBId); }
    for (const e of m.log) {
      if (e.type !== "SUB") continue;
      for (const p of [e.outPlayerId, e.inPlayerId]) {
        if (p == null) continue;
        addGame(p, m.id);
        if (e.teamId && !teamOf.has(p)) teamOf.set(p, e.teamId);
      }
    }
    for (const e of m.events) {
      if (e.playerId == null) continue;
      pts.set(e.playerId, (pts.get(e.playerId) ?? 0) + e.points);
      if (e.points === 3) threes.set(e.playerId, (threes.get(e.playerId) ?? 0) + 1);
      addGame(e.playerId, m.id);
      teamOf.set(e.playerId, e.teamId);
    }
  }
  return t.players
    .filter((p) => games.has(p.id) || pts.has(p.id))
    .map((p) => {
      const points = pts.get(p.id) ?? 0;
      const g = games.get(p.id)?.size ?? 0;
      return {
        playerId: p.id, teamId: teamOf.get(p.id) ?? t.teams.find((tm) => tm.playerIds.includes(p.id))?.id ?? null,
        points, threes: threes.get(p.id) ?? 0, games: g, ppg: g === 0 ? 0 : points / g,
      };
    })
    .sort((a, b) => b.points - a.points || b.threes - a.threes);
}
