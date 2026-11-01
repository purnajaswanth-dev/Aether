package filter

import (
	"bufio"
	"os"
	"path/filepath"
	"strings"
)

var defaultIgnoredDirs = map[string]bool{
	"node_modules": true,
	"target":       true,
	".git":         true,
	".svn":         true,
	".hg":          true,
	"dist":         true,
	"build":        true,
	".next":        true,
	"bin":          true,
	"obj":          true,
	".idea":        true,
	".vscode":      true,
	"__pycache__":  true,
	".pytest_cache": true,
	"coverage":     true,
	".aether":      true,
}

var defaultIgnoredExtensions = map[string]bool{
	".exe":   true,
	".dll":   true,
	".so":    true,
	".dylib": true,
	".jar":   true,
	".war":   true,
	".zip":   true,
	".tar":   true,
	".gz":    true,
	".png":   true,
	".jpg":   true,
	".jpeg":  true,
	".gif":   true,
	".ico":   true,
	".mp4":   true,
	".pdf":   true,
	".pem":   true,
	".key":   true,
	".p12":   true,
	".pfx":   true,
	".keystore": true,
	".class": true,
}

type Filter struct {
	customPatterns []string
}

func NewFilter(rootPath string) *Filter {
	f := &Filter{
		customPatterns: make([]string, 0),
	}

	ignoreFile := filepath.Join(rootPath, ".aetherignore")
	if file, err := os.Open(ignoreFile); err == nil {
		defer file.Close()
		scanner := bufio.NewScanner(file)
		for scanner.Scan() {
			line := strings.TrimSpace(scanner.Text())
			if line != "" && !strings.HasPrefix(line, "#") {
				f.customPatterns = append(f.customPatterns, strings.ToLower(line))
			}
		}
	}

	return f
}

func (f *Filter) ShouldIgnoreDir(name string) bool {
	lower := strings.ToLower(name)
	if defaultIgnoredDirs[lower] {
		return true
	}
	return false
}

func (f *Filter) ShouldIgnoreFile(relPath string) bool {
	lowerPath := strings.ToLower(filepath.ToSlash(relPath))
	baseName := strings.ToLower(filepath.Base(relPath))
	ext := strings.ToLower(filepath.Ext(relPath))

	// 1. Secret & Credential checks
	if strings.HasPrefix(baseName, ".env") {
		return true
	}
	if strings.HasPrefix(baseName, "id_rsa") || strings.HasPrefix(baseName, "id_ecdsa") || strings.HasPrefix(baseName, "id_ed25519") {
		return true
	}
	if strings.Contains(baseName, "credential") || strings.Contains(baseName, "secret") || strings.Contains(baseName, "token") {
		return true
	}

	// 2. Binary / Media / Certificate extensions
	if defaultIgnoredExtensions[ext] {
		return true
	}

	// 3. Ignored directory paths
	parts := strings.Split(lowerPath, "/")
	for _, part := range parts {
		if defaultIgnoredDirs[part] {
			return true
		}
	}

	// 4. Custom patterns from .aetherignore
	for _, pattern := range f.customPatterns {
		if matched, _ := filepath.Match(pattern, baseName); matched {
			return true
		}
		if matched, _ := filepath.Match(pattern, lowerPath); matched {
			return true
		}
	}

	return false
}

