/*
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 */
package org.wso2.andes.server.security.access.plugins;

import org.wso2.andes.configuration.qpid.plugins.ConfigurationPluginFactory;
import org.wso2.andes.server.security.SecurityPluginActivator;
import org.wso2.andes.server.security.SecurityPluginFactory;
import org.osgi.framework.BundleActivator;

/**
 * The OSGi {@link BundleActivator} for {@link Firewall}.
 */
public class FirewallActivator extends SecurityPluginActivator
{
    public SecurityPluginFactory getFactory()
    {
        return Firewall.FACTORY;
    }

    public ConfigurationPluginFactory getConfigurationFactory()
    {
        return FirewallConfiguration.FACTORY;
    }
}
