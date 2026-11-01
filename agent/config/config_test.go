package config

import (
	"os"
	"testing"
)

func TestConfigSaveAndLoad(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "aether-cfg-test-*")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tempDir)

	cfg := &AgentConfig{
		ServerURL:   "http://localhost:8080",
		ProjectID:   "proj-123",
		DeviceID:    "dev-456",
		DeviceToken: "aeth_agent_xyz",
		Paused:      false,
		LocalOnly:   true,
		LocalPort:   8124,
		LocalPath:   tempDir,
	}

	if err := cfg.Save(tempDir); err != nil {
		t.Fatalf("Failed to save config: %v", err)
	}

	loaded, err := LoadConfig(tempDir)
	if err != nil {
		t.Fatalf("Failed to load config: %v", err)
	}

	if loaded.ProjectID != "proj-123" || loaded.DeviceToken != "aeth_agent_xyz" || !loaded.LocalOnly {
		t.Errorf("Config mismatch after load: %+v", loaded)
	}

	if err := ClearConfig(tempDir); err != nil {
		t.Fatalf("Failed to clear config: %v", err)
	}

	_, err = os.Stat(ConfigFilePath(tempDir))
	if !os.IsNotExist(err) {
		t.Errorf("Expected config file to be removed, but still exists")
	}
}

