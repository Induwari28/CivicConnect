# CivicConnect — Smart Municipal Civic Issue Reporting Platform

CivicConnect is a full-stack civic engagement and municipal issue tracking platform designed for the Moratuwa Municipal Council region. It empowers citizens to report local infrastructure issues (roads, streetlights, sanitation, hazards) with precise GPS coordinates and photos, while leveraging **PostGIS spatial clustering**, **Google Gemini AI priority triage**, and **crowdsourced confirmation escalation** to help municipal authorities dispatch crews effectively.

---

## Key Features

- **Geospatial Reporting & PostGIS Duplicate Clustering:**
  - Pinned GPS location detection via HTML5 Geolocation or direct map-pinning.
  - Automatically identifies existing reports within a 50-meter radius using PostGIS spatial geometry (`ST_DWithin`) to prevent duplicate work orders while incrementing community confirmation counts.
- **AI-Powered Priority Triage (Gemini AI):**
  - Analyzes issue titles and hazard descriptions upon submission to categorize urgency (`HIGH`, `MEDIUM`, `LOW`, or `UNCLASSIFIED`).
- **Crowdsourced Priority Escalation ("Me Too / Confirm"):**
  - Residents can confirm existing community issues. Reaching 3 or more confirmations automatically boosts priority to `HIGH`.
- **Interactive Spatial Map (Leaflet):**
  - Real-time Moratuwa spatial grid with 50m cluster buffers, quick zone jumping (Katubedda, Molpe, Soysapura), and dynamic priority color-coded markers.
  - Reverse geocoding via OpenStreetMap Nominatim with local client-side caching.
- **Citizen Report Tracker:**
  - Device-level issue tracking using `localStorage` and report IDs to observe real-time municipal triage stages (`Reported` → `Crew Dispatched` → `Resolved`).
- **Administrative Lifecycle & Issue Management:**
  - Basic Auth protected status updates (`PENDING`, `IN_PROGRESS`, `RESOLVED`).
  - Full CRUD capabilities: edit title/description and remove resolved/erroneous reports.

---

## Tech Stack

### Backend
- **Framework:** Spring Boot (Java 25, Spring Boot 4.x / 3.x)
- **Database & Spatial Engine:** PostgreSQL 15+ with PostGIS extension & Hibernate Spatial
- **AI Integration:** Google Gemini API (gemini-1.5-flash / generative AI REST API)
- **Security:** Spring Security (HTTP Basic Auth for Administrative endpoints, CORS configuration)
- **Storage:** Local multipart image file storage (`/uploads/**`)

### Frontend
- **Framework:** Vue 3 (Composition API `<script setup lang="ts">`) + TypeScript
- **Build Tool:** Vite
- **Styling:** Tailwind CSS
- **Mapping:** Leaflet & `@types/leaflet`
- **Routing & Networking:** Vue Router, Axios

---

## Architecture Overview

```text
CivicConnect/
├── backend/
│   ├── src/main/java/com/civicconnect/backend/
│   │   ├── config/          # Security & CORS configuration
│   │   ├── controller/      # REST API endpoints (IssueController, Admin)
│   │   ├── dto/             # Request & Response records
│   │   ├── model/           # Issue entity with JTS Point geometry & enums
│   │   ├── repository/      # Spring Data JPA + PostGIS native spatial queries
│   │   └── service/         # GeminiService, IssueImageStorageService
│   └── src/main/resources/
│       └── application.properties
└── frontend/
    ├── src/
    │   ├── assets/          # Tailwind & Leaflet styles
    │   ├── types/           # Issue, Status, and Priority TypeScript interfaces
    │   ├── router/          # Vue Router configurations (/feed, /map, /my-reports)
    │   └── views/           # IssueFeedView, SmartMapView, MyReportsView
    └── package.json
