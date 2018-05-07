package org.apereo.cas.web.flow.action;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apereo.cas.authentication.RememberMeCredential;
import org.apereo.cas.authentication.SurrogateUsernamePasswordCredential;
import org.apereo.cas.authentication.UsernamePasswordCredential;
import org.apereo.cas.authentication.adaptive.AdaptiveAuthenticationPolicy;
import org.apereo.cas.web.flow.actions.InitialAuthenticationAction;
import org.apereo.cas.web.flow.resolver.CasDelegatingWebflowEventResolver;
import org.apereo.cas.web.flow.resolver.CasWebflowEventResolver;
import org.apereo.cas.web.support.WebUtils;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;

/**
 * This is {@link SurrogateInitialAuthenticationAction}.
 *
 * @author Misagh Moayyed
 * @since 5.1.0
 */
@Slf4j
public class SurrogateInitialAuthenticationAction extends InitialAuthenticationAction {
    private final String separator;

    public SurrogateInitialAuthenticationAction(final CasDelegatingWebflowEventResolver delegatingWebflowEventResolver,
                                                final CasWebflowEventResolver webflowEventResolver,
                                                final AdaptiveAuthenticationPolicy adaptiveAuthenticationPolicy,
                                                final String separator) {
        super(delegatingWebflowEventResolver, webflowEventResolver, adaptiveAuthenticationPolicy);
        this.separator = separator;
    }

    @Override
    protected Event doPreExecute(final RequestContext context) {
        final var up = WebUtils.getCredential(context, UsernamePasswordCredential.class);
        if (up == null || up instanceof SurrogateUsernamePasswordCredential) {
            LOGGER.debug("Provided credentials cannot be found, or are already of type [{}]", SurrogateUsernamePasswordCredential.class.getName());
            return null;
        }
        if (up.getUsername().contains(this.separator)) {
            LOGGER.debug("Credential username includes the separator [{}]. Converting to surrogate...", this.separator);
            convertToSurrogateCredential(context, up);
        }
        return null;
    }

    private void convertToSurrogateCredential(final RequestContext context, final UsernamePasswordCredential up) {
        final var sc = new SurrogateUsernamePasswordCredential();

        final var tUsername = up.getUsername();
        final var surrogateUsername = tUsername.substring(0, tUsername.indexOf(this.separator));
        final var realUsername = tUsername.substring(tUsername.indexOf(this.separator) + 1);
        LOGGER.debug("Converting to surrogate credential for username [{}], surrogate username [{}]", realUsername, surrogateUsername);

        if (StringUtils.isBlank(surrogateUsername)) {
            up.setUsername(realUsername);
            WebUtils.putRequestSurrogateAuthentication(context, Boolean.TRUE);
            WebUtils.putCredential(context, up);

            LOGGER.debug("No surrogate username is defined; Signal webflow to request for surrogate credentials");
            return;
        }

        sc.setUsername(realUsername);
        sc.setSurrogateUsername(surrogateUsername);
        sc.setPassword(up.getPassword());
        if (up instanceof RememberMeCredential) {
            sc.setRememberMe(((RememberMeCredential) up).isRememberMe());
        }
        WebUtils.putRequestSurrogateAuthentication(context, Boolean.FALSE);
        LOGGER.debug("Converted credential to surrogate for username [{}] and assigned it to webflow", realUsername);
        WebUtils.putCredential(context, sc);
    }
}
