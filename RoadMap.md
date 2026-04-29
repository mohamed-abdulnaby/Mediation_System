# CDR Mediation System - Development Plan

## Overview

A Java-based Call Detail Record (CDR) Mediation System that processes ASN.1 encoded CDR files from multiple network elements (MSC, SMSC, A-PGW) and outputs validated, enriched, aggregated CSV records for downstream billing/telecom systems.

## Data Sources

| Source | Network Element | CDR Type | Protocol |
|--------|----------------|----------|----------|
| MSC | Mobile Switching Center | Voice/Call CDR | ASN.1 BER |
| SMSC | Short Message Service Center | SMS CDR | ASN.1 BER |
| A-PGW | Access Packet Data Gateway (PGW) | Data/Internet CDR | ASN.1 BER |

### Delivery Mechanism

- **Protocol:** FTP/SFTP (batch file transfer)
- **Directory:** Common input directory (`/data/input`)
- **Real-time:** Files dropped as they are generated (near real-time processing)
- **Pattern:** Network elements upload files with pattern `CDR_<timestamp>_<SOURCE>_<seq>.asn`
- **Processing:** WatchService detects new files immediately and triggers processing pipeline

## Architecture - Multi-Source

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    Network Elements (via FTP)                           │
│                                                                         │
│  MSC (Voice) ──\                                                        │
│                │                                                        │
│  SMSC (SMS) ───┼──→ FTP Server → /data/input directory                 │
│                │                                                        │
│  A-PGW (Data) ─/                                                        │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                  CdrCollector (NIO WatchService)                       │
│                  - Detects new .asn files                               │
│                  - Triggers processing on file arrival                   │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                       SourceDetector                                    │
│                       - Extracts source from filename                   │
│                       - Pattern: CDR_<ts>_<SOURCE>_<seq>.asn           │
│                       - SOURCE: MSC | SMSC | PGW                       │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     CdrDecoder (Single Schema)                          │
│                     - Decodes ASN.1 BER using single schema             │
│                     - Returns unified CDR with source-specific fields    │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    CdrValidator                                          │
│                    - Source-specific validation rules                   │
│                    - MSC: dialA, dialB, duration, imsi required        │
│                    - SMSC: sender, receiver, messageLength required     │
│                    - PGW: imsi, apn, bytesIn, bytesOut required        │
└───────────────────────────────┬─────────────────────────────────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    PROCESSING PHASE                                     │
├─────────────────────────────────────────────────────────────────────────┤
│  Enrichment (CSV)  │  Duplicate Detection  │  Aggregation             │
│  Buffering         │  Sorting              │  Consolidation             │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    OUTPUT PHASE                                          │
├─────────────────────────────────────────────────────────────────────────┤
│  CSV Format Mapping  │  Header/Trailer Gen  │  File Distribution       │
│  Error Alarms        │  Monitor Servlet                                │
└───────────────────────────────┬─────────────────────────────────────────┘
                                ▼
                         CSV Output Files
                         (single file with nulls for unused fields)

## Tech Stack

| Component | Technology | Version | Reason |
|-----------|-----------|--------|--------|
| Framework | Jakarta Servlet EE 11 | 7.0.0 | Tomcat 11 + Java 21 |
| ASN.1 Decoding | ASN1bean (beanit/jasn1) | 1.3.2 | Apache 2.0, ESA approved, fastest benchmark |
| File Watching | Java NIO WatchService | Built-in | No extra dependencies |
| CSV Parsing | OpenCSV | 5.9 | Simple CSV enrichment lookups |
| JSON | Jakarta JSON EE 11 (Parsson) | 3.2.0 / 1.1.0 | REST API, config files |
| YAML | SnakeYAML | 2.3 | Config files |
| Testing | JUnit Jupiter | 5.13.2 | Unit testing |
| Build Tool | Maven | - | Dependency management |
| Deployment | Tomcat 11 + Docker | JDK 21 | Per deployment target |

