const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_host_123";
const BOB_UID = "bob_guest_456";
const CHARLIE_UID = "charlie_intruder_789";
const ADMIN_UID = "admin_uljha_001";
const ADMIN_EMAIL = "uljhasawal@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const pastDate = (offsetMs = 5000) => new Date(Date.now() - offsetMs);

function validUserPayload(uid) {
  const ts = pastDate(10000);
  return {
    userId: uid,
    displayName: "CinemaHost",
    avatarColorHex: "#FF4D6D",
    darkThemeEnabled: true,
    autoSyncEnabled: true,
    hapticsEnabled: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validRoomPayload(hostUid, overrides = {}) {
  const ts = pastDate(10000);
  return {
    hostId: hostUid,
    hostName: "Alice",
    title: "Friday Movie Night",
    sourceType: "YOUTUBE",
    mediaUrl: "jfKfPfyJRdk",
    mediaTitle: "Lofi Hip Hop Radio",
    playbackState: "PAUSED",
    positionMs: 0,
    playbackRate: 1.0,
    everyoneCanControl: false,
    roomLocked: false,
    status: "ACTIVE",
    memberUids: [hostUid],
    kickedUids: [],
    mutedUids: [],
    syncUpdatedAt: ts,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validParticipantPayload(uid, role = "HOST") {
  const ts = pastDate(8000);
  return {
    userId: uid,
    displayName: role === "HOST" ? "Alice" : "Bob",
    avatarColorHex: role === "HOST" ? "#FF4D6D" : "#2EC4B6",
    role,
    typing: false,
    micMuted: true,
    speaking: false,
    voiceJoined: false,
    joinedAt: ts,
    updatedAt: ts,
  };
}

function validChannelPayload(adminUid, overrides = {}) {
  const ts = pastDate(10000);
  return {
    name: "DD News",
    category: "NEWS",
    language: "Hindi",
    streamType: "YOUTUBE_LIVE",
    streamUrl: "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    broadcaster: "Prasar Bharati",
    badgeColorHex: "#FFB703",
    published: true,
    healthStatus: "ONLINE",
    createdBy: adminUid,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// --- 1. UNAUTHENTICATED DENIAL ---

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("rooms").doc("AB12CD").get());
  await assertFails(unauthDb.collection("rooms").doc("AB12CD").set(validRoomPayload(ALICE_UID)));
  await assertFails(unauthDb.collection("live_channels").where("published", "==", true).get());
});

// --- 2. USER PROFILE ISOLATION & SHADOW UPDATE ---

test("User profile: owner CRUD succeeds; cross-user and shadow fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Cinema",
      liveKitUrl: "wss://livekit.example.com",
      updatedAt: pastDate(1000),
    })
  );

  // Cross-user PII read/write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked" }));
  await assertFails(aliceDb.collection("users").get());

  // Shadow update (ghost field) denied
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );

  // Future timestamp denied
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Future",
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

// --- 3. ROOM LIFECYCLE, SYNC PERMISSIONS, LOCKING, KICKING & TERMINAL STATE ---

