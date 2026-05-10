# 📡 Telecom CDR Mediation System

A production-grade **Call Detail Record (CDR) Mediation System** that collects, decodes, validates, enriches, aggregates, and distributes telecom usage records from multiple network elements to downstream billing and fraud detection systems.

---

## 🏗️ System Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        NETWORK SIMULATORS                               │
│                                                                         │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐                  │
│  │ msc-service │   │smsc-service │   │ pgw-service │                  │
│  │  Voice CDRs │   │  SMS CDRs   │   │  Data CDRs  │                  │
│  │  ASN.1 BER  │   │  ASN.1 BER  │   │  ASN.1 BER  │                  │
│  └──────┬──────┘   └──────┬──────┘   └──────┬──────┘                  │
└─────────┼─────────────────┼─────────────────┼───────────────────────────┘
          └─────────────────┴─────────────────┘
                            │ FTP PUSH (Apache Commons Net)
                            ▼
                  ┌─────────────────┐
                  │   FTP Server    │
                  │  (pure-ftpd)    │
                  │ /home/testuser/ │
                  └────────┬────────┘
                           │ FTP POLL (every 5s)
                           ▼
          ┌────────────────────────────────────────┐
          │          mediation-service             │
          │                                        │
          │  Input      → FtpDownloader            │
          │               FtpProcessor             │
          │               Decoder (ASN.1 BER)      │
          │               Filter (isValid)         │
          │                                        │
          │  Processing → DuplicateDetector        │
          │               CDREnricher              │
          │               CDRAggregator            │
          │               CDRSorter                │
          │               CDRBuffer                │
          │               CDR_DAO                  │
          │                                        │
          │  Output     → CSVFormatter             │
          │               FileSender               │
          │               RmiFileSender            │
          └──────────────┬─────────────────────────┘
                         │              │
              ┌──────────┘              └──────────┐
              │ Java RMI (port 1099)               │ Java RMI (port 1099)
              ▼                                    ▼
     ┌──────────────────┐               ┌──────────────────┐
     │  billing-service │               │  fraud-service   │
     │  BillingRmiService│              │  FraudRmiService  │
     │  /app/input/ CSV │               │  /app/input/ CSV  │
     └────────┬─────────┘               └──────────────────┘
              │
              │ (Billing system processes CSV)
              ▼
     ┌──────────────────┐   ┌──────────────────┐
     │  Mediation NeonDB│   │  Billing NeonDB  │
     │  mediation_cdr   │   │  cdr + trigger   │
     │  subscribers     │   │  bill, file, etc.│
     │  cdr_aggregated  │   │  (auto-rated)    │
     └──────────────────┘   └──────────────────┘
