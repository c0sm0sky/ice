# Ice

Aplikacja logowania i zarządzania użytkownikami. Jest przeznaczona do uruchomienia jako WAR w WildFly 26.1.3 z Java 8. Widoki JSF 1.1 są obsługiwane przez MyFaces i ICEfaces 1.8.2; nawigację prowadzi Spring Web Flow 1.0.5. PostgreSQL przechowuje konta, a Hibernate ORM mapuje encję użytkownika.

## Wersje

- Java 8
- Apache Maven 2.2.1
- JSF 1.1 / MyFaces 1.1.10
- ICEfaces 1.8.2
- Spring Framework 2.0.6
- Spring Web Flow 1.0.5, definicja przepływu 1.0
- Hibernate ORM 3.6.10.Final
- PostgreSQL 18

## Uruchomienie

Wymagany jest WildFly 26.1.3 uruchomiony na Java 8. Ustaw `JAVA_HOME` na JDK 8 i `WILDFLY_HOME` na katalog serwera.

Uruchom lokalną bazę PostgreSQL:

```sh
docker compose up -d postgres
```

Zbuduj WAR przy użyciu Apache Maven 2.2.1 i Java 8:

```sh
export JAVA_HOME="/ścieżka/do/jdk8/Contents/Home"
export PATH="/ścieżka/do/apache-maven-2.2.1/bin:$PATH"
MAVEN_OPTS="-Dhttps.protocols=TLSv1.2" mvn clean package
```

Wdróż aplikację:

```sh
cp target/ice.war "$WILDFLY_HOME/standalone/deployments/"
```

Otwórz `http://localhost:8080/ice/`. Konto demonstracyjne: `admin` / `admin`.

Konfigurację PostgreSQL można nadpisać właściwościami systemowymi:

```sh
-Dice.db.url=jdbc:postgresql://localhost:5434/ice
-Dice.db.username=ice
-Dice.db.password=ice_dev
```

Pierwsze uruchomienie tworzy tabelę `users` z kolumnami `id`, `login`, `haslo`, `imie` i `nazwisko` oraz konto `admin` / `admin`. Hibernate tworzy schemat na podstawie mapowania encji.

## Eksperymentalna migracja ICEfaces do PrimeFaces
Migracja jest odtwarzalna i nie zmienia źródłowego wariantu ICE. Wersje docelowe znajdują się w sekcji `<properties>` pliku `pom.xml` jako `migration.*`; recepty OpenRewrite są w `rewrite.yml`.

Własne recepty są w module `rewrite-plugins/`. Moduł ponownie wykorzystuje oficjalne recepty OpenRewrite dostępne w Maven Central dla migracji Jakarta, Spring Framework i Hibernate, a uzupełnia je o brakujące transformacje, między innymi ICEfaces do PrimeFaces oraz konfigurację Spring, Web Flow i WildFly. Recepty można ponownie uruchamiać i rozwijać dla projektów ze zgodnym stosem. Część jest ogólna, a część dopasowana do struktury tej aplikacji.

Profil wymaga JDK 25 i Maven 3.6.3 lub nowszego. Tworzy kompletny, samodzielny projekt PrimeFaces w `primefaces-migrated/`, usuwa z jego POM-a profil migracyjny, a następnie buduje WAR właśnie z tego projektu. Źródła ICE pozostają bez zmian:

```sh
mvn clean install -P primefaces-migration
```

`mvn clean -P primefaces-migration` usuwa również wygenerowany katalog `primefaces-migrated/`.

Wynikowy projekt można potem budować niezależnie od ICE i profilu migracyjnego:

```sh
cd primefaces-migrated
mvn clean package
```

Profil migracyjny zapisuje WAR wyłącznie w `primefaces-migrated/target/prime.war`, bez ICEfaces i MyFaces, gotowy do wdrożenia na WildFly 42 z Java 25. Bazowy build bez profilu pozostaje przy Java 8 i tworzy `target/ice.war`.
