# MY WALKIE Backend Service (Render / Cloud Run)

This service keeps your **LiveKit API Secret** off the Android device, verifies **Firebase Authentication ID Tokens**, issues short-lived **LiveKit Room Access Tokens**, and enforces **single-speaker Push-to-Talk floor control** per room.

## Required Environment Variables

- `LIVEKIT_URL`: Your LiveKit WebSocket URL (e.g., `wss://my-walkie-xxxx.livekit.cloud`)
- `LIVEKIT_API_KEY`: Your LiveKit API Key
- `LIVEKIT_API_SECRET`: Your LiveKit API Secret
- `FIREBASE_PROJECT_ID`: `gen-lang-client-0055622074`

## Deploy on Render

1. Push this repository to GitHub (from the AI Studio Settings / GitHub menu).
2. In Render (`https://dashboard.render.com`), choose **New + -> Web Service** (or **Blueprint** using `/render.yaml`).
3. Set **Root Directory** to `backend`, **Build Command** to `npm install`, and **Start Command** to `npm start`.
4. Add the 4 environment variables above in **Environment**.
5. Verify `https://<your-service>.onrender.com/health` opens in your browser with `"ok": true`.
6. Copy ONLY the base URL (`https://<your-service>.onrender.com`, without `/health` or `/api`) and paste it inside the **MY WALKIE** Android app under **Profile -> Voice Backend & Audio -> Token & Floor Backend URL**, then tap **Test Connection**.

## API Endpoints

- `GET /health` (and `/healthz`): Public health check + LiveKit/Firebase config status
- `POST /api/livekit/token`: Issues server-signed LiveKit access token
- `POST /api/floor/acquire`: Acquires exclusive room PTT floor lock
- `POST /api/floor/heartbeat`: Renews active PTT floor lease
- `POST /api/floor/release`: Releases room PTT floor lock
- `GET /api/floor/status`: Reads current room speaker state