```

---

## 🧩 Maven Multi-Module Structure

```
Mediation_System/                    ← telecom-parent (root POM)
├── telecom-common/                  ← Shared library (all modules depend on this)
│   └── FtpUploader, RemoteFileService, AbstractRemoteFileService
├── msc-service/                     ← MSC Voice CDR simulator
├── smsc-service/                    ← SMSC SMS CDR simulator
├── pgw-service/                     ← PGW Data CDR simulator
├── mediation-service/               ← Core mediation engine
│   ├── mediation/                   ← Input + Output layer
│   └── processing/                  ← Processing pipeline (Person B)
├── billing-service/                 ← Billing RMI file receiver
└── fraud-service/                   ← Fraud RMI file receiver
```

---

## ⚙️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Build | Maven (multi-module) |
| ASN.1 Encoding/Decoding | BouncyCastle (`bcprov-jdk15on`) |
| FTP | Apache Commons Net |
| CSV Parsing | OpenCSV |
| Database Connection Pool | HikariCP |
| Database | PostgreSQL (NeonDB cloud) |
| Inter-service Communication | Java RMI (UnicastRemoteObject) |
| Containerization | Docker / Podman |
| Orchestration | Docker Compose / Podman Compose |

---

## 📦 Module Descriptions

### `telecom-common` — Shared Library

| Class | Description |
|---|---|
| `FtpUploader` | Uploads encoded CDR binary files to the FTP server using Apache Commons Net |
| `RemoteFileService` | Java RMI interface — defines `receiveFile(String filename, byte[] data)` |
| `AbstractRemoteFileService` | Base RMI server implementation — extends `UnicastRemoteObject`, delegates to `onFileReceived()` |

---

### `msc-service` / `smsc-service` / `pgw-service` — Network Simulators

Each simulator generates realistic CDR data, encodes it as ASN.1 BER binary, and uploads it to the FTP server every 5 seconds.

| Class | Description |
|---|---|
| `MscVoiceCdr` / `SmscCdr` / `PgwDataCdr` | Java DTOs representing telecom CDR fields |
| `MscCdrGenerator` / `SmscCdrGenerator` / `PgwCdrGenerator` | Generates random CDR data + encodes to ASN.1 BER |
| `MscEngine` / `SmscEngine` / `PgwEngine` | Main loop — generate → encode → FTP upload |

---

### `mediation-service` — Core Processing Engine

#### Input Layer

| Class | Description |
|---|---|
| `Main` | Application entry point — starts the FTP polling loop |
| `FtpDownloader` | Polls the FTP server every 5 seconds, downloads new `.asn` files into memory, tracks already-processed files |
| `Decoder` | Parses ASN.1 BER `byte[]` → identifies record type → returns a typed `MscVoiceCdr`, `SmscCdr`, or `PgwDataCdr` |
| `FtpProcessor` | Orchestrates the complete pipeline — decoding → filtering → processing → output |

#### Processing Pipeline

| Class | Description |
|---|---|
| `DuplicateDetector` | Thread-safe deduplication using `ConcurrentHashMap` with composite keys per CDR type |
| `CDREnricher` | Loads `subscribers.csv` once at startup into an in-memory `HashMap` cache — enriches CDRs with carrier, region, and HPLMN data |
| `CDRAggregator` | Maintains hourly and daily aggregation buckets per subscriber using thread-safe `ConcurrentHashMap` |
| `CDRSorter` | Sorts a batch of CDRs by their timestamp field before persistence |
| `CDRBuffer` | Thread-safe `LinkedBlockingQueue` buffer — flushes sorted batches to the DB and triggers CSV generation |
| `CDR_DAO` | Lightweight static data access class — maps CDR fields to the `mediation_cdr` schema and calls `DB.executeUpdate()` |

#### Output Layer

| Class | Description |
|---|---|
| `DB` | HikariCP connection pool — single static `DataSource` initialized from environment variables |
| `CSVFormatter` | Reads `mediation_cdr` from Mediation NeonDB, writes a timestamped CSV file, calls `FileSender` |
| `FileSender` | Wraps `RmiFileSender` with 3-attempt exponential backoff (1s → 2s → 4s) |
| `RmiFileSender` | Connects to the `BillingFileService` and `FraudFileService` RMI registries on port `1099` and sends the CSV as raw bytes |

---

### `billing-service` / `fraud-service` — Downstream Receivers

| Class | Description |
|---|---|
| `BillingRmiService` | Extends `AbstractRemoteFileService` — registers itself on port `1099` as `"BillingFileService"`, saves received CSV files to `/app/input/` |
| `FraudRmiService` | Same pattern — registers as `"FraudFileService"`, saves CSV to `/app/input/` |

---

## 🗄️ Database Architecture

### Two Separate NeonDB Instances

This system uses **two completely separate** NeonDB databases:

#### Mediation NeonDB (mediation-service)

```sql
-- Enrichment reference data
CREATE TABLE subscribers (
    msisdn          VARCHAR(20)   PRIMARY KEY,
    carrier         VARCHAR(100)  NOT NULL,
    region          VARCHAR(100)  NOT NULL,
    subscriber_type VARCHAR(50)   NOT NULL DEFAULT 'Standard',
    status          VARCHAR(20)   NOT NULL DEFAULT 'active',
    hplmn           VARCHAR(20)   NOT NULL
);

