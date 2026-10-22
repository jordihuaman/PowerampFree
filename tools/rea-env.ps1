# Environment for REA + JNI analysis (dot-source in each PowerShell call)
$env:JAVA_HOME    = 'C:\FromGitHub\PowerampFree\tools\jdk\jdk-21.0.12.1+1'
$env:Path         = "$env:JAVA_HOME\bin;$env:Path"
$env:REA_JADX_MCP_JAR = 'C:\FromGitHub\PowerampFree\tools\jadx-headless-mcp-0.7.1-all.jar'
