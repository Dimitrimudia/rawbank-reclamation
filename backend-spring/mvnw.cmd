@ECHO OFF
SETLOCAL ENABLEEXTENSIONS
SET MVN_VERSION=3.9.7
SET BASE_DIR=%~dp0
SET WRAPPER_DIR=%BASE_DIR%\.mvn\wrapper
SET DIST_URL=https://archive.apache.org/dist/maven/maven-3/%MVN_VERSION%/binaries/apache-maven-%MVN_VERSION%-bin.zip
SET ARCHIVE=%WRAPPER_DIR%\apache-maven-%MVN_VERSION%-bin.zip
SET MAVEN_HOME=%WRAPPER_DIR%\apache-maven-%MVN_VERSION%

IF NOT EXIST "%WRAPPER_DIR%" (
  mkdir "%WRAPPER_DIR%"
)

IF NOT EXIST "%MAVEN_HOME%" (
  ECHO [mvnw] Téléchargement Maven %MVN_VERSION%...
  WHERE curl >NUL 2>&1
  IF %ERRORLEVEL%==0 (
    curl -fSL "%DIST_URL%" -o "%ARCHIVE%"
  ) ELSE (
    WHERE powershell >NUL 2>&1
    IF %ERRORLEVEL%==0 (
      powershell -Command "Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%ARCHIVE%'"
    ) ELSE (
      ECHO Erreur: curl ou powershell requis pour télécharger Maven.
      EXIT /B 1
    )
  )
  powershell -Command "Expand-Archive -Path '%ARCHIVE%' -DestinationPath '%WRAPPER_DIR%'"
  DEL /Q "%ARCHIVE%"
)

SET MVN_BIN=%MAVEN_HOME%\bin\mvn.cmd
IF NOT EXIST "%MVN_BIN%" (
  ECHO Erreur: mvn introuvable après extraction.
  EXIT /B 1
)

"%MVN_BIN%" -Dmaven.multiModuleProjectDirectory="%BASE_DIR%" %*
