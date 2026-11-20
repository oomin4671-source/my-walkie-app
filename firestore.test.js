const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_101";
const BOB_UID = "bob_uid_202";
const CHARLIE_UID = "charlie_uid_303";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated users are denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("public_profiles").where("discoverable", "==", true).get());
  await assertFails(unauthDb.collection("rooms").where("memberUids", "array-contains", ALICE_UID).get());
  await assertFails(unauthDb.collection("room_invites").doc("WK4829").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      hapticsEnabled: true,
      autoJoinAudio: true,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Private user profile (PII) is strictly owner-isolated", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      hapticsEnabled: true,
      autoJoinAudio: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Partial update by owner succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      backendUrl: "https://walkie.example.com",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user read & write fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ hapticsEnabled: false, updatedAt: now }));

  // Immutable createdAt mutation fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Public profiles support owner write and filtered discoverable list query", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Vega",
      callsign: "ALPHA-1",
      statusMessage: "Standing by on channel",
      onlineStatus: "ONLINE",
      discoverable: true,
      lastSeenAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Filtered query matching Kotlin repository succeeds
  await assertSucceeds(
    bobDb.collection("public_profiles").where("discoverable", "==", true).get()
  );

  // Unfiltered list query fails
  await assertFails(bobDb.collection("public_profiles").get());

  // Bob cannot overwrite Alice's public profile
  await assertFails(
    bobDb.collection("public_profiles").doc(ALICE_UID).update({
      onlineStatus: "OFFLINE",
      updatedAt: now,
    })
  );
});

test("Group room + invite atomic batch creation, join by code, and member-scoped list query", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  // Non-atomic group room creation without room_invites must fail
  await assertFails(
    aliceDb.collection("rooms").doc("room_no_invite").set({
      name: "Squad Alpha",
      roomType: "GROUP",
      joinCode: "WK9999",
      livekitRoomName: "room_no_invite",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      maxParticipants: 20,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Atomic batch write creating room + room_invites succeeds
  const batch = aliceDb.batch();
  batch.set(aliceDb.collection("rooms").doc("room_alpha"), {
    name: "Squad Alpha",
    description: "Field coordination",
    roomType: "GROUP",
    joinCode: "WK4829",
    livekitRoomName: "lk_room_alpha",
    ownerId: ALICE_UID,
    memberUids: [ALICE_UID],
    maxParticipants: 20,
    createdAt: now,
    updatedAt: now,
  });
  batch.set(aliceDb.collection("room_invites").doc("WK4829"), {
    roomId: "room_alpha",
    roomName: "Squad Alpha",
    ownerId: ALICE_UID,
    createdAt: now,
  });
  await assertSucceeds(batch.commit());

  // Listing all room_invites is blocked (must know code)
  await assertFails(bobDb.collection("room_invites").get());

  // Bob looks up invite by exact code and joins room
  await assertSucceeds(bobDb.collection("room_invites").doc("WK4829").get());
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_alpha").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Member-scoped query succeeds for Bob
  await assertSucceeds(
    bobDb.collection("rooms").where("memberUids", "array-contains", BOB_UID).get()
  );

  // Unfiltered rooms query fails
  await assertFails(bobDb.collection("rooms").get());

  // Non-member Charlie cannot read room directly
  await assertFails(charlieDb.collection("rooms").doc("room_alpha").get());

  // Shadow update with extra ghost field fails
  await assertFails(
    aliceDb.collection("rooms").doc("room_alpha").update({
      name: "Hijacked",
      isVerified: true,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Writing @DocumentId `id` inside room body fails
  await assertFails(
    aliceDb.collection("rooms").doc("room_bad_id").set({
      id: "room_bad_id",
      name: "Bad Room",
      roomType: "PRIVATE",
      joinCode: "DM1234",
      livekitRoomName: "lk_bad",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      maxParticipants: 2,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Private 1-on-1 room allows two members and blocks third user join", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("rooms").doc("dm_alice_bob").set({
      name: "Direct: Alice & Bob",
      roomType: "PRIVATE",
      joinCode: "DM101202",
      livekitRoomName: "dm_alice_bob",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      maxParticipants: 2,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can read and update floor speaker metadata
  await assertSucceeds(bobDb.collection("rooms").doc("dm_alice_bob").get());
  await assertSucceeds(
    bobDb.collection("rooms").doc("dm_alice_bob").update({
      activeSpeakerId: BOB_UID,
      activeSpeakerName: "Bob",
      floorUpdatedAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Charlie cannot join a PRIVATE room
  await assertFails(
    charlieDb.collection("rooms").doc("dm_alice_bob").update({
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      updatedAt: new Date(Date.now() - 200),
    })
  );
});
