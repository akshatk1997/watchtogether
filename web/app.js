import { initializeApp } from "https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js";
import {
  getAuth,
  onAuthStateChanged,
  GoogleAuthProvider,
  signInWithPopup,
  signInAnonymously,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signOut
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js";
import {
  getFirestore,
  doc,
  getDoc,
  setDoc,
  updateDoc,
  addDoc,
  collection,
  query,
  orderBy,
  limit,
  onSnapshot,
  serverTimestamp,
  arrayUnion
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js";

const firebaseConfig = {
  apiKey: "AIzaSyDpsp5Jh4Gq3Hn9hGMQlzoETqRcHDG4dQI",
  authDomain: "gen-lang-client-0046061309.firebaseapp.com",
  projectId: "gen-lang-client-0046061309",
  storageBucket: "gen-lang-client-0046061309.firebasestorage.app",
  messagingSenderId: "602219792722",
  appId: "1:602219792722:android:994285b9f7155a470d128b"
};

const DATABASE_ID = "ai-studio-android-watchtog-ae63a478-739a-4bb1-9781-55f6102b9e0f";
const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app, DATABASE_ID);

// State
let currentUid = localStorage.getItem("wt_guest_uid") || null;
let currentName = "Guest";
let isEmailSignUp = false;
let activeRoomCode = null;
let unsubRoom = null;
let unsubChat = null;
let unsubQueue = null;
let unsubReactions = null;
let ytPlayer = null;
let pendingYtId = null;
let hlsInstance = null;
let currentMediaUrl = "";

window.onYouTubeIframeAPIReady = () => {
  if (pendingYtId) {
    initYtPlayer(pendingYtId);
    pendingYtId = null;
  }
};

function initYtPlayer(videoId) {
  ytPlayer = new YT.Player("yt-player", {
    videoId,
    host: "https://www.youtube-nocookie.com",
    playerVars: { playsinline: 1, rel: 0, autoplay: 1 }
  });
}

// Elements
const authView = document.getElementById("auth-view");
const homeView = document.getElementById("home-view");
const roomView = document.getElementById("room-view");
const authError = document.getElementById("auth-error");
const userBadge = document.getElementById("user-badge");

function showView(view) {
  [authView, homeView, roomView].forEach(v => v.classList.remove("active"));
  view.classList.add("active");
}

function showError(msg) {
  authError.textContent = msg;
  authError.classList.remove("hidden");
}

function clearError() {
  authError.classList.add("hidden");
}

// Auth listeners
onAuthStateChanged(auth, (user) => {
  if (user) {
    currentUid = user.uid;
    currentName = user.displayName || user.email || "Cinema Fan";
    userBadge.textContent = currentName;
    handlePostAuth();
  } else if (localStorage.getItem("wt_guest_active") === "1" && currentUid) {
    currentName = "Guest";
    userBadge.textContent = "Guest";
    handlePostAuth();
  } else {
    showView(authView);
  }
});

function handlePostAuth() {
  const params = new URLSearchParams(window.location.search);
  const roomParam = params.get("room");
  if (roomParam && roomParam.length === 6) {
    joinRoom(roomParam.toUpperCase());
  } else if (activeRoomCode) {
    showView(roomView);
  } else {
    showView(homeView);
  }
}

document.getElementById("btn-google").addEventListener("click", async () => {
  clearError();
  try {
    await signInWithPopup(auth, new GoogleAuthProvider());
    localStorage.removeItem("wt_guest_active");
  } catch (e) {
    showError(e.message || "Google Sign-In failed. You can use Email or Continue as Guest.");
  }
});

document.getElementById("btn-guest").addEventListener("click", async () => {
  clearError();
  try {
    await signInAnonymously(auth);
  } catch {
    if (!currentUid) {
      currentUid = "guest_" + Math.random().toString(36).substring(2, 10);
      localStorage.setItem("wt_guest_uid", currentUid);
    }
    localStorage.setItem("wt_guest_active", "1");
    currentName = "Guest";
    userBadge.textContent = "Guest";
    handlePostAuth();
  }
});

const emailForm = document.getElementById("email-form");
document.getElementById("btn-toggle-email").addEventListener("click", () => {
  emailForm.classList.toggle("hidden");
});

document.getElementById("btn-email-mode").addEventListener("click", (e) => {
  isEmailSignUp = !isEmailSignUp;
  document.getElementById("btn-email-submit").textContent = isEmailSignUp ? "Create Account" : "Sign In with Email";
  e.target.textContent = isEmailSignUp ? "Already have an account? Sign In" : "New here? Create an account";
});

emailForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  clearError();
  const email = document.getElementById("email-input").value.trim();
  const pass = document.getElementById("password-input").value;
  try {
    if (isEmailSignUp) {
      await createUserWithEmailAndPassword(auth, email, pass);
    } else {
      await signInWithEmailAndPassword(auth, email, pass);
    }
    localStorage.removeItem("wt_guest_active");
  } catch (err) {
    showError(err.message || "Email authentication failed.");
  }
});

