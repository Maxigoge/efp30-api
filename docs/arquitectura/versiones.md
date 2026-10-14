# Versiones fijadas

La versión de Java de compilación es 21. El proyecto usa Spring Boot 3.5.16 y
Gradle Wrapper 8.14.4; el wrapper valida la distribución con su SHA-256 oficial
`f1771298a70f6db5a29daf62378c4e18a17fc33c9ba6b14362e0cdf40610380d`.
MySQL Community Server 8.4.11 es la versión fijada para la imagen de pruebas y
la versión mínima de referencia de producción en este hito. Es una publicación
de la serie 8.4 LTS del 28 de julio de 2026. El equipo tiene MySQL 8.0.43
instalado localmente; esa instalación no se configura ni se usa para validar
este repositorio y no es el objetivo de producción.

La serie MySQL 8.0 llegó al fin de mantenimiento en abril de 2026. Para evitar
fijar una instalación local a una rama fuera de mantenimiento, la prueba de
migraciones usa `mysql:8.4.11` en Testcontainers. La compatibilidad del esquema
se debe confirmar ejecutando `MySqlMigrationTest` con Docker disponible antes
de actualizar el repositorio de infraestructura.

Spring Boot 3.5.16 documenta soporte para Java 17 a 25 y Gradle 7.6.4 o 8.4 en
adelante. Por eso Java 21 y Gradle 8.14.4 quedan dentro del rango soportado.
Las versiones compatibles de Spring Framework, Spring Security, Spring Data,
Hibernate, Spring Session, Flyway y Connector/J se resuelven desde el BOM de
Spring Boot 3.5.16. Flyway usa el módulo de base de datos separado
`flyway-mysql`. El plugin de administración de dependencias de Gradle está
fijado en 1.1.7; Testcontainers usa la versión administrada por el mismo BOM.

La API almacena IDs como `CHAR(36)` y UUID en UTC como `DATETIME(6)`. La URL JDBC
fuerza la sesión MySQL a UTC; la capa JDBC convierte fechas a UTC al escribir y
las devuelve con el offset vigente de Argentina. No se depende de las tablas de
zonas horarias instaladas en el servidor MySQL.

La imagen multietapa usa las etiquetas fijadas
`eclipse-temurin:21.0.12.1_1-jdk-noble` y
`eclipse-temurin:21.0.12.1_1-jre-noble`; ambas comparten parche de Java 21 y
Ubuntu Noble. Las etiquetas se verifican en el catálogo oficial de
[Eclipse Temurin en Docker Hub](https://hub.docker.com/_/eclipse-temurin/tags?name=21).
Actualizá ambas al mismo parche al revisar seguridad.

Referencias oficiales:

- [Requisitos de Spring Boot 3.5.16](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [Gradle 8.14.4](https://docs.gradle.org/8.14.4/release-notes.html)
- [MySQL 8.4.11](https://dev.mysql.com/doc/relnotes/mysql/8.4/en/news-8-4-11.html)
- [Fin de mantenimiento de MySQL 8.0](https://dev.mysql.com/doc/relnotes/mysql/8.0/en/)
- [Opciones de zona horaria de Connector/J](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-datetime-types-processing.html)
- [Módulo Flyway para MySQL](https://documentation.red-gate.com/flyway/reference/database-driver-reference/mysql)
