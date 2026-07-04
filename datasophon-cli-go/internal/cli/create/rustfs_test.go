package create

import "testing"

func TestShellSingleQuote(t *testing.T) {
	cases := []struct {
		name  string
		input string
		want  string
	}{
		{"plain url", "http://127.0.0.1:4318", "'http://127.0.0.1:4318'"},
		{"empty", "", "''"},
		{
			"embedded single quote",
			"http://127.0.0.1:4318/'; rm -rf /",
			`'http://127.0.0.1:4318/'\''; rm -rf /'`,
		},
		{
			"shell metacharacters are inert once single-quoted",
			"http://a b;c$(d)",
			"'http://a b;c$(d)'",
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := shellSingleQuote(tc.input); got != tc.want {
				t.Errorf("shellSingleQuote(%q) = %q, want %q", tc.input, got, tc.want)
			}
		})
	}
}
