BINARY   = datasophon-cli
DIST     = dist
MODULE   = github.com/88fantasy/datasophon/datasophon-cli-go
MAIN     = ./cmd/datasophon-cli

.PHONY: build test clean release vet

build:
	@mkdir -p $(DIST)
	go build -o $(DIST)/$(BINARY) $(MAIN)

test:
	go test ./...

vet:
	go vet ./...

clean:
	rm -rf $(DIST)

release: clean
	@mkdir -p $(DIST)
	GOOS=linux  GOARCH=amd64 go build -o $(DIST)/$(BINARY)-linux-amd64  $(MAIN)
	GOOS=linux  GOARCH=arm64 go build -o $(DIST)/$(BINARY)-linux-arm64  $(MAIN)
	GOOS=darwin GOARCH=amd64 go build -o $(DIST)/$(BINARY)-darwin-amd64 $(MAIN)
	GOOS=darwin GOARCH=arm64 go build -o $(DIST)/$(BINARY)-darwin-arm64 $(MAIN)
	@echo "Build complete:"
	@ls -lh $(DIST)/
