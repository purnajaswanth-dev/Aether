package config

import (
	"encoding/json"
	"os"
	"path/filepath"
)

type AgentConfig struct {
	ServerURL   string `json:"server_url"`
	ProjectID   string `json:"project_id"`
	DeviceID    string `json:"device_id"`
	DeviceToken string `json:"device_token"`
	Paused      bool   `json:"paused"`
	LocalOnly   bool   `json:"local_only"`
	LocalPort   int    `json:"local_port"`
	LocalPath   string `json:"local_path"`
}

func DefaultConfig() *AgentConfig {
	cwd, _ := os.Getwd()
	return &AgentConfig{
		ServerURL: "http://localhost:8080",
		LocalPort: 8124,
		LocalPath: cwd,
		Paused:    false,
		LocalOnly: false,
	}
}

func ConfigFilePath(workspaceRoot string) string {
	return filepath.Join(workspaceRoot, ".aether", "agent.json")
}

func LoadConfig(workspaceRoot string) (*AgentConfig, error) {
	path := ConfigFilePath(workspaceRoot)
	data, err := os.ReadFile(path)
	if err != nil {
		return DefaultConfig(), err
	}

	cfg := DefaultConfig()
	if err := json.Unmarshal(data, cfg); err != nil {
		return DefaultConfig(), err
	}

	return cfg, nil
}

func (c *AgentConfig) Save(workspaceRoot string) error {
	dir := filepath.Join(workspaceRoot, ".aether")
	if err := os.MkdirAll(dir, 0755); err != nil {
		return err
	}

	path := ConfigFilePath(workspaceRoot)
	data, err := json.MarshalIndent(c, "", "  ")
	if err != nil {
		return err
	}

	return os.WriteFile(path, data, 0600)
}

func ClearConfig(workspaceRoot string) error {
	path := ConfigFilePath(workspaceRoot)
	if err := os.Remove(path); err != nil && !os.IsNotExist(err) {
		return err
	}
	return nil
}

