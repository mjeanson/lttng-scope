/*******************************************************************************
 * Copyright (c) 2013, 2014 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Geneviève Bastien - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.core.analysis.internal;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.InvalidRegistryObjectException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.tracecompass.tmf.core.activator.internal.Activator;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModuleSource;

/**
 * Utility class for accessing TMF analysis module extensions from the
 * platform's extensions registry and returning the module sources.
 *
 * @author Geneviève Bastien
 */
public final class TmfAnalysisModuleSources {

    /** Extension point ID */
    public static final String TMF_ANALYSIS_TYPE_ID = "org.eclipse.linuxtools.tmf.core.analysis"; //$NON-NLS-1$

    /** Extension point element 'module' */
    public static final String SOURCE_ELEM = "source"; //$NON-NLS-1$

    /** Extension point attribute 'class' */
    public static final String CLASS_ATTR = "class"; //$NON-NLS-1$

    private TmfAnalysisModuleSources() {

    }

    /**
     * Return the analysis module sources advertised in the extension point, in
     * iterable format.
     *
     * @return List of {@link IAnalysisModuleSource}
     */
    public static Iterable<IAnalysisModuleSource> getSources() {
        List<IAnalysisModuleSource> sources = new ArrayList<>();
        // Get the sources element from the extension point
        IConfigurationElement[] config = Platform.getExtensionRegistry().getConfigurationElementsFor(TMF_ANALYSIS_TYPE_ID);
        for (IConfigurationElement ce : config) {
            String elementName = ce.getName();
            if (elementName.equals(SOURCE_ELEM)) {
                try {
                    IAnalysisModuleSource source = (IAnalysisModuleSource) ce.createExecutableExtension(CLASS_ATTR);
                    if (source != null) {
                        sources.add(source);
                    }
                } catch (InvalidRegistryObjectException e) {
                    Activator.instance().logError("Error creating module source", e); //$NON-NLS-1$
                } catch (CoreException e) {
                    Activator.instance().logError("Error creating module source", e); //$NON-NLS-1$
                }

            }
        }
        return sources;
    }

}