package main

import (
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"aether-agent/client"
	"aether-agent/config"
	"aether-agent/runner"
	"aether-agent/scanner"
	"aether-agent/server"
)

func main() {
	if len(os.Args) < 2 {
		printUsage()
		os.Exit(1)
	}

	command := os.Args[1]
	cwd, _ := os.Getwd()

	switch command {
	case "pair":
		runPair(cwd, os.Args[2:])
	case "start":
		runStart(cwd, os.Args[2:])
	case "pause":
		runPause(cwd)
	case "resume":
		runResume(cwd)
	case "status":
		runStatus(cwd)
	case "disconnect":
		runDisconnect(cwd)
	case "run":
		runCommand(cwd, os.Args[2:])
	default:
		fmt.Fprintf(os.Stderr, "Unknown command: %s\n", command)
		printUsage()
		os.Exit(1)
	}
}

func printUsage() {
	fmt.Print(`Aether Local Project Agent (aether-agent)

Usage:
  aether-agent <command> [options]

Commands:
  pair         Pair this local directory with an Aether project via device code flow
  start        Start background file watcher and synchronization daemon
  pause        Pause background synchronization
  resume       Resume background synchronization
  status       Display current agent and project pairing status
  disconnect   Disconnect and clear local agent credentials
  run -- <cmd> Execute a command locally and track verification evidence

Options:
  --local-only Run in local-only mode (0 network calls, serves localhost:8124)
`)
}

func runPair(cwd string, args []string) {
	fs := flag.NewFlagSet("pair", flag.ExitOnError)
	serverURL := fs.String("server", "http://localhost:8080", "Aether backend server URL")
	projectID := fs.String("project", "", "Target Project UUID")
	fs.Parse(args)

	if *projectID == "" {
		fmt.Fprintln(os.Stderr, "Error: -project <UUID> is required for pairing.")
		os.Exit(1)
	}

	apiClient := client.NewClient()
	fmt.Println("Initiating device pairing flow...")
	initResp, err := apiClient.InitPair(*serverURL, *projectID)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Failed to initiate pairing: %v\n", err)
		os.Exit(1)
	}

	fmt.Printf("\nPAIRING CODE: %s\n", initResp.UserCode)
	fmt.Printf("Please confirm pairing in the Aether Web UI at:\n  %s%s\n\n", "http://localhost:5173", initResp.VerificationUri)
	fmt.Println("Waiting for confirmation in browser (timeout: 15 minutes)...")

	// Poll until approved
	ticker := time.NewTicker(2 * time.Second)
	defer ticker.Stop()
	timeout := time.After(time.Duration(initResp.ExpiresIn) * time.Second)

	for {
		select {
		case <-timeout:
			fmt.Fprintln(os.Stderr, "Pairing timed out. Please try again.")
			os.Exit(1)
		case <-ticker.C:
			pollResp, err := apiClient.PollPair(*serverURL, initResp.DeviceCode)
			if err != nil {
				continue
			}
			if pollResp.Status == "ACTIVE" {
				cfg := &config.AgentConfig{
					ServerURL:   *serverURL,
					ProjectID:   *projectID,
					DeviceID:    pollResp.DeviceID,
					DeviceToken: pollResp.DeviceToken,
					LocalPath:   cwd,
					Paused:      false,
					LocalOnly:   false,
					LocalPort:   8124,
				}
				if err := cfg.Save(cwd); err != nil {
					fmt.Fprintf(os.Stderr, "Failed to save configuration: %v\n", err)
					os.Exit(1)
				}
				fmt.Printf("\nPairing successful! Device is connected to project %s.\n", *projectID)
				fmt.Println("Run 'aether-agent start' to begin synchronization.")
				return
			}
		}
	}
}

