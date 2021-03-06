/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.service.uma;

import org.gluu.site.ldap.persistence.exception.EntryPersistenceException;
import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.*;
import org.jboss.seam.log.Log;
import org.xdi.oxauth.model.common.AuthorizationGrant;
import org.xdi.oxauth.model.common.AuthorizationGrantList;
import org.xdi.oxauth.model.common.uma.UmaRPT;
import org.xdi.oxauth.model.configuration.AppConfiguration;
import org.xdi.oxauth.model.error.ErrorResponseFactory;
import org.xdi.oxauth.model.uma.UmaErrorResponseType;
import org.xdi.oxauth.model.uma.UmaPermission;
import org.xdi.oxauth.model.uma.UmaScopeType;
import org.xdi.oxauth.model.uma.persistence.ResourceSet;
import org.xdi.oxauth.model.uma.persistence.ResourceSetPermission;
import org.xdi.oxauth.service.token.TokenService;
import org.xdi.util.StringHelper;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.UNAUTHORIZED;
import static org.xdi.oxauth.model.uma.UmaErrorResponseType.*;

/**
 * @author Yuriy Zabrovarnyy
 * @version 0.9, 04/02/2013
 */
@Scope(ScopeType.STATELESS)
@Name("umaValidationService")
@AutoCreate
public class UmaValidationService {

    @Logger
    private Log log;
    @In
    private ErrorResponseFactory errorResponseFactory;
    @In
    private TokenService tokenService;
    @In
    private AuthorizationGrantList authorizationGrantList;
    @In
   	private ResourceSetService resourceSetService;
    @In
    private ScopeService umaScopeService;
    @In
    private AppConfiguration appConfiguration;

    public String validateAmHost(String host) {
        if (StringHelper.isEmpty(host)) {
            log.error("AM host is invalid");
            throw new WebApplicationException(Response.status(BAD_REQUEST)
                    .entity(errorResponseFactory.getUmaJsonErrorResponse(UmaErrorResponseType.INVALID_REQUEST)).build());
        }

        try {
            new URI(host);
        } catch (URISyntaxException ex) {
            log.error("Failed to parse AM host: '{0}'", ex, host);
            throw new WebApplicationException(Response.status(BAD_REQUEST)
                    .entity(errorResponseFactory.getUmaJsonErrorResponse(UmaErrorResponseType.INVALID_REQUEST)).build());
        }

        try {
            URI umaBaseEndpoint = new URI(appConfiguration.getBaseEndpoint());
            if (!StringHelper.equalsIgnoreCase(host, umaBaseEndpoint.getHost())) {
                log.error("Get request for another AM: '{0}'", host);
                throw new WebApplicationException(Response.status(BAD_REQUEST)
                        .entity(errorResponseFactory.getUmaJsonErrorResponse(UmaErrorResponseType.INVALID_REQUEST)).build());
            }
        } catch (URISyntaxException ex) {
            log.error("Failed to parse AM host: '{0}'", ex, host);
            throw new WebApplicationException(Response.status(BAD_REQUEST)
                    .entity(errorResponseFactory.getUmaJsonErrorResponse(UmaErrorResponseType.INVALID_REQUEST)).build());
        }

        return StringHelper.toLowerCase(host).trim();
    }

//    public String validateHost(String host) {
//   		if (StringHelper.isEmpty(host)) {
//   			log.error("Host is invalid");
//   			throw new WebApplicationException(Response.status(BAD_REQUEST)
//   					.entity(errorResponseFactory.getUmaJsonErrorResponse(UmaErrorResponseType.INVALID_REQUEST)).build());
//   		}
//
//   		try {
//   			new URI(host);
//   		} catch (URISyntaxException ex) {
//   			log.error("Failed to parse host: '{0}'", ex, host);
//   			throw new WebApplicationException(Response.status(BAD_REQUEST)
//   					.entity(errorResponseFactory.getUmaJsonErrorResponse(UmaErrorResponseType.INVALID_REQUEST)).build());
//   		}
//
//   		return StringHelper.toLowerCase(host).trim();
//   	}


    public AuthorizationGrant assertHasProtectionScope(String authorization) {
        return validateAuthorization(authorization, UmaScopeType.PROTECTION);
    }

    public AuthorizationGrant assertHasAuthorizationScope(String authorization) {
        return validateAuthorization(authorization, UmaScopeType.AUTHORIZATION);
    }

