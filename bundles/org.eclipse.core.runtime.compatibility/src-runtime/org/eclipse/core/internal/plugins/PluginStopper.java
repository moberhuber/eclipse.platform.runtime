/*******************************************************************************
 * Copyright (c) 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.plugins;

import java.util.*;
import org.eclipse.core.internal.runtime.InternalPlatform;
import org.eclipse.core.runtime.*;
import org.osgi.framework.*;

/**
 * Implementation for the runtime shutdown hook that provides 
 * support for legacy bundles. All legacy bundles are stopped 
 * in the proper order.
 */
public class PluginStopper implements IShutdownHook {

	private static final String OPTION_DEBUG_PLUGIN_STOPPER = "org.eclipse.core.runtime/debug/pluginstopper"; //$NON-NLS-1$		
	
	private class ReferenceKey {
		private long referrerId;
		private long referredId;
		public ReferenceKey(long referrerId, long referredId) {
			this.referrerId = referrerId;
			this.referredId = referredId;
		}
		public boolean equals(Object obj) {
			return referredId == ((ReferenceKey) obj).referredId && referrerId == ((ReferenceKey) obj).referrerId;
		}
		public int hashCode() {
			return ((int) (referredId & 0xFFFF)) << 16 + (int) (referrerId & 0xFFFF);
		}
	}

	public void run() {
		boolean debug = InternalPlatform.getDefault().getBooleanOption(OPTION_DEBUG_PLUGIN_STOPPER,false);		
		BundleContext context = InternalPlatform.getDefault().getBundleContext();
		Map references = new HashMap();
		IPluginDescriptor[] plugins = Platform.getPluginRegistry().getPluginDescriptors();		
		Map activeLegacyBundles = new HashMap(plugins.length);
		// gather all active legacy bundles
		for (int i = 0; i < plugins.length; i++) {
			if (!plugins[i].isLegacy())
				continue; 
			Bundle pluginBundle = context.getBundle(plugins[i].getUniqueIdentifier());
			if (pluginBundle.getState()  == Bundle.ACTIVE) {
				activeLegacyBundles.put(pluginBundle.getGlobalName(),pluginBundle);
				if (debug)
					System.out.println("Active plugin bundle: " + pluginBundle.getGlobalName()); //$NON-NLS-1$
			}
		}
		// find dependencies betweeen them
		for (Iterator pluginBundlesIter = activeLegacyBundles.values().iterator(); pluginBundlesIter.hasNext();) {
			Bundle pluginBundle = (Bundle) pluginBundlesIter.next();
			// TODO eliminate this reference to getHeaders
			Dictionary headers = pluginBundle.getHeaders();
			String requireBundleNames = (String) headers.get(Constants.REQUIRE_BUNDLE);
			if (requireBundleNames == null)
				// no Require-Bundle entry - does not depend on other legacy bundles
				continue;
			StringTokenizer tokenizer = new StringTokenizer(requireBundleNames," ,\t\n\r\f");
			while(tokenizer.hasMoreTokens()) {				
				String importedBundleName = tokenizer.nextToken();
				Bundle importedBundle = (Bundle) activeLegacyBundles.get(importedBundleName);
				// ignore dependencies on non-active legacy bundles
				if (importedBundle == null)
					continue;
				references.put(new ReferenceKey(pluginBundle.getBundleId(),importedBundle.getBundleId()), new Object[] { pluginBundle, importedBundle });
				
			}
		}
		Bundle[] orderedBundles = (Bundle[]) activeLegacyBundles.values().toArray(new Bundle[activeLegacyBundles.size()]);
		Object[][] cycles = ComputeNodeOrder.computeNodeOrder(orderedBundles, (Object[][]) references.values().toArray(new Object[references.size()][]));
		if (debug) { 
			for (int i = 0; i < cycles.length; i++) {
				StringBuffer sb = new StringBuffer("***Cycle: "); //$NON-NLS-1$
				for (int j = 0; j < cycles[i].length; j++) {
					sb.append('\n');				
					sb.append(((Bundle)cycles[i][j]).getGlobalName());
				}
				System.out.println(sb);
			}
			for (Iterator iter = references.values().iterator(); iter.hasNext();) {
				Object[] ref = (Object[]) iter.next();
				System.out.println(ref[0].toString() + " -> " + ref[1]); //$NON-NLS-1$
			}
		}
		// stop all active legacy bundles in the reverse order of Require-Bundle
		for (int i = orderedBundles.length - 1; i >= 0; i--) {
			try {
				if (Platform.getPluginRegistry().getPluginDescriptor(orderedBundles[i].getGlobalName()).isLegacy() && orderedBundles[i].getState() == Bundle.ACTIVE) {
					if (debug)
						System.out.println("Stopping: " + orderedBundles[i].getGlobalName() + " (#" + orderedBundles[i].getBundleId() + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					orderedBundles[i].stop();
				}
			} catch (Exception be) {
				be.printStackTrace();//TODO Need to log the error
			}
		}
	}
}