package com.printkeep.quota.core.processing.stages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.printkeep.quota.codec.model.IppAttribute;
import com.printkeep.quota.codec.model.IppAttributeGroup;
import com.printkeep.quota.codec.model.IppPacket;
import com.printkeep.quota.codec.model.IppTag;
import com.printkeep.quota.core.identity.service.IdentityService;
import com.printkeep.quota.core.model.User;
import com.printkeep.quota.core.processing.decision.IppDecision;
import com.printkeep.quota.core.processing.pipeline.PipelineContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests verifying IdentityResolutionStage.
 */
class IdentityResolutionStageTests {

    private IdentityResolutionStage stage;
    private IdentityService identityService;

    @BeforeEach
    void setUp() {
        identityService = mock(IdentityService.class);
        stage = new IdentityResolutionStage(identityService);
    }

    @Test
    void testActiveUserResolvesSuccessfully() {
        final User user = new User();
        user.setDomainUsername("company\\jdoe");
        user.setActive(true);

        when(identityService.findUser("jdoe")).thenReturn(Optional.of(user));

        final IppAttribute reqUser = new IppAttribute("requesting-user-name", IppTag.NAME_WITHOUT_LANGUAGE, List.of("jdoe"));
        final IppAttributeGroup opGroup = new IppAttributeGroup(IppTag.OPERATION_ATTRIBUTES, List.of(reqUser));
        final IppPacket packet = new IppPacket((byte) 2, (byte) 0, (short) 0x0002, 1, List.of(opGroup), new byte[0]);

        final PipelineContext context = new PipelineContext(packet, "corr-123", "localhost", "jdoe");
        stage.process(context);

        assertThat(context.getUser()).isEqualTo(user);
        assertThat(context.getDecision()).isNull();
    }

    @Test
    void testDisabledUserRejects() {
        final User user = new User();
        user.setDomainUsername("company\\disabled_user");
        user.setActive(false);

        when(identityService.findUser("disabled_user")).thenReturn(Optional.of(user));

        final IppAttribute reqUser = new IppAttribute("requesting-user-name", IppTag.NAME_WITHOUT_LANGUAGE, List.of("disabled_user"));
        final IppAttributeGroup opGroup = new IppAttributeGroup(IppTag.OPERATION_ATTRIBUTES, List.of(reqUser));
        final IppPacket packet = new IppPacket((byte) 2, (byte) 0, (short) 0x0002, 1, List.of(opGroup), new byte[0]);

        final PipelineContext context = new PipelineContext(packet, "corr-123", "localhost", "disabled_user");
        stage.process(context);

        assertThat(context.getDecision()).isEqualTo(IppDecision.REJECT_DISABLED_USER);
    }

    @Test
    void testMixedCaseCertificateCommonNameMatchesUsername() {
        final User user = new User();
        user.setDomainUsername("company\\jdoe");
        user.setActive(true);

        when(identityService.findUser("jdoe")).thenReturn(Optional.of(user));

        final IppPacket packet = packetForUsername("jdoe");
        final PipelineContext context = new PipelineContext(packet, "corr-123", "10.0.0.7", "JDOE");
        stage.process(context);

        assertThat(context.getUser()).isEqualTo(user);
        assertThat(context.getDecision()).isNull();
    }

    @Test
    void testCertificateUsernameMismatchRejectsBeforeLookup() {
        final IppPacket packet = packetForUsername("jdoe");
        final PipelineContext context = new PipelineContext(packet, "corr-123", "10.0.0.7", "asmith");
        stage.process(context);

        assertThat(context.getDecision()).isEqualTo(IppDecision.REJECT_UNKNOWN_USER);
        assertThat(context.getReason()).contains("does not match");
        verify(identityService, never()).findUser("jdoe");
    }

    @Test
    void testMissingCertificateCommonNameRejectsBeforeLookup() {
        final IppPacket packet = packetForUsername("jdoe");
        final PipelineContext context = new PipelineContext(packet, "corr-123", "10.0.0.7", null);
        stage.process(context);

        assertThat(context.getDecision()).isEqualTo(IppDecision.REJECT_INVALID_REQUEST);
        assertThat(context.getReason()).contains("Missing verified client certificate identity");
        verify(identityService, never()).findUser("jdoe");
    }

    @Test
    void testEmptyCertificateCommonNameRejectsBeforeLookup() {
        final IppPacket packet = packetForUsername("jdoe");
        final PipelineContext context = new PipelineContext(packet, "corr-123", "10.0.0.7", " ");
        stage.process(context);

        assertThat(context.getDecision()).isEqualTo(IppDecision.REJECT_INVALID_REQUEST);
        assertThat(context.getReason()).contains("Missing verified client certificate identity");
        verify(identityService, never()).findUser("jdoe");
    }

    private static IppPacket packetForUsername(final String username) {
        final IppAttribute reqUser = new IppAttribute(
                "requesting-user-name",
                IppTag.NAME_WITHOUT_LANGUAGE,
                List.of(username)
        );
        final IppAttributeGroup opGroup = new IppAttributeGroup(IppTag.OPERATION_ATTRIBUTES, List.of(reqUser));
        return new IppPacket((byte) 2, (byte) 0, (short) 0x0002, 1, List.of(opGroup), new byte[0]);
    }
}
