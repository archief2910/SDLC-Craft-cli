# Setup Guide for SDLCraft CLI

This guide will help you set up your development environment for SDLCraft CLI.

## Prerequisites Installation

### 1. Install Go (1.21+)

**Windows**:
1. Download installer from https://go.dev/dl/
2. Run the installer
3. Verify installation:
   ```powershell
   go version
   ```

**macOS**:
```bash
brew install go
go version
```

**Linux**:
```bash
wget https://go.dev/dl/go1.21.5.linux-amd64.tar.gz
sudo tar -C /usr/local -xzf go1.21.5.linux-amd64.tar.gz
export PATH=$PATH:/usr/local/go/bin
go version
```

### 2. Install Java (17+)

**Windows**:
1. Download from https://adoptium.net/
2. Run the installer
3. Verify installation:
   ```powershell
   java -version
   ```

**macOS**:
```bash
brew install openjdk@17
java -version
```

**Linux**:
```bash
sudo apt update
sudo apt install openjdk-17-jdk
java -version
```

### 3. Install Maven (3.8+)

**Windows**:
1. Download from https://maven.apache.org/download.cgi
2. Extract to `C:\Program Files\Apache\maven`
3. Add to PATH: `C:\Program Files\Apache\maven\bin`
4. Verify installation:
   ```powershell
   mvn --version
   ```

**macOS**:
```bash
brew install maven
mvn --version
```

**Linux**:
```bash
sudo apt update
sudo apt install maven
mvn --version
```

### 4. Install PostgreSQL (14+)

**Windows**:
1. Download from https://www.postgresql.org/download/windows/
2. Run the installer
3. Remember the password you set for the postgres user
4. Verify installation:
   ```powershell
   psql --version
   ```

**macOS**:
```bash
brew install postgresql@14
brew services start postgresql@14
psql --version
```

**Linux**:
```bash
sudo apt update
sudo apt install postgresql postgresql-contrib
sudo systemctl start postgresql
psql --version
```

## Project Setup

### 1. Clone Repository

```bash
git clone https://github.com/sdlcraft/sdlcraft-cli.git
cd sdlcraft-cli
```

### 2. Set Up Database

```bash
# Connect to PostgreSQL (use your postgres password)
psql -U postgres

# In psql prompt:
CREATE DATABASE sdlcraft;
CREATE USER sdlcraft WITH PASSWORD 'sdlcraft';
GRANT ALL PRIVILEGES ON DATABASE sdlcraft TO sdlcraft;
\q
```

Verify connection:
```bash
psql -U sdlcraft -d sdlcraft -c "SELECT version();"
```

### 3. Build CLI

```bash
cd cli
go mod download
go build -o sdlc
```

Test:
```bash
./sdlc --help
```

Run tests:
```bash
go test ./... -v
```

### 4. Build Backend

```bash
cd backend
mvn clean install
```

This will:
- Download all dependencies
- Compile the code
- Run tests
- Create executable JAR

### 5. Run Backend

```bash
mvn spring-boot:run
```

The backend will:
- Start on http://localhost:8080
- Automatically run Flyway database migrations
- Create all necessary tables

Verify:
```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{"status":"UP"}
```

### 6. Verify Database Schema

```bash
psql -U sdlcraft -d sdlcraft
```

In psql:
```sql
-- List all tables
\dt

-- Check SDLC state table
\d sdlc_state

-- Check intent definitions
SELECT name, description, default_risk_level FROM intent_definitions;

-- Exit
\q
```

You should see:
- sdlc_state
- command_executions
- audit_logs
- agent_executions
- project_context
- intent_definitions

## IDE Setup

### Visual Studio Code (for CLI)

1. Install Go extension
2. Open `cli` folder
3. Install recommended extensions when prompted
4. Configure settings:
   ```json
   {
     "go.useLanguageServer": true,
     "go.lintTool": "golangci-lint",
     "go.formatTool": "gofmt"
   }
   ```

### IntelliJ IDEA (for Backend)

1. Open `backend` folder as Maven project
2. Wait for Maven to download dependencies
3. Enable annotation processing (for Lombok):
   - Settings → Build → Compiler → Annotation Processors
   - Check "Enable annotation processing"
4. Install Lombok plugin if prompted

## Troubleshooting

### Go: "go: command not found"

**Solution**: Add Go to PATH
- Windows: Add `C:\Go\bin` to PATH
- macOS/Linux: Add `export PATH=$PATH:/usr/local/go/bin` to `~/.bashrc` or `~/.zshrc`

### Maven: "mvn: command not found"

**Solution**: Add Maven to PATH
- Windows: Add `C:\Program Files\Apache\maven\bin` to PATH
- macOS/Linux: Add `export PATH=$PATH:/opt/maven/bin` to `~/.bashrc` or `~/.zshrc`

### PostgreSQL: "psql: command not found"

**Solution**: Add PostgreSQL to PATH
- Windows: Add `C:\Program Files\PostgreSQL\14\bin` to PATH
- macOS: `brew link postgresql@14`
- Linux: Usually already in PATH after installation

### Database: "FATAL: password authentication failed"

**Solution**: 
1. Check password in `backend/src/main/resources/application.yml`
2. Reset PostgreSQL password:
   ```bash
   psql -U postgres
   ALTER USER sdlcraft WITH PASSWORD 'sdlcraft';
   ```

### Backend: "Failed to configure a DataSource"

**Solution**:
1. Verify PostgreSQL is running:
   - Windows: Check Services
   - macOS: `brew services list`
   - Linux: `sudo systemctl status postgresql`
2. Verify database exists: `psql -U postgres -l | grep sdlcraft`
3. Check connection string in `application.yml`

### CLI: "cannot find package"

**Solution**:
```bash
cd cli
go mod tidy
go mod download
```

### Backend: "Cannot resolve symbol"

**Solution**:
1. Reimport Maven project: Right-click `pom.xml` → Maven → Reimport
2. Invalidate caches: File → Invalidate Caches / Restart
3. Clean and rebuild: `mvn clean install`

## Next Steps

Once setup is complete:

1. Read [architecture.md](architecture.md) to understand the system design
2. Read [development.md](development.md) for development workflows
3. Check [tasks.md](../.kiro/specs/sdlcraft-cli/tasks.md) for current implementation status
4. Start with Task 2: Implement CLI command parser and grammar

## Verification Checklist

- [ ] Go installed and in PATH (`go version`)
- [ ] Java installed and in PATH (`java -version`)
- [ ] Maven installed and in PATH (`mvn --version`)
- [ ] PostgreSQL installed and running (`psql --version`)
- [ ] Database created (`psql -U sdlcraft -d sdlcraft`)
- [ ] CLI builds successfully (`cd cli && go build`)
- [ ] CLI tests pass (`cd cli && go test ./...`)
- [ ] Backend builds successfully (`cd backend && mvn clean install`)
- [ ] Backend starts successfully (`mvn spring-boot:run`)
- [ ] Backend health check passes (`curl http://localhost:8080/actuator/health`)
- [ ] Database schema created (7 tables visible in psql)

If all items are checked, you're ready to start development! 🚀
