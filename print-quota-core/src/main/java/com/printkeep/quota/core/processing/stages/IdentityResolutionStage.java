package com.printkeep.quota.core.processing.stages;

import com.printkeep.quota.codec.model.IppAttribute;
import com.printkeep.quota.codec.model.IppAttributeGroup;
import com.printkeep.quota.codec.model.IppTag;
import com.printkeep.quota.core.identity.service.IdentityService;
import com.printkeep.quota.core.model.User;
import com.printkeep.quota.core.processing.decision.IppDecision;
import com.printkeep.quota.core.processing.pipeline.PipelineContext;
import com.printkeep.quota.core.processing.pipeline.PipelineStage;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Identity stage resolving the requesting print user and checking active/disabled account flags.
 */
@Component
@Order(2)
public class IdentityResolutionStage implements PipelineStage {

    private final IdentityService identityService;

    public IdentityResolutionStage(final IdentityService identityService) {
        this.identityService = identityService;
    }

    @Override
    public void process(final PipelineContext context) {
        final Optional<IppAttributeGroup> groupOpt = context.getIppPacket().getGroup(IppTag.OPERATION_ATTRIBUTES);
        if (groupOpt.isEmpty()) {
            context.setDecision(IppDecision.REJECT_INVALID_REQUEST);
            context.setReason("Missing Operation Attributes group");
            return;
        }

        final IppAttributeGroup opGroup = groupOpt.get();
        final Optional<IppAttribute> userAttrOpt = opGroup.getAttribute("requesting-user-name");

        if (userAttrOpt.isEmpty()) {
            context.setDecision(IppDecision.REJECT_INVALID_REQUEST);
            context.setReason("Missing mandatory attribute: requesting-user-name");
            return;
        }

        final String username = userAttrOpt.get().getValue().toString();
        final Optional<User> userOpt = identityService.findUser(username);

        if (userOpt.isEmpty()) {
            context.setDecision(IppDecision.REJECT_UNKNOWN_USER);
            context.setReason("Unknown user: " + username);
            return;
        }

        final User user = userOpt.get();
        if (!user.isActive()) {
            context.setDecision(IppDecision.REJECT_DISABLED_USER);
            context.setReason("User account is disabled: " + username);
            return;
        }

        context.setUser(user);
    }
}
