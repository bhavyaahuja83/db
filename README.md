# Student Registry

A Jakarta Servlet and JDBC student-record CRUD application for MySQL 8. The first request to `/students` creates the `studentdb` database and `students` table if they do not already exist. The same SQL is available in `src/main/resources/studentdb.sql` for manual setup.

## MySQL configuration

The application connects to `localhost:3306` as `root` using the settings in `src/main/java/com/mycompany/db/db/DBConnection.java`. The account needs permission to create the database and table on first use, and CRUD permissions afterward. Do not commit real database credentials to a shared repository.

## Build and deploy

Build the WAR with Maven (`mvn package`) and deploy `target/db-1.0-SNAPSHOT.war` to Apache Tomcat 11. Open the deployed application context in a browser; `/` forwards to the Servlet-driven student page. HTML forms post create, update, and delete actions to `/students`; GET `/students` lists and retrieves records for editing.

The MySQL Connector/J driver is packaged in the WAR. The application uses Jakarta Servlet 6.1 and JDBC only; no Jakarta REST or JSON-B endpoints are used.
