# Etapa 1: Compilación (Build)
FROM maven:3.9.4-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copiar el archivo de configuración de dependencias
COPY pom.xml .

# Descargar dependencias para aprovechar la caché de capas de Docker
RUN mvn dependency:go-offline -B

# Copiar el código fuente
COPY src ./src

# Compilar y generar el JAR saltando los tests
RUN mvn clean package -DskipTests

# Etapa 2: Imagen de ejecución (Runtime)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copiar el JAR generado (gateway-1.0.0-SNAPSHOT.jar según el pom)
COPY --from=build /app/target/gateway-1.0.0-SNAPSHOT.jar app.jar

# Exponer el puerto del Gateway
EXPOSE 8080

# Ejecutar la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]