func runStart(cwd string, args []string) {
	fs := flag.NewFlagSet("start", flag.ExitOnError)
	localOnly := fs.Bool("local-only", false, "Run in local-only mode with 0 network calls")
	port := fs.Int("port", 8124, "Port for local HTTP server in local-only mode")
	fs.Parse(args)

	cfg, _ := config.LoadConfig(cwd)
	fileScanner := scanner.NewScanner(cwd)

	if *localOnly || cfg.LocalOnly {
		fmt.Println("[Local-Only Mode] Starting local agent without external network connections...")
		localSrv := server.NewLocalServer(*port, fileScanner)
		// Perform initial scan
		files, _ := fileScanner.Scan()
		fmt.Printf("[Local-Only] Indexed %d local project files.\n", len(files))
		if err := localSrv.Start(); err != nil {
			fmt.Fprintf(os.Stderr, "Local server stopped: %v\n", err)
		}
		return
	}

	if cfg.DeviceToken == "" {
		fmt.Fprintln(os.Stderr, "Agent is not paired with any project. Run 'aether-agent pair -project <UUID>' first, or use '--local-only'.")
		os.Exit(1)
	}

	apiClient := client.NewClient()
	fmt.Printf("Starting Aether Agent daemon for project %s...\n", cfg.ProjectID)

	// Initial scan
	files, err := fileScanner.Scan()
	if err == nil {
		fmt.Printf("Initial scan: indexed %d files. Syncing to Aether...\n", len(files))
		if err := apiClient.SyncFiles(cfg.ServerURL, cfg.DeviceToken, files); err != nil {
			fmt.Printf("Initial sync notice: %v\n", err)
		} else {
			fmt.Println("Initial sync completed successfully.")
		}
	}

	// Watcher loop (debounced periodic rescan and heartbeat)
	heartbeatTicker := time.NewTicker(30 * time.Second)
	scanTicker := time.NewTicker(5 * time.Second)
	defer heartbeatTicker.Stop()
	defer scanTicker.Stop()

	fmt.Println("Agent daemon running. Press Ctrl+C to stop.")

	for {
		select {
		case <-heartbeatTicker.C:
			currentCfg, _ := config.LoadConfig(cwd)
			if !currentCfg.Paused {
				if err := apiClient.Heartbeat(cfg.ServerURL, cfg.DeviceToken); err != nil {
					fmt.Printf("[Heartbeat warning] %v\n", err)
				}
			}
		case <-scanTicker.C:
			currentCfg, _ := config.LoadConfig(cwd)
			if currentCfg.Paused {
				continue
			}
			newFiles, err := fileScanner.Scan()
			if err == nil && len(newFiles) > 0 {
				_ = apiClient.SyncFiles(cfg.ServerURL, cfg.DeviceToken, newFiles)
			}
		}
	}
}

func runPause(cwd string) {
	cfg, err := config.LoadConfig(cwd)
	if err != nil {
		fmt.Fprintln(os.Stderr, "No active agent configuration found.")
		os.Exit(1)
	}
	cfg.Paused = true
	cfg.Save(cwd)
	fmt.Println("Aether Agent synchronization PAUSED.")
}

func runResume(cwd string) {
	cfg, err := config.LoadConfig(cwd)
	if err != nil {
		fmt.Fprintln(os.Stderr, "No active agent configuration found.")
		os.Exit(1)
	}
	cfg.Paused = false
	cfg.Save(cwd)
	fmt.Println("Aether Agent synchronization RESUMED.")
}

func runStatus(cwd string) {
	cfg, err := config.LoadConfig(cwd)
	if err != nil {
		fmt.Println("Status: NOT PAIRED (no configuration in current directory)")
		return
	}

	fileScanner := scanner.NewScanner(cwd)
	files, _ := fileScanner.Scan()

	fmt.Printf("Aether Local Agent Status\n")
	fmt.Printf("=========================\n")
	fmt.Printf("Project ID:   %s\n", cfg.ProjectID)
	fmt.Printf("Server URL:   %s\n", cfg.ServerURL)
	fmt.Printf("Local Path:   %s\n", cwd)
	fmt.Printf("Paused:       %t\n", cfg.Paused)
	fmt.Printf("Local Only:   %t\n", cfg.LocalOnly)
	fmt.Printf("Indexed Files: %d\n", len(files))
}

func runDisconnect(cwd string) {
	if err := config.ClearConfig(cwd); err != nil {
		fmt.Fprintf(os.Stderr, "Failed to remove agent configuration: %v\n", err)
		os.Exit(1)
	}
	fmt.Println("Agent disconnected and local credentials removed.")
}

func runCommand(cwd string, args []string) {
	// Look for separator "--"
	var cmdArgs []string
	foundSep := false
	for i, arg := range args {
		if arg == "--" {
			cmdArgs = args[i+1:]
			foundSep = true
			break
		}
	}
	if !foundSep {
		cmdArgs = args
	}

	if len(cmdArgs) == 0 {
		fmt.Fprintln(os.Stderr, "Error: run requires a command, e.g. 'aether-agent run -- mvn test' or 'aether-agent run -- docker build .'")
		os.Exit(1)
	}

	fmt.Printf("[aether-agent run] Executing: %v\n", cmdArgs)
	result, err := runner.ExecuteCommand(cmdArgs)
	if err != nil {
		fmt.Fprintf(os.Stderr, "[aether-agent run] Execution error: %v\n", err)
	}

	fmt.Printf("\n[aether-agent run] Finished in %v with exit code %d\n", result.Duration, result.ExitCode)

	// Record execution summary into local .aether/runs.log
	recordRunLog(cwd, result)

	os.Exit(result.ExitCode)
}

func recordRunLog(cwd string, res *runner.ExecutionResult) {
	logPath := filepath.Join(cwd, ".aether", "runs.log")
	f, err := os.OpenFile(logPath, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	if err != nil {
		return
	}
	defer f.Close()

	line := fmt.Sprintf("[%s] CMD=%s EXIT=%d DURATION=%v\n", res.StartedAt.Format(time.RFC3339), res.Command, res.ExitCode, res.Duration)
	f.WriteString(line)
}
