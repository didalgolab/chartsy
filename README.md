# **Chartsy|One** Open Source

The project is an ongoing work in progress... help or support of any kind is muchly appreciated and welcomed.

### Prerequisites
* Java 17+
* Maven 3.8+

## Build from sources
1. Clone source code repository from GitHub:
```shell
git clone https://github.com/chartsyone/chartsy.git
```
2. Compile and build project files from inside the main project folder where the top-most pom.xml is located:
```shell
cd chartsy
mvn clean install
```
3. Launch desktop application:
```shell
mvn nbm:cluster-app nbm:run-platform -f application/pom.xml
```

## Troubleshooting build problems
Problem 1: I'm getting the following error when building project with Maven.
```
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.8.1:compile (default-compile) on project chartsy-core:
  Fatal error compiling: error: invalid target release: 17 -> [Help 1]
```
Solution: Ensure that proper java version is installed and used by your Maven builds. To check used java version type `mvn -version`. **Java 17 or later** is required to compile and run the project.
___
Problem 2: I'm getting the following error when trying to compile project using Maven.
```
[ERROR] Error executing Maven.
[ERROR] java.lang.IllegalStateException: Unable to load cache item
[ERROR] Caused by: Unable to load cache item
[ERROR] Caused by: Could not initialize class com.google.inject.internal.cglib.core.$MethodWrapper
```
Solution: Please ensure that Maven 3.8 or later is used. For example when on Linux the below command can install Maven 3.8.5 distribution:
```shell
sudo wget https://archive.apache.org/dist/maven/maven-3/3.8.5/binaries/apache-maven-3.8.5-bin.tar.gz -P /tmp \
  && sudo tar xf /tmp/apache-maven-*.tar.gz -C /opt \
  && sudo ln -s /opt/apache-maven-* /opt/maven \
  && JAVA_DIR=$(readlink -f /usr/bin/java | sed "s:/jre/bin/java::" | sed "s:/bin/java::") \
  && echo "export JAVA_HOME=${JAVA_DIR}"       | sudo tee /etc/profile.d/maven.sh \
  && echo 'export M2_HOME=/opt/maven'          | sudo tee -a /etc/profile.d/maven.sh \
  && echo 'export MAVEN_HOME=/opt/maven'       | sudo tee -a /etc/profile.d/maven.sh \
  && echo 'export PATH=${M2_HOME}/bin:${PATH}' | sudo tee -a /etc/profile.d/maven.sh \
  && sudo chmod +x /etc/profile.d/maven.sh \
  && source /etc/profile.d/maven.sh \
  && mvn -version
```
___
## License
To be established.