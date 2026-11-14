FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /project
COPY app/pom.xml app/pom.xml
COPY build/runtime-pom.xml build/runtime-pom.xml
RUN mvn -B -f app/pom.xml dependency:go-offline
RUN mvn -B -f build/runtime-pom.xml org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies -DincludeScope=runtime -DoutputDirectory=/project/server-libs
COPY app/src app/src
RUN mvn -B -f app/pom.xml clean package

FROM tomcat:10.1-jdk17-temurin
COPY --from=build /project/server-libs/ /usr/local/tomcat/lib/
COPY --from=build /project/app/target/student-profile.war /usr/local/tomcat/webapps/student-profile.war
COPY config/server.xml /usr/local/tomcat/conf/server.xml
COPY config/student-profile.xml /usr/local/tomcat/conf/Catalina/localhost/student-profile.xml
RUN keytool -genkeypair -alias local-demo -keyalg RSA -keysize 2048 -validity 365 -storetype PKCS12 -keystore /usr/local/tomcat/conf/local-demo.p12 -storepass LocalTLSOnly2026 -dname "CN=localhost,OU=Local Lab,O=MCSL222,C=IN" -ext "SAN=dns:localhost,ip:127.0.0.1"
EXPOSE 8443
CMD ["catalina.sh", "run"]
