package plan

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"
)

var s3BucketNamePattern = regexp.MustCompile(`^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$`)

type s3HTTPError struct {
	statusCode int
	message    string
}

func (e *s3HTTPError) Error() string { return e.message }

func ensureS3Bucket(endpoint, bucket, region, accessKey, secretKey string) error {
	if endpoint == "" || accessKey == "" || secretKey == "" {
		return errors.New("创建基础采集 S3 bucket 所需的 endpoint 或凭据为空")
	}
	if !s3BucketNamePattern.MatchString(bucket) {
		return fmt.Errorf("基础采集 S3 bucket 名不合法: %q", bucket)
	}
	if region == "" {
		region = "us-east-1"
	}

	client := &http.Client{Timeout: 5 * time.Second}
	var lastErr error
	for attempt := 1; attempt <= 5; attempt++ {
		lastErr = createS3Bucket(context.Background(), client, endpoint, bucket, region,
			accessKey, secretKey, time.Now())
		if lastErr == nil {
			return nil
		}
		if !isRetryableS3Error(lastErr) {
			break
		}
		if attempt < 5 {
			time.Sleep(2 * time.Second)
		}
	}
	return fmt.Errorf("创建或确认基础采集 S3 bucket 失败: %w", lastErr)
}

func isRetryableS3Error(err error) bool {
	var statusErr *s3HTTPError
	if errors.As(err, &statusErr) {
		return statusErr.statusCode == http.StatusRequestTimeout ||
			statusErr.statusCode == http.StatusTooManyRequests || statusErr.statusCode >= 500
	}
	var networkErr net.Error
	return errors.As(err, &networkErr)
}

func createS3Bucket(ctx context.Context, client *http.Client, endpoint, bucket, region,
	accessKey, secretKey string, now time.Time,
) error {
	endpointURL, err := url.Parse(endpoint)
	if err != nil || endpointURL.Scheme == "" || endpointURL.Host == "" {
		return fmt.Errorf("S3 endpoint 不合法: %q", endpoint)
	}
	if endpointURL.Scheme != "http" && endpointURL.Scheme != "https" {
		return fmt.Errorf("S3 endpoint 仅支持 http/https: %q", endpoint)
	}
	if endpointURL.User != nil || endpointURL.RawQuery != "" || endpointURL.Fragment != "" {
		return fmt.Errorf("S3 endpoint 不能包含用户信息、查询参数或 fragment: %q", endpoint)
	}
	endpointURL.Path = strings.TrimRight(endpointURL.Path, "/") + "/" + bucket

	req, err := http.NewRequestWithContext(ctx, http.MethodPut, endpointURL.String(), nil)
	if err != nil {
		return fmt.Errorf("构造 S3 bucket 请求失败: %w", err)
	}
	signS3Request(req, region, accessKey, secretKey, now)
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(io.LimitReader(resp.Body, 64<<10))
	if resp.StatusCode >= 200 && resp.StatusCode < 300 {
		return nil
	}
	if resp.StatusCode == http.StatusConflict && strings.Contains(string(body), "BucketAlreadyOwnedByYou") {
		return nil
	}
	if resp.StatusCode == http.StatusConflict && strings.Contains(string(body), "BucketAlreadyExists") {
		return checkS3BucketAccess(ctx, client, endpointURL, region, accessKey, secretKey, now)
	}
	return &s3HTTPError{
		statusCode: resp.StatusCode,
		message:    fmt.Sprintf("S3 PUT bucket 返回 HTTP %d: %s", resp.StatusCode, strings.TrimSpace(string(body))),
	}
}

func checkS3BucketAccess(ctx context.Context, client *http.Client, bucketURL *url.URL,
	region, accessKey, secretKey string, now time.Time,
) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodHead, bucketURL.String(), nil)
	if err != nil {
		return fmt.Errorf("构造 S3 bucket 校验请求失败: %w", err)
	}
	signS3Request(req, region, accessKey, secretKey, now)
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 200 && resp.StatusCode < 300 {
		return nil
	}
	return &s3HTTPError{
		statusCode: resp.StatusCode,
		message:    fmt.Sprintf("S3 bucket 已存在但当前凭据不可访问: HTTP %d", resp.StatusCode),
	}
}

func signS3Request(req *http.Request, region, accessKey, secretKey string, now time.Time) {
	const emptyPayloadHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
	utc := now.UTC()
	amzDate := utc.Format("20060102T150405Z")
	date := utc.Format("20060102")
	req.Header.Set("x-amz-content-sha256", emptyPayloadHash)
	req.Header.Set("x-amz-date", amzDate)

	canonicalURI := req.URL.EscapedPath()
	if canonicalURI == "" {
		canonicalURI = "/"
	}
	canonicalHeaders := "host:" + req.URL.Host + "\n" +
		"x-amz-content-sha256:" + emptyPayloadHash + "\n" +
		"x-amz-date:" + amzDate + "\n"
	const signedHeaders = "host;x-amz-content-sha256;x-amz-date"
	canonicalRequest := strings.Join([]string{
		req.Method,
		canonicalURI,
		req.URL.Query().Encode(),
		canonicalHeaders,
		signedHeaders,
		emptyPayloadHash,
	}, "\n")
	scope := date + "/" + region + "/s3/aws4_request"
	stringToSign := "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n" + sha256Hex(canonicalRequest)

	dateKey := hmacSHA256([]byte("AWS4"+secretKey), date)
	regionKey := hmacSHA256(dateKey, region)
	serviceKey := hmacSHA256(regionKey, "s3")
	signingKey := hmacSHA256(serviceKey, "aws4_request")
	signature := hex.EncodeToString(hmacSHA256(signingKey, stringToSign))
	req.Header.Set("Authorization", "AWS4-HMAC-SHA256 Credential="+accessKey+"/"+scope+
		", SignedHeaders="+signedHeaders+", Signature="+signature)
}

func sha256Hex(value string) string {
	sum := sha256.Sum256([]byte(value))
	return hex.EncodeToString(sum[:])
}

func hmacSHA256(key []byte, value string) []byte {
	mac := hmac.New(sha256.New, key)
	_, _ = mac.Write([]byte(value))
	return mac.Sum(nil)
}
