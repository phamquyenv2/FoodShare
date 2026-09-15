plugins {
	java
	jacoco
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.datn"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	
	// OpenAPI / Swagger UI
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5")

	// Load .env files automatically
	implementation("me.paulschwarz:spring-dotenv:4.0.0")

	// JWT (JJWT)
	implementation("io.jsonwebtoken:jjwt-api:0.12.5")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")

	// Cloudinary
	implementation("com.cloudinary:cloudinary-http44:1.38.0")

	// Firebase Admin SDK
	implementation("com.google.firebase:firebase-admin:9.3.0")

	// Google API Client for ID Token verification
	implementation("com.google.api-client:google-api-client:2.4.0")
	implementation("com.google.http-client:google-http-client-gson:1.44.1")

	compileOnly("org.projectlombok:lombok")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("com.mysql:mysql-connector-j")
	runtimeOnly("org.flywaydb:flyway-mysql")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-mail-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testRuntimeOnly("com.h2database:h2")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

fun Test.useFoodShareTestRuntime() {
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	useJUnitPlatform()
}

tasks.register<Test>("unitTest") {
	description = "Runs isolated JUnit/Mockito tests without Spring MVC or a database."
	group = "verification"
	useFoodShareTestRuntime()
	include("**/service/**/*Test.class")
	include("**/event/**/*Test.class")
	include("**/security/JwtTokenProviderTest.class")
	include("**/security/GoogleTokenVerifierTest.class")
	exclude("**/*IntegrationTest.class")
}

tasks.register<Test>("componentTest") {
	description = "Runs Spring MVC slice and security component tests."
	group = "verification"
	useFoodShareTestRuntime()
	include("**/controller/**/*Test.class")
	include("**/security/*MvcTest.class")
}

tasks.register<Test>("integrationTest") {
	description = "Runs database, transaction, and application-context integration tests."
	group = "verification"
	useFoodShareTestRuntime()
	include("**/integration/**/*IntegrationTest.class")
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required = true
		html.required = true
	}
}

tasks.register<JacocoReport>("jacocoUnitTestReport") {
	description = "Generates JaCoCo coverage from isolated unit tests only."
	group = "verification"
	dependsOn(tasks.named("unitTest"))
	executionData(layout.buildDirectory.file("jacoco/unitTest.exec"))
	sourceSets(sourceSets.main.get())
	reports {
		xml.required = true
		html.required = true
		xml.outputLocation = layout.buildDirectory.file("reports/jacoco/unitTest/jacocoUnitTestReport.xml")
		html.outputLocation = layout.buildDirectory.dir("reports/jacoco/unitTest/html")
	}
}