## Requirements

| # | Requirement | Description |
|----|-------------|-------------|
| 1 | CDR Collection | Monitor input directory for ASN.1 CDR files |
| 2 | Decoding/Encoding | Decode ASN.1 BER encoded CDR records |
| 3 | Filtering | Apply filtering rules to incoming CDRs |
| 4 | Conversion | Convert between formats as needed |
| 5 | Validation | Validate CDR field integrity and constraints |
| 6 | Record Enrichment | Enrich CDRs using CSV lookup reference data |
| 7 | Duplicate Detection | Detect duplicates by dial_a + dial_b + timestamp |
| 8 | Aggregation/Correlation | Aggregate duration totals + count grouping |
| 9 | Consolidation | Consolidate multiple records into single output |
| 10 | Buffering | Buffer records before downstream processing |
| 11 | Sorting | Sort records by configurable keys |
| 12 | Downstream Format Mapping | Map CDR fields to output CSV format |
| 13 | Header/Trailer Generation | Generate file header + trailer with metadata |
| 14 | Downstream Distribution | Distribute processed files to output directory |
| 15 | Error Messaging/Alarms | Log errors + optional email notifications |

## Architecture

```
ASN.1 CDR Input Files
         ↓
┌─────────────────────────┐
│      INPUT PHASE        │
├─────────────────────────┤
│  Collection (NIO)       │  Person A
│  ASN.1 Decoding         │  Person A
│  Filtering              │  Person A
│  Validation             │  Person A
└─────────────────────────┘
         ↓
┌─────────────────────────┐
│   PROCESSING PHASE      │
├─────────────────────────┤
│  Enrichment (CSV)       │  Person B
│  Duplicate Detection    │  Person B
│  Aggregation            │  Person B
│  Consolidation          │  Person B
│  Buffering              │  Person B
│  Sorting                │  Person B
└─────────────────────────┘
         ↓
┌─────────────────────────┐
│      OUTPUT PHASE       │
├─────────────────────────┤
│  CSV Format Mapping     │  Person C
│  Header/Trailer Gen     │  Person C
│  File Distribution      │  Person C
│  Error Alarms           │  Person C
│  Monitor Servlet        │  Person C
└─────────────────────────┘
         ↓
CSV Output Files (to downstream)
```

## Input/Output Specifications

### Input
- **Format:** ASN.1 BER (Basic Encoding Rules)
- **Source:** Directory watcher monitoring input folder (common directory for all sources)
- **File Naming Pattern:** `CDR_<timestamp>_<SOURCE>_<sequence>.asn`
  - Examples: `CDR_20240115_143022_MSC_001.asn`, `CDR_20240115_143025_SMSC_001.asn`, `CDR_20240115_143028_PGW_001.asn`
- **Source Detection:** Filename pattern (MSC/SMSC/PGW extracted from filename)
- **Single Schema:** All three CDR types use same ASN.1 schema with optional fields

### Output
- **Format:** CSV (flat file)
- **Destination:** File system (output directory)
- **Content:** Single merged file with nulls for source-specific fields not used by each record type
- **Header:** Column names + metadata
- **Trailer:** Record count + checksum

## Duplicate Detection

- **Scope:** Works across all source types (MSC, SMSC, PGW)
- **Key Fields:** recordId + timestamp (unique per source within same second)
- **Match Window:** Same second (same timestamp value)
- **Implementation:** HashSet with composite key
- **Note:** For MSC use dialA+dialB+timestamp, for SMSC use sender+receiver+timestamp, for PGW use imsi+apn+timestamp

## Enrichment

- **Source:** CSV lookup files on disk
- **Lookup Key:** dial_a (caller number)
- **Enrichment Data:** Carrier, Region, Subscriber Info

## Aggregation

- **Type:** Duration totals per dial_a + Count by time window
- **Configuration:** Configurable time windows (JSON/YAML)
- **Metrics:** Total duration, CDR count, grouped by criteria

