package main

import (
	"fmt"
	"os"

	"github.com/sdlcraft/cli/cmd"
)

func main() {
	if err := cmd.GetRootCmd().Execute(); err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		os.Exit(1)
	}
}
