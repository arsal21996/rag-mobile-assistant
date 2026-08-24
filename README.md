# RAG Mobile Assistant

A full-stack document-grounded AI assistant: **Android + Jetpack Compose** frontend and **Python + FastAPI** backend.

The app retrieves relevant document chunks with embeddings and cosine similarity, applies a similarity threshold, and asks Gemini to answer from the retrieved context. Retrieved sources are returned to the phone so the UI can show where the answer came from.

## Architecture

```text
Android / Jetpack Compose
        |
        | HTTP JSON
        v
Python / FastAPI
        |
        +--> query embedding
        |       |
        |       v
        |   cosine similarity
        |       |
        |       v
        |   top-k chunks
        |       |
        |       v
        |   threshold
        |
        +--> grounded prompt --> Gemini generation
        |
        +--> sources + answer --> Android
```

## Backend setup

From `backend/`:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
Copy-Item .env.example .env
```

Put your Gemini key in `.env`:

```text
GEMINI_API_KEY=your-key
```

Generate the document embeddings once:

```powershell
python ingest.py
```

Start the API:

```powershell
python -m uvicorn app:app --reload --host 0.0.0.0 --port 8000
```

Health check: `http://localhost:8000/health`

Chat endpoint: `POST http://localhost:8000/chat`

## Android setup

Open the `android/` folder in Android Studio. The default emulator API base URL is:

```text
http://10.0.2.2:8000/
```

That maps the Android emulator's `10.0.2.2` to your Windows host's localhost.

For a physical phone, change `BASE_URL` in `android/app/src/main/java/com/arsal/ragmobile/data/RagApi.kt` to your computer's LAN IP, for example `http://192.168.1.10:8000/`.

Make sure Windows Firewall allows port 8000 on your private network.

## Current MVP

- Modern dark/light Material 3 chat UI
- Fresh RAG retrieval on every user turn
- Top-3 similarity scores
- Similarity threshold and grounded `I don't know` behavior
- Source cards under assistant responses
- Retry/backoff for temporary Gemini 429/5xx errors
- Cached document embeddings on disk
- Conversation history kept separate from retrieved context
- Gemini API key stays on the backend

## Why the backend owns Gemini

Never ship `GEMINI_API_KEY` inside the Android APK. The phone talks to our backend, and only the backend talks to Gemini. This keeps the secret server-side and gives us one place to control retrieval, prompts, rate limits, logging, and future authentication.

## Next upgrades

1. PDF/TXT document upload from Android
2. Incremental ingestion instead of rebuilding the whole index
3. Persistent vector database / FAISS for larger collections
4. Authentication and per-user document collections
5. Streaming assistant responses
6. Source expansion and document preview
7. Retrieval evaluation and threshold tuning
