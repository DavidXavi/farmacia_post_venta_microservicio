package com.posfarmacia.identidad;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La regla de dependencia de identidad, comprobada y no prometida.
 *
 * <p>CLAUDE.md lo dice sin margen: "Las dependencias apuntan hacia adentro. {@code domain} no depende de nada, ni de Spring ni de JPA. Lo impone un test de ArchUnit, no la revision de codigo."
 *
 * <p>Sin esta prueba la regla la sostiene la revision de codigo, que es exactamente
 * como ms-ventas termino con un caso de uso importando su adaptador HTTP: nada fallaba,
 * compilaba igual, y probar una suma obligaba a levantar timeouts y circuit breakers.
 */
class ArquitecturaTest {

    private static JavaClasses clases;

    @BeforeAll
    static void importar() {
        clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.posfarmacia.identidad");
    }

    @Test
    @DisplayName("Las dependencias apuntan hacia adentro")
    void capas() {
        Architectures.layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                // Cinco de los nueve servicios no tienen paquete domain, y esta bien:
                // catalogo, clientes, credito, facturacion y reportes son consulta y
                // altas, sin reglas que modelar. Obligarlos a inventar un dominio vacio
                // para cumplir una plantilla es la clase de ceremonia que esta
                // arquitectura evita. La capa que exista tiene que respetar la regla; la
                // que no exista no es una violacion.
                .withOptionalLayers(true)
                .layer("dominio").definedBy("com.posfarmacia.identidad.domain..")
                .layer("casos de uso").definedBy("com.posfarmacia.identidad.usecases..")
                .layer("adaptadores").definedBy("com.posfarmacia.identidad.adapters..")
                .layer("infraestructura").definedBy("com.posfarmacia.identidad.infrastructure..")

                .whereLayer("adaptadores").mayOnlyBeAccessedByLayers("infraestructura")
                .whereLayer("casos de uso").mayOnlyBeAccessedByLayers("adaptadores", "infraestructura")
                .whereLayer("dominio").mayOnlyBeAccessedByLayers("casos de uso", "adaptadores", "infraestructura")

                .check(clases);
    }

    @Test
    @DisplayName("El dominio no sabe que existe Spring ni JPA")
    void dominioPuro() {
        noClasses()
                .that().resideInAPackage("com.posfarmacia.identidad.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta.persistence..",
                        "com.posfarmacia.identidad.adapters..", "com.posfarmacia.identidad.infrastructure..")
                .because("el dominio es aritmetica y reglas: si necesita un framework para "
                        + "existir, deja de poder probarse sin levantar el framework")
                .allowEmptyShould(true)
                .check(clases);
    }

    @Test
    @DisplayName("Los casos de uso no dependen de los adaptadores")
    void casosDeUsoSinAdaptadores() {
        noClasses()
                .that().resideInAPackage("com.posfarmacia.identidad.usecases..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.posfarmacia.identidad.adapters..",
                        "com.posfarmacia.identidad.infrastructure..")
                .because("lo que el caso de uso necesita de afuera se declara como puerto "
                        + "en usecases.port.out, y el adaptador lo implementa")
                .allowEmptyShould(true)
                .check(clases);
    }
}
