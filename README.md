<div align="center">

# 💳 Unipay — Campus Cashless Payment Backend

**A Spring Boot backend with automated tests, CI, schema migrations, and secure campus cashless payment capabilities.**

[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1?style=flat-square&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Firebase](https://img.shields.io/badge/Firebase-Auth-FFCA28?style=flat-square&logo=firebase&logoColor=black)](https://firebase.google.com/)
[![Razorpay](https://img.shields.io/badge/Razorpay-Payments-3395FF?style=flat-square&logo=razorpay&logoColor=white)](https://razorpay.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)

</div>

---

## 📌 Overview

Unipay enables **secure, instantaneous NFC card-based payments** from students to campus merchants, alongside **digital wallet top-ups** through Razorpay. The system is designed with atomic safety guarantees — zero double-spending through strict pessimistic database locks, and an immutable double-entry ledger for every microtransaction.

The project also includes an **ESP32-based NFC POS terminal** ([`hardware/`](hardware/)) that runs on physical hardware to process tap-to-pay transactions via the REST API.

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                         CLIENT LAYER                                 │
│  ┌─────────────┐  ┌──────────────────┐  ┌────────────────────────┐  │
│  │  Mobile App  │  │  Flutter App     │  │  ESP32 NFC POS Terminal│  │
│  │  (Student)   │  │  (Android/iOS/Web)│  │  (Hardware Device)     │  │
│  └──────┬───────┘  └───────┬──────────┘  └───────────┬────────────┘  │
└─────────┼──────────────────┼─────────────────────────┼───────────────┘
          │                  │                         │
          ▼                  ▼                         ▼
┌──────────────────────────────────────────────────────────────────────┐
│                       API GATEWAY (REST)                             │
│                     Spring Boot + Spring Security                    │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │  Firebase Auth → Custom JWT → Role-Based Access Control (RBAC) │ │
│  │  Roles: STUDENT | MERCHANT | ADMIN | SUPER_ADMIN               │ │
│  └─────────────────────────────────────────────────────────────────┘ │
├──────────────────────────────────────────────────────────────────────┤
│                       BUSINESS LOGIC                                 │
│  ┌────────────┐ ┌────────────┐ ┌──────────────┐ ┌───────────────┐  │
│  │ Auth       │ │ Wallet     │ │ Payment      │ │ Card          │  │
│  │ Service    │ │ Service    │ │ Service      │ │ Service       │  │
│  └────────────┘ └────────────┘ └──────────────┘ └───────────────┘  │
├──────────────────────────────────────────────────────────────────────┤
│                       DATA LAYER                                     │
│  ┌──────────────────────────────────────────────┐ ┌──────────────┐  │
│  │  PostgreSQL + Pessimistic Row-Level Locking   │ │  Razorpay    │  │
│  │  Double-Entry Ledger (CREDIT + DEBIT)         │ │  Gateway     │  │
│  │  JPA Specifications for Dynamic Queries       │ │  (INR)       │  │
│  └──────────────────────────────────────────────┘ └──────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
```

---

## ✨ Key Features

### 🔐 Two-Tier Authentication
- Clients authenticate with **Firebase** (Email/Password) for identity validation
- Backend issues **stateless, role-bound JWTs** for internal API access
- Explicit token invalidation on logout with refresh token rotation

### 🛡️ Role-Based Access Control (RBAC)
- Strict segregation between `STUDENT`, `MERCHANT`, `ADMIN`, and `SUPER_ADMIN` operations
- Enforced at the Spring Security filter chain + method-level authorization

### 🔒 Pessimistic Row-Level Locking
- Wallet balances are locked in PostgreSQL via `SELECT ... FOR UPDATE` during transactions
- Physically prevents race conditions and double-spending during rapid NFC taps

### 📒 Immutable Double-Entry Ledger
- Every monetary movement generates **two synchronous `WalletTransaction` entries** (one `CREDIT`, one `DEBIT`)
- Wrapped inside a master `Payment` umbrella object for full audit traceability

### 💳 NFC Card Payments
- Physical student ID cards mapped to user wallets via configurable UIDs
- Remote locking, temporary blocking, and instant unlinking via REST API
- ESP32 hardware POS terminal for real-world tap-to-pay ([see hardware/](hardware/))

### 💰 Razorpay Wallet Top-Ups
- Create recharge orders with amount validation (₹10 – ₹10,000)
- Server-side Razorpay signature verification for atomic balance updates

### 📊 Dynamic Transaction History
- High-performance query engine with type filtering, date ranges, and pagination
- Built on `JpaSpecificationExecutor` for composable, dynamic queries

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Java 17 |
| **Framework** | Spring Boot 4.0, Spring Security, Spring Data JPA |
| **Database** | PostgreSQL 15 (Hibernate ORM) |
| **Auth** | Firebase Admin SDK + Custom JWT (jjwt) |
| **Payments** | Razorpay Java SDK |
| **Migration** | Flyway |
| **API Docs** | SpringDoc OpenAPI (Swagger UI) |
| **Build** | Maven |
| **CI/CD** | GitHub Actions |
| **Container** | Docker + Docker Compose |
| **Hardware** | ESP32 + MFRC522 NFC Reader + SH1106 OLED |

---

## 📁 Project Structure

```
unipay-backend/
├── src/main/java/com/umesh/unipay_1/
│   ├── config/           # Security, Firebase, Swagger, JWT filter configurations
│   ├── controller/       # REST API controllers (Auth, Payment, Wallet, Cards, Admin)
│   ├── dto/              # Request/Response DTOs with validation annotations
│   ├── entity/           # JPA entities (User, Wallet, Card, Payment, WalletTransaction)
│   ├── enums/            # Role, TransactionType, RechargeOrderStatus
│   ├── exception/        # Custom exceptions + GlobalExceptionHandler
│   ├── repository/       # Spring Data JPA repositories with custom queries
│   ├── security/         # RBAC aspect, authorization annotations
│   ├── service/          # Core business logic (Auth, Payment, Wallet, Card, etc.)
│   └── util/             # Utility classes
├── src/main/resources/
│   ├── application.properties       # Default/local configuration
│   └── application-prod.properties  # Production configuration (env vars)
├── hardware/                         # ESP32 NFC POS Terminal code
│   ├── esp32_pos_terminal.ino
│   └── README.md
├── docker-compose.yml    # PostgreSQL + App containers
├── Dockerfile            # Multi-stage build
├── .github/workflows/    # CI pipeline
├── api_test_inputs.md    # Complete API testing guide
└── .env.example          # Environment variable template
```

---

## 🚀 Getting Started

### Prerequisites

- **Java 17+** — [Download](https://adoptium.net/)
- **Maven 3.9+** — [Download](https://maven.apache.org/)
- **PostgreSQL 15+** — [Download](https://www.postgresql.org/) or use Docker
- **Firebase Project** — [Console](https://console.firebase.google.com/)
- **Razorpay Account** — [Dashboard](https://dashboard.razorpay.com/) (test mode)

### Option 1: Docker Compose (Recommended)

```bash
# 1. Clone the repository
git clone https://github.com/umesh3386/Unipay-Backend1.git
cd Unipay-Backend1

# 2. Create your environment file
cp .env.example .env
# Edit .env with your Firebase, JWT, and Razorpay credentials

# 3. Start everything
docker compose up --build

# App runs at http://localhost:8080
# Swagger UI at http://localhost:8080/swagger-ui/index.html
```

### Option 2: Local Development

```bash
# 1. Clone the repository
git clone https://github.com/umesh3386/Unipay-Backend1.git
cd Unipay-Backend1

# 2. Start PostgreSQL on localhost:5432
#    Create a database named 'unipay_db'

# 3. Create your environment file
cp .env.example .env
# Edit .env with your credentials

# 4. Run with Maven
mvn clean spring-boot:run
```

### Environment Variables

Copy `.env.example` to `.env` and fill in your values:

| Variable | Description |
|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
| `JWT_SECRET` | Base64-encoded 256-bit key (`openssl rand -base64 32`) |
| `FIREBASE_SERVICE_ACCOUNT` | Full Firebase service account JSON (single line) |
| `FIREBASE_API_KEY` | Firebase Web API key |
| `RAZORPAY_KEY` | Razorpay API key (test or live) |
| `RAZORPAY_SECRET` | Razorpay API secret |

> **Note:** For Firebase setup, go to Firebase Console → Project Settings → Service Accounts → Generate New Private Key. Paste the entire JSON as a single-line string in `FIREBASE_SERVICE_ACCOUNT`.

---

## 📡 API Documentation

### Interactive Docs

Once the app is running, access the **Swagger UI** at:
```
http://localhost:8080/swagger-ui/index.html
```

### API Overview

#### Authentication (`/api/auth`)
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `POST` | `/api/auth/register` | Register a new student + provision wallet | Public |
| `POST` | `/api/auth/login` | Login → get JWT token pair | Public |
| `POST` | `/api/auth/merchant/login` | Merchant-only login | Public |
| `POST` | `/api/auth/refresh` | Rotate access token via refresh token | Public |
| `POST` | `/api/auth/logout` | Revoke session | Any |
| `GET` | `/api/auth/me` | Get current user info | Any |

#### Wallet & Payments (`/api/wallet`, `/api/payment`)
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `GET` | `/api/wallet/balance` | Get wallet balance | User |
| `POST` | `/api/wallet/create-recharge-order` | Create Razorpay recharge order | User |
| `POST` | `/api/wallet/verify-payment` | Verify Razorpay payment + credit wallet | User |
| `POST` | `/api/payment/tap` | **Core:** Process NFC card payment | Merchant |
| `POST` | `/api/payment/refund` | Process refund | Merchant |
| `GET` | `/api/payment/status/{id}` | Check payment status | Any |

#### NFC Cards (`/api/cards`, `/api/admin/cards`)
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `GET` | `/api/cards/my-cards` | List student's NFC cards | Student |
| `PATCH` | `/api/cards/{uid}` | Activate/deactivate own card | Student |
| `POST` | `/api/admin/cards` | Register new NFC card UID | Admin |
| `POST` | `/api/admin/cards/link` | Link card to a student | Admin |
| `DELETE` | `/api/admin/cards/{uid}/unlink` | Unlink card from user | Admin |

#### Transactions (`/api/transactions`)
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `GET` | `/api/transactions/history` | Paginated history with filters | Any |
| `GET` | `/api/transactions/{id}` | Detailed transaction info | Any |

#### Admin (`/api/admin`)
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `POST` | `/api/admin/merchants/register` | Register a new merchant | Admin |
| `GET` | `/api/admin/users` | List all users (paginated) | Admin |
| `PATCH` | `/api/admin/users/{id}/block` | Block a user | Admin |
| `PATCH` | `/api/admin/users/{id}/unblock` | Unblock a user | Admin |

> 📋 For complete request/response examples and test payloads, see [`api_test_inputs.md`](api_test_inputs.md).

---

## 🔌 ESP32 NFC POS Terminal

This project includes a hardware component — an **ESP32-based NFC Point-of-Sale terminal** that processes contactless card payments in real-time.

**Features:**
- 128×64 OLED display with step-by-step payment UI
- 4×4 keypad for amount entry
- MFRC522 NFC reader for card detection
- LED + buzzer feedback for payment status
- Auto-reconnect WiFi and JWT token refresh

👉 See [`hardware/README.md`](hardware/README.md) for wiring diagrams, components, and setup instructions.

---

## 🔧 CI/CD Setup

The project includes a GitHub Actions workflow (`.github/workflows/ci.yml`) that builds on every push to `main`.

To enable CI, add these **repository secrets** in GitHub:
`Settings → Secrets and variables → Actions → New repository secret`

Required secrets: `DB_URL`, `DB_USER`, `DB_PASS`, `JWT_SECRET`, `FIREBASE_SERVICE_ACCOUNT`, `FIREBASE_API_KEY`, `RAZORPAY_KEY`, `RAZORPAY_SECRET`

---

## 📊 Database Schema

```
┌──────────────┐     ┌──────────────┐     ┌───────────────────┐
│    users     │     │   wallets    │     │  wallet_transactions│
├──────────────┤     ├──────────────┤     ├───────────────────┤
│ id (PK)      │◄───┐│ id (PK)      │     │ id (PK)           │
│ email        │    ││ user_id (FK) │◄────│ wallet_id (FK)    │
│ full_name    │    │├──────────────┤     │ payment_id (FK)   │
│ role         │    ││ balance      │     │ amount            │
│ is_blocked   │    │└──────────────┘     │ type (CREDIT/     │
│ student_id   │    │                     │       DEBIT)      │
└──────────────┘    │┌──────────────┐     │ transaction_type  │
                    ││   cards      │     └───────────────────┘
                    │├──────────────┤
                    ││ id (PK)      │     ┌──────────────────┐
                    ││ card_uid     │     │   payments       │
                    └│ user_id (FK) │     ├──────────────────┤
                     │ is_active    │     │ id (PK)          │
                     └──────────────┘     │ sender_id (FK)   │
                                          │ receiver_id (FK) │
                     ┌──────────────┐     │ amount           │
                     │recharge_orders│     │ status           │
                     ├──────────────┤     └──────────────────┘
                     │ id (PK)      │
                     │ user_id (FK) │
                     │ order_id     │
                     │ amount       │
                     │ status       │
                     └──────────────┘
```

---

## 📝 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

---

<div align="center">

**Built with ❤️ by [Umesh Shinde](https://github.com/umesh3386)**

⭐ Star this repo if you found it useful!

</div>