    private AuthorizationGrant validateAuthorization(String authorization, UmaScopeType umaScopeType) {
        log.trace("Validate authorization: {0}", authorization);
        if (StringHelper.isEmpty(authorization)) {
            throw new WebApplicationException(Response.status(UNAUTHORIZED)
                    .entity(errorResponseFactory.getUmaJsonErrorResponse(UNAUTHORIZED_CLIENT)).build());
        }

        String token = tokenService.getTokenFromAuthorizationParameter(authorization);
        if (StringHelper.isEmpty(token)) {
            log.debug("Token is invalid");
            throw new WebApplicationException(Response.status(UNAUTHORIZED)
                    .entity(errorResponseFactory.getUmaJsonErrorResponse(UNAUTHORIZED_CLIENT)).build());
        }

        AuthorizationGrant authorizationGrant = authorizationGrantList.getAuthorizationGrantByAccessToken(token);
        if (authorizationGrant == null) {
            throw new WebApplicationException(Response.status(UNAUTHORIZED)
                    .entity(errorResponseFactory.getUmaJsonErrorResponse(ACCESS_DENIED)).build());
        }

        if (!authorizationGrant.isValid()) {
            throw new WebApplicationException(Response.status(UNAUTHORIZED)
                    .entity(errorResponseFactory.getUmaJsonErrorResponse(INVALID_TOKEN)).build());
        }

        Set<String> scopes = authorizationGrant.getScopes();
        if (!scopes.contains(umaScopeType.getValue())) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_ACCEPTABLE)
                    .entity(errorResponseFactory.getUmaJsonErrorResponse(UmaErrorResponseType.INVALID_CLIENT_SCOPE)).build());
        }
        return authorizationGrant;
    }

    public void validateRPT(UmaRPT rpt) {
   		if (rpt == null) {
   			throw new WebApplicationException(Response.status(UNAUTHORIZED)
   					.entity(errorResponseFactory.getUmaJsonErrorResponse(UmaErrorResponseType.NOT_AUTHORIZED_PERMISSION)).build());
   		}

   		rpt.checkExpired();
   		if (!rpt.isValid()) {
   			throw new WebApplicationException(Response.status(UNAUTHORIZED)
   					.entity(errorResponseFactory.getUmaJsonErrorResponse(UmaErrorResponseType.NOT_AUTHORIZED_PERMISSION)).build());
   		}
   	}

    public void validateResourceSetPermission(ResourceSetPermission resourceSetPermission) {
   		if (resourceSetPermission == null || "invalidated".equalsIgnoreCase(resourceSetPermission.getAmHost())) {
   			throw new WebApplicationException(Response.status(BAD_REQUEST)
   					.entity(errorResponseFactory.getUmaJsonErrorResponse(UmaErrorResponseType.INVALID_TICKET)).build());
   		}

   		resourceSetPermission.checkExpired();
   		if (!resourceSetPermission.isValid()) {
   			throw new WebApplicationException(Response.status(BAD_REQUEST)
   					.entity(errorResponseFactory.getUmaJsonErrorResponse(UmaErrorResponseType.EXPIRED_TICKET)).build());
   		}
   	}

    public void validateResourceSet(UmaPermission resourceSetPermissionRequest) {
   		String resourceSetId = resourceSetPermissionRequest.getResourceSetId();
   		if (StringHelper.isEmpty(resourceSetId)) {
   			log.error("Resource set id is empty");
   			throw new WebApplicationException(Response.status(BAD_REQUEST)
   					.entity(errorResponseFactory.getUmaJsonErrorResponse(UmaErrorResponseType.INVALID_RESOURCE_SET_ID)).build());
   		}

   		ResourceSet resourceSet;
   		try {
   			ResourceSet exampleResourceSet = new ResourceSet();
   			exampleResourceSet.setDn(resourceSetService.getBaseDnForResourceSet());
   			exampleResourceSet.setId(resourceSetId);
            List<ResourceSet> resourceSets = resourceSetService.findResourceSets(exampleResourceSet);
            if (resourceSets.size() != 1) {
       			log.error("Resource set isn't registered or there are two resource set with same Id");
       			throw new WebApplicationException(Response.status(BAD_REQUEST)
       					.entity(errorResponseFactory.getUmaJsonErrorResponse(UmaErrorResponseType.INVALID_RESOURCE_SET_ID)).build());
            }
            resourceSet = resourceSets.get(0);
   		} catch (EntryPersistenceException ex) {
   			log.error("Resource set isn't registered");
   			throw new WebApplicationException(Response.status(BAD_REQUEST)
   					.entity(errorResponseFactory.getUmaJsonErrorResponse(UmaErrorResponseType.INVALID_RESOURCE_SET_ID)).build());
   		}

        final List<String> scopeUrls = umaScopeService.getScopeUrlsByDns(resourceSet.getScopes());
        if (!scopeUrls.containsAll(resourceSetPermissionRequest.getScopes())) {
   			log.error("At least one of the scope isn't registered");
   			throw new WebApplicationException(Response.status(BAD_REQUEST)
   					.entity(errorResponseFactory.getUmaJsonErrorResponse(UmaErrorResponseType.INVALID_RESOURCE_SET_SCOPE)).build());
   		}
   	}
}
