# SDLCraft CLI Component

This directory contains the Go-based CLI component of SDLCraft.

## Structure

```
cli/
├── main.go              # Entry point with Cobra setup
├── cmd/                 # Command implementations (to be added)
├── parser/              # Command parsing logic (to be added)
├── repair/              # Deterministic repair engine (to be added)
├── client/              # Backend communication (to be added)
└── renderer/            # Output rendering (to be added)
```

## Building

```bash
go mod download
go build -o sdlc
```

## Running

```bash
./sdlc --help
```

## Testing

```bash
# Unit tests
go test ./... -v

# Property-based tests
go test -tags=property ./... -v
```

## Dependencies

- **Cobra**: CLI framework for command structure and parsing
- **gopter**: Property-based testing (to be added)

## Design Principles

1. **Local-First**: Deterministic operations (parsing, repair) happen without network calls
2. **Fast**: Command parsing and repair should complete in < 50ms
3. **User-Friendly**: Clear error messages, helpful suggestions, color-coded output
4. **Testable**: All components have unit tests and property tests

## Next Steps

See [tasks.md](../.kiro/specs/sdlcraft-cli/tasks.md) for implementation tasks:
- Task 2: Implement command parser and grammar
- Task 3: Implement deterministic command repair engine
- Task 4: Implement backend client
- Task 5: Implement output renderer
