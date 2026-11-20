const crypto = require("crypto");
const express = require("express");
const admin = require("firebase-admin");
const { AccessToken, RoomServiceClient, DataPacket_Kind } = require("livekit-server-sdk");

const app = express();
app.use(express.json());

// Required environment variables (configured in Render Dashboard -> Environment):
// - LIVEKIT_URL (e.g. wss://your-project.livekit.cloud)
// - LIVEKIT_API_KEY
// - LIVEKIT_API_SECRET
// - FIREBASE_PROJECT_ID (gen-lang-client-0055622074)
const PORT = process.env.PORT || 8080;
const LIVEKIT_URL = (process.env.LIVEKIT_URL || "").trim();
const LIVEKIT_API_KEY = (process.env.LIVEKIT_API_KEY || "").trim();
const LIVEKIT_API_SECRET = (process.env.LIVEKIT_API_SECRET || "").trim();
const FIREBASE_PROJECT_ID = (process.env.FIREBASE_PROJECT_ID || "").trim();
const FLOOR_TTL_MS = 30_000;

if (!admin.apps.length) {
  const serviceAccountRaw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (serviceAccountRaw) {
    const serviceAccount = JSON.parse(serviceAccountRaw);
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
      projectId: FIREBASE_PROJECT_ID || serviceAccount.project_id,
    });
  } else {
    admin.initializeApp({
      projectId: FIREBASE_PROJECT_ID || undefined,
    });
  }
}

const livekitHttpUrl = LIVEKIT_URL.replace(/^wss:/, "https:").replace(/^ws:/, "http:");
const roomService =
  LIVEKIT_URL && LIVEKIT_API_KEY && LIVEKIT_API_SECRET
    ? new RoomServiceClient(livekitHttpUrl, LIVEKIT_API_KEY, LIVEKIT_API_SECRET)
    : null;

// Authoritative per-room floor lock store
// roomId -> { speakerUid, speakerName, livekitRoomName, leaseToken, leaseExpiresAtMs }
const roomFloors = new Map();

function getActiveFloor(roomId) {
  const floor = roomFloors.get(roomId);
  if (!floor) return null;
  if (Date.now() > floor.leaseExpiresAtMs) {
    roomFloors.delete(roomId);
    return null;
  }
  return floor;
}

async function authenticateFirebaseUser(req, res, next) {
  try {
    const header = req.headers.authorization || "";
    const match = header.match(/^Bearer\s+(.+)$/i);
    if (!match) {
      return res.status(401).json({ error: "Missing Authorization Bearer token" });
    }
    const decoded = await admin.auth().verifyIdToken(match[1]);
    req.user = {
      uid: decoded.uid,
      name: decoded.name || decoded.email || decoded.uid,
    };
    return next();
  } catch (err) {
    return res.status(401).json({ error: "Invalid Firebase ID token" });
  }
}

async function broadcastFloorState(livekitRoomName, speakerUid, speakerName, active) {
  if (!roomService || !livekitRoomName) return;
  try {
    const payload = Buffer.from(
      JSON.stringify({
        active,
        speakerUid: speakerUid || null,
        speakerName: speakerName || null,
      }),
      "utf8"
    );
    await roomService.sendData(livekitRoomName, payload, DataPacket_Kind.RELIABLE, {
      topic: "walkie_floor",
    });
  } catch (_) {
    // Room may not have active participants yet
  }
}

// Normalize accidental double `/api/api/...` prefixes if a client enters `/api` in base URL
app.use((req, _res, next) => {
  if (req.url.startsWith("/api/api/")) {
    req.url = req.url.replace(/^\/api\/api\//, "/api/");
  }
  next();
});

function buildHealthPayload() {
  return {
    ok: true,
    service: "my-walkie-backend",
    status: "running",
    livekitConfigured: Boolean(LIVEKIT_URL && LIVEKIT_API_KEY && LIVEKIT_API_SECRET),
    firebaseProjectConfigured: Boolean(FIREBASE_PROJECT_ID || process.env.FIREBASE_SERVICE_ACCOUNT_JSON),
    endpoints: [
      "GET /health",
      "GET /healthz",
      "POST /api/livekit/token",
      "POST /api/floor/acquire",
      "POST /api/floor/heartbeat",
      "POST /api/floor/release",
      "GET /api/floor/status",
    ],
  };
}

app.get(["/", "/health", "/healthz", "/api/health"], (_req, res) => {
  res.json(buildHealthPayload());
});

// 1. Issue short-lived, server-authorized LiveKit Access Token
app.post(["/api/livekit/token", "/livekit/token"], authenticateFirebaseUser, async (req, res) => {
  try {
    if (!LIVEKIT_URL || !LIVEKIT_API_KEY || !LIVEKIT_API_SECRET) {
      return res.status(500).json({ error: "LiveKit server credentials not configured on backend." });
    }

    const { roomId, livekitRoomName, participantName } = req.body || {};
    if (!roomId || !livekitRoomName) {
      return res.status(400).json({ error: "roomId and livekitRoomName are required." });
    }

    const at = new AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET, {
      identity: req.user.uid,
      name: String(participantName || req.user.name).slice(0, 50),
      ttl: "2h",
    });

    at.addGrant({
      roomJoin: true,
      room: String(livekitRoomName),
      canSubscribe: true,
      canPublish: true,
      canPublishData: true,
    });

    const token = await at.toJwt();
    return res.json({
      token,
      serverUrl: LIVEKIT_URL,
      identity: req.user.uid,
    });
  } catch (err) {
    console.error("Error generating LiveKit token:", err);
    return res.status(500).json({ error: "Failed to generate LiveKit access token." });
  }
});

