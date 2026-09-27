// Run with:  node --test web-tests/
// Mirrors the Kotlin unit tests for the parts the web viewer ports (event replay, shootout, standings).
import test from "node:test";
import assert from "node:assert/strict";
import * as M from "../docs/js/model.js";

const team = (id, name, color = 0, playerIds = []) => ({ id, name, color, playerIds });
const rawMatch = (o) => ({ id: "m1", number: 1, stageIndex: 0, stage: "Group Stage", stageType: "GROUP", group: "Group A", teamAId: "a", teamBId: "b", lineupA: ["pa"], lineupB: ["pb"], ...o });
const rawTournament = (matches, o = {}) => ({
  id: "t1", name: "Cup", sport: "BASKETBALL", algorithm: "GROUP_KO", status: "ACTIVE", groups: 1, advance: 2,
  format: { type: "HALVES", periodMin: 12, breakMin: 5 },
  teams: [team("a", "Red"), team("b", "Blue", 1), team("c", "Green", 2), team("d", "Gold", 3)],
  players: [{ id: "pa", name: "Ana" }, { id: "pb", name: "Ben" }],
  matches, ...o,
});
let seq = 0;
const ev = (type, o = {}) => ({ id: `e${++seq}`, seq: seq, type, epochMs: 1_000_000 + seq, ...o });
const t0 = (matches, o) => M.parseTournament(rawTournament(matches, o));

test("normalizeCode upper-cases, drops confusable characters and caps at 6", () => {
  assert.equal(M.normalizeCode(" ab-c0 o1i 234 5678"), "ABC234");
  assert.equal(M.normalizeCode(null), "");
});

test("parseTournament fills the same defaults as Json.fromJson", () => {
  const t = M.parseTournament({ id: "x", teams: [], matches: [{ id: "m" }] });
  assert.equal(t.rrFinal, true);
  assert.equal(t.winPoints, 2);
  assert.equal(t.format.breakMin, 5);
  assert.equal(t.matches[0].status, "SCHEDULED");
  assert.equal(t.matches[0].teamAId, null);
});

test("computeDisplaySeconds counts down only while running and never goes negative or upward", () => {
  assert.equal(M.computeDisplaySeconds(600, 0, false, 99_000), 600);
  assert.equal(M.computeDisplaySeconds(600, 0, true, 90_000), 510);
  assert.equal(M.computeDisplaySeconds(10, 0, true, 60_000), 0);
  assert.equal(M.computeDisplaySeconds(600, 50_000, true, 40_000), 600); // viewer clock behind the host
});

test("replay rebuilds score, clock and undo from the log", () => {
  seq = 0;
  const base = t0([rawMatch({})]);
  const log = [
    ev("MATCH_START", { clockSec: 720 }),
    ev("RESUME", { clockSec: 720 }),
    ev("SCORE", { teamId: "a", playerId: "pa", points: 3, note: "1 11:40", clockSec: 700 }),
    ev("SCORE", { teamId: "b", playerId: "pb", points: 2, note: "1 10:00" }),
  ];
  log.push(ev("VOID", { voidsSeq: 4 })); // undo B's basket
  const m = M.replay(base.matches[0], log, base.format);
  assert.equal(m.status, "LIVE");
  assert.deepEqual([m.scoreA, m.scoreB], [3, 0]);
  assert.equal(m.events.length, 1);
  assert.equal(m.events[0].period, "1");
  assert.equal(m.events[0].clock, "11:40");
  assert.equal(m.clockRunning, true);
  assert.equal(m.remainingSec, 700);
});

test("halftime break and second half events drive status and period", () => {
  seq = 0;
  const base = t0([rawMatch({})]);
  const log = [
    ev("MATCH_START", { clockSec: 720 }),
    ev("BREAK_START", { clockSec: 300 }),
    ev("BREAK_END"),
    ev("PERIOD_START", { clockSec: 720 }),
  ];
  const m = M.replay(base.matches[0], log.slice(0, 2), base.format);
  assert.equal(m.status, "BREAK");
  assert.equal(m.breakRemainingSec, 300);
  const m2 = M.replay(base.matches[0], log, base.format);
  assert.equal(m2.status, "LIVE");
  assert.equal(m2.period, 2);
  assert.equal(m2.clockRunning, true);
});