test("Room creation, guest join/leave, shared playback toggle, and terminal locking", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // Invalid room code rejected
  await assertFails(aliceDb.collection("rooms").doc("bad_code_123").set(validRoomPayload(ALICE_UID)));

  // Extra @DocumentId 'id' field rejected
  await assertFails(
    aliceDb.collection("rooms").doc("AB12CD").set({
      id: "AB12CD",
      ...validRoomPayload(ALICE_UID),
    })
  );

  // Host creates valid room
  await assertSucceeds(aliceDb.collection("rooms").doc("AB12CD").set(validRoomPayload(ALICE_UID)));

  // Filtered memberUids query succeeds; unfiltered list fails
  await assertSucceeds(
    aliceDb.collection("rooms").where("memberUids", "array-contains", ALICE_UID).get()
  );
  await assertFails(aliceDb.collection("rooms").get());

  // Bob joins unlocked room
  await assertSucceeds(
    bobDb.collection("rooms").doc("AB12CD").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(2000),
    })
  );

  // Bob cannot control playback while everyoneCanControl is false
  await assertFails(
    bobDb.collection("rooms").doc("AB12CD").update({
      playbackState: "PLAYING",
      positionMs: 15000,
      syncUpdatedAt: pastDate(1500),
      updatedAt: pastDate(1500),
    })
  );

  // Host enables everyoneCanControl and locks room
  await assertSucceeds(
    aliceDb.collection("rooms").doc("AB12CD").update({
      everyoneCanControl: true,
      roomLocked: true,
      updatedAt: pastDate(1200),
    })
  );

  // Charlie cannot join a locked room
  await assertFails(
    charlieDb.collection("rooms").doc("AB12CD").update({
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      updatedAt: pastDate(1100),
    })
  );

  // Bob CAN now play/seek when everyoneCanControl is true
  await assertSucceeds(
    bobDb.collection("rooms").doc("AB12CD").update({
      playbackState: "PLAYING",
      positionMs: 32000,
      syncUpdatedAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // Bob STILL cannot escalate privileges or lock/kick
  await assertFails(
    bobDb.collection("rooms").doc("AB12CD").update({
      roomLocked: false,
      updatedAt: pastDate(900),
    })
  );

  // Host ends room (terminal state)
  await assertSucceeds(
    aliceDb.collection("rooms").doc("AB12CD").update({
      status: "ENDED",
      updatedAt: pastDate(800),
    })
  );

  // Terminal state lock: subsequent updates by host or guest rejected
  await assertFails(
    aliceDb.collection("rooms").doc("AB12CD").update({
      title: "Reopened",
      updatedAt: pastDate(500),
    })
  );
});

// --- 4. PARTICIPANTS & CHAT MESSAGES (MUTED/KICKED ENFORCEMENT) ---

test("Participants and messages enforce room membership, role integrity, and mute controls", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  await assertSucceeds(aliceDb.collection("rooms").doc("XY89ZW").set(validRoomPayload(ALICE_UID)));
  await assertSucceeds(
    bobDb.collection("rooms").doc("XY89ZW").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(4000),
    })
  );

  // Host registers participant with HOST role; Bob cannot self-assign HOST role
  await assertSucceeds(
    aliceDb.collection("rooms").doc("XY89ZW").collection("participants").doc(ALICE_UID).set(
      validParticipantPayload(ALICE_UID, "HOST")
    )
  );
  await assertFails(
    bobDb.collection("rooms").doc("XY89ZW").collection("participants").doc(BOB_UID).set(
      validParticipantPayload(BOB_UID, "HOST")
    )
  );
  await assertSucceeds(
    bobDb.collection("rooms").doc("XY89ZW").collection("participants").doc(BOB_UID).set(
      validParticipantPayload(BOB_UID, "GUEST")
    )
  );

  // Member can read fellow participant doc via get; non-member Charlie cannot; list is disabled
  await assertSucceeds(
    aliceDb.collection("rooms").doc("XY89ZW").collection("participants").doc(BOB_UID).get()
  );
  await assertFails(
    charlieDb.collection("rooms").doc("XY89ZW").collection("participants").doc(BOB_UID).get()
  );
  await assertFails(
    aliceDb.collection("rooms").doc("XY89ZW").collection("participants").get()
  );

  // Bob sends chat message with denormalized memberUids
  await assertSucceeds(
    bobDb.collection("rooms").doc("XY89ZW").collection("messages").doc("msg_1").set({
      senderId: BOB_UID,
      senderName: "Bob",
      senderColorHex: "#2EC4B6",
      text: "This scene is awesome!",
      messageType: "TEXT",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: pastDate(3000),
    })
  );

  // Filtered message query succeeds for Alice & Bob; fails for Charlie and unfiltered
  await assertSucceeds(
    aliceDb.collection("rooms").doc("XY89ZW").collection("messages")
      .where("memberUids", "array-contains", ALICE_UID).get()
  );
  await assertFails(
    aliceDb.collection("rooms").doc("XY89ZW").collection("messages").get()
  );

  // Host mutes Bob -> Bob can no longer post messages
  await assertSucceeds(
    aliceDb.collection("rooms").doc("XY89ZW").update({
      mutedUids: [BOB_UID],
      updatedAt: pastDate(2000),
    })
  );
  await assertFails(
    bobDb.collection("rooms").doc("XY89ZW").collection("messages").doc("msg_2").set({
      senderId: BOB_UID,
      senderName: "Bob",
      senderColorHex: "#2EC4B6",
      text: "Trying while muted",
      messageType: "TEXT",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: pastDate(1000),
    })
  );
});

// --- 5. LIVE TV CATALOG & ADMIN EMAIL VERIFICATION ---

test("Live channels: signed-in users list published only; verified admin manages catalog; spoofed email rejected", async () => {
  const verifiedAdminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  const spoofedAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Unverified spoofed admin cannot create channel
  await assertFails(
    spoofedAdminDb.collection("live_channels").doc("dd_news").set(validChannelPayload("spoof_uid"))
  );

  // Regular user cannot create channel
  await assertFails(
    aliceDb.collection("live_channels").doc("dd_news").set(validChannelPayload(ALICE_UID))
  );

  // Verified admin creates published and hidden (offline) channels
  await assertSucceeds(
    verifiedAdminDb.collection("live_channels").doc("dd_news").set(validChannelPayload(ADMIN_UID))
  );
  await assertSucceeds(
    verifiedAdminDb.collection("live_channels").doc("dead_ch").set(
      validChannelPayload(ADMIN_UID, {
        name: "Offline Channel",
        published: false,
        healthStatus: "OFFLINE",
      })
    )
  );

  // Regular user can query published channels only; cannot read hidden or unfiltered list
  await assertSucceeds(
    aliceDb.collection("live_channels").where("published", "==", true).get()
  );
  await assertFails(aliceDb.collection("live_channels").get());
  await assertFails(aliceDb.collection("live_channels").doc("dead_ch").get());

  // Verified admin can list all channels and update healthStatus
  await assertSucceeds(verifiedAdminDb.collection("live_channels").get());
  await assertSucceeds(
    verifiedAdminDb.collection("live_channels").doc("dd_news").update({
      healthStatus: "DEGRADED",
      published: false,
      updatedAt: pastDate(1000),
    })
  );
});
