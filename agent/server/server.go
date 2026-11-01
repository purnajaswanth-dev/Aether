package server

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"

	"aether-agent/scanner"
)

type LocalServer struct {
	port    int
	scanner *scanner.Scanner
	server  *http.Server
}

func NewLocalServer(port int, s *scanner.Scanner) *LocalServer {
	return &LocalServer{
		port:    port,
		scanner: s,
	}
}

func (ls *LocalServer) Start() error {
	mux := http.NewServeMux()

	mux.HandleFunc("/status", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]interface{}{
			"mode":          "local-only",
			"status":        "ACTIVE",
			"files_indexed": len(ls.scanner.GetCached()),
		})
	})

	mux.HandleFunc("/files", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		query := strings.ToLower(r.URL.Query().Get("query"))
		all := ls.scanner.GetCached()

		var filtered []scanner.FileMetadata
		for _, f := range all {
			if query == "" || strings.Contains(strings.ToLower(f.RelativePath), query) || strings.Contains(strings.ToLower(f.Language), query) {
				filtered = append(filtered, f)
			}
		}

		json.NewEncoder(w).Encode(map[string]interface{}{
			"count": len(filtered),
			"files": filtered,
		})
	})

	mux.HandleFunc("/scan", func(w http.ResponseWriter, r *http.Request) {
		files, err := ls.scanner.Scan()
		w.Header().Set("Content-Type", "application/json")
		if err != nil {
			w.WriteHeader(http.StatusInternalServerError)
			json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
			return
		}
		json.NewEncoder(w).Encode(map[string]interface{}{
			"status": "SCANNED",
			"count":  len(files),
		})
	})

	addr := fmt.Sprintf("127.0.0.1:%d", ls.port)
	ls.server = &http.Server{
		Addr:    addr,
		Handler: mux,
	}

	fmt.Printf("[Local-Only] Aether Local Agent server listening at http://%s\n", addr)
	return ls.server.ListenAndServe()
}

func (ls *LocalServer) Stop() error {
	if ls.server != nil {
		return ls.server.Close()
	}
	return nil
}