test("substitution swaps the lineup", () => {
  seq = 0;
  const base = t0([rawMatch({})]);
  const log = [ev("MATCH_START", { clockSec: 720 }), ev("SUB", { teamId: "a", outPlayerId: "pa", inPlayerId: "px" })];
  assert.deepEqual(M.replay(base.matches[0], log, base.format).lineupA, ["px"]);
});

function shootoutLog(results) {
  seq = 0;
  const log = [ev("MATCH_START", { clockSec: 720 }), ev("SCORE", { teamId: "a", points: 2, note: "2 00:10" }), ev("SCORE", { teamId: "b", points: 2, note: "2 00:05" }), ev("TIEBREAK_START")];
  for (const [team, made] of results) log.push(ev("SHOOTOUT_ATTEMPT", { teamId: team, playerId: team === "a" ? "pa" : "pb", points: made ? 1 : 0 }));
  return log;
}

test("basketball shootout: alternating attempts, decided early, tally read from the log", () => {
  const t = t0([rawMatch({})]);
  const log = shootoutLog([["a", 1], ["b", 0], ["a", 1], ["b", 0], ["a", 1]]); // 3-0 after 3 vs 2 attempts: B cannot catch up
  const m = M.replay(t.matches[0], log, t.format);
  assert.equal(m.status, "TIEBREAK");
  const so = M.shootout(m, "BASKETBALL");
  assert.equal(so.winnerId, "a");
  assert.deepEqual([so.madeA, so.madeB], [3, 0]);
  assert.equal(so.decided, true);
});

test("shootout goes to sudden death when level after the allotment", () => {
  const t = t0([rawMatch({})]);
  const log = shootoutLog([["a", 1], ["b", 1], ["a", 0], ["b", 0], ["a", 1], ["b", 1]]); // 2-2 after 3 each
  const so = M.shootout(M.replay(t.matches[0], log, t.format), "BASKETBALL");
  assert.equal(so.winnerId, null);
  assert.equal(so.suddenDeath, true);
  assert.equal(so.slots, 4);
  const log2 = [...log, ev("SHOOTOUT_ATTEMPT", { teamId: "a", points: 1 }), ev("SHOOTOUT_ATTEMPT", { teamId: "b", points: 0 })];
  assert.equal(M.shootout(M.replay(t.matches[0], log2, t.format), "BASKETBALL").winnerId, "a");
});

test("a voided shootout attempt is ignored", () => {
  const t = t0([rawMatch({})]);
  const log = shootoutLog([["a", 1], ["b", 1]]);
  log.push(ev("VOID", { voidsSeq: log[log.length - 1].seq }));
  const so = M.shootout(M.replay(t.matches[0], log, t.format), "BASKETBALL");
  assert.equal(so.attemptsB.length, 0);
});

test("finished shootout match: result note from the log, or from the note when the log is not loaded", () => {
  const t = t0([rawMatch({})]);
  const log = shootoutLog([["a", 1], ["b", 0], ["a", 1], ["b", 0], ["a", 1]]);
  log.push(ev("MATCH_END", { teamId: "a", note: "Won 3–0 on free throws · tie-breaker recorded" }));
  const full = M.replay(t.matches[0], log, t.format);
  const tt = { ...t, matches: [full] };
  assert.equal(M.resultNote(full, tt), "Red won 3–0 on free throws");
  assert.equal(M.hasShootoutDetail(full, tt), true);
  const bare = { ...t.matches[0], status: "FINISHED", scoreA: 2, scoreB: 2, winnerId: "a", tieNote: "Won 3–1 on free throws · tie-breaker recorded" };
  assert.equal(M.resultNote(bare, { ...t, matches: [bare] }), "Red won 3–1 on free throws");
  assert.equal(M.hasShootoutDetail(bare, t), false);
});

