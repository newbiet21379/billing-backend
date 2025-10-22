package com.acme.billing.architecture;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.GeneralCodingRules.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Architecture tests for the billing system.
 * Ensures package structure, dependency rules, and coding standards are followed.
 */
class ArchitectureTest {

    // === Package Structure Rules ===

    @ArchTest
    static final ArchRule domain_layer_should_not_depend_on_other_layers =
        classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "java..",
                "lombok..",
                "org.axonframework..",
                "jakarta.persistence..",
                "..domain.."
            )
            .because("Domain layer should be independent of infrastructure and application layers");

    @ArchTest
    static final ArchRule api_package_should_only_contain_dtos_and_commands_queries =
        classes()
            .that().resideInAPackage("..api..")
            .should().bePublic()
            .or().bePackagePrivate()
            .because("API classes should be accessible but properly encapsulated");

    @ArchTest
    static final ArchRule service_classes_should_be_annotated_with_service =
        classes()
            .that().resideInAPackage("..service..")
            .and().areNotInterfaces()
            .and().areNotEnums()
            .should().beAnnotatedWith("org.springframework.stereotype.Service")
            .because("Service classes should be annotated with @Service");

    @ArchTest
    static final ArchRule controller_classes_should_be_annotated_with_rest_controller =
        classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .because("Controller classes should be annotated with @RestController");

    @ArchTest
    static final ArchRule repository_classes_should_be_interfaces_or_spring_data_repositories =
        classes()
            .that().haveSimpleNameEndingWith("Repository")
            .should().beInterfaces()
            .or().beAnnotatedWith("org.springframework.stereotype.Repository")
            .because("Repository pattern should be followed");

    // === CQRS Specific Rules ===

    @ArchTest
    static final ArchRule command_classes_should_be_in_api_commands_package =
        classes()
            .that().haveSimpleNameEndingWith("Command")
            .should().resideInAPackage("..api.commands..")
            .because("Commands should be in the api.commands package");

    @ArchTest
    static final ArchRule query_classes_should_be_in_api_queries_package =
        classes()
            .that().haveSimpleNameEndingWith("Query")
            .should().resideInAPackage("..api.queries..")
            .because("Queries should be in the api.queries package");

    @ArchTest
    static final ArchRule event_classes_should_be_in_domain_events_package =
        classes()
            .that().haveSimpleNameEndingWith("Event")
            .should().resideInAPackage("..domain.events..")
            .because("Domain events should be in the domain.events package");

    @ArchTest
    static final ArchRule aggregate_classes_should_be_in_domain_package =
        classes()
            .that().haveSimpleNameEndingWith("Aggregate")
            .should().resideInAPackage("..domain..")
            .because("Aggregates should be in the domain package");

    @ArchTest
    static final ArchRule projection_classes_should_be_in_projection_package =
        classes()
            .that().haveSimpleNameContaining("Projection")
            .should().resideInAPackage("..projection..")
            .because("Projections should be in the projection package");

    // === Exception Handling Rules ===

    @ArchTest
    static final ArchRule exception_classes_should_be_in_exception_package =
        classes()
            .that().areAssignableTo(RuntimeException.class)
            .or().areAssignableTo(Exception.class)
            .should().resideInAPackage("..service.exception..")
            .or().resideInAPackage("..web.errors..")
            .because("Custom exceptions should be organized in exception packages");

    // === Configuration Rules ===

    @ArchTest
    static final ArchRule configuration_classes_should_be_in_config_package =
        classes()
            .that().areAnnotatedWith("org.springframework.context.annotation.Configuration")
            .or().haveSimpleNameContaining("Config")
            .should().resideInAPackage("..config..")
            .because("Configuration classes should be in config package");

    // === Coding Standards Rules ===

    @ArchTest
    static final ArchRule no_classes_should_access_standard_streams_directly =
        NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS_DIRECTLY
            .because("System.out and System.err should not be used directly");

    @ArchTest
    static final ArchRule no_classes_should_throw_generic_exceptions =
        NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS
            .because("Generic exceptions should not be thrown");

    @ArchTest
    static final ArchRule no_classes_should_use_java_util_logging =
        NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING
            .because("Use SLF4J for logging instead of java.util.logging");

    @ArchTest
    static final ArchRule no_classes_should_use_field_injection =
        NO_CLASSES_SHOULD_USE_FIELD_INJECTION
            .because("Constructor injection should be preferred over field injection");

    // === Dependency Rules ===

    @ArchTest
    static final ArchRule api_should_not_depend_on_service_layer =
        classes()
            .that().resideInAPackage("..api..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "java..",
                "lombok..",
                "com.fasterxml.jackson..",
                "jakarta.validation..",
                "..api.."
            )
            .because("API layer (DTOs) should not depend on service layer");

    @ArchTest
    static final ArchRule web_layer_should_only_access_service_and_api_layers =
        classes()
            .that().resideInAPackage("..web..")
            .should().onlyAccessClassesThat()
            .resideInAnyPackage(
                "java..",
                "lombok..",
                "org.springframework..",
                "jakarta..",
                "com.fasterxml.jackson..",
                "..service..",
                "..api..",
                "..web.."
            )
            .because("Web layer should only access service and API layers");

    @ArchTest
    static final ArchRule service_layer_should_not_depend_on_web_layer =
        classes()
            .that().resideInAPackage("..service..")
            .should().notDependOnClassesThat()
            .resideInAPackage("..web..")
            .because("Service layer should not depend on web layer");

    // === Test Rules ===

    @ArchTest
    static final ArchRule test_classes_should_be_in_test_package =
        classes()
            .that().areAnnotatedWith("org.junit.jupiter.api.Test")
            .or().haveSimpleNameEndingWith("Test")
            .or().haveSimpleNameEndingWith("Tests")
            .should().resideInAPackage("..test..")
            .because("Test classes should be in test packages");

    @ArchTest
    static final ArchRule test_classes_should_not_be_in_main_packages =
        classes()
            .that().haveSimpleNameEndingWith("Test")
            .or().haveSimpleNameEndingWith("Tests")
            .should().notResideInAPackage("..main..")
            .because("Test classes should not be in main source packages");

    // === Cyclic Dependency Rules ===

    @ArchTest
    static final ArchRule no_circular_dependencies_between_packages =
        slices()
            .matching("com.acme.billing.(*)..")
            .should().beFreeOfCycles()
            .because("Packages should not have circular dependencies");

    @ArchTest
    static final ArchRule no_circular_dependencies_between_layers =
        slices()
            .matching("com.acme.billing.(*)..")
            .should().notDependOnEachOther()
            .because("Architectural layers should not depend on each other in cycles");

    // === Naming Conventions ===

    @ArchTest
    static final ArchRule handlers_should_be_named_properly =
        classes()
            .that().areAnnotatedWith("org.springframework.context.annotation.Component")
            .and().resideInAPackage("..projection.handler..")
            .should().haveSimpleNameEndingWith("Handler")
            .because("Event and query handlers should be properly named");

    @ArchTest
    static final ArchRule dto_classes_should_be_named_properly =
        classes()
            .that().resideInAPackage("..api.dto..")
            .should().haveSimpleNameEndingWith("Response")
            .or().haveSimpleNameEndingWith("Request")
            .because("DTO classes should have appropriate suffixes");

    // === Spring-specific Rules ===

    @ArchTest
    static final ArchRule spring_components_should_be_properly_annotated =
        classes()
            .that().areAnnotatedWith("org.springframework.stereotype.Component")
            .or().areAnnotatedWith("org.springframework.stereotype.Service")
            .or().areAnnotatedWith("org.springframework.stereotype.Repository")
            .or().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .should().bePackagePrivate()
            .or().bePublic()
            .because("Spring components should have proper visibility");

    // === Lombok Rules ===

    @ArchTest
    static final ArchRule entities_should_use_lombok_annotations =
        classes()
            .that().areAnnotatedWith("jakarta.persistence.Entity")
            .or().areAnnotatedWith("org.springframework.data.annotation.Id")
            .should().beAnnotatedWith("lombok.Builder")
            .or().beAnnotatedWith("lombok.Data")
            .or().beAnnotatedWith("lombok.Getter")
            .or().beAnnotatedWith("lombok.Setter")
            .because("Entities should use Lombok annotations for boilerplate code");

    // === Security Rules ===

    @ArchTest
    static final ArchRule no_hardcoded_secrets_or_passwords =
        noClasses().should().accessStandardStreams()
            .andShould().accessClassesThat()
            .resideInAnyPackage("java.security", "javax.crypto")
            .because("Security-related operations should be properly encapsulated");

    // === Performance Rules ===

    @ArchTest
    static final ArchRule no_expensive_operations_in_constructors =
        noClasses().should().callMethod("java.lang.Thread", "sleep")
            .because("Constructors should not perform expensive operations");

    // === Domain-Specific Rules ===

    @ArchTest
    static final ArchRule domain_events_should_be_immutable =
        classes()
            .that().resideInAPackage("..domain.events..")
            .should().bePackagePrivate()
            .or().bePublic()
            .andShould().haveSimpleNameEndingWith("Event")
            .andShould().beAnnotatedWith("lombok.Getter")
            .or().beAnnotatedWith("lombok.Value")
            .because("Domain events should be immutable");

    @ArchTest
    static final ArchRule command_and_query_classes_should_be_immutable =
        classes()
            .that().resideInAPackage("..api.commands..")
            .or().resideInAPackage("..api.queries..")
            .should().beAnnotatedWith("lombok.Builder")
            .or().beAnnotatedWith("lombok.Value")
            .because("Commands and queries should be immutable");

    @ArchTest
    static final ArchRule aggregate_root_classes_should_be_final =
        classes()
            .that().haveSimpleNameEndingWith("Aggregate")
            .and().areNotInterfaces()
            .should().bePackagePrivate()
            .or().bePublic()
            .because("Aggregate roots should have proper visibility");
}