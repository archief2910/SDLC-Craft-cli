package cmd

import (
	"encoding/json"
	"fmt"
	"os"

	"github.com/spf13/cobra"
)

var indexCmd = &cobra.Command{
	Use:   "index",
	Short: "Index the codebase for RAG-based AI search",
	Long: `Index the current codebase into the vector store for RAG-based AI search.

This command will:
1. Scan all source files in the project
2. Chunk files into manageable pieces
3. Generate embeddings for each chunk
4. Store embeddings in Pinecone vector store

After indexing, you can use 'sdlc ai' for intelligent code search and modifications.

Examples:
  sdlc index                    # Index current directory
  sdlc index --path /path/to/project  # Index specific project`,
	Run: runIndex,
}

var indexPath string

func init() {
	rootCmd.AddCommand(indexCmd)
	indexCmd.Flags().StringVarP(&indexPath, "path", "p", "", "Path to the project to index (default: current directory)")
}

func runIndex(cmd *cobra.Command, args []string) {
	// Get project path
	projectPath := indexPath
	if projectPath == "" {
		var err error
		projectPath, err = os.Getwd()
		if err != nil {
			fmt.Fprintf(os.Stderr, "Error getting current directory: %v\n", err)
			os.Exit(1)
		}
	}
	
	// Get backend URL
	backendURL := os.Getenv("BACKEND_URL")
	if backendURL == "" {
		backendURL = "http://localhost:8080"
	}
	
	// Check if backend is available
	if !checkBackendAvailable(backendURL) {
		fmt.Fprintf(os.Stderr, "❌ Backend is not available at %s\n", backendURL)
		fmt.Fprintf(os.Stderr, "   Please start the backend with: cd backend && mvn spring-boot:run\n")
		os.Exit(1)
	}
	
	fmt.Println("🔍 Indexing codebase for RAG search...")
	fmt.Printf("   Project: %s\n\n", projectPath)
	
	// Send index request
	request := map[string]string{
		"projectPath": projectPath,
	}
	
	jsonData, err := json.Marshal(request)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error: %v\n", err)
		os.Exit(1)
	}
	
	resp, err := makeHTTPRequest(backendURL+"/api/rag/index", "POST", jsonData)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error indexing codebase: %v\n", err)
		os.Exit(1)
	}
	
	var result map[string]interface{}
	if err := json.Unmarshal(resp, &result); err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error parsing response: %v\n", err)
		os.Exit(1)
	}
	
	if success, ok := result["success"].(bool); ok && success {
		fmt.Println("✅ Codebase indexed successfully!")
		fmt.Println("\nYou can now use:")
		fmt.Println("  sdlc ai \"your question or task\"")
		fmt.Println("\nExamples:")
		fmt.Println("  sdlc ai \"explain how authentication works\"")
		fmt.Println("  sdlc ai \"add error handling to the API endpoints\"")
		fmt.Println("  sdlc ai \"refactor the database queries for better performance\"")
	} else {
		fmt.Println("⚠️ Indexing completed with issues")
		if msg, ok := result["message"].(string); ok {
			fmt.Printf("   %s\n", msg)
		}
		if errMsg, ok := result["error"].(string); ok {
			fmt.Printf("   Error: %s\n", errMsg)
		}
	}
}






