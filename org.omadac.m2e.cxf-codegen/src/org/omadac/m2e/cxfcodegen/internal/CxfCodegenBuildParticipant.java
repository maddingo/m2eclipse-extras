/*******************************************************************************
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.omadac.m2e.cxfcodegen.internal;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.bitstrings.eclipse.m2e.common.BuildHelper;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.project.configurator.MojoExecutionBuildParticipant;
import org.sonatype.plexus.build.incremental.BuildContext;

public class CxfCodegenBuildParticipant extends MojoExecutionBuildParticipant {

    public CxfCodegenBuildParticipant(MojoExecution execution) {
        super(execution, true);
    }

    @Override
    public Set<IProject> build(int kind, IProgressMonitor monitor) throws Exception {
        IMaven maven = MavenPlugin.getMaven();
        BuildContext buildContext = getBuildContext();

        boolean needBuild = false;

        Class<?> wsdlOptionCls = loadPluginClass(maven, "org.apache.cxf.maven_plugin.wsdl2java.WsdlOption");

        // default option (http://cxf.apache.org/docs/maven-cxf-codegen-plugin-wsdl-to-java.html#Mavencxf-codegen-plugin%28WSDLtoJava%29-Example3:UsingdefaultOptiontoavoidrepetition)
        Object defaultOptions = maven.getMojoParameterValue(getSession(), getMojoExecution(), "defaultOptions", wsdlOptionCls);
        needBuild = needBuild(buildContext, new WsdlOptionWrapper(defaultOptions));
    	
    	if (!needBuild) {
        	// check wsdl if files have changed
			List<?> wsdlOptions = maven.getMojoParameterValue(getSession(), getMojoExecution(), "wsdlOptions", List.class);
        	for (Object wsdlOptionObj : wsdlOptions) {
        		WsdlOptionWrapper wsdlOption = new WsdlOptionWrapper(wsdlOptionObj);
				needBuild = needBuild(buildContext, wsdlOption);
				if (needBuild) {
					break;
				}
			}
    	}

        if (!needBuild) {
        	return null;
        }
        
		final Set<IProject> result = super.build(kind, monitor);

		File sourceRoot = maven.getMojoParameterValue(getSession(), getMojoExecution(),
                "sourceRoot", File.class);
		if (sourceRoot != null) {
			buildContext.refresh(sourceRoot);
		}

		return result;
    }

	private Class<?> loadPluginClass(IMaven maven, String className) throws CoreException,
			ClassNotFoundException {
		// we need to get the class loader of the plugin in order to load WsdlOption
        Mojo mojo = maven.getConfiguredMojo(getSession(), getMojoExecution(), Mojo.class);
        ClassLoader cl = mojo.getClass().getClassLoader();
        Class<?> wsdlOptionCls = Class.forName(className, true, cl);
		return wsdlOptionCls;
	}

	private boolean needBuild(BuildContext buildContext, WsdlOptionWrapper wsdlOption)
			throws Exception {
		boolean needBuild = false;

		String defaultWsdl = wsdlOption.getWsdl();
		needBuild = needsBuild(buildContext, defaultWsdl);
		
		if (!needBuild) {
			Collection<String> defaultBindingFiles = wsdlOption.getBindingFiles();
			for (String bindingFile : defaultBindingFiles) {
				needBuild = needsBuild(buildContext, bindingFile);
				if (needBuild) {
					break;
				}
			}
		}
		if (!needBuild) {
			Object artifact = wsdlOption.getWsdlArtifact();
			if (artifact != null) {
				needBuild = needsBuild(buildContext, artifact.toString());
			}
		}
		return needBuild;
	}

	private boolean needsBuild(BuildContext buildContext, String currFile)
			throws Exception {
		if(currFile == null || currFile.isEmpty()) {
			return false;
		}
		String[] modifiedFiles = BuildHelper.getModifiedFiles(buildContext, new File(currFile));
		
		return (modifiedFiles != null && modifiedFiles.length > 0);
	}
	
	// a class to cope with the class loading issues
	private static class WsdlOptionWrapper {
		private final Object target;
		
		private final Method mGetWsdl;
		private final Method mGetBindingFiles;
		private final Method mGetWsdlArtifact;
		
		public WsdlOptionWrapper(Object target) throws Exception {
			this.target = target;
			mGetWsdl = target.getClass().getMethod("getWsdl");
			mGetBindingFiles = target.getClass().getMethod("getBindingFiles");
			mGetWsdlArtifact = target.getClass().getMethod("getWsdlArtifact");
		}

		public String getWsdl() {
			try {
				return (String) mGetWsdl.invoke(target);
			} catch(Exception e) {
				throw new RuntimeException(e);
			}
		}

		@SuppressWarnings("unchecked")
		public Collection<String> getBindingFiles() {
			try {
				return (Collection<String>) mGetBindingFiles.invoke(target);
			} catch(Exception e) {
				throw new RuntimeException(e);
			}
		}
		
		public Object getWsdlArtifact() {
			try {
				
				return mGetWsdlArtifact.invoke(target);
			} catch(Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
}
