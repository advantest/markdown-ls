@echo off
rem Copyright (c) 2026 Advantest Europe GmbH
rem SPDX-License-Identifier: BSD-3-Clause
rem
rem Starts markdown-ls with a Java 21 or newer, taken from
rem   1. the environment variable MARKDOWN_LS_JAVA, the path of a java executable,
rem   2. the environment variable JAVA_HOME,
rem   3. the PATH.
rem The environment variable MARKDOWN_LS_OPTS may hold further options for the Java VM.
rem The server speaks the protocol on standard input and output, so this script writes nothing there.

setlocal
set "MARKDOWN_LS_HOME=%~dp0.."

if defined MARKDOWN_LS_JAVA (
	set "MARKDOWN_LS_JAVA_EXE=%MARKDOWN_LS_JAVA%"
) else if defined JAVA_HOME (
	set "MARKDOWN_LS_JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
	set "MARKDOWN_LS_JAVA_EXE=java"
)

"%MARKDOWN_LS_JAVA_EXE%" %MARKDOWN_LS_OPTS% -cp "%MARKDOWN_LS_HOME%\markdown-ls.jar;%MARKDOWN_LS_HOME%\lib\*" com.advantest.markdown.ls.MarkdownLanguageServer %*
exit /b %ERRORLEVEL%
