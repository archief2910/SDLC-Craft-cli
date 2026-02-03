# SDLCraft CLI - Setup and Testing Guide

## Overview
This guide will help you install all required dependencies and test the SDLCraft CLI system on Windows.

---

## Prerequisites

### 1. Java Development Kit (JDK) 17+

**Check if installed:**
```cmd
java -version
```

**Install if needed:**
- Download from: https://adoptium.net/ (Eclipse Temurin)
- Or use: https://www.oracle.com/java/technologies/downloads/
- Choose JDK 17 or later
- Add to PATH during installation

**Verify installation:**
```cmd
java -version
javac -version
```

### 2. Maven

**Check if installed:**
```cmd
mvn -version
```

**Install if needed:**
- Download from: https://maven.apache.org/download.cgi
- Extract to `C:\Program Files\Maven`
- Add to PATH: `C:\Program Files\Maven\bin`
- Set `JAVA_HOME` environment variable to JDK location

**Verify installation:**
```cmd
mvn -version
```

### 3. Go (Golang)

**Check if installed:**
```cmd
go version
```

**Install if needed:**
- Download from: https://go.dev/dl/
- Run installer (adds to PATH automatically)
- Recommended version: 1.21 or later

**Verify installation:**
```cmd
go version
```

### 4. PostgreSQL

**Check if installed:**
```cmd
psql --version
```

**Install if needed:**
- Download from: https://www.postgresql.org/download/windows/
- Run installer
- Remember the password you set for `postgres` user
- Default port: 5432

**Verify installation:**
```cmd
psql --version
```

**Create database:**
```cmd
# Connect to PostgreSQL
psql -U postgres

# In psql prompt:
CREATE DATABASE sdlcraft;
CREATE USER sdlcraft_user WITH PASSWORD 'sdlcraft_pass';
GRANT ALL PRIVILEGES ON DATABASE sdlcraft TO sdlcraft_user;
\q
```

---

## Project Setup

### 1. Backend Setup (Spring Boot)

**Navigate to backend directory:**
```cmd
cd backend
```

**Update database configuration:**
Edit `src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/sdlcraft
    username: sdlcraft_user
    password: sdlcraft_pass
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    baseline-on-migrate: true
```

**Install dependencies:**
```cmd
mvn clean install -DskipTests
```

**Run database migrations:**
```cmd
mvn flyway:migrate
```

**Build the project:**
```cmd
mvn clean package -DskipTests
```

**Run the backend:**
```cmd
mvn spring-boot:run
```

The backend should start on `http://localhost:8080`

**Verify backend is running:**
Open browser or use curl:
```cmd
curl http://localhost:8080/actuator/health
```

Should return: `{"status":"UP"}`

### 2. CLI Setup (Go)

**Navigate to CLI directory:**
```cmd
cd cli
```

**Install dependencies:**
```cmd
go mod download
go mod tidy
```

**Build the CLI:**
```cmd
go build -o sdlc.exe .
```

**Verify CLI build:**
```cmd
.\sdlc.exe --help
```

---

## Running Tests

### Backend Tests

**Run all backend tests:**
```cmd
cd backend
mvn test
```

**Run specific test class:**
```cmd
mvn test -Dtest=DefaultSDLCStateMachineTest
```

**Run tests with coverage:**
```cmd
mvn clean test jacoco:report
```

Coverage report will be in: `target/site/jacoco/index.html`

### CLI Tests

**Run all Go tests:**
```cmd
cd cli
go test ./...
```

**Run tests with verbose output:**
```cmd
go test -v ./...
```

**Run tests for specific package:**
```cmd
go test -v ./parser
go test -v ./renderer
go test -v ./client
```

**Run tests with coverage:**
```cmd
go test -cover ./...
```

**Generate coverage report:**
```cmd
go test -coverprofile=coverage.out ./...
go tool cover -html=coverage.out
```

---

## Manual Testing

### 1. Test Backend Endpoints

**Start the backend:**
```cmd
cd backend
mvn spring-boot:run
```

**Test health endpoint:**
```cmd
curl http://localhost:8080/actuator/health
```

**Test intent inference (if REST controller exists):**
```cmd
curl -X POST http://localhost:8080/api/intent/infer ^
  -H "Content-Type: application/json" ^
  -d "{\"rawCommand\":\"sdlc status\",\"userId\":\"test-user\",\"projectId\":\"test-project\"}"
```

**Test state query:**
```cmd
curl http://localhost:8080/api/state/test-project
```

### 2. Test CLI Components

**Test parser:**
```cmd
cd cli
go run main.go parse "sdlc status"
```

**Test with repair:**
```cmd
go run main.go parse "sdlc stauts"
```

**Test backend connection:**
```cmd
go run main.go test-connection
```

### 3. Integration Test

**Terminal 1 - Start backend:**
```cmd
cd backend
mvn spring-boot:run
```

**Terminal 2 - Run CLI:**
```cmd
cd cli
.\sdlc.exe status --project test-project
```

---

## Database Verification

**Connect to database:**
```cmd
psql -U sdlcraft_user -d sdlcraft
```

**Check tables:**
```sql
\dt

-- Should show:
-- sdlc_state
-- command_executions
-- audit_logs
-- agent_executions
-- project_context
-- intent_definitions
```

**Query data:**
```sql
-- Check SDLC states
SELECT * FROM sdlc_state;

-- Check intent definitions
SELECT * FROM intent_definitions;

-- Check audit logs
SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 10;

-- Check agent executions
SELECT * FROM agent_executions ORDER BY start_time DESC LIMIT 10;
```

