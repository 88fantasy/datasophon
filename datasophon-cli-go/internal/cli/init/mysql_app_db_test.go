package initcmd

import (
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestInitMysqlAppDb_InitCommonAccountCanBeRetried(t *testing.T) {
	exec := &recordingExec{}
	task := &InitMysqlAppDb{
		RootPassword: "root-secret",
		Account:      "bigdata",
		Password:     "app-secret",
		DBName:       "datasophon",
		Port:         3306,
	}

	require.NoError(t, task.initCommonAccount(exec))

	assert.True(t, hasCmd(exec.commands, "CREATE DATABASE IF NOT EXISTS datasophon"))
	assert.True(t, hasCmd(exec.commands, "CREATE USER IF NOT EXISTS 'bigdata'@'%'"))
	assert.True(t, hasCmd(exec.commands, "ALTER USER 'bigdata'@'%' IDENTIFIED BY 'app-secret'"))
}

func TestInitMysqlAppDb_ErrorDoesNotExposeSqlCredentials(t *testing.T) {
	exec := &recordingExec{failCmds: []string{"CREATE USER IF NOT EXISTS"}}
	task := &InitMysqlAppDb{
		RootPassword: "root-secret",
		Account:      "bigdata",
		Password:     "app-secret",
		DBName:       "datasophon",
		Port:         3306,
	}

	err := task.initCommonAccount(exec)

	require.Error(t, err)
	assert.False(t, strings.Contains(err.Error(), "app-secret"))
	assert.False(t, strings.Contains(err.Error(), "root-secret"))
}