## Error Handling

- **Logging:** java.util.logging (log file)
- **Alarms:** Log file (default) + Email (optional)

## Package Structure

```
org.example.mediation_system
├── input/
│   ├── collector/
│   │   └── CdrCollector.java         (NIO WatchService file watcher)
│   ├── detector/
│   │   └── SourceDetector.java       (Extracts source from filename: MSC/SMSC/PGW)
│   ├── decoder/
│   │   ├── CdrDecoder.java           (ASN1bean wrapper - single schema)
│   │   └── [generated]/              (ASN1bean generated classes)
│   ├── filter/
│   │   └── CdrFilter.java           (CDR filtering rules)
│   └── validator/
│       └── CdrValidator.java        (Source-specific field validation)
├── processing/
│   ├── enricher/
│   │   └── CdrEnricher.java          (CSV lookup enrichment)
│   ├── dedup/
│   │   └── DuplicateDetector.java   (dial_a + dial_b + timestamp)
│   ├── aggregator/
│   │   └── CdrAggregator.java      (Duration totals + count grouping)
│   ├── consolidator/
│   │   └── CdrConsolidator.java     (Record consolidation)
│   ├── buffer/
│   │   └── CdrBuffer.java           (Record buffering)
│   └── sorter/
│       └── CdrSorter.java           (Record sorting)
├── output/
│   ├── formatter/
│   │   └── CsvFormatter.java        (CSV format mapping)
│   ├── generator/
│   │   └── HeaderTrailerGenerator.java (Header/Trailer)
│   └── distributor/
│       └── CdrDistributor.java      (File distribution)
├── common/
│   ├── model/
│   │   ├── CdrSource.java           (Enum: MSC, SMSC, PGW)
│   │   ├── Cdr.java                 (CDR domain object with all fields)
│   │   └── AggregatedCdr.java      (Aggregated CDR)
│   ├── config/
│   │   └── MediationConfig.java    (Configuration loader)
│   └── error/
│       └── ErrorHandler.java        (Error + alarm handling)
└── servlet/
    └── MonitorServlet.java          (REST endpoints for monitoring)
```

## Development Phases

### Phase 0: Project Setup (All 3)
| Task | Owner | Notes |
|------|-------|-------|
| Update pom.xml with dependencies | All | ASN1bean, OpenCSV, Jakarta Mail, Jackson |
| Create package structure | All | Create all packages per structure above |
| Create Docker configuration | All | Multi-stage Dockerfile for Tomcat |
| Create config files | All | mediation.properties, logging.properties |

### Phase 1: Domain Model + ASN.1 Schema (Person A)
| Task | Owner | Notes |
|------|-------|-------|
| Define ASN.1 schema (.asn file) | Person A | Single schema for MSC/SMSC/PGW with optional fields |
| Generate Java classes | Person A | Run asn1bean-compiler |
| Create CdrSource enum | Person A | Enum: MSC=1, SMSC=2, PGW=3 |
| Create CDR domain model | Person A | CDR.java with all fields (source-specific) |

### Phase 2: Input Pipeline (Person A)
| Task | Owner | Dependencies | Notes |
|------|-------|-------------|-------|
| Collection service | Person A | Phase 1 | NIO WatchService file watcher |
| SourceDetector | Person A | Phase 1 | Extract source type from filename pattern |
| ASN.1 Decoder wrapper | Person A | Phase 1 | Use generated ASN1bean classes (single schema) |
| Filtering | Person A | Phase 2 | CDR field filtering rules |
| Validation | Person A | Phase 2 | Source-specific field validation |

### Phase 3: Processing Pipeline (Person B)
| Task | Owner | Dependencies | Notes |
|------|-------|-------------|-------|
| CSV Enrichment engine | Person B | Phase 1 | CSV lookup by dial_a |
| Duplicate Detection | Person B | Phase 2 | dial_a + dial_b + timestamp |
| Aggregation engine | Person B | Phase 2 | Duration totals + count |
| Consolidation | Person B | Phase 3 | Record consolidation |
| Buffering | Person B | Phase 3 | Buffer records |
| Sorting | Person B | Phase 3 | Sort by configurable keys |