-- Intermediate processed CDR store
CREATE TABLE mediation_cdr (
    id               BIGSERIAL     PRIMARY KEY,
    dial_a           VARCHAR(20)   NOT NULL,
    dial_b           VARCHAR(20)   NOT NULL,
    start_time       TIMESTAMP     NOT NULL,
    duration         BIGINT        NOT NULL DEFAULT 0,
    service_id       INT           NOT NULL,   -- 1=Voice, 2=Data, 3=SMS
    hplmn            VARCHAR(20),
    vplmn            VARCHAR(20),
    external_charges NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    rejection_reason VARCHAR(255),
    source_file      VARCHAR(200),
    record_type      VARCHAR(20)   NOT NULL,
    is_sent          BOOLEAN       DEFAULT FALSE,
    sent_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   DEFAULT NOW(),
    UNIQUE (dial_a, dial_b, start_time, duration)
);

-- Aggregation summaries
CREATE TABLE mediation_cdr_aggregated (
    id              BIGSERIAL    PRIMARY KEY,
    record_type     VARCHAR(20)  NOT NULL,
    dial_a          VARCHAR(20),
    hplmn           VARCHAR(20),
    window_type     VARCHAR(20)  NOT NULL,   -- HOURLY / DAILY
    window_start    TIMESTAMPTZ  NOT NULL,
    window_end      TIMESTAMPTZ  NOT NULL,
    total_records   INT          DEFAULT 0,
    total_duration  BIGINT       DEFAULT 0,
    total_bytes     BIGINT       DEFAULT 0,
    total_messages  INT          DEFAULT 0,
    computed_at     TIMESTAMPTZ  DEFAULT NOW()
);
```

#### Billing NeonDB (billing-service — separate project)

The billing system has its own NeonDB with a `cdr` table that includes a `trg_auto_rate_cdr` trigger. This trigger automatically computes `cost`, sets `rated_flag = true`, and links `bill_id` when a row is inserted. The mediation system **only delivers CSV files** to the billing service — it never writes directly to the billing database.

---

## 🚀 Getting Started

### Prerequisites

- Java 21+
- Maven 3.8+
- Docker or Podman
- A NeonDB account (two separate projects)

### 1. Clone the Repository

```bash
git clone https://github.com/mohamed-abdulnaby/Mediation_System.git
cd Mediation_System
```

### 2. Build All Modules

Always build from the **root** directory to resolve cross-module dependencies:

```bash
mvn clean install
```

### 3. Configure Environment Variables

Create or update your `docker-compose.yml` environment sections, or set these in your IntelliJ Run Configuration for local development:

```env
# Mediation NeonDB
DB_URL=jdbc:postgresql://<your-neondb-host>/neondb?sslmode=require
DB_USER=<your-neondb-user>
DB_PASSWORD=<your-neondb-password>

# FTP Connection
FTP_HOST=ftp-server
FTP_USER=testuser
FTP_PASS=testpass

# Downstream RMI hosts
BILLING_RMI_HOST=billing
FRAUD_RMI_HOST=fraud
```

### 4. Initialize Mediation NeonDB

Run the SQL statements from the [Database Architecture](#-database-architecture) section above in your **Mediation NeonDB** SQL console.

### 5. Run with Docker / Podman

#### Full Stack

```bash
podman compose up
```

#### Development Mode (Partial Stack — recommended for Person B)

Start only the infrastructure and simulators, then run `mediation-service` locally from IntelliJ for live debugging:

```bash
# One-time Podman setup for Linux
sudo sysctl net.ipv4.ip_unprivileged_port_start=21
sudo loginctl enable-linger $USER

