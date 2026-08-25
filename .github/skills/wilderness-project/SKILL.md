---
name: wilderness-project
description: Use this skill when working on the Wilderness astronomy website project; it captures the product vision, repo structure, technical stack, current status, and the expected development direction.
---

# Wilderness Project

The project is a bilingual astronomy education website called "宇宙是旷野" (Wilderness of the Universe). The product currently focuses on a showcase-driven front-end experience with clear future expansion toward search, collections, detail pages, AI explanations, and public API access.

## Product direction

- Primary positioning: astronomy knowledge for a general audience
- Language: Chinese + English bilingual presentation
- Content focus: stars, planets, moons, galaxies, nebulae, comets, asteroids, and other celestial objects
- Audience expectation: lightweight, approachable, visually attractive educational content
- Growth path: static showcase -> category browsing -> content detail -> search + favorites -> AI narration -> public API

## Tech stack

- Frontend: Vue 3 + Vite
- Backend: Spring Boot 3 + Java 17
- Build tooling: Maven Wrapper for backend; npm for frontend
- Runtime status: frontend runs on localhost:5173; backend runs on localhost:8080 by default

## Repo structure

- `frontend/`: Vue app and UI assets
- `backend/`: Spring Boot API and service layer
- `README.md`: product overview and startup guidance
- `.gitignore`: excludes generated files such as node_modules, dist, and target folders

## Current project status

- The homepage and initial bilingual site structure already exist
- A backend health endpoint has been implemented and responds successfully
- The project is still in the early showcase stage, not yet a full-featured product
- The main architectural goal is to leave room for future expansion without premature overengineering

## Design principles

- Keep the experience accessible and visually polished
- Present content bilingually without sacrificing clarity
- Separate UI, domain content, and API concerns cleanly
- Keep future features in mind: search, collection, AI explanation, and API exposure
- Prefer content structure that can evolve into CMS-like or data-driven architecture later

## Typical workflow

- Frontend development: `cd frontend && npm install && npm run dev`
- Backend development: `cd backend && ./mvnw spring-boot:run`
- Use `http://localhost:5173` for the UI and `http://localhost:8080/api/health` for backend validation
- Keep new features aligned with the bilingual, educational, showcase-first direction

## What to avoid

- Do not turn the project into a heavy enterprise app too early
- Do not hardcode everything into UI components without a future data model in mind
- Do not lose the bilingual content/UX consistency while adding features
- Do not add AI or API complexity before the core content model is stable

## Recommended next phases

1. Refine the homepage and landing experience
2. Build category pages and topic browsing
3. Add object detail pages with bilingual content
4. Design domain data model for stars, planets, and other celestial objects
5. Prepare backend endpoints for future search and collection features
6. Add AI-based educational explanation layer and public API exposure

## Useful context for future sessions

This project is being built as a lightweight but scalable astronomy learning site. The current focus is clarity, aesthetic presentation, and a strong bilingual content foundation. Future sessions should prioritize maintaining that educational tone while expanding the information architecture gradually.
