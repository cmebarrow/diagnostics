# Log4j utility classes
Classes to help with logging and diagnostics for any system using log4j for logging, 
for example, Kaazing WebSocket Gateway.

# About this Project


# Minimum requirements for building the project
* Java Developer Kit (JDK) 8 
* Maven 3.0.5 or above

# Running this project
Include the jar file in the class path of the product under test.
Edit the log4j-config.xml file to make use of TriggeredRollingFileAppender.
Start up the product and reproduce the issue being investigated.
Look for diagnostic log messages in the configured output files of TriggeredRollingFileAppender.
