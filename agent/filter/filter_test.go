package filter

import (
	"testing"
)

func TestFilterExclusions(t *testing.T) {
	f := NewFilter(".")

	secretFiles := []string{
		".env",
		".env.local",
		".env.production",
		"id_rsa",
		"id_rsa.pub",
		"id_ed25519",
		"credentials.json",
		"db_secret.yaml",
		"api_token.txt",
		"server.key",
		"cert.pem",
		"keystore.p12",
	}

	for _, file := range secretFiles {
		if !f.ShouldIgnoreFile(file) {
			t.Errorf("Expected secret file %s to be ignored, but was allowed", file)
		}
	}

	buildFiles := []string{
		"node_modules/express/index.js",
		"target/classes/App.class",
		".git/config",
		"dist/bundle.js",
		"build/output.log",
		"app.exe",
		"library.dll",
		"archive.zip",
	}

	for _, file := range buildFiles {
		if !f.ShouldIgnoreFile(file) {
			t.Errorf("Expected build/binary file %s to be ignored, but was allowed", file)
		}
	}

	allowedFiles := []string{
		"src/main/java/com/App.java",
		"frontend/src/components/Button.tsx",
		"docs/CONSTITUTION.md",
		"Dockerfile",
		"docker-compose.yml",
		"package.json",
		"pom.xml",
	}

	for _, file := range allowedFiles {
		if f.ShouldIgnoreFile(file) {
			t.Errorf("Expected valid project file %s to be accepted, but was ignored", file)
		}
	}
}

