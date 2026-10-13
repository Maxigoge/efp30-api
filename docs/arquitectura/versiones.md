# Versiones fijadas

La versión de Java de compilación es 21. El proyecto usa Spring Boot 3.5.16 y
Gradle Wrapper 8.14.4; el wrapper valida la distribución con su SHA-256 oficial
`f1771298a70f6db5a29daf62378c4e18a17fc33c9ba6b14362e0cdf40610380d`.
PostgreSQL 18.6 es la versión de referencia para desarrollo, pruebas y
producción.

Spring Boot 3.5.16 documenta soporte para Java 17 a 25 y Gradle 7.6.4 o 8.4 en
adelante. Por eso Java 21 y Gradle 8.14.4 quedan dentro del rango soportado.
PostgreSQL 18.6 es el último parche publicado de la rama 18 al fijar este
proyecto.

La imagen multietapa usa las etiquetas fijadas
`eclipse-temurin:21.0.12.1_1-jdk-noble` y
`eclipse-temurin:21.0.12.1_1-jre-noble`; ambas comparten parche de Java 21 y
Ubuntu Noble. Las etiquetas se verifican en el catálogo oficial de
[Eclipse Temurin en Docker Hub](https://hub.docker.com/_/eclipse-temurin/tags?name=21).
Actualizá ambas al mismo parche al revisar seguridad.
Las versiones compatibles de Spring Framework, Spring Security, Spring Data,
Hibernate, Spring Session, Flyway y el driver PostgreSQL se resuelven desde el
BOM de Spring Boot 3.5.16. No se sobrescriben individualmente. El plugin de
administración de dependencias de Gradle está fijado en 1.1.7; Testcontainers
usa la versión administrada por el mismo BOM.

Referencias oficiales:

- [Requisitos de Spring Boot 3.5.16](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [Gradle 8.14.4](https://docs.gradle.org/8.14.4/release-notes.html)
- [PostgreSQL 18.6](https://www.postgresql.org/about/news/postgresql-186-1711-1615-1519-1424-and-19-beta-3-released-3365/)
