package bootstrap

import (
	"io"
	"net/http"
	"strings"
	"testing"
)

func TestAcceptNexusEULAForwardsServerDisclaimer(t *testing.T) {
	const disclaimer = "Nexus “exact” disclaimer"
	var posted string
	get := func(_, _, _, _ string) (*http.Response, error) {
		return response(http.StatusOK, `{"accepted":false,"disclaimer":"`+disclaimer+`"}`), nil
	}
	post := func(_, _, _, _, _ string, body io.Reader) (*http.Response, error) {
		data, err := io.ReadAll(body)
		if err != nil {
			t.Fatal(err)
		}
		posted = string(data)
		return response(http.StatusNoContent, ""), nil
	}

	if err := AcceptNexusEULA("http://nexus", "admin", "secret", get, post); err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(posted, disclaimer) {
		t.Fatalf("posted payload %q does not contain disclaimer %q", posted, disclaimer)
	}
}

func TestAcceptNexusEULASkipsPostWhenAccepted(t *testing.T) {
	posted := false
	get := func(_, _, _, _ string) (*http.Response, error) {
		return response(http.StatusOK, `{"accepted":true,"disclaimer":"already"}`), nil
	}
	post := func(_, _, _, _, _ string, _ io.Reader) (*http.Response, error) {
		posted = true
		return response(http.StatusNoContent, ""), nil
	}

	if err := AcceptNexusEULA("http://nexus", "admin", "secret", get, post); err != nil {
		t.Fatal(err)
	}
	if posted {
		t.Fatal("accepted EULA must not be posted again")
	}
}

func response(status int, body string) *http.Response {
	return &http.Response{StatusCode: status, Body: io.NopCloser(strings.NewReader(body))}
}
