/*
 * The MIT License
 *
 * Copyright (c) 2011 Arnaud Héritier
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkins.plugins.appaloosa;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.RunList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.sf.json.JSONObject;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import com.appaloosastore.client.AppaloosaClient;
import com.appaloosastore.client.AppaloosaDeployException;

public class AppaloosaRecorder extends Recorder {

    private String token;

    public String getToken() {
        return this.token;
    }

    @DataBoundConstructor
    public AppaloosaRecorder(String token) {
        this.token = token;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        if (build.getResult().isWorseOrEqualTo(Result.FAILURE))
            return false;

        // Validates that the organization token is filled in the project configuration.
        if (StringUtils.isBlank(getToken())) {
            listener.error("Appaloosa token not defined in project settings");
            return false;
        }

        boolean hadFailure = false;
        boolean hadArtifactsToUpload = false;
        // Process the list of archived artifacts to find .apk and .ipa files
        List<Run.Artifact> artifacts = build.getArtifacts();
        listener.getLogger().println("# of archived artifacts : " + artifacts.size());
        AppaloosaClient appaloosaClient = new AppaloosaClient(getToken());
        appaloosaClient.useLogger(listener.getLogger());
        
        for (Run.Artifact artifact : artifacts) {
            if (artifact.getFileName().endsWith(".ipa") || artifact.getFileName().endsWith(".apk")) {
                hadArtifactsToUpload = true;
                listener.getLogger().println("Artifact : " + artifact.getDisplayPath());
                try{
                	appaloosaClient.deployFile(artifact.getDisplayPath());
                	listener.getLogger().println("Upload to Appaloosa Done.");
                }catch (AppaloosaDeployException e) {
                    listener.error("Upload to Appaloosa Failed");
				}
            }
        }
        if (!hadArtifactsToUpload) {
            listener.error("No .ipa or .apk files to upload was found in project archives. Did you configured it to archive such files ?");
            return false;
        } else if (hadFailure) {
            listener.error("There was at least one error while uploading files to appaloosa");
            return false;
        }


        return true;
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
        ArrayList<AppaloosaBuildAction> actions = new ArrayList<AppaloosaBuildAction>();
        RunList<? extends AbstractBuild<?, ?>> builds = project.getBuilds();

        Collection predicated = CollectionUtils.select(builds, new Predicate() {
            public boolean evaluate(Object o) {
                return ((AbstractBuild<?, ?>) o).getResult().isBetterOrEqualTo(Result.SUCCESS);
            }
        });

        ArrayList<AbstractBuild<?, ?>> filteredList = new ArrayList<AbstractBuild<?, ?>>(predicated);


        Collections.reverse(filteredList);
        for (AbstractBuild<?, ?> build : filteredList) {
            List<AppaloosaBuildAction> appaloosaActions = build.getActions(AppaloosaBuildAction.class);
            if (appaloosaActions != null && appaloosaActions.size() > 0) {
                for (AppaloosaBuildAction action : appaloosaActions) {
                    actions.add(new AppaloosaBuildAction(action));
                }
                break;
            }
        }

        return actions;
    }

    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public DescriptorImpl() {
            super(AppaloosaRecorder.class);
            load();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            // XXX is this now the right style?
            req.bindJSON(this, json);
            save();
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Upload to Appaloosa";
        }
    }
}
