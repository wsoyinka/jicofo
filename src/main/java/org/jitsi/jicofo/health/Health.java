/*
 * Copyright @ 2018 - present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.jicofo.health;

import org.jitsi.health.*;
import org.jitsi.jicofo.*;
import org.jitsi.osgi.*;
import org.jitsi.utils.logging.Logger;
import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.*;
import org.jxmpp.jid.parts.*;
import org.jxmpp.stringprep.*;
import org.osgi.framework.*;

import java.util.*;
import java.util.logging.*;

/**
 * Checks the health of {@link FocusManager}.
 *
 * @author Lyubomir Marinov
 * @author Pawel Domas
 */
public class Health
    extends AbstractHealthCheckService
{
    private FocusManager focusManager;

    /**
     * The {@code Logger} utilized by the {@code Health} class to print
     * debug-related information.
     */
    private static final Logger logger = Logger.getLogger(Health.class);

    /**
     * The {@code JitsiMeetConfig} properties to be utilized for the purposes of
     * checking the health (status) of Jicofo.
     */
    private static final Map<String,String> JITSI_MEET_CONFIG
        = Collections.emptyMap();

    /**
     * The pseudo-random generator used to generate random input for
     * {@link FocusManager} such as room names.
     */
    private static final Random RANDOM = new Random();

    @Override
    public void start(BundleContext bundleContext)
        throws Exception
    {
        focusManager
            = Objects.requireNonNull(
                ServiceUtils2.getService(bundleContext, FocusManager.class),
                "Can not find FocusManager.");

        super.start(bundleContext);
    }

    @Override
    public void stop(BundleContext bundleContext)
        throws Exception
    {
        focusManager = null;

        super.stop(bundleContext);
    }

    @Override
    public void performCheck()
        throws Exception
    {
        Objects.requireNonNull(focusManager, "FocusManager is not set.");

        check(focusManager);
    }

    /**
     * Checks the health (status) of a specific {@link FocusManager}.
     *
     * @param focusManager the {@code FocusManager} to check the health (status)
     * of
     * @throws Exception if an error occurs while checking the health (status)
     * of {@code focusManager} or the check determines that {@code focusManager}
     * is not healthy
     */
    private static void check(FocusManager focusManager)
        throws Exception
    {
        // Get the MUC service to perform the check on.
        JitsiMeetServices services = focusManager.getJitsiMeetServices();

        Jid mucService = services != null ? services.getMucService() : null;

        if (mucService == null)
        {
            logger.error(
                "No MUC service found on XMPP domain or Jicofo has not" +
                    " finished initial components discovery yet");

            throw new RuntimeException("No MUC component");
        }

        // Generate a pseudo-random room name. Minimize the risk of clashing
        // with existing conferences.
        EntityBareJid roomName;

        do
        {
            roomName = JidCreate.entityBareFrom(
                generateRoomName(),
                mucService.asDomainBareJid()
            );
        }
        while (focusManager.getConference(roomName) != null);

        // Create a conference with the generated room name.
        if (!focusManager.conferenceRequest(
                roomName,
                JITSI_MEET_CONFIG,
                Level.WARNING /* conference logging level */,
                false /* don't include in statistics */))
        {
            throw new RuntimeException(
                "Failed to create conference with room name " + roomName);
        }
    }

    /**
     * Generates a pseudo-random room name which is not guaranteed to be unique.
     *
     * @return a pseudo-random room name which is not guaranteed to be unique
     */
    private static Localpart generateRoomName()
    {
        try
        {
            return
                Localpart.from(Health.class.getName()
                    + "-"
                    + Long.toHexString(
                        System.currentTimeMillis() + RANDOM.nextLong()));
        }
        catch (XmppStringprepException e)
        {
            // ignore, cannot happen
            return null;
        }
    }
}
