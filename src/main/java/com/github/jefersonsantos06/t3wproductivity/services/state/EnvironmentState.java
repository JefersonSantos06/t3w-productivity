package com.github.jefersonsantos06.t3wproductivity.services.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EnvironmentState {

    public static class Project {
        private List<String> visibleEnvironmentKeys = new ArrayList<>();
        private Map<String, String> projectEnvironment = new LinkedHashMap<>();

        public Map<String, String> getProjectEnvironment() {
            return projectEnvironment;
        }

        public void setProjectEnvironment(final Map<String, String> projectEnvironment) {
            this.projectEnvironment = projectEnvironment;
        }

        public List<String> getVisibleEnvironmentKeys() {
            return visibleEnvironmentKeys;
        }

        public void setVisibleEnvironmentKeys(final List<String> visibleEnvironmentKeys) {
            this.visibleEnvironmentKeys = visibleEnvironmentKeys;
        }
    }

    public static class Global {
        private Map<String, String> globalEnvironment = new LinkedHashMap<>();

        public Map<String, String> getGlobalEnvironment() {
            return globalEnvironment;
        }

        public void setGlobalEnvironment(final Map<String, String> globalEnvironment) {
            this.globalEnvironment = globalEnvironment;
        }
    }

}