test("a level group match ends as a draw", () => {
  const m = M.parseTournament(rawTournament([rawMatch({ status: "FINISHED", scoreA: 4, scoreB: 4, winnerId: null, tieNote: "Draw" })])).matches[0];
  assert.equal(M.isDraw(m), true);
  assert.equal(M.resultNote(m, t0([m])), "Draw");
});

test("mergeRemote keeps a FINISHED snapshot result even if the event log is missing MATCH_END", () => {
  seq = 0;
  const raw = rawMatch({ status: "FINISHED", scoreA: 5, scoreB: 3, winnerId: "a" });
  const base = t0([raw]);
  const partial = new Map([["m1", [ev("MATCH_START", { clockSec: 720 }), ev("SCORE", { teamId: "a", points: 2, note: "1 10:00" })]]]);
  const applied = M.applyRemote(base, partial);
  assert.equal(applied.matches[0].status, "LIVE"); // the raw replay would flip back to live...
  const merged = M.mergeRemote(base, partial);
  assert.equal(merged.matches[0].status, "FINISHED"); // ...the merge does not
  assert.deepEqual([merged.matches[0].scoreA, merged.matches[0].scoreB], [5, 3]);
  assert.equal(merged.matches[0].log.length, 2);
});

test("standings: configurable points, draws, head-to-head on a two-way tie, then point difference", () => {
  const mk = (id, a, b, sa, sb, w) => rawMatch({ id, teamAId: a, teamBId: b, scoreA: sa, scoreB: sb, status: "FINISHED", winnerId: w });
  const t = t0([mk("1", "a", "b", 10, 8, "a"), mk("2", "b", "c", 6, 6, null), mk("3", "a", "c", 5, 9, "c")]);
  const rows = M.computeStandings(["a", "b", "c"], t.matches, { win: 3, tie: 1, loss: 0 });
  assert.deepEqual(rows.map((r) => [r.teamId, r.points]), [["c", 4], ["a", 3], ["b", 1]]);
  assert.equal(rows[0].ties, 1);
  // x and y are level on 2 points and y has the far better point difference, but x won the head-to-head
  const h2h = M.computeStandings(["x", "y", "z"], [mk("5", "x", "y", 10, 9, "x"), mk("6", "y", "z", 30, 0, "y")].map((m) => ({ ...t.matches[0], ...m })), { win: 2, tie: 1, loss: 0 });
  assert.deepEqual(h2h.map((r) => r.teamId), ["x", "y", "z"]);
});

test("overall leaderboard marks the champion and knockout exits", () => {
  const ko = (id, num, a, b, sa, sb, w, o = {}) => rawMatch({ id, number: num, stageIndex: 1, stage: "Semi-finals", stageType: "KNOCKOUT", group: "", teamAId: a, teamBId: b, scoreA: sa, scoreB: sb, status: "FINISHED", winnerId: w, ...o });
  const t = t0(
    [
      rawMatch({ id: "g1", teamAId: "a", teamBId: "b", scoreA: 9, scoreB: 4, status: "FINISHED", winnerId: "a" }),
      ko("s1", 3, "a", "d", 7, 5, "a"), ko("s2", 4, "b", "c", 3, 6, "c"),
      ko("f", 5, "a", "c", 8, 6, "a", { stageIndex: 2, stage: "Final", isFinal: true, stageType: "LEAGUE" }),
    ],
    { status: "COMPLETED", championId: "a" },
  );
  const rows = M.overall(t);
  assert.equal(rows[0].teamId, "a");
  assert.equal(rows[0].statusText, "Champion");
  assert.equal(rows.find((r) => r.teamId === "c").statusText, "Finalist");
  assert.equal(rows.find((r) => r.teamId === "d").statusText, "Out in Semi-finals");
});

test("player stats sum points from score events and count games", () => {
  const t = t0([
    rawMatch({ id: "m1", status: "FINISHED", events: [{ id: "x", teamId: "a", playerId: "pa", points: 3, period: "1", clock: "1", createdAt: 1 }, { id: "y", teamId: "a", playerId: "pa", points: 2, period: "1", clock: "2", createdAt: 2 }] }),
  ]);
  const [top] = M.playerStats(t);
  assert.deepEqual([top.playerId, top.points, top.threes, top.games, top.ppg], ["pa", 5, 1, 1, 5]);
});

