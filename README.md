# Chatt

A full-stack chatbot app with:

- **Backend:** Spring Boot (Java 17) + WebFlux
- **Frontend:** React + Vite
- **LLM provider:** OpenRouter API (streaming responses over SSE)

## Project structure

- `/src/main/java` — Spring Boot API
- `/src/main/resources/applicationyml.example` — backend config example
- `/front/chatbot` — React client

## Prerequisites

- Java 17+
- Node.js 18+ and npm
- An OpenRouter API key

## Backend setup (Spring Boot)

1. Create your runtime config from the example:
   - copy `/home/runner/work/chatt/chatt/src/main/resources/applicationyml.example`
   - create `/home/runner/work/chatt/chatt/src/main/resources/application.yml`
2. Update `openrouter.api-key` in `application.yml`.
3. Start backend from repository root:

```bash
./mvnw spring-boot:run
```

Backend runs on `http://localhost:8080`.

## Frontend setup (React)

From `/home/runner/work/chatt/chatt/front/chatbot`:

```bash
npm install
npm run dev
```

Frontend runs on `http://localhost:5173`.

## How chat streaming works

The frontend opens an `EventSource` connection to:

`GET /api/chat/stream?sessionId=<id>&message=<text>&model=<model>`

Main events:

- `chat` — streamed token chunks
- `done` — stream completed

## Default models in UI

- `openai/gpt-3.5-turbo`
- `mistralai/mixtral-8x7b-instruct`
- `meta-llama/llama-3-8b-instruct`
- `openrouter/auto`

## Notes

- CORS is currently configured for `http://localhost:5173`.
- Session history is stored in-memory in the backend (`ChatSessionService`).
