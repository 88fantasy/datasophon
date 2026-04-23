@echo off
setlocal enabledelayedexpansion

set "tag=latest"
set "proxy="
set "arch="

if not "%~1"=="" (
    set "tag=%~1"
)
shift

:parse_args
if "%~1"=="" goto args_done
if "%~1"=="--proxy" (
    set "proxy=%~2"
    shift
    shift
    goto parse_args
)
if "%~1"=="--arch" (
    set "arch=%~2"
    shift
    shift
    goto parse_args
)
echo Unknown option: %~1
exit /b 1

:args_done

if not "%proxy%"=="" (
    set "https_proxy=%proxy%"
    set "http_proxy=%proxy%"
    echo use proxy: "%proxy%".
)

set "BUILDER_NAME=datasophon-build"
set "image_name=datasophon-k8s-agent"

if "%arch%"=="all" (
    set "platform=linux/amd64,linux/arm64"
) else if not "%arch%"=="" (
    set "platform=linux/%arch%"
)

if "%arch%"=="all" (
    for /f "delims=" %%i in ('docker buildx ls 2^>nul') do (
        echo %%i | findstr /c:"%BUILDER_NAME%" >nul && (
            set "builder_exists=1"
        )
    )

    if defined builder_exists (
        echo BuildKit builder '%BUILDER_NAME%' already exists.
    ) else (
        echo BuildKit builder '%BUILDER_NAME%' does not exist.
        docker buildx create --name %BUILDER_NAME% --platform linux/amd64,linux/arm64 --use --bootstrap
    )

    docker buildx build ^
        --builder %BUILDER_NAME% ^
        --platform=!platform! ^
        --progress plain ^
        --output type=oci,dest=./%image_name%-%tag%-image-all.tar ^
        -t %image_name%:%tag% .
) else if not "%arch%"=="" (
    docker build ^
        --platform=!platform! ^
        --progress plain ^
        --output type=docker,dest=./%image_name%-%tag%-image-%arch%.tar ^
        -t %image_name%:%tag% .
) else (
    docker build ^
        --progress plain ^
        --output type=docker,dest=./%image_name%-%tag%-image-default.tar ^
        -t %image_name%:%tag% .
)

endlocal