test("expectedStages lists the stages still to come", () => {
  assert.deepEqual(M.expectedStages(t0([], { algorithm: "GROUP_KO", groups: 2, advance: 2 })), ["Group Stage", "Semi-finals", "Final"]);
  assert.deepEqual(M.expectedStages(t0([], { algorithm: "ROUND_ROBIN", rrSemis: true, rrFinal: true, teams: [1, 2, 3, 4, 5].map((i) => team(`t${i}`, `T${i}`)) })), ["Round Robin", "Semi-finals", "Final"]);
  assert.deepEqual(M.expectedStages(t0([], { algorithm: "SWISS" })), ["Swiss"]);
});

// ------------------------------------------------------------------ match feed

const feedOf = (log, o = {}, t = t0([rawMatch(o)])) => M.buildMatchFeed(t, M.replay(t.matches[0], log, t.format));

test("feed: chronological, running score, halftime, subs, noise skipped", () => {
  seq = 0;
  const items = feedOf([
    ev("MATCH_START", { clockSec: 720 }), ev("RESUME"),
    ev("SCORE", { teamId: "a", playerId: "pa", points: 3, note: "1 11:40" }),
    ev("PAUSE"), ev("SUB", { teamId: "b", outPlayerId: "pb", inPlayerId: "pa" }), ev("RESUME"),
    ev("SCORE", { teamId: "b", playerId: "pb", points: 2, note: "1 10:00" }),
    ev("BREAK_START", { clockSec: 300 }), ev("BREAK_END"), ev("PERIOD_START", { clockSec: 720 }),
    ev("SCORE", { teamId: "a", playerId: "pa", points: 1, note: "2 09:05" }),
  ]);
  assert.deepEqual(items.map((i) => i.kind), ["score", "sub", "score", "halftime", "secondhalf", "score"]);
  assert.deepEqual(items.map((i) => i.title), ["Ana +3", "Ana on for Ben", "Ben +2", "Half-time", "Second half", "Ana +1"]);
  assert.deepEqual(items.map((i) => i.subtitle), ["Red · 3–0", "Blue", "Blue · 3–2", null, null, "Red · 4–2"]);
  assert.deepEqual([items[0].time, items[5].time, items[1].time], ["1 11:40", "2 09:05", null]);
  assert.deepEqual([items[5].scoreA, items[5].scoreB, items[5].points, items[5].playerName], [4, 2, 1, "Ana"]);
  assert.deepEqual(M.newestFirst(items, 2).map((i) => i.title), ["Ana +1", "Second half"]);
  assert.equal(M.newestFirst(items).length, 6);
  assert.equal(items[0].title, "Ana +3"); // newestFirst does not reorder the original
});

test("feed: a voided score disappears and the running score skips it", () => {
  seq = 0;
  const log = [ev("MATCH_START"), ev("SCORE", { teamId: "a", playerId: "pa", points: 2, note: "1 10:00" }), ev("SCORE", { teamId: "b", points: 3, note: "1 09:00" })];
  log.push(ev("VOID", { voidsSeq: 2 }), ev("SCORE", { teamId: "b", playerId: "pb", points: 2, note: "1 08:00" }));
  const items = feedOf(log);
  assert.deepEqual(items.map((i) => [i.title, i.subtitle]), [["Blue +3", "Blue · 0–3"], ["Ben +2", "Blue · 0–5"]]);
  assert.equal(items[0].playerName, null);
});

