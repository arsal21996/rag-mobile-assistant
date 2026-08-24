import json
import os
import time
import urllib.error
import urllib.request
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent
DOCS_DIR = ROOT / "docs"
EMBEDDINGS_FILE = ROOT / "data" / "embeddings.json"

GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta"
EMBEDDING_MODEL = "gemini-embedding-001"
CHAT_MODEL = os.getenv("GEMINI_CHAT_MODEL", "gemini-2.5-flash")
TOP_K = int(os.getenv("TOP_K", "3"))
SIMILARITY_THRESHOLD = float(os.getenv("SIMILARITY_THRESHOLD", "0.35"))
CHUNK_WORDS = int(os.getenv("CHUNK_WORDS", "80"))
MAX_RETRIES = 4

RETRYABLE_STATUS_CODES = {429, 500, 502, 503, 504}


def load_env_file():
    env_path = ROOT / ".env"
    if not env_path.exists():
        return
    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip().strip('"').strip("'"))


load_env_file()


def gemini_request(path: str, payload: dict) -> dict:
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        raise RuntimeError("GEMINI_API_KEY is not set. Add it to backend/.env")

    for attempt in range(MAX_RETRIES + 1):
        request = urllib.request.Request(
            f"{GEMINI_API_BASE}/{path}",
            data=json.dumps(payload).encode("utf-8"),
            headers={
                "x-goog-api-key": api_key,
                "Content-Type": "application/json",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            body = exc.read().decode("utf-8", errors="replace")
            if exc.code not in RETRYABLE_STATUS_CODES or attempt == MAX_RETRIES:
                raise RuntimeError(f"Gemini API error {exc.code}: {body}") from exc
            delay = 2 ** attempt
            print(f"Gemini temporarily unavailable (HTTP {exc.code}). Retrying in {delay}s...")
            time.sleep(delay)
        except urllib.error.URLError as exc:
            if attempt == MAX_RETRIES:
                raise RuntimeError(f"Network error: {exc.reason}") from exc
            delay = 2 ** attempt
            print(f"Network error. Retrying in {delay}s...")
            time.sleep(delay)

    raise RuntimeError("Gemini request failed after retries")


def embed_texts(texts: list[str]) -> list[list[float]]:
    if not texts:
        return []
    response = gemini_request(
        f"models/{EMBEDDING_MODEL}:batchEmbedContents",
        {
            "requests": [
                {
                    "model": f"models/{EMBEDDING_MODEL}",
                    "content": {"parts": [{"text": text}]},
                }
                for text in texts
            ]
        },
    )
    return [item["values"] for item in response["embeddings"]]


def embed_query(text: str) -> list[float]:
    return embed_texts([text])[0]


def generate(prompt: str) -> str:
    response = gemini_request(
        f"models/{CHAT_MODEL}:generateContent",
        {
            "contents": [{"role": "user", "parts": [{"text": prompt}]}],
            "generationConfig": {"temperature": 0.2},
        },
    )
    candidates = response.get("candidates", [])
    if not candidates:
        return ""
    return "\n".join(
        part.get("text", "")
        for part in candidates[0].get("content", {}).get("parts", [])
    ).strip()


def chunk_text(text: str) -> list[str]:
    words = text.split()
    return [
        " ".join(words[i : i + CHUNK_WORDS])
        for i in range(0, len(words), CHUNK_WORDS)
    ]


def build_chunks() -> list[dict]:
    chunks = []
    for path in sorted(DOCS_DIR.glob("*.txt")):
        text = path.read_text(encoding="utf-8")
        for chunk_id, chunk in enumerate(chunk_text(text)):
            chunks.append({"source": path.name, "chunk_id": chunk_id, "text": chunk})

    embeddings = embed_texts([item["text"] for item in chunks])
    for item, embedding in zip(chunks, embeddings):
        item["embedding"] = embedding
    return chunks


def save_embeddings(chunks: list[dict]) -> None:
    EMBEDDINGS_FILE.parent.mkdir(parents=True, exist_ok=True)
    EMBEDDINGS_FILE.write_text(json.dumps(chunks, indent=2), encoding="utf-8")


def load_embeddings() -> list[dict]:
    if not EMBEDDINGS_FILE.exists():
        raise RuntimeError("embeddings.json is missing. Run: python ingest.py")
    return json.loads(EMBEDDINGS_FILE.read_text(encoding="utf-8"))


def cosine_similarity(a: list[float], b: list[float]) -> float:
    va = np.asarray(a, dtype=np.float32)
    vb = np.asarray(b, dtype=np.float32)
    denominator = np.linalg.norm(va) * np.linalg.norm(vb)
    if denominator == 0:
        return 0.0
    return float(np.dot(va, vb) / denominator)


def retrieve(query: str, chunks: list[dict], top_k: int = TOP_K) -> list[dict]:
    query_embedding = embed_query(query)
    scored = []
    for chunk in chunks:
        scored.append(
            {
                "source": chunk["source"],
                "chunk_id": chunk["chunk_id"],
                "text": chunk["text"],
                "score": cosine_similarity(query_embedding, chunk["embedding"]),
            }
        )
    scored.sort(key=lambda item: item["score"], reverse=True)
    return scored[:top_k]


def grounded_answer(query: str, results: list[dict], history: list[dict] | None = None) -> tuple[str, bool]:
    if not results or results[0]["score"] < SIMILARITY_THRESHOLD:
        return "I don't know based on the provided documents.", False

    context = "\n\n".join(
        f"[Source: {item['source']}, chunk {item['chunk_id']}]\n{item['text']}"
        for item in results
    )
    history_text = "\n".join(
        f"{item['role'].upper()}: {item['content']}"
        for item in (history or [])[-6:]
    ) or "(none)"

    prompt = f"""You are a grounded document assistant.
Use ONLY the supplied document context as factual evidence.
If the context does not contain enough information to answer, say exactly:
I don't know based on the provided documents.
Do not invent facts or use outside knowledge.
Conversation history is only for understanding references; it is NOT a source of factual evidence.

CONVERSATION HISTORY:
{history_text}

FRESH RETRIEVED CONTEXT:
{context}

CURRENT QUESTION:
{query}
"""
    return generate(prompt), True
