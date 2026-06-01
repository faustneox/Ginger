@echo off
cd /d %~dp0
call gradlew.bat :app:testDebugUnitTest --tests=com.ginger.android.data.repository.RequestPagingUnitTest --no-daemon --console=plain
