package executor

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestSucceed(t *testing.T) {
	r := Succeed("hello")
	assert.True(t, r.Success)
	assert.Equal(t, "hello", r.Output)
	assert.Empty(t, r.ErrOutput)
}

func TestFail(t *testing.T) {
	r := Fail("something went wrong")
	assert.False(t, r.Success)
	assert.Equal(t, "something went wrong", r.ErrOutput)
	assert.Empty(t, r.Output)
}
