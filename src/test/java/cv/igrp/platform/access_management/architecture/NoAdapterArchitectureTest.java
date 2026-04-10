package cv.igrp.platform.access_management.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "cv.igrp.platform.access_management")
public class NoAdapterArchitectureTest {

    @ArchTest
    static final ArchRule no_classes_should_depend_on_iadapter =
            noClasses()
                    .that()
                    .resideInAPackage("cv.igrp.platform.access_management..")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("cv.igrp.framework.auth.core.adapter.IAdapter")
                    .orShould()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("cv.igrp.framework.auth.core.adapter.UserAdapter")
                    .orShould()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("cv.igrp.framework.auth.core.adapter.RoleAdapter")
                    .because("Integration Architecture Definition specifies a NO ADAPTER approach. No application code should interact with IAM providers directly.");

    @ArchTest
    static final ArchRule no_classes_should_use_keycloak_admin_api =
            noClasses()
                    .that()
                    .resideInAPackage("cv.igrp.platform.access_management..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("org.keycloak.admin.client..")
                    .because("Integration Architecture Definition specifies a NO ADAPTER approach. Keycloak Admin APIs must not be used.");

}