# Start simulators and FTP server only
podman compose up ftp-server msc smsc pgw
```

Then run `mediation.Main` from IntelliJ with the environment variables configured.

---

## 🔁 Data Flow — Step by Step

1. **Generation:** Each simulator generates a random CDR every 5 seconds, encodes it as ASN.1 BER binary, and uploads it to the FTP server as a `.asn` file.
2. **Collection:** `FtpDownloader` polls the FTP server, finds new `.asn` files, and downloads each into memory as a `byte[]`.
3. **Decoding:** `Decoder` parses the binary using BouncyCastle ASN.1 into a typed Java CDR object (`MscVoiceCdr`, `SmscCdr`, or `PgwDataCdr`).
4. **Filtering:** `FtpProcessor.isValid()` discards records that are too short (voice < 3s), failed SMS, or zero-byte data sessions.
5. **Deduplication:** `DuplicateDetector` computes a composite key per CDR type and discards any CDR whose key has been seen before.
6. **Enrichment:** `CDREnricher` looks up the calling subscriber's MSISDN in the in-memory cache loaded from `subscribers.csv` and returns their carrier, region, and HPLMN.
7. **Aggregation:** `CDRAggregator` updates hourly and daily running totals (duration, bytes, message count) per subscriber.
8. **Buffering & Sorting:** `CDRBuffer` holds CDRs in a `LinkedBlockingQueue`, flushing every 5 seconds or when 100 records accumulate. Each flush sorts the batch by timestamp via `CDRSorter`.
9. **Persistence:** `CDR_DAO.insertCdr()` maps each CDR to the `mediation_cdr` schema and writes it to the Mediation NeonDB using `DB.executeUpdate()`.
10. **CSV Generation:** `CSVFormatter` reads unsent records from `mediation_cdr` and writes a timestamped CSV file compatible with the Billing system's schema.
11. **Delivery:** `FileSender` wraps `RmiFileSender` with retry logic. `RmiFileSender` connects to the `BillingFileService` and `FraudFileService` RMI registries on port `1099` and sends the CSV as raw bytes via Java RMI.
12. **Receipt:** `BillingRmiService` and `FraudRmiService` receive the CSV and save it to `/app/input/` for downstream processing.

---

## 📊 CDR Field Mapping

| Field | MSC Voice | SMSC SMS | PGW Data |
|---|---|---|---|
| `dial_a` | `callingNumber` | `senderMSISDN` | `servedMSISDN` |
| `dial_b` | `calledNumber` | `receiverMSISDN` | `"internet"` |
| `start_time` | `callStartTime` | `submissionTime` | `startTime` |
| `duration` | `callDuration` (seconds) | `1` (per message) | `totalBytes` (bytes) |
| `service_id` | `1` | `3` | `2` |

---

## 👥 Team Contributions

| Person | Responsibility |
|---|---|
| **Person A** | Network simulators (MSC/SMSC/PGW), ASN.1 encoding, FTP upload, FTP download, ASN.1 decoding, CDR filtering |
| **Person B** | Processing pipeline — `DuplicateDetector`, `CDREnricher`, `CDRAggregator`, `CDRSorter`, `CDRBuffer`, `CDR_DAO`, Mediation NeonDB schema |
| **Person C** | Output pipeline — `CSVFormatter`, `FileSender`, `RmiFileSender`, `BillingRmiService`, `FraudRmiService`, Docker packaging, HikariCP DB setup |

---

## 🧪 Testing

Unit tests are located in `mediation-service/src/test/java/`:

```bash
# Run all tests from the mediation-service module
cd mediation-service
mvn test
```

| Test Class | What It Covers |
|---|---|
| `DuplicateDetectorTest` | Verifies duplicate CDRs are correctly identified and blocked |
| `CDREnricherTest` | Verifies CSV loading and subscriber lookup by MSISDN |
| `CdrFlowTest` | End-to-end flow test for the processing pipeline |
| `RmiFileSenderTest` | Verifies RMI connection and file transmission |

---

## 🐳 Docker Services

| Service | Image | Port | Role |
|---|---|---|---|
| `ftp-server` | `stilliard/pure-ftpd` | `21`, `30000-30009` | CDR file collection point |
| `msc` | `mediation_system_msc` | `8080` | MSC Voice CDR simulator |
| `smsc` | `mediation_system_smsc` | `8080` | SMSC SMS CDR simulator |
| `pgw` | `mediation_system_pgw` | `8080` | PGW Data CDR simulator |
| `mediation` | `mediation_system_mediation` | `8080` | Core mediation engine |
| `billing` | `mediation_system_billing` | `1099` | Billing RMI file receiver |
| `fraud` | `mediation_system_fraud` | `1099` | Fraud RMI file receiver |

> **Note for Podman users:** Run `sudo sysctl net.ipv4.ip_unprivileged_port_start=21` before starting the stack to allow binding to port 21 in rootless mode.

---

## 📄 License

This project was developed as a graduation project for educational purposes.