### Phase 4: Output Pipeline (Person C)
| Task | Owner | Dependencies | Notes |
|------|-------|-------------|-------|
| CSV Formatter | Person C | Phase 2 | Map CDR fields → CSV |
| Header/Trailer Generator | Person C | Phase 4 | Record count + checksum |
| File Distributor | Person C | Phase 4 | Write to output directory |
| Error Handler + Alarms | Person C | All | Log file + optional email |
| Monitor Servlet | Person C | All | REST status/metrics endpoints |

### Phase 5: Testing & Deployment (All 3)
| Task | Owner | Notes |
|------|-------|-------|
| Unit tests | All | JUnit tests per module |
| Integration tests | All | End-to-end pipeline test |
| Docker image | All | Multi-stage build |
| Tomcat configuration | All | context.xml, web.xml |

## Implementation Notes

### ASN1bean Workflow
1. Write CDR ASN.1 schema in `.asn` file format
2. Download asn1bean-compiler
3. Run: `java -jar asn1bean-compiler.jar -f CDR.asn -o src/main/java/org/example/mediation_system/input/decoder/generated/`
4. Generated classes can be used directly in decoder wrapper

### Source Detection (Filename Pattern)
```
CDR_<timestamp>_<SOURCE>_<sequence>.asn
Examples:
- CDR_20240115_143022_MSC_001.asn   → Source: MSC
- CDR_20240115_143025_SMSC_001.asn  → Source: SMSC
- CDR_20240115_143028_PGW_001.asn   → Source: PGW
```

### Source-Specific Validation Rules

| Source | sourceType | Required Fields |
|--------|------------|-----------------|
| MSC (Voice) | 1 | dialA, dialB, duration, imsi, timestamp |
| SMSC (SMS) | 2 | sender, receiver, messageLength, imsi, timestamp |
| PGW (Data) | 3 | imsi, apn, bytesIn, bytesOut, timestamp |

### Single ASN.1 Schema (Unified)
```asn
CDR DEFINITIONS ::= BEGIN

CdrRecord ::= SEQUENCE {
    -- Common fields
    sourceType         INTEGER (1..3),  -- 1=MSC, 2=SMSC, 3=PGW
    recordId           INTEGER,
    timestamp          GeneralizedTime,

    -- Voice (MSC) fields - sourceType=1
    dialA              NumericString (SIZE(1..20)) OPTIONAL,
    dialB              NumericString (SIZE(1..20)) OPTIONAL,
    duration           INTEGER (0..86400) OPTIONAL,
    callType           INTEGER (0..9) OPTIONAL,
    imsi               NumericString (SIZE(15)) OPTIONAL,
    imei               NumericString (SIZE(15)) OPTIONAL,

    -- SMS (SMSC) fields - sourceType=2
    sender             NumericString (SIZE(1..20)) OPTIONAL,
    receiver           NumericString (SIZE(1..20)) OPTIONAL,
    smscNumber         NumericString (SIZE(1..20)) OPTIONAL,
    messageLength      INTEGER (0..160) OPTIONAL,
    messageType        INTEGER (0..5) OPTIONAL,

    -- Data (PGW) fields - sourceType=3
    apn                NumericString (SIZE(1..100)) OPTIONAL,
    bytesIn            INTEGER (0..4294967295) OPTIONAL,
    bytesOut           INTEGER (0..4294967295) OPTIONAL,
    ratType            INTEGER (0..10) OPTIONAL
}

CdrFile ::= SEQUENCE OF CdrRecord
END
```

### Duplicate Detection Implementation
```java
Set<String> seen = new HashSet<>();
String key = dial_a + "|" + dial_b + "|" + timestamp; // same second
if (seen.contains(key)) duplicate = true;
else seen.add(key);
```

