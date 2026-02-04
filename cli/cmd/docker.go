package cmd

import (
	"encoding/json"
	"fmt"
	"os"

	"github.com/sdlcraft/cli/client"
	"github.com/spf13/cobra"
)

var dockerCmd = &cobra.Command{
	Use:   "docker",
	Short: "Docker integration commands",
	Long: `Docker commands for building, pushing, and managing containers.

Commands:
  build     Build a Docker image
  push      Push image to registry
  run       Run a container
  compose   Docker Compose operations
  ps        List containers`,
}

var dockerBuildCmd = &cobra.Command{
	Use:   "build",
	Short: "Build a Docker image",
	RunE: func(cmd *cobra.Command, args []string) error {
		contextPath, _ := cmd.Flags().GetString("context")
		imageName, _ := cmd.Flags().GetString("name")
		tag, _ := cmd.Flags().GetString("tag")
		dockerfile, _ := cmd.Flags().GetString("file")

		if contextPath == "" {
			contextPath, _ = os.Getwd()
		}

		c := client.New()
		body, _ := json.Marshal(map[string]interface{}{
			"action":      "build",
			"contextPath": contextPath,
			"imageName":   imageName,
			"tag":         tag,
			"dockerfile":  dockerfile,
		})

		fmt.Printf("🐳 Building image %s:%s...\n", imageName, tag)
		resp, err := c.Post("/api/integration/docker/execute", body)
		if err != nil {
			return fmt.Errorf("failed to build image: %w", err)
		}

		var result map[string]interface{}
		json.Unmarshal(resp, &result)

		if result["success"].(bool) {
			fmt.Printf("✅ Image built: %s:%s\n", imageName, tag)
		} else {
			fmt.Printf("❌ Build failed: %s\n", result["message"])
		}

		return nil
	},
}

var dockerPushCmd = &cobra.Command{
	Use:   "push [image:tag]",
	Short: "Push image to registry",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		image := args[0]
		imageName, tag := parseImageTag(image)

		c := client.New()
		body, _ := json.Marshal(map[string]string{
			"action":    "push",
			"imageName": imageName,
			"tag":       tag,
		})

		fmt.Printf("🚀 Pushing %s:%s...\n", imageName, tag)
		resp, err := c.Post("/api/integration/docker/execute", body)
		if err != nil {
			return fmt.Errorf("failed to push image: %w", err)
		}

		var result map[string]interface{}
		json.Unmarshal(resp, &result)

		if result["success"].(bool) {
			fmt.Printf("✅ Image pushed: %s:%s\n", imageName, tag)
		} else {
			fmt.Printf("❌ Push failed: %s\n", result["message"])
		}

		return nil
	},
}

var dockerRunCmd = &cobra.Command{
	Use:   "run [image]",
	Short: "Run a container",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		image := args[0]
		name, _ := cmd.Flags().GetString("name")
		detach, _ := cmd.Flags().GetBool("detach")
		port, _ := cmd.Flags().GetStringSlice("port")

		portMappings := make(map[string]string)
		for _, p := range port {
			// Parse "host:container" format
			var host, container string
			fmt.Sscanf(p, "%s:%s", &host, &container)
			if container == "" {
				container = host
			}
			portMappings[host] = container
		}

		c := client.New()
		body, _ := json.Marshal(map[string]interface{}{
			"action":        "run",
			"imageName":     image,
			"containerName": name,
			"detached":      detach,
			"portMappings":  portMappings,
		})

		fmt.Printf("🐳 Running container from %s...\n", image)
		resp, err := c.Post("/api/integration/docker/execute", body)
		if err != nil {
			return fmt.Errorf("failed to run container: %w", err)
		}

		var result map[string]interface{}
		json.Unmarshal(resp, &result)

		if result["success"].(bool) {
			data := result["data"].(map[string]interface{})
			fmt.Printf("✅ Container started: %s\n", data["containerId"])
		} else {
			fmt.Printf("❌ Run failed: %s\n", result["message"])
		}

		return nil
	},
}

