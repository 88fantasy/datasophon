package shellutil

import "testing"

func TestQuote(t *testing.T) {
	tests := []struct {
		input string
		want  string
	}{
		{"", "''"},
		{"plain", "'plain'"},
		{"a b;c$(d)", "'a b;c$(d)'"},
		{"x'; rm -rf /", `'x'\''; rm -rf /'`},
	}
	for _, test := range tests {
		if got := Quote(test.input); got != test.want {
			t.Errorf("Quote(%q) = %q, want %q", test.input, got, test.want)
		}
	}
}
