from rag_core import build_chunks, save_embeddings


if __name__ == "__main__":
    chunks = build_chunks()
    save_embeddings(chunks)
    print(f"Indexed {len(chunks)} chunks into data/embeddings.json")
