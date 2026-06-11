package ai.luumo.tools.pi.piswarm.gui.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Persisted set of {@link Profile launch profiles}, stored in
 * {@code profiles-config.json}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ProfilesConfig {

    private List<Profile> profiles = new ArrayList<>();

    public List<Profile> getProfiles() {
        return profiles;
    }

    public void setProfiles(List<Profile> profiles) {
        this.profiles = profiles == null ? new ArrayList<>() : profiles;
    }
}