// 2. Acquire exclusive Push-to-Talk floor lock for a room
app.post(["/api/floor/acquire", "/floor/acquire"], authenticateFirebaseUser, async (req, res) => {
  const { roomId, livekitRoomName, participantName } = req.body || {};
  if (!roomId) {
    return res.status(400).json({ error: "roomId is required." });
  }

  const existing = getActiveFloor(roomId);
  if (existing && existing.speakerUid !== req.user.uid) {
    return res.json({
      granted: false,
      speakerUid: existing.speakerUid,
      speakerName: existing.speakerName,
      leaseExpiresAtMs: existing.leaseExpiresAtMs,
      message: `${existing.speakerName} currently holds the floor.`,
    });
  }

  const leaseToken = crypto.randomBytes(16).toString("hex");
  const leaseExpiresAtMs = Date.now() + FLOOR_TTL_MS;
  const speakerName = String(participantName || req.user.name).slice(0, 50);

  roomFloors.set(roomId, {
    speakerUid: req.user.uid,
    speakerName,
    livekitRoomName: livekitRoomName || roomId,
    leaseToken,
    leaseExpiresAtMs,
  });

  await broadcastFloorState(livekitRoomName || roomId, req.user.uid, speakerName, true);

  return res.json({
    granted: true,
    speakerUid: req.user.uid,
    speakerName,
    leaseToken,
    leaseExpiresAtMs,
  });
});

// 3. Renew active PTT lease while button is held
app.post(["/api/floor/heartbeat", "/floor/heartbeat"], authenticateFirebaseUser, (req, res) => {
  const { roomId, leaseToken } = req.body || {};
  if (!roomId) {
    return res.status(400).json({ error: "roomId is required." });
  }

  const existing = getActiveFloor(roomId);
  if (!existing || existing.speakerUid !== req.user.uid || existing.leaseToken !== leaseToken) {
    return res.json({
      granted: false,
      speakerUid: existing ? existing.speakerUid : null,
      speakerName: existing ? existing.speakerName : null,
      leaseExpiresAtMs: existing ? existing.leaseExpiresAtMs : 0,
      message: "Floor lease expired or held by another participant.",
    });
  }

  existing.leaseExpiresAtMs = Date.now() + FLOOR_TTL_MS;
  return res.json({
    granted: true,
    speakerUid: existing.speakerUid,
    speakerName: existing.speakerName,
    leaseToken: existing.leaseToken,
    leaseExpiresAtMs: existing.leaseExpiresAtMs,
  });
});

// 4. Release PTT floor immediately when button is released
app.post(["/api/floor/release", "/floor/release"], authenticateFirebaseUser, async (req, res) => {
  const { roomId, leaseToken } = req.body || {};
  if (!roomId) {
    return res.status(400).json({ error: "roomId is required." });
  }

  const existing = getActiveFloor(roomId);
  if (existing && existing.speakerUid === req.user.uid && (!leaseToken || existing.leaseToken === leaseToken)) {
    const lkRoom = existing.livekitRoomName;
    roomFloors.delete(roomId);
    await broadcastFloorState(lkRoom, null, null, false);
  }
  return res.json({
    granted: false,
    speakerUid: null,
    speakerName: null,
    leaseExpiresAtMs: 0,
  });
});

// 5. Inspect current floor owner
app.get(["/api/floor/status", "/floor/status"], authenticateFirebaseUser, (req, res) => {
  const roomId = String(req.query.roomId || "").trim();
  if (!roomId) {
    return res.status(400).json({ error: "roomId query parameter is required." });
  }

  const existing = getActiveFloor(roomId);
  return res.json({
    granted: Boolean(existing && existing.speakerUid === req.user.uid),
    speakerUid: existing ? existing.speakerUid : null,
    speakerName: existing ? existing.speakerName : null,
    leaseExpiresAtMs: existing ? existing.leaseExpiresAtMs : 0,
  });
});

// JSON 404 handler so Android client receives exact failing route diagnostics
app.use((req, res) => {
  res.status(404).json({
    error: `Endpoint not found: ${req.method} ${req.originalUrl}`,
    method: req.method,
    path: req.originalUrl,
  });
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`MY WALKIE backend listening on port ${PORT}`);
});
