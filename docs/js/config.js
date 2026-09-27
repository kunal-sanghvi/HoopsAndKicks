// Firebase web config for the read-only viewer. These values are identifiers, not secrets: access is enforced by
// firestore.rules (anyone with a room code may read that one room, only the host may write) and by restricting the
// API key to this site in Google Cloud Console (see the README, "Web viewer").
export const firebaseConfig = {
  apiKey: "AIzaSyBMCRMR09FcDt9ZLAfAG_CePJx-Pk7csCk",
  authDomain: "hoopsandkicks.firebaseapp.com",
  projectId: "hoopsandkicks",
  storageBucket: "hoopsandkicks.firebasestorage.app",
  messagingSenderId: "910331092885",
  appId: "1:910331092885:web:2e5a1c68a4d709c93ee09c",
};

export const FIREBASE_SDK_VERSION = "12.19.0";
