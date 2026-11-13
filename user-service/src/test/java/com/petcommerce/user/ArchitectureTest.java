package com.petcommerce.user;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Package boundary rules (R-STR-03). The folder a class lives in defines its job:
 * api = HTTP in/out, application = business use cases, domain = core model and rules,
 * security = auth concerns, messaging = Kafka, persistence = database, client = outbound HTTP.
 * Rules that match no classes in this service pass trivially (allowEmptyShould).
 */
class ArchitectureTest {

    private static final String BASE = "com.petcommerce.user";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    @Test
    void domainDependsOnNothingOutsideDomain() {
        noClasses()
                .that().resideInAPackage(BASE + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".api..", BASE + ".application..", BASE + ".messaging..", BASE + ".persistence..",
                        BASE + ".security..", BASE + ".client..", BASE + ".config..", BASE + ".routing..")
                .because("the domain model must not know about HTTP, Kafka, persistence or security")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void domainHasNoFrameworkPlumbing() {
        noClasses()
                .that().resideInAPackage(BASE + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..", "org.springframework.kafka..", "org.springframework.http..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void controllersOnlyTalkToApplication() {
        noClasses()
                .that().resideInAPackage(BASE + ".api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".persistence..", BASE + ".messaging..", BASE + ".client..")
                .because("controllers delegate to application services; they never touch repositories or Kafka")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void consumersOnlyTalkToApplication() {
        noClasses()
                .that().resideInAPackage(BASE + ".messaging.consumer..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".persistence..", BASE + ".api..", BASE + ".client..", BASE + ".messaging.publisher..")
                .because("Kafka listeners only translate messages into application calls")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void applicationIsIndependentOfTransport() {
        noClasses()
                .that().resideInAPackage(BASE + ".application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".api.controller..", BASE + ".api.error..", BASE + ".messaging.consumer..",
                        "org.springframework.kafka..", "org.springframework.web..")
                .because("business logic must not depend on controllers, HTTP error mapping or Kafka plumbing")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void repositoriesUsedOnlyByApplicationAndPublishers() {
        classes()
                .that().resideInAPackage(BASE + ".persistence.repository..")
                .should().onlyBeAccessed().byAnyPackage(
                        BASE + ".application..", BASE + ".messaging.publisher..", BASE + ".persistence..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void restControllersLiveInApiController() {
        classes()
                .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .should().resideInAPackage(BASE + ".api.controller..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void exceptionHandlersLiveInApiError() {
        classes()
                .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestControllerAdvice.class)
                .should().resideInAPackage(BASE + ".api.error..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void businessExceptionsLiveInDomainException() {
        classes()
                .that().areAssignableTo(RuntimeException.class)
                .and().resideInAPackage(BASE + "..")
                .should().resideInAPackage(BASE + ".domain.exception..")
                .because("business exceptions are part of the domain; HTTP mapping lives in api.error")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void kafkaListenersLiveInMessagingConsumer() {
        methods()
                .that().areAnnotatedWith("org.springframework.kafka.annotation.KafkaListener")
                .should().beDeclaredInClassesThat().resideInAPackage(BASE + ".messaging.consumer..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void noDependencyOnOtherServices() {
        noClasses()
                .that().resideInAPackage(BASE + "..")
                .should().dependOnClassesThat(
                        DescribedPredicate.describe("another service's classes",
                                (JavaClass c) -> c.getPackageName().startsWith("com.petcommerce.")
                                        && !c.getPackageName().startsWith(BASE)))
                .because("services share contracts (JSON events, HTTP), never code")
                .check(CLASSES);
    }
}