document.getElementById("btn-signout").addEventListener("click", async () => {
  leaveRoom();
  localStorage.removeItem("wt_guest_active");
  await signOut(auth).catch(() => {});
  showView(authView);
});

// Room helpers
function generateCode() {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  let out = "";
  for (let i = 0; i < 6; i++) out += chars[Math.floor(Math.random() * chars.length)];
  return out;
}

function detectMedia(url) {
  if (!url) return { type: "NONE", id: "" };
  const ytMatch = url.match(/(?:v=|youtu\.be\/|shorts\/|embed\/|live\/)([a-zA-Z0-9_-]{11})/);
  if (ytMatch) return { type: "YOUTUBE", id: ytMatch[1] };
  const driveMatch = url.match(/drive\.google\.com\/file\/d\/([a-zA-Z0-9_-]+)/);
  if (driveMatch) return { type: "DRIVE", id: driveMatch[1] };
  if (url.includes(".m3u8")) return { type: "HLS", id: url };
  return { type: "HLS", id: url };
}

document.getElementById("btn-create-room").addEventListener("click", async () => {
  const code = generateCode();
  const name = document.getElementById("create-room-name").value.trim() || "Watch Party";
  const mediaUrl = document.getElementById("create-media-url").value.trim();
  const media = detectMedia(mediaUrl);

  try {
    await setDoc(doc(db, "rooms", code), {
      roomCode: code,
      name,
      hostId: currentUid,
      memberUids: [currentUid],
      kickedUids: [],
      mediaUrl,
      mediaType: media.type,
      isPlaying: false,
      positionMs: 0,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
    joinRoom(code);
  } catch (err) {
    alert("Could not create room: " + err.message);
  }
});

document.getElementById("btn-join-room").addEventListener("click", () => {
  const code = document.getElementById("join-room-code").value.trim().toUpperCase();
  if (code.length === 6) joinRoom(code);
});

document.querySelectorAll(".livetv-btn").forEach((btn) => {
  btn.addEventListener("click", async () => {
    const code = generateCode();
    const name = btn.dataset.name || "Live TV";
    const mediaUrl = btn.dataset.url || "";
    try {
      await setDoc(doc(db, "rooms", code), {
        roomCode: code,
        name,
        hostId: currentUid,
        memberUids: [currentUid],
        kickedUids: [],
        mediaUrl,
        mediaType: "HLS",
        isPlaying: true,
        positionMs: 0,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      });
      joinRoom(code);
    } catch (err) {
      alert("Could not start Live TV room: " + err.message);
    }
  });
});

async function joinRoom(code) {
  activeRoomCode = code;
  document.getElementById("room-code-pill").textContent = code;
  showView(roomView);

  const roomRef = doc(db, "rooms", code);
  try {
    const snap = await getDoc(roomRef);
    if (snap.exists() && currentUid) {
      await updateDoc(roomRef, { memberUids: arrayUnion(currentUid) }).catch(() => {});
    }
  } catch {}

  if (unsubRoom) unsubRoom();
  unsubRoom = onSnapshot(roomRef, (snap) => {
    if (!snap.exists()) return;
    const data = snap.data();
    document.getElementById("room-title").textContent = data.name || "Watch Party";
    if (data.mediaUrl && data.mediaUrl !== currentMediaUrl) {
      currentMediaUrl = data.mediaUrl;
      loadPlayer(data.mediaUrl);
    }
    syncPlayback(Boolean(data.isPlaying), Number(data.positionMs || 0));
  });

  if (unsubChat) unsubChat();
  const chatQ = query(collection(db, "rooms", code, "messages"), orderBy("timestamp", "asc"), limit(50));
  unsubChat = onSnapshot(chatQ, (snap) => {
    const box = document.getElementById("chat-messages");
    box.innerHTML = "";
    snap.forEach((d) => {
      const m = d.data();
      const div = document.createElement("div");
      div.className = "chat-msg";
      div.innerHTML = `<strong>${escapeHtml(m.senderName || "User")}:</strong>${escapeHtml(m.text || "")}`;
      box.appendChild(div);
    });
    box.scrollTop = box.scrollHeight;
  });

  if (unsubQueue) unsubQueue();
  const queueQ = query(collection(db, "rooms", code, "queue"), orderBy("addedAt", "asc"), limit(30));
  unsubQueue = onSnapshot(queueQ, (snap) => {
    const qList = document.getElementById("queue-list");
    qList.innerHTML = "";
    snap.forEach((d) => {
      const item = d.data();
      const div = document.createElement("div");
      div.className = "queue-item";
      div.innerHTML = `<span class="small">${escapeHtml(item.url || "")}</span><button class="btn btn-outline btn-sm">Play</button>`;
      div.querySelector("button").addEventListener("click", async () => {
        const media = detectMedia(item.url || "");
        await updateDoc(doc(db, "rooms", code), {
          mediaUrl: item.url,
          mediaType: media.type,
          positionMs: 0,
          isPlaying: true,
          updatedAt: serverTimestamp()
        });
      });
      qList.appendChild(div);
    });
  });

  if (unsubReactions) unsubReactions();
  let initialReactionsLoaded = false;
  const reactQ = query(collection(db, "rooms", code, "reactions"), orderBy("timestamp", "desc"), limit(10));
  unsubReactions = onSnapshot(reactQ, (snap) => {
    if (!initialReactionsLoaded) {
      initialReactionsLoaded = true;
      return;
    }
    snap.docChanges().forEach((change) => {
      if (change.type === "added") {
        spawnFloatingEmoji(change.doc.data().emoji || "❤️");
      }
    });
  });
}

function spawnFloatingEmoji(emoji) {
  const overlay = document.getElementById("reactions-overlay");
  if (!overlay) return;
  const el = document.createElement("div");
  el.className = "floating-emoji";
  el.textContent = emoji;
  el.style.left = `${15 + Math.random() * 70}%`;
  overlay.appendChild(el);
  setTimeout(() => el.remove(), 2200);
}

function leaveRoom() {
  if (unsubRoom) { unsubRoom(); unsubRoom = null; }
  if (unsubChat) { unsubChat(); unsubChat = null; }
  if (unsubQueue) { unsubQueue(); unsubQueue = null; }
  if (unsubReactions) { unsubReactions(); unsubReactions = null; }
  activeRoomCode = null;
  currentMediaUrl = "";
  showView(homeView);
}

document.getElementById("btn-leave-room").addEventListener("click", leaveRoom);
document.getElementById("btn-copy-link").addEventListener("click", () => {
  const url = `${window.location.origin}${window.location.pathname}?room=${activeRoomCode}`;
  navigator.clipboard.writeText(url);
  alert("Invite link copied: " + url);
});

document.getElementById("btn-load-media").addEventListener("click", async () => {
  const url = document.getElementById("room-media-url").value.trim();
  if (!url || !activeRoomCode) return;
  const media = detectMedia(url);
  await updateDoc(doc(db, "rooms", activeRoomCode), {
    mediaUrl: url,
    mediaType: media.type,
    positionMs: 0,
    isPlaying: true,
    updatedAt: serverTimestamp()
  });
});

document.getElementById("btn-play-pause").addEventListener("click", async () => {
  if (!activeRoomCode) return;
  const ref = doc(db, "rooms", activeRoomCode);
  const snap = await getDoc(ref);
  if (!snap.exists()) return;
  const playing = !snap.data().isPlaying;
  await updateDoc(ref, { isPlaying: playing, updatedAt: serverTimestamp() });
});

document.getElementById("chat-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const input = document.getElementById("chat-input");
  const text = input.value.trim();
  if (!text || !activeRoomCode) return;
  input.value = "";
  await addDoc(collection(db, "rooms", activeRoomCode, "messages"), {
    senderId: currentUid,
    senderName: currentName,
    text,
    timestamp: serverTimestamp()
  });
});

document.querySelectorAll(".react-btn").forEach((btn) => {
  btn.addEventListener("click", async () => {
    if (!activeRoomCode) return;
    await addDoc(collection(db, "rooms", activeRoomCode, "reactions"), {
      emoji: btn.dataset.emoji || "❤️",
      senderId: currentUid,
      timestamp: serverTimestamp()
    });
  });
});

const tabChat = document.getElementById("tab-chat");
const tabQueue = document.getElementById("tab-queue");
const panelChat = document.getElementById("panel-chat");
const panelQueue = document.getElementById("panel-queue");
tabChat.addEventListener("click", () => {
  tabChat.classList.add("active"); tabQueue.classList.remove("active");
  panelChat.classList.remove("hidden"); panelQueue.classList.add("hidden");
});
tabQueue.addEventListener("click", () => {
  tabQueue.classList.add("active"); tabChat.classList.remove("active");
  panelQueue.classList.remove("hidden"); panelChat.classList.add("hidden");
});

document.getElementById("queue-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const input = document.getElementById("queue-url-input");
  const url = input.value.trim();
  if (!url || !activeRoomCode) return;
  input.value = "";
  await addDoc(collection(db, "rooms", activeRoomCode, "queue"), {
    url,
    addedBy: currentName,
    addedAt: serverTimestamp()
  });
});