### Enrichment CSV Lookup
```csv
dial_a,carrier,region,subscriber_type
123456,AT&T,Northeast,Premium
```

### Aggregation Configuration
```yaml
aggregation:
  windows:
    - name: hourly
      duration: 3600000
    - name: daily
      duration: 86400000
  groupBy:
    - dial_a
    - carrier
```

## Team Assignment Summary

| Person | Phases | Modules |
|--------|--------|---------|
| **Person A** | Phase 0-2 | input/, common/model/ (CDR) |
| **Person B** | Phase 3 | processing/ (all modules) |
| **Person C** | Phase 4 | output/, common/error/ (Servlet) |

## Files to Create

| File | Owner | Phase |
|------|-------|-------|
| pom.xml (updated) | All | 0 |
| Dockerfile | All | 0 |
| docker-compose.yml | All | 0 |
| config.yaml | All | 0 |
| logging.properties | All | 0 |
| CDR.asn (Single ASN.1 schema) | A | 1 |
| CdrSource.java (enum: MSC, SMSC, PGW) | A | 1 |
| Cdr.java (with all source-specific fields) | A | 1 |
| SourceDetector.java | A | 2 |
| CdrCollector.java | A | 2 |
| CdrDecoder.java (single decoder for all sources) | A | 2 |
| CdrFilter.java | A | 2 |
| CdrValidator.java (source-specific validation) | A | 2 |
| CdrEnricher.java | B | 3 |
| DuplicateDetector.java | B | 3 |
| CdrAggregator.java | B | 3 |
| CdrConsolidator.java | B | 3 |
| CdrBuffer.java | B | 3 |
| CdrSorter.java | B | 3 |
| CsvFormatter.java (with null handling) | C | 4 |
| HeaderTrailerGenerator.java | C | 4 |
| CdrDistributor.java | C | 4 |
| ErrorHandler.java | C | 4 |
| MonitorServlet.java | C | 4 |
| MediationConfig.java | All | 0 |
| integration tests | All | 5 |

## Configuration File Format (config.yaml)

```yaml
mediation:
  input:
    directory: /data/input
    file-pattern: "*.cdr"
    watch-interval-ms: 5000
  output:
    directory: /data/output
    file-prefix: "processed_cdr"
    batch-size: 1000
  processing:
    buffer-size: 10000
    worker-threads: 4
    sort-keys:
      - dial_a
      - timestamp
  enrichment:
    csv-path: /data/reference
    cache-enabled: true
    cache-size: 5000
  aggregation:
    windows:
      - name: hourly
        duration-ms: 3600000
      - name: daily
        duration-ms: 86400000
  duplicate:
    check-window-seconds: 1
    retain-first: true
  error-handling:
    retry-attempts: 3
    retry-delay-ms: 1000
    dead-letter-dir: /data/error
logging:
  level: INFO
  file: /var/log/mediation/mediation.log
  max-size-mb: 100
  max-backups: 10
alarms:
  enabled: true
  email-recipients:
    - ops@example.com
  error-threshold: 100
  interval-minutes: 15
```

## Logging Configuration (logging.properties)

```properties
handlers=java.util.logging.FileHandler, java.util.logging.ConsoleHandler
.level=INFO
java.util.logging.FileHandler.pattern=/var/log/mediation/mediation.log
java.util.logging.FileHandler.limit=10485760
java.util.logging.FileHandler.count=5
java.util.logging.FileHandler.formatter=java.util.logging.SimpleFormatter
java.util.logging.SimpleFormatter.format=[%1$tF %1$tP] %4$s %2$s: %5$s%6$s%n
org.example.mediation.level=FINE
```

## Error Recovery Strategy

| Scenario | Recovery Action |
|----------|-----------------|
| Malformed CDR record | Skip record, log error, increment error counter |
| Missing enrichment data | Use default values, log warning |
| File write failure | Retry with exponential backoff, move to dead-letter on failure |
| Decoder failure | Move file to error directory, notify via alarm |
| Buffer overflow | Trigger flush to disk, pause input collection |

