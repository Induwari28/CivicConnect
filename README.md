
# 🏙️ CivicConnect — Smart Municipal Civic Issue Reporting Platform

**Making cities smarter through citizen participation, geospatial intelligence, and AI-powered municipal issue management.**

CivicConnect is a full-stack civic engagement platform designed for the **Moratuwa Municipal Council region, Sri Lanka**. It connects citizens with municipal authorities through a centralized platform where residents can report infrastructure problems, track issue progress, and confirm existing community reports.

By combining **PostGIS spatial intelligence, Google Gemini AI, and crowdsourced prioritization**, CivicConnect helps identify duplicate reports, assess issue urgency, and provide authorities with better insights for managing local infrastructure problems.

---

## ✨ Key Features

### 📝 1. Citizen Issue Reporting
- Report local infrastructure issues, including damaged roads, drainage problems, broken streetlights, waste accumulation, and public hazards.
- Add issue titles and detailed descriptions.
- Capture precise GPS coordinates using HTML5 Geolocation.
- Select report locations directly on the interactive map.
- Upload images to provide visual evidence of reported problems.

### 🤖 2. AI-Powered Priority Classification
- Integrates the **Google Gemini API** to analyze submitted issue titles and descriptions.
- Automatically classifies issues into `HIGH`, `MEDIUM`, `LOW`, or `UNCLASSIFIED` priority levels.
- Helps municipal authorities identify urgent community problems more efficiently.

### 📍 3. PostGIS Spatial Duplicate Detection
- Uses PostgreSQL with the PostGIS extension and Hibernate Spatial.
- Identifies existing unresolved reports within a **50-meter geographical radius** using `ST_DWithin`.
- Groups nearby reports instead of creating unnecessary duplicate work orders.
- Increments the existing report's community confirmation count when a matching issue is identified.

### 👥 4. Crowdsourced Priority Escalation
- Citizens can confirm existing reports using the **"Me Too / Confirm"** functionality.
- Confirmation counts reflect community interest in reported problems.
- Automatically escalates an issue to `HIGH` priority when it receives three or more confirmations.

### 🗺️ 5. Interactive Geospatial Map
- Visualizes reported civic issues across the Moratuwa region using **Leaflet and OpenStreetMap**.
- Displays location-based issue markers with priority-based color coding.
- Supports geographical clustering and 50-meter spatial proximity detection.
- Provides quick navigation to areas such as Katubedda, Molpe, and Soysapura.
- Uses OpenStreetMap Nominatim for reverse geocoding.
- Implements client-side caching to reduce repeated geocoding requests.

### 📋 6. Citizen Report Tracking
- Allows citizens to save reports they want to follow.
- Uses browser `localStorage` and report IDs to maintain device-level tracking.
- Displays issue progress through municipal lifecycle stages:
  - `Reported`
  - `Crew Dispatched`
  - `Resolved`

### 🏛️ 7. Administrative Issue Management
- Provides an administrative dashboard for municipal authorities.
- Protects administrative operations using HTTP Basic Authentication.
- Allows authorized administrators to review and manage reported issues.
- Supports issue status transitions:
  - `PENDING`
  - `IN_PROGRESS`
  - `RESOLVED`
- Supports editing issue details and removing resolved or erroneous reports where the corresponding backend operations are available.

### 📊 8. Municipal Analytics
- Provides an overview of reported civic issues.
- Displays issue statistics, including total reports, priority distribution, and resolution progress.
- Helps identify frequently reported problem areas.
- Supports CSV export for further analysis.

### 🏢 9. Municipal Services Directory
- Provides a centralized directory of municipal service departments.
- Covers Roads and Drainage, Electrical and Street Lighting, Solid Waste and Sanitation, and Public Health and Environment.

---

## 🛠️ Technology Stack

### Backend

| Technology | Purpose |
|---|---|
| Java 25 | Backend programming language |
| Spring Boot 4.x | REST API and application framework |
| Spring Data JPA | Database persistence |
| Hibernate ORM | Object-relational mapping |
| Hibernate Spatial | Geospatial data integration |
| PostgreSQL 15+ | Relational database |
| PostGIS | Spatial queries and geographical operations |
| Google Gemini API | AI-powered priority classification |
| Spring Security | Administrative authentication |
| Maven | Dependency and build management |

### Frontend

| Technology | Purpose |
|---|---|
| Vue 3 | Frontend framework |
| TypeScript | Type-safe development |
| Composition API | Component logic using `<script setup>` |
| Vite | Development server and build tool |
| Tailwind CSS | Responsive UI styling |
| Vue Router | Client-side navigation |
| Axios | HTTP communication with backend APIs |
| Leaflet | Interactive maps |
| OpenStreetMap | Map tiles and geographical data |

### Infrastructure & Storage

- Local multipart file storage for uploaded issue images.
- RESTful API communication between frontend and backend.
- PostgreSQL database with spatial extensions.
- Flyway database migration management.

---

## 🏗️ System Architecture

