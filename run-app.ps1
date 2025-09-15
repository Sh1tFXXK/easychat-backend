# Set Java environment
$env:JAVA_HOME = "C:\Users\Administrator\.jdks\ms-17.0.16"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Change to project directory
Set-Location "E:\project\EASYCHATPROJECT\easychat"

# Run the application
& ".\mvnw.cmd" spring-boot:run