# Build y runtime separados: la imagen final no lleva Maven ni el codigo fuente.
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /src

# Se copian TODOS los pom.xml antes que cualquier src. Dos razones:
#  - el pom raiz declara los trece modulos del reactor, y Maven falla si falta uno;
#  - los pom cambian poco, asi que la capa de dependencias se reutiliza entre builds
#    y cambiar una linea de codigo no vuelve a bajar internet.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY contracts/pom.xml contracts/
COPY plataforma/pom.xml plataforma/
COPY services/api-gateway/pom.xml services/api-gateway/
COPY services/ms-identidad/pom.xml services/ms-identidad/
COPY services/ms-catalogo/pom.xml services/ms-catalogo/
COPY services/ms-inventario/pom.xml services/ms-inventario/
COPY services/ms-clientes/pom.xml services/ms-clientes/
COPY services/ms-credito/pom.xml services/ms-credito/
COPY services/ms-promociones/pom.xml services/ms-promociones/
COPY services/ms-ventas/pom.xml services/ms-ventas/
COPY services/ms-facturacion/pom.xml services/ms-facturacion/
COPY services/ms-reportes/pom.xml services/ms-reportes/

RUN ./mvnw -q -pl contracts,services/api-gateway -am dependency:go-offline -DskipTests

# Solo los fuentes que esta imagen necesita: nada de copiar los nueve servicios.
COPY contracts/src contracts/src
COPY services/api-gateway/src services/api-gateway/src
RUN ./mvnw -q -pl contracts,services/api-gateway -am package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S pos && adduser -S pos -G pos
COPY --from=build /src/services/api-gateway/target/*.jar app.jar
USER pos
EXPOSE 8080
# MaxRAMPercentage y no -Xmx: la JVM respeta el limite del contenedor, asi el mismo
# comando sirve en la laptop y en Kubernetes con otro limite.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