```text
                 ┌──────────────────────────┐
                 │        CITIZENS          │
                 │  Report / Confirm / Track│
                 └────────────┬─────────────┘
                              │
                              ▼
                 ┌──────────────────────────┐
                 │      VUE 3 FRONTEND      │
                 │                          │
                 │  Issue Feed              │
                 │  Smart Map               │
                 │  My Reports              │
                 │  Admin Dashboard         │
                 │  Analytics               │
                 └────────────┬─────────────┘
                              │
                         REST API
                              │
                              ▼
                 ┌──────────────────────────┐
                 │    SPRING BOOT BACKEND   │
                 │                          │
                 │  REST Controllers        │
                 │  Business Services       │
                 │  Spring Security         │
                 │  JPA Repositories        │
                 └───────┬──────────┬───────┘
                         │          │
              ┌──────────▼───┐  ┌───▼────────────┐
              │  PostgreSQL  │  │  Gemini AI    │
              │  + PostGIS   │  │  Priority     │
              │              │  │  Classification│
              └──────────────┘  └────────────────┘
```

---

## 📂 Project Structure

```text
CivicConnect/
│
├── backend/
│   ├── src/main/java/com/civicconnect/backend/
│   │   ├── config/          # Security and CORS configuration
│   │   ├── controller/      # REST API controllers
│   │   ├── dto/             # Request and response DTOs
│   │   ├── model/           # JPA entities and enums
│   │   ├── repository/      # Spring Data JPA repositories
│   │   └── service/         # Business logic and AI services
│   │
│   └── src/main/resources/
│       └── application.properties
│
├── frontend/
│   ├── src/
│   │   ├── assets/          # Styles and static assets
│   │   ├── components/      # Reusable Vue components
│   │   ├── types/           # TypeScript interfaces
│   │   ├── router/          # Vue Router configuration
│   │   └── views/           # Application pages
│   │
│   └── package.json
│
└── README.md
```

---

## 🔄 Core Workflows

### Citizen Issue Reporting Workflow

1. A citizen submits an issue with a title, description, location, and optional image.
2. The backend receives and validates the report.
3. PostGIS checks for an existing unresolved issue within 50 meters.
4. If a matching issue is found, its confirmation count is incremented.
5. Otherwise, a new issue is created.
6. Gemini AI analyzes the issue and determines its priority.
7. The issue becomes available through the community feed and interactive map.

### Community Confirmation Workflow

1. A citizen finds an existing issue in the community feed.
2. The citizen selects "Me Too / Confirm".
3. The report's confirmation count increases.
4. When the count reaches three or more, the issue is escalated to `HIGH` priority.

### Municipal Issue Management Workflow

1. An administrator accesses the protected dashboard.
2. The administrator reviews reported issues.
3. The issue status is updated as work progresses.
4. Citizens can follow the report through the tracking interface.

---

## ⚙️ Getting Started

### Prerequisites

Make sure you have installed:

- Java 21 or a compatible Java version supported by your project configuration.
- Maven (or use the included Maven Wrapper).
- Node.js and npm.
- PostgreSQL 15+.
- PostGIS extension.

### 1. Clone the Repository

```bash
git clone https://github.com/Induwari28/CivicConnect.git
cd CivicConnect
```

### 2. Configure the Database

Create a PostgreSQL database:

```sql
CREATE DATABASE civicconnect_db;
```

Enable PostGIS:

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
```

Configure your database connection in the backend's `application.properties` file.

### 3. Configure Environment Variables

Configure the required database credentials, Gemini API key, administrative credentials, and image storage settings.

**Important:** Do not commit API keys, passwords, or other secrets to GitHub.

### 4. Run the Backend

```bash
cd backend
```

On Windows:

```bash
.\mvnw.cmd spring-boot:run
```

On macOS/Linux:

```bash
./mvnw spring-boot:run
```

### 5. Run the Frontend

Open another terminal:

```bash
cd frontend
npm install
npm run dev
```

Open the local development URL displayed by Vite in your browser.

---

## 🔐 Security Considerations

- Administrative endpoints use HTTP Basic Authentication.
- Configure CORS to allow only trusted frontend origins.
- Store Gemini API keys and database credentials outside source control.
- Validate uploaded files and restrict allowed file types and sizes.
- Restrict access to uploaded files and administrative operations.

---

## 🚀 Future Enhancements

Potential improvements to extend CivicConnect include:

- **AI Image Recognition:** Automatically identify infrastructure problems from uploaded photographs.
- **Real-Time Notifications:** Notify citizens when issue statuses change.
- **Predictive Analytics:** Analyze historical reports to identify recurring problem areas and predict maintenance needs.
- **Intelligent Department Assignment:** Automatically route issues to the relevant municipal departments.
- **Multilingual Support:** Provide Sinhala, Tamil, and English interfaces.
- **Mobile Application:** Develop dedicated Android and iOS applications.
- **Advanced Duplicate Detection:** Combine geographical proximity, text similarity, and image similarity.
- **Citizen Authentication:** Introduce registered user profiles and personalized reporting histories.
- **Public Feedback System:** Allow citizens to rate completed repairs and provide service feedback.

---

## 🎯 Project Goals

CivicConnect aims to:

- Encourage active citizen participation in municipal development.
- Improve the visibility of local infrastructure problems.
- Reduce duplicate reporting and unnecessary municipal work.
- Help authorities prioritize urgent community issues.
- Support data-driven decision-making through geospatial analytics and AI.

---

## 👩‍💻 Project Information

**Project:** CivicConnect — Smart Municipal Civic Issue Reporting Platform  
**Target Region:** Moratuwa Municipal Council, Sri Lanka  
**Category:** Full-Stack Web Application | Smart City | Civic Technology | AI Integration

**Developed using:** Vue 3, TypeScript, Spring Boot, PostgreSQL, PostGIS, and Google Gemini AI.

---

⭐ If you find this project interesting, consider giving the repository a star!
