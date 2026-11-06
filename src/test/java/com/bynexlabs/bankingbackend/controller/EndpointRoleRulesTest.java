package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.enums.Roles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.util.SimpleMethodInvocation;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Evaluates each endpoint's @PreAuthorize rule for every staff role, using the same authorization
 * manager Spring Security applies at runtime, so the tests read as the owner-approved role matrix.
 */
class EndpointRoleRulesTest {

    private final PreAuthorizeAuthorizationManager authorizationManager = new PreAuthorizeAuthorizationManager();

    private Set<Roles> rolesAllowedFor(Object controller, String methodName) {
        Method method = Arrays.stream(controller.getClass().getMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow();

        Set<Roles> allowed = EnumSet.noneOf(Roles.class);
        for (Roles role : Roles.values()) {
            Authentication staff = new UsernamePasswordAuthenticationToken(
                    "900100", null, List.of(new SimpleGrantedAuthority(role.name())));
            AuthorizationResult result = authorizationManager.authorize(
                    () -> staff, new SimpleMethodInvocation(controller, method));
            if (result != null && result.isGranted()) {
                allowed.add(role);
            }
        }
        return allowed;
    }

    @Test
    void onlyAdministratorsCreateStaff() {
        assertThat(rolesAllowedFor(new StaffController(null), "addNewStaff"))
                .containsExactly(Roles.ADMINISTRATOR);
    }

    @Test
    void administratorsAndAgentsRegisterMembers() {
        assertThat(rolesAllowedFor(new MemberController(null, null, null), "addNewMember"))
                .containsExactlyInAnyOrder(Roles.ADMINISTRATOR, Roles.AGENT);
    }

    @Test
    void administratorsAndAgentsUploadKycDocuments() {
        assertThat(rolesAllowedFor(new MemberController(null, null, null), "submitKyc"))
                .containsExactlyInAnyOrder(Roles.ADMINISTRATOR, Roles.AGENT);
    }

    @Test
    void administratorsAndAgentsApproveKycManually() {
        assertThat(rolesAllowedFor(new MemberController(null, null, null), "approveKycInfo"))
                .containsExactlyInAnyOrder(Roles.ADMINISTRATOR, Roles.AGENT);
    }

    // Guard for new endpoints: every handler outside /api/auth must state who may call it,
    // so a forgotten annotation fails the build instead of silently allowing every role.
    @Test
    void everyNonAuthEndpointDeclaresItsRoles() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> unprotected = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.bynexlabs.bankingbackend.controller")) {
            Class<?> controller = Class.forName(candidate.getBeanClassName());
            if (controller == AuthController.class) {
                continue;
            }
            for (Method method : controller.getDeclaredMethods()) {
                if (AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)
                        && !method.isAnnotationPresent(PreAuthorize.class)) {
                    unprotected.add(controller.getSimpleName() + "." + method.getName());
                }
            }
        }

        assertThat(unprotected).as("endpoints without @PreAuthorize").isEmpty();
    }
}
