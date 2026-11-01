package runner

import (
	"bytes"
	"fmt"
	"io"
	"os"
	"os/exec"
	"runtime"
	"time"
)

type ExecutionResult struct {
	Command      string
	Args         []string
	ExitCode     int
	Duration     time.Duration
	Stdout       string
	Stderr       string
	StartedAt    time.Time
	FinishedAt   time.Time
}

func ExecuteCommand(args []string) (*ExecutionResult, error) {
	if len(args) == 0 {
		return nil, fmt.Errorf("no command provided")
	}

	cmdName := args[0]
	cmdArgs := args[1:]

	var cmd *exec.Cmd
	if runtime.GOOS == "windows" {
		// On windows, route through cmd.exe if needed or invoke executable directly
		cmd = exec.Command(cmdName, cmdArgs...)
	} else {
		cmd = exec.Command(cmdName, cmdArgs...)
	}

	var stdoutBuf, stderrBuf bytes.Buffer
	// Multiplex output so developer sees stdout/stderr in real time while we capture it
	stdoutWriter := io.MultiWriter(os.Stdout, &stdoutBuf)
	stderrWriter := io.MultiWriter(os.Stderr, &stderrBuf)

	cmd.Stdout = stdoutWriter
	cmd.Stderr = stderrWriter
	cmd.Stdin = os.Stdin

	startTime := time.Now()
	err := cmd.Run()
	finishedTime := time.Now()
	duration := finishedTime.Sub(startTime)

	exitCode := 0
	if err != nil {
		if exitError, ok := err.(*exec.ExitError); ok {
			exitCode = exitError.ExitCode()
		} else {
			exitCode = 1
		}
	}

	result := &ExecutionResult{
		Command:    cmdName,
		Args:       cmdArgs,
		ExitCode:   exitCode,
		Duration:   duration,
		Stdout:     stdoutBuf.String(),
		Stderr:     stderrBuf.String(),
		StartedAt:  startTime,
		FinishedAt: finishedTime,
	}

	return result, nil
}

