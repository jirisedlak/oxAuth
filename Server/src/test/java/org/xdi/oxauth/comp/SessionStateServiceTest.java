/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.comp;

import org.testng.Assert;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import org.xdi.oxauth.BaseComponentTest;
import org.xdi.oxauth.model.common.SessionIdState;
import org.xdi.oxauth.model.common.SessionState;
import org.xdi.oxauth.service.SessionStateService;
import org.xdi.oxauth.service.UserService;

import java.util.*;

import static org.testng.Assert.*;

/**
 * @author Yuriy Zabrovarnyy
 * @author Javier Rojas Blum
 * @version December 15, 2015
 */

public class SessionStateServiceTest extends BaseComponentTest {

    private SessionStateService m_service;
    private UserService userService;

    @Override
    public void beforeClass() {
        m_service = SessionStateService.instance();
        userService = UserService.instance();
    }

    @Override
    public void afterClass() {
    }

    private SessionState generateSession(String userInum) {
    	String userDn = userService.getDnForUser(userInum);
        return m_service.generateUnauthenticatedSessionState(userDn, new Date(), SessionIdState.UNAUTHENTICATED, new HashMap<String, String>(), true);
    }

    @Parameters({"userInum"})
    @Test
    public void checkOutdatedUnauthenticatedSessionIdentification(String userInum) {

        // set time -1 hour
        Calendar c = Calendar.getInstance();
        c.add(Calendar.HOUR, -1);
        SessionState m_sessionState = generateSession(userInum);
        m_sessionState.setLastUsedAt(c.getTime());
        m_service.updateSessionState(m_sessionState, false);

        // check identification
        final List<SessionState> outdatedSessions = m_service.getUnauthenticatedIdsOlderThan(60);
        Assert.assertTrue(outdatedSessions.contains(m_sessionState));

    }

    @Parameters({"userInum"})
    @Test
    public void statePersistence(String userInum) {
        SessionState newId = null;
        try {
        	String userDn = userService.getDnForUser(userInum);
            newId = m_service.generateAuthenticatedSessionState(userDn);

            Assert.assertEquals(newId.getState(), SessionIdState.AUTHENTICATED);

            Map<String, String> sessionAttributes = new HashMap<String, String>();
            sessionAttributes.put("k1", "v1");
            newId.setSessionAttributes(sessionAttributes);

            m_service.updateSessionState(newId);

            final SessionState fresh = m_service.getSessionByDN(newId.getDn());
            Assert.assertEquals(fresh.getState(), SessionIdState.AUTHENTICATED);
            Assert.assertTrue(fresh.getSessionAttributes().containsKey("k1"));
            Assert.assertTrue(fresh.getSessionAttributes().containsValue("v1"));
        } finally {
            if (newId != null) {
                getLdapManager().remove(newId);
            }
        }
    }

    @Parameters({"userInum"})
    @Test
    public void testUpdateLastUsedDate(String userInum) {
        SessionState m_sessionState = generateSession(userInum);
        final SessionState fromLdap1 = m_service.getSessionByDN(m_sessionState.getDn());
        final Date createdDate = m_sessionState.getLastUsedAt();
        System.out.println("Created date = " + createdDate);
        Assert.assertEquals(createdDate, fromLdap1.getLastUsedAt());

        sleepSeconds(1);
        m_service.updateSessionState(m_sessionState);

        final SessionState fromLdap2 = m_service.getSessionByDN(m_sessionState.getDn());
        System.out.println("Updated date = " + fromLdap2.getLastUsedAt());
        Assert.assertTrue(createdDate.before(fromLdap2.getLastUsedAt()));
    }

    @Parameters({"userInum"})
    @Test
    public void testUpdateAttributes(String userInum) {
        SessionState m_sessionState = generateSession(userInum);
        final String clientId = "testClientId";
        final SessionState fromLdap1 = m_service.getSessionByDN(m_sessionState.getDn());
        final Date createdDate = m_sessionState.getLastUsedAt();
        assertEquals(createdDate, fromLdap1.getLastUsedAt());
        assertFalse(fromLdap1.isPermissionGrantedForClient(clientId));

        sleepSeconds(1);
        m_sessionState.setAuthenticationTime(new Date());
        m_sessionState.addPermission(clientId, true);
        m_service.updateSessionState(m_sessionState);

        final SessionState fromLdap2 = m_service.getSessionByDN(m_sessionState.getDn());
        assertTrue(createdDate.before(fromLdap2.getLastUsedAt()));
        assertNotNull(fromLdap2.getAuthenticationTime());
        assertTrue(fromLdap2.isPermissionGrantedForClient(clientId));
    }


    @Parameters({"userInum"})
    @Test
    public void testOldSessionsIdentification(String userInum) {
        SessionState m_sessionState = generateSession(userInum);

        sleepSeconds(2);
        Assert.assertTrue(m_service.getIdsOlderThan(1).contains(m_sessionState));
    }
}