test("feed: falls back to the snapshot's score events when the log is not loaded", () => {
  const t = t0([rawMatch({ status: "LIVE", scoreA: 3, events: [
    { id: "x", teamId: "a", playerId: "pa", points: 2, period: "1", clock: "08:30" },
    { id: "y", teamId: "a", playerId: "ghost", points: 1, period: "1", clock: "" },
  ] })]);
  const items = M.buildMatchFeed(t, t.matches[0]);
  assert.deepEqual(items.map((i) => [i.title, i.subtitle, i.time]), [["Ana +2", "Red · 2–0", "1 08:30"], ["Red +1", "Red · 3–0", null]]);
  assert.equal(items[1].playerName, null); // unknown player id
  const empty = t0([rawMatch({})]);
  assert.deepEqual(M.buildMatchFeed(empty, empty.matches[0]), []);
});

test("feed: full time reads '<Winner> won' or 'Draw', and a reopened match loses its old full time", () => {
  seq = 0;
  const win = feedOf([ev("MATCH_START"), ev("SCORE", { teamId: "b", points: 2, note: "1 05:00" }), ev("MATCH_END", { teamId: "b" })]);
  assert.deepEqual([win.at(-1).kind, win.at(-1).title, win.at(-1).subtitle], ["fulltime", "Full time", "Blue won"]);
  seq = 0;
  const draw = [ev("MATCH_START"), ev("MATCH_END", { note: "Draw" })];
  assert.deepEqual(feedOf(draw).map((i) => [i.title, i.subtitle]), [["Full time", "Draw"]]);
  assert.deepEqual(feedOf([...draw, ev("REOPEN")]), []);
});

test("feed: shootout header, attempts, voided attempt, and the tie note as the result line", () => {
  const log = shootoutLog([["a", 1], ["b", 0], ["a", 1], ["b", 1]]);
  log.push(ev("VOID", { voidsSeq: log.at(-1).seq }), ev("SHOOTOUT_ATTEMPT", { teamId: "b", points: 0 }), ev("SHOOTOUT_ATTEMPT", { teamId: "a", playerId: "pa", points: 1 }));
  log.push(ev("MATCH_END", { teamId: "a", note: "Won 3–0 on free throws · tie-breaker recorded" }));
  const items = feedOf(log);
  assert.deepEqual(items.map((i) => i.title), [
    "Red +2", "Blue +2", "Free-throw shootout", "Ana made", "Ben missed", "Ana made", "Blue missed", "Ana made", "Full time",
  ]);
  assert.deepEqual([items[4].subtitle, items[4].made], ["Blue", false]);
  assert.equal(items.at(-1).subtitle, "Won 3–0 on free throws");
});

test("feed: undoing a score from the tie-break drops that shootout header", () => {
  const log = shootoutLog([]);
  log.push(ev("VOID", { voidsSeq: 3, clockSec: 0 }));
  assert.deepEqual(feedOf(log).map((i) => i.title), ["Red +2"]);
});

test("feed: football wording", () => {
  seq = 0;
  const t = t0([rawMatch({})], { sport: "FOOTBALL" });
  const log = [
    ev("MATCH_START"), ev("SCORE", { teamId: "a", playerId: "pa", points: 1, note: "1 03:12" }), ev("SCORE", { teamId: "b", points: 1, note: "2 01:00" }),
    ev("TIEBREAK_START"), ev("SHOOTOUT_ATTEMPT", { teamId: "a", playerId: "pa", points: 1 }), ev("SHOOTOUT_ATTEMPT", { teamId: "b", playerId: "pb", points: 0 }),
  ];
  const items = M.buildMatchFeed(t, M.replay(t.matches[0], log, t.format));
  assert.deepEqual(items.map((i) => [i.title, i.subtitle, i.time]), [
    ["Goal · Ana", "Red · 1–0", "1 03:12"], ["Goal · Blue", "Blue · 1–1", "2 01:00"],
    ["Free-kick shootout", null, null], ["Ana scored", "Red", null], ["Ben missed", "Blue", null],
  ]);
});

test("feed: a substitution with players that are not on the roster says Unknown player", () => {
  seq = 0;
  const items = feedOf([
    ev("MATCH_START", { clockSec: 720 }), ev("RESUME"),
    ev("SUB", { teamId: "a", outPlayerId: "ghost1", inPlayerId: "ghost2" }),
  ]);
  assert.equal(items[0].title, "Unknown player on for Unknown player");
});
