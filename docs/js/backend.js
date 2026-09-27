// Firestore access for the viewer: read-only listeners, mirroring RemoteSync.joinRoom on Android.
//   rooms/{code}                                 -> the tournament snapshot (teams, schedule, results; match logs stripped)
//   rooms/{code}/matches/{matchId}/events        -> the append-only event log of one match
// The rest of the app only sees this small interface, so tests can swap in a fake via globalThis.__HK_BACKEND__.
import { firebaseConfig, FIREBASE_SDK_VERSION } from "./config.js";

const CDN = `https://www.gstatic.com/firebasejs/${FIREBASE_SDK_VERSION}`;

/**
 * @returns {Promise<{
 *   watchRoom(code: string, onRoom: (tournament: object|null) => void, onError: (e: Error) => void): () => void,
 *   watchEvents(code: string, matchId: string, onEvents: (events: object[]) => void, onError: (e: Error) => void): () => void,
 *   getEvents(code: string, matchId: string): Promise<object[]>
 * }>}
 */
export async function createBackend() {
  if (globalThis.__HK_BACKEND__) return globalThis.__HK_BACKEND__;

  const [{ initializeApp }, fs] = await Promise.all([
    import(`${CDN}/firebase-app.js`),
    import(`${CDN}/firebase-firestore.js`),
  ]);
  const app = initializeApp(firebaseConfig);
  // Auto-detect falls back to long polling on networks that block WebChannel streaming (some mobile carriers, proxies).
  const db = fs.initializeFirestore(app, { experimentalAutoDetectLongPolling: true });
  const events = (code, matchId) => fs.collection(db, "rooms", code, "matches", matchId, "events");

  return {
    watchRoom(code, onRoom, onError) {
      return fs.onSnapshot(
        fs.doc(db, "rooms", code),
        (snap) => onRoom(snap.exists() ? (snap.data().tournament ?? null) : null),
        onError,
      );
    },
    watchEvents(code, matchId, onEvents, onError) {
      return fs.onSnapshot(events(code, matchId), (qs) => onEvents(qs.docs.map((d) => d.data())), onError);
    },
    async getEvents(code, matchId) {
      const qs = await fs.getDocs(events(code, matchId));
      return qs.docs.map((d) => d.data());
    },
  };
}
