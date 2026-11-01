package client

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"aether-agent/scanner"
)

type Client struct {
	httpClient *http.Client
}

func NewClient() *Client {
	return &Client{
		httpClient: &http.Client{
			Timeout: 15 * time.Second,
		},
	}
}

type PairInitResponse struct {
	DeviceCode      string `json:"deviceCode"`
	UserCode        string `json:"userCode"`
	VerificationUri string `json:"verificationUri"`
	ExpiresIn       int    `json:"expiresIn"`
}

type PairPollResponse struct {
	Status      string `json:"status"`
	DeviceToken string `json:"deviceToken"`
	ProjectID   string `json:"projectId"`
	DeviceID    string `json:"deviceId"`
}

func (c *Client) InitPair(serverURL, projectID string) (*PairInitResponse, error) {
	url := fmt.Sprintf("%s/api/agent/pair/init?projectId=%s", serverURL, projectID)
	resp, err := c.httpClient.Post(url, "application/json", nil)
	if err != nil {
		return nil, fmt.Errorf("failed to init pair: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("server error (%d): %s", resp.StatusCode, string(body))
	}

	var result PairInitResponse
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, err
	}
	return &result, nil
}

func (c *Client) PollPair(serverURL, deviceCode string) (*PairPollResponse, error) {
	url := fmt.Sprintf("%s/api/agent/pair/poll", serverURL)
	payload, _ := json.Marshal(map[string]string{"deviceCode": deviceCode})

	resp, err := c.httpClient.Post(url, "application/json", bytes.NewBuffer(payload))
	if err != nil {
		return nil, fmt.Errorf("failed to poll pair: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("server error (%d): %s", resp.StatusCode, string(body))
	}

	var result PairPollResponse
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, err
	}
	return &result, nil
}

func (c *Client) Heartbeat(serverURL, deviceToken string) error {
	url := fmt.Sprintf("%s/api/agent/heartbeat", serverURL)
	req, err := http.NewRequest("POST", url, nil)
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+deviceToken)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("heartbeat error (%d): %s", resp.StatusCode, string(body))
	}
	return nil
}

func (c *Client) SyncFiles(serverURL, deviceToken string, files []scanner.FileMetadata) error {
	url := fmt.Sprintf("%s/api/agent/sync-files", serverURL)
	payload, err := json.Marshal(files)
	if err != nil {
		return err
	}

	req, err := http.NewRequest("POST", url, bytes.NewBuffer(payload))
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+deviceToken)
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("sync-files error (%d): %s", resp.StatusCode, string(body))
	}
	return nil
}

