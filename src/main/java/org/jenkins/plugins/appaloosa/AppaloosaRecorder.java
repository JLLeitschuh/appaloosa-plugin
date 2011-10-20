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
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.RunList;
import net.sf.json.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        if (build.getResult().isWorseOrEqualTo(Result.FAILURE))
            return false;

        listener.getLogger().println("Uploading to Appaloosa");

        listener.getLogger().println("Upload to Appaloosa Done");
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
