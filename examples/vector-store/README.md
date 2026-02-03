# Vector Store Example: Pinecone Integration

This example demonstrates how to integrate Pinecone as a vector store backend for SDLCraft's long-term memory.

## Overview

The `PineconeVectorStore` implements the `VectorStore` interface to:
- Store embeddings with metadata
- Perform semantic similarity search
- Delete vectors
- Handle API errors gracefully

## Files

- `PineconeVectorStore.java` - Pinecone implementation
- `VectorStoreConfig.java` - Configuration class
- `PineconeVectorStoreTest.java` - Unit tests
- `application.yml` - Configuration example

## Prerequisites

- Pinecone account (sign up at https://www.pinecone.io/)
- Create an index with dimension 1536 (for OpenAI embeddings)
- Set environment variables:
  ```bash
  export PINECONE_API_KEY=your-key-here
  export PINECONE_ENVIRONMENT=us-west1-gcp
  export PINECONE_INDEX=sdlcraft-memory
  ```

## Installation

1. Copy the files to your backend project:
   ```bash
   cp -r examples/vector-store/src/* backend/src/
   ```

2. Update `application.yml`:
   ```yaml
   sdlcraft:
     vector-store:
       provider: pinecone
       pinecone:
         api-key: ${PINECONE_API_KEY}
         environment: ${PINECONE_ENVIRONMENT}
         index: ${PINECONE_INDEX}
   ```

3. Rebuild and restart:
   ```bash
   cd backend
   mvn clean install
   mvn spring-boot:run
   ```

## Usage

The vector store is automatically used for semantic search:

```bash
sdlc what did we do for security last week?
# Uses Pinecone to find similar past commands
```

## Customization

- Adjust search filters: Modify the `filter` parameter in `search()`
- Add namespaces: Use Pinecone namespaces for multi-tenancy
- Implement batching: Batch multiple vectors for better performance
- Add caching: Cache frequently accessed vectors

## Other Providers

See also:
- `WeaviateVectorStore.java` - Weaviate integration
- `QdrantVectorStore.java` - Qdrant integration
- `ChromaVectorStore.java` - Chroma integration

## See Also

- [Developer Guide](../../docs/developer-guide.md)
- [Pinecone Documentation](https://docs.pinecone.io/)
