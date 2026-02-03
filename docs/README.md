# SDLCraft Documentation

Welcome to the SDLCraft documentation! This directory contains comprehensive guides for users and developers.

## Documentation Index

### For Users

- **[User Guide](user-guide.md)** - Complete guide for using SDLCraft CLI
  - Getting started tutorial
  - Command reference with examples
  - Configuration options
  - Troubleshooting common issues
  - Best practices

- **[API Reference](api-reference.md)** - Complete REST API documentation
  - All endpoints with request/response examples
  - Error codes and messages
  - Authentication (future)
  - Rate limiting (future)

### For Developers

- **[Developer Guide](developer-guide.md)** - Guide for extending SDLCraft
  - Creating custom intents
  - Implementing custom agents
  - Integrating LLM providers
  - Implementing vector store backends
  - Plugin development
  - Testing extensions

- **[Architecture](architecture.md)** - System architecture and design
  - High-level architecture
  - Component interactions
  - Data flow diagrams
  - Technology stack

- **[Integration Architecture](integration-architecture.md)** - External system and AI integrations
  - Jira, GitHub, Jenkins, CI/CD integrations
  - AI/LLM integration (OpenAI, Anthropic, local models)
  - Vector store integration (Pinecone, Weaviate)
  - Data collection and aggregation
  - Implementation status and roadmap

### Setup and Development

- **[Setup Guide](setup-guide.md)** - Installation and configuration
  - Prerequisites
  - Installation steps
  - Database setup
  - Environment configuration

- **[Development Guide](development.md)** - Development workflow
  - Building from source
  - Running tests
  - Contributing guidelines
  - Code style

## Example Implementations

The `examples/` directory contains working examples for extending SDLCraft:

### Custom Intent Example
- **Location**: `examples/custom-intent/`
- **Description**: Deploy intent for application deployment
- **Learn**: How to create custom high-level commands

### Custom Agent Example
- **Location**: `examples/custom-agent/`
- **Description**: Database migration agent
- **Learn**: How to implement PLAN → ACT → OBSERVE → REFLECT pattern

### LLM Provider Example
- **Location**: `examples/llm-provider/`
- **Description**: OpenAI integration
- **Learn**: How to integrate different AI models

### Vector Store Example
- **Location**: `examples/vector-store/`
- **Description**: Pinecone integration
- **Learn**: How to implement semantic search backends

## Quick Links

### Getting Started
1. Read the [User Guide](user-guide.md) to learn basic usage
2. Follow the [Setup Guide](setup-guide.md) to install SDLCraft
3. Try the examples in the User Guide

### Extending SDLCraft
1. Read the [Developer Guide](developer-guide.md)
2. Review the [Architecture](architecture.md) document
3. Study the example implementations in `examples/`
4. Check the [API Reference](api-reference.md) for backend integration

### Contributing
1. Read `CONTRIBUTING.md` in the project root
2. Follow the [Development Guide](development.md)
3. Submit pull requests with tests and documentation

## Support

- **GitHub Issues**: Report bugs and request features
- **GitHub Discussions**: Ask questions and share tips
- **Documentation**: Check these guides first

## Version

This documentation is for SDLCraft v1.0.0

Last updated: January 24, 2026
