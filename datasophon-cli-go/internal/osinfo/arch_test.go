package osinfo

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestParseArch(t *testing.T) {
	tests := []struct {
		raw  string
		want ArchType
	}{
		{"x86_64", ArchX86_64},
		{"aarch64", ArchAarch64},
		{"arm64", ArchAarch64},
		{"unknown", ArchOther},
		{"", ArchOther},
		{"  x86_64  ", ArchX86_64},
	}
	for _, tc := range tests {
		t.Run(tc.raw, func(t *testing.T) {
			assert.Equal(t, tc.want, ParseArch(tc.raw))
		})
	}
}