---

## Troubleshooting

### Backend Issues

**Issue: Port 8080 already in use**
```
Solution: Change port in application.yml:
server:
  port: 8081
```

**Issue: Database connection failed**
```
Solution: 
1. Check PostgreSQL is running: services.msc
2. Verify credentials in application.yml
3. Test connection: psql -U sdlcraft_user -d sdlcraft
```

**Issue: Flyway migration failed**
```
Solution:
1. Check migration files in src/main/resources/db/migration
2. Reset database: DROP DATABASE sdlcraft; CREATE DATABASE sdlcraft;
3. Run migrations again: mvn flyway:migrate
```

**Issue: Maven build failed**
```
Solution:
1. Check Java version: java -version (must be 17+)
2. Clear Maven cache: mvn clean
3. Update dependencies: mvn clean install -U
```

### CLI Issues

**Issue: Go build failed**
```
Solution:
1. Check Go version: go version (must be 1.21+)
2. Clean module cache: go clean -modcache
3. Reinstall dependencies: go mod download
```

**Issue: Tests failing**
```
Solution:
1. Check if backend is running (for integration tests)
2. Run tests individually to isolate issue
3. Check test output for specific errors
```

### Database Issues

**Issue: PostgreSQL not starting**
```
Solution:
1. Check Windows Services: services.msc
2. Start "postgresql-x64-XX" service
3. Check logs: C:\Program Files\PostgreSQL\XX\data\log
```

**Issue: Permission denied**
```
Solution:
1. Grant permissions: GRANT ALL PRIVILEGES ON DATABASE sdlcraft TO sdlcraft_user;
2. Grant schema permissions: GRANT ALL ON SCHEMA public TO sdlcraft_user;
```

---

## Development Workflow

### 1. Start Development Environment

**Terminal 1 - Database:**
```cmd
# PostgreSQL should be running as Windows service
# Check: services.msc
```

**Terminal 2 - Backend:**
```cmd
cd backend
mvn spring-boot:run
```

**Terminal 3 - CLI Development:**
```cmd
cd cli
go run main.go [command]
```

### 2. Make Changes

**Backend changes:**
1. Edit Java files
2. Spring Boot will auto-reload (if using spring-boot-devtools)
3. Or restart: Ctrl+C, then `mvn spring-boot:run`

**CLI changes:**
1. Edit Go files
2. Rebuild: `go build -o sdlc.exe .`
3. Or run directly: `go run main.go [command]`

### 3. Run Tests

**After backend changes:**
```cmd
cd backend
mvn test
```

**After CLI changes:**
```cmd
cd cli
go test ./...
```

---

## Quick Start Commands

**Complete setup from scratch:**
```cmd
# 1. Create database
psql -U postgres -c "CREATE DATABASE sdlcraft;"
psql -U postgres -c "CREATE USER sdlcraft_user WITH PASSWORD 'sdlcraft_pass';"
psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE sdlcraft TO sdlcraft_user;"

# 2. Setup backend
cd backend
mvn clean install -DskipTests
mvn flyway:migrate
mvn spring-boot:run

# 3. In new terminal - Setup CLI
cd cli
go mod download
go build -o sdlc.exe .

# 4. Run tests
cd backend
mvn test

cd cli
go test ./...
```

**Daily development:**
```cmd
# Start backend
cd backend
mvn spring-boot:run

# In new terminal - Use CLI
cd cli
go run main.go status
```

---

## Next Steps

Once everything is installed and running:

1. **Explore the codebase:**
   - Backend: `backend/src/main/java/com/sdlcraft/backend/`
   - CLI: `cli/`
   - Tests: Look for `*Test.java` and `*_test.go` files

2. **Run example commands:**
   - `sdlc status`
   - `sdlc analyze security`
   - `sdlc improve performance`

3. **Check the documentation:**
   - `README.md` - Project overview
   - `docs/architecture.md` - System architecture
   - `docs/development.md` - Development guide
   - `TASKS_*_COMPLETE.md` - Implementation details

4. **Add REST controllers** (if needed):
   - Create controllers in `backend/src/main/java/com/sdlcraft/backend/controller/`
   - Wire up services and orchestrator
   - Test with curl or Postman

5. **Implement CLI main.go** (if needed):
   - Wire up parser, repair engine, renderer, backend client
   - Add command handlers
   - Test end-to-end flow

---

## Useful Commands Reference

**Maven:**
```cmd
mvn clean                    # Clean build artifacts
mvn compile                  # Compile code
mvn test                     # Run tests
mvn package                  # Build JAR
mvn spring-boot:run          # Run application
mvn dependency:tree          # Show dependencies
```

**Go:**
```cmd
go build                     # Build executable
go run main.go               # Run without building
go test ./...                # Run all tests
go mod tidy                  # Clean up dependencies
go fmt ./...                 # Format code
```

**PostgreSQL:**
```cmd
psql -U postgres             # Connect as postgres user
psql -U sdlcraft_user -d sdlcraft  # Connect to sdlcraft DB
\l                           # List databases
\dt                          # List tables
\d table_name                # Describe table
\q                           # Quit
```

---

## Support

If you encounter issues:

1. Check the troubleshooting section above
2. Review error messages carefully
3. Check logs:
   - Backend: Console output
   - PostgreSQL: `C:\Program Files\PostgreSQL\XX\data\log`
4. Verify all prerequisites are installed correctly
5. Ensure all services are running

The system is now ready for development and testing!