function loadPlayer(url) {
  const media = detectMedia(url);
  const ytEl = document.getElementById("yt-player");
  const hlsEl = document.getElementById("hls-player");
  const driveEl = document.getElementById("drive-player");
  const emptyEl = document.getElementById("empty-player");

  emptyEl.classList.add("hidden");
  hlsEl.classList.add("hidden");
  driveEl.classList.add("hidden");
  ytEl.classList.add("hidden");

  if (media.type === "YOUTUBE") {
    ytEl.classList.remove("hidden");
    if (ytPlayer && ytPlayer.loadVideoById) {
      ytPlayer.loadVideoById(media.id);
    } else if (window.YT && window.YT.Player) {
      initYtPlayer(media.id);
    } else {
      pendingYtId = media.id;
    }
  } else if (media.type === "DRIVE") {
    driveEl.classList.remove("hidden");
    driveEl.src = `https://drive.google.com/file/d/${media.id}/preview`;
  } else {
    hlsEl.classList.remove("hidden");
    if (window.Hls && Hls.isSupported()) {
      if (hlsInstance) hlsInstance.destroy();
      hlsInstance = new Hls();
      hlsInstance.loadSource(url);
      hlsInstance.attachMedia(hlsEl);
    } else {
      hlsEl.src = url;
    }
  }
}

function syncPlayback(isPlaying, positionMs) {
  if (ytPlayer && ytPlayer.getPlayerState) {
    const state = ytPlayer.getPlayerState();
    const targetSec = positionMs / 1000;
    if (ytPlayer.getCurrentTime && Math.abs(ytPlayer.getCurrentTime() - targetSec) > 2.5) {
      ytPlayer.seekTo(targetSec, true);
    }
    if (isPlaying && state !== 1) ytPlayer.playVideo();
    if (!isPlaying && state === 1) ytPlayer.pauseVideo();
  }
  const hlsEl = document.getElementById("hls-player");
  if (!hlsEl.classList.contains("hidden")) {
    if (isPlaying && hlsEl.paused) hlsEl.play().catch(() => {});
    if (!isPlaying && !hlsEl.paused) hlsEl.pause();
  }
}

function escapeHtml(str) {
  return str.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
