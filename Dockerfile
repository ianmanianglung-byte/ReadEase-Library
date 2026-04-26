FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY LibraryServer.java .
COPY index.html .
RUN javac LibraryServer.java
EXPOSE 8080
CMD ["java", "LibraryServer"]
