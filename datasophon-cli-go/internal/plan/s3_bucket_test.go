package plan

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestCreateS3BucketSignsPathStylePut(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, http.MethodPut, r.Method)
		assert.Equal(t, "/otel", r.URL.Path)
		assert.Equal(t, "20260722T120000Z", r.Header.Get("x-amz-date"))
		assert.Equal(t,
			"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
			r.Header.Get("x-amz-content-sha256"),
		)
		authorization := r.Header.Get("Authorization")
		assert.True(t, strings.HasPrefix(authorization,
			"AWS4-HMAC-SHA256 Credential=access/20260722/us-east-1/s3/aws4_request,"))
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	err := createS3Bucket(context.Background(), server.Client(), server.URL, "otel", "us-east-1",
		"access", "secret", time.Date(2026, 7, 22, 12, 0, 0, 0, time.UTC))
	require.NoError(t, err)
}

func TestCreateS3BucketTreatsExistingBucketAsSuccess(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusConflict)
		_, _ = w.Write([]byte(`<Error><Code>BucketAlreadyOwnedByYou</Code></Error>`))
	}))
	defer server.Close()

	err := createS3Bucket(context.Background(), server.Client(), server.URL, "otel", "us-east-1",
		"access", "secret", time.Now())
	require.NoError(t, err)
}

func TestCreateS3BucketVerifiesAccessWhenNameAlreadyExists(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodPut {
			w.WriteHeader(http.StatusConflict)
			_, _ = w.Write([]byte(`<Error><Code>BucketAlreadyExists</Code></Error>`))
			return
		}
		assert.Equal(t, http.MethodHead, r.Method)
		assert.NotEmpty(t, r.Header.Get("Authorization"))
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	err := createS3Bucket(context.Background(), server.Client(), server.URL, "otel", "us-east-1",
		"access", "secret", time.Now())
	require.NoError(t, err)
}

func TestCreateS3BucketRejectsInaccessibleExistingBucket(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodPut {
			w.WriteHeader(http.StatusConflict)
			_, _ = w.Write([]byte(`<Error><Code>BucketAlreadyExists</Code></Error>`))
			return
		}
		w.WriteHeader(http.StatusForbidden)
	}))
	defer server.Close()

	err := createS3Bucket(context.Background(), server.Client(), server.URL, "otel", "us-east-1",
		"access", "secret", time.Now())
	require.Error(t, err)
	assert.Contains(t, err.Error(), "不可访问")
}

func TestEnsureS3BucketRejectsInvalidNameBeforeRequest(t *testing.T) {
	err := ensureS3Bucket("http://127.0.0.1:9040", "../otel", "us-east-1", "access", "secret")
	require.Error(t, err)
	assert.Contains(t, err.Error(), "bucket 名不合法")
}

func TestS3RetryClassification(t *testing.T) {
	assert.False(t, isRetryableS3Error(&s3HTTPError{statusCode: http.StatusForbidden}))
	assert.False(t, isRetryableS3Error(&s3HTTPError{statusCode: http.StatusConflict}))
	assert.True(t, isRetryableS3Error(&s3HTTPError{statusCode: http.StatusTooManyRequests}))
	assert.True(t, isRetryableS3Error(&s3HTTPError{statusCode: http.StatusServiceUnavailable}))
}
