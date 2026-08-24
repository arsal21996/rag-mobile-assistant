from pathlib import Path
from typing import Literal

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from rag_core import (
    EMBEDDINGS_FILE,
    SIMILARITY_THRESHOLD,
    grounded_answer,
    load_embeddings,
    retrieve,
)

app = FastAPI(title="RAG Mobile Assistant API", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


class Message(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=8000)


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=8000)
    history: list[Message] = Field(default_factory=list)


class Source(BaseModel):
    source: str
    chunk_id: int
    score: float
    text: str


class ChatResponse(BaseModel):
    answer: str
    grounded: bool
    threshold: float
    best_score: float
    sources: list[Source]


@app.get("/health")
def health():
    return {
        "status": "ok",
        "embeddings_ready": EMBEDDINGS_FILE.exists(),
        "threshold": SIMILARITY_THRESHOLD,
    }


@app.post("/chat", response_model=ChatResponse)
def chat(request: ChatRequest):
    chunks = load_embeddings()
    results = retrieve(request.message, chunks)
    answer, grounded = grounded_answer(
        request.message,
        results,
        [message.model_dump() for message in request.history],
    )
    best_score = results[0]["score"] if results else 0.0

    return ChatResponse(
        answer=answer,
        grounded=grounded,
        threshold=SIMILARITY_THRESHOLD,
        best_score=best_score,
        sources=[Source(**item) for item in results],
    )