## Performance Monitoring

| Metric | Description | Threshold |
|--------|-------------|-----------|
| Processing Rate | CDRs processed per second | > 1000/s |
| Latency | Average processing time per CDR | < 50ms |
| Memory Usage | Heap utilization | < 80% |
| File Queue | Input directory backlog | < 100 files |
| Error Rate | Failed records percentage | < 1% |

## CI/CD Pipeline

```yaml
# .github/workflows/ci-cd.yml
name: CDR Mediation CI/CD

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Build with Maven
        run: mvn clean package
      - name: Run Tests
        run: mvn test
      - name: Integration Tests
        run: mvn verify -Pintegration-tests

  deploy:
    needs: build
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - name: Build Docker Image
        run: docker build -t cdr-mediation:${{ github.sha }} .
      - name: Push to Registry
        run: docker push registry.example.com/cdr-mediation:${{ github.sha }}
```

## Runtime Management

- **Hot Reload**: Configuration changes via `/api/reload` endpoint
- **Graceful Shutdown**: Wait for in-flight processing to complete
- **Health Checks**: `/api/health` for container orchestration
- **JMX Monitoring**: Enable JMX for external monitoring tools

## Metrics Collection (Optional - Prometheus)

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'cdr-mediation'
    static_configs:
      - targets: ['localhost:8080']
```

| Metric | Type | Description |
|--------|------|-------------|
| cdr_processed_total | Counter | Total CDRs processed |
| cdr_errors_total | Counter | Total errors encountered |
| processing_duration_seconds | Histogram | Processing latency |
| buffer_size | Gauge | Current buffer utilization |

## Dependencies (pom.xml additions)

```xml
<!-- JUnit 5 Testing -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.13.2</version>
    <scope>test</scope>
</dependency>

<!-- Jakarta Servlet API EE 11 -->
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <version>7.0.0</version>
    <scope>provided</scope>
</dependency>

<!-- Jakarta JSON EE 11 -->
<dependency>
    <groupId>jakarta.json</groupId>
    <artifactId>jakarta.json-api</artifactId>
    <version>3.2.0</version>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>org.eclipse.parsson</groupId>
    <artifactId>parsson</artifactId>
    <version>1.1.0</version>
    <scope>runtime</scope>
</dependency>

<!-- ASN.1 Decoding (ASN1bean) -->
<dependency>
    <groupId>com.beanit</groupId>
    <artifactId>asn1bean-core</artifactId>
    <version>1.3.2</version>
</dependency>

<!-- CSV Parsing (OpenCSV) -->
<dependency>
    <groupId>com.opencsv</groupId>
    <artifactId>opencsv</artifactId>
    <version>5.9</version>
</dependency>

<!-- YAML Configuration (SnakeYAML) -->
<dependency>
    <groupId>org.yaml</groupId>
    <artifactId>snakeyaml</artifactId>
    <version>2.3</version>
</dependency>
```

## Docker Compose Configuration

```yaml
version: '3.8'

services:
  mediation:
    image: cdr-mediation:latest
    container_name: cdr-mediation
    environment:
      - JAVA_OPTS=-Xmx2g -Xms512m
      - MEDIATION_CONFIG=/config/config.yaml
      - LOGGING_CONFIG=/config/logging.properties
    volumes:
      - ./data/input:/data/input
      - ./data/output:/data/output
      - ./data/error:/data/error
      - ./data/reference:/data/reference
      - ./config:/config
      - mediation-logs:/var/log/mediation
    ports:
      - "8080:8080"
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/api/health"]
      interval: 30s
      timeout: 10s
      retries: 3

