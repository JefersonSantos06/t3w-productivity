package com.github.jefersonsantos06.t3wproductivity.services.state;

import com.github.jefersonsantos06.t3wproductivity.model.EnvironmentPreset;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EnvironmentState {

    public static class Project {
        /**
         * Legado: as variáveis visíveis agora são globais. Mantido apenas para migrar estados antigos.
         */
        private List<String> visibleEnvironmentKeys = new ArrayList<>();
        private Map<String, String> projectEnvironment = new LinkedHashMap<>();
        private String activePresetId;

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

        public String getActivePresetId() {
            return activePresetId;
        }

        public void setActivePresetId(final String activePresetId) {
            this.activePresetId = activePresetId;
        }
    }

    public static class Global {
        private Map<String, String> globalEnvironment = new LinkedHashMap<>();
        private List<EnvironmentPreset> presets = new ArrayList<>();
        private List<String> visibleEnvironmentKeys = new ArrayList<>();

        public Map<String, String> getGlobalEnvironment() {
            return globalEnvironment;
        }

        public void setGlobalEnvironment(final Map<String, String> globalEnvironment) {
            this.globalEnvironment = globalEnvironment;
        }

        public List<EnvironmentPreset> getPresets() {
            return presets;
        }

        public void setPresets(final List<EnvironmentPreset> presets) {
            this.presets = presets;
        }

        public List<String> getVisibleEnvironmentKeys() {
            return visibleEnvironmentKeys;
        }

        public void setVisibleEnvironmentKeys(final List<String> visibleEnvironmentKeys) {
            this.visibleEnvironmentKeys = visibleEnvironmentKeys;
        }
    }

}
