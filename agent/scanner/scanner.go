package scanner

import (
	"bufio"
	"crypto/sha256"
	"encoding/hex"
	"io"
	"log"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
	"time"

	"aether-agent/filter"
)

type FileMetadata struct {
	RelativePath  string    `json:"relativePath"`
	ContentHash   string    `json:"contentHash"`
	FileSizeBytes int64     `json:"fileSizeBytes"`
	Mtime         time.Time `json:"mtime"`
	Language      string    `json:"language"`
	Symbols       []string  `json:"symbols"`
}

type Scanner struct {
	rootDir string
	filter  *filter.Filter
	cache   map[string]FileMetadata
	mu      sync.RWMutex
}

func NewScanner(rootDir string) *Scanner {
	return &Scanner{
		rootDir: rootDir,
		filter:  filter.NewFilter(rootDir),
		cache:   make(map[string]FileMetadata),
	}
}

var languageMap = map[string]string{
	".java": "Java",
	".go":   "Go",
	".ts":   "TypeScript",
	".tsx":  "TypeScript",
	".js":   "JavaScript",
	".jsx":  "JavaScript",
	".py":   "Python",
	".sql":  "SQL",
	".md":   "Markdown",
	".json": "JSON",
	".yaml": "YAML",
	".yml":  "YAML",
	".html": "HTML",
	".css":  "CSS",
}

var classRegex = regexp.MustCompile(`(?m)^\s*(?:public\s+|export\s+)?(?:class|interface|enum|type|struct)\s+([A-Za-z0-9_]+)`)
var funcRegex = regexp.MustCompile(`(?m)^\s*(?:public\s+|private\s+|protected\s+|func\s+|def\s+)(?:[A-Za-z0-9_<>[\]]+\s+)?([A-Za-z0-9_]+)\s*\(`)

func isWithinWorkspace(canonicalRoot, targetPath string) bool {
	rel, err := filepath.Rel(canonicalRoot, targetPath)
	if err != nil {
		return false
	}
	if rel == ".." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) {
		return false
	}
	if filepath.IsAbs(rel) {
		return false
	}
	return true
}

func (s *Scanner) Scan() ([]FileMetadata, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	// 1. Resolve canonical workspace root
	canonicalRoot, err := filepath.EvalSymlinks(s.rootDir)
	if err != nil {
		canonicalRoot, err = filepath.Abs(s.rootDir)
		if err != nil {
			return nil, err
		}
	}
	canonicalRoot = filepath.Clean(canonicalRoot)

	visitedDirs := make(map[string]bool)
	visitedDirs[canonicalRoot] = true

	var results []FileMetadata

	var walkFn func(currentDir string) error
	walkFn = func(currentDir string) error {
		entries, err := os.ReadDir(currentDir)
		if err != nil {
			return nil
		}

		for _, entry := range entries {
			entryPath := filepath.Join(currentDir, entry.Name())
			info, err := entry.Info()
			if err != nil {
				continue
			}

			isSymlink := info.Mode()&os.ModeSymlink != 0
			resolvedPath := entryPath

			if isSymlink {
				resolved, err := filepath.EvalSymlinks(entryPath)
				if err != nil {
					// Broken symlink - handle gracefully and skip
					log.Printf("[Scanner] Skipping broken symlink: %s (%v)", entryPath, err)
					continue
				}
				resolved = filepath.Clean(resolved)

				// Check workspace escape
				if !isWithinWorkspace(canonicalRoot, resolved) {
					// Rejected: symlink target escapes workspace root
					log.Printf("[Scanner] Skipping symlink escaping workspace root: %s -> %s", entryPath, resolved)
					continue
				}

				targetInfo, err := os.Stat(resolved)
				if err != nil {
					log.Printf("[Scanner] Skipping inaccessible symlink target: %s (%v)", resolved, err)
					continue
				}

				if targetInfo.IsDir() {
					// Symlink loop / cycle detection for directories
					if visitedDirs[resolved] {
						log.Printf("[Scanner] Skipping cyclic directory symlink loop: %s -> %s", entryPath, resolved)
						continue
					}
					visitedDirs[resolved] = true
				}

				info = targetInfo
				resolvedPath = resolved
			}

			relPath, err := filepath.Rel(canonicalRoot, resolvedPath)
			if err != nil || relPath == "." {
				continue
			}

			displayRelPath, err := filepath.Rel(s.rootDir, entryPath)
			if err != nil || displayRelPath == "." {
				displayRelPath = relPath
			}

			if info.IsDir() {
				if s.filter.ShouldIgnoreDir(info.Name()) {
					continue
				}
				if err := walkFn(entryPath); err != nil {
					return err
				}
				continue
			}

			if s.filter.ShouldIgnoreFile(displayRelPath) {
				continue
			}

			// Check cache for incremental mtime & size match
			cached, exists := s.cache[displayRelPath]
			if exists && cached.FileSizeBytes == info.Size() && cached.Mtime.Equal(info.ModTime()) {
				results = append(results, cached)
				continue
			}

			// Read and hash
			hash, err := computeHash(resolvedPath)
			if err != nil {
				continue
			}

			ext := strings.ToLower(filepath.Ext(resolvedPath))
			lang := languageMap[ext]
			if lang == "" {
				lang = "Text"
			}

			symbols := extractSymbols(resolvedPath)

			meta := FileMetadata{
				RelativePath:  filepath.ToSlash(displayRelPath),
				ContentHash:   hash,
				FileSizeBytes: info.Size(),
				Mtime:         info.ModTime(),
				Language:      lang,
				Symbols:       symbols,
			}

			s.cache[displayRelPath] = meta
			results = append(results, meta)
		}
		return nil
	}

	err = walkFn(s.rootDir)
	return results, err
}

func (s *Scanner) GetCached() []FileMetadata {
	s.mu.RLock()
	defer s.mu.RUnlock()

	list := make([]FileMetadata, 0, len(s.cache))
	for _, m := range s.cache {
		list = append(list, m)
	}
	return list
}

func computeHash(path string) (string, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer f.Close()

	hasher := sha256.New()
	if _, err := io.Copy(hasher, f); err != nil {
		return "", err
	}

	return hex.EncodeToString(hasher.Sum(nil)), nil
}

func extractSymbols(path string) []string {
	f, err := os.Open(path)
	if err != nil {
		return []string{}
	}
	defer f.Close()

	symbols := make([]string, 0)
	symbolSet := make(map[string]bool)

	scanner := bufio.NewScanner(f)
	lineCount := 0
	for scanner.Scan() && lineCount < 2000 {
		line := scanner.Text()
		lineCount++

		if matches := classRegex.FindStringSubmatch(line); len(matches) > 1 {
			name := matches[1]
			if !symbolSet[name] {
				symbolSet[name] = true
				symbols = append(symbols, name)
			}
		}

		if matches := funcRegex.FindStringSubmatch(line); len(matches) > 1 {
			name := matches[1]
			if !symbolSet[name] && name != "if" && name != "for" && name != "switch" && name != "while" {
				symbolSet[name] = true
				symbols = append(symbols, name)
			}
		}
	}

	return symbols
}