volumes:
  mediation-logs:
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| JAVA_OPTS | JVM options | -Xmx1g -Xms256m |
| MEDIATION_CONFIG | Path to config.yaml | /config/config.yaml |
| LOGGING_CONFIG | Path to logging.properties | /config/logging.properties |
| INPUT_DIR | Override input directory | From config |
| OUTPUT_DIR | Override output directory | From config |

## Docker Configuration

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml src/ ./
RUN mvn package -DskipTests

FROM tomcat:11.0-jre21
COPY --from=builder /app/target/*.war /usr/local/tomcat/webapps/
EXPOSE 8080
```

## REST Endpoints (Monitor Servlet)

| Method | Endpoint | Description |
|--------|-----------|-------------|
| GET | /api/status | System status |
| GET | /api/metrics | Processing metrics |
| GET | /api/errors | Recent errors |
| POST | /api/reload | Reload configuration |
| GET | /api/health | Health check |

## Implementation Checklist

### Phase 0: Project Setup
- [ ] Create Maven project structure with pom.xml
- [ ] Add all dependencies to pom.xml
- [ ] Configure logging.properties
- [ ] Create config.yaml with mediation settings
- [ ] Create Docker configuration
- [ ] Create docker-compose.yml

### Phase 1: Domain Model + ASN.1 Schema
- [ ] Define CDR.asn schema file (single schema with all source fields)
- [ ] Generate ASN1bean Java classes
- [ ] Create CdrSource.java enum (MSC=1, SMSC=2, PGW=3)
- [ ] Create CDR.java domain model with all fields

### Phase 2: Input Pipeline
- [ ] Implement CdrCollector with NIO WatchService
- [ ] Implement SourceDetector (extract source from filename pattern)
- [ ] Implement CdrDecoder using ASN1bean (single schema)
- [ ] Implement CdrFilter with configurable rules
- [ ] Implement CdrValidator with source-specific validation

### Phase 3: Processing Pipeline
- [ ] Implement CdrEnricher with CSV lookup
- [ ] Implement DuplicateDetector (dial_a + dial_b + timestamp)
- [ ] Implement CdrAggregator with time windows
- [ ] Implement CdrConsolidator for record merging
- [ ] Implement CdrBuffer for buffering records
- [ ] Implement CdrSorter with configurable keys

### Phase 4: Output Pipeline
- [ ] Implement CsvFormatter for CSV mapping
- [ ] Implement HeaderTrailerGenerator
- [ ] Implement CdrDistributor
- [ ] Implement ErrorHandler with alarms
- [ ] Implement MonitorServlet with REST endpoints

### Phase 5: Testing & Deployment
- [ ] Write unit tests for all modules
- [ ] Write integration tests
- [ ] Build and test Docker image
- [ ] Configure Tomcat deployment
- [ ] Run end-to-end pipeline test

### Success Criteria
- [ ] ASN.1 CDR files successfully decoded
- [ ] Duplicates detected correctly (same dial_a + dial_b + timestamp)
- [ ] CDRs enriched with carrier/region info
- [ ] Aggregated totals computed correctly
- [ ] CSV output matches expected format
- [ ] Header/Trailer generated with accurate counts
- [ ] Errors logged with alarm notifications
- [ ] Monitor API returns correct status
- [ ] Docker image builds successfully
- [ ] End-to-end integration test passes

## Sample Data Formats

### Input Filename Pattern (Source Detection)
```
CDR_<timestamp>_<SOURCE>_<sequence>.asn

Examples:
- CDR_20240115_143022_MSC_001.asn    → Voice CDR from MSC
- CDR_20240115_143025_SMSC_001.asn   → SMS CDR from SMSC
- CDR_20240115_143028_PGW_001.asn    → Data CDR from PGW
```

### Unified ASN.1 Schema (CDR.asn)
```asn
CDR DEFINITIONS ::= BEGIN

CdrRecord ::= SEQUENCE {
    sourceType         INTEGER (1..3),  -- 1=MSC, 2=SMSC, 3=PGW
    recordId           INTEGER,
    timestamp          GeneralizedTime,

    -- Voice (MSC) fields - sourceType=1
    dialA              NumericString (SIZE(1..20)) OPTIONAL,
    dialB              NumericString (SIZE(1..20)) OPTIONAL,
    duration           INTEGER (0..86400) OPTIONAL,
    callType           INTEGER (0..9) OPTIONAL,
    imsi               NumericString (SIZE(15)) OPTIONAL,
    imei               NumericString (SIZE(15)) OPTIONAL,

    -- SMS (SMSC) fields - sourceType=2
    sender             NumericString (SIZE(1..20)) OPTIONAL,
    receiver           NumericString (SIZE(1..20)) OPTIONAL,
    smscNumber         NumericString (SIZE(1..20)) OPTIONAL,
    messageLength      INTEGER (0..160) OPTIONAL,
    messageType        INTEGER (0..5) OPTIONAL,

    -- Data (PGW) fields - sourceType=3
    apn                NumericString (SIZE(1..100)) OPTIONAL,
    bytesIn            INTEGER (0..4294967295) OPTIONAL,
    bytesOut           INTEGER (0..4294967295) OPTIONAL,
    ratType            INTEGER (0..10) OPTIONAL
}

CdrFile ::= SEQUENCE OF CdrRecord
END
```

### Enrichment CSV (subscribers.csv)
```csv
identifier,carrier,region,subscriber_type,status
1234567890,AT&T,Northeast,Premium,active
1234567891,Verizon,Southeast,Standard,active
1234567892,T-Mobile,Southwest,Prepaid,active
```

### Output CSV (with nulls for unused source fields)
```csv
H|2024-01-15 10:00:00|application/csv|3
1|1001|2024-01-15T09:30:00Z|1234567890|1234567891|120|0|123456789012345678|IMEI123456789|null|null|null|null|null|null|null|null|null
2|1002|2024-01-15T09:31:00Z|null|null|null|null|null|null|1234567890|1234567891|+1234567890|80|1|null|null|null|null|null
3|1003|2024-01-15T09:32:00Z|null|null|null|null|123456789012345678|null|null|null|null|null|internet|1024|2048|6
T|3|2048|abc123def456
```

**Column mapping:**
| Col | MSC (Voice) | SMSC (SMS) | PGW (Data) |
|-----|-------------|------------|-------------|
| 1 | sourceType | sourceType | sourceType |
| 2 | recordId | recordId | recordId |
| 3 | timestamp | timestamp | timestamp |
| 4 | dialA | null | null |
| 5 | dialB | null | null |
| 6 | duration | null | null |
| 7 | callType | null | null |
| 8 | imsi | imsi | imsi |
| 9 | imei | null | null |
| 10 | null | sender | null |
| 11 | null | receiver | null |
| 12 | null | smscNumber | null |
| 13 | null | messageLength | null |
| 14 | null | messageType | null |
| 15 | null | null | apn |
| 16 | null | null | bytesIn |
| 17 | null | null | bytesOut |
| 18 | null | null | ratType |

### Aggregated Output
```csv
dial_a,carrier,total_calls,total_duration_seconds
1234567890,AT&T,50,6000
1234567891,Verizon,30,3600
```

### Enrichment CSV (subscribers.csv)
```csv
dial_a,carrier,region,subscriber_type,status
1234567890,AT&T,Northeast,Premium,active
1234567891,Verizon,Southeast,Standard,active
1234567892,T-Mobile,Southwest,Prepaid,active
```

### Output CSV
```csv
H|2024-01-15 10:00:00|application/csv|1000
1234567890|1234567891|2024-01-15 09:30:00|120|Premium|AT&T|Northeast|voice
1234567892|1234567893|2024-01-15 09:31:00|60|Standard|Verizon|Southeast|voice
T|2|180|abc123def456
```

### Aggregated Output
```csv
dial_a,carrier,total_calls,total_duration_seconds
1234567890,AT&T,50,6000
1234567891,Verizon,30,3600
```