var dockerComposeCmd = &cobra.Command{
	Use:   "compose [up|down]",
	Short: "Docker Compose operations",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		action := args[0]
		file, _ := cmd.Flags().GetString("file")
		detach, _ := cmd.Flags().GetBool("detach")

		c := client.New()
		
		var apiAction string
		switch action {
		case "up":
			apiAction = "composeUp"
		case "down":
			apiAction = "composeDown"
		default:
			return fmt.Errorf("unknown compose action: %s (use 'up' or 'down')", action)
		}

		body, _ := json.Marshal(map[string]interface{}{
			"action":      apiAction,
			"composePath": file,
			"detached":    detach,
		})

		fmt.Printf("🐳 Docker compose %s...\n", action)
		resp, err := c.Post("/api/integration/docker/execute", body)
		if err != nil {
			return fmt.Errorf("failed to run compose: %w", err)
		}

		var result map[string]interface{}
		json.Unmarshal(resp, &result)

		if result["success"].(bool) {
			fmt.Printf("✅ Compose %s completed\n", action)
		} else {
			fmt.Printf("❌ Compose %s failed: %s\n", action, result["message"])
		}

		return nil
	},
}

var dockerPsCmd = &cobra.Command{
	Use:   "ps",
	Short: "List containers",
	RunE: func(cmd *cobra.Command, args []string) error {
		all, _ := cmd.Flags().GetBool("all")

		c := client.New()
		body, _ := json.Marshal(map[string]interface{}{
			"action": "listContainers",
			"all":    all,
		})

		resp, err := c.Post("/api/integration/docker/execute", body)
		if err != nil {
			return fmt.Errorf("failed to list containers: %w", err)
		}

		var result map[string]interface{}
		json.Unmarshal(resp, &result)

		if result["success"].(bool) {
			data := result["data"].(map[string]interface{})
			containers := data["containers"].([]interface{})
			
			fmt.Println("🐳 Containers:")
			fmt.Printf("%-12s %-30s %-20s %s\n", "ID", "IMAGE", "STATUS", "NAME")
			fmt.Println("─────────────────────────────────────────────────────────────────────────")
			
			for _, c := range containers {
				container := c.(map[string]interface{})
				fmt.Printf("%-12s %-30s %-20s %s\n",
					container["id"],
					truncateString(container["image"].(string), 28),
					container["status"],
					container["name"])
			}
		} else {
			fmt.Printf("❌ %s\n", result["message"])
		}

		return nil
	},
}

func parseImageTag(image string) (string, string) {
	for i := len(image) - 1; i >= 0; i-- {
		if image[i] == ':' {
			return image[:i], image[i+1:]
		}
		if image[i] == '/' {
			break
		}
	}
	return image, "latest"
}

func init() {
	rootCmd.AddCommand(dockerCmd)
	dockerCmd.AddCommand(dockerBuildCmd)
	dockerCmd.AddCommand(dockerPushCmd)
	dockerCmd.AddCommand(dockerRunCmd)
	dockerCmd.AddCommand(dockerComposeCmd)
	dockerCmd.AddCommand(dockerPsCmd)

	// Build flags
	dockerBuildCmd.Flags().StringP("context", "c", "", "Build context path")
	dockerBuildCmd.Flags().StringP("name", "n", "", "Image name (required)")
	dockerBuildCmd.Flags().StringP("tag", "t", "latest", "Image tag")
	dockerBuildCmd.Flags().StringP("file", "f", "", "Dockerfile path")
	dockerBuildCmd.MarkFlagRequired("name")

	// Run flags
	dockerRunCmd.Flags().String("name", "", "Container name")
	dockerRunCmd.Flags().BoolP("detach", "d", true, "Run in background")
	dockerRunCmd.Flags().StringSliceP("port", "p", nil, "Port mappings (host:container)")

	// Compose flags
	dockerComposeCmd.Flags().StringP("file", "f", "", "Compose file path")
	dockerComposeCmd.Flags().BoolP("detach", "d", true, "Run in background")

	// Ps flags
	dockerPsCmd.Flags().BoolP("all", "a", false, "Show all containers")
}

