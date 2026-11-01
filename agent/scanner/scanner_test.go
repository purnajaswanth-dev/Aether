package scanner

import (
	"os"
	"path/filepath"
	"testing"
)

func TestScanner(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "aether-test-scan-*")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tempDir)

	// Create a dummy Java file
	javaCode := `package com.example;

public class TestService {
    public void executeTask() {
        System.out.println("Hello");
    }
}
`
	srcDir := filepath.Join(tempDir, "src")
	if err := os.MkdirAll(srcDir, 0755); err != nil {
		t.Fatalf("Failed to create src dir: %v", err)
	}
	if err := os.WriteFile(filepath.Join(srcDir, "TestService.java"), []byte(javaCode), 0644); err != nil {
		t.Fatalf("Failed to write Java file: %v", err)
	}

	// Create a secret file that should be ignored
	if err := os.WriteFile(filepath.Join(tempDir, ".env"), []byte("SECRET=12345"), 0644); err != nil {
		t.Fatalf("Failed to write .env file: %v", err)
	}

	s := NewScanner(tempDir)
	files, err := s.Scan()
	if err != nil {
		t.Fatalf("Scan failed: %v", err)
	}

	if len(files) != 1 {
		t.Fatalf("Expected 1 valid scanned file, got %d", len(files))
	}

	file := files[0]
	if file.Language != "Java" {
		t.Errorf("Expected language Java, got %s", file.Language)
	}

	foundClass := false
	foundMethod := false
	for _, sym := range file.Symbols {
		if sym == "TestService" {
			foundClass = true
		}
		if sym == "executeTask" {
			foundMethod = true
		}
	}

	if !foundClass {
		t.Errorf("Expected symbol TestService in extracted symbols, got: %v", file.Symbols)
	}
	if !foundMethod {
		t.Errorf("Expected symbol executeTask in extracted symbols, got: %v", file.Symbols)
	}
}

func TestIsWithinWorkspace(t *testing.T) {
	root := filepath.Clean("/workspace/project")

	insideFile := filepath.Clean("/workspace/project/src/Main.java")
	if !isWithinWorkspace(root, insideFile) {
		t.Errorf("Expected %s to be within %s", insideFile, root)
	}

	outsideFile := filepath.Clean("/workspace/other/Secret.txt")
	if isWithinWorkspace(root, outsideFile) {
		t.Errorf("Expected %s to NOT be within %s", outsideFile, root)
	}

	siblingEscape := filepath.Clean("/workspace/project_evil/file.txt")
	if isWithinWorkspace(root, siblingEscape) {
		t.Errorf("Expected %s to NOT be within %s", siblingEscape, root)
	}

	etcPasswd := filepath.Clean("/etc/passwd")
	if isWithinWorkspace(root, etcPasswd) {
		t.Errorf("Expected %s to NOT be within %s", etcPasswd, root)
	}

	parentEscape := filepath.Clean("/workspace/project/../project_sibling/file.txt")
	if isWithinWorkspace(root, parentEscape) {
		t.Errorf("Expected %s to NOT be within %s", parentEscape, root)
	}

	// Windows paths test
	winRoot := `C:\Projects\Aether`
	winInside := `C:\Projects\Aether\src\Main.java`
	winOutside := `C:\Projects\Other\Secret.txt`
	winDiffDrive := `D:\Projects\Aether\src\Main.java`

	if !isWithinWorkspace(winRoot, winInside) {
		t.Errorf("Expected %s to be within %s", winInside, winRoot)
	}
	if isWithinWorkspace(winRoot, winOutside) {
		t.Errorf("Expected %s to NOT be within %s", winOutside, winRoot)
	}
	if isWithinWorkspace(winRoot, winDiffDrive) {
		t.Errorf("Expected %s to NOT be within %s", winDiffDrive, winRoot)
	}
}

func TestSymlinkSecurity(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "aether-symlink-test-*")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tempDir)

	outsideDir, err := os.MkdirTemp("", "aether-outside-*")
	if err != nil {
		t.Fatalf("Failed to create outside dir: %v", err)
	}
	defer os.RemoveAll(outsideDir)

	// Create valid file inside
	validFile := filepath.Join(tempDir, "Valid.java")
	if err := os.WriteFile(validFile, []byte("public class Valid {}"), 0644); err != nil {
		t.Fatalf("Failed to write valid file: %v", err)
	}

	// Create sensitive file outside workspace
	outsideFile := filepath.Join(outsideDir, "Secret.java")
	if err := os.WriteFile(outsideFile, []byte("public class SecretExfiltrated {}"), 0644); err != nil {
		t.Fatalf("Failed to write outside file: %v", err)
	}

	// Try creating symlinks
	symlinkInside := filepath.Join(tempDir, "ValidLink.java")
	err1 := os.Symlink(validFile, symlinkInside)

	symlinkEscape := filepath.Join(tempDir, "EscapeLink.java")
	err2 := os.Symlink(outsideFile, symlinkEscape)

	brokenLink := filepath.Join(tempDir, "BrokenLink.java")
	err3 := os.Symlink(filepath.Join(tempDir, "nonexistent-target.java"), brokenLink)

	// Recursive symlink loop
	loopDir := filepath.Join(tempDir, "loopDir")
	if err := os.MkdirAll(loopDir, 0755); err == nil {
		_ = os.Symlink(loopDir, filepath.Join(loopDir, "circular"))
	}

	if err1 != nil || err2 != nil || err3 != nil {
		t.Logf("Symlink creation not permitted in this OS environment (%v, %v, %v); verified via logic tests", err1, err2, err3)
		return
	}

	s := NewScanner(tempDir)
	files, err := s.Scan()
	if err != nil {
		t.Fatalf("Scan failed on symlink workspace: %v", err)
	}

	for _, f := range files {
		if f.RelativePath == "EscapeLink.java" {
			t.Errorf("SECURITY VIOLATION: Escaping symlink EscapeLink.java was scanned!")
		}
		for _, sym := range f.Symbols {
			if sym == "SecretExfiltrated" {
				t.Errorf("SECURITY VIOLATION: Symbol from escaped file was extracted: %s", sym)
			}
		}
	}
}
