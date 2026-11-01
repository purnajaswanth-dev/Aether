package runner

import (
	"runtime"
	"strings"
	"testing"
)

func TestExecuteCommand(t *testing.T) {
	var cmd []string
	if runtime.GOOS == "windows" {
		cmd = []string{"cmd", "/c", "echo", "hello-aether"}
	} else {
		cmd = []string{"echo", "hello-aether"}
	}

	result, err := ExecuteCommand(cmd)
	if err != nil {
		t.Fatalf("ExecuteCommand returned error: %v", err)
	}

	if result.ExitCode != 0 {
		t.Errorf("Expected exit code 0, got %d", result.ExitCode)
	}

	if !strings.Contains(result.Stdout, "hello-aether") {
		t.Errorf("Expected stdout to contain 'hello-aether', got: %s", result.Stdout)
	}

	if result.Duration <= 0 {
		t.Errorf("Expected positive duration, got %v", result.Duration)
	}
